# C# Port of the Foundry Agent Lab — Implementation Plan

> Historical record. This was carried out on 2026-10-28; the unchecked boxes below are the plan as written, not open work.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the Python lab into `python/`, add a C# version of all six lab steps in `csharp/`, and write `docs/language-comparison.md` setting the two side by side.

**Architecture:** One .NET 10 solution with one console project per Python script plus a tiny `Shared` library (env loading, required settings, credential). Local steps use `AIProjectClient.AsAIAgent(model:, …)` (in-process, nothing created in Foundry); Foundry-native steps publish and run the server-side agent `lab-assistant-csharp`. Same prompts, instructions and output as Python.

**Tech Stack:** .NET SDK 10.0.400 in `~/.dotnet`, C# latest, `Microsoft.Agents.AI.Foundry`, `Azure.AI.Projects` 2.x, `Azure.Identity`, `DotNetEnv`, central package management. Python 3.14 side unchanged except location and pins.

**Spec:** `docs/superpowers/specs/2026-10-28-csharp-port-design.md` — read it before starting any task.

## Global Constraints

- No identifiers (subscription, tenant, endpoint, resource/group names) in any tracked file. Values come only from the root `.env`.
- Every package version pinned exactly: `requirements.txt` uses `==`; NuGet versions only in `csharp/Directory.Packages.props`.
- Target `net10.0`, `<Nullable>enable</Nullable>`, `<ImplicitUsings>enable</ImplicitUsings>`, `<LangVersion>latest</LangVersion>`, `<TreatWarningsAsErrors>true</TreatWarningsAsErrors>` (so "no nullability warnings in our code" is enforced by the build).
- Use the SDK in `~/.dotnet`. Do not install .NET via Homebrew.
- Prompts, instructions, agent names (`HelloAgent`, `OrderAgent`, `OpsAgent`), printed labels and messages are copied verbatim from the matching Python script.
- C# server-side agent name: `lab-assistant-csharp`. Never touch Python's `lab-assistant`.
- Idiomatic C#: `async`/`await` end to end, no `.Result`/`.Wait()`, no `dynamic`, typed SDK objects.
- Each `Program.cs` opens with a comment block carrying the Python module docstring's explanation, adapted where C# differs. Inline comments where Python has them; no more.
- **Writes to the Foundry project** (Publish, `Cleanup --delete`) and **edits to `~/.zprofile`** run only after the user says go in this session.
- Verification is running the program and observing output; there is no test project (spec non-goal). HTTP 429 = rate limit (3K TPM), wait and retry.

## Review Focus

1. **Run from anywhere** — from repo root, from `csharp/`, from a project dir, and from Rider (working dir = project dir): `.env` must be found by walking up. Pinned in Task 3 Step 4.
2. **Missing or empty setting** — must print `<NAME> is not set. …` to stderr and exit 1, not throw `NullReferenceException`/`ArgumentNullException`. `.env` must *not* overwrite variables already set in the environment (matches python-dotenv's default), otherwise the check can't be exercised. Pinned in Task 3 Step 5.
3. **`az` not on PATH** (Rider from the Dock) — Ops tools must return `ERROR: 'az' not found on PATH.`, not crash. Pinned in Task 5 Step 4 via `--tools-only`.
4. **Run before Publish / unknown version** — `FoundryNative.Run 99` must print a one-line readable error naming the agent and version and exit 1, not dump a stack trace. Pinned in Task 7 Step 3.
5. **Cleanup when the agent doesn't exist** — listing prints `could not list: …` (as Python), and `--delete` prints a readable message and exits 1. Pinned in Task 6 Step 3.

---

### Task 1: Move Python into `python/` and pin it

**Files:**
- Move: `local_agent/` → `python/local_agent/`, `foundry_native/` → `python/foundry_native/`, `requirements.txt` → `python/requirements.txt`
- Modify: `python/requirements.txt`, `python/local_agent/ops_agent.py` (the `_required` message), `.gitignore`
- Create (untracked): `python/.venv/`; delete root `.venv/` at the end

**Interfaces:**
- Produces: Python lab runnable as `python/.venv/bin/python python/local_agent/hello_agent.py` from the repo root.

- [ ] **Step 0: Commit the spec and this plan** — `git add docs/superpowers && git commit -m "Add C# port design and implementation plan"`.
- [ ] **Step 1: Record current versions** — already captured from the root venv:
  `agent-framework-foundry==1.10.4`, `azure-identity==1.25.3`, `pydantic==2.13.4`, `python-dotenv==1.2.2`. Re-run `.venv/bin/pip freeze | grep -iE '^(agent-framework-foundry|azure-identity|python-dotenv|pydantic)=='` and use its output if it differs.
- [ ] **Step 2: Move with history** — `git mv local_agent python/local_agent && git mv foundry_native python/foundry_native && git mv requirements.txt python/requirements.txt`
- [ ] **Step 3: Pin** — replace each bare package line in `python/requirements.txt` with its `==` pin from Step 1; keep the existing comments. Add one comment line: `# Pinned on purpose: the SDK surface moves fast. Bump deliberately.`
- [ ] **Step 4: Fix the misleading path text** — in `ops_agent.py` `_required()` the message says "run this from the project root or from local_agent/"; change to "run this from anywhere inside the repo." Grep `python/` for other "project root" / "root scripts" wording; in `publish_agent.py` "the sibling scripts in the project root" → "the scripts in local_agent/". Leave everything else.
- [ ] **Step 5: .gitignore** — append a `# .NET` block: `bin/`, `obj/`, `*.user`. (`.venv/` already matches at any depth.)
- [ ] **Step 6: New venv** — `/opt/homebrew/bin/python3.14 -m venv python/.venv && python/.venv/bin/pip install -r python/requirements.txt`. Expected: installs without resolver errors.
- [ ] **Step 7: Verify .env still resolves** — `cd python && .venv/bin/python local_agent/hello_agent.py` → prints an answer naming Antwerp. Then from the repo root: `python/.venv/bin/python python/local_agent/hello_agent.py` → same. Then the free tool check: `cd python/local_agent && ../.venv/bin/python -c "import ops_agent; print(ops_agent.check_quota('gpt-5-nano'))"` → JSON with `limit`.
- [ ] **Step 8: Remove old venv** — only after Step 7 passed: `rm -rf .venv` (root).
- [ ] **Step 9: Commit** — `git add -A python .gitignore && git status` (confirm only moves + those edits, no `.env`, no venv), then `git commit -m "Move Python lab into python/ and pin its dependencies"`. Tell the user PyCharm's interpreter and run configurations now need repointing to `python/.venv` (do not edit `.idea/`).

---

### Task 2: .NET toolchain, solution skeleton and `Shared`

**Files:**
- Modify (user's machine, after go-ahead): `~/.zprofile`
- Create: `csharp/global.json`, `csharp/AgentArchitectures.sln`, `csharp/Directory.Build.props`, `csharp/Directory.Packages.props`, `csharp/Shared/Shared.csproj`, `csharp/Shared/Settings.cs`

**Interfaces:**
- Produces (namespace `AgentArchitectures.Shared`, every later task uses these):
  - `public static class Settings`
    - `public static void Load()` — `DotNetEnv.Env.NoClobber().TraversePath().Load()`; idempotent.
    - `public static string Required(string name)` — returns non-empty value; otherwise writes `"{name} is not set. Copy .env.example to .env and fill it in, then run this from anywhere inside the repo."` to stderr and `Environment.Exit(1)`. Return type is non-nullable `string`.
    - `public static TokenCredential Credential { get; }` — a single `AzureCliCredential` instance.

- [ ] **Step 1: Ask the user** for go-ahead to append to `~/.zprofile`:
  ```zsh
  # .NET SDK (user-local install)
  export DOTNET_ROOT="$HOME/.dotnet"
  export PATH="$DOTNET_ROOT:$DOTNET_ROOT/tools:$PATH"
  export DOTNET_CLI_TELEMETRY_OPTOUT=1
  ```
  If declined, use `~/.dotnet/dotnet` with `DOTNET_ROOT=$HOME/.dotnet` explicitly in every command below. Verify: new login shell `dotnet --list-sdks` lists `10.0.400`.
- [ ] **Step 2: Resolve package versions** — for each of `microsoft.agents.ai.foundry`, `azure.ai.projects`, `azure.identity`, `dotnetenv`:
  `curl -s https://api.nuget.org/v3-flatcontainer/<id>/index.json | jq -r '.versions[]' | tail -5`.
  Decision rule for `Microsoft.Agents.AI.Foundry`: restore stable `1.5.0` in a scratch project under the scratchpad, then grep its XML docs (`~/.nuget/packages/microsoft.agents.ai.foundry/1.5.0/lib/net*/**.xml` plus its `Azure.AI.Projects` dependency) for `FoundryAgent`, `AgentReference`, `PromptAgentDefinition`, `AgentAdministrationClient`, `CreateAgentVersionAsync`. All present → pin stable; any missing → pin the newest `-preview` and re-check. Note which assembly/namespace each type lives in (needed for `using`s and possibly an extra package such as `Azure.AI.Projects.Agents`; add it if so). Also record the exact admin-client method names for listing agents, listing versions, getting one version and deleting an agent.
- [ ] **Step 3: Write the skeleton** — `global.json` (`"sdk": { "version": "10.0.400", "rollForward": "latestFeature" }`); `Directory.Build.props` with the Global Constraints properties; `Directory.Packages.props` with `ManagePackageVersionsCentrally=true` and the Step 2 versions; `dotnet new sln -n AgentArchitectures` in `csharp/`; `dotnet new classlib -n Shared -o Shared`, delete `Class1.cs`, `dotnet sln add Shared`. Remove `TargetFramework`/`Nullable`/`ImplicitUsings` from the generated csproj (they come from `Directory.Build.props`). Package refs carry no `Version` attribute.
- [ ] **Step 4: Implement `Settings`** in `csharp/Shared/Settings.cs` per the Interfaces block. `Required` treats null and whitespace as unset.
- [ ] **Step 5: Build** — `dotnet build csharp/AgentArchitectures.sln` → `Build succeeded. 0 Warning(s) 0 Error(s)`.
- [ ] **Step 6: Review Focus 1 + 2 are verified in Task 3 Steps 4-5** (Shared has no entry point of its own). Commit now: `git add csharp .gitignore && git commit -m "Add C# solution skeleton and Shared settings"`.

---

### Task 3: `LocalAgent.Hello`

**Files:**
- Create: `csharp/LocalAgent.Hello/LocalAgent.Hello.csproj`, `Program.cs`, `Properties/launchSettings.json`

**Interfaces:**
- Consumes: `Settings.Load()`, `Settings.Required(string)`, `Settings.Credential`.
- Produces: the `launchSettings.json` shape every later project copies:
  ```json
  { "profiles": { "<ProjectName>": { "commandName": "Project",
      "environmentVariables": { "PATH": "/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin" } } } }
  ```

- [ ] **Step 1: Baseline the project's agents (read-only)** — `python/.venv/bin/python python/foundry_native/cleanup.py` and save the "agents in this project" list.
- [ ] **Step 2: Create the project** — `dotnet new console -n LocalAgent.Hello -o LocalAgent.Hello` in `csharp/`, `dotnet sln add`, reference `Shared`, package refs for `Microsoft.Agents.AI.Foundry`, `Azure.AI.Projects`. Add `launchSettings.json` as above.
- [ ] **Step 3: Write `Program.cs`** — top-level statements: `Settings.Load()`; `new AIProjectClient(new Uri(Settings.Required("FOUNDRY_PROJECT_ENDPOINT")), Settings.Credential).AsAIAgent(model: Settings.Required("FOUNDRY_MODEL"), name: "HelloAgent", instructions: "You are a concise, friendly assistant.")`; `Console.WriteLine(await agent.RunAsync("What is the largest city in Belgium?"))`. Header comment from `hello_agent.py`, plus its three inline "where / which deployment / who you are" comments.
- [ ] **Step 4: Run from three places** — `dotnet run --project csharp/LocalAgent.Hello` (repo root), then from `csharp/`, then from `csharp/LocalAgent.Hello/`. Each prints an answer naming Antwerp. *(Review Focus 1)*
- [ ] **Step 5: Missing-setting path** — `FOUNDRY_MODEL= dotnet run --project csharp/LocalAgent.Hello`. Expected stderr: `FOUNDRY_MODEL is not set. Copy .env.example to .env and fill it in, then run this from anywhere inside the repo.`; `echo $?` → `1`. If it instead runs normally, `.env` is clobbering — fix `Settings.Load()`. *(Review Focus 2)*
- [ ] **Step 6: Nothing created server-side** — rerun the Step 1 listing; it must equal the baseline. If it differs, STOP and report to the user (spec: the in-process property wins).
- [ ] **Step 7: Commit** — `git commit -m "Add C# LocalAgent.Hello"`.

---

### Task 4: `LocalAgent.Order`

**Files:**
- Create: `csharp/LocalAgent.Order/LocalAgent.Order.csproj`, `Program.cs`, `Properties/launchSettings.json`

**Interfaces:**
- Consumes: as Task 3. Same csproj shape plus `Microsoft.Extensions.AI` only if `AIFunctionFactory` is not already transitively available (it lives in `Microsoft.Extensions.AI`; if it is transitive, still add an explicit pinned reference — direct use deserves a direct declaration, as Python's `requirements.txt` does for pydantic).

- [ ] **Step 1: Create the project** as in Task 3 Step 2.
- [ ] **Step 2: Write `Program.cs`** — a `static string GetOrderStatus([Description("Order ID, format ORD-12345")] string orderId)` with `[Description("Look up the current delivery status of a customer order.")]` on the method, returning `$"{orderId}: shipped, tracking BE998877, arriving 8 August"`. Agent `OrderAgent` with the three-sentence instructions from `order_agent.py`, `tools: [AIFunctionFactory.Create(GetOrderStatus)]`. Prompt: `"Hi, where has my order ORD-40021 got to?"`. Header comment adapted: "everything the model knows comes from the parameter types and `[Description]` attributes". Keep the idempotency and "instructions shape behaviour" inline comments.
- [ ] **Step 3: Run** — `dotnet run --project csharp/LocalAgent.Order` → reply mentions `BE998877` and `8 August`.
- [ ] **Step 4: Commit** — `git commit -m "Add C# LocalAgent.Order"`.

---

### Task 5: `LocalAgent.Ops`

**Files:**
- Create: `csharp/LocalAgent.Ops/LocalAgent.Ops.csproj`, `Program.cs`, `OpsTools.cs`, `Properties/launchSettings.json`

**Interfaces:**
- Consumes: as Task 4.
- Produces (in `OpsTools.cs`, `static class OpsTools`):
  - `static async Task<string> ListDeploymentsAsync()` — `[Description]` = Python docstring.
  - `static async Task<string> CheckQuotaAsync([Description("Model family to check, e.g. 'gpt-5-nano'")] string model)` — `[Description]` = Python docstring.
  - `static async Task<string> AzAsync(params string[] args)` — private helper.

- [ ] **Step 1: Create the project** as in Task 3 Step 2.
- [ ] **Step 2: Implement `OpsTools`** — `AzAsync` runs `az` via `Process` with `ArgumentList` (no shell string), redirects stdout/stderr, 60 s timeout via `CancellationTokenSource` + `WaitForExitAsync`; returns `"ERROR: 'az' not found on PATH."` when `Process.Start` throws `Win32Exception`, `"ERROR: az timed out after 60s."` on timeout (kill the process tree), `$"ERROR: {stderr.Trim()}"` on non-zero exit, else `stdout.Trim()`. Same `--query` strings as `ops_agent.py`, with resource/group/region from `Settings.Required("AZURE_FOUNDRY_RESOURCE" | "AZURE_RESOURCE_GROUP" | "AZURE_REGION")`. Port the `_az` docstring as the comment explaining why errors are returned, not thrown.
- [ ] **Step 3: Implement `Program.cs`** — agent `OpsAgent`, instructions and prompt verbatim from `ops_agent.py`, tools `AIFunctionFactory.Create(OpsTools.ListDeploymentsAsync)` and `…CheckQuotaAsync`. Add a `--tools-only` argument: prints `CheckQuotaAsync("gpt-5-nano")` and `ListDeploymentsAsync()` output and exits without creating a client — the C# counterpart of Python's free `python -c` check; say so in a comment.
- [ ] **Step 4: Verify tools without tokens** — `dotnet run --project csharp/LocalAgent.Ops -- --tools-only` → quota JSON then deployments JSON. Then with `az` hidden: `dotnet build csharp/LocalAgent.Ops && env PATH=/usr/bin:/bin "$HOME/.dotnet/dotnet" csharp/LocalAgent.Ops/bin/Debug/net10.0/LocalAgent.Ops.dll --tools-only` → both lines print `ERROR: 'az' not found on PATH.`, exit 0. *(Review Focus 3)*
- [ ] **Step 5: Run the agent** — `dotnet run --project csharp/LocalAgent.Ops` → answer says gpt-5-nano is not deployable (limit 0) and suggests an existing deployment (the `my-model`/gpt-5.4-mini one).
- [ ] **Step 6: Commit** — `git commit -m "Add C# LocalAgent.Ops with chained az tools"`.

---

### Task 6: `FoundryNative.Cleanup` (list mode first — read-only)

**Files:**
- Create: `csharp/FoundryNative.Cleanup/FoundryNative.Cleanup.csproj`, `Program.cs`, `Properties/launchSettings.json`

**Interfaces:**
- Consumes: Shared; admin-client method names recorded in Task 2 Step 2.
- Produces: `const string AgentName = "lab-assistant-csharp";` — the same literal is used in Tasks 7 and 8 (each program declares its own; no shared constant, matching Python).

- [ ] **Step 1: Create the project** as in Task 3 Step 2 (no agent-framework package needed if the admin client lives in `Azure.AI.Projects`).
- [ ] **Step 2: Write `Program.cs`** — mirror `cleanup.py` output exactly: `agents in this project:` + `  {name}` lines; blank line + `versions of lab-assistant-csharp:` + `  version {v}  {description}` lines, wrapped so a failure prints `  could not list: {message}`; without `--delete` print the blank line + `nothing deleted. pass --delete to remove the agent.` and return. With `--delete`: delete the agent; on a not-found error print `nothing to delete: lab-assistant-csharp does not exist.` and exit 1; on success `deleted: lab-assistant-csharp`. Use `await foreach` for paged lists.
- [ ] **Step 3: Verify list mode before anything is published** — `dotnet run --project csharp/FoundryNative.Cleanup` → agent list includes Python's `lab-assistant`; versions section prints `could not list: …` (agent doesn't exist yet); final line `nothing deleted…`. *(Review Focus 5, listing half; the `--delete` half is verified in Task 8.)*
- [ ] **Step 4: Commit** — `git commit -m "Add C# FoundryNative.Cleanup"`.

---

### Task 7: `FoundryNative.Publish` and `FoundryNative.Run`

**Files:**
- Create: `csharp/FoundryNative.Publish/{FoundryNative.Publish.csproj,Program.cs,Properties/launchSettings.json}`, `csharp/FoundryNative.Run/{FoundryNative.Run.csproj,Program.cs,Properties/launchSettings.json}` (Run's profile adds `"commandLineArgs": "1"`)

**Interfaces:**
- Consumes: Shared; `AgentName` literal from Task 6; type/method names from Task 2 Step 2.

- [ ] **Step 1: Write Publish** — build a `PromptAgentDefinition` with model `Settings.Required("FOUNDRY_MODEL")` and the three-sentence instructions from `publish_agent.py`; `CreateAgentVersionAsync(AgentName, …)` with description `"Lab assistant published from FoundryNative.Publish"`. Comment where Python calls `to_prompt_agent()`: no C# equivalent, the definition is written directly. Output: `published: lab-assistant-csharp`, `version:   {v}`, blank, `Now open ai.azure.com -> your project -> Agents. It is there.`, `Then: dotnet run --project csharp/FoundryNative.Run -- {v}`.
- [ ] **Step 2: Write Run** — `version = args.Length > 0 ? args[0] : "1"`; `projectClient.AsAIAgent(new AgentReference(AgentName, version))`; `var session = await agent.CreateSessionAsync()`; the two turns verbatim from `run_agent.py`, printed `turn 1: …`, blank, `turn 2: …`. Wrap the first `RunAsync` so a not-found/bad-request service error prints `lab-assistant-csharp version {version} not found. Run FoundryNative.Publish first, or pass an existing version.` to stderr and exits 1 (catch the SDK's service exception type — `ClientResultException` or `RequestFailedException`, whichever the package throws — filtered on status 400/404). Port the "no instructions, no model…" and "The proof" comments.
- [ ] **Step 3: Verify Run's error path before publishing (read-only)** — `dotnet run --project csharp/FoundryNative.Run -- 99` → the one-line message, exit 1. *(Review Focus 4)*
- [ ] **Step 4: Ask the user for go-ahead to publish**, then `dotnet run --project csharp/FoundryNative.Publish` → `version:   1`. Then `dotnet run --project csharp/FoundryNative.Cleanup` → shows `lab-assistant-csharp` with `version 1`, and Python's `lab-assistant` is untouched.
- [ ] **Step 5: Run the session** — `dotnet run --project csharp/FoundryNative.Run -- 1` → turn 2 answers "swedencentral".
- [ ] **Step 6: Commit** — `git commit -m "Add C# FoundryNative.Publish and Run"`.

---

### Task 8: Rider check and delete path

- [ ] **Step 1: User opens `csharp/AgentArchitectures.sln` in Rider** (launched from the Dock, not the terminal). Ask them to run `LocalAgent.Hello` and `LocalAgent.Ops` from the run-configuration dropdown and paste the output. Expected: the same answers as the terminal; no `az` / auth error. If Rider doesn't find the SDK, give the Toolset setting from the spec.
- [ ] **Step 2: Ask the user** whether to delete `lab-assistant-csharp` now or keep it. If delete: `dotnet run --project csharp/FoundryNative.Cleanup -- --delete` → `deleted: lab-assistant-csharp`; run it again → `nothing to delete: …`, exit 1 *(Review Focus 5, delete half)*; Python's `cleanup.py` still lists `lab-assistant`.
- [ ] **Step 3: Commit** any fixes found here.

---

### Task 9: Documentation

**Files:**
- Create: `docs/language-comparison.md`
- Modify: `README.md`, `CLAUDE.md`, `docs/architecture.md:395`, `docs/architecture2.md:17`

- [ ] **Step 1: `docs/language-comparison.md`** — structure per spec: opening concept table (concept | Python | C#) with rows client, local agent, tool registration, tool schema source, session, versioned definition, admin client, config loading, credential. Then one section per lab step (Hello, Order, Ops, Publish, Run, Cleanup), each with: one-line purpose; the pattern-carrying lines as a Python block and a C# block, copied from the real files; **Same concept**; **Language-specific**. Must cover: async model, `Annotated`/`Field` vs `[Description]`, `to_prompt_agent()` vs `PromptAgentDefinition`, `load_dotenv` (no override) vs `Env.NoClobber().TraversePath()`, the `--tools-only` vs `python -c` difference, one agent per language and the one-line note that Run could reference any agent by name. Built so Java/PHP add a column and a block per section.
- [ ] **Step 2: README.md** — layout table now `python/` and `csharp/`; quick start split into "Python" and "C#" (dotnet setup via `~/.dotnet`, `dotnet run --project …` for all six); link the comparison doc in the Documentation table. Keep the existing argument and warnings.
- [ ] **Step 3: CLAUDE.md** — retitle intro "…with Python and C#"; layout section gains `python/` prefix and a `csharp/` subsection; Commands for both languages; Known constraints: chosen NuGet versions, any renamed types/missing helpers found in Task 2, `AzureCliCredential` needs `az` on PATH in Rider, `.NET in ~/.dotnet not Homebrew`. Replace the stale "Assessment … Nothing below has been acted on" section with a short "Assessment (2026-08-06) — acted on" note: `foundry_native/` implemented the proposed direction; A1–A4 resolved, A5/A6 still open per `architecture2.md`.
- [ ] **Step 4: architecture docs** — `architecture.md:395` command becomes `cd python/local_agent && ../.venv/bin/python -c …`; `architecture2.md:17` "remain at the project root" → "remain in `python/local_agent/`". No other changes.
- [ ] **Step 5: Verify** — `grep -rn -E "(^|[^/])local_agent/|foundry_native/" README.md CLAUDE.md docs/*.md` → every hit is prefixed `python/` or is a conceptual (non-path) mention. `git grep -n -i -E "[0-9a-f]{8}-[0-9a-f]{4}-" -- ':!docs/superpowers'` → no hits (the existing truncated `89f684e6-…` mention doesn't match a full GUID).
- [ ] **Step 6: Commit** — `git commit -m "Document the C# port and the Python/C# comparison"`.
