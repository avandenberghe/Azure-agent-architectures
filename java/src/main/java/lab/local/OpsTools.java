package lab.local;

import lab.agent.Tool;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Both tools are read-only `az` calls. Nothing here creates or deletes anything.
 *
 * <p>The resource, group and region are passed in, so OpsAgent reads them at
 * startup and a missing setting fails before any tokens are spent.
 */
final class OpsTools {

    private final String resource;
    private final String group;
    private final String region;

    OpsTools(String resource, String group, String region) {
        this.resource = resource;
        this.group = group;
        this.region = region;
    }

    /** Both tools, as the model will see them. */
    List<Tool> tools() {
        return List.of(
                new Tool("list_deployments",
                        "List the model deployments that already exist in this Foundry resource.",
                        Tool.noParams(),
                        arguments -> listDeployments()),
                new Tool("check_quota",
                        "Check deployment quota for a model. Quota is granted per model, SKU and "
                                + "region, and is separate from whether the model appears in the catalogue. "
                                + "A limit of 0 means no allocation was ever granted.",
                        Tool.stringParams(new Tool.Param("model", "Model family to check, e.g. 'gpt-5-nano'")),
                        arguments -> checkQuota((String) arguments.get("model"))));
    }

    String listDeployments() {
        return az("cognitiveservices", "account", "deployment", "list",
                "-n", resource, "-g", group,
                "--query", "[].{deployment:name, model:properties.model.name, sku:sku.name}",
                "-o", "json");
    }

    String checkQuota(String model) {
        // The model-supplied value goes into a JMESPath query. There's no shell
        // (arguments are passed as a list), but a tool argument is still untrusted input.
        return az("cognitiveservices", "usage", "list", "-l", region,
                "--query", "[?contains(name.value, '" + model + "')].{sku:name.value, used:currentValue, limit:limit}",
                "-o", "json");
    }

    /**
     * Run an az command and return stdout.
     *
     * <p>Errors come back as text rather than raising. An exception ends the run;
     * a returned string lets the model read the failure and decide what to do
     * about it, which is the behaviour you want from a tool.
     */
    private static String az(String... args) {
        List<String> command = new ArrayList<>();
        command.add("az");
        command.addAll(List.of(args));

        Process process;
        try {
            process = new ProcessBuilder(command).start();
        } catch (IOException e) {
            // az lives in /opt/homebrew/bin; IntelliJ launched from the Dock doesn't inherit it.
            return "ERROR: 'az' not found on PATH.";
        }

        // Read both streams while the process runs: a full pipe buffer would
        // otherwise block az, and waitFor would never return.
        CompletableFuture<String> stdout = CompletableFuture.supplyAsync(() -> read(process.getInputStream()));
        CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> read(process.getErrorStream()));
        try {
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return "ERROR: az timed out after 60s.";
            }
            if (process.exitValue() != 0) {
                return "ERROR: " + stderr.join().strip();
            }
            return stdout.join().strip();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return "ERROR: interrupted while waiting for az.";
        }
    }

    private static String read(InputStream stream) {
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
