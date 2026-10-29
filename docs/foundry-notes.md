# Foundry platform notes

Reference material on what Microsoft Foundry offers beyond the inference endpoint
this project uses. Moved out of `CLAUDE.md`, which should stay operational.

See also: `docs/architecture.md` for the local architecture and why it is not
Foundry-centric, and `docs/architecture2.md` for the Foundry-native one that answers it.

## What a server-side agent actually is

Verified from the installed package. `FoundryAgent`'s docstring:

> Connects to an existing PromptAgent or HostedAgent in Foundry.
> This is the recommended class for production use.

```python
FoundryAgent(project_endpoint=..., agent_name="my-prompt-agent",
             agent_version="1.0", credential=...)
```

The correct terms are **PromptAgent** and **HostedAgent**. The agent lives in the
project, addressed by name *and version*; `FoundryAgent` connects to one rather
than creating it in Python. `agent_version` is the load-bearing parameter:

- Instruction changes stop being code changes — ship v1.1 without redeploying,
  roll back by pointing at v1.0.
- The instructions are editable in the portal, so non-engineers can change
  behaviour without a pull request.
- Runs are attributable to an agent identity, which makes "did v1.1 regress?"
  answerable by evaluation rather than by opinion.

It also exposes `create_conversation()` and `create_session()` — conversation
state held by the service instead of by the caller.

`client.as_agent()`, which the `python/local_agent/` scripts use, has none of this. It is an
in-memory object; when the process exits, the agent never existed.

## Why organisations use Foundry beyond inference

Inference is the commodity part and can be bought anywhere. The platform sells
what surrounds it:

| Capability | SDK evidence | Why it is bought |
|---|---|---|
| Identity and compliance boundary | managed identity, Entra token flow | Prompts and documents stay inside the tenant. In regulated sectors this outranks model quality |
| Grounding on private data | `FileSearchConfig`, `ContentUnderstandingContextProvider` | Retrieval over internal documents without building chunking, embedding and a vector store |
| Evaluation | `evaluate_traces`, `evaluate_foundry_target` | Regression-testing prompt changes; otherwise prompts are untested code |
| Hosting | `InvocationsHostServer`, `ResponsesHostServer` | Agent runs as a managed endpoint with its own identity and scaling |
| Model portfolio | `AnthropicFoundryClient`, `FoundryLocalClient` | Claude and others under the existing Azure contract — no separate vendor or data-processing review. Local client covers edge |
| Inherited governance | resource type is `Microsoft.CognitiveServices/accounts` | Private endpoints, customer-managed keys, Azure Policy and diagnostics already apply; nothing new passes security review |

In one line: organisations adopt Foundry not to get a model, but so the model sits
inside an identity, compliance and observability perimeter they already own.

**Confidence.** The SDK surface above was verified against the installed package.
The claims about *why* organisations buy it are general knowledge with a May 2026
cutoff. Microsoft renamed this product recently and the emphasis moves quickly —
check current Microsoft material before repeating any of it externally.
