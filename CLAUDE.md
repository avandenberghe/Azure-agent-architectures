# Azure agents lab

Learning project: building agents on Microsoft Foundry, the same lab once per
language — Python (`python/`) and C# (`csharp/`) so far, Java and PHP planned.
The point is to separate the agent *pattern* from the language and SDK;
`docs/language-comparison.md` sets the versions side by side.

## Layout

**Two folders, two diverging architectures.** The split is the point of the project:
the same lab, built twice, so the difference is legible.

### `python/local_agent/` — the agent lives in the Python process

Follows the setup guide. The agent is an in-memory object; nothing exists in Azure
except the model deployment it calls.

- `hello_agent.py` — guide chapter 11. One client, one agent, one question.
  No tools; the model answers from its own knowledge.
- `order_agent.py` — guide chapter 12. One tool, one call. The function returns a
  hardcoded string, so it shows the tool-calling *mechanism* and nothing else.
- `ops_agent.py` — not from the guide. Two read-only `az` tools where the second
  call depends on what the first returns: asked whether `gpt-5-nano` is deployable,
  the agent checks quota, found `limit: 0` (as of 2026-08; quota has since been
  granted), and only then looks at existing deployments to suggest an
  alternative. This is the first one with an actual loop.

### `python/foundry_native/` — the agent lives in the Foundry project

The agent is a named, versioned resource. The scripts hold no behaviour.

- `publish_agent.py` — converts a local definition with `to_prompt_agent()` and
  writes it with `create_version()`. Run once; re-running creates a new version.
- `run_agent.py` — connects by name and version, opens a session, runs two turns.
  Contains no instructions and no model name.
- `cleanup.py` — lists versions; deletes the agent with `--delete`.

Python publishes the agent `lab-assistant`.

### `csharp/` — the same six steps in C#

`AgentArchitectures.slnx`, one console project per Python script, plus `Shared/`
(`Settings.Load()`, `Settings.Required()`, `Settings.Credential`) — nothing
agent-related lives in Shared.

- `LocalAgent.Hello`, `LocalAgent.Order`, `LocalAgent.Ops` — `AsAIAgent(model:, …)`,
  in-process, nothing created in Foundry. `Ops` takes `--tools-only` to run both
  `az` tools without inference (the C# stand-in for Python's `python -c` check).
- `FoundryNative.Publish`, `FoundryNative.Run [version]`, `FoundryNative.Cleanup
  [--delete]` — publish and use the agent `lab-assistant-csharp`. One agent per
  language, so either cleanup leaves the other language's agent alone.
- Same prompts, instructions and output as the Python scripts, by design.

Supporting files:

- `.env.example` — copy to `.env` and fill in. Both languages walk up from the
  working directory to find the root `.env`, and neither overrides variables
  already set in the environment.
- `files/` — `provision.sh` (steps 1-8 as one script; already run, the resources it
  creates exist) and `azure-agent-setup-guide_1.pdf`, the setup guide.
- `docs/architecture.md` — C4 for `local_agent/`, annotated A1-A6 where it fails as
  a Foundry reference.
- `docs/architecture2.md` — C4 for `foundry_native/`, scored against those same
  annotations. A5 and A6 remain unresolved and the document says so.
- `docs/foundry-notes.md` — what the Foundry platform offers beyond inference.

Two things in that guide are now wrong for this environment: the `pip install
agent-framework` instruction, and its warnings about a second tenant that has since
been deleted. Both are covered under constraints below.

## Commands

```bash
source python/.venv/bin/activate
pip install -r python/requirements.txt   # -c constraints.txt pins transitives
set -a && source .env && set +a          # identifiers live in .env, never in tracked files
az login --tenant "$AZURE_TENANT_ID"     # explicit; plain `az login` also works now
az account set --subscription "$AZURE_SUBSCRIPTION_ID"

python python/local_agent/hello_agent.py   # or order_agent.py, ops_agent.py

cd python/foundry_native
python publish_agent.py                  # once — creates lab-assistant v1
python run_agent.py 1                    # connect and hold a session
python cleanup.py                        # list; --delete to remove
```

```bash
# C# — from the repo root
dotnet build csharp/AgentArchitectures.slnx
dotnet run --project csharp/LocalAgent.Hello          # or .Order, .Ops
dotnet run --project csharp/LocalAgent.Ops -- --tools-only
dotnet run --project csharp/FoundryNative.Publish     # creates lab-assistant-csharp
dotnet run --project csharp/FoundryNative.Run -- 1
dotnet run --project csharp/FoundryNative.Cleanup     # -- --delete to remove
```

There is no test suite or linter configured. Verification is running a script and
seeing an answer print. `ops_agent.py`'s tools are plain functions, so they can be
exercised without spending tokens:

```bash
cd python/local_agent && ../.venv/bin/python -c "import ops_agent; print(ops_agent.check_quota('gpt-5-nano'))"
```

## Cost of the two architectures

Neither architecture costs anything at rest. Both bill the same way for inference —
per token against the `my-model` deployment — so a given conversation costs the same
in either. The differences are in what else exists.

| | `local_agent/` | `foundry_native/` |
|---|---|---|
| At rest | Nothing exists. No resource, nothing to bill | Agent + versions exist. **Definitions are metadata and are not billed**; an idle agent costs nothing, same as an idle deployment |
| Per conversation | Tokens only | Tokens only. Publishing itself is a control-plane write, not an inference call |
| Session history | None — every turn resends whatever you resend | **The service replays history.** A 10-turn session sends growing context each turn, so token cost grows through the conversation. This is the one real cost difference, and it is a *token* cost, not a platform fee |
| Hidden multiplier | Tool calls cost a round trip each: the model calls, you return, it reasons again. `ops_agent.py`'s two chained calls are three inference passes, not one | Same, when tools are attached |

Practical consequences:

- **Sessions are not free.** Convenience is server-side history management, but you
  pay for the tokens it replays. Long conversations need a compaction strategy —
  `as_agent()` and `FoundryAgent` both take `compaction_strategy` for this.
- **Capacity, not price, is the binding constraint here.** 3K TPM. A long session
  hits 429 rate limits before it produces a noticeable bill.
- **Delete agents you are done with** — not for cost, but because a stale versioned
  agent in a shared project is a governance problem, not a billing one.
- The whole of this session's experimentation — publishing, sessions, several agent
  runs — cost well under one euro, consistent with the setup guide's estimate.

## Environment

**No identifiers in this file.** Subscription, tenant, resource, group and region
live in `.env`, which is gitignored. `.env.example` shows the shape. This file is
tracked, so it names variables rather than values.

| What | Where to read it |
|---|---|
| Subscription ID | `$AZURE_SUBSCRIPTION_ID`, or `az account show --query id -o tsv` |
| Tenant ID | `$AZURE_TENANT_ID`, or `az account show --query tenantId -o tsv` |
| Resource group | `$AZURE_RESOURCE_GROUP` |
| Foundry resource | `$AZURE_FOUNDRY_RESOURCE` |
| Region | `$AZURE_REGION` |
| Project endpoint | `$FOUNDRY_PROJECT_ENDPOINT` — the project name is the last path segment |
| Model deployment | `$FOUNDRY_MODEL` → gpt-5.4-mini, SKU DataZoneStandard, capacity 3 |

To load them into a shell: `set -a && source .env && set +a`

Run `az account show` before any az command that creates or modifies resources,
to confirm the CLI is pointed at the right subscription.

The setup guide devotes several warnings to a second tenant (Avathar-prod,
`89f684e6-…`) that could be signed into by mistake. That tenant has been deleted;
those warnings no longer apply. The `--tenant` flag below is now belt-and-braces
rather than a fix for a real hazard.

## Conventions

- Python 3.14 in `python/.venv` (Homebrew `python@3.14`). Never install into system Python.
  The setup guide said 3.12; the actual environment is newer. The package set in
  `requirements.txt` installs and imports cleanly on 3.14 — verified, not assumed.
- .NET SDK 10.0.400 in `~/.dotnet` (user-local, arm64). NOT Homebrew — two SDKs
  would let Rider and the terminal resolve different ones. `csharp/global.json`
  pins it. `~/.dotnet` must be on PATH with `DOTNET_ROOT` set.
- Every version is pinned. Python: `python/requirements.txt` (direct) +
  `python/constraints.txt` (full set). C#: `csharp/Directory.Packages.props`
  (direct, central package management) + `packages.lock.json` per project.
- C# builds with `TreatWarningsAsErrors` and nullable enabled.
- Settings live in `.env`, loaded with python-dotenv / DotNetEnv. Never hard-code the endpoint.
- Shell is zsh on macOS.
- Auth is `AzureCliCredential` locally; `ManagedIdentityCredential` in anything deployed.

## Known constraints in this environment

- Do NOT install `agent-framework`, despite what the setup guide says. It resolves
  to `agent-framework-core[all]` and pip dies with `resolution-too-deep` after
  backtracking through ~50 versions of fastapi and ~40 of agent-framework-devui.
  Use `agent-framework-foundry` — it provides `agent_framework.foundry` and installs
  46 packages instead of 198. PyCharm's package UI compounds this: it times out at
  5 minutes and offers `azure-core`, which is not `azure-identity` and will not
  satisfy the import.
- Pin transitive Python packages too. On 2026-10-28 a fresh venv with only the four
  direct pins resolved `openai` 3.x / `httpx2`, and `azure-ai-projects` 2.x failed
  with `No module named 'httpx'`. `constraints.txt` is the frozen known-good set.
- C# packages: stable `Microsoft.Agents.AI.Foundry` 1.5.0 + `Azure.AI.Projects`,
  `.Agents`, `Extensions.OpenAI` 2.0.0 cover all six steps; the 1.26 previews are
  not needed. Names differ from Microsoft's docs and from Python:
  - the prompt agent definition is `DeclarativeAgentDefinition` (docs say
    `PromptAgentDefinition`); there is no `to_prompt_agent()` equivalent.
  - admin operations hang off `AIProjectClient.AgentAdministrationClient`
    (older blogs: `.Agents`); sessions are `AgentSession` (older: `AgentThread`).
  - naming the `FoundryAgent` type raises `OPENAI001` (evaluation-only), and with
    TreatWarningsAsErrors that breaks the build. `AsAIAgent(AgentReference)` does not
    raise it — hold the result as `AIAgent`.
- `AzureCliCredential` shells out to `az`, so in a Dock-launched IDE *every*
  program fails auth, not just `ops_agent`. Rider: each `launchSettings.json` sets
  PATH. PyCharm: set PATH in the run configuration.
- `dotnet run` honours `launchSettings.json`, so its PATH replaces the shell's
  PATH for the program even in the terminal. It includes `/opt/homebrew/bin`.
- Capacity is 3K TPM. HTTP 429 means rate limiting, not broken code. Two agent
  runs back to back are enough to hit it.
- gpt-5-nano quota was 0 on 2026-08-06; by 2026-10-28 it was granted (5000
  GlobalStandard / 2000 DataZoneStandard). The Ops agent now correctly answers
  "yes" — the docs' `limit: 0` walkthrough describes the original state.
- Quota is granted per model + SKU + region. gpt-5.4-mini has NO GlobalStandard
  quota here — only DataZoneStandard (200) and GlobalBatch.
- `az` lives in /opt/homebrew/bin. GUI-launched apps do not inherit it on PATH;
  PyCharm run configurations need PATH set explicitly.
- Control plane (`az`, ARM) and data plane (project endpoint) authorise separately.
  Subscription Owner grants nothing on the data plane.
- On `az cognitiveservices account list-models`, the filter field is `format`,
  not `kind`. A wrong field silently matches nothing rather than erroring.

## Decisions so far

- gpt-5.4-mini over gpt-5.4-nano: better tool-calling reliability, which is the
  behaviour the labs are meant to demonstrate.
- DataZoneStandard over GlobalStandard: EU routing, and the only Standard-family
  SKU with quota for this model on this subscription.
- Provisioning by CLI rather than portal, for reproducibility and because the
  portal hides prerequisites (e.g. resource provider registration).

## Assessment (2026-08-06) — acted on

The original assessment found the first three scripts taught the SDK, not the
platform: Foundry was used as an inference endpoint only. Its proposed direction —
make the agent a real project resource via `FoundryAgent` — was implemented as
`foundry_native/` (now `python/foundry_native/`, and `csharp/FoundryNative.*`).
Per `docs/architecture2.md`, A1–A4 are resolved; A5 and A6 remain open (a
PromptAgent cannot run local `az` tools, so tools were dropped rather than moved
server-side). `FoundryAgent` did not need `agent-framework-foundry-hosting`.

Background on what Foundry offers beyond inference — server-side
PromptAgents/HostedAgents, grounding, evaluation, hosting — is in
`docs/foundry-notes.md`.
