package dev.arachneledger.ledger;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/** CSV output has no tracking side effects and preserves the selected recorded receipts. */
public final class LedgerCsv {
    public static Path write(Ledger ledger, boolean total, String profile, Path directory, long now)
            throws IOException {
        Files.createDirectories(directory);
        Path file = directory.resolve("arachne-" + profile + "-" + now + ".csv");
        try (BufferedWriter writer = Files.newBufferedWriter(file)) {
            writer.write("time_utc,active_ms,kind,item,quantity,unit_coins,income,cost,source\n");
            for (int index = total ? 0 : ledger.sessionStart;
                    index < ledger.entries.size();
                    index++) {
                Ledger.Entry entry = ledger.entries.get(index);
                writer.write(
                        String.join(
                                        ",",
                                        Instant.ofEpochMilli(entry.at()).toString(),
                                        Long.toString(entry.elapsed()),
                                        entry.kind().toString(),
                                        quote(entry.item()),
                                        Long.toString(entry.count()),
                                        Double.toString(entry.unit()),
                                        Double.toString(entry.income()),
                                        Double.toString(entry.cost()),
                                        quote(entry.source()))
                                + "\n");
            }
        }
        return file;
    }

    private static String quote(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private LedgerCsv() {}
}
