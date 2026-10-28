using System.ComponentModel;
using System.Diagnostics;

/// <summary>
/// Both tools are read-only `az` calls. Nothing here creates or deletes anything.
/// The resource, group and region are passed in, so Program.cs reads them at
/// startup and a missing setting fails before any tokens are spent.
/// </summary>
sealed class OpsTools(string resource, string group, string region)
{
    [Description("List the model deployments that already exist in this Foundry resource.")]
    public Task<string> ListDeploymentsAsync() => AzAsync(
        "cognitiveservices", "account", "deployment", "list",
        "-n", resource, "-g", group,
        "--query", "[].{deployment:name, model:properties.model.name, sku:sku.name}",
        "-o", "json");

    [Description(
        "Check deployment quota for a model. Quota is granted per model, SKU and " +
        "region, and is separate from whether the model appears in the catalogue. " +
        "A limit of 0 means no allocation was ever granted.")]
    public Task<string> CheckQuotaAsync(
        [Description("Model family to check, e.g. 'gpt-5-nano'")] string model) => AzAsync(
        "cognitiveservices", "usage", "list", "-l", region,
        "--query", $"[?contains(name.value, '{model}')].{{sku:name.value, used:currentValue, limit:limit}}",
        "-o", "json");

    /// <summary>
    /// Run an az command and return stdout.
    ///
    /// Errors come back as text rather than raising. An exception ends the run;
    /// a returned string lets the model read the failure and decide what to do
    /// about it — which is the behaviour you want from a tool.
    /// </summary>
    static async Task<string> AzAsync(params string[] args)
    {
        var start = new ProcessStartInfo("az")
        {
            RedirectStandardOutput = true,
            RedirectStandardError = true,
        };
        foreach (var arg in args) start.ArgumentList.Add(arg);

        Process process;
        try
        {
            process = Process.Start(start)!;
        }
        catch (Win32Exception)
        {
            // az lives in /opt/homebrew/bin; GUI-launched Rider does not inherit it.
            return "ERROR: 'az' not found on PATH.";
        }

        using (process)
        using (var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(60)))
        {
            var stdout = process.StandardOutput.ReadToEndAsync(timeout.Token);
            var stderr = process.StandardError.ReadToEndAsync(timeout.Token);
            try
            {
                await process.WaitForExitAsync(timeout.Token);
            }
            catch (OperationCanceledException)
            {
                process.Kill(entireProcessTree: true);
                return "ERROR: az timed out after 60s.";
            }

            if (process.ExitCode != 0)
                return $"ERROR: {(await stderr).Trim()}";
            return (await stdout).Trim();
        }
    }
}
