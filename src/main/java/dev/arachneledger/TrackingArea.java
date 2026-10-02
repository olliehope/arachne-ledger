package dev.arachneledger;

import java.util.Locale;

/** Short-lived server evidence supplements visible location data, never crosses worlds. */
public final class TrackingArea {
    private boolean hypixel, skyBlock, sanctuary, permitsEvidence;
    private long evidenceUntil;
    private String location = "", reason = "Waiting for Hypixel SkyBlock";

    public void update(
            boolean hypixel, boolean skyBlock, boolean sanctuary, String location, String reason) {
        this.hypixel = hypixel;
        this.skyBlock = hypixel && skyBlock;
        this.sanctuary = this.skyBlock && sanctuary;
        this.location = location == null ? "" : location;
        this.reason = reason == null ? "Location unavailable" : reason;
        String key = this.location.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        permitsEvidence =
                this.skyBlock && (this.sanctuary || key.isEmpty() || key.equals("spidersden"));
        if (!permitsEvidence) {
            evidenceUntil = 0;
        }
    }

    public void observeArachne(long now) {
        if (permitsEvidence) {
            evidenceUntil = now + 90_000;
        }
    }

    public boolean skyBlock() {
        return hypixel && skyBlock;
    }

    public boolean active(long now, boolean manual) {
        return skyBlock() && (sanctuary || manual || (permitsEvidence && now < evidenceUntil));
    }

    public String location() {
        return location;
    }

    public String reason(long now, boolean manual) {
        if (manual && skyBlock()) {
            return "Manual tracking in SkyBlock";
        }
        if (!sanctuary && permitsEvidence && now < evidenceUntil) {
            return "Arachne detected from boss messages";
        }
        return reason;
    }

    public void reset() {
        hypixel = skyBlock = sanctuary = permitsEvidence = false;
        evidenceUntil = 0;
        location = "";
        reason = "Waiting for Hypixel SkyBlock";
    }
}
