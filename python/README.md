# Foundry agent lab: Python

The Python version of the lab. The project overview is in the [root README](../README.md).
[`docs/language-comparison.md`](../docs/language-comparison.md) puts each step side by
side with the C# and Java versions.

Built on `agent-framework-foundry` (`agent_framework.foundry`) and `azure-ai-projects`.

## Layout

| Path | What it is |
|---|---|
| `local_agent/hello_agent.py` | One client, one agent, one question. No tools |
| `local_agent/order_agent.py` | One tool, one call. The tool returns a fixed string, so it shows only the mechanism |
| `local_agent/ops_agent.py` | Two read-only `az` tools. The second call depends on what the first returned, so this is the first real loop |
| `foundry_native/publish_agent.py` | Turns a local definition into a prompt agent with `to_prompt_agent()` and publishes it as `lab-assistant` |
| `foundry_native/run_agent.py` | Connects to `lab-assistant` by name and version and holds a two-turn session |
| `foundry_native/cleanup.py` | Lists agents and versions. `--delete` removes `lab-assistant` |
| `requirements.txt` | The four direct dependencies, pinned |
| `constraints.txt` | The full known-good dependency set, pulled in by `requirements.txt` |

`local_agent/` creates nothing in Azure: the agent exists only in the process.
`foundry_native/` creates a named, versioned agent in the Foundry project.

## Setup

Requires Python 3.12+ (the lab uses 3.14 from Homebrew), the Azure CLI, and the
filled-in `.env` at the repo root. Run these from the repo root:

```bash
python3 -m venv python/.venv && source python/.venv/bin/activate
pip install -r python/requirements.txt

az login --tenant "$AZURE_TENANT_ID"
```

`load_dotenv()` walks up from each **script's folder** to find the root `.env`, so
the scripts work from any working directory.

## Run

```bash
python python/local_agent/hello_agent.py
python python/local_agent/order_agent.py
python python/local_agent/ops_agent.py

python python/foundry_native/publish_agent.py   # writes to the project, creates a new version each run
python python/foundry_native/run_agent.py 1     # version to connect to, default 1
python python/foundry_native/cleanup.py         # list only; --delete removes lab-assistant
```

The Ops tools are plain functions, so you can exercise them without spending tokens:

```bash
cd python/local_agent && ../.venv/bin/python -c "import ops_agent; print(ops_agent.check_quota('gpt-5-nano'))"
```

## Python-specific notes

- **Don't `pip install agent-framework`.** It resolves to every provider Microsoft
  ships, and pip fails with `resolution-too-deep`. `agent-framework-foundry` gives
  you the same `agent_framework.foundry` namespace with a fraction of the packages.
- **Bump `constraints.txt` deliberately.** Pinning only the direct dependencies is
  not enough. On 2026-10-28 a fresh install pulled in `openai` 3.x with `httpx2`, and
  `azure-ai-projects` failed with `No module named 'httpx'`. To upgrade: change
  `requirements.txt`, install into a fresh venv without `-c`, test every script,
  then regenerate `constraints.txt` with `pip freeze`.
- **The tool schema comes from your signature.** Type hints,
  `Annotated[..., Field(description=...)]` and the docstring are what the model
  sees. Treat them as an API contract.
- **`to_prompt_agent()` is experimental** and warns about it at runtime.
- **PyCharm** needs `python/.venv` as its interpreter. Launched from the Dock, it
  doesn't see your shell's PATH, so add `/opt/homebrew/bin` to PATH in each run
  configuration. Without it, `AzureCliCredential` can't find `az`.
- **HTTP 429** means the 3K TPM capacity is used up, not that the code is broken.
  Wait a minute.
