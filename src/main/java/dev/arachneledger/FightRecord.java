package dev.arachneledger;

/** Persistent fight metadata; all coin and drop values come from its journal entries. */
public final class FightRecord {
    public enum Outcome { FIGHTING, WAITING_DAMAGE, COUNTED, ZERO_DAMAGE, LOW_DAMAGE, MISSING_DAMAGE, INTERRUPTED }
    public long id, session, spawned, died, activeStart, activeEnd;
    /** Position of a confirmed spawn; -1 keeps older saves on their original fight-start marker. */
    public long spawnActiveMillis = -1;
    public long damage = -1, minimumDamage = 10_000;
    public Outcome outcome = Outcome.FIGHTING;
    public long duration() { return spawned > 0 && died > 0 ? Math.max(0,died-spawned) : -1; }
    public String reason(boolean killRecorded) {
        return switch(outcome) {
            case FIGHTING -> "Fight in progress";
            case WAITING_DAMAGE -> "Waiting for damage summary";
            case COUNTED -> killRecorded ? "Counted" : "Kill entry removed";
            case ZERO_DAMAGE -> "Skipped: zero damage";
            case LOW_DAMAGE -> "Skipped: below "+String.format(java.util.Locale.ROOT,"%,d",minimumDamage)+" damage";
            case MISSING_DAMAGE -> "Skipped: no damage summary";
            case INTERRUPTED -> "Interrupted: tracking stopped";
        };
    }
    public void validate() {
        if(id<1 || session<1 || spawned<0 || died<0 || (spawned>0 && died>0 && died<spawned)
                || activeStart<0 || activeEnd<activeStart || spawnActiveMillis< -1
                || (spawnActiveMillis>=0 && (spawned==0 || spawnActiveMillis<activeStart))
                || damage< -1 || minimumDamage<1 || outcome==null)
            throw new IllegalArgumentException("Invalid fight history");
    }
}
