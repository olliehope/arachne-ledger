package dev.arachneledger.diagnostics;

/** A read-only view of eligibility at a particular client tick. */
public record DetectionSnapshot(
        String status,
        String location,
        String reason,
        boolean ready,
        boolean skyblock,
        boolean sanctuary,
        boolean paused,
        String timer,
        long fightId,
        String outcome,
        long damage,
        long minimumDamage,
        boolean pickups,
        boolean labels,
        long rewardMillis) {}
