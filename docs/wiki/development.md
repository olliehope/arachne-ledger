# Development

Use **JDK 25**. From a clone of [the repository](https://github.com/olliehope/arachne-ledger), run:

```powershell
.\gradlew.bat build
```

On Linux or macOS, use `./gradlew build`. Installable JARs are written to `build/libs`.

Run `./gradlew formatJava` or `.\gradlew.bat formatJava` before committing Java changes.

## Code map

| Package | Responsibility |
| --- | --- |
| `client` | Fabric lifecycle, commands, and local messages |
| `client.render` | World label extraction and pedestal rendering |
| `config` | Saved preferences and persistence |
| `tracking` | Tracker orchestration, fight lifecycle, active clock, farming cues, summaries, and duplicate reconciliation |
| `skyblock` | Server text, location, item IDs, and reward parsing |
| `ledger` | Recorded entries, joined history, totals, projections, RNG history, recaps, records, and CSV export |
| `achievement` | Data-driven milestone definitions, trusted facts, unlock evaluation, and saved state |
| `diagnostics` | Bounded observation history, tracking snapshots, and plain-text reports |
| `pricing` | Bazaar fetches, NPC/George sale defaults, and salvage valuation |
| `ui` and `ui.screen` | HUD, graph rendering, and screens |
| `integration` and `mixin` | Optional Mod Menu, pickup hooks, and nearby ritual particle observations |

Keep server-format parsing separate from accounting. Price and display changes should preserve recorded history unless the player explicitly requests a correction or repricing. Config field names and ledger JSON are save compatibility contracts.

`NpcPrices` holds the base sale table, separate from summon acquisition costs. `Config` chooses the valuation for future loot; `GearValuation` applies independent armor and weapon salvage choices. An entry's `intentionalZero` flag distinguishes excluded Ironman rewards from missing prices. Preserve it when copying or correcting receipts, and calculate warnings from receipts rather than the player's current pricing mode.

For a new achievement, add a stable entry in `Achievements.definitions()`; add a metric and fact only if existing metrics cannot express the goal. Keep IDs permanent and increment the catalog version when adding definitions so existing history fills silently. UI filtering and notifications should not change the ledger's financial entries.

`HistoryIndex` joins receipts to immutable fight snapshots once per calculation, sharing qualification and detected-source rules with achievements and records. `ProfitBreakdown`, `SessionSummary`, and `PersonalRecords` are independent of Minecraft.

`RngSince` derives pet and fang observations from the same qualifying fight history. Receipts retain their original fight association; manual corrections are excluded from detected reward rates and dry-run resets. Achievement dry metrics reuse a joined history snapshot, and their earned state remains separate from recalculated intervals.

`FarmingEvents` and `PedestalTimer` hold optional farming cues separately from financial tracking. Timer zero is presentation only: actual server signals still control spawn, death, and active time. `FarmingPreferences` owns bounded timing and alert choices; the settings screen edits a copy before committing it. Keep sound, chat, and overlay delivery local, and preserve saved notification acknowledgments when preferences change.

Documentation sources are maintained in `docs/wiki` and built with MkDocs. The docs workflow automatically deploys documentation changes to [GitHub Pages](https://olliehope.github.io/arachne-ledger/); navigation and site settings live in the root `mkdocs.yml`.

For a release, update the version and changelog, run a clean build, then upload the installable JAR to GitHub Releases or Modrinth.
