# Tracking diagnostics

Run `/arachne diagnostics` or open **Journal → Tracking diagnostics** from the dashboard when a summon, drop, or location is missed. This page shows the current tracking state and the recent observations that explain a decision. `/arachne debug` also saves a detection report for command-based troubleshooting.

The header shows the active/AFK timer state, location, fight outcome, reported damage and threshold, and whether pickup or floating-name-tag reward windows are open. The event list covers location, summons, spawns, deaths, damage, loot, Scavenger coins, storage, and sessions. Accepted events are green; ignored events include the reason they were skipped.

## Controls

- **All / Accepted / Ignored:** cycle the displayed event filter. Informational events appear in All.
- **Copy report:** copy the current state, recent observations, visible sidebar, and visible tab list as plain text.
- **Save report:** write the same report to `config/arachneledger/detection-debug.txt` in the Minecraft instance.
- **Clear log:** clear the local observation history. Recorded loot and profit remain unchanged.

Scroll over the event list for older observations and hover for complete details. The log retains at most **120 events** in memory; repeated identical observations are condensed and repeated entity scans are limited. It is not a permanent chat transcript and is not restored after restarting the client.

## Reporting a bug

Reproduce the problem, then copy or save the report while the relevant observations remain visible. Include what you expected, which drop or summon was missed, and the mod version when opening a [GitHub issue](https://github.com/olliehope/arachne-ledger/issues).

Reports stay local until you choose to share them. They contain server-visible text and may include player names, purse values, and location information from the sidebar or tab list. They do not include authentication tokens, API keys, or full account saves. Review the plain text before posting it publicly.
