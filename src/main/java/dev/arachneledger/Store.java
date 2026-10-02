package dev.arachneledger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.*;
import java.nio.file.*;
import java.util.function.*;

public final class Store {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static <T> T read(Path file, Class<T> type, Supplier<T> fresh, Consumer<T> validate) throws IOException {
        if (!Files.exists(file) && !Files.exists(backup(file))) return fresh.get();
        Exception original;
        try { return parse(file, type, validate); } catch (Exception ex) { original = ex; }
        try {
            T restored = parse(backup(file), type, validate);
            if (Files.exists(file)) {
                // Recovery can run twice in the same millisecond. Reserve a unique quarantine
                // filename instead of letting a timestamp collision block a usable backup.
                Path corrupt = Files.createTempFile(file.toAbsolutePath().getParent(), file.getFileName() + ".corrupt-", "");
                Files.move(file, corrupt, StandardCopyOption.REPLACE_EXISTING);
            }
            write(file, restored);
            return restored;
        } catch (Exception ex) { throw new IOException("Cannot load " + file.getFileName() + "; originals preserved", original); }
    }
    private static <T> T parse(Path p, Class<T> type, Consumer<T> validate) throws IOException {
        try (Reader r = Files.newBufferedReader(p)) {
            T value = GSON.fromJson(r, type);
            if (value == null) throw new IOException("Empty JSON");
            validate.accept(value); return value;
        }
    }
    public static void write(Path file, Object value) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (Writer w = Files.newBufferedWriter(tmp)) { GSON.toJson(value, w); }
        if (Files.exists(file)) Files.copy(file, backup(file), StandardCopyOption.REPLACE_EXISTING);
        try { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException ex) { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING); }
    }
    private static Path backup(Path p) { return p.resolveSibling(p.getFileName() + ".bak"); }
    private Store() {}
}
