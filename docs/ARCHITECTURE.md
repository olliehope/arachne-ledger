# Architecture and extension guide

Arachne Ledger uses one journal for all financial values. Minecraft adapters observe events; Tracker decides whether they are eligible and which fight owns them; Ledger records and calculates them; the UI reads those results. The mod never controls combat or movement.

All Java classes below are in `dev.arachneledger`, apart from the `mixin` and `integration` subpackages.

## Reading the code

Start with a single event and follow its path:

1. `ArachneLedger.tick` shows the client loop: prepare context, advance tracking, apply prices/titles, observe labels, publish summaries, then handle keys and requested screens. `receive` is the server-chat entry point; `PickupMixin` is the local pickup entry point.
2. `Messages`, `LootLabels`, and `PetDrops` translate server text into domain inputs. They parse without writing money or choosing a fight.
3. `Tracker.message` handles lifecycle decisions in server-event order. Its named helpers make spawn confirmation, death, damage qualification, summon ownership, and personal pet receipts separate steps. Reward observers share the accepted-loot path after deduplication.
4. `Ledger.add` records a receipt. `LedgerTotals` turns selected receipts into fight/session totals; `Analytics` and `GraphData` derive rates and graph series. Display code never maintains another profit counter.
5. `DashboardScreen`, `Hud`, and `Graph` organize rendering into named layout and drawing steps. Start with their top-level `init`, `render`, or `draw` method, then follow only the helper for the feature you are changing.

The saved models intentionally retain their existing field names. `Config.validate` separates manual-value validation, legacy defaults, automatic-price validation, and HUD normalization. `Ledger.validate` separates history checks, stable-ID migration, journal validation, and cache reset. Read those stages before changing the save format.

Build logic is separated too: `build.gradle` declares compilation and regression suites, `gradle/java-format.gradle` owns formatting, and `gradle/release.gradle` owns archives and release metadata checks. Use `formatJava` to apply the style and `checkJavaFormat` to verify it.

## Responsibilities

| Component | Responsibility and boundary |
| --- | --- |
| `ArachneLedger` | Fabric initialization, client events/keybindings, deferred screen requests, HUD registration, and context preparation. `prepareContext` handles a world/account change before packets or ticks can reuse an old fight. |
| `integration.ModMenuIntegration` | Optional Mod Menu entrypoint. Its lazy screen factory opens the existing Prices screen with the supplied parent. The API is compile-only and stays out of normal client initialization, so the tracker starts without Mod Menu installed. |
| `LedgerCommands` | Builds the client Brigadier command tree from an explicit Tracker and `Actions` callbacks for dashboard, HUD editor, context refresh, diagnostics, and local messages. Command registration does not own a second tracker. |
| `ClientMessages` | Formats local chat and kill summaries, and writes a supplied location snapshot to a diagnostic file. Compatibility delegates remain in ArachneLedger. |
| `GameContext`, `LocationDetection`, `TrackingArea` | Read the active sidebar/tab/server context, normalize server text, and decide whether location or temporary boss evidence permits tracking. Explicit other areas and world changes clear that evidence. |
| `Messages` | Parses anchored server-style spawn, activity, death, own placement, and damage messages. Ordinary player chat must not become a boss event. |
| `LootLabels`, `ItemIds`, `PickupMixin` | Turn formatted armor stand labels and local-player pickup packets into catalogue IDs and counts. Pet rarity depends on preserved component colours, explicit Epic/Legendary names, or item metadata. |
| `PetDrops` | Parses the anchored personal Tarantula pet-claim receipt, including SkyHanni's explicit-rarity variant. It shares LootLabels' pet identity rules; Tracker determines its reward-window eligibility. |
| `Tracker` | Coordinates eligibility, active time, fight lifecycle, summon ownership, delayed summaries, persistence, and accepted reward notifications. Public observation methods take timestamps so event sequences can be tested. |
| `LootDeduplicator` | Keeps observed stand UUIDs and short-lived item quantity balances. Returns newly observed units after split/stacked counterpart reconciliation. Pets retain cumulative stand/pickup/claim coverage for the whole reward window; this class does not parse labels, choose fights, value items, or write the journal. |
| `PurseCoins` | Parses purse/gain snapshots and pairs marked gains with positive balance deltas. Returns accepted coin amounts, with baselines and a short delayed-annotation allowance; it does not decide farming/menu eligibility or write entries. |
| `Ledger`, `FightRecord` | Persist journal entries and fight metadata. Calculate scope totals, fight breakdowns, values, graphs, and corrections from those entries. |
| `LedgerTotals` | Temporary accumulator shared by fight and session totals. Includes each receipt once, keeps Scavenger as a revenue subtotal, and returns immutable display results. It owns no saved state. |
| `Analytics` | Calculates observed active-time pace, averages, and the rolling current-session projection. It does not estimate unseen drops. |
| `GraphPreferences`, `GraphData`, `GraphOptionsScreen` | GraphPreferences stores shared graph dashboard/HUD series and text choices. GraphData derives immutable actual/projection snapshots and known-spawn markers from recorded entries and fights. GraphOptionsScreen edits display preferences without changing accounting. |
| `Config`, `Store` | Validate settings and saved data, select effective valuations, migrate older fields, and write JSON with a backup and atomic replacement where supported. |
| `GearValuation`, `ValuationScreen` | GearValuation selects the NPC/manual-sale or 5-Spider-Essence basis for base armor and Arack. ValuationScreen saves separate armor/weapon settings and the Scavenger toggle; it never initiates a sale or salvage action. |
| `BazaarPrices` | Fetches optional public price snapshots off the client thread, validating instant-sell and sell-offer sides independently. Its client-thread `tick` applies both caches; Config selects the mode only after manual-price priority. A supplier/executor boundary permits deterministic fixtures. |
| `RngAlerts` | Queues accepted rare rewards and renders local fading titles with optional recorded values. Unpriced captions are empty and reserve no layout space. It does not modify server titles or financial records. |
| `Hud`, `Graph`, screens | Present Ledger/Analytics results and call Tracker/Config operations. FightDetailsScreen and FightEditScreen use journal-derived fight values and absolute quantity edits. |

## Event flow

1. A Fabric event or pickup mixin prepares the current world/account context, then observes a message, location, or item.
2. Tracker checks storage readiness, pause state, area, and the relevant time window.
3. Any player's completed summon can set a temporary Summoning state, but own placements alone become stable pending cost entry IDs. Actual spawn dialogue starts active time and associates the surviving own summon entries with its fight ID.
4. Death records timing and freezes the damage threshold. A valid damage line within five seconds chooses the participation outcome and, when qualified, creates one KILL entry.
5. An eligible item reward passes duplicate suppression before creating a LOOT entry at `Config.lootPrice`, including the selected gear basis. Only this accepted automatic item path feeds rare-drop titles. Eligible marked purse deltas create INCOME entries separately.
6. Totals, history rows, graphs, and delayed kill summaries derive money from the same entries. The UI does not maintain independent profit accumulators.

Armor stands are scanned near the player for 45 seconds after boss death. Physical pickups also have a broader activity window. LootDeduplicator's `hasSeenStand`, `acceptStandCount`, and `acceptPickupCount` methods retain UUID identity and reconcile item quantity balances for ten seconds, in either arrival order. A label of ten units followed by two five-unit pickups records ten total; a five-unit pickup before that label leaves five new units to record. `beginRewardWindow()` clears balances but preserves known stand UUIDs; `reset()` also clears UUIDs. These are observed reward signals rather than proof of a completed sale or guaranteed personal inventory receipt.

Tarantula pet claims share the 45-second post-death window. Fabric message adapters preserve Component colours before passing text to Tracker; ordinary boss parsing still strips formatting. `PetDrops.claim` requires a complete personal server receipt and a supported rarity. Pet duplicates retain cumulative coverage for all three sources, so a late claim cannot recount matched stands/pickups. Repeated claims remain suppressed until the next reward window. A title is queued only for accepted journal units; `/arachne rng test` is a separate preview that never writes an entry. RngAlerts uses a visibility-adjusted clock so inventory screens and F1 do not consume its display lifetime.

Tracker's `observePurse` continuously supplies sidebar snapshots and eligibility to PurseCoins, including when tracking is ineligible so unrelated gains establish a baseline. Only a fight or its first ten post-death seconds are eligible; menus and their two-second closing cooldown are excluded. PurseCoins pairs the positive balance delta and `(+amount)` annotation within two seconds, consumes the accepted allowance once, and returns the amount for one `SCAVENGER_COINS` INCOME entry. It cannot establish whether another marked reward during farming was actually Scavenger.

## Accounting and lifecycle invariants

- An immutable `Ledger.Entry` has stable `id` and `fightId` values. Repricing and association preserve its identity. Pending summons refer to IDs, not list indices that can move after undo or correction.
- `fightId=0` means an entry is unassociated. Pre-1.5.0 financial history receives entry IDs during validation but does not acquire invented fight metadata.
- FightRecord stores session, spawn/death and active timestamps, damage, the threshold used at death, and an outcome. Revenue, costs, drops, and whether a KILL remains recorded come from Ledger entries.
- The current session uses both a persistent session ID for fights and a journal boundary/time origin for totals. Inserting or removing an older fight's reward must adjust the journal boundary without changing current-session totals.
- Per-fight quantity edits replace the item's absolute count. Existing mixed-price receipts use their weighted recorded unit value; a previously missing item uses the current effective price. Zero removes the item. Entries remain ordered by active elapsed time for graphs and rolling analytics.
- Costs placed after one death are pending for the next fight. Late reward labels during the prior reward window stay with the prior fight even if another spawn occurs.
- Zero/low/missing damage does not create a KILL or success report. Actual recorded spend and rewards remain. Undoing a KILL produces a visible “Kill entry removed” reason rather than silently changing its original damage decision.
- Active time begins at spawn, or recovered fresh activity with unknown duration, and runs through 60 seconds after death unless another spawn starts. Pre-spawn waiting, paused/outside time, and stalled tick deltas over five seconds are excluded.
- Completed summons by any player may show Summoning for up to 60 seconds; they do not start pre-spawn active time or charge another player's costs. After death, generic activity can recover a new fight only after fresh completed-summon evidence, rather than reviving the previous boss.
- Pause, session reset, world/account/profile changes, and reopening unfinished saved fights finalize interrupted or missing-damage outcomes. They do not carry an old reward window into a new context.
- Configured manual prices, including zero, override Bazaar values. A selected gear salvage basis instead uses `5 * Config.price("ESSENCE_SPIDER")` and preserves its saved sale-price override for returning to NPC mode. Fresh armor/Arack defaults do not overwrite existing settings. A refresh or valuation-mode change affects future entries; explicit Reprice Session updates current-session entries. Historical corrections and manual adjustments do not replay rare-drop titles.
- `Config.BazaarMode.INSTANT_SELL` selects `bazaarPrices` from `quick_status.sellPrice`; `SELL_OFFER` selects `bazaarSellOfferPrices` from `quick_status.buyPrice`. These are pre-tax, top-2%-volume weighted estimates of buyer bids and seller asks respectively. Each side needs usable price/liquidity; a missing side must not borrow the other mode's quote. Legacy caches migrate as instant-sell only.
- Coin observations are snapshots, not repeated reward events. The current purse is not income; only paired positive changes are. `SCAVENGER_COINS` is already counted in ordinary income, so subtotal display must not add it a second time. Coin pairing and menu state reset with fight context changes.
- Graph profit accumulates all income minus costs; loot accumulates only LOOT income; costs accumulate all expense kinds. Hiding a series or text row does not alter any financial entry. The selected enabled primary metric supplies corresponding labels and text values, rather than labelling a loot rate as profit/hour.
- Graph projections use the current session's recent active-time window even in lifetime scope. After at least 60 active seconds and one recent KILL, projected totals equal this scope's current metric value plus its recent rate over five further active minutes. Known-spawn markers use FightRecord.spawnActiveMillis on the scope's active-time axis, falling back to activeStart for older saves. Unknown spawn times have no marker, and skipped fights can still have one.
- Ordinary boss dialogue can create a provisional fight with spawned=0. A later known welcome calls Ledger.confirmFightSpawn on that same ongoing fight, recording its timestamp and active-time marker position, and invalidating derived graph caches. It does not duplicate journal entries or costs, overwrite an already known spawn, or confirm completed/interrupted history. Kill summaries read the persistent fight's duration so late confirmation is included.
- GraphData caches actual journal traversals by Ledger revision and scope, while recent rates also depend on activeMillis. Display selection changes produce a new view snapshot without rewriting entries. Every journal/fight mutation that changes graph data must invalidate the revision; validation clears derived caches when loading data.
- Mutable Tracker/Config/Ledger state belongs to the client thread. Background price work returns a snapshot; it does not mutate them directly. Save failures stop normal tracking rather than replacing recoverable history with an empty ledger.

## Extension recipes

### Add an item

1. Add its stable SkyBlock ID and display name to `Catalog.ITEMS`. Keep IDs unchanged once records can be saved. Set a default value only when its valuation is known; otherwise leave it unpriced. GearValuation support needs a verified NPC/salvage rule and should be limited to attributes actually observable in base reward labels.
2. Check `LootLabels.parse` against actual formatted labels, including `xN` quantity. Add a precise alias if needed. For physical items, confirm ItemIds can obtain the right ID/tier from metadata; do not guess a pet's rarity from an uncoloured name.
3. If Bazaar support is appropriate, verify the public product ID and usable instant-sell/sell-offer fields before changing BazaarPrices support. Test the sides independently, including a one-sided market. An Auction-only item remains manual. Test Config migration so existing explicit prices keep priority. Reward valuation consumers must use `lootPrice`/`lootPriceSource`; raw `price` is the underlying item/material value and deliberately does not apply the gear salvage basis.
4. Choose the item's display colour in Hud. If it is an intended rare-title item, add its exact rarity in RngAlerts and exercise both reward arrival orders; an ordinary drop should not alert.
5. Add parser and tracking fixtures, and check the item's quantity/value in fight, session, lifetime, and graph results. Validate live labels separately as described in CONTRIBUTING.md.

### Add or change a server parser

1. Collect the minimum redacted server message/label and formatting needed to reproduce the issue. Preserve colour when it carries rarity.
2. Update the appropriate boundary: Messages for chat, LocationDetection for area text, LootLabels for reward names, ItemIds for item metadata, or PurseCoins for purse snapshots. Keep server patterns anchored and include a player-chat/mob-name rejection case. A new coin annotation needs delta/baseline/repeated-snapshot tests, not just a string match.
3. Extend the relevant `*Checks` suite with realistic orderings: repeated message, missing summary, delayed label, warp, or a fast next spawn. A parser acceptance alone is insufficient if the event can charge the wrong fight.
4. Keep ownership, timing, duplicate suppression, and journal writing in their existing components. A parser should return data, not change totals.

### Add a dashboard or HUD view

1. Use Ledger stats/fightStats, Analytics, and GraphData instead of recalculating profit in a screen. Preserve distinct dashboard and HUD layout preferences; graph display choices deliberately share Config.graph.
2. A new dashboard page can be a separate Screen with a parent return path. If extending `Config.View`, update every selector/cycle/layout consumer and its validation/migration; enum order is currently used by the three base tabs.
3. Extend graph metrics in GraphPreferences/GraphData rather than changing only the renderer. Test the series totals, corresponding text labels, recent rates, projection endpoints, hidden-series behaviour, and cache invalidation after edits/repricing. For a cached long-history view, invalidate derived values on journal revision and scope changes; active-time rates also depend on the advancing clock.
4. Check clipping, scrolling, hover details, session/total switching, and interaction at small GUI dimensions in a local client. Changes that edit data must also exercise historical session boundaries in HistoryChecks.

These are internal extension points, not a stable public plugin API. Keep refactors bounded and use regression scenarios to prove behaviour before changing persistence or protocol assumptions. See [ROADMAP.md](ROADMAP.md) for proposed features and [CONTRIBUTING.md](../CONTRIBUTING.md) for verification.
