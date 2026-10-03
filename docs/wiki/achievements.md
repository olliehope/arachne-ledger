# Achievements

Open **Journal → Achievements** from the dashboard or run `/arachne achievements`. The achievement book has **23 unlocks**, grouped into Hunting, Summoning, Collection, Rare drops, and Speed. Progress is lifetime for the selected account/profile, independent of the dashboard's Session/Total choice.

Use **All**, **In progress**, or **Unlocked** to filter entries. The category button cycles categories; scroll or use the arrow buttons to browse. Each visible milestone shows its description and progress. Hover for the full details and unlock date.

| Achievement | Targets |
| --- | --- |
| Spider Exterminator I–III | 10 / 100 / 1,000 counted Arachne kills |
| Summoner I–III | 10 / 100 / 500 of your own Arachne Crystal placements |
| Silk Merchant I–III | 1,000 / 10,000 / 100,000 detected Soul String |
| Lucky Legs | Your first detected Tarantula Pet |
| Fang Collector I–III | 1 / 10 / 100 detected Arachne Fangs |
| Looking for a Pet I–III | 100 / 500 / 1,000 consecutive qualifying fights without a Tarantula Pet |
| Fangless I–III | 50 / 100 / 250 consecutive qualifying fights without an Arachne Fang |
| Speed Weaver I–III | A qualifying, confirmed timed fight in 60 / 45 / 30 seconds or less |
| Hidden milestone | Discover a rare pet milestone to reveal its name and goal |

## What counts

Kill and speed goals require a retained server kill receipt and a Counted fight meeting its saved damage threshold. Speed also requires a confirmed spawn and a positive spawn-to-death duration. Trusted legacy server kill receipts can fill kill milestones, but never invent a speed record.

Summoner counts your server-confirmed Crystal placements, including receipts that were not associated with a completed fight. Another player's summons and your Callings do not contribute to this Crystal milestone.

Collection and rare-drop goals use detected loot quantities, including rewards from fights skipped for damage. Manual additions and manually replaced fight drops do not manufacture progress. Prices do not affect any achievement.

Dry-run milestones use the **longest historical run** of qualifying fights without the target reward. Either pet rarity resets the pet run. A fight that drops the target reward is not a dry kill. Manual drops and rewards from low-damage fights do not reset these runs; low-damage fights do not add dry kills. Legacy aggregate kills without associated fight history cannot establish an ordered dry run. See [RNG history](farming.md#rng-history) for current intervals and separate rarity histories.

## History and notifications

Existing saved observations fill achievements silently on the first load. Imported unlocks have no invented date; their tooltip says **Imported from saved history**. Newly added achievement definitions also fill from existing history without replaying old notifications.

New live unlocks can print a local message after their state is saved:

```text
[Arachne] Achievement Unlocked >> Looking for a Pet I
```

Hover for the category and objective, or click to open the achievement book. **Settings → Tracking → Achievement chat** controls the message. **Settings → Farming cues → Alerts** independently controls achievement titles and sounds. Unlocks still progress and are acknowledged when cues are off, so enabling them does not replay earlier milestones.

Earned achievements stay earned if receipts are later corrected or removed. Progress toward locked goals follows the remaining trusted observations. [Personal records](tracking-and-profit.md#session-recaps-and-personal-records) are recalculated from current receipts instead.
