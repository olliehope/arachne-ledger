# Development

Use **JDK 25**. From a clone of [the repository](https://github.com/olliehope/arachne-ledger), run:

```powershell
.\gradlew.bat build
```

On Linux or macOS, use `./gradlew build`. Installable JARs are written to `build/libs`. `build` includes formatting validation and the registered regression suites; `test` alone is not the project's regression runner.

Run `./gradlew formatJava` or `.\gradlew.bat formatJava` before committing Java changes.

## Code map

| Package | Responsibility |
| --- | --- |
| `client` | Fabric lifecycle, commands, and local messages |
| `config` | Saved preferences, validation, and persistence |
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

`HistoryIndex` joins receipts to immutable fight snapshots once per calculation, sharing qualification and detected-source rules with achievements and records. `ProfitBreakdown`, `SessionSummary`, and `PersonalRecords` are independent of Minecraft. Add meaningful scenarios to the registered regression suites when changing eligibility, persistence, or accounting; GUI behavior can be checked in an isolated client fixture.

Wiki sources are maintained in `docs/wiki`. GitHub's wiki is a separate Git repository; a source-repository commit does not publish wiki changes automatically.

For a release, update the version and changelog, run a clean build, then upload the installable JAR to GitHub Releases or Modrinth. The build workflow validates pushes and pull requests; publishing a release is a separate action.
