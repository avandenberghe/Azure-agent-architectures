"""Connect to the published agent and hold a two-turn conversation.

Two things here that the root scripts cannot do:

1. The agent is not defined in this file. It is referenced by name and version.
   The instructions live in Foundry — edit them in the portal, re-run this, and
   the behaviour changes without touching code.

2. The conversation has a session. Turn two knows what turn one said, and that
   memory is held by the service, not by a variable in this process.

Usage:  python run_agent.py [version]
"""

import asyncio
import os
import sys

from dotenv import load_dotenv, find_dotenv
from azure.identity import AzureCliCredential
from agent_framework.foundry import FoundryAgent

load_dotenv(find_dotenv())

AGENT_NAME = "lab-assistant"


async def main():
    version = sys.argv[1] if len(sys.argv) > 1 else "1"

    # No instructions, no model, no tools defined here — only a reference.
    # Everything that shapes behaviour lives in the project.
    agent = FoundryAgent(
        project_endpoint=os.environ["FOUNDRY_PROJECT_ENDPOINT"],
        agent_name=AGENT_NAME,
        agent_version=version,
        credential=AzureCliCredential(),
    )

    session = agent.create_session()

    first = await agent.run(
        "I am setting up a lab in the swedencentral region. Note that.",
        session=session,
    )
    print("turn 1:", first.text)
    print()

    # The proof. This question is unanswerable without the previous turn, and
    # nothing in this file is holding that history.
    second = await agent.run("Which region did I just say I was using?", session=session)
    print("turn 2:", second.text)


if __name__ == "__main__":
    asyncio.run(main())
