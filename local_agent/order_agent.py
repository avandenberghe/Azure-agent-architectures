"""Agent with a tool.

The model decides on its own whether to call get_order_status. Everything it
knows about that function comes from the type hints, the Field description and
the docstring — treat them as an API contract, not as comments.
"""

import asyncio
import os
from typing import Annotated

from dotenv import load_dotenv
from pydantic import Field
from agent_framework.foundry import FoundryChatClient
from azure.identity import AzureCliCredential

load_dotenv()


def get_order_status(
    order_id: Annotated[str, Field(description="Order ID, format ORD-12345")],
) -> str:
    """Look up the current delivery status of a customer order."""
    # A real implementation would call your order system here.
    # Note: the agent may call this more than once. Fine for a read;
    # anything that writes needs an idempotency key.
    return f"{order_id}: shipped, tracking BE998877, arriving 8 August"


async def main():
    client = FoundryChatClient(
        project_endpoint=os.environ["FOUNDRY_PROJECT_ENDPOINT"],
        model=os.environ["FOUNDRY_MODEL"],
        credential=AzureCliCredential(),
    )

    agent = client.as_agent(
        name="OrderAgent",
        # Instructions shape behaviour but do not enforce it. Anything that
        # MUST happen every time belongs in ordinary code, not in here.
        instructions=(
            "You help customers with order queries. "
            "Always look up the order before answering. Never guess a status. "
            "If the order ID is not in ORD-NNNNN format, ask for a valid one."
        ),
        tools=get_order_status,
    )

    result = await agent.run("Hi, where has my order ORD-40021 got to?")
    print(result.text)


if __name__ == "__main__":
    asyncio.run(main())
