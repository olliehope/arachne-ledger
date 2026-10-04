# Changelog

## 1.4.0

- Reduced graph work with cached history indexes, bounded drawing, and fewer spawn-marker draw calls. Cached HUD rows, dashboard history, and hourly projections across unchanged frames.
- Fixed stale fights blocking a new ritual countdown and recovery when spawn dialogue is missed. Adaptive Crystal sampling tolerates delayed particles and retains the fallback when evidence is weak.
- Added Real Arachne Non, a first-kill achievement, accessible milestone targets, and optional longer challenges. The 28-achievement catalog preserves existing unlocks and fills new goals silently from saved history.
- Added a Progress hub, consistent Session/Total controls, a last-fight summary, and a copyable session recap.
- Added T1/T2 comparisons with recorded earnings, actual costs, and clearly labeled fight-only hourly rates; older fights without a known ritual remain Unknown.
- Added pet and fang graph markers that open their original fight, optional session targets with completion chat, and a carried Crystal/Calling supplies view.
- Added named HUD presets, an optional countdown row, and pinnable achievement progress. HUD, graph, and other settings use Save/Cancel drafts.
- Standardized elapsed time as HH:MM:SS, keeping hours above 24 instead of switching to days.

## 1.3.0

- Added the aligned Loot ledger HUD layout, with observed rates for pets and fangs only. New settings use this layout; saved layouts and individual row choices are kept.
- Added a pedestal spawn countdown after Crystal and Calling placements, configurable fallback delays, adaptive Crystal timing, and optional elapsed fight time. Countdown estimates never record a spawn or change active time.
- Added RNG history with Session/Total scope, detected pet and fang quantities, kill and active-time intervals, observed rates, and longest dry runs.
- Added six dry-streak achievements, bringing the catalog to 23. Existing history fills new milestones silently.
- Updated achievement chat to clickable, colored unlock messages with descriptions on hover; added rare-drop interval chat and compact kill summaries with full details on hover.
- Added Farming cues settings for pedestal labels and independent local titles, sounds, and chat choices. Changes stay in a draft until Save.

## 1.2.1

- Filled base loot prices with NPC sale values and Tarantula pet prices from George.
- Added Ironman pricing: unsellable drops and salvaged essence retain their quantities without coin value or missing-price warnings.
- Added an NPC defaults action and Ironman controls in the price settings and `/arachne ironman` command.
- Kept saved market prices available when switching modes; recorded history changes only when explicitly repricing the session.

## 1.2.0

- Added 17 Arachne achievements with tiers, progress bars, a hidden rare milestone, optional unlock chat, and silent backfill from existing history.
- Added session recaps, personal records, and profit/hour excluding Tarantula pets and Arachne Fangs.
- Added individual HUD row ordering and optional profit-without-RNG rows.
- Added a tracking diagnostics page with live eligibility, accepted/ignored observations, and copy/save reports.
- Split clock, fight reports, CSV export, deferred navigation, and local notifications into focused modules; preserved existing settings and ledger history.

## 1.1.0

- Added Minimal, Classic, and Split HUD layouts, with individual controls for displayed stats and loot rows. Minimal is the default.
- Added a settings hub, HUD display options, and editable tracking and notification settings, accessible from Mod Menu and the dashboard.
- Organized client, SkyBlock, tracking, ledger, configuration, pricing, and UI code into focused packages.
- Shortened the README and added a small GitHub wiki with setup, settings, tracking, and troubleshooting guides.

## 1.0.2

- Improved rare-drop titles, Mod Menu support, and tracking reliability.
- Added fight history, Bazaar pricing, graphs, and configurable HUD options.

## 1.0.1

- Fixed release packaging and repository setup.

## 1.0.0

- First public release of Arachne Ledger.
