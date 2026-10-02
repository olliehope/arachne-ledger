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
| `tracking` | Fight lifecycle, eligibility, and duplicate reconciliation |
| `skyblock` | Server text, location, item IDs, and reward parsing |
| `ledger` | Recorded entries, history, totals, and projections |
| `pricing` | Bazaar fetches and NPC/salvage valuation |
| `ui` and `ui.screen` | HUD, graph rendering, and screens |
| `integration` and `mixin` | Optional Mod Menu and pickup hooks |

Keep server-format parsing separate from accounting. Price and display changes should preserve recorded history unless the player explicitly requests a correction or repricing. Config field names and ledger JSON are save compatibility contracts.

Wiki sources are maintained in `docs/wiki`. GitHub's wiki is a separate Git repository; a source-repository commit does not publish wiki changes automatically.

For a release, update the version and changelog, run a clean build, then upload the installable JAR to GitHub Releases or Modrinth. The build workflow validates pushes and pull requests; publishing a release is a separate action.
