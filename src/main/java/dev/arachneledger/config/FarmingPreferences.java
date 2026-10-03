package dev.arachneledger.config;

/** Optional farming cues. These preferences never change receipts or kill qualification. */
public final class FarmingPreferences {
    public boolean pedestalTimer = true;
    public boolean pedestalFightTime = false;
    public boolean pedestalThroughWalls = false;
    public boolean adaptiveCrystalTimer = true;
    public int crystalSpawnSeconds = 40;
    public int callingSpawnSeconds = 19;
    public double pedestalScale = 1;
    public int pedestalRange = 64;
    public boolean spawnSound = false;
    public boolean spawnTitle = false;
    public boolean rngChat = true;
    public boolean rngSound = true;
    public boolean achievementSound = false;
    public boolean achievementTitle = false;
    public boolean compactKillChat = false;

    public FarmingPreferences copy() {
        FarmingPreferences copy = new FarmingPreferences();
        copy.pedestalTimer = pedestalTimer;
        copy.pedestalFightTime = pedestalFightTime;
        copy.pedestalThroughWalls = pedestalThroughWalls;
        copy.adaptiveCrystalTimer = adaptiveCrystalTimer;
        copy.crystalSpawnSeconds = crystalSpawnSeconds;
        copy.callingSpawnSeconds = callingSpawnSeconds;
        copy.pedestalScale = pedestalScale;
        copy.pedestalRange = pedestalRange;
        copy.spawnSound = spawnSound;
        copy.spawnTitle = spawnTitle;
        copy.rngChat = rngChat;
        copy.rngSound = rngSound;
        copy.achievementSound = achievementSound;
        copy.achievementTitle = achievementTitle;
        copy.compactKillChat = compactKillChat;
        return copy;
    }

    public void validate() {
        crystalSpawnSeconds = Math.clamp(crystalSpawnSeconds, 10, 60);
        callingSpawnSeconds = Math.clamp(callingSpawnSeconds, 10, 60);
        pedestalRange = Math.clamp(pedestalRange, 16, 128);
        if (!Double.isFinite(pedestalScale)) pedestalScale = 1;
        pedestalScale = Math.clamp(pedestalScale, .5, 2);
    }
}
