package lab.local;

import com.azure.ai.agents.AgentsClientBuilder;
import lab.agent.Agent;
import lab.shared.Settings;

import java.util.List;

/**
 * Minimal Foundry agent: verifies the whole chain works end to end.
 *
 * <p>Counterpart of python/local_agent/hello_agent.py and csharp/LocalAgent.Hello.
 */
public final class HelloAgent {

    public static void main(String[] args) {
        Settings.load(); // not automatic: must be called

        // The project connection: where, and who you are. One builder serves
        // both the model calls and, in the foundry steps, the agent service.
        var project = new AgentsClientBuilder()
                .endpoint(Settings.required("FOUNDRY_PROJECT_ENDPOINT")) // where
                .credential(Settings.credential());                      // who you are

        // The agent wraps the connection with a standing brief. Agent is this
        // lab's own class (lab/agent/Agent.java): Java's SDK doesn't have one.
        Agent agent = Agent.local(project,
                Settings.required("FOUNDRY_MODEL"),                       // which deployment
                "HelloAgent",
                "You are a concise, friendly assistant.",
                List.of());                                               // no tools

        System.out.println(agent.run("What is the largest city in Belgium?"));
    }

    private HelloAgent() {
    }
}
