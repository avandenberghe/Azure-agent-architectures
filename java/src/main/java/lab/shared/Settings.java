package lab.shared;

import com.azure.core.credential.TokenCredential;
import com.azure.identity.AzureCliCredentialBuilder;
import io.github.cdimascio.dotenv.Dotenv;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * What every program in the lab does identically: load .env, read settings,
 * authenticate. Nothing agent-related lives here.
 *
 * <p>Counterpart of Python's load_dotenv() + _required() and C#'s Shared/Settings.cs.
 */
public final class Settings {

    private static Dotenv dotenv;
    private static TokenCredential credential;

    private Settings() {
    }

    /**
     * Find the repo-root .env by walking up from the working directory, and read it.
     *
     * <p>Java can't modify its own environment variables, so unlike Python and C#
     * nothing is copied into the environment. {@link #required} looks in the real
     * environment first and falls back to .env, which gives the same "never
     * override what's already set" behaviour.
     */
    public static void load() {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            if (Files.isRegularFile(dir.resolve(".env"))) {
                dotenv = Dotenv.configure().directory(dir.toString()).load();
                return;
            }
        }
        // No .env found: settings can still come from the environment.
    }

    /**
     * Read a setting, failing with a useful message rather than a null.
     * Exits the program, so callers never see a missing value.
     */
    public static String required(String name) {
        String value = System.getenv(name);
        if (isBlank(value) && dotenv != null) {
            value = dotenv.get(name, null);
        }
        if (isBlank(value)) {
            System.err.println(name + " is not set. Copy .env.example to .env and fill it in, "
                    + "then run this from anywhere inside the repo.");
            System.exit(1);
        }
        return value;
    }

    /** Who you are: the identity `az login` holds. Same as Python and C#. */
    public static synchronized TokenCredential credential() {
        if (credential == null) {
            credential = new AzureCliCredentialBuilder().build();
        }
        return credential;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
