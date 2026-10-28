package lab.local;

import com.azure.ai.agents.AgentsClientBuilder;
import lab.agent.Agent;
import lab.agent.Tool;
import lab.shared.Settings;

import java.util.List;

/**
 * Agent with a tool.
 *
 * <p>The model decides on its own whether to call get_order_status. Everything
 * it knows about that function is the name, the description and the parameter
 * schema in the {@link Tool} below. Treat them as an API contract, not as
 * comments. In Python and C# the framework derives them from the function
 * signature; here they're written out, so you can see exactly what the model
 * receives.
 *
 * <p>Counterpart of python/local_agent/order_agent.py and csharp/LocalAgent.Order.
 */
public final class OrderAgent {

    public static void main(String[] args) {
        Settings.load();

        var project = new AgentsClientBuilder()
                .endpoint(Settings.required("FOUNDRY_PROJECT_ENDPOINT"))
                .credential(Settings.credential());

        var orderStatus = new Tool(
                "get_order_status",
                "Look up the current delivery status of a customer order.",
                Tool.stringParams(new Tool.Param("order_id", "Order ID, format ORD-12345")),
                // The handler receives the arguments the model chose, already parsed
                // from JSON. The cast is safe because the schema says "string" and
                // the tool is strict.
                arguments -> getOrderStatus((String) arguments.get("order_id")));

        Agent agent = Agent.local(project,
                Settings.required("FOUNDRY_MODEL"),
                "OrderAgent",
                // Instructions shape behaviour but do not enforce it. Anything that
                // MUST happen every time belongs in ordinary code, not in here.
                "You help customers with order queries. "
                        + "Always look up the order before answering. Never guess a status. "
                        + "If the order ID is not in ORD-NNNNN format, ask for a valid one.",
                List.of(orderStatus));

        // run() makes at least two model calls here: one where the model asks for
        // the tool, one where it reads the result and answers. See Agent.run.
        System.out.println(agent.run("Hi, where has my order ORD-40021 got to?"));
    }

    static String getOrderStatus(String orderId) {
        // A real implementation would call your order system here.
        // Note: the agent may call this more than once. Fine for a read;
        // anything that writes needs an idempotency key.
        return orderId + ": shipped, tracking BE998877, arriving 8 August";
    }

    private OrderAgent() {
    }
}
