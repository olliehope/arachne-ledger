# Arachne Ledger 1.8

Client-side Hypixel SkyBlock Arachne profit tracker for **Minecraft Java 26.1.2 / Fabric**.

[Changelog](CHANGELOG.md) · [Contributing](CONTRIBUTING.md) · [Architecture and extension guide](docs/ARCHITECTURE.md) · [Roadmap](docs/ROADMAP.md) · [Publishing and releases](docs/RELEASING.md)

Version 1.8 adds instant-sell/sell-offer Bazaar estimates and shared graph options for profit, loot, costs, projections, spawn markers, and text rows. Existing history and manual price overrides are preserved. For GitHub setup and tag/release steps, use the publishing guide. This source has substantial AI-generated contributions; the guide explains how Modrinth's current listing policy affects publication.

## Install

1. Install Minecraft Java 26.1.2 and Fabric Loader 0.19.5 or newer for that version.
2. Put Fabric API 0.155.3+26.1.2 and `arachne-ledger-1.8.jar` in your Minecraft `mods` folder. When updating, close Minecraft and remove the old Arachne Ledger JAR first. Your existing prices and saved history are preserved.
3. Start the game and join Hypixel SkyBlock. Press **O** or type `/arachne` to open the dashboard.

The mod requires Java 25, as Minecraft 26.1.2 does. The HUD appears while you are in Arachne's Sanctuary. Keybindings are configurable in Minecraft Controls.

## What it tracks

- Arachne rewards shown as armor stand labels around the defeated boss, such as `Spider Essence x8`, `Spider Eye x30`, and Arachne Shard. The quantity on each label is recorded once per armor stand. Physical item pickups are still supported, with a short matching window to avoid duplicate counts. Tarantula pets also support explicit names such as `Legendary Tarantula Pet` and your personal pet-claim message during the 45-second reward window.
- Arachne Crystals and Arachne's Callings **you** place, identified from both your player name and Hypixel's `You placed...` messages.
- Boss kills where the server reports at least **10,000 damage**, with an adjustable minimum. Zero and lower-damage participation do not count as kills or print success summaries.
- Marked purse gains during fights or shortly after death, shown as Scavenger coins and included in profit.
- Loot value, costs, net profit, profit per active hour, kills, crystal count, and a cumulative net profit graph.
- Session and lifetime totals, stored separately per Minecraft account and optional manual SkyBlock profile name.

The active timer starts at Arachne's spawn. Known spawn dialogue includes **“With your sacrifice.”** and **“A befitting welcome!”** for the different summons. It includes the fight and up to **60 seconds after death**, then automatically pauses as AFK if no new Arachne has spawned. Another spawn resumes timing immediately.

A completed summon by any player shows **Summoning** for up to 60 seconds while Arachne awakens. This status does not start pre-spawn active time; only your own placements add costs. Partial Callings, entering the Sanctuary, and movement do not start the timer. When joining mid-fight, fresh boss activity can recover tracking, with an unknown spawn time. After a death, generic boss dialogue cannot revive the old fight unless a fresh completed summon established that another boss is awakening. Manual pause, leaving the area, and disconnecting stop tracking.

New Session starts a new session without deleting lifetime history. The dashboard offers Overview, Drops, Graph, and Fights tabs. The separate live HUD has its own Compact, Detailed, and Graph layouts. Graphs use active time and recorded event values; hover over a line for its value and timestamp.

The UI uses a simpler SkyHanni-inspired layout: floating Minecraft text, compact quantity/name/value rows, and small session controls. The HUD is text-only by default. Its editor can enable a subtle background for readability. The dashboard uses a compact list instead of statistic cards, with details available on hover.

**Actual / hr** is net profit divided by active time for the selected session or lifetime scope. **Projected / hr** extrapolates the current session's latest five active minutes (or elapsed session time if shorter), including costs and the one-minute grace between fights; AFK time is excluded. It needs at least 60 active seconds and one kill in that window. Below three kills it shows a small-sample indicator. The projection always uses current-session pace, even while viewing lifetime totals. Rare drops can swing this estimate.

Additional metrics include crystal and Calling costs, average net per kill, kills/hour, average time per kill, recorded loot values, and unpriced drops. Rates and profits are in SkyBlock coins.

## Graph options

Open **Graph → Options**, or choose the Graph layout in the HUD editor and open **Graph options**. These preferences are shared by the graph dashboard and graph HUD; they do not change the ledger or the other HUD layouts.

When earlier boss dialogue opens a fight with an unknown spawn time, the later known welcome now confirms that same fight and adds its spawn marker. This fixes missed Crystal/T2 counts without duplicating fights or costs. The marker uses the confirmation's active-time position. This correction applies to newly tracked fights; older unknown spawn times cannot be reconstructed.

In **Lines**, show any combination of **Net profit**, **Loot value**, and **Costs**. Profit includes item values and coin income minus all recorded costs. Loot includes item values only, excluding Scavenger and other coin income; costs include crystals, Callings, and manual expenses. **Text metric** chooses which enabled line supplies the total/hourly labels and values, so choosing Loot changes the text to loot totals and loot/hour.

Optional **Projection** lines extend the selected totals for five more active minutes at the current session's recent pace. The sample is the last five active session minutes, or the shorter elapsed session, and requires at least 60 active seconds and one recent qualifying kill. A projected total is **the selected scope's current value + recent hourly rate × 5/60**, even when viewing lifetime totals. This extrapolates observed values; it does not predict an unseen RNG drop.

Optional **Spawn markers** and the spawn count show known Arachne spawns, including fights skipped for low damage. Fights joined after their spawn are excluded, so the count differs from qualifying kills. In **Text**, independently show/hide total value, projected total, hourly rate, projected hourly rate, Arachne spawns, active time, and session/total scope. Hiding a row does not stop tracking or switch scope.

## Kill chat and participation

Each qualifying kill prints a coloured local chat summary after a short reward-collection delay, for example:

`[Arachne] Kill #8 · 50.0s · Profit +205.1k coins (rewards 223.0k, cost 17.9k) · Damage 142.0k`

The summary uses spawn-to-death time and recorded rewards, including tracked coin gains, minus **your** summon costs for that fight. Other players' placements cost you zero. Your four Callings are charged separately when applicable. Summon placements for the following fight are excluded from the previous report. Unpriced items add a warning, and tracked coins add a Scavenger subtotal. Normal reporting starts three seconds after death and waits for 750 ms without new rewards, up to ten seconds. Rewards detected later still update the ledger, but do not rewrite an already printed summary. Item values are estimates, not completed sales.

Kills need the server's `Your Damage` line within five seconds of `ARACHNE DOWN!`. Damage must be **at least 10,000** by default; zero, lower damage, missing or late damage summaries do not create a kill or success report. Duplicate results cannot count another kill or reset AFK without a new fight. Summon expenses and recorded drops stay in the ledger even when participation does not qualify as a kill.

Use `/arachne mindamage <damage>` to change the minimum and `/arachne chat on` or `off` to control summaries. The AFK policy is automatic and does not change the manual Pause setting. The HUD and dashboard show AFK while waiting for the next spawn. Previous history keeps its recorded times and kills; use Reset to start a fresh session under the new timing rules.

## Prices and costs

Crystals use the current Shaggy recipe by default: **2 Arachne Fragments + 16 Enchanted Spider Eyes + 16 Enchanted String**. The initial material values are NPC sell values, so the initial recipe estimate is **17,896 coins**. Change material prices on the Prices screen to reflect your own opportunity cost, or switch to a fixed crystal cost. `/arachne crystal <coins>` sets a fixed cost; `/arachne recipe` switches back. An Arachne's Calling costs 0 until you enter its cost in Prices.

The default Soul String, String, Spider Eye, Enchanted String, Enchanted Spider Eye, and Arachne Fragment prices are NPC sell values. Fresh settings also value each Arachne armor piece at **2,000 coins** and Arack at **5,000 coins**. Other item prices start at 0 and show as unpriced. Existing saved prices, including explicit zero values, are kept. Set each item to the net amount you expect to receive. **Existing entries keep the value used when recorded**; the Prices screen has a Reprice Session button if you want to update the current session's history and graph. Older sessions retain their values.

Profit is estimated item value plus recorded coin income, minus crystal/Calling and manual expenses. It is **not** a record of actual Bazaar or Auction House sales. Optional automatic Bazaar pricing does not require an API key.

Spider Essence, Arachne Shard, and other rewards without a default sale value still appear in drop counts, but contribute zero coins until you set their prices. Use **Prices** or `/arachne price <ITEM_ID> <coins>` and the dashboard's unpriced indicator to check your valuation. Armor stand labels show spawned rewards; if you leave an item behind, adjust or undo its entry.

### NPC or salvage values

Open **Prices → Salvage** to choose armor and tools/weapons separately. NPC mode uses the fresh defaults above or your saved manual sale-price override. Salvage mode values each unupgraded Arachne armor piece or Arack as **5 Spider Essence**, using the current manual/Bazaar Spider Essence value. It does not also add an NPC sale value. If essence is unpriced, its salvage value is zero and the gear is unpriced too.

Selecting salvage preserves your saved sale-price override; switching back restores that basis. These settings affect future rewards only until you explicitly Reprice Session. The mod does not salvage or sell items automatically, and upgraded/starred gear bonuses are not inferred from reward name tags.

## Scavenger coins

Scavenger tracking is on by default and can be toggled in **Prices → Salvage**. It pairs the sidebar's yellow **`(+amount)`** annotation with a real positive purse change, accepting a delayed annotation for up to two seconds. Repeated snapshots cannot add the same gain again. The first observation establishes a baseline rather than treating your existing purse as income.

Coins are eligible only during a tracked fight or within **10 seconds after its death**. Container menus are excluded, with a further two-second cooldown after closing them to avoid delayed sale updates. Pauses, area/world changes, missing purse text, and newly eligible tracking establish a fresh baseline. Ordinary unmarked purse changes are ignored.

Accepted gains become income entries, so they are already included in profit/hour, projections, graphs, fight profit, and chat rewards. The Detailed HUD, Overview, fight details, and applicable chat summaries show a separate Scavenger subtotal; do not add it to profit again. Other marked coin rewards received while farming cannot be distinguished from Scavenger by these client signals.

## Recent fights and corrections

Open **Fights** in the dashboard for the latest 50 fights in the selected session or lifetime scope. Each row shows kill time, recorded net profit, damage and whether the fight counted. Click a row for its drops and summoning costs. Skipped fights explain zero damage, insufficient damage, a missing damage summary, or interrupted tracking. The minimum damage in each result is the threshold used when that fight died; changing it later does not rewrite old results.

**Edit drops** sets an item's absolute quantity for that fight. Set 0 to remove it, or select a previously missing item and enter its count. The correction updates the same journal used by fight profit, totals and graphs. Editing an older session does not add its rewards to the current session. Existing items keep their recorded unit value; mixed-price entries use their weighted average. A newly added item uses its current effective price. Corrections and repricing do not show rare-drop titles.

Fight metadata starts with version 1.5.0. Earlier totals remain intact, but cannot be reconstructed into reliable fight breakdowns because their spawn times and damage were not saved. Tracking stopped during a fight is shown as interrupted when reopening the game.

## Automatic Bazaar prices

Automatic pricing is **off by default**. In **Prices**, choose **Use Bazaar items** to opt supported items into automatic values while keeping other prices manual. Each item has a Manual/Auto selector. **Manual overrides win over Bazaar values**, including explicit zero values. The separate gear salvage choice uses the selected basis without erasing its sale-price override. Existing saved prices migrate as manual overrides and are never silently replaced. `/arachne bazaar defaults` performs the same bulk opt-in; `bazaar on` only enables fetching and preserves overrides.

In **Prices**, select **Instant sell** or **Sell offer** for automatic valuations. Instant sell uses the API's `quick_status.sellPrice` buyer-bid estimate; Sell offer uses `quick_status.buyPrice`, the seller-ask estimate. Both are volume-weighted estimates over the top 2% of their order-book side, **before tax**, rather than a guaranteed sale price. Sell offers can take time or remain unfilled. Manual prices still win, and each mode has a separate saved cache; old settings default to Instant sell rather than treating an old cache as a sell-offer price.

Prices refresh in the background at most once every five minutes from the public Hypixel Bazaar endpoint. The screen shows the source, selected mode, update age and refresh errors; values become marked stale after 15 minutes. Cached or saved fallback values remain available if a request fails or the selected side has no usable price. Crystal recipes and salvage use the effective material values as opportunity costs; set a fixed crystal cost to reflect a different purchase price. Refreshing or switching mode affects future entries; only explicit Reprice Session changes recorded history.

Pets and other items absent from the Bazaar retain manual values. No Auction House price is guessed. Use `/arachne bazaar` for status, `bazaar on|off` to control requests, and `bazaar refresh` to request a throttled refresh.

## Rare-drop titles

A detected **Arachne's Fang**, **Epic Tarantula**, or **Legendary Tarantula** shows a fading title with its recorded drop value to the right. Fang uses Uncommon green, Epic uses dark purple and Legendary uses gold. An item without a price shows **Unpriced**. The value is the drop's value, not the whole fight's net profit. Alerts queue when several rare drops appear and do not overwrite server titles. Hologram colours are preserved so a plain `[Lvl 1] Tarantula` label can be assigned its supported rarity safely. Explicit `Epic Tarantula Pet` and `Legendary Tarantula Pet` names also identify rarity, without needing a colour code.

Personal `You claimed a Tarantula Pet! You can manage your Pets...` receipts are also detected during the 45 seconds after Arachne's death. Their original rarity colour or an explicit Epic/Legendary name is required. Matching pet labels, pickups, and claims are recorded once across that reward window, in any arrival order. Repeated claim messages cannot add another copy. Claims outside a boss reward window, other players' messages, and unknown rarities are ignored.

Use `/arachne rng on|off` to control titles and `/arachne rng value` to show or hide their values. `/arachne rng test` previews a Legendary title; append `epic`, `legendary`, or `fang` to select one. Previews use your configured price and never add loot or change profit. Titles require the GUI to be visible; they appear in ordinary gameplay and chat, and last four seconds. Opening an inventory or dashboard hides the title without pausing its lifetime.

## Controls

- **O**: Open the dashboard.
- **Dashboard Scope**: Switch session and total.
- **Overview / Drops / Graph / Fights**: Switch dashboard tab without changing the HUD layout.
- **Graph → Options**: Choose graph lines, projections, spawn markers, the text metric, and individual text rows; the graph HUD shares these choices.
- **HUD** or `/arachne hud edit`: drag the overlay, resize it, change its layout, reset its position, or toggle its background. The preview works outside the Sanctuary.
- `/arachne view`: cycle the HUD layout. `/arachne hud on` or `off`: toggle visibility. `/arachne hud always`: toggle visibility throughout SkyBlock, including waiting status. The HUD remains visible while chatting.
- **Prices**: Set crystal, Calling and drop values; select Instant sell/Sell offer for automatic prices. **Salvage** opens separate armor/weapon valuation choices and the Scavenger toggle.
- **Adjust**: Add a missed item, income, or expense; undo the latest session entry; export a CSV.
- **Reset**: Two clicks within five seconds start a new session; lifetime totals stay intact.
- **Pause**: Stop both the active timer and automatic tracking; resuming waits for a fresh spawn or mid-fight activity.

Commands: `/arachne bazaar [on|off|defaults|refresh]`, `/arachne rng [on|off|value|test [epic|legendary|fang]]`, `/arachne chat on|off`, `/arachne mindamage <damage>`, `/arachne help`, `/arachne session`, `/arachne total`, `/arachne pause`, `/arachne view`, `/arachne new`, `/arachne undo`, `/arachne crystal <coins>`, `/arachne recipe`, `/arachne price <ITEM_ID> <coins>`, `/arachne add <ITEM_ID> <count>`, `/arachne income <coins>`, `/arachne expense <coins>`, `/arachne export`, `/arachne profile <name>`.

If you play multiple SkyBlock profiles on the same Minecraft account, use `/arachne profile <name>` when you switch; the mod cannot identify your active SkyBlock profile from the server. Saves live in `config/arachneledger` and are written periodically and on disconnect. CSV exports are saved under `config/arachneledger/exports`.

## Location detection in 1.1.1

Sanctuary detection reads the visible sidebar and tab location, handles Unicode apostrophes and symbols inserted inside words, and recognizes Hypixel's server brand for alternate addresses. Version 1.1.1 fixes Hypixel's custom `§v` style codes inside sidebar words; the sidebar's Sanctuary sub-area now takes priority over the tab's broader Spider's Den island. This was checked with the captured scoreboard line that previously failed. Recent server boss or summoning messages provide a temporary fallback when SkyBlock is confirmed but the sub-area is unavailable. An explicit different area or world change clears this fallback. Modern player-head decorations and all four Calling placements are supported; only your own placements are charged and kills require the configured minimum reported damage.

If tracking still waits in the Sanctuary, run `/arachne debug`. It displays the detection state and saves visible sidebar/tab text to `config/arachneledger/detection-debug.txt`. This local file may contain visible player names. `/arachne track manual` enables tracking anywhere in confirmed Hypixel SkyBlock when another mod hides location data; use it only while farming, then `/arachne track auto` to restore location detection. Manual mode still requires boss/summoning messages to open loot collection windows.

The armor stand scan covers the visible Arachne rewards for 45 seconds after the boss-down message. Rewards that are never shown as stands, physical item pickups, or eligible marked purse gains may need a manual adjustment. Item values and server message formats can change after a SkyBlock update. Review the Drops tab against your inventory before using the profit number for decisions.

## Build and verify

In this directory, run `gradlew.bat build` on Windows or `./gradlew build` on macOS/Linux using JDK 25. The build produces `build/libs/arachne-ledger-1.8.jar`. **1,342 checks across 18 regression suites passed**, covering accounting, graph series/labels/projections/spawn markers, price-mode selection and caches, location and reward detection, spawn/summoning/AFK timing, purse pairing, NPC/salvage valuations, damage qualification, chat summaries, persistent history/corrections, isolation, migration, Bazaar failure fallback, pet rarity labels, and rare-drop title deduplication.

Run `gradlew.bat clean prepareRelease` to create the installable JAR, complete source ZIP, changelog notes and SHA-256 checksums in `build/release`. On Linux/macOS, use `bash ./gradlew clean prepareRelease`. The included GitHub workflows build pull requests and prepare a draft prerelease when you push a matching `v<version>` tag. Read [Publishing and releases](docs/RELEASING.md) before your first upload.

A previous 1.6.0 local Minecraft client check rendered the dashboard, Scavenger subtotals, fight breakdown, HUD and gear settings at two GUI scales. It exercised a fight quantity correction, navigation to/from Salvage, both valuation toggles and the Scavenger setting, and checked the Summoning HUD after AFK. The 1.8 local client check rendered 20 screens at two GUI scales and exercised graph line/text toggles, metric selection, scrolling, spawn markers, projection layouts, dashboard/HUD navigation, scope switching, Bazaar modes, manual overrides and invalid input. Display and price changes preserved recorded history and active time. The Java Bazaar service also successfully fetched and installed both price caches from the public endpoint without an API key. No live Hypixel server was joined.

Source references: [Fabric 26.1 migration notice](https://www.fabricmc.net/2026/03/14/261.html), [Arachne Crystal recipe](https://hypixelskyblock.minecraft.wiki/w/Arachne_Crystal), [Hypixel public API documentation](https://api.hypixel.net/index.html), [Hypixel Salvaging](https://wiki.hypixel.net/Salvaging), [Arachne's Armor NPC values](https://hypixel-skyblock.fandom.com/wiki/Arachne%27s_Armor), [Arack NPC value](https://hypixel-skyblock.fandom.com/wiki/Arack), [SkyHanni's Arachne message examples](https://github.com/hannibal002/SkyHanni/blob/beta/src/main/java/at/hannibal2/skyhanni/features/chat/ArachneChatMessageHider.kt), [SkyHanni's armor stand loot scan](https://github.com/hannibal002/SkyHanni/blob/beta/src/main/java/at/hannibal2/skyhanni/features/combat/end/ProfitPerDragon.kt), and [SkyHanni's tracker layout](https://github.com/hannibal002/SkyHanni/blob/beta/src/main/java/at/hannibal2/skyhanni/utils/tracker/SkyHanniItemTracker.kt). These are references; this mod has no runtime dependency on SkyHanni.

Licensed under MIT. This is an independent client-side mod and is not affiliated with Hypixel, FabricMC, or Mojang.

The Crystal spawn fix was reproduced against the original 1.8 JAR and verified with 121 additional checks. A focused local Minecraft client check used a real inline player-head component and rendered the graph before confirmation, after confirmation and after the kill. Its spawn count changed from zero to one, its marker appeared at the confirmed active-time position, and the kill summary used the confirmed duration. No live Hypixel server was joined for this check.

The pet fix was reproduced against the previous 1.8 JAR: the exact reported `Legendary Tarantula Pet` label recorded no loot or title. The updated code records one pet and queues one title. Another 254 checks cover explicit/coloured names, personal receipts, all six stand/pickup/claim arrival orders, duplicate suppression, windows, isolation and persistence. An offline flat-world client check drove the label through Tracker and the actual registered Fabric HUD, confirming a gold title and its recorded value at GUI scales 2 and 4, with hidden chat and open chat, and confirming F1 hides it. Five screenshots were inspected. No live Hypixel server was joined for this check.
