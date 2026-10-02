# Tracking and profit

## What is recorded

- Your Crystals and Callings, from placement messages using your player name or **You**.
- Rewards shown on armor stand labels around the defeated boss, including their `xN` quantities.
- Physical item pickups and eligible personal pet claims, reconciled with the observed labels.
- Eligible marked purse gains during a fight or shortly after its death.

A floating reward label represents a spawned reward. If you leave it behind, use **Adjust** or a fight correction to remove it. Unsupported or unseen signals may require a manual correction.

## Kills and active time

A kill requires the server's **Your Damage** result within five seconds of **ARACHNE DOWN!**, with damage at or above the configured threshold. The default is 10,000. Zero, low, missing, or late damage does not count as a kill. Observed drops and your summon costs remain recorded.

Active time begins with a known spawn, includes the fight, and continues for up to **60 seconds after death**. With no new spawn, the timer becomes AFK until farming resumes. **Summoning** indicates a completed summon is awakening; it does not add time before the spawn. Moving or entering the Sanctuary does not start the timer.

Joining mid-fight can recover tracking from fresh boss activity, but the original spawn time is unknown. Such fights have no known-spawn marker. Pausing, leaving the tracking area, or changing worlds interrupts the current fight.

## Profit and hourly rates

**Net profit = recorded loot value + coin income − all recorded costs.** Values are estimates based on the chosen prices, rather than completed sales. Scavenger income is already included; do not add it again.

Actual hourly rates divide the selected session or lifetime value by its active time. Projected rates use the **current session's latest five active minutes**, or its elapsed active time if shorter. A projection needs at least 60 active seconds and one qualifying kill in that window. It always uses recent session pace, even when displaying lifetime totals. A small sample or rare drop can move it sharply.

**Profit without RNG** uses the same recorded journal and active clock, excluding the loot value of Epic/Legendary Tarantula Pets and Arachne Fangs. It keeps normal loot, Scavenger and other coin income, and all Crystal, Calling, and other costs. These optional HUD rows show a baseline alongside jackpot-inclusive earnings; they do not discard drops or change the graph's existing metrics. Unpriced RNG drops contribute zero to both values.

The graph's projected total extends the selected total by five more active minutes at that recent rate. It does not predict an unseen RNG drop.

## Graph

Open **Graph → Options** to choose net profit, loot value, and cost lines; projection lines; and known-spawn markers. **Text metric** chooses which line supplies total and hourly labels. Loot value excludes coin income; costs include your summon costs and manual expenses.

You can independently hide total, projected total, hourly rate, projected hourly rate, spawn count, active time, and scope text. These choices are shared with the graph HUD and only affect display. Known spawns include fights skipped for damage, so their count can differ from kills.

## Scavenger coins

Tracking pairs the sidebar's yellow `(+amount)` annotation with a positive purse change. It establishes a baseline first and ignores repeated observations and ordinary unmarked changes. Gains are eligible during a tracked fight or within **10 seconds after death**. Container menus and the two seconds after closing them are excluded.

Other marked coin rewards received during farming cannot be distinguished from Scavenger by these client signals.

## Fight history and corrections

**Fights** shows the latest 50 fights in the selected scope. Open one for its damage, outcome, rewards, and summon costs. **Edit drops** sets an item's absolute quantity; zero removes it. Existing items retain their recorded price, while newly added items use the current price.

**Adjust** adds a missed drop, coin income, or expense. `/arachne undo` removes the latest current-session entry. `/arachne export` saves a CSV under `config/arachneledger/exports` using the selected scope.

Kill chat summaries are printed after a short collection delay. Later rewards still update totals but do not rewrite an already printed summary.

## Session recaps and personal records

**Recap → Current** shows selected session or lifetime finances, active time, qualifying kills, actual average and fastest kill times, summon counts and spend, Scavenger coins, and RNG value. The average includes only qualifying fights with a confirmed spawn and death; joining mid-fight or old history without a trustworthy spawn never creates an estimated speed record. Older kill receipts may contribute financial/kill totals without enough metadata for the qualified-kill or timing rows.

Starting a new session captures the previous summary before resetting its boundary. **Last session** stays fixed even if later prices or historical corrections change the live journal. Up to 20 summaries are kept in the account/profile save. Session recap chat can be disabled independently of this capture.

**Recap → Records** shows the fastest qualifying timed kill, most profitable qualifying fight, and most profitable tracked session. A qualifying record requires a Counted fight, damage meeting that fight's saved threshold, and a retained server kill receipt. Records use detected rewards from server, pickup, floating-label, personal pet-claim, and purse signals. Manual loot, manual coins, and manually replaced fight loot cannot increase records; associated costs still subtract.

The best tracked session groups fight-associated receipts by their saved session IDs. It subtracts associated costs from unsuccessful attempts, and includes rewards only from qualifying kills. Unassociated historical receipts cannot be assigned to a past session, so this record can differ from a full financial session recap. Negative results are shown as recorded rather than replaced with zero.

Personal records are recalculated after late rewards, explicit repricing, undo, or corrections. [Achievements](https://github.com/olliehope/arachne-ledger/wiki/Achievements) keep earned milestones once unlocked.
