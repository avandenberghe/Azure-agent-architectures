# Foundry agent lab: Java

The Java version of the lab: the same six steps as the [Python](../python/README.md)
and [C#](../csharp/README.md) versions, with the same prompts, instructions and
output. The project overview is in the [root README](../README.md).
[`docs/language-comparison.md`](../docs/language-comparison.md) puts each step side
by side across the languages.

Built on Java 21 (Amazon Corretto), Maven, `com.azure:azure-ai-agents` and
`com.openai:openai-java`.

## What's different about Java

Microsoft's Agent Framework, which the Python and C# versions use, has no Java
SDK. Java has the Azure agents client, which publishes and calls agents, but no
**agent object** and no **tool loop**. So this lab writes its own:

- [`lab/agent/Agent.java`](src/main/java/lab/agent/Agent.java) is the agent.
  `run()` holds the loop the frameworks hide: send the prompt, run any tool the
  model asks for, send the result back, repeat until the model answers in text,
  give up after `MAX_ROUNDS`. Read it first; it's commented step by step.
- [`lab/agent/Tool.java`](src/main/java/lab/agent/Tool.java) is a tool: name,
  description, JSON Schema, and the Java function to call. Python and C# derive
  the schema from the function signature; here it's written out, so you see
  exactly what the model receives.

Run any local step with `-Dagent.trace=true` to watch the loop's rounds on stderr.

## Layout

| Class | Python counterpart | What it is |
|---|---|---|
| `lab.local.HelloAgent` | `hello_agent.py` | One agent, one question. No tools |
| `lab.local.OrderAgent` | `order_agent.py` | One tool. The tool returns a fixed string |
| `lab.local.OpsAgent` (+ `OpsTools`) | `ops_agent.py` | Two read-only `az` tools that chain. `--tools-only` runs the tools without the model |
| `lab.foundry.PublishAgent` | `publish_agent.py` | Writes a `PromptAgentDefinition` as a new version of `lab-assistant-java` |
| `lab.foundry.RunAgent` | `run_agent.py` | Connects to `lab-assistant-java` by name and version, holds a two-turn session |
| `lab.foundry.Cleanup` | `cleanup.py` | Lists agents and versions. `--delete` removes `lab-assistant-java` |
| `lab.agent.Agent`, `lab.agent.Tool` | (the framework) | The hand-written agent and tool loop |
| `lab.shared.Settings` | `load_dotenv`, `_required` | `.env` loading, required settings, credential |

Other files:

| File | Purpose |
|---|---|
| `pom.xml` | Dependencies and plugins, every version exact |
| `.run/*.run.xml` | IntelliJ run configurations, one per step. They put `/opt/homebrew/bin` on PATH (see below) |

Java publishes its own agent, `lab-assistant-java`, so cleaning it up never
touches the Python or C# agents.

## Setup

Requires JDK 21+ (the lab uses Amazon Corretto 21), Maven 3.9+, the Azure CLI,
and the filled-in `.env` at the repo root.

```bash
java -version                        # 21.x
mvn -v                               # 3.9+, running on that JDK
mvn -q -f java/pom.xml compile       # from the repo root
az login --tenant "$AZURE_TENANT_ID"
```

`Settings.load()` walks up from the **working directory** to find the root `.env`.
Java can't change its own environment, so settings are read from the real
environment first and from `.env` second. A variable already set in your shell
wins, as in Python and C#.

## Run

From the repo root, each step is a main class run through Maven:

```bash
mvn -q -f java/pom.xml exec:java -Dexec.mainClass=lab.local.HelloAgent
mvn -q -f java/pom.xml exec:java -Dexec.mainClass=lab.local.OrderAgent
mvn -q -f java/pom.xml exec:java -Dexec.mainClass=lab.local.OpsAgent
mvn -q -f java/pom.xml exec:java -Dexec.mainClass=lab.local.OpsAgent -Dexec.args=--tools-only
mvn -q -f java/pom.xml exec:java -Dexec.mainClass=lab.local.OpsAgent -Dagent.trace=true   # watch the loop

mvn -q -f java/pom.xml exec:java -Dexec.mainClass=lab.foundry.PublishAgent   # writes to the project
mvn -q -f java/pom.xml exec:java -Dexec.mainClass=lab.foundry.RunAgent -Dexec.args=1
mvn -q -f java/pom.xml exec:java -Dexec.mainClass=lab.foundry.Cleanup       # -Dexec.args=--delete removes it
```

**In IntelliJ IDEA:** open the `java/` folder (or `java/pom.xml`) as a project.
The run configurations from `.run/` appear in the run dropdown. `RunAgent` runs
version 1; edit its program arguments for another.

## Java-specific notes

- **The agent and the loop are yours.** `run()`, `send()` and `invoke()` in
  `Agent.java` are about 90 lines including comments (the whole class is about
  150 lines of code). That's the logic `agent.run()` / `RunAsync()` hide in the
  other two languages. Things
  you'd otherwise never see:
  - a cap on rounds (`MAX_ROUNDS`), so a model that keeps calling tools can't
    loop and bill forever.
  - **where the history lives**: without a session, the client resends the
    growing list every round (`store(false)`, nothing kept on the server). With a
    session, the server conversation holds it and each round sends only the new
    tool results.
  - tool failures go back to the model as text, never as exceptions.
- **Per-call version pinning.** `RunAgent` selects the version on every request
  (`AzureCreateResponseOptions.setAgentReference(new AgentReference(name).setVersion(v))`),
  like Python's `agent_version=` and C#'s `AgentReference`. Microsoft's Java sample
  for pinning instead rewrites the agent's endpoint settings
  (`updateAgentDetails`). That is a server-side write that changes what *every*
  caller gets. The lab doesn't use it.
- **Names.** The prompt agent definition is `PromptAgentDefinition`, as in
  Microsoft's docs (C#'s package calls it `DeclarativeAgentDefinition`).
  Publishing returns `AgentVersionDetails`; listing gives `AgentDetails`.
- **Pinning.** Every version in `pom.xml` is exact, and the Azure and OpenAI
  libraries declare exact versions for their own dependencies. That makes Maven's
  resolution reproducible without a separate lock file. Python needs
  `constraints.txt` and C# needs `packages.lock.json` to get the same guarantee.
- **The build is strict.** `-Xlint:all -Werror` turns compiler warnings into
  errors, and the enforcer plugin rejects a JDK older than 21 or Maven older
  than 3.9.
- **SLF4J.** The Azure SDK logs through SLF4J. `slf4j-nop` discards those logs;
  without it every run starts with a "no binding" warning.
- **PATH in the IDE.** IntelliJ launched from the Dock doesn't inherit your
  shell's PATH. `AzureCliCredential` shells out to `az`, so every step would fail
  to sign in. The `.run/` configurations set
  `PATH=/opt/homebrew/bin:…`; change them if your `az` lives elsewhere.
- **HTTP 429** means the 3K TPM capacity is used up, not that the code is broken.
  An agent run with tools is several model calls. Wait a minute between runs.
