package lab.foundry;

import com.azure.ai.agents.AgentsClientBuilder;
import com.openai.errors.BadRequestException;
import com.openai.errors.NotFoundException;
import lab.agent.Agent;
import lab.shared.Settings;

/**
 * Connect to the published agent and hold a two-turn conversation.
 *
 * <p>Two things here that the lab.local programs cannot do:
 *
 * <ol>
 *   <li>The agent is not defined in this file. It is referenced by name and
 *       version. The instructions live in Foundry: edit them in the portal,
 *       re-run this, and the behaviour changes without touching code.</li>
 *   <li>The conversation has a session. Turn two knows what turn one said, and
 *       that memory is held by the service, not by a variable in this process.</li>
 * </ol>
 *
 * <p>Counterpart of python/foundry_native/run_agent.py and csharp/FoundryNative.Run.
 *
 * <p>Usage: {@code RunAgent [version]}, default 1.
 */
public final class RunAgent {

    public static void main(String[] args) {
        Settings.load();

        String version = args.length > 0 ? args[0] : "1";

        var project = new AgentsClientBuilder()
                .endpoint(Settings.required("FOUNDRY_PROJECT_ENDPOINT"))
                .credential(Settings.credential());

        // No instructions, no model, no tools defined here: only a reference.
        // Everything that shapes behaviour lives in the project. Same Agent class
        // as the local steps, so the same run() call works for both.
        Agent agent = Agent.remote(project, PublishAgent.AGENT_NAME, version);

        // Creates a conversation on the server; this process keeps only its id.
        Agent.Session session = agent.createSession();

        String first;
        try {
            first = agent.run("I am setting up a lab in the swedencentral region. Note that.", session);
        } catch (NotFoundException | BadRequestException e) {
            // 400 also covers other bad requests, so pass on the service's own reason.
            System.err.println(PublishAgent.AGENT_NAME + " version " + version + " could not be used ("
                    + e.getMessage().lines().findFirst().orElse("") + "). "
                    + "If it doesn't exist, run lab.foundry.PublishAgent first or pass an existing version.");
            System.exit(1);
            return;
        }
        System.out.println("turn 1: " + first);
        System.out.println();

        // The proof. This question is unanswerable without the previous turn, and
        // nothing in this file is holding that history.
        String second = agent.run("Which region did I just say I was using?", session);
        System.out.println("turn 2: " + second);
    }

    private RunAgent() {
    }
}
