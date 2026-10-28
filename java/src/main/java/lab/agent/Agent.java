package lab.agent;

import com.azure.ai.agents.AgentsClientBuilder;
import com.azure.ai.agents.ResponsesClient;
import com.azure.ai.agents.models.AgentReference;
import com.azure.ai.agents.models.AzureCreateResponseOptions;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.core.JsonValue;
import com.openai.models.conversations.ConversationCreateParams;
import com.openai.models.responses.EasyInputMessage;
import com.openai.models.responses.FunctionTool;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseInputItem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A hand-written agent: the piece Python's {@code Agent} and C#'s {@code AIAgent}
 * give you ready-made.
 *
 * <p>The Java SDK has no agent class, so the lab writes one. That's the point
 * of this file: the logic a framework hides is all here, and it is small.
 *
 * <ul>
 *   <li><b>Configuration</b>: a model, a name, instructions and tools. The same
 *       ingredients as {@code client.as_agent(...)} / {@code client.AsAIAgent(...)}.</li>
 *   <li><b>{@link #run}</b>: the tool loop. Send the prompt; if the model asks for
 *       a tool, run it and send the result back; repeat until it answers in text.</li>
 * </ul>
 *
 * <p>Two kinds, behind one interface, as in C#:
 * <ul>
 *   <li>{@link #local}: everything (model, instructions, tools) is defined in
 *       this process. Nothing is created in Foundry; the agent dies with the
 *       process.</li>
 *   <li>{@link #remote}: a reference to a published agent, by name and version.
 *       Model and instructions live in the Foundry project.</li>
 * </ul>
 */
public final class Agent {

    /**
     * Upper bound on model round trips per {@link #run}. A framework applies a
     * limit like this silently; without one, a model that keeps asking for tools
     * would loop (and bill) forever. Each round is a full inference call.
     */
    public static final int MAX_ROUNDS = 10;

    /** Server-side conversation that holds the history between turns. */
    public record Session(String conversationId) {
    }

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final boolean TRACE = Boolean.getBoolean("agent.trace");

    private final OpenAIClient openai;       // model calls (local) and conversations
    private final ResponsesClient responses; // calls to a published agent (remote); null for local
    private final String name;
    private final String model;              // local only
    private final String instructions;       // local only
    private final String version;            // remote only
    private final Map<String, Tool> tools = new LinkedHashMap<>();

    private Agent(OpenAIClient openai, ResponsesClient responses, String name, String model,
                  String instructions, String version, List<Tool> tools) {
        this.openai = openai;
        this.responses = responses;
        this.name = name;
        this.model = model;
        this.instructions = instructions;
        this.version = version;
        tools.forEach(t -> this.tools.put(t.name(), t));
    }

    /**
     * An agent that exists only in this process.
     *
     * @param project the Foundry project connection (endpoint + credential)
     * @param model   the deployment name, e.g. "my-model"
     */
    public static Agent local(AgentsClientBuilder project, String model, String name,
                              String instructions, List<Tool> tools) {
        return new Agent(project.buildOpenAIClient(), null, name, model, instructions, null, tools);
    }

    /**
     * A reference to an agent published in the Foundry project. No model and no
     * instructions here: those are stored with the agent version.
     */
    public static Agent remote(AgentsClientBuilder project, String name, String version) {
        return new Agent(project.buildOpenAIClient(), project.buildResponsesClient(),
                name, null, null, version, List.of());
    }

    /**
     * Open a conversation on the server. Turns run with the same session see each
     * other, and this process holds nothing but the conversation's id.
     */
    public Session createSession() {
        if (responses == null) {
            // Local agents in this lab are stateless, like Python's and C#'s local
            // steps. A local session would be a list of messages held in memory.
            throw new UnsupportedOperationException("sessions are only used with remote agents in this lab");
        }
        return new Session(openai.conversations().create(ConversationCreateParams.builder().build()).id());
    }

    /** One turn without a session: the history lives only inside this call. */
    public String run(String prompt) {
        return run(prompt, null);
    }

    /**
     * One turn: the agent loop.
     *
     * <p>Where the history lives decides what gets sent each round:
     * <ul>
     *   <li><b>No session</b>: nobody else remembers anything, so {@code input}
     *       grows each round with the model's tool calls and our results, and the
     *       whole list is sent again. That's why every tool round costs more
     *       tokens than the last.</li>
     *   <li><b>With a session</b>: the server conversation already holds the
     *       prompt and the model's calls, so each round sends only what's new: the
     *       tool results.</li>
     * </ul>
     */
    public String run(String prompt, Session session) {
        List<ResponseInputItem> input = new ArrayList<>();
        input.add(ResponseInputItem.ofEasyInputMessage(EasyInputMessage.builder()
                .role(EasyInputMessage.Role.USER)
                .content(prompt)
                .build()));

        for (int round = 1; round <= MAX_ROUNDS; round++) {
            // 1. Ask the model. It sees the instructions, the tool schemas and the input.
            Response response = send(input, session);

            // 2. Did it ask for tools? A reply can contain several calls at once.
            List<ResponseFunctionToolCall> calls = response.output().stream()
                    .flatMap(item -> item.functionCall().stream())
                    .toList();

            // 3. No tool calls means the model has answered. The loop ends here.
            if (calls.isEmpty()) {
                return text(response);
            }

            // 4. Keep the history consistent for the next round.
            if (session == null) {
                // Echo each call back, so the model can match our results to its
                // requests by callId. Rebuilt with only callId, name and arguments:
                // the response wasn't stored on the server (see send), so its item
                // ids wouldn't resolve there.
                calls.forEach(call -> input.add(ResponseInputItem.ofFunctionCall(
                        ResponseFunctionToolCall.builder()
                                .callId(call.callId())
                                .name(call.name())
                                .arguments(call.arguments())
                                .build())));
            } else {
                // The conversation already recorded the calls.
                input.clear();
            }

            // 5. Run each requested tool and add its result to the next request.
            for (ResponseFunctionToolCall call : calls) {
                String result = invoke(call);
                trace(round, call, result);
                input.add(ResponseInputItem.ofFunctionCallOutput(ResponseInputItem.FunctionCallOutput.builder()
                        .callId(call.callId())
                        .output(result)
                        .build()));
            }
            // 6. Loop: the model now reads the results and decides again.
        }
        throw new IllegalStateException(name + " gave up after " + MAX_ROUNDS + " rounds of tool calls");
    }

    /** One round trip to the model, local or remote. */
    private Response send(List<ResponseInputItem> input, Session session) {
        ResponseCreateParams.Builder request = ResponseCreateParams.builder().inputOfResponse(input);

        if (responses == null) {
            // Local: we supply everything the published agent would otherwise hold.
            request.model(model).instructions(instructions);
            tools.values().forEach(t -> request.addTool(toFunctionTool(t)));
            // store(false): nothing is kept on the server. This agent leaves no trace
            // in Foundry, which is also why it has to resend its history.
            request.store(false);
            return openai.responses().create(request.build());
        }

        // Remote: name and version select the stored definition, on every call.
        // Changing the version here never changes what other callers get.
        if (session != null) {
            request.conversation(session.conversationId());
        }
        var options = new AzureCreateResponseOptions()
                .setAgentReference(new AgentReference(name).setVersion(version));
        return responses.createAzureResponse(options, request);
    }

    /**
     * Run the Java code behind a tool call.
     *
     * <p>Every failure comes back as text, never as an exception: an exception
     * would end the run, while text lets the model read the problem and decide
     * what to do. Same reasoning as the az helpers in the Python and C# Ops steps.
     */
    private String invoke(ResponseFunctionToolCall call) {
        Tool tool = tools.get(call.name());
        if (tool == null) {
            return "ERROR: no tool named '" + call.name() + "'.";
        }
        try {
            // The model sends its arguments as a JSON string, e.g. {"order_id":"ORD-40021"}.
            Map<String, Object> args = JSON.readValue(call.arguments(), new TypeReference<>() {
            });
            return tool.handler().apply(args);
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    /** The model's view of a tool: name, description, JSON Schema. Nothing else. */
    private static FunctionTool toFunctionTool(Tool tool) {
        var parameters = FunctionTool.Parameters.builder();
        tool.parameters().forEach((key, value) -> parameters.putAdditionalProperty(key, JsonValue.from(value)));
        return FunctionTool.builder()
                .name(tool.name())
                .description(tool.description())
                .parameters(parameters.build())
                // strict: the model must match the schema exactly. Requires every
                // property in "required" and additionalProperties=false, which
                // Tool.stringParams guarantees.
                .strict(true)
                .build();
    }

    /** The text of the model's answer, from all its message parts. */
    private static String text(Response response) {
        var text = new StringBuilder();
        response.output().forEach(item -> item.message().ifPresent(message ->
                message.content().forEach(part -> part.outputText().ifPresent(t -> text.append(t.text())))));
        return text.toString();
    }

    /** Run with -Dagent.trace=true to watch the loop on stderr. */
    private static void trace(int round, ResponseFunctionToolCall call, String result) {
        if (TRACE) {
            String shown = result.length() > 80 ? result.substring(0, 80) + "…" : result;
            System.err.printf("[round %d] %s(%s) -> %s%n", round, call.name(), call.arguments(),
                    shown.replace('\n', ' '));
        }
    }
}
