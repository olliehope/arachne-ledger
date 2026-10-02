# Arachne Ledger

Client-side Arachne profit tracker for Hypixel SkyBlock on **Minecraft Java 26.1.2 / Fabric**.

<p align="center">
  <a href="https://modrinth.com/arachne-profit-tracker">
    <img alt="Modrinth" src="https://img.shields.io/badge/Modrinth-Download-1BD96A?style=for-the-badge&logo=modrinth&logoColor=white">
  </a>
  <a href="https://olliehope.github.io/arachne-ledger/">
    <img alt="Wiki" src="https://img.shields.io/badge/Wiki-Documentation-D69E3A?style=for-the-badge">
  </a>
</p>

## Install

1. Install Fabric Loader and Fabric API for Minecraft 26.1.2.
2. Download the latest Arachne Ledger JAR from [Modrinth](https://modrinth.com/arachne-profit-tracker) or [GitHub Releases](https://github.com/olliehope/arachne-ledger/releases).
3. Put it in your Minecraft `mods` folder and launch the game with Java 25.

Press **O** or run `/arachne` to open the dashboard.

## Features

- Tracks Arachne kills, summon costs, drops, purse income, and profit.
- Shows session and lifetime totals in a configurable HUD.
- Supports manual and Bazaar-based item prices.
- Keeps fight history, graphs, rare-drop titles, and CSV exports.

## Development

Use JDK 25. Build and run the checks with:

```powershell
.\gradlew.bat build
```

The installable JAR is written to `build/libs`.

See the [wiki](https://olliehope.github.io/arachne-ledger/) for commands and usage. Changes are listed in [CHANGELOG.md](CHANGELOG.md).
