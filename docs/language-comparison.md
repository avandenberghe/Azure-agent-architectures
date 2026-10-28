# Language comparison

The same lab, written in more than one language. Each section below takes one lab
step and puts the lines that carry the pattern side by side.

- **Same concept** is the agent pattern itself. It survives a change of language,
  so it's what's worth learning.
- **Language-specific** is what changes with the language or its SDK.

Languages so far: **Python** (`python/`) and **C#** (`csharp/`). Java and PHP will
be added as a new column in the table and a new block in each section.

## Concept map

| Concept | Python (`agent_framework.foundry`) | C# (`Microsoft.Agents.AI.Foundry`) |
|---|---|---|
| Project connection | `FoundryChatClient(project_endpoint=, model=, credential=)` | `new AIProjectClient(uri, credential)` |
| Local agent (in-process) | `client.as_agent(name=, instructions=, tools=)` | `client.AsAIAgent(model:, name:, instructions:, tools:)` → `AIAgent` |
| Run one turn | `await agent.run(prompt)` → `.text` | `await agent.RunAsync(prompt)` → `.Text` |
| Function tool | a plain function, passed in `tools=` | a method, wrapped with `AIFunctionFactory.Create(...)` |
| Tool schema comes from | type hints, `Annotated[..., Field(description=)]`, docstring | parameter types, `[Description]` attributes |
| Versioned definition | `to_prompt_agent(local_agent)` (experimental) | `new DeclarativeAgentDefinition(model) { Instructions = ... }` |
| Publish a version | `AIProjectClient.agents.create_version(agent_name=, definition=)` | `AgentAdministrationClient.CreateAgentVersionAsync(name, options)` |
| Server-side agent | `FoundryAgent(agent_name=, agent_version=)` | `client.AsAIAgent(new AgentReference(name, version))` → `AIAgent` |
| Session | `agent.create_session()`, `run(..., session=)` | `await agent.CreateSessionAsync()`, `RunAsync(..., session)` |
| List / delete | `agents.list()`, `list_versions()`, `delete()` | `GetAgentsAsync()`, `GetAgentVersionsAsync()`, `DeleteAgentAsync()` |
| Config | `load_dotenv()`: walks up, never overrides | `Env.NoClobber().TraversePath().Load()`: same behaviour |
| Credential | `AzureCliCredential()` | `new AzureCliCredential()` |
| Version pinning | `requirements.txt` (direct) + `constraints.txt` (transitive) | `Directory.Packages.props` (direct) + `packages.lock.json` (transitive) |
| Server-side agent name | `lab-assistant` | `lab-assistant-csharp` |

## 1. Hello: one client, one agent, one question

```python
client = FoundryChatClient(
    project_endpoint=os.environ["FOUNDRY_PROJECT_ENDPOINT"],
    model=os.environ["FOUNDRY_MODEL"],
    credential=AzureCliCredential(),
)
agent = client.as_agent(name="HelloAgent", instructions="You are a concise, friendly assistant.")
result = await agent.run("What is the largest city in Belgium?")
print(result.text)
```

```csharp
var client = new AIProjectClient(
    new Uri(Settings.Required("FOUNDRY_PROJECT_ENDPOINT")),
    Settings.Credential);
AIAgent agent = client.AsAIAgent(
    model: Settings.Required("FOUNDRY_MODEL"),
    name: "HelloAgent",
    instructions: "You are a concise, friendly assistant.");
var result = await agent.RunAsync("What is the largest city in Belgium?");
Console.WriteLine(result.Text);
```

**Same concept.** The pattern has three parts: *where* (the project endpoint), *which
model* (the deployment) and *who you are* (a credential). The agent wraps those
with a standing brief, its instructions. Nothing is created in Foundry, and the
agent stops existing when the process exits.

**Language-specific.**
- Python attaches the model to the client. C# passes it to the agent, so one
  `AIProjectClient` can serve agents on different deployments.
- Both are `async`. Python needs `asyncio.run(main())`; C# top-level statements
  allow `await` directly.
- Python reads `os.environ[...]` and raises `KeyError` if a value is missing. C#
  uses `Settings.Required`, which stops with a readable message.

## 2. Order: one tool, one call

```python
def get_order_status(
    order_id: Annotated[str, Field(description="Order ID, format ORD-12345")],
) -> str:
    """Look up the current delivery status of a customer order."""
    return f"{order_id}: shipped, tracking BE998877, arriving 8 August"

agent = client.as_agent(name="OrderAgent", instructions=..., tools=get_order_status)
```

```csharp
[Description("Look up the current delivery status of a customer order.")]
static string GetOrderStatus(
    [Description("Order ID, format ORD-12345")] string orderId)
    => $"{orderId}: shipped, tracking BE998877, arriving 8 August";

AIAgent agent = client.AsAIAgent(..., tools: [AIFunctionFactory.Create(GetOrderStatus)]);
```

**Same concept.** A tool is an ordinary function. The model sees only its
*schema*: the name, the parameter types and the descriptions. It decides on its
own whether to call it. The framework runs the call loop: model asks → your code
runs → result goes back → model answers.

**Language-specific.**
- Python builds the schema from type hints, `pydantic.Field` and the docstring,
  so the docstring is part of the API. C# builds it by reflection from parameter
  types and `[Description]` attributes, so comments don't reach the model.
- Python accepts a bare function. C# wraps the method explicitly
  (`AIFunctionFactory.Create`), which makes the step from method to tool visible.

## 3. Ops: two chained tools, an actual loop

```python
def check_quota(model: Annotated[str, Field(description="...")]) -> str:
    """Check deployment quota for a model. ..."""
    return _az("cognitiveservices", "usage", "list", "-l", REGION, "--query", ...)

def _az(*args):
    try:
        done = subprocess.run(["az", *args], capture_output=True, text=True, timeout=60)
    except FileNotFoundError:
        return "ERROR: 'az' not found on PATH."
```

```csharp
[Description("Check deployment quota for a model. ...")]
public static Task<string> CheckQuotaAsync([Description("...")] string model)
    => AzAsync("cognitiveservices", "usage", "list", "-l", Region, "--query", ...);

static async Task<string> AzAsync(params string[] args)
{
    var start = new ProcessStartInfo("az") { RedirectStandardOutput = true, RedirectStandardError = true };
    foreach (var arg in args) start.ArgumentList.Add(arg);
    try { process = Process.Start(start)!; }
    catch (Win32Exception) { return "ERROR: 'az' not found on PATH."; }
    ...
}
```

**Same concept.** The second tool call depends on what the first one returned.
That dependency is what makes it an agent loop rather than a single tool call.
Tools *return* their errors as text instead of throwing, so the model can read a
failure and work around it. Each tool call costs another inference round trip.

**Language-specific.**
- Python's tools are synchronous. The C# tools are `async Task<string>`, and the
  framework awaits them.
- Both pass arguments as a list (`["az", *args]` / `ArgumentList`), never as a
  shell string. Same reason in both languages: no quoting bugs, no injection.
- **Exercising the tools without spending tokens.** Python can import the module
  and call a function: `python -c "import ops_agent; ..."`. A compiled program
  has no such side door, so C# has a flag instead:
  `dotnet run --project csharp/LocalAgent.Ops -- --tools-only`.
- The scenario has changed since the lab was written. On 2026-08-06 the
  gpt-5-nano quota was 0. By 2026-10-28 it was granted, and both versions
  correctly answer "yes".

## 4. Publish: the agent becomes a project resource

```python
local_agent = client.as_agent(name=AGENT_NAME, instructions="...")
definition = to_prompt_agent(local_agent)          # experimental
version = projects.agents.create_version(
    agent_name=AGENT_NAME, definition=definition, description="...")
```

```csharp
var definition = new DeclarativeAgentDefinition(Settings.Required("FOUNDRY_MODEL"))
{
    Instructions = "...",
};
var version = (await projects.AgentAdministrationClient.CreateAgentVersionAsync(
    AgentName,
    new ProjectsAgentVersionCreationOptions(definition) { Description = "..." })).Value;
```

**Same concept.** A prompt agent is *declarative*: a model reference plus
instructions, stored in the project. Publishing it is a control-plane write, not an
inference call. Publishing again creates a new version instead of overwriting the
old one, which is what makes rollback possible.

**Language-specific.**
- Python builds a local agent first and converts it with `to_prompt_agent()`. C#
  has no such helper, so you write the definition directly. That is more typing,
  but it is exactly the object Foundry stores.
- Microsoft's docs call this a "prompt agent". In the stable C# package
  (`Azure.AI.Projects.Agents` 2.0.0) the type is named `DeclarativeAgentDefinition`.
- **One agent per language.** Python publishes `lab-assistant` and C# publishes
  `lab-assistant-csharp`, so deleting one never breaks the other. Nothing ties
  an agent to a language, though: it is a project resource, and C#'s `Run` could
  reference Python's agent by name just as well.

## 5. Run: connect by name and version, hold a session

```python
agent = FoundryAgent(project_endpoint=..., agent_name=AGENT_NAME,
                     agent_version=version, credential=AzureCliCredential())
session = agent.create_session()
first = await agent.run("I am setting up a lab in the swedencentral region. Note that.", session=session)
second = await agent.run("Which region did I just say I was using?", session=session)
```

```csharp
AIAgent agent = projects.AsAIAgent(new AgentReference(AgentName, version));
var session = await agent.CreateSessionAsync();
var first = await agent.RunAsync("I am setting up a lab in the swedencentral region. Note that.", session);
var second = await agent.RunAsync("Which region did I just say I was using?", session);
```

**Same concept.** This file contains no instructions, no model and no tools, only
a reference to the agent. Edit the agent in the portal and the behaviour changes
without touching code. The service holds the session, which is why turn two
can answer a question that only turn one makes answerable. Each turn replays the
growing history, so it is billed in tokens.

**Language-specific.**
- C# returns the same `AIAgent` type for a local agent and a server-side one. The
  concrete `FoundryAgent` class is marked evaluation-only (`OPENAI001`), so the C#
  code holds the abstraction. Python names `FoundryAgent` directly.
- C# creates the session asynchronously. In Python it's a plain call.

## 6. Cleanup: what you create, you can remove

```python
for a in projects.agents.list(): print(f"  {a.name}")
for v in projects.agents.list_versions(agent_name=AGENT_NAME): ...
if "--delete" in sys.argv: projects.agents.delete(agent_name=AGENT_NAME)
```

```csharp
await foreach (var a in agents.GetAgentsAsync()) Console.WriteLine($"  {a.Name}");
await foreach (var v in agents.GetAgentVersionsAsync(AgentName)) ...
if (args.Contains("--delete")) await agents.DeleteAgentAsync(AgentName);
```

**Same concept.** Listing is safe. Deleting removes the agent and every one of
its versions. Without `--delete` nothing is deleted.

**Language-specific.**
- Python iterates paged results with a plain `for`. C# uses `await foreach`
  over `IAsyncEnumerable`, so each page is fetched lazily as the loop needs it.
- C# reports a missing agent as a normal message with exit code 1
  (`ClientResultException` with status 404). Python lets the exception surface.
- Listing the versions of an agent that doesn't exist returns an empty list in
  both languages, not an error.

## Cross-cutting lessons

- **Pin everything, including what you don't import.** On 2026-10-28, a fresh
  Python install that pinned only its direct dependencies pulled in `openai` 3.x
  with `httpx2`, and `azure-ai-projects` failed to import. The fix in Python is
  `constraints.txt`; in C# it is `packages.lock.json`. Same lesson in both.
- **IDE launched from the Dock doesn't see your shell's PATH.** `AzureCliCredential`
  shells out to `az`, so even Hello fails to authenticate there. PyCharm needs PATH
  in its run configuration. In Rider, each project's `launchSettings.json` sets it.
- **429 is capacity, not a bug.** At 3K TPM, running two agents back to back is
  enough to hit the limit. Wait a minute.
