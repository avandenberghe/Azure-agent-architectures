// Publish an agent INTO the Foundry project, as a named, versioned resource.
//
// This is the difference the programs in LocalAgent.* do not show. AsAIAgent()
// there builds an object that dies with the process. Here the agent definition
// is sent to Foundry and becomes a resource you can see in the portal, connect
// to from anywhere, and version.
//
// Nothing is containerised or "deployed" in the hosting sense. A prompt agent
// is declarative — a model reference plus instructions — and Foundry's own
// runtime executes it. The existing model deployment is reused, not duplicated.
//
// Counterpart of python/foundry_native/publish_agent.py. Each language
// publishes its own agent; this one is lab-assistant-csharp.
//
// Run this once, then look at ai.azure.com.

using AgentArchitectures.Shared;
using Azure.AI.Projects;
using Azure.AI.Projects.Agents;

const string AgentName = "lab-assistant-csharp";

Settings.Load();

// Step 1 — describe the agent. Python builds a local agent first and converts
// it with to_prompt_agent(); C# has no such helper, so the definition is
// written directly. Arguably clearer: this is exactly what Foundry stores.
var definition = new DeclarativeAgentDefinition(Settings.Required("FOUNDRY_MODEL"))
{
    Instructions =
        "You are a concise assistant for an Azure Foundry lab. " +
        "Answer in at most three sentences. " +
        "If you are unsure, say so rather than guessing.",
};

// Step 2 — the actual write. From here the agent exists independently of
// this process. Re-running creates a NEW version rather than overwriting,
// which is what makes rollback possible.
var projects = new AIProjectClient(
    new Uri(Settings.Required("FOUNDRY_PROJECT_ENDPOINT")),
    Settings.Credential);
var version = (await projects.AgentAdministrationClient.CreateAgentVersionAsync(
    AgentName,
    new ProjectsAgentVersionCreationOptions(definition)
    {
        Description = "Lab assistant published from FoundryNative.Publish",
    })).Value;

Console.WriteLine($"published: {AgentName}");
Console.WriteLine($"version:   {version.Version}");
Console.WriteLine();
Console.WriteLine("Now open ai.azure.com -> your project -> Agents. It is there.");
Console.WriteLine($"Then: dotnet run --project csharp/FoundryNative.Run -- {version.Version}");
