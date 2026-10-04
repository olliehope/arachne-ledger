package dev.arachneledger.client;

import dev.arachneledger.ledger.Ledger;
import dev.arachneledger.ledger.Supplies;
import dev.arachneledger.skyblock.ItemIds;

import net.minecraft.client.Minecraft;

/** Read only carried inventory; storage and other players' summon items are never assumed. */
public final class ClientSupplies {
    public static Supplies.Snapshot read(Minecraft client, Ledger ledger) {
        if (client.player == null) return Supplies.calculate(false, 0, 0, ledger);
        long crystals = 0, callings = 0;
        var carried =
                new java.util.ArrayList<>(client.player.getInventory().getNonEquipmentItems());
        carried.add(client.player.getOffhandItem());
        for (var stack : carried) {
            if (stack.isEmpty()) continue;
            switch (ItemIds.of(stack)) {
                case "ARACHNE_CRYSTAL" -> crystals += stack.getCount();
                case "ARACHNE_KEEPER_FRAGMENT" -> callings += stack.getCount();
                default -> {}
            }
        }
        return Supplies.calculate(true, crystals, callings, ledger);
    }

    private ClientSupplies() {}
}
