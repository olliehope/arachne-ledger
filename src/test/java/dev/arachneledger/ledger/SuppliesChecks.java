package dev.arachneledger.ledger;

/** Stock estimates use the player's own recorded Crystal costs and current session active time. */
public final class SuppliesChecks {
    private static int checks;

    private static void yes(boolean value, String why) {
        checks++;
        if (!value) throw new AssertionError(why);
    }

    public static void main(String[] args) {
        Ledger ledger = new Ledger();
        yes(
                Supplies.calculate(false, 0, 0, ledger).crystalMillisLeft() == -1,
                "No world gives no estimate");
        yes(
                Supplies.calculate(true, 10, 5, ledger).crystalMillisLeft() == -1,
                "Fresh stock is not a guessed consumption rate");
        ledger.add(Ledger.Kind.CRYSTAL, "ARACHNE_CRYSTAL", 3, 10, "server", 1);
        for (int i = 0; i < 12; i++) ledger.tick(5_000);
        var stock = Supplies.calculate(true, 6, 4, ledger);
        yes(stock.crystals() == 6 && stock.callings() == 4, "Exact stock counts retained");
        yes(
                stock.crystalMillisLeft() == 120_000,
                "Three personal crystals per active minute estimates two minutes left");
        var empty = Supplies.calculate(true, -2, -1, ledger);
        yes(
                empty.crystals() == 0 && empty.callings() == 0 && empty.crystalMillisLeft() == 0,
                "Negative corrupt counts clamp to zero");
        ledger.newSession();
        yes(
                Supplies.calculate(true, 6, 4, ledger).crystalMillisLeft() == -1,
                "Lifetime usage does not leak into new session estimate");
        ledger.add(Ledger.Kind.CALLING, "ARACHNE_KEEPER_FRAGMENT", 50, 10, "server", 2);
        for (int i = 0; i < 12; i++) ledger.tick(5_000);
        yes(
                Supplies.calculate(true, 6, 4, ledger).crystalMillisLeft() == -1,
                "Calling usage cannot invent Crystal consumption");
        System.out.println("PASS: " + checks + " supplies checks.");
    }

    private SuppliesChecks() {}
}
