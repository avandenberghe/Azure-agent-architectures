# C# port of the Foundry agent lab — design

Date: 2026-10-28
Status: awaiting approval (builder check done)

## Intent

This repo is a learning lab: one small agent, built two ways (`local_agent/` —
the agent lives in the process; `foundry_native/` — the agent is a named,
versioned resource in the Foundry project). It currently exists in Python.

The goal is to learn the **agent coding pattern** — client, agent, tool, tool
loop, versioned server-side agent, session — by implementing the same lab in
several languages: Python (done), **C# (this design)**, then Java and PHP later.
Seeing the same lab in several languages separates what is the *pattern* from
what is the *language* or the *SDK*.

The C# version must be runnable on this Mac (Apple Silicon, macOS), from Rider
and from the terminal.

## Goals

1. A C# implementation of all six lab steps, each one a direct counterpart of a
   Python script, with the same behaviour and the same prompts.
2. Restructure the repo so every language sits at the same level
   (`python/`, `csharp/`, later `java/`, `php/`), sharing `.env`, `docs/`, `files/`.
3. A side-by-side comparison document, `docs/language-comparison.md`, that grows a
   column per language.

## Non-goals

- Java and PHP. Their folders are not created now.
- New Azure resources. The existing Foundry project, `my-model` deployment and
  `.env` are reused as-is.
- A test suite. As in Python, verification is running each step and seeing the
  expected output.
- Making C# a line-by-line transliteration of Python.

## Anti-goals (would make this a failure even if it runs)

- C# code that reads like translated Python (sync-over-async, `dynamic`, string
  dictionaries instead of types). Each language must be idiomatic, otherwise the
  comparison teaches nothing.
- Identifiers (subscription, tenant, endpoint, resource names) in any tracked file.
- Floating package versions. The SDK surface is moving fast (`AgentThread` →
  `AgentSession`, `Agents` → `AgentAdministrationClient`); unpinned versions would
  make the lab silently rot.

## Repo restructure

```
agent-architectures/
  .env, .env.example           shared by all languages (unchanged)
  CLAUDE.md, README.md         updated for the new layout
  docs/                        shared; gains language-comparison.md
  files/                       unchanged
  python/
    local_agent/  foundry_native/  requirements.txt  .venv/
  csharp/
    (see below)
```

- Move with `git mv` so history follows the files: `local_agent/`,
  `foundry_native/`, `requirements.txt` → `python/`. The `.venv` is not tracked;
  recreate it under `python/` (its absolute paths break if moved) and delete the
  old root `.venv`.
- `.env` stays at the root. Python's `load_dotenv()` / `find_dotenv()` search
  upward from the script, so it should still resolve — **verify by running
  `hello_agent.py` from `python/` after the move.**
- Paths inside the Python scripts' docstrings that say "project root" or
  "local_agent/" are updated where they would now mislead.
- `.gitignore` gains `bin/`, `obj/`, `*.user` for .NET.
- `requirements.txt` gets pinned (`==`) to the versions in the *current* root
  `.venv` (`pip freeze` filtered to the four direct deps) as part of the move
  commit, before the venv is recreated. The no-floating-versions rule applies to
  every language, so pinning is not presented as a C# difference.
  *(Builder's call, recorded.)*
- PyCharm's `.idea/` is untracked; its run configurations will need repointing by
  the user. Mention this, don't edit `.idea/`.

## C# solution

```
csharp/
  AgentArchitectures.sln
  Directory.Build.props        net10.0, nullable, implicit usings, LangVersion latest
  Directory.Packages.props     central package management — every version pinned here
  Shared/                      class library
  LocalAgent.Hello/            ↔ python/local_agent/hello_agent.py
  LocalAgent.Order/            ↔ python/local_agent/order_agent.py
  LocalAgent.Ops/              ↔ python/local_agent/ops_agent.py
  FoundryNative.Publish/       ↔ python/foundry_native/publish_agent.py
  FoundryNative.Run/           ↔ python/foundry_native/run_agent.py
  FoundryNative.Cleanup/       ↔ python/foundry_native/cleanup.py
```

One console project per Python script, so each step is its own Rider run
configuration and `dotnet run --project csharp/<Project>` works from the terminal.
Top-level statements are fine for `Program.cs`; tools may live in a separate
static class where that reads better (Ops).

### Shared library

Small, and only for what every program does identically:

- **`.env` loading** — walk up from the current directory to the first `.env` and
  load it into the process environment. Use the `DotNetEnv` package
  (`Env.TraversePath().Load()`, the same call Microsoft's own samples use).
- **`Settings.Required(name)`** — the C# counterpart of Python's `_required()`:
  returns the value, or exits with the same readable message when unset.
- **Credential** — `new AzureCliCredential()`, exposed once. Same as Python.

Nothing agent-related goes in Shared; every program must show the full pattern on
its own, as each Python script does.

### Per-step behaviour

All prompts, instructions, agent names and printed output match the Python
versions, so the two can be run side by side and compared.

**Local steps create nothing server-side.** `AIProjectClient.AsAIAgent(model:,
name:, instructions:)` is what Microsoft's docs call the *Responses Agent*
pattern: it returns a `ChatClientAgent` and "no server-side agent resource is
created" (Learn: *Microsoft Foundry model provider*, C# zone). It is the exact
counterpart of Python's `FoundryChatClient(...).as_agent(...)`. The overload that
takes an `AgentReference` / `ProjectsAgentRecord` is the server-side one and is
used only in `FoundryNative.Run`. If implementation shows the model overload
persists anything after all, stop and report — the in-process property wins.

| Step | C# shape |
|---|---|
| Hello | `new AIProjectClient(uri, cred).AsAIAgent(model:, name: "HelloAgent", instructions:)`, `await agent.RunAsync(...)`, print |
| Order | Same, plus `GetOrderStatus` as a static method with `[Description]` on the method and parameter, registered with `AIFunctionFactory.Create`. Returns the same hardcoded string |
| Ops | Two tools, `ListDeployments` and `CheckQuota`, shelling out to `az` with `System.Diagnostics.Process`, 60 s timeout, errors returned as `"ERROR: ..."` strings rather than thrown (same reasoning as Python's `_az`). Resource, group and region read via `Settings.Required` |
| Publish | Build a `PromptAgentDefinition` directly (model + instructions — C# has no `to_prompt_agent()`; say so in a comment) and call `AgentAdministrationClient.CreateAgentVersionAsync("lab-assistant-csharp", ...)` with the same description. Print name and version and the "open ai.azure.com" hint |
| Run | Version from `args[0]`, default `"1"`. `projectClient.AsAIAgent(new AgentReference("lab-assistant-csharp", version))`, `await agent.CreateSessionAsync()`, the same two turns, printed as `turn 1:` / `turn 2:` |
| Cleanup | List agents, list versions of `lab-assistant-csharp`; delete only with `--delete`, which removes that agent and all its versions — same behaviour and wording as Python |

**One agent per language.** Python keeps `lab-assistant` (unchanged, so its
existing versions and the user's habits keep working). C# publishes
`lab-assistant-csharp`; later languages follow the pattern
(`lab-assistant-java`, `lab-assistant-php`). Each language's cleanup deletes only
its own agent, so `--delete` in one never breaks `run` in another. *(User's
decision; the names are the builder's call, recorded.)* The comparison doc may
note in one line that `FoundryNative.Run` could equally reference Python's agent
by name — the agent is a project resource, not a language artefact — but the
lab doesn't depend on it.

### Comments

Match the Python scripts: each `Program.cs` opens with a comment block carrying the
same explanation as the Python module docstring, adapted where C# differs. Inline
comments where Python has them. No more.

### Packages

Pinned in `Directory.Packages.props`. Starting point, to be confirmed against
NuGet when implementing:

- `Microsoft.Agents.AI.Foundry` — **prerelease** (Microsoft's docs install it with
  `--prerelease`; `FoundryAgent` / `AgentReference` may not exist in stable 1.5.0).
  Pin the exact preview version current at implementation time. Builder's call:
  use stable 1.5.0 instead if it contains every type the six steps need.
- `Azure.AI.Projects` (2.x), `Azure.Identity`, `DotNetEnv`.
- Whatever the above require for `PromptAgentDefinition` / `AgentReference`
  (possibly `Azure.AI.Projects.Agents` or `Azure.AI.Extensions.OpenAI`).

Record the chosen versions and any surprises (renamed types, missing helpers) in
CLAUDE.md under the known-constraints section, the way the Python
`agent-framework` trap is recorded.

## Toolchain on this Mac

- A working arm64 .NET install already exists in `~/.dotnet` (user-local, no
  sudo): SDKs 10.0.400 and 8.0.416, runtime 10.0.11 — verified with
  `~/.dotnet/dotnet --list-sdks`. It is simply not on PATH. **Use it; do not
  install a second SDK via Homebrew** (two SDKs would let Rider and the terminal
  resolve different ones).
- Make it reachable: `export DOTNET_ROOT="$HOME/.dotnet"` and
  `export PATH="$DOTNET_ROOT:$DOTNET_ROOT/tools:$PATH"` in `~/.zprofile`.
  Editing the user's shell profile needs their go-ahead at implementation time.
- `csharp/global.json` pins SDK `10.0.400` with `rollForward: latestFeature`, so
  terminal and Rider agree. *(Builder's call, recorded.)*
- Rider is installed via JetBrains Toolbox; if it doesn't auto-detect the SDK,
  set Settings → Build, Execution, Deployment → Toolset → .NET CLI executable to
  `~/.dotnet/dotnet`.
- `az` lives in `/opt/homebrew/bin`. Rider launched from the Dock does not inherit
  the shell PATH, exactly like PyCharm. This breaks **every** project, not just
  Ops: `AzureCliCredential` itself shells out to `az`, so Hello fails auth too.
  Fix: each project's `Properties/launchSettings.json` sets
  `PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin`. That is a generic path,
  not an identifier, so it is fine in tracked files. *(Builder's call, recorded.)*

## Comparison document — `docs/language-comparison.md`

Organised by lab step, not by language. Each section:

1. One line on what the step demonstrates.
2. The few lines that carry the pattern, Python and C# next to each other (two
   fenced blocks, or a table where short enough).
3. **Same concept** — what is identical across languages (that is the pattern).
4. **Language-specific** — what differs and why (async model, tool schema from
   type hints vs attributes, `to_prompt_agent()` vs building the definition,
   config loading).

Plus a short opening table: concept → Python name → C# name (client, agent,
tool registration, session, versioned definition, admin client). Structured so
that Java and PHP become added columns and sections, not a rewrite.

## Docs updates

- **README.md**: new layout; quick start split per language; C# run commands.
- **CLAUDE.md**: layout, commands for both languages, the .NET constraints found
  during implementation. Also fix the stale "Assessment… nothing below has been
  acted on" section — `foundry_native/` exists, so record it as done and keep
  what still holds (A5/A6 unresolved).
- `docs/architecture.md` / `architecture2.md`: update paths only if they reference
  `local_agent/` as a root folder in a way that would now mislead.

## Verification

Each step is "done" when it has been run on this Mac and the output observed:

- Python, after the move: `hello_agent.py` runs from `python/` and still finds `.env`.
- C#: all six projects build with `dotnet build csharp/AgentArchitectures.sln`
  and no warnings about nullability in our own code.
- C#: Hello, Order and Ops print sensible answers; Ops visibly calls quota then
  deployments.
- C#: Publish creates `lab-assistant-csharp` v1; Run answers turn 2
  correctly using turn 1; Cleanup lists versions. **Publish and `--delete` write
  to the shared Foundry project — run them only with the user's go-ahead.**
- The build and at least Hello run from Rider as well as the terminal.

Capacity is 3K TPM; a 429 means rate limiting, not broken code.

## Left to the builder

- Exact package versions and the exact admin-client method names (verify against
  the installed package, not blog posts).
- Whether a helper class per program or top-level statements only.
- Commit granularity, but the Python move is its own commit before any C# lands.
