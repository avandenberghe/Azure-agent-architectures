"""Agent with two real tools, where the second call depends on the first.

order_agent.py answers one question with one lookup. That is a tool call, not
an agent loop. Here the goal is open-ended enough that the model has to decide
what to check, in what order, and what the combination means:

    "Can I deploy gpt-5-nano?"

It cannot answer that in one call. Azure keeps availability and entitlement in
two unrelated systems — the catalogue says what exists, quota says what you may
actually deploy. The model has to consult both and reconcile them.

Both tools are read-only `az` calls. Nothing here creates or deletes anything.
"""

import asyncio
import os
import subprocess
from typing import Annotated

from dotenv import load_dotenv
from pydantic import Field
from agent_framework.foundry import FoundryChatClient
from azure.identity import AzureCliCredential

load_dotenv()


def _required(name: str) -> str:
    """Read a setting from .env, failing with a useful message rather than a KeyError.

    These name a specific Azure lab. Hardcoding them made this script unrunnable
    by anyone else, and put identifiers in a tracked file.
    """
    value = os.environ.get(name)
    if not value:
        raise SystemExit(
            f"{name} is not set. Copy .env.example to .env and fill it in, "
            f"then run this from the project root or from local_agent/."
        )
    return value


RESOURCE = _required("AZURE_FOUNDRY_RESOURCE")
GROUP = _required("AZURE_RESOURCE_GROUP")
REGION = _required("AZURE_REGION")


def _az(*args: str) -> str:
    """Run an az command and return stdout.

    Errors come back as text rather than raising. A raised exception ends the
    run; a returned string lets the model read the failure and decide what to
    do about it — which is the behaviour you want from a tool.
    """
    try:
        done = subprocess.run(
            ["az", *args], capture_output=True, text=True, timeout=60
        )
    except FileNotFoundError:
        # az lives in /opt/homebrew/bin; GUI-launched PyCharm does not inherit it.
        return "ERROR: 'az' not found on PATH."
    except subprocess.TimeoutExpired:
        return "ERROR: az timed out after 60s."
    if done.returncode != 0:
        return f"ERROR: {done.stderr.strip()}"
    return done.stdout.strip()


def list_deployments() -> str:
    """List the model deployments that already exist in this Foundry resource."""
    return _az(
        "cognitiveservices", "account", "deployment", "list",
        "-n", RESOURCE, "-g", GROUP,
        "--query", "[].{deployment:name, model:properties.model.name, sku:sku.name}",
        "-o", "json",
    )


def check_quota(
    model: Annotated[str, Field(description="Model family to check, e.g. 'gpt-5-nano'")],
) -> str:
    """Check deployment quota for a model. Quota is granted per model, SKU and
    region, and is separate from whether the model appears in the catalogue.
    A limit of 0 means no allocation was ever granted."""
    return _az(
        "cognitiveservices", "usage", "list", "-l", REGION,
        "--query", f"[?contains(name.value, '{model}')].{{sku:name.value, used:currentValue, limit:limit}}",
        "-o", "json",
    )


async def main():
    client = FoundryChatClient(
        project_endpoint=os.environ["FOUNDRY_PROJECT_ENDPOINT"],
        model=os.environ["FOUNDRY_MODEL"],
        credential=AzureCliCredential(),
    )

    agent = client.as_agent(
        name="OpsAgent",
        instructions=(
            "You help with Azure Foundry deployments. "
            "Check the facts with your tools before answering; never guess "
            "whether something is deployable. A quota limit of 0 means no "
            "allocation exists, which is different from the model being absent."
        ),
        tools=[list_deployments, check_quota],
    )

    result = await agent.run(
        "I want to deploy gpt-5-nano in this project. Can I? "
        "If not, tell me what I already have that I could use instead."
    )
    print(result.text)


if __name__ == "__main__":
    asyncio.run(main())
