package lab.foundry;

import com.azure.ai.agents.AgentsClient;
import com.azure.ai.agents.AgentsClientBuilder;
import com.azure.ai.agents.models.AgentDetails;
import com.azure.ai.agents.models.AgentVersionDetails;
import com.azure.core.exception.HttpResponseException;
import com.azure.core.exception.ResourceNotFoundException;
import lab.shared.Settings;

import java.util.List;

/**
 * List and delete the published agent.
 *
 * <p>Publishing creates a resource that outlives the process, which is the
 * point, and also the reason this program exists. Anything you create, you
 * should be able to remove in one command.
 *
 * <p>Counterpart of python/foundry_native/cleanup.py and csharp/FoundryNative.Cleanup.
 * Each language publishes its own agent, so this only ever deletes lab-assistant-java.
 *
 * <p>Usage: {@code Cleanup} lists versions and deletes nothing;
 * {@code Cleanup --delete} deletes the agent and all its versions.
 */
public final class Cleanup {

    public static void main(String[] args) {
        Settings.load();
        String name = PublishAgent.AGENT_NAME;

        AgentsClient agents = new AgentsClientBuilder()
                .endpoint(Settings.required("FOUNDRY_PROJECT_ENDPOINT"))
                .credential(Settings.credential())
                .buildAgentsClient();

        // PagedIterable fetches further pages lazily as the loop advances.
        System.out.println("agents in this project:");
        for (AgentDetails a : agents.listAgents()) {
            System.out.println("  " + a.getName());
        }

        System.out.println();
        System.out.println("versions of " + name + ":");
        try {
            for (AgentVersionDetails v : agents.listAgentVersions(name)) {
                String description = v.getDescription() == null ? "" : v.getDescription();
                System.out.println("  version " + v.getVersion() + "  " + description);
            }
        } catch (HttpResponseException e) {
            System.out.println("  could not list: " + e.getMessage());
        }

        if (!List.of(args).contains("--delete")) {
            System.out.println();
            System.out.println("nothing deleted. pass --delete to remove the agent.");
            return;
        }

        try {
            agents.deleteAgent(name);
        } catch (ResourceNotFoundException e) {
            System.err.println();
            System.err.println("nothing to delete: " + name + " does not exist.");
            System.exit(1);
        }
        System.out.println();
        System.out.println("deleted: " + name);
    }

    private Cleanup() {
    }
}
