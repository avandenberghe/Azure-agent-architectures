package lab.local;

import com.azure.ai.agents.AgentsClientBuilder;
import lab.agent.Agent;
import lab.shared.Settings;

import java.util.List;

/**
 * Agent with two real tools, where the second call depends on the first.
 *
 * <p>OrderAgent answers one question with one lookup. That is a tool call, not
 * an agent loop. Here the goal is open-ended enough that the model has to decide
 * what to check, in what order, and what the combination means:
 *
 * <pre>    "Can I deploy gpt-5-nano?"</pre>
 *
 * <p>It cannot answer that in one call. Azure keeps availability and entitlement
 * in two unrelated systems: the catalogue says what exists, quota says what you
 * may actually deploy. The model has to consult both and reconcile them.
 *
 * <p>This is where {@link Agent#run}'s loop earns its keep: run with
 * {@code -Dagent.trace=true} to watch each round on stderr.
 *
 * <p>The tools are in OpsTools.java. Both are read-only `az` calls.
 *
 * <p>Counterpart of python/local_agent/ops_agent.py and csharp/LocalAgent.Ops.
 *
 * <p>Usage: {@code OpsAgent} asks the agent; {@code OpsAgent --tools-only} runs
 * the tools without the model, so no tokens are spent.
 */
public final class OpsAgent {

    public static void main(String[] args) {
        Settings.load();

        // Read every setting before anything else, so a missing one stops the
        // program here, not in the middle of a tool call after an inference
        // round trip. Python gets this for free: ops_agent.py reads them at
        // import time.
        var tools = new OpsTools(
                Settings.required("AZURE_FOUNDRY_RESOURCE"),
                Settings.required("AZURE_RESOURCE_GROUP"),
                Settings.required("AZURE_REGION"));
        String endpoint = Settings.required("FOUNDRY_PROJECT_ENDPOINT");
        String model = Settings.required("FOUNDRY_MODEL");

        // The tools are ordinary methods, so they can be exercised without
        // spending tokens. Python does this with `python -c "import ops_agent; ..."`;
        // a compiled program has no such side door, so it gets a flag instead.
        if (List.of(args).contains("--tools-only")) {
            System.out.println(tools.checkQuota("gpt-5-nano"));
            System.out.println(tools.listDeployments());
            return;
        }

        var project = new AgentsClientBuilder()
                .endpoint(endpoint)
                .credential(Settings.credential());

        Agent agent = Agent.local(project,
                model,
                "OpsAgent",
                "You help with Azure Foundry deployments. "
                        + "Check the facts with your tools before answering; never guess "
                        + "whether something is deployable. A quota limit of 0 means no "
                        + "allocation exists, which is different from the model being absent.",
                tools.tools());

        System.out.println(agent.run(
                "I want to deploy gpt-5-nano in this project. Can I? "
                        + "If not, tell me what I already have that I could use instead."));
    }

    private OpsAgent() {
    }
}
