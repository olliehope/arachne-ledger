# Getting started

Arachne Ledger runs on your Minecraft client. It tracks Arachne farming on Hypixel SkyBlock; no server installation is required.

## Requirements

| Component | Version |
| --- | --- |
| Minecraft Java | 26.1.2 |
| Fabric Loader | 0.19.5 or newer |
| Fabric API | 0.155.3+26.1.2 |
| Java | 25 |

## Install

1. Use the [Fabric installer](https://fabricmc.net/use/installer/) to install a client profile for Minecraft **26.1.2** with Fabric Loader **0.19.5 or newer**.
2. Download [Fabric API](https://modrinth.com/mod/fabric-api) for Minecraft **26.1.2** and put its JAR in that Minecraft instance's `mods` folder.
3. Download Arachne Ledger from [Modrinth](https://modrinth.com/project/arachne-profit-tracker) or [GitHub Releases](https://github.com/olliehope/arachne-ledger/releases). Choose the installable mod JAR; the source ZIP is for development.
4. Put the Arachne Ledger JAR in the same `mods` folder. Remove the old Arachne Ledger JAR when updating.
5. Launch the **Fabric** profile with Java **25**.

[Mod Menu](https://modrinth.com/mod/modmenu) is optional. It adds **Arachne Ledger → Configure** to the mod list.

## Open the dashboard

Join Hypixel SkyBlock and press **O**, or run `/arachne`. Keybindings can be changed in Minecraft **Controls** if another mod uses the same key.

Open **Settings** from the dashboard or run `/arachne settings` to configure the tracker.

## Start tracking

Farm in **Arachne's Sanctuary** in Spider's Den. The tracker records your summon costs, observed rewards, and eligible purse gains. Choose **Session** or **Total** to switch the displayed scope. Starting a new session keeps lifetime history.

Profit starts with NPC sell values and George's pet sell values. Open **Settings → Prices & salvage** to use Bazaar prices, set custom values, or enable **Ironman**. Ironman keeps unsellable reward counts without coin value or missing-price warnings. Save applies your choices to future drops; existing history keeps its recorded values. The default Minimal HUD keeps profit, hourly rates, and tracking status visible; customize it in **Settings → HUD**.

Open **Journal** from the dashboard's Overview, Drops, or Fights tab for achievements, recaps and records, or tracking diagnostics.

For details, read [Settings](settings.md) and [Tracking and profit](tracking-and-profit.md). If something is missed, use [Diagnostics](diagnostics.md) and [Troubleshooting](troubleshooting.md).
