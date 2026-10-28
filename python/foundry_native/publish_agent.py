"""Publish an agent INTO the Foundry project, as a named, versioned resource.

This is the difference the scripts in local_agent/ do not show.
`client.as_agent()` builds a Python object that dies with the process. Here the
agent definition is sent to Foundry and becomes a resource you can see in the
portal, connect to from anywhere, and version.

Nothing is containerised or "deployed" in the hosting sense. A PromptAgent is
declarative — a model reference plus instructions — and Foundry's own runtime
executes it. The existing my-model deployment is reused, not duplicated.

Run this once, then look at ai.azure.com.
"""

import os

from dotenv import load_dotenv, find_dotenv
from azure.identity import AzureCliCredential
from azure.ai.projects import AIProjectClient
from agent_framework.foundry import FoundryChatClient, to_prompt_agent

load_dotenv(find_dotenv())

AGENT_NAME = "lab-assistant"


def main():
    endpoint = os.environ["FOUNDRY_PROJECT_ENDPOINT"]
    credential = AzureCliCredential()

    # Step 1 — describe the agent locally. Identical to what the local_agent/ scripts do:
    # at this point it is still just a Python object with no server-side existence.
    client = FoundryChatClient(
        project_endpoint=endpoint,
        model=os.environ["FOUNDRY_MODEL"],
        credential=credential,
    )
    local_agent = client.as_agent(
        name=AGENT_NAME,
        instructions=(
            "You are a concise assistant for an Azure Foundry lab. "
            "Answer in at most three sentences. "
            "If you are unsure, say so rather than guessing."
        ),
    )

    # Step 2 — convert it into something Foundry can store. This lifts the model
    # deployment name off the bound client and translates generation settings.
    # Marked experimental by the library; expect this call to move.
    definition = to_prompt_agent(local_agent)

    # Step 3 — the actual write. From here the agent exists independently of
    # this process. Re-running creates a NEW version rather than overwriting,
    # which is what makes rollback possible.
    projects = AIProjectClient(endpoint=endpoint, credential=credential)
    version = projects.agents.create_version(
        agent_name=AGENT_NAME,
        definition=definition,
        description="Lab assistant published from publish_agent.py",
    )

    print(f"published: {AGENT_NAME}")
    print(f"version:   {getattr(version, 'version', '?')}")
    print()
    print("Now open ai.azure.com -> your project -> Agents. It is there.")
    print(f"Then: python run_agent.py {getattr(version, 'version', '')}")


if __name__ == "__main__":
    main()
