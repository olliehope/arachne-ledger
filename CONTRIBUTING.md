# Contributing

Arachne Ledger is a client-side Fabric mod for Minecraft Java **26.1.2**. Start with [README.md](README.md) for behaviour, [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for boundaries and extension recipes, and [docs/ROADMAP.md](docs/ROADMAP.md) for proposed work. Public release preparation is described in [docs/RELEASING.md](docs/RELEASING.md).

## Development setup

Use **JDK 25** and the checked-in Gradle wrapper. Configure `JAVA_HOME` to your JDK if Gradle selects another Java installation. Dependency versions are configured in `gradle.properties` and consumed by `build.gradle`; the Minecraft, Fabric Loader, and Fabric API requirements are also recorded in `src/main/resources/fabric.mod.json`.

From this directory on Windows:

```powershell
.\gradlew.bat build
.\gradlew.bat runClient
```

On macOS or Linux:

```sh
./gradlew build
./gradlew runClient
```

The first build needs access to the configured dependency repositories. After the dependencies and game assets are cached, `--offline` can be useful; it is optional and cannot replace an initial download. Do not check in machine-specific Gradle cache paths or JDK locations. The development client uses the local `run/` directory; do not commit its settings, logs, worlds, or account data.

## Changes and verification

Keep changes focused. Preserve saved-data compatibility and the accounting invariants in the architecture document. Prefer a small helper with an explicit input/output over another responsibility in the Fabric entry point or Tracker. Explain why a comment matters—such as a packet ordering rule, migration constraint, or ownership invariant—rather than narrating every line.

`build` includes `check`, which runs the main-class `*Checks` regression suites through Gradle JavaExec tasks. The standard Gradle `test` task is disabled. Targeted examples:

```powershell
.\gradlew.bat verifyHistory
.\gradlew.bat verifyFight
.\gradlew.bat verifyLootTracking
.\gradlew.bat verifyPetReward
.\gradlew.bat verifyBazaar
```

Use the equivalent `./gradlew` commands on macOS/Linux. Accounting, parsing, migration, and lifecycle changes need a regression that would fail before the fix. Drive Tracker with realistic event sequences and explicit timestamps, then check the journal, history, totals, and graph together. Include a rejected or out-of-order input when it changes the correctness of the result.

**Deterministic checks must not contact Hypixel or any live price service.** BazaarPrices accepts a fetch supplier and executor for fixtures; exercise success, malformed data, stale data, throttling, and failure fallback with those. Do not add credentials, API keys, clock-dependent waits, or live server joins to the build.

For GUI changes, use a local development client and check the affected screen at both ordinary and high GUI scales. Verify interaction as well as appearance, including scrolling, scope changes, edit/save behaviour, and returning to the parent screen. Synthetic data can verify rendering and interaction; it does not prove that Hypixel emits the expected packets.

## Live validation

Changes to server message parsing, ownership, reward matching, or valuation need manual live acceptance evidence before being described as live-verified. This is separate from the deterministic build and does not require a live connection for a contributor's local test run.

- Verify Sanctuary detection from the visible sidebar and tab list, including custom formatting and a warp out/back.
- Observe your own and another player's summon placements. Only your costs should be charged, and placements after one death should belong to the next fight.
- Compare a completed fight's visible labels and inventory changes with its recorded quantities. Check stand-first and pickup-first arrivals, repeated labels, and a new spawn before old rewards disappear.
- Compare an Epic/Legendary Tarantula floating name and personal claim with one recorded pet and one rarity-coloured title. `/arachne rng test` checks title visibility only; it does not verify server reward detection.
- Check a qualifying damage line, low/zero participation, a missing summary, and the 60-second AFK boundary. Inspect skipped-fight reasons and confirm that rejected participation creates no kill.
- If pricing changes, verify a successful live refresh, manual override, disabled auto mode, and a failure retaining saved values. Do not infer success from fixture tests.

Record the game/mod versions, the exact scenario, the expected/observed result, and whether the evidence was synthetic or live. If live access is unavailable, state that limitation in the change description and leave live acceptance pending. The 1.5.0 client smoke test did not join Hypixel, and its live Bazaar request failed; fixture parsing and saved fallback were checked separately.

## Bug reports and private data

For detection problems, `/arachne debug` writes visible sidebar/tab text to `config/arachneledger/detection-debug.txt`. A short relevant excerpt is usually enough. Include the missed message or label, its quantity/rarity when relevant, and the steps that reproduced the issue.

Before sharing logs, screenshots, exports, or fixtures, remove unrelated chat, player names/UUIDs, profile names, and account/session credentials. Do not upload the whole Minecraft or `config/arachneledger` directory. Local ledgers are named for a Minecraft account and contain profile-specific history; use synthetic data for committed tests.

New source and documentation contributions use this project's [MIT licence](LICENSE). Cite external protocol or UI references when they explain a decision. Do not copy another mod's implementation without checking its licence and required attribution.
