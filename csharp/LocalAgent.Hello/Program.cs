// Minimal Foundry agent — verifies the whole chain works end to end.
//
// Counterpart of python/local_agent/hello_agent.py.

using AgentArchitectures.Shared;
using Azure.AI.Projects;
using Microsoft.Agents.AI;

Settings.Load(); // not automatic — must be called

// The client is your connection to the Foundry project.
var client = new AIProjectClient(
    new Uri(Settings.Required("FOUNDRY_PROJECT_ENDPOINT")), // where
    Settings.Credential);                                   // who you are

// The agent wraps the client with a standing brief.
AIAgent agent = client.AsAIAgent(
    model: Settings.Required("FOUNDRY_MODEL"),              // which deployment
    name: "HelloAgent",
    instructions: "You are a concise, friendly assistant.");

var result = await agent.RunAsync("What is the largest city in Belgium?");
Console.WriteLine(result.Text);
