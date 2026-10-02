package dev.arachneledger.ledger;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Records use detected rewards from qualifying fights. Manual additions and fight edits never
 * create financial records. Cost corrections can lower them. Late receipts, repricing, deleted
 * kills, and edits are reevaluated from the current journal, not stored as irreversible trophies.
 */
public final class PersonalRecords {
    public record TimedKill(long fightId, long sessionId, long durationMillis, long died) {}

    public record ProfitableFight(
            long fightId, long sessionId, double net, double ordinaryNet, long died) {}

    /** Only fight-associated receipts can be assigned to old sessions without inventing history. */
    public record ProfitableSession(long sessionId, long qualifiedKills, double net) {}

    public record Snapshot(
            boolean total,
            long qualifiedKills,
            TimedKill fastestKill,
            ProfitableFight bestFight,
            ProfitableSession bestSession) {}

    private static final class Session {
        long kills;
        double net;
    }

    public static Snapshot calculate(Ledger ledger, boolean total) {
        return calculate(HistoryIndex.build(ledger), ledger.sessionId, total);
    }

    public static Snapshot calculate(HistoryIndex.Snapshot index, long sessionId, boolean total) {
        TimedKill fastest = null;
        ProfitableFight bestFight = null;
        Map<Long, Session> sessions = new LinkedHashMap<>();
        long kills = 0;
        for (HistoryIndex.Fight fight : index.fights()) {
            if (!total && fight.session() != sessionId) {
                continue;
            }
            Session session = sessions.computeIfAbsent(fight.session(), ignored -> new Session());
            // Failed and unfinished attempts retain their spend. Their rewards cannot inflate
            // a record, since the player did not have a confirmed qualifying kill.
            session.net -= fight.costs();
            if (!fight.qualifying()) {
                continue;
            }
            kills++;
            session.kills++;
            session.net += fight.detectedRevenue();
            long duration = fight.duration();
            if (duration > 0
                    && (fastest == null
                            || duration < fastest.durationMillis()
                            || (duration == fastest.durationMillis()
                                    && fight.id() < fastest.fightId()))) {
                fastest = new TimedKill(fight.id(), fight.session(), duration, fight.died());
            }
            if (bestFight == null
                    || fight.net() > bestFight.net()
                    || (fight.net() == bestFight.net() && fight.id() < bestFight.fightId())) {
                bestFight =
                        new ProfitableFight(
                                fight.id(),
                                fight.session(),
                                fight.net(),
                                fight.ordinaryNet(),
                                fight.died());
            }
        }
        ProfitableSession bestSession = null;
        for (Map.Entry<Long, Session> entry : sessions.entrySet()) {
            Session session = entry.getValue();
            if (session.kills > 0
                    && (bestSession == null
                            || session.net > bestSession.net()
                            || (session.net == bestSession.net()
                                    && entry.getKey() < bestSession.sessionId()))) {
                bestSession = new ProfitableSession(entry.getKey(), session.kills, session.net);
            }
        }
        return new Snapshot(total, kills, fastest, bestFight, bestSession);
    }

    private PersonalRecords() {}
}
