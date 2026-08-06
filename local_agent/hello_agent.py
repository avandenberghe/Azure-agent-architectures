"""Minimal Foundry agent — verifies the whole chain works end to end."""

import asyncio
import os

from dotenv import load_dotenv
from agent_framework.foundry import FoundryChatClient
from azure.identity import AzureCliCredential

load_dotenv()  # not automatic — must be called


async def main():
    # The client is your connection to the Foundry project.
    client = FoundryChatClient(
        project_endpoint=os.environ["FOUNDRY_PROJECT_ENDPOINT"],  # where
        model=os.environ["FOUNDRY_MODEL"],                        # which deployment
        credential=AzureCliCredential(),                          # who you are
    )

    # The agent wraps the client with a standing brief.
    agent = client.as_agent(
        name="HelloAgent",
        instructions="You are a concise, friendly assistant.",
    )

    result = await agent.run("What is the largest city in Belgium?")
    print(result.text)


if __name__ == "__main__":
    asyncio.run(main())
