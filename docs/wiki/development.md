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
| `config` | Saved preferences and persistence |
| `tracking` | Tracker orchestration, fight lifecycle, active clock, summaries, and duplicate reconciliation |
| `skyblock` | Server text, location, item IDs, and reward parsing |
| `ledger` | Recorded entries, joined history, totals, projections, RNG breakdown, recaps, records, and CSV export |
| `achievement` | Data-driven milestone definitions, trusted facts, unlock evaluation, and saved state |
| `diagnostics` | Bounded observation history, tracking snapshots, and plain-text reports |
| `pricing` | Bazaar fetches and NPC/salvage valuation |
| `ui` and `ui.screen` | HUD, graph rendering, and screens |
| `integration` and `mixin` | Optional Mod Menu and pickup hooks |

Keep server-format parsing separate from accounting. Price and display changes should preserve recorded history unless the player explicitly requests a correction or repricing. Config field names and ledger JSON are save compatibility contracts.

For a new achievement, add a stable entry in `Achievements.definitions()`; add a metric and fact only if existing metrics cannot express the goal. Keep IDs permanent and increment the catalog version when adding definitions so existing history fills silently. UI filtering and notifications should not change the ledger's financial entries.

`HistoryIndex` joins receipts to immutable fight snapshots once per calculation, sharing qualification and detected-source rules with achievements and records. `ProfitBreakdown`, `SessionSummary`, and `PersonalRecords` are independent of Minecraft.

Documentation sources are maintained in `docs/wiki` and built with MkDocs. The docs workflow automatically deploys documentation changes to [GitHub Pages](https://olliehope.github.io/arachne-ledger/); navigation and site settings live in the root `mkdocs.yml`.

For a release, update the version and changelog, run a clean build, then upload the installable JAR to GitHub Releases or Modrinth.
