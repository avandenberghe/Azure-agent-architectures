# Foundry agent lab

A demo project for understanding **Microsoft Foundry** — what it is, what it gives
you beyond a model endpoint, and what changes when you actually build around it.

It is not a library, a template, or production code. It exists to be read and run.

## What Microsoft Foundry is

Microsoft Foundry (formerly Azure AI Foundry) is Azure's platform for building with
AI models. At its simplest it gives you a model endpoint. What it adds around that
endpoint is what this lab is about: agents stored as named, versioned resources,
conversations held by the service, evaluation, and the Azure identity, network and
compliance boundary your organisation already has.

The pieces you'll meet, from the outside in:

| Piece | What it is | In this lab |
|---|---|---|
| **Foundry resource** | The Azure resource (`Microsoft.CognitiveServices/accounts`) that holds everything else. Lives in a resource group and a region | `$AZURE_FOUNDRY_RESOURCE` in `$AZURE_RESOURCE_GROUP` |
| **Project** | A workspace inside the resource. Your code connects to a project, through its **project endpoint** | `$FOUNDRY_PROJECT_ENDPOINT` |
| **Model deployment** | A model made callable under a name you choose, with a SKU (routing: global, data-zone) and a capacity in tokens per minute | `my-model` → gpt-5.4-mini, DataZoneStandard, 3K TPM |
| **Quota** | What you're *allowed* to deploy, per model, SKU and region. Separate from what the catalogue lists | What the Ops step checks |
| **Agent** | A named, versioned definition stored in the project: model + instructions (+ tools). Visible and editable in the portal | `lab-assistant`, `lab-assistant-csharp` |
| **Conversation / session** | Message history held by the service, so each turn doesn't resend it | What the Run step shows |

Two things that surprise people:

- **Control plane and data plane authorise separately.** Creating the resource
  (control plane, `az` / ARM) doesn't let you *use* it. Calling the project endpoint
  (data plane) needs a Foundry user role on the project, even for a subscription
  Owner.
- **The portal is [ai.azure.com](https://ai.azure.com)**, not the general Azure
  portal. Agents you publish show up there under your project → Agents.

[`docs/foundry-notes.md`](docs/foundry-notes.md) goes further: what a server-side
agent is, and why organisations adopt Foundry beyond inference.

## Microsoft's AI landscape

Foundry is one of several Microsoft offerings for AI. Microsoft's own decision
order starts with whether you need an agent at all, and whether you need to
build one:

**0. No agent.** If the task needs no reasoning (static Q&A, plain content
generation), use ordinary code or a non-generative model.

**1. Adopt a ready-made agent (SaaS).** Check these first:

| Agent | For |
|---|---|
| Microsoft 365 Copilot and its built-in agents (Researcher, Analyst, App Builder, Workflows) | Work across Microsoft 365 data |
| GitHub Copilot | Coding |
| Fabric data agents | Questions over data in Microsoft Fabric |
| Azure Copilot | Your Azure environment |
| Dynamics 365 agents | Customer service and business processes |
| Security Copilot | Threat detection and response |

**2. Build your own**, from lightest to most control:

| Option | Kind | Fits |
|---|---|---|
| Agent Builder in Microsoft 365 Copilot | No-code; declarative agents on Copilot's own engine | Instructions plus knowledge, shared in your organisation. No actions on external services |
| Copilot Studio | Low-code SaaS (Power Platform) | Business teams; connectors, workflows, many channels |
| **Microsoft Foundry** | PaaS, low- or pro-code | Developers; your own models, tools, orchestration and hosting. **This lab** |
| Microsoft 365 Agents SDK + Agents Toolkit | Pro-code | Multi-channel agents with your own orchestration and models |
| Teams SDK | Pro-code | Agents that work with several people in Teams channels and meetings |
| GPUs and containers (AKS, Container Apps) | IaaS | Your own hosted models and agent runtime; full control, full responsibility |

**3. Building blocks**, usable with or without an agent:

| Building block | What it is |
|---|---|
| Foundry Models | The model catalogue and deployments. Azure OpenAI now lives here |
| Foundry Tools | Speech, Vision, Language, Document Intelligence, Translator and more. Formerly Azure AI services / Cognitive Services |
| Azure AI Search | Indexing and retrieval for RAG |
| Azure Machine Learning | Training and operating your own ML models |
| Foundry Local | Running models on your own device |

The names have changed several times: Azure AI Studio became Azure AI Foundry,
then Microsoft Foundry. Older articles and role names (Azure AI User → Foundry
User) still use the previous names.

## Foundry or Copilot Studio?

Of the build options above, these are the two people most often weigh against
each other. Microsoft positions them as its two main build paths, for different
builders and different levels of control.

| | Copilot Studio | Microsoft Foundry |
|---|---|---|
| Kind of service | SaaS, part of Power Platform | PaaS, a resource in your Azure subscription |
| How you build | Low/no-code, in the Copilot Studio designer | Pro-code with SDKs (Python, C#, Java…), or low-code prompt agents in the Foundry portal |
| Typical builder | Business teams, makers | Developers |
| Orchestration | Copilot Studio's own | Yours: a framework, your own code, or Foundry's agent service |
| Models | Chosen by Copilot Studio; Foundry models can be added | Your choice from the Foundry catalogue (OpenAI, Anthropic, Meta, Mistral…), deployed with your quota |
| Tools and data | Prebuilt connectors; native access to Microsoft 365 content | Your own code, OpenAPI, MCP, a tool catalogue; Microsoft 365 data through Graph |
| Where it runs for users | Microsoft 365 Copilot, Teams, websites, mobile and partner apps | Your own applications; can be published to Microsoft 365 Copilot and Teams |
| Billing | Copilot Studio licences and Copilot Credits | Azure consumption: model tokens and the tools you use. An idle agent costs nothing |
| Governance | Power Platform admin | Azure RBAC, Policy, networking (private networking in the standard setup) |

Microsoft's own example: an HR onboarding assistant that answers FAQs from
content already in Microsoft 365 goes to **Copilot Studio**. An agent with
custom AI logic and integrations, used in the company's own system and also
surfaced in Microsoft 365, goes to **Foundry**. The two also combine: Copilot
Studio can use Foundry models, and Foundry agents can be published into
Microsoft 365.

Microsoft's advice is to check first whether a ready-made agent (Microsoft 365
Copilot, GitHub Copilot, Dynamics 365…) already covers the need. Only if none
does, build one. If you do build, try both paths in short prototypes (one to two
weeks each) and pick the lightest one that meets the requirements.

**Why this lab uses Foundry:** it's about the code underneath an agent — the
tool loop, versioned definitions, sessions — and about comparing that code across
languages. Copilot Studio deliberately hides all of that.

Sources for this section and the landscape above (checked 2026-10-28), all
Microsoft Learn:
[Custom engine agents for Microsoft 365](https://learn.microsoft.com/en-us/microsoft-365/copilot/extensibility/overview-custom-engine-agent),
[Cloud Adoption Framework: technology plan for AI agents](https://learn.microsoft.com/en-us/azure/cloud-adoption-framework/ai-agents/technology-solutions-plan-strategy),
[Compare tools for declarative agents](https://learn.microsoft.com/en-us/microsoft-365/copilot/extensibility/declarative-agent-tool-comparison),
[What is Microsoft Foundry?](https://learn.microsoft.com/en-us/azure/foundry/what-is-foundry).
Licensing details change often; check
[Copilot Studio licensing](https://learn.microsoft.com/en-us/microsoft-copilot-studio/requirements-licensing-subscriptions)
before you plan a budget.

## Getting a Foundry project

You need a Foundry resource, a project, a chat-model deployment, and a data-plane
role on the project. [`files/provision.sh`](files/provision.sh) does all of it
with the Azure CLI, in eight commented steps:

1. Sign in and select the subscription
2. Register the resource providers (one-time, free)
3. Create the resource group
4. Create the Foundry resource (with project management enabled)
5. Give it a custom subdomain (needed for Entra sign-in and for projects)
6. Create the project
7. Check quota, then deploy a chat model under the name your code will use
8. Grant yourself the Foundry user role on the project

```bash
cp .env.example .env               # fill in tenant, subscription, and names you choose
set -a && source .env && set +a
zsh files/provision.sh             # safe to re-run
```

Then copy the project endpoint from **ai.azure.com → your project → overview**
into `FOUNDRY_PROJECT_ENDPOINT` in `.env`. Copy it rather than building it by hand;
its format has changed between versions.

Before step 7, check which models you hold quota for in your region. Quota is
granted per model, SKU and region, and a model can be in the catalogue with a
limit of 0. The script prints your quota before deploying.

Cost: nothing at rest. You pay per token for inference, and the whole lab costs
well under one euro to run. Capacity runs out before money does: at 3K TPM, two
runs back to back can return HTTP 429.

## The idea

The same small agent is built **twice**, in two folders, using two different
architectures. Reading them side by side is the point.

The whole lab then exists once per language: `python/`, `csharp/` and `java/`
today, with PHP to follow. What survives a change of language is the agent pattern;
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
| [`java/`](java/) | Java 21 / Maven, `azure-ai-agents` + a hand-written agent and tool loop | `lab-assistant-java` | [`java/README.md`](java/README.md) |

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

Python and C# use Microsoft's Agent Framework, which provides the agent and runs
the tool loop for you. It has no Java SDK, so the Java version writes its own
small agent class. That makes Java the place to read what the framework does.

## Shared setup

Requires the Azure CLI 2.67+ and a Foundry project with a chat model deployment
(see [Getting a Foundry project](#getting-a-foundry-project)). From the repo root,
with `.env` filled in:

```bash
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
