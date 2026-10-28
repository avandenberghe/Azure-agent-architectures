# Language comparison

The same lab, written in more than one language. Each section below takes one lab
step and puts the lines that carry the pattern side by side.

- **Same concept** is the agent pattern itself. It survives a change of language,
  so it's what's worth learning.
- **Language-specific** is what changes with the language or its SDK.

Languages so far: **Python** (`python/`), **C#** (`csharp/`) and **Java** (`java/`).
PHP will be added as a new column in the table and a new block in each section.

Java is the odd one out, on purpose. Microsoft's Agent Framework (used by Python
and C#) has no Java SDK, so the Java version writes its own agent class and tool
loop ([`java/src/main/java/lab/agent/Agent.java`](../java/src/main/java/lab/agent/Agent.java)).
Read that file to see what the frameworks do for you in the other two.

## Concept map

| Concept | Python (`agent_framework.foundry`) | C# (`Microsoft.Agents.AI.Foundry`) | Java (`com.azure:azure-ai-agents` + own `Agent`) |
|---|---|---|---|
| Project connection | `FoundryChatClient(project_endpoint=, model=, credential=)` | `new AIProjectClient(uri, credential)` | `new AgentsClientBuilder().endpoint(...).credential(...)` |
| Local agent (in-process) | `client.as_agent(name=, instructions=, tools=)` | `client.AsAIAgent(model:, name:, instructions:, tools:)` → `AIAgent` | `Agent.local(project, model, name, instructions, tools)` (the lab's own class) |
| Run one turn | `await agent.run(prompt)` → `.text` | `await agent.RunAsync(prompt)` → `.Text` | `agent.run(prompt)` → `String` (synchronous) |
| Function tool | a plain function, passed in `tools=` | a method, wrapped with `AIFunctionFactory.Create(...)` | `new Tool(name, description, schema, args -> ...)` (the lab's own record) |
| Tool schema comes from | type hints, `Annotated[..., Field(description=)]`, docstring | parameter types, `[Description]` attributes | written by hand as JSON Schema (`Tool.stringParams(...)`) |
| Versioned definition | `to_prompt_agent(local_agent)` (experimental) | `new DeclarativeAgentDefinition(model) { Instructions = ... }` | `new PromptAgentDefinition(model).setInstructions(...)` |
| Publish a version | `AIProjectClient.agents.create_version(agent_name=, definition=)` | `AgentAdministrationClient.CreateAgentVersionAsync(name, options)` | `AgentsClient.createAgentVersion(name, new CreateAgentVersionInput(def))` |
| Server-side agent | `FoundryAgent(agent_name=, agent_version=)` | `client.AsAIAgent(new AgentReference(name, version))` → `AIAgent` | `Agent.remote(project, name, version)`; per call: `AgentReference(name).setVersion(v)` |
| Session | `agent.create_session()`, `run(..., session=)` | `await agent.CreateSessionAsync()`, `RunAsync(..., session)` | `agent.createSession()` (a server conversation), `run(..., session)` |
| List / delete | `agents.list()`, `list_versions()`, `delete()` | `GetAgentsAsync()`, `GetAgentVersionsAsync()`, `DeleteAgentAsync()` | `listAgents()`, `listAgentVersions()`, `deleteAgent()` |
| Config | `load_dotenv()`: walks up from the *script's* folder, never overrides | `Env.NoClobber().TraversePath().Load()`: walks up from the *working* folder, never overrides | `Settings.load()`: walks up from the *working* folder; real environment wins over `.env` |
| Credential | `AzureCliCredential()` | `new AzureCliCredential()` | `new AzureCliCredentialBuilder().build()` |
| Version pinning | `requirements.txt` (direct) + `constraints.txt` (transitive) | `Directory.Packages.props` (direct) + `packages.lock.json` (transitive) | exact versions in `pom.xml`; Maven resolution is already reproducible |
| Server-side agent name | `lab-assistant` | `lab-assistant-csharp` | `lab-assistant-java` |

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

```java
var project = new AgentsClientBuilder()
        .endpoint(Settings.required("FOUNDRY_PROJECT_ENDPOINT"))
        .credential(Settings.credential());
Agent agent = Agent.local(project, Settings.required("FOUNDRY_MODEL"),
        "HelloAgent", "You are a concise, friendly assistant.", List.of());
System.out.println(agent.run("What is the largest city in Belgium?"));
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
- Java has no agent class in its SDK. `Agent` is the lab's own, about 150
  lines of code; `Agent.local(...)` takes the same ingredients as the other two.
- Java is synchronous here. No `async`/`await`; `run()` blocks until the answer
  arrives.
- One `AgentsClientBuilder` is the project connection for everything: model
  calls, conversations and the agent service.

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

```java
var orderStatus = new Tool(
        "get_order_status",
        "Look up the current delivery status of a customer order.",
        Tool.stringParams(new Tool.Param("order_id", "Order ID, format ORD-12345")),
        arguments -> getOrderStatus((String) arguments.get("order_id")));

Agent agent = Agent.local(project, model, "OrderAgent", "...", List.of(orderStatus));
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
- Java writes the schema out by hand: name, description and a JSON Schema
  object. Nothing is inferred from the method. More typing, but it is exactly
  what the model receives, which the other two never show you.
- The handler gets the arguments as a parsed JSON map and returns text.

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
sealed class OpsTools(string resource, string group, string region)
{
    [Description("Check deployment quota for a model. ...")]
    public Task<string> CheckQuotaAsync([Description("...")] string model)
        => AzAsync("cognitiveservices", "usage", "list", "-l", region, "--query", ...);
}

static async Task<string> AzAsync(params string[] args)
{
    var start = new ProcessStartInfo("az") { RedirectStandardOutput = true, RedirectStandardError = true };
    foreach (var arg in args) start.ArgumentList.Add(arg);
    try { process = Process.Start(start)!; }
    catch (Win32Exception) { return "ERROR: 'az' not found on PATH."; }
    ...
}
```

```java
// Agent.run(): the loop the frameworks hide (abridged)
for (int round = 1; round <= MAX_ROUNDS; round++) {
    Response response = send(input, session);                // 1. ask the model
    List<ResponseFunctionToolCall> calls = toolCalls(response); // 2. tools requested?
    if (calls.isEmpty()) return text(response);              // 3. no: that's the answer
    // 4. keep the history consistent, 5. run each tool, add its result
    for (ResponseFunctionToolCall call : calls) input.add(functionCallOutput(call, invoke(call)));
}                                                            // 6. ask again
throw new IllegalStateException("gave up after " + MAX_ROUNDS + " rounds");
```

**Same concept.** The second tool call depends on what the first one returned.
That dependency is what makes it an agent loop rather than a single tool call.
Tools *return* their errors as text instead of throwing, so the model can read a
failure and work around it. Each tool call costs another inference round trip.

**Language-specific.**
- Python reads its settings at import time, so a missing one fails before any
  inference. C# gets the same behaviour by reading them in `Program.cs` and
  passing them to an `OpsTools` instance; the tools are then instance methods.
- Python's tools are synchronous. The C# tools are `async Task<string>`, and the
  framework awaits them.
- Both pass arguments as a list (`["az", *args]` / `ArgumentList`), never as a
  shell string, so there is no shell injection. The model-supplied `model` is
  still interpolated into the JMESPath `--query` in both languages: a tool
  argument is untrusted input, wherever it ends up.
- **Exercising the tools without spending tokens.** Python can import the module
  and call a function: `python -c "import ops_agent; ..."`. A compiled program
  has no such side door, so C# has a flag instead:
  `dotnet run --project csharp/LocalAgent.Ops -- --tools-only`.
- The scenario has changed since the lab was written. On 2026-08-06 the
  gpt-5-nano quota was 0. By 2026-10-28 it had been granted, and the C# run on
  that date correctly answered "yes". Python reads the same data, so it should
  give the same answer, but nobody has rerun it since.
- **Java shows the loop.** In Python and C# the framework runs it inside
  `run()` / `RunAsync()`. In Java it's
  [`Agent.run()`](../java/src/main/java/lab/agent/Agent.java): ask, collect tool
  calls, run them, send results back, repeat. Run with `-Dagent.trace=true` to see
  each round. On 2026-10-28 the model asked for **both** tools in one round
  (quota exists now, so it no longer has to wait for the first answer), then
  answered in round 2.
- `MAX_ROUNDS` caps the loop. The frameworks have an equivalent limit you never
  set.
- Without a session the client holds the history and resends it every round
  (`store(false)`), so each tool round costs more tokens than the last.
- `--tools-only` works like the C# flag. `ProcessBuilder` with a list of
  arguments: no shell, same as the other two.

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

```java
var definition = new PromptAgentDefinition(Settings.required("FOUNDRY_MODEL"))
        .setInstructions("...");
AgentVersionDetails version = agents.createAgentVersion(AGENT_NAME,
        new CreateAgentVersionInput(definition).setDescription("..."));
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
- Java's type is `PromptAgentDefinition`, the name Microsoft's docs use.
  C#'s stable package calls the same thing `DeclarativeAgentDefinition`.
- Like C#, the definition is written directly; there is no `to_prompt_agent()`.

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

```java
Agent agent = Agent.remote(project, AGENT_NAME, version);
Agent.Session session = agent.createSession();
String first = agent.run("I am setting up a lab in the swedencentral region. Note that.", session);
String second = agent.run("Which region did I just say I was using?", session);

// inside Agent: the version is chosen per call, like Python and C#
var options = new AzureCreateResponseOptions()
        .setAgentReference(new AgentReference(name).setVersion(version));
responses.createAzureResponse(options, request.conversation(session.conversationId()));
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
- The session is a server-side **conversation**; the process keeps only its id.
  With a session, each tool round sends only the new results, because the
  conversation already holds the rest.
- Microsoft's only Java sample for pinning a version rewrites the agent's
  endpoint settings (`updateAgentDetails`), which changes the version *every*
  caller gets. Per-call `AgentReference` avoids that; the lab uses it.
- The same `Agent` class serves local and remote agents, like C#'s `AIAgent`.

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

```java
for (AgentDetails a : agents.listAgents()) System.out.println("  " + a.getName());
for (AgentVersionDetails v : agents.listAgentVersions(name)) ...
if (List.of(args).contains("--delete")) agents.deleteAgent(name);
```

**Same concept.** Listing is safe. Deleting removes the agent and every one of
its versions. Without `--delete` nothing is deleted.

**Language-specific.**
- Both fetch pages lazily as the loop advances. Only the syntax differs: a plain
  `for` over `ItemPaged` in Python, `await foreach` over `IAsyncEnumerable` in C#.
- C# is written to report a missing agent as a normal message with exit code 1
  (`ClientResultException` with status 404). That path hasn't been exercised
  against the live service yet. Python lets the exception surface.
- Listing the versions of an agent that doesn't exist returns an empty list in
  both languages, not an error.
- `PagedIterable` fetches pages lazily, like the other two; Java iterates it
  with a plain `for`.
- A missing agent on `--delete` is caught (`ResourceNotFoundException`) and
  reported with exit code 1, like C#.

## Cross-cutting lessons

- **Pin everything, including what you don't import.** On 2026-10-28, a fresh
  Python install that pinned only its direct dependencies pulled in `openai` 3.x
  with `httpx2`, and `azure-ai-projects` failed to import. The fix in Python is
  `constraints.txt`; in C# it is `packages.lock.json`. Java's Maven needs no
  extra file: with exact versions in `pom.xml` and no version ranges in the
  libraries, resolution is already reproducible.
- **The framework is a loop.** Java's hand-written `Agent` shows what Python's
  `Agent` and C#'s `AIAgent` do: a bounded loop of model calls and tool runs,
  with the history held either by the client or by a server conversation.
- **IDE launched from the Dock doesn't see your shell's PATH.** `AzureCliCredential`
  shells out to `az`, so even Hello fails to authenticate there. PyCharm needs PATH
  in its run configuration. In Rider, each project's `launchSettings.json` sets it;
  in IntelliJ, the configurations in `java/.run/`.
- **429 is capacity, not a bug.** At 3K TPM, running two agents back to back is
  enough to hit the limit. Wait a minute.
