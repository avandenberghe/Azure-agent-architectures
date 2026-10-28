# Architecture — iteration 2, Foundry-centric

Companion to `docs/architecture.md`. Same C4 levels, same project, different
architecture. Read them side by side: the annotations **A1–A6** in the first
document are the checklist this one is measured against.

**What changed in one sentence:** the agent stopped being a Python object and
became a resource in the project, addressed by name and version.

```
iteration 1                          iteration 2
client.as_agent(...)                 FoundryAgent(agent_name="lab-assistant",
  → object, dies at exit                          agent_version="1")
                                       → reference to a published resource
```

Code lives in `python/foundry_native/`. The iteration-1 scripts remain in
`python/local_agent/`, unchanged, for comparison. The C# counterparts are the
`csharp/FoundryNative.*` and `csharp/LocalAgent.*` projects.

---

## What is deployed, and what is not

The word "deploy" covers three unrelated things here, and conflating them causes
most of the confusion:

| | Status in iteration 2 |
|---|---|
| **Model deployment** (`my-model`) | Unchanged. Reused, not duplicated. A PromptAgent references a deployment; it does not create one |
| **Agent version** | **New.** Created over the API by `publish_agent.py`. Appears in ai.azure.com, but the portal is never used to make it |
| **Hosted agent** (code, containerised) | **Not used.** That is `create_version_from_code` plus `agent-framework-foundry-hosting`. A PromptAgent is declarative — Foundry's runtime executes it, so there is no image and no endpoint to stand up |

---

## Level 1 — System Context

```mermaid
graph TB
    dev["<b>Developer</b><br/><i>[Person]</i><br/>Publishes a definition,<br/>then invokes it"]

    subgraph foundry ["Microsoft Foundry — <project>"]
        agentres["<b>lab-assistant</b><br/><i>[Agent resource, v1]</i><br/>Instructions and model<br/>reference live HERE"]
        session["<b>Session</b><br/><i>[Service-managed state]</i><br/>Conversation history"]
        model["<b>my-model</b><br/><i>[Deployment]</i><br/>gpt-5.4-mini"]
    end

    caller["<b>foundry_native/</b><br/><i>[Software System]</i><br/>Thin scripts: publish,<br/>invoke, delete.<br/>Hold no behaviour"]

    entra["<b>Microsoft Entra ID</b><br/><i>[External System]</i>"]

    dev --> caller
    caller -->|"create_version(definition)"| agentres
    caller -->|"run(msg, session=...)"| agentres
    agentres --> model
    agentres --> session
    entra -.->|"token via az login"| caller

    classDef person fill:#08427b,stroke:#052e56,color:#fff
    classDef system fill:#1168bd,stroke:#0b4884,color:#fff
    classDef external fill:#999,stroke:#6b6b6b,color:#fff
    classDef inside fill:#2d9e5f,stroke:#1c6b3f,color:#fff
    class dev person
    class caller system
    class entra external
    class agentres,session,model inside
```

**The structural difference from iteration 1.** There, Foundry was one grey box on
the edge returning completions. Here it is a boundary with things inside it that
belong to the system. Instructions — the thing that determines what the agent
actually does — moved across that line.

The find-and-replace test from iteration 1 no longer passes. `agent_framework.openai`
has no equivalent of `create_version` against a project, so the vendor is now load-bearing.

---

## Level 2 — Containers

```mermaid
graph TB
    subgraph mac ["MacBook"]
        pub["<b>publish_agent.py</b><br/><i>[Python]</i><br/>Defines locally,<br/>converts, publishes.<br/>Run once"]
        run["<b>run_agent.py</b><br/><i>[Python]</i><br/>Holds NO instructions.<br/>References name+version"]
        clean["<b>cleanup.py</b><br/><i>[Python]</i><br/>list / delete"]
        envf[("<b>.env</b><br/>endpoint + deployment")]
    end

    subgraph proj ["Foundry project — <project>"]
        v1["<b>lab-assistant v1</b><br/><i>[PromptAgentDefinition]</i><br/>model + instructions<br/>+ generation settings"]
        sess["<b>AgentSession</b><br/><i>[conversation state]</i>"]
        dep["<b>my-model</b><br/><i>[deployment]</i>"]
    end

    pub -->|"to_prompt_agent()<br/>then create_version()"| v1
    run -->|"FoundryAgent(name, version)<br/>create_session()"| sess
    run -->|"run(messages, session)"| v1
    clean -->|"list_versions / delete"| v1
    v1 --> dep
    sess -.->|"history replayed<br/>by the service"| v1
    envf -.-> pub
    envf -.-> run
    envf -.-> clean

    classDef container fill:#438dd5,stroke:#2e6295,color:#fff
    classDef store fill:#438dd5,stroke:#2e6295,color:#fff
    classDef inside fill:#2d9e5f,stroke:#1c6b3f,color:#fff
    class pub,run,clean container
    class envf store
    class v1,sess,dep inside
```

**Elements**

| Container | Responsibility | Note |
|---|---|---|
| `publish_agent.py` | Builds a local `Agent`, converts with `to_prompt_agent()`, writes it with `create_version()` | Re-running creates a *new version* rather than overwriting — which is what makes rollback possible |
| `run_agent.py` | Connects by name and version, opens a session, sends turns | Contains no instructions, no model name, no tools. Delete this file and the agent still exists |
| `cleanup.py` | Lists versions; deletes on `--delete` | Anything created should be removable in one command |
| `lab-assistant v1` | The agent itself | Editable in the portal. Change the instructions there, re-run `run_agent.py` untouched, behaviour changes |
| `AgentSession` | Conversation history | Held by the service. No local variable carries it |

---

## Level 3 — The two flows

Publishing and invoking are separate lifecycles, which is the point. The first
happens rarely, the second happens constantly.

```mermaid
sequenceDiagram
    participant D as Developer
    participant P as publish_agent.py
    participant R as run_agent.py
    participant F as Foundry project

    Note over D,F: PUBLISH - once, or on each instruction change
    D->>P: python publish_agent.py
    P->>P: client.as_agent(name, instructions)
    P->>P: to_prompt_agent() returns PromptAgentDefinition
    P->>F: agents.create_version(name, definition)
    F-->>P: AgentVersionDetails(version=1)
    Note over F: resource exists, visible at ai.azure.com

    Note over D,F: INVOKE - repeatedly, by anyone with access
    D->>R: python run_agent.py 1
    R->>F: FoundryAgent(agent_name, agent_version)
    F-->>R: bound to published definition
    R->>F: create_session()
    F-->>R: AgentSession
    R->>F: run("lab is in swedencentral", session)
    F-->>R: "Noted - Sweden Central"
    R->>F: run("which region did I say?", session)
    Note over F: history is service-side, caller sent no context
    F-->>R: "Sweden Central."
    R-->>D: prints both turns
```

That second turn is the observable proof. `run_agent.py` never resent the first
message, and holds no history variable.

---

## Deployment

```mermaid
graph TB
    subgraph mac ["MacBook — macOS"]
        scripts["<b>foundry_native/*.py</b><br/>thin callers only"]
        cred["<b>AzureCliCredential</b><br/><i>still a human's az login</i>"]
    end

    subgraph azure ["Azure — swedencentral"]
        subgraph res ["<foundry-resource>"]
            proj2["<b><project></b>"]
            agentv["<b>lab-assistant v1</b><br/>instructions + model ref"]
            sessions["<b>Sessions</b><br/>conversation state"]
            deployed["<b>my-model</b><br/>gpt-5.4-mini"]
        end
    end

    scripts -->|HTTPS| proj2
    proj2 --> agentv
    agentv --> deployed
    proj2 --> sessions
    cred -.-> scripts

    classDef container fill:#438dd5,stroke:#2e6295,color:#fff
    classDef inside fill:#2d9e5f,stroke:#1c6b3f,color:#fff
    class scripts,cred container
    class proj2,agentv,sessions,deployed inside
```

Compare with the deployment diagram in iteration 1, where everything except
inference sat inside the laptop boundary. Here the laptop holds only credentials
and three scripts that could be deleted and rewritten in any language.

---

## Scorecard against A1–A6

| | Iteration 1 | Iteration 2 | Status |
|---|---|---|---|
| **A1** Foundry's role | Inference endpoint | A boundary containing the agent, its versions and its sessions | **resolved** |
| **A2** Where the loop runs | Entirely local | Service-side — this agent has no local tools, so nothing executes on the laptop | **resolved** |
| **A3** Conversation state | None; every run cold | `create_session()`, history held by the service | **resolved** |
| **A4** Agent identity | In-memory object | `lab-assistant`, version 1, addressable and rollback-able | **resolved** |
| **A5** Tools | Local functions Azure never sees | **No tools at all.** Sidestepped, not solved | **unresolved** |
| **A6** Credential | A human's `az login` | Still `AzureCliCredential` — the caller is a person at a terminal | **unresolved** |

### Being honest about A5

A PromptAgent's tools must be things *Foundry* can execute. `ops_agent.py`'s
`check_quota()` shells out to the local `az` binary and cannot run inside Azure.
So this iteration did not move tools server-side — it dropped them.

Three ways forward, in increasing order of effort:

1. **Hybrid.** `FoundryAgent.run()` accepts `tools=`, so local functions can be
   attached at call time to a published agent. Definition is server-side,
   execution is local. Honest and useful, but partially reopens A2.
2. **Hosted tools.** `FileSearchConfig`, `FoundryToolbox` — capability that lives
   in the platform and is shared across agents. Needs extra packages.
3. **Hosted agent.** Package the tool code and deploy it with
   `create_version_from_code`. Fully resolves A2, A5 and A6 together, and is the
   only route that makes the tools reusable by anything other than this laptop.

### Being honest about A6

The agent runs under Foundry's runtime, but *invocation* is still authorised by an
interactive `az login`. Nothing here runs unattended. That only changes with a
hosted agent or a service principal, and the RBAC granted in setup step 8 would
have to be re-granted to that identity.

---

## What is verified and what is not

Everything above was run against the live `<project>` project on 2026-08-06:

- `publish_agent.py` created `lab-assistant` version 1 — confirmed by `cleanup.py`
  listing it back
- `run_agent.py` held a two-turn session; turn 2 correctly answered "Sweden Central"
  from turn 1's context
- The delete path was exercised against a throwaway agent, so `--delete` is tested
  without having destroyed the demo

**Not verified: portal tracing.** Iteration 1's tracing failed because the loop ran
locally. That reason is gone — the loop is now service-side, so traces *should*
appear at ai.azure.com. I have not been able to confirm this, and it should be
checked before being repeated as fact.

**`to_prompt_agent` is experimental**, and says so at runtime with an
`ExperimentalWarning`. Expect this call to change.
