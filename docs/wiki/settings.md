# Settings

Open **Settings** from the dashboard, run `/arachne settings`, or use Mod Menu's **Arachne Ledger → Configure** button. The hub contains **HUD**, **Tracking**, **Prices & salvage**, and **Graph**. Display and price choices do not erase your saved history.

## HUD

Open **Settings → HUD** for four tabs:

- **Rows:** independently show or hide the title, total profit, profit/hour, projected/hour, profit without RNG, without RNG/hour, tracking status, loot items and their values, Scavenger coins, Crystal costs, Calling costs, kills, active time, scope, and unpriced warning. The two rows without RNG exclude Tarantula Pets and Arachne Fangs while keeping Scavenger coins and every recorded cost.
- **Items:** choose 0–8 loot rows, sort by recorded value, quantity, or name, and show or hide individual items. Hidden items still contribute to totals and profit.
- **Display:** choose Text or Graph, a text layout, visibility, and background. **Move / resize** opens the draggable preview; scroll over it to resize. `/arachne hud edit` also opens the editor.
- **Order:** move rows **Up** or **Down** within Rewards or Summary. Loot items move as one block; individual item order still comes from **Items → Sort loot by**. Hidden rows retain their position. **Reset row order** restores the default order without changing visibility or item filters.

**Minimal** is the default, showing the title, total profit, actual and projected hourly rates, and tracking status. **Classic** restores a fuller item-and-stat list. **Split** separates the item list from the summary.

Changing **Text layout** rearranges your existing selections. Minimal shows Summary before Rewards; Classic shows Rewards before Summary; Split keeps Rewards on the left and Summary on the right. Custom order is retained inside each group. **Apply preset** deliberately resets row toggles, item exclusions, sorting, row order, and the loot-row limit to that preset, then selects the text HUD. Position and scale are kept. Graph rows and lines use the separate **Graph** settings.

`/arachne hud on` and `/arachne hud off` control visibility. `/arachne hud always` toggles display throughout SkyBlock, including the waiting status. The HUD stays visible while chatting.

## Prices

Set loot unit values and Calling costs on **Settings → Prices & salvage**. Items without a value still appear in the drop counts, but add no coins. Pet prices are manual.

Crystals use the configured value of **2 Arachne Fragments + 16 Enchanted Spider Eyes + 16 Enchanted String** by default. Choose a fixed cost if you buy them at a different price. `/arachne crystal <coins>` sets that cost; `/arachne recipe` restores recipe valuation.

Automatic Bazaar pricing is off by default. **Use Bazaar items** opts supported materials into automatic pricing; **Manual/Auto** controls an individual item. A manual override wins, including an explicit zero.

| Bazaar mode | Estimate |
| --- | --- |
| Instant sell | Current buyer-bid estimate |
| Sell offer | Current seller-ask estimate; the offer may take time or remain unfilled |

Both estimates are before tax. Prices refresh at most once every five minutes, with saved fallback values if a request fails. The screen shows update age and errors. No API key is needed.

**Saved entries keep their recorded value.** Changing prices affects future entries. **Reprice session** deliberately updates the current session; older sessions keep their values.

## Salvage and purse income

Choose armor and tools/weapons separately on **Salvage**. NPC mode uses the saved sale value. Salvage mode values each base Arachne armor piece or Arack as **5 Spider Essence**, using your essence price. The two values are not added together. Switching back preserves the saved sale-price override.

These settings value drops; they do not sell or salvage items. Upgraded gear bonuses are not inferred from floating labels. **Scavenger coins** controls the eligible marked purse income described in [Tracking and profit](tracking-and-profit.md).

## Notifications and participation

Open **Settings → Tracking** to set minimum damage, kill chat, rare-drop titles, rare-drop values, Scavenger collection, session recap chat, and achievement notifications. Click **Save** to apply the whole form; **Back** discards unsaved changes. Damage must be a whole number from 1 to 1 trillion. Invalid input leaves all draft choices unsaved.

The default minimum damage is **10,000**. `/arachne mindamage <damage>` also changes it for future fight results. `/arachne chat on|off` controls the local kill summary. Hiding the Scavenger HUD row does not disable Scavenger collection.

`/arachne rng on|off` controls rare-drop titles, and `/arachne rng value` toggles their recorded values. `/arachne rng test [epic|legendary|fang]` previews a title without recording loot. Titles last four visible seconds; menus and F1 pause their lifetime.

**Session recap chat** controls the local summary printed when you start a new session. The saved recap is retained when this notification is off. **Achievement chat** controls local messages for newly unlocked milestones; turning it back on does not replay previous unlocks. These choices do not change earnings or achievement progress.

## Sessions and profiles

`/arachne session` and `/arachne total` choose the displayed scope. `/arachne new` begins a new session without deleting lifetime totals. `/arachne pause` stops automatic tracking and its active timer.

Open **Journal → Recaps & records** from the dashboard, or run `/arachne recap`, for **Current**, **Last session**, and **Records**. Current and Records have their own Session/Total scope buttons. Last session is the frozen summary captured before the reset; the latest 20 active session summaries are saved. **Journal → Achievements** or `/arachne achievements` opens lifetime milestones. **Journal → Tracking diagnostics** or `/arachne diagnostics` opens the [diagnostics page](diagnostics.md). The graph tab keeps its Options button; switch to Overview, Drops, or Fights for Journal.

Saves are separate per Minecraft account. If you change SkyBlock profiles on the same account, use `/arachne profile <name>` to select a separate ledger. Profile names use 1–32 letters, digits, `_`, or `-`; the server profile is not selected automatically.
