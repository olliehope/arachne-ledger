package dev.arachneledger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

/** Recovery must preserve the last usable data and the original corrupt bytes. */
public final class PersistenceChecks {
    private static int checks;

    private static void yes(boolean value, String why) {
        checks++;
        if (!value) throw new AssertionError(why);
    }

    private static void same(Object expected, Object actual, String why) {
        checks++;
        if (!Objects.equals(expected, actual))
            throw new AssertionError(why + ": expected " + expected + ", got " + actual);
    }

    private static Config read(Path file) throws IOException {
        return Store.read(file, Config.class, Config::new, Config::validate);
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory(Path.of(System.getProperty("test.root", "build")), "arachne-persistence-");
        Path file = root.resolve("settings.json");
        Path backup = root.resolve("settings.json.bak");
        Config initial = read(file);
        same("default", initial.profile, "A new installation receives fresh settings");
        yes(!Files.exists(file), "Reading a missing installation does not write data");
        initial.profile = "first";
        Store.write(file, initial);
        same("first", read(file).profile, "Initial save can be read back");
        yes(!Files.exists(backup), "Initial save does not invent a previous backup");
        initial.profile = "second";
        Store.write(file, initial);
        same("second", read(file).profile, "Replacement saves the new value");
        same("first", read(backup).profile, "Replacement preserves the preceding value");
        yes(!Files.exists(root.resolve("settings.json.tmp")), "A completed save leaves no temporary file");

        String originalBackup = Files.readString(backup);
        String corrupt = "{ invalid settings";
        Files.writeString(file, corrupt);
        same("first", read(file).profile, "An invalid main file recovers the last usable backup");
        same(originalBackup, Files.readString(backup), "Recovery preserves the usable backup unchanged");
        try (var files = Files.list(root)) {
            var quarantined = files.filter(path -> path.getFileName().toString().startsWith("settings.json.corrupt-")).toList();
            same(1, quarantined.size(), "Recovery quarantines the corrupt main file");
            same(corrupt, Files.readString(quarantined.getFirst()), "Quarantine preserves the original corrupt bytes");
        }
        same("first", read(file).profile, "Restored main file remains readable on a subsequent load");

        Files.delete(file);
        same("first", read(file).profile, "A missing main file can also recover its backup");
        yes(Files.exists(file), "Backup-only recovery recreates the main file");
        Files.writeString(file, "{\"profile\":\"invalid profile\"}");
        same("first", read(file).profile, "Validation failure uses the same recovery path as malformed JSON");

        Files.writeString(file, corrupt);
        Files.writeString(backup, "{ invalid backup");
        boolean rejected = false;
        try { read(file); }
        catch (IOException expected) { rejected = true; }
        yes(rejected, "An unusable main and backup stop loading instead of inventing an empty ledger");
        same(corrupt, Files.readString(file), "Failed recovery preserves the main file");
        same("{ invalid backup", Files.readString(backup), "Failed recovery preserves the backup");

        // A bare filename has no Path parent, but is still a valid destination in the current directory.
        Path relative = Path.of("arachne-store-" + UUID.randomUUID() + ".json");
        try {
            Store.write(relative, initial);
            same("second", read(relative).profile, "A bare relative filename is a supported save destination");
        } finally {
            Files.deleteIfExists(relative);
            Files.deleteIfExists(relative.resolveSibling(relative.getFileName() + ".bak"));
            Files.deleteIfExists(relative.resolveSibling(relative.getFileName() + ".tmp"));
        }
        System.out.println("PASS: " + checks + " persistence, backup recovery and relative-path checks.");
    }
}
