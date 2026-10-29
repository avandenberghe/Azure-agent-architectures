#!/usr/bin/env zsh
# =====================================================================
#  Foundry lab provisioning  —  steps 1 to 8 of the setup guide
#
#  Run:  zsh provision.sh
#  Safe to re-run: resource creates are idempotent, role assignment
#  will report an existing assignment rather than failing.
# =====================================================================

set -eu   # abort on error or unset variable

# ---------------------------------------------------------------------
# Settings — read from .env. From the repo root:  set -a && source .env && set +a
# Nothing here is hardcoded, so this file is safe in a public repo.
# ---------------------------------------------------------------------
TENANT="${AZURE_TENANT_ID:?set it in .env — az account show --query tenantId -o tsv}"
SUBSCRIPTION="${AZURE_SUBSCRIPTION_ID:?set it in .env — az account show --query id -o tsv}"

LOC="${AZURE_REGION:-swedencentral}"
RG="${AZURE_RESOURCE_GROUP:?resource group name — pick your own}"
RES="${AZURE_FOUNDRY_RESOURCE:?globally unique across Azure — pick your own}"
PROJ="${AZURE_PROJECT:?project name inside the resource — pick your own}"

DEPLOYMENT=my-model             # your label — what your code references
MODEL=gpt-5.4-mini              # a CHAT model, not audio/image/codex
MODEL_VERSION=2026-03-17
SKU=DataZoneStandard            # must be one you hold quota for
CAPACITY=3                      # capped by the model's maxCapacity

FOUNDRY_USER_ROLE=53ca6127-db72-4b80-b1b0-d745d6d5456d


# ---------------------------------------------------------------------
# 1. Point the CLI at the right directory and subscription
# ---------------------------------------------------------------------
az login --tenant "$TENANT"
az account set --subscription "$SUBSCRIPTION"

# Verify before creating anything — the wrong tenant means the wrong estate
echo "==> Active context:"
az account show --query "{sub:name, tenant:tenantId}" -o table


# ---------------------------------------------------------------------
# 2. Register resource providers (one-time per subscription, free)
# ---------------------------------------------------------------------
echo "==> Registering resource providers..."
for ns in Microsoft.CognitiveServices Microsoft.Storage Microsoft.KeyVault \
          Microsoft.Insights Microsoft.OperationalInsights \
          Microsoft.ContainerRegistry Microsoft.App Microsoft.Search; do
  az provider register --namespace "$ns"
done

# Block until the one we need right now is ready
az provider register --namespace Microsoft.CognitiveServices --wait


# ---------------------------------------------------------------------
# 3. Resource group — the container for everything else
# ---------------------------------------------------------------------
az group create --name "$RG" --location "$LOC"


# ---------------------------------------------------------------------
# 4. The Foundry resource
#    --allow-project-management is REQUIRED; without it, step 6 fails
# ---------------------------------------------------------------------
az cognitiveservices account create \
  --name "$RES" --resource-group "$RG" \
  --kind AIServices --sku s0 --location "$LOC" \
  --allow-project-management


# ---------------------------------------------------------------------
# 5. Custom subdomain
#    Enables Entra token auth AND gives projects a namespace to live in.
#    Must complete before step 6.
# ---------------------------------------------------------------------
az cognitiveservices account update \
  --name "$RES" --resource-group "$RG" --custom-domain "$RES"


# ---------------------------------------------------------------------
# 6. The project — what your code connects to
# ---------------------------------------------------------------------
# Guard: a null subdomain returns an EMPTY string from -o tsv, not "null"
SUBDOMAIN=$(az cognitiveservices account show \
  --name "$RES" --resource-group "$RG" \
  --query properties.customSubDomainName -o tsv)

if [ -z "$SUBDOMAIN" ]; then
  echo "STOP: $RES has no custom subdomain — step 5 did not take effect."
  exit 1
fi

az cognitiveservices account project create \
  --name "$RES" --resource-group "$RG" \
  --project-name "$PROJ" --location "$LOC"

echo "==> Projects in $RES:"
az cognitiveservices account project list \
  --name "$RES" --resource-group "$RG" -o table


# ---------------------------------------------------------------------
# 7. Deploy a model
# ---------------------------------------------------------------------
# What can this resource offer? (informational)
#   NOTE: the filter field is 'format', not 'kind'.
echo "==> Available chat models:"
az cognitiveservices account list-models \
  --name "$RES" --resource-group "$RG" \
  --query "[?format=='OpenAI' && isDefaultVersion && lifecycleStatus=='GenerallyAvailable' && contains(name,'gpt')].{model:name, version:version, maxCap:maxCapacity}" \
  -o table

# What do you actually hold QUOTA for? Availability != entitlement.
#   Single quotes: JMESPath number literals use backticks, which would be
#   command substitution inside double quotes.
echo "==> Quota you hold in $LOC:"
az cognitiveservices usage list -l "$LOC" \
  --query '[?limit > `0`].{name:name.value, used:currentValue, limit:limit}' \
  -o table

az cognitiveservices account deployment create \
  --name "$RES" --resource-group "$RG" \
  --deployment-name "$DEPLOYMENT" \
  --model-name "$MODEL" --model-version "$MODEL_VERSION" \
  --model-format OpenAI \
  --sku-capacity "$CAPACITY" --sku-name "$SKU"

echo "==> Deployments:"
az cognitiveservices account deployment list \
  --name "$RES" --resource-group "$RG" -o table


# ---------------------------------------------------------------------
# 8. Data-plane access
#    Creating a resource does NOT grant permission to use it.
#    Control plane (ARM) and data plane (project endpoint) authorise
#    separately — subscription Owner gives you nothing here.
# ---------------------------------------------------------------------
PROJECT_ID=$(az cognitiveservices account project show \
  --name "$RES" --resource-group "$RG" --project-name "$PROJ" \
  --query id -o tsv)

MY_ID=$(az ad signed-in-user show --query id -o tsv)

echo "==> Scope:     $PROJECT_ID"
echo "==> Principal: $MY_ID"

az role assignment create \
  --role "$FOUNDRY_USER_ROLE" \
  --assignee-object-id "$MY_ID" \
  --assignee-principal-type User \
  --scope "$PROJECT_ID"

echo "==> Role assignments on the project:"
az role assignment list --scope "$PROJECT_ID" -o table


# ---------------------------------------------------------------------
# Done
# ---------------------------------------------------------------------
cat <<EOF

Provisioning complete.

Next:
  1. Wait 30-60s for the role assignment to propagate.
  2. Copy your project endpoint from ai.azure.com (project welcome screen).
  3. Put it in .env along with:  FOUNDRY_MODEL=$DEPLOYMENT

Teardown when finished:
  az group delete --name $RG --yes --no-wait

EOF
