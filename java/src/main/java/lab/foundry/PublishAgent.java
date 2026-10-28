package lab.foundry;

import com.azure.ai.agents.AgentsClient;
import com.azure.ai.agents.AgentsClientBuilder;
import com.azure.ai.agents.models.AgentVersionDetails;
import com.azure.ai.agents.models.CreateAgentVersionInput;
import com.azure.ai.agents.models.PromptAgentDefinition;
import lab.shared.Settings;

/**
 * Publish an agent INTO the Foundry project, as a named, versioned resource.
 *
 * <p>This is the difference the lab.local programs do not show. Agent.local()
 * there builds an object that dies with the process. Here the agent definition
 * is sent to Foundry and becomes a resource you can see in the portal, connect
 * to from anywhere, and version.
 *
 * <p>Nothing is containerised or "deployed" in the hosting sense. A prompt agent
 * is declarative (a model reference plus instructions) and Foundry's own runtime
 * executes it. The existing model deployment is reused, not duplicated.
 *
 * <p>Counterpart of python/foundry_native/publish_agent.py and
 * csharp/FoundryNative.Publish. Each language publishes its own agent; this one
 * is lab-assistant-java.
 *
 * <p>Run this once, then look at ai.azure.com.
 */
public final class PublishAgent {

    static final String AGENT_NAME = "lab-assistant-java";

    public static void main(String[] args) {
        Settings.load();

        // Step 1: describe the agent. Like C#, and unlike Python's
        // to_prompt_agent(), the definition is written directly: it is exactly
        // what Foundry stores. Java's SDK calls it PromptAgentDefinition, the
        // name Microsoft's docs use (C#'s package calls it DeclarativeAgentDefinition).
        var definition = new PromptAgentDefinition(Settings.required("FOUNDRY_MODEL"))
                .setInstructions("You are a concise assistant for an Azure Foundry lab. "
                        + "Answer in at most three sentences. "
                        + "If you are unsure, say so rather than guessing.");

        // Step 2: the actual write. From here the agent exists independently of
        // this process. Re-running creates a NEW version rather than overwriting,
        // which is what makes rollback possible.
        AgentsClient agents = new AgentsClientBuilder()
                .endpoint(Settings.required("FOUNDRY_PROJECT_ENDPOINT"))
                .credential(Settings.credential())
                .buildAgentsClient();
        AgentVersionDetails version = agents.createAgentVersion(AGENT_NAME,
                new CreateAgentVersionInput(definition)
                        .setDescription("Lab assistant published from lab.foundry.PublishAgent"));

        System.out.println("published: " + AGENT_NAME);
        System.out.println("version:   " + version.getVersion());
        System.out.println();
        System.out.println("Now open ai.azure.com -> your project -> Agents. It is there.");
        System.out.println("Then run lab.foundry.RunAgent with argument " + version.getVersion());
    }

    private PublishAgent() {
    }
}
