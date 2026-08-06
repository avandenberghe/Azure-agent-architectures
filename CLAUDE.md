# Azure agents lab

Learning project: building agents on Microsoft Foundry with Python.

## Layout

**Two folders, two diverging architectures.** The split is the point of the project:
the same lab, built twice, so the difference is legible.

### `local_agent/` — the agent lives in the Python process

Follows the setup guide. The agent is an in-memory object; nothing exists in Azure
except the model deployment it calls.

- `hello_agent.py` — guide chapter 11. One client, one agent, one question.
  No tools; the model answers from its own knowledge.
- `order_agent.py` — guide chapter 12. One tool, one call. The function returns a
  hardcoded string, so it shows the tool-calling *mechanism* and nothing else.
- `ops_agent.py` — not from the guide. Two read-only `az` tools where the second
  call depends on what the first returns: asked whether `gpt-5-nano` is deployable,
  the agent checks quota, finds `limit: 0`, and only then looks at existing
  deployments to suggest an alternative. This is the first one with an actual loop.

### `foundry_native/` — the agent lives in the Foundry project

The agent is a named, versioned resource. The scripts hold no behaviour.

- `publish_agent.py` — converts a local definition with `to_prompt_agent()` and
  writes it with `create_version()`. Run once; re-running creates a new version.
- `run_agent.py` — connects by name and version, opens a session, runs two turns.
  Contains no instructions and no model name.
- `cleanup.py` — lists versions; deletes the agent with `--delete`.

Supporting files:

- `.env.example` — copy to `.env` and fill in. `load_dotenv()` walks up from the
  calling script, so running from inside either folder resolves the root `.env`.
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
source .venv/bin/activate
pip install -r requirements.txt
set -a && source .env && set +a          # identifiers live in .env, never in tracked files
az login --tenant "$AZURE_TENANT_ID"     # explicit; plain `az login` also works now
az account set --subscription "$AZURE_SUBSCRIPTION_ID"

python local_agent/hello_agent.py        # or order_agent.py, ops_agent.py

cd foundry_native
python publish_agent.py                  # once — creates lab-assistant v1
python run_agent.py 1                    # connect and hold a session
python cleanup.py                        # list; --delete to remove
```

There is no test suite or linter configured. Verification is running a script and
seeing an answer print. `ops_agent.py`'s tools are plain functions, so they can be
exercised without spending tokens:

```bash
cd local_agent && python -c "import ops_agent; print(ops_agent.check_quota('gpt-5-nano'))"
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

- Python 3.14 in `.venv` (Homebrew `python@3.14`). Never install into system Python.
  The setup guide said 3.12; the actual environment is newer. The package set in
  `requirements.txt` installs and imports cleanly on 3.14 — verified, not assumed.
- Settings live in `.env`, loaded with python-dotenv. Never hard-code the endpoint.
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
- Capacity is 3K TPM. HTTP 429 means rate limiting, not broken code.
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

## Assessment: this is not yet a Foundry tutorial

Recorded 2026-08-06. Nothing below has been acted on.

The three scripts work and are correct, but they teach the `agent-framework` SDK
rather than the Foundry platform. All three do the same thing — build a client,
wrap it in an agent, call `run()`. Swapping `agent_framework.foundry` for
`agent_framework.openai` would leave them almost unchanged. A learner finishes
knowing an API surface, with no answer to "why Foundry rather than any model API?"

Foundry is currently being used as an inference endpoint and nothing else. The
deployment diagram in `docs/architecture.md` shows why: the agent, the tools and
the loop all run locally; only completions happen in Azure. That is also the
reason portal tracing does not work — it is a consequence of the design, not a
configuration mistake.

**What the library offers that the project ignores**

| Available in `agent_framework.foundry` | What the project uses instead |
|---|---|
| `FoundryAgent` — server-side agent with middleware and telemetry | `client.as_agent()`, a client-side object that dies with the process |
| `FoundryEvals` — evaluation backed by Foundry | nothing |
| `FoundryToolbox`, `FoundrySessionStore` (needs `agent-framework-foundry-hosting`) | local functions, no persistence |
| `FileSearchConfig` (needs `agent-framework-azure-contentunderstanding`) | nothing |

**Three concrete gaps**

1. The agent is client-side. `as_agent()` builds a Python object; `FoundryAgent`
   creates a resource in the project with an ID, visible in the portal. That single
   distinction separates using Foundry from using a model.
2. No conversation state. Every run starts cold. Session management is something
   Foundry provides and the project does not use.
3. ~~`ops_agent.py` hardcodes the resource and resource-group names, so nobody
   else can run it.~~ **Fixed.** Resource, group and region now come from `.env`
   via a `_required()` helper that fails with a readable message when unset.

**Proposed direction, not yet approved**

Rebuild around `FoundryAgent` so the agent becomes a real project resource.
Portal visibility, sessions and working tracing all follow from that one change,
and it gives the project the checkpoint it currently lacks: open ai.azure.com and
see the thing you just made.

Two open questions before starting: it creates a persistent resource in the
project (a real write — needs explicit approval), and it is unverified whether
`FoundryAgent` requires `agent-framework-foundry-hosting`, as `FoundryToolbox`
and `FoundrySessionStore` do.

Background on what Foundry offers beyond inference — server-side
PromptAgents/HostedAgents, grounding, evaluation, hosting — is in
`docs/foundry-notes.md`.
