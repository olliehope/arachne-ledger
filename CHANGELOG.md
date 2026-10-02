# Changelog

Public GitHub releases start at 1.0.0. Earlier development builds used separate numbering and are retained below for reference.

## [1.0.1]

- Reconciled reward quantities across labels and split/stacked pickup packets, preventing partial pickups from counting loot twice. Kept pet claim deduplication across all three reward sources.
- Observed container menus every client tick and before packet processing, so brief NPC visits and delayed sales cannot bypass the Scavenger cooldown.
- Preserved typed prices, adjustment amounts, and fight quantities through resize/navigation. Recipe mode keeps the separate fixed Crystal draft. Fixed clicks on hidden fight rows and refreshed edit availability after damage results arrive.
- Allowed valid composite recipe/salvage valuations to record, reprice, correct, and reload, while retaining the manual price limit. Ongoing fight statistics now show their active elapsed time.
- Fixed persistence for relative filenames and reserved unique corrupt-file quarantine paths during backup recovery.
- Kept settings and correction screens open on failed saves, retained their drafts, and blocked repeated mutations. Commands now report success only after persistence remains healthy; diagnostics stay available after storage errors.
- Paused rare-title lifetimes while the overlay is hidden by menus or F1, and kept title previews available while tracking is paused. Previews never add rewards.
- Fixed CI branch selection so pushes to the repository's `master` branch run Windows and Linux checks.
- Removed Gradle caches, generated Minecraft binaries and editor configuration from Git tracking, added repository ignore rules, and made the Gradle wrapper executable on Unix.
- Corrected release numbering and added the project icon and source/issue links to the packaged mod metadata.
- Verified 1,436 checks across 19 regression suites and 52 synthetic Minecraft UI checks at two GUI scales. Live Hypixel acceptance remains pending.

## [1.0.0]

- Initial GitHub release, carrying forward the complete 1.8 development feature set: fight and loot tracking, session/lifetime totals, costs, Scavenger income, valuation settings, graphs, projections, rare-drop titles, and saved history.

## [1.8] (internal development build)

- Fixed missing Tarantula pet rewards and titles for explicit `Epic Tarantula Pet` / `Legendary Tarantula Pet` floating names. Preserved Component colours in chat and added personal pet-claim detection during the boss reward window.
- Deduplicated matching pet labels, pickups, and claims in any order throughout the reward window, including repeated claim messages.
- Added `/arachne rng test [epic|legendary|fang]` to preview a rare-drop title without changing the ledger or profit.
- Fixed Crystal/T2 spawn counts and graph markers when ordinary Arachne dialogue opens a fight before its known spawn welcome. The later welcome confirms that same fight, updates cached graphs, and supplies its kill-summary duration without adding another fight or summon cost.
- Stored the confirmed spawn's active-time position separately so its marker appears at confirmation. Earlier saves keep their original marker positions; old unknown timestamps cannot be reconstructed.
- Added Instant sell and Sell offer choices for automatic Bazaar valuation, with separate saved caches. Manual overrides remain authoritative, prices are before tax, and switching modes does not revalue history without explicit Reprice Session.
- Added shared graph options for the dashboard and graph HUD: net profit, item loot value, costs, optional five-minute projection lines, and known-spawn markers.
- Added a text metric that follows an enabled series, plus independent total, projected-total, hourly, projected-hourly, spawn-count, active-time, and scope rows. Loot series exclude Scavenger/coin income; net profit includes them.
- Calculated projected totals from the selected scope's current value plus five more active minutes at the recent current-session rate. Projection still requires 60 active seconds and a recent qualifying kill.
- Added graph, price-mode, Crystal spawn-confirmation, and pet reward/claim/title regression coverage; all 1,342 checks across 18 suites passed.

## [1.6.0]

- Recognized Arachne's “With your sacrifice.” and “A befitting welcome!” spawn dialogue, and added a temporary Summoning status after any player's completed summon without starting pre-spawn active time. Partial summons and stale post-death dialogue do not restart a fight.
- Added Scavenger coin tracking by pairing marked positive purse annotations with actual purse changes. Duplicate snapshots, delayed annotations, menus, and post-menu cooldowns are handled before adding income to the journal.
- Included accepted coins in profit, hourly rates, projections, graphs, fight details, and chat, with a separate Scavenger subtotal. Other marked coin gains during farming remain indistinguishable from Scavenger.
- Added separate armor and tool/weapon choices in Prices → Salvage. Base drops use their NPC value or 5 Spider Essence; saved sale-price overrides are preserved. Changes affect future entries unless the session is explicitly repriced.
- Set fresh NPC defaults to 2,000 coins per Arachne armor piece and 5,000 for Arack, without overwriting existing saved prices. No automatic selling or salvaging is performed.

## [1.5.1]

- Added release preparation configuration and contributor, architecture, roadmap, and release documentation for preparing a public repository and distribution.
- Added Windows/Linux CI definitions and a tag-triggered GitHub draft prerelease workflow, with version/tag/metadata checks, source ZIPs, checksums, and release notes. Modrinth's first beta upload remains manual.
- Centralized dependency versions in `gradle.properties`.
- Extracted command registration and local message formatting from the Fabric entry point.
- Extracted reward duplicate suppression from Tracker while preserving its loot windows, journal accounting, and fight lifecycle.

## [1.5.0]

- Added the Fights dashboard tab with the latest 50 fights in session or lifetime scope, recorded damage, duration, costs, drops, profit, and skipped-fight explanations.
- Added per-fight quantity corrections, including removing a drop or adding a missing item. Historical corrections preserve session boundaries and recorded valuations.
- Added optional automatic Bazaar prices, per-item manual overrides, saved fallback values, source/update indicators, and throttled refreshes. Prices affect future entries unless the user explicitly reprices the session.
- Added rarity-coloured titles for Arachne's Fang and Epic/Legendary Tarantula pets, with an optional recorded value beside the title.
- Added persistence and migration checks for fight metadata and stable journal identities, plus Bazaar and rare-drop regression coverage.

## [1.4.0]

- Added local chat summaries with kill time, damage, recorded loot value, summon costs, net profit, and unpriced-drop warnings.
- Started active timing at spawn and paused it 60 seconds after death when no new Arachne spawns.
- Required at least 10,000 reported damage by default to count a kill; made the threshold and kill chat configurable.
- Added AFK and waiting-for-spawn status to the HUD and dashboard.

## [1.3.0]

- Simplified the HUD and dashboard using compact Minecraft text rows, smaller session controls, and vanilla rarity colours.
- Made the HUD text-only by default and added an optional background in its editor.
- Restyled buttons, graphs, and supporting screens for a more consistent layout.

## [1.2.0]

- Recognized Hypixel's `You placed...` crystal and Calling messages, including all four Calling placements.
- Added armor stand reward-label detection and quantity parsing for the drops surrounding Arachne.
- Suppressed repeated scans of the same stand and matching physical-pickup duplicates.
- Added Arachne Shard to the item catalogue.

## [1.1.1]

- Fixed Sanctuary detection when Hypixel inserts custom formatting codes, including `§v`, inside the location text.
- Gave the sidebar's Sanctuary sub-area priority over the tab list's broader Spider's Den location.

## [1.1.0]

- Added projected profit/hour from recent active time and expanded dashboard metrics.
- Added the active overlay HUD, its layout choices, and a drag/resize editor.

## [1.0.0]

- Initial Fabric 26.1.2 Arachne tracker with reward valuations, crystal/Calling costs, session and lifetime profit, hourly rates, and graphs.
- Added manual adjustments, price settings, account/profile storage, and CSV export.
