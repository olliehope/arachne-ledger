# Farming cues and RNG history

The profit HUD keeps the totals visible while the pedestal label and local notifications show what is happening in the current fight. Open **Settings → Farming cues** to choose which cues you want. Changes stay in a draft until **Save**; **Back** discards them.

## Pedestal countdown

A detected Arachne Crystal or completed **4/4** Calling placement starts an estimated countdown above the pedestal. Repeated placement relays do not restart it. The default fallback is **40 seconds** for a Crystal and **19 seconds** for a Calling; both are adjustable from 10–60 seconds.

With **Adaptive Crystal timing** on, the mod samples the first nearby ritual dust burst after three seconds and estimates another **21 or 37 seconds** from that burst, usually about **24 or 40 seconds** from placement. If particles were not observed, it keeps the configured fallback. The timer remains an estimate rather than a precise server clock.

The `~` marks an estimate. At zero, the label waits for the actual spawn message. The countdown does not record a spawn, count a kill, or start the fight clock. A confirmed spawn ends the estimate; **Fight duration** can then show elapsed fight time at the pedestal.

Use **Through walls** to choose block visibility, **Label scale** for 50–200% size, and **Label range** for a 16–128 block draw distance. The regular profit HUD has separate position and scale controls.

## Local notifications

Choose spawn titles and sounds, rare-drop chat and sounds, and achievement titles and sounds independently. The existing rare-drop title and value controls remain under **Settings → Tracking**. Cues never send chat messages to the server or change recorded earnings.

Achievement chat uses this format:

```text
[Arachne] Achievement Unlocked >> Spider Exterminator I
```

Hover an unlock for its category and objective, or click it to open the achievement book. Imported history and new milestone definitions backfill silently. There are **23 achievements**, including **Looking for a Pet** at 100, 500, and 1,000 consecutive counted fights without a pet, and **Fangless** at 50, 100, and 250 without a fang. These milestones use your longest recorded dry run, so a later drop does not erase earned progress.

**Compact kill summary** shows the kill number, fight duration, and net profit. Hover it for rewards, costs, damage, Scavenger income, and any missing values. Kill chat itself must be enabled under **Tracking**.

Rare-drop chat colors pets and fangs by rarity. It shows a recorded coin value only when a positive value is known and **Rare drop value** is enabled. A trusted kill/time interval is included for drops associated with qualifying recorded fights; a reward from an unqualified fight does not display an invented interval.

## RNG history

Open **Journal → RNG history** to see **Any Tarantula pet**, **Epic**, **Legendary**, and **Arachne's Fang**. Switch between Session and Total for detected drop quantities, qualifying kills, kills and active time since the last drop, observed drops per 100 kills, and the longest dry run.

The observed rate describes your recorded rewards; it is not a prediction for the next kill. Only qualifying fights and automatically detected rewards contribute. Manual loot entries do not reset a dry run, and low-damage fights do not add dry kills or reset the interval. Old aggregate kill totals without associated fight history cannot establish an ordered streak.

Late pickups belong to their original fight. A delayed label or pet claim does not erase dry kills or elapsed active time from later fights. Active intervals use the original reward fight's end; the last-drop date shows when the receipt was recorded. **Looking for a Pet** accepts either Tarantula pet rarity as a reset; the Epic and Legendary rows retain their separate histories.

The HUD's rare-drop rate setting applies only to Tarantula Pets and Arachne Fangs. String, Soul String, essence, and other ordinary loot keep quantity and value rows without RNG percentages. Hidden items still contribute to saved totals.

For participation rules and active-time accounting, see [Tracking and profit](tracking-and-profit.md). For every display and price option, see [Settings](settings.md).
