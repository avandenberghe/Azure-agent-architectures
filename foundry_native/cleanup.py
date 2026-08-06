"""List and delete the published agent.

Publishing creates a resource that outlives the process — which is the point,
and also the reason this script exists. Anything you create, you should be able
to remove in one command.

Usage:
    python cleanup.py           # list versions, delete nothing
    python cleanup.py --delete  # delete the agent and all its versions
"""

import os
import sys

from dotenv import load_dotenv, find_dotenv
from azure.identity import AzureCliCredential
from azure.ai.projects import AIProjectClient

load_dotenv(find_dotenv())

AGENT_NAME = "lab-assistant"


def main():
    projects = AIProjectClient(
        endpoint=os.environ["FOUNDRY_PROJECT_ENDPOINT"],
        credential=AzureCliCredential(),
    )

    print("agents in this project:")
    for a in projects.agents.list():
        print(f"  {getattr(a, 'name', a)}")

    print(f"\nversions of {AGENT_NAME}:")
    try:
        for v in projects.agents.list_versions(agent_name=AGENT_NAME):
            print(f"  version {getattr(v, 'version', '?')}"
                  f"  {getattr(v, 'description', '') or ''}")
    except Exception as exc:
        print(f"  could not list: {exc}")

    if "--delete" not in sys.argv:
        print("\nnothing deleted. pass --delete to remove the agent.")
        return

    projects.agents.delete(agent_name=AGENT_NAME)
    print(f"\ndeleted: {AGENT_NAME}")


if __name__ == "__main__":
    main()
