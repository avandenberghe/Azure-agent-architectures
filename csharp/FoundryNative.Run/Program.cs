// Connect to the published agent and hold a two-turn conversation.
//
// Two things here that the LocalAgent.* programs cannot do:
//
// 1. The agent is not defined in this file. It is referenced by name and
//    version. The instructions live in Foundry — edit them in the portal,
//    re-run this, and the behaviour changes without touching code.
//
// 2. The conversation has a session. Turn two knows what turn one said, and
//    that memory is held by the service, not by a variable in this process.
//
// Counterpart of python/foundry_native/run_agent.py.
//
// Usage:  dotnet run --project csharp/FoundryNative.Run -- [version]

using System.ClientModel;
using AgentArchitectures.Shared;
using Azure.AI.Extensions.OpenAI;
using Azure.AI.Projects;
using Microsoft.Agents.AI;

const string AgentName = "lab-assistant-csharp";

Settings.Load();

var version = args.Length > 0 ? args[0] : "1";

var projects = new AIProjectClient(
    new Uri(Settings.Required("FOUNDRY_PROJECT_ENDPOINT")),
    Settings.Credential);

// No instructions, no model, no tools defined here — only a reference.
// Everything that shapes behaviour lives in the project.
// The concrete type is FoundryAgent, but the library marks it evaluation-only
// (OPENAI001). Holding it as AIAgent avoids that, and shows the point: a
// server-side agent has the same interface as the local ones.
AIAgent agent = projects.AsAIAgent(new AgentReference(AgentName, version));

var session = await agent.CreateSessionAsync();

AgentResponse first;
try
{
    first = await agent.RunAsync(
        "I am setting up a lab in the swedencentral region. Note that.", session);
}
catch (ClientResultException ex) when (ex.Status is 400 or 404)
{
    Console.Error.WriteLine(
        $"{AgentName} version {version} not found. " +
        "Run FoundryNative.Publish first, or pass an existing version.");
    return 1;
}
Console.WriteLine($"turn 1: {first.Text}");
Console.WriteLine();

// The proof. This question is unanswerable without the previous turn, and
// nothing in this file is holding that history.
var second = await agent.RunAsync("Which region did I just say I was using?", session);
Console.WriteLine($"turn 2: {second.Text}");
return 0;
