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

| | Local agent | Foundry-native agent |
|---|---|---|
| Where the agent lives | In your process | In the Foundry project |
| Created by | Wrapping a project client in an agent object | Publishing a version — a real resource |
| Identity | None. An object that dies at exit | Named and versioned, visible in the portal |
| Conversation state | None. Every run starts cold | Service-managed sessions |
| Instructions | In the source file | In the project — editable without touching code |
| Foundry's role | An inference endpoint | The platform the system runs on |

The first version is what the usual tutorial teaches. It works, and it is a fair
illustration of an agent loop — but swap the Foundry provider for a plain OpenAI
one and almost nothing changes, which means it is not really teaching you Foundry. The second version cannot survive that substitution.

## Languages

| Folder | Language | Server-side agent | Setup and run |
|---|---|---|---|
| [`python/`](python/) | Python 3.14, `agent-framework-foundry` | `lab-assistant` | [`python/README.md`](python/README.md) |
| [`csharp/`](csharp/) | C# / .NET 10, `Microsoft.Agents.AI.Foundry` | `lab-assistant-csharp` | [`csharp/README.md`](csharp/README.md) |

Every language implements the same six steps, with the same prompts and output:

| Step | What it shows |
|---|---|
| Hello | One client, one agent, one question. No tools |
| Order | One tool, one call. The tool returns a fixed string, so it shows only the mechanism |
| Ops | Two read-only `az` tools, where the second call depends on what the first returned. The first real loop |
| Publish | The agent definition is written to the Foundry project as a new version |
| Run | Connects to that agent by name and version, and holds a two-turn session |
| Cleanup | Lists agents and versions; deletes the agent on request |

Each language publishes its own agent, so cleaning up one never breaks another.

## Shared setup

Requires the Azure CLI 2.67+ and a Foundry project with a chat model deployment.
From the repo root:

```bash
cp .env.example .env      # then fill in your own values — shared by every language
set -a && source .env && set +a

az login --tenant "$AZURE_TENANT_ID"
az account set --subscription "$AZURE_SUBSCRIPTION_ID"
```

`.env` is the only place real identifiers live. Nothing tracked in git contains a
subscription ID, tenant ID or project endpoint. Every language finds the root
`.env` on its own, and stops with a message naming the missing variable if one
isn't set.

Then follow the README in the language folder you want to run.

## What to look for

- **Ops** is the interesting local step. Asked whether a model is deployable, it
  checks quota, and *then* looks at existing deployments to suggest an
  alternative: a second call made because of what the first returned. When the
  lab was written the quota limit was `0`. Azure has since granted gpt-5-nano
  quota, so today the agent correctly answers "yes".
- **After Publish**, open **ai.azure.com** and the agent is there. Edit its
  instructions in the portal, run the Run step again without changing a line of
  code, and the behaviour changes. That is the whole argument in one gesture.
- **Run** asks a second question that is unanswerable without the first. It
  resends nothing: the service holds the history.
- **Publish writes to your project.** Cleanup with `--delete` removes everything it
  created.

## Documentation

| File | Contents |
|---|---|
| [`docs/language-comparison.md`](docs/language-comparison.md) | Every lab step in each language side by side: what is the pattern, what is the language |
| [`docs/architecture.md`](docs/architecture.md) | C4 diagrams for the local architecture, annotated **A1–A6** where the design fails as a Foundry reference |
| [`docs/architecture2.md`](docs/architecture2.md) | C4 diagrams for the Foundry-native architecture, scored against those same annotations |
| [`docs/foundry-notes.md`](docs/foundry-notes.md) | What the platform offers beyond inference, and why organisations buy it |
| [`CLAUDE.md`](CLAUDE.md) | Environment, commands, cost, and the traps this lab actually hit |

The two architecture documents are meant to be read as a pair. The second one
scores itself honestly: **A1–A4 resolved, A5 and A6 not.** A prompt agent cannot
execute local `az` calls, so tools were dropped rather than moved server-side.

## Things worth knowing before you copy anything

- **Pin transitive dependencies too.** On 2026-10-28, a fresh install that pinned
  only its direct dependencies broke. Each language folder pins the full set; its
  README explains how.
- **Tracing does not work for the local agent.** It isn't a misconfiguration: the
  agent loop runs on your machine, so Azure never observes it. `architecture.md`
  explains this; it is the clearest illustration of what "not Foundry-centric"
  means.
- **Costs are token-based and small**, but sessions replay growing history, so long
  conversations cost more per turn. Capacity limits bite before the bill does: at
  3K TPM, two runs back to back can return HTTP 429.
- **An IDE launched from the Dock doesn't see your shell's PATH.** The Azure CLI
  credential shells out to `az`, so every step fails to sign in until PATH
  includes `/opt/homebrew/bin`. Each language README says where to set it.
- Language-specific traps (package names, experimental APIs, renamed types) are in
  the language folders' READMEs.
