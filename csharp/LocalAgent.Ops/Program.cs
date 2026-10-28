// Agent with two real tools, where the second call depends on the first.
//
// LocalAgent.Order answers one question with one lookup. That is a tool call,
// not an agent loop. Here the goal is open-ended enough that the model has to
// decide what to check, in what order, and what the combination means:
//
//     "Can I deploy gpt-5-nano?"
//
// It cannot answer that in one call. Azure keeps availability and entitlement
// in two unrelated systems — the catalogue says what exists, quota says what
// you may actually deploy. The model has to consult both and reconcile them.
//
// The tools are in OpsTools.cs. Both are read-only `az` calls.
//
// Counterpart of python/local_agent/ops_agent.py.
//
// Usage:
//     dotnet run --project csharp/LocalAgent.Ops                  # ask the agent
//     dotnet run --project csharp/LocalAgent.Ops -- --tools-only  # tools only, no tokens

using AgentArchitectures.Shared;
using Azure.AI.Projects;
using Microsoft.Agents.AI;
using Microsoft.Extensions.AI;

Settings.Load();

// Read every setting before anything else, so a missing one stops the program
// here — not in the middle of a tool call, after an inference round trip.
// Python gets this for free: ops_agent.py reads them at import time.
var tools = new OpsTools(
    Settings.Required("AZURE_FOUNDRY_RESOURCE"),
    Settings.Required("AZURE_RESOURCE_GROUP"),
    Settings.Required("AZURE_REGION"));
var endpoint = new Uri(Settings.Required("FOUNDRY_PROJECT_ENDPOINT"));
var model = Settings.Required("FOUNDRY_MODEL");

// The tools are ordinary methods, so they can be exercised without spending
// tokens. Python does this with `python -c "import ops_agent; ..."`; a compiled
// program has no such side door, so it gets a flag instead.
if (args.Contains("--tools-only"))
{
    Console.WriteLine(await tools.CheckQuotaAsync("gpt-5-nano"));
    Console.WriteLine(await tools.ListDeploymentsAsync());
    return;
}

var client = new AIProjectClient(endpoint, Settings.Credential);

AIAgent agent = client.AsAIAgent(
    model: model,
    name: "OpsAgent",
    instructions:
        "You help with Azure Foundry deployments. " +
        "Check the facts with your tools before answering; never guess " +
        "whether something is deployable. A quota limit of 0 means no " +
        "allocation exists, which is different from the model being absent.",
    tools:
    [
        AIFunctionFactory.Create(tools.ListDeploymentsAsync),
        AIFunctionFactory.Create(tools.CheckQuotaAsync),
    ]);

var result = await agent.RunAsync(
    "I want to deploy gpt-5-nano in this project. Can I? " +
    "If not, tell me what I already have that I could use instead.");
Console.WriteLine(result.Text);
