using Azure.Core;
using Azure.Identity;

namespace AgentArchitectures.Shared;

/// <summary>
/// What every program in the lab does identically: load .env, read settings,
/// authenticate. Nothing agent-related lives here — each program shows the whole
/// pattern on its own, as each Python script does.
/// </summary>
public static class Settings
{
    /// <summary>
    /// Load the repo-root .env into the process environment.
    /// TraversePath walks up from the working directory, so this works from the
    /// repo root, from csharp/, from a project folder, and from Rider.
    /// NoClobber keeps variables already set in the environment, matching
    /// python-dotenv's default.
    /// </summary>
    public static void Load() => DotNetEnv.Env.NoClobber().TraversePath().Load();

    /// <summary>
    /// Read a setting, failing with a useful message rather than a null.
    /// The C# counterpart of _required() in python/local_agent/ops_agent.py.
    /// </summary>
    public static string Required(string name)
    {
        var value = Environment.GetEnvironmentVariable(name);
        if (string.IsNullOrWhiteSpace(value))
        {
            Console.Error.WriteLine(
                $"{name} is not set. Copy .env.example to .env and fill it in, " +
                "then run this from anywhere inside the repo.");
            Environment.Exit(1);
        }
        return value;
    }

    /// <summary>Who you are: the identity `az login` holds. Same as Python.</summary>
    public static TokenCredential Credential { get; } = new AzureCliCredential();
}
