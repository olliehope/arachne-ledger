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

A fresh completed ritual interrupts a stale fight when its death message was missed, allowing the next countdown to start. A real death and qualifying personal damage can recover the subsequent result when its spawn dialogue was missed; its original spawn time stays unknown. Elapsed time is shown as **HH:MM:SS**, with hours continuing past 24.

The optional pedestal countdown is a separate **estimate**, marked with `~`, starting from a detected placement. Adaptive Crystal timing can adjust the estimate using nearby ritual particles; absent observations keep the configured fallback. It stops on the actual spawn signal. A countdown reaching zero never starts the active clock, creates a spawn marker, or counts a kill. See [Farming cues](farming.md#pedestal-countdown) for timing and label settings.

## Profit and hourly rates

**Net profit = recorded loot value + coin income − all recorded costs.** Values are estimates based on the chosen prices, rather than completed sales. Scavenger income is already included; do not add it again.

Base prices use NPC sale values or George's pet sale values. **Ironman** uses those values for future loot and excludes Spider Essence, Arachne Shards, and gear selected for salvage from coin revenue. Their quantities still count, and these intentionally excluded drops do not trigger missing-price warnings. Configured summon costs still subtract from profit.

Changing prices or pricing modes leaves recorded values untouched. **Reprice session** deliberately applies your current pricing choices to the current session; older sessions and captured recaps retain their saved values.

Actual hourly rates divide the selected session or lifetime value by its active time. Projected rates use the **current session's latest five active minutes**, or its elapsed active time if shorter. A projection needs at least 60 active seconds and one qualifying kill in that window. It always uses recent session pace, even when displaying lifetime totals. A small sample or rare drop can move it sharply.

**Profit without RNG** uses the same recorded journal and active clock, excluding the loot value of Epic/Legendary Tarantula Pets and Arachne Fangs. It keeps normal loot, Scavenger and other coin income, and all Crystal, Calling, and other costs. These optional HUD rows show a baseline alongside jackpot-inclusive earnings; they do not discard drops or change the graph's existing metrics. Unpriced RNG drops contribute zero to both values.

The graph's projected total extends the selected total by five more active minutes at that recent rate. It does not predict an unseen RNG drop.

**Loot ledger** is the default text HUD for new settings, with aligned loot quantities and recorded values before the money and kill summary. Existing saved layouts and row selections are preserved. Optional rare-drop rates appear beside pets and fangs only; ordinary loot does not have an RNG percentage. Rates use automatically detected reward quantities per qualifying kill, rather than every manually entered item in the financial journal.

## RNG history

**Progress → RNG history** shows Session or Total observations for Any Tarantula pet, Epic pets, Legendary pets, and Arachne's Fang. Each row tracks detected quantity, qualifying kills, kills and active time since the last drop, and the longest dry run. With no drop in the selected scope, it shows the interval since that scope began.

Only rewards associated with qualifying recorded fights contribute. Manual loot and low-damage fights cannot reset a run. Late rewards remain associated with their original fight, preserving later dry kills. Observed drops per 100 kills describe your history and do not predict the next drop. [Dry-run achievements](achievements.md) use the longest lifetime run rather than resetting their earned progress when a reward arrives.

## Graph

Open **Graph → Options** to choose net profit, loot value, and cost lines; projection lines; and known-spawn markers. **Text metric** chooses which line supplies total and hourly labels. Loot value excludes coin income; costs include your summon costs and manual expenses.

Optional **Rare drop markers** show detected Tarantula Pets and Arachne Fangs. Hover for the recorded reward and click to open its original fight. Manual entries do not create RNG markers. Graph options use a **Save/Cancel** draft. Dense histories are drawn at the visible pixel width while retaining spikes and recorded end values; the original journal keeps every receipt.

You can independently hide total, projected total, hourly rate, projected hourly rate, spawn count, active time, and scope text. These choices are shared with the graph HUD and only affect display. Known spawns include fights skipped for damage, so their count can differ from kills.

## Scavenger coins

Tracking pairs the sidebar's yellow `(+amount)` annotation with a positive purse change. It establishes a baseline first and ignores repeated observations and ordinary unmarked changes. Gains are eligible during a tracked fight or within **10 seconds after death**. Container menus and the two seconds after closing them are excluded.

Other marked coin rewards received during farming cannot be distinguished from Scavenger by these client signals.

## Fight history and corrections

**Fights** shows the latest 50 fights in the selected scope. Open one for its damage, outcome, rewards, and summon costs. **Edit drops** sets an item's absolute quantity; zero removes it. Existing items retain their recorded price, while newly added items use the current price.

**Adjust** adds a missed drop, coin income, or expense. `/arachne undo` removes the latest current-session entry. `/arachne export` saves a CSV under `config/arachneledger/exports` using the selected scope.

Kill chat summaries are printed after a short collection delay. Later rewards still update totals but do not rewrite an already printed summary. **Settings → Timers & sounds → Chat → Compact kill summary** reduces the visible line to kill number, duration, and net profit; hover for rewards, costs, damage, Scavenger income, and missing values.

Rare-drop chat uses the reward's rarity color, an optional recorded value, and a trusted kill/time interval. Unknown values stay hidden. Spawn, reward, and achievement titles and sounds have independent local controls under **Timers & sounds**; they do not change financial entries or kill qualification.

## Session recaps and personal records

The dashboard's **Copy** button copies the selected scope's recap to your local clipboard. **Overview** shows the latest completed or ignored fight, including its outcome; click it to inspect its receipts. **Progress** remains accessible from every dashboard tab.

**Recap → Current** shows selected session or lifetime finances, active time, qualifying kills, actual average and fastest kill times, summon counts and spend, Scavenger coins, and RNG value. The average includes only qualifying fights with a confirmed spawn and death; joining mid-fight or old history without a trustworthy spawn never creates an estimated speed record. Older kill receipts may contribute financial/kill totals without enough metadata for the qualified-kill or timing rows.

Starting a new session captures the previous summary before resetting its boundary. **Last session** stays fixed even if later prices or historical corrections change the live journal. Up to 20 summaries are kept in the account/profile save. Session recap chat can be disabled independently of this capture.

**Recap → Records** shows the fastest qualifying timed kill, most profitable qualifying fight, and most profitable tracked session. A qualifying record requires a Counted fight, damage meeting that fight's saved threshold, and a retained server kill receipt. Records use detected rewards from server, pickup, floating-label, personal pet-claim, and purse signals. Manual loot, manual coins, and manually replaced fight loot cannot increase records; associated costs still subtract.

The best tracked session groups fight-associated receipts by their saved session IDs. It subtracts associated costs from unsuccessful attempts, and includes rewards only from qualifying kills. Unassociated historical receipts cannot be assigned to a past session, so this record can differ from a full financial session recap. Negative results are shown as recorded rather than replaced with zero.

Personal records are recalculated after late rewards, explicit repricing, undo, or corrections. [Achievements](achievements.md) keep earned milestones once unlocked.

## Calling and Crystal comparisons

Open **Progress → T1 / T2 comparison** for qualifying kills, detected loot, Scavenger coins, actual costs, profit, profit without RNG, average fight time, and **fight-only profit/hour**. This last rate divides earnings by confirmed combat time and excludes the wait between spawns; use the dashboard rate for your overall farming pace.

Tier comes from a completed Calling or Crystal ritual observed for that fight. Older fights and missed rituals remain **Unknown**. Another player's placement can identify a tier without charging you a cost. Unlinked manual corrections and receipts from skipped fights remain visible as unassigned income/costs instead of being silently attributed to a tier.

## Session targets and supplies

**Progress → Session target**, or `/arachne goals`, sets one optional target: counted kills, active time, or net profit. Time input uses **HH:MM:SS**. Save applies the draft; Cancel discards it. Completion can print one local message per selected goal and session. Loading an already completed goal does not replay the message. Targets always use the current session, regardless of the display's Session/Total choice.

**Progress → Supplies**, or `/arachne supplies`, shows carried Crystals and Callings. A rough Crystal stock estimate becomes available after three of your own recorded Crystal placements and one active minute in the current session. It excludes storage and does not infer another player's costs. `/arachne progress` opens the full Progress hub.
