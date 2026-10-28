// List and delete the published agent.
//
// Publishing creates a resource that outlives the process — which is the
// point, and also the reason this program exists. Anything you create, you
// should be able to remove in one command.
//
// Counterpart of python/foundry_native/cleanup.py. Each language publishes its
// own agent, so this only ever deletes lab-assistant-csharp.
//
// Usage:
//     dotnet run --project csharp/FoundryNative.Cleanup               # list versions, delete nothing
//     dotnet run --project csharp/FoundryNative.Cleanup -- --delete   # delete the agent and all its versions

using System.ClientModel;
using AgentArchitectures.Shared;
using Azure.AI.Projects;

const string AgentName = "lab-assistant-csharp";

Settings.Load();

var projects = new AIProjectClient(
    new Uri(Settings.Required("FOUNDRY_PROJECT_ENDPOINT")),
    Settings.Credential);
var agents = projects.AgentAdministrationClient;

Console.WriteLine("agents in this project:");
await foreach (var a in agents.GetAgentsAsync())
    Console.WriteLine($"  {a.Name}");

Console.WriteLine($"\nversions of {AgentName}:");
try
{
    await foreach (var v in agents.GetAgentVersionsAsync(AgentName))
        Console.WriteLine($"  version {v.Version}  {v.Description}");
}
catch (ClientResultException ex)
{
    Console.WriteLine($"  could not list: {ex.Message}");
}

if (!args.Contains("--delete"))
{
    Console.WriteLine("\nnothing deleted. pass --delete to remove the agent.");
    return 0;
}

try
{
    await agents.DeleteAgentAsync(AgentName);
}
catch (ClientResultException ex) when (ex.Status == 404)
{
    Console.WriteLine($"\nnothing to delete: {AgentName} does not exist.");
    return 1;
}
Console.WriteLine($"\ndeleted: {AgentName}");
return 0;
