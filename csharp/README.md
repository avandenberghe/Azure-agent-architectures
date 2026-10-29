# Foundry agent lab: C#

The C# version of the lab: the same six steps as the [Python](../python/README.md)
and [Java](../java/README.md) versions, with the same prompts, instructions and output. The project overview is in the
[root README](../README.md). [`docs/language-comparison.md`](../docs/language-comparison.md)
puts each step side by side across the languages.

Built on .NET 10, `Microsoft.Agents.AI.Foundry` and `Azure.AI.Projects`.

## Layout

| Project | Python counterpart | What it is |
|---|---|---|
| `LocalAgent.Hello` | `hello_agent.py` | One client, one agent, one question. No tools |
| `LocalAgent.Order` | `order_agent.py` | One tool registered with `AIFunctionFactory.Create`. The tool returns a fixed string |
| `LocalAgent.Ops` | `ops_agent.py` | Two read-only `az` tools (in `OpsTools.cs`) that chain. `--tools-only` runs the tools without the model |
| `FoundryNative.Publish` | `publish_agent.py` | Writes a `DeclarativeAgentDefinition` as a new version of `lab-assistant-csharp` |
| `FoundryNative.Run` | `run_agent.py` | Connects to `lab-assistant-csharp` by name and version and holds a two-turn session |
| `FoundryNative.Cleanup` | `cleanup.py` | Lists agents and versions. `--delete` removes `lab-assistant-csharp` |
| `Shared` | (`load_dotenv`, `_required`) | `Settings.Load()`, `Settings.Required(name)`, `Settings.Credential`. Nothing agent-related |

Solution-wide files:

| File | Purpose |
|---|---|
| `AgentArchitectures.slnx` | The solution, in the XML format that is the .NET 10 default |
| `global.json` | Pins SDK 10.0.400 (rolls forward to newer feature bands) |
| `Directory.Build.props` | `net10.0`, nullable, `TreatWarningsAsErrors`, lock files on |
| `Directory.Packages.props` | Every NuGet version, in one place |
| `*/packages.lock.json` | Every transitive package, pinned. The C# counterpart of `python/constraints.txt` |
| `*/Properties/launchSettings.json` | Puts `/opt/homebrew/bin` on PATH so `az` is found (see below) |

C# publishes its own agent, `lab-assistant-csharp`. Python's is `lab-assistant` and
Java's is `lab-assistant-java`, so cleaning up one never touches the others.

## Setup

Requires the .NET 10 SDK, the Azure CLI, and the filled-in `.env` at the repo root.

On this Mac the SDK lives in `~/.dotnet`, not Homebrew. Put it on your PATH in
`~/.zprofile`:

```zsh
export DOTNET_ROOT="$HOME/.dotnet"
export PATH="$DOTNET_ROOT:$DOTNET_ROOT/tools:$PATH"
export DOTNET_CLI_TELEMETRY_OPTOUT=1
```

Then, from the repo root:

```bash
dotnet --list-sdks                      # expect 10.0.400
dotnet build csharp/AgentArchitectures.slnx
az login --tenant "$AZURE_TENANT_ID"
```

`Settings.Load()` walks up from the **working directory** to find the root `.env`.
That works from the repo root, from `csharp/`, from a project folder, and from
Rider. It doesn't override variables already set in your environment.

## Run

From the repo root:

```bash
dotnet run --project csharp/LocalAgent.Hello
dotnet run --project csharp/LocalAgent.Order
dotnet run --project csharp/LocalAgent.Ops
dotnet run --project csharp/LocalAgent.Ops -- --tools-only      # no tokens spent

dotnet run --project csharp/FoundryNative.Publish               # writes to the project, new version each run
dotnet run --project csharp/FoundryNative.Run -- 1              # version to connect to, default 1
dotnet run --project csharp/FoundryNative.Cleanup               # list only; -- --delete removes it
```

**In Rider:** open `csharp/AgentArchitectures.slnx`. Each project is its own run
configuration. `FoundryNative.Run` runs version 1 by default; change
`commandLineArgs` in its `launchSettings.json` for another version. If Rider doesn't
find the SDK, set Settings → Build, Execution, Deployment → Toolset and Build →
.NET CLI executable to `~/.dotnet/dotnet`.

## C#-specific notes

- **`launchSettings.json` sets PATH, and it matters.** Rider launched from the Dock
  doesn't inherit your shell's PATH. `AzureCliCredential` shells out to `az`, so
  *every* project would fail to sign in, not just Ops. Each profile sets
  `PATH=/opt/homebrew/bin:…`, and `dotnet run` applies it in the terminal too. If
  your `az` lives somewhere else, change it there.
- **Names differ from Microsoft's docs and from older blog posts.** The prompt agent
  definition is `DeclarativeAgentDefinition` (docs: `PromptAgentDefinition`). Admin
  calls go through `AIProjectClient.AgentAdministrationClient` (older: `.Agents`).
  Sessions are `AgentSession` (older: `AgentThread`). There is no
  `to_prompt_agent()`; you write the definition directly.
- **The server-side agent is held as `AIAgent`, not `FoundryAgent`.** Naming the
  `FoundryAgent` type raises `OPENAI001` (evaluation-only), which
  `TreatWarningsAsErrors` turns into a build failure.
  `AsAIAgent(new AgentReference(...))` doesn't raise it. It also shows that a local
  agent and a server-side agent share one interface.
- **The tool schema comes from your signature.** Parameter types and
  `[Description]` attributes are what the model sees. XML doc comments are not.
- **Settings are read at startup.** A missing value stops the program with a
  readable message before any tokens are spent. That's why `OpsTools` is an
  instance built from settings in `Program.cs` rather than a static class.
- **Stable packages on purpose.** `Microsoft.Agents.AI.Foundry` 1.5.0 covers all six
  steps; the 1.26 previews aren't needed. Bump in `Directory.Packages.props`, then
  `dotnet restore --force-evaluate` to refresh the lock files.
- **HTTP 429** means the 3K TPM capacity is used up, not that the code is broken.
  Two agent runs back to back are enough to hit it. Wait a minute.
