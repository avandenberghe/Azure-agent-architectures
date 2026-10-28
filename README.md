# Foundry agent lab

A demo project for understanding **Microsoft Foundry** — what it is, what it gives
you beyond a model endpoint, and what changes when you actually build around it.

It is not a library, a template, or production code. It exists to be read and run.

## The idea

The same small agent is built **twice**, in two folders, using two different
architectures. Reading them side by side is the point.

The whole lab then exists once per language: `python/` and `csharp/` today, with
Java and PHP to follow. What survives a change of language is the agent pattern;
what changes is the SDK. [`docs/language-comparison.md`](docs/language-comparison.md)
sets them side by side.

| | `local_agent/` · `LocalAgent.*` | `foundry_native/` · `FoundryNative.*` |
|---|---|---|
| Where the agent lives | In your process | In the Foundry project |
| Created by | `as_agent()` / `AsAIAgent(model:)` | `create_version()` / `CreateAgentVersionAsync()` — a real resource |
| Identity | None. An object that dies at exit | Named and versioned, visible in the portal |
| Conversation state | None. Every run starts cold | Service-managed sessions |
| Instructions | In the source file | In the project — editable without touching code |
| Foundry's role | An inference endpoint | The platform the system runs on |

The first version is what the usual tutorial teaches. It works, and it is a fair
illustration of an agent loop — but swap `agent_framework.foundry` for
`agent_framework.openai` and almost nothing changes, which means it is not really
teaching you Foundry. The second version cannot survive that substitution.

## Quick start

Requires the Azure CLI 2.67+ and a Foundry project with a chat model deployment.
Then Python 3.12+ (this lab uses 3.14), the .NET 10 SDK, or both.

```bash
cp .env.example .env      # then fill in your own values — shared by every language
set -a && source .env && set +a

az login --tenant "$AZURE_TENANT_ID"
az account set --subscription "$AZURE_SUBSCRIPTION_ID"
```

`.env` is the only place real identifiers live. Nothing tracked in git contains a
subscription ID, tenant ID or project endpoint. Every language finds it by walking
up from where you run.

### Python setup

```bash
cd python
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt      # pulls in constraints.txt: everything pinned
```

### C# setup

```bash
dotnet --list-sdks                   # needs 10.0.x; csharp/global.json pins 10.0.400
dotnet build csharp/AgentArchitectures.slnx
```

Open `csharp/AgentArchitectures.slnx` in Rider: each lab step is its own run
configuration. Every project's `launchSettings.json` puts `/opt/homebrew/bin` on
PATH, because a Dock-launched IDE doesn't inherit your shell's PATH and
`AzureCliCredential` needs `az`.

### Run the local architecture

```bash
python python/local_agent/hello_agent.py     # one prompt, one reply, no tools
python python/local_agent/order_agent.py     # one tool returning a fixed string
python python/local_agent/ops_agent.py       # two real az tools, chained

dotnet run --project csharp/LocalAgent.Hello
dotnet run --project csharp/LocalAgent.Order
dotnet run --project csharp/LocalAgent.Ops
```

`ops_agent.py` is the interesting one. Asked whether a model is deployable, it
checks quota, finds a limit of `0`, and *then* looks up existing deployments to
suggest an alternative — a second call that happens because of what the first
returned. Its tools are ordinary functions, so you can exercise them for free:

```bash
cd python/local_agent && python -c "import ops_agent; print(ops_agent.check_quota('gpt-5-nano'))"
dotnet run --project csharp/LocalAgent.Ops -- --tools-only
```

When this lab was written the quota limit was `0`. Azure has since granted
gpt-5-nano quota, so today the agent correctly answers "yes".

### Run the Foundry-native architecture

```bash
cd python/foundry_native
python publish_agent.py    # creates lab-assistant in your project, prints its version
python run_agent.py 1      # connects by name+version, holds a two-turn session
python cleanup.py          # lists versions; --delete removes it

dotnet run --project csharp/FoundryNative.Publish        # creates lab-assistant-csharp
dotnet run --project csharp/FoundryNative.Run -- 1
dotnet run --project csharp/FoundryNative.Cleanup        # -- --delete removes it
```

Each language publishes its own agent, so cleaning up one never breaks the other.

After `publish_agent.py`, open **ai.azure.com** and the agent is there. Edit its
instructions in the portal, re-run `run_agent.py` without changing a line of code,
and the behaviour changes. That is the whole argument in one gesture.

`run_agent.py` asks a second question that is unanswerable without the first. It
resends nothing — the history is held by the service.

**It writes to your project.** `cleanup.py --delete` removes everything it created.

## Documentation

| File | Contents |
|---|---|
| [`docs/language-comparison.md`](docs/language-comparison.md) | Every lab step in Python and C# side by side: what is the pattern, what is the language |
| [`docs/architecture.md`](docs/architecture.md) | C4 diagrams for `local_agent/`, annotated **A1–A6** where the design fails as a Foundry reference |
| [`docs/architecture2.md`](docs/architecture2.md) | C4 diagrams for `foundry_native/`, scored against those same annotations |
| [`docs/foundry-notes.md`](docs/foundry-notes.md) | What the platform offers beyond inference, and why organisations buy it |
| [`CLAUDE.md`](CLAUDE.md) | Environment, commands, cost, and the traps this lab actually hit |

The two architecture documents are meant to be read as a pair. The second one
scores itself honestly: **A1–A4 resolved, A5 and A6 not.** A PromptAgent cannot
execute local `az` calls, so tools were dropped rather than moved server-side.

## Things worth knowing before you copy anything

- **Pin transitive dependencies too.** A fresh Python install that pinned only its
  direct dependencies broke on 2026-10-28 (`openai` 3.x / `httpx2`). That's why
  `python/constraints.txt` and `csharp/**/packages.lock.json` exist.
- **Do not `pip install agent-framework`.** It resolves to
  `agent-framework-core[all]` — every provider Microsoft ships — and pip fails with
  `resolution-too-deep`. Use `agent-framework-foundry`: same
  `agent_framework.foundry` namespace, 46 packages instead of 198.
- **`to_prompt_agent()` is experimental** and warns so at runtime. Expect it to move.
- **Tracing does not work in `local_agent/`.** Not a misconfiguration — the agent
  loop runs on your machine, so Azure never observes it. `architecture.md` explains
  this; it is the clearest illustration of what "not Foundry-centric" means.
- **Costs are token-based and small**, but sessions replay growing history, so long
  conversations cost more per turn. Capacity limits bite before the bill does.
- Everything that names a specific Azure lab comes from `.env`. If a variable is
  missing the scripts stop with a message telling you which one, rather than a
  `KeyError` forty lines later.
