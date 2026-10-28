// Agent with a tool.
//
// The model decides on its own whether to call GetOrderStatus. Everything it
// knows about that function comes from the parameter types and the
// [Description] attributes — treat them as an API contract, not as comments.
// AIFunctionFactory reads them by reflection and builds the tool schema, the
// way Python's agent framework reads type hints, Field and the docstring.
//
// Counterpart of python/local_agent/order_agent.py.

using System.ComponentModel;
using AgentArchitectures.Shared;
using Azure.AI.Projects;
using Microsoft.Agents.AI;
using Microsoft.Extensions.AI;

Settings.Load();

var client = new AIProjectClient(
    new Uri(Settings.Required("FOUNDRY_PROJECT_ENDPOINT")),
    Settings.Credential);

AIAgent agent = client.AsAIAgent(
    model: Settings.Required("FOUNDRY_MODEL"),
    name: "OrderAgent",
    // Instructions shape behaviour but do not enforce it. Anything that
    // MUST happen every time belongs in ordinary code, not in here.
    instructions:
        "You help customers with order queries. " +
        "Always look up the order before answering. Never guess a status. " +
        "If the order ID is not in ORD-NNNNN format, ask for a valid one.",
    tools: [AIFunctionFactory.Create(GetOrderStatus)]);

var result = await agent.RunAsync("Hi, where has my order ORD-40021 got to?");
Console.WriteLine(result.Text);

[Description("Look up the current delivery status of a customer order.")]
static string GetOrderStatus(
    [Description("Order ID, format ORD-12345")] string orderId)
{
    // A real implementation would call your order system here.
    // Note: the agent may call this more than once. Fine for a read;
    // anything that writes needs an idempotency key.
    return $"{orderId}: shipped, tracking BE998877, arriving 8 August";
}
