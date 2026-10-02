# Troubleshooting

## Waiting outside the Sanctuary

Check that you are on Hypixel, in SkyBlock, and in Arachne's Sanctuary. Detection reads sidebar and tab location data. A broader **Spider's Den** tab location should not override a Sanctuary sidebar location.

Run `/arachne debug` to show the detected state and write `config/arachneledger/detection-debug.txt`. If another mod hides location data, `/arachne track manual` permits tracking anywhere in confirmed Hypixel SkyBlock. Use it while farming, then restore `/arachne track auto`. Manual mode still needs boss signals to open reward windows.

## Missing drops or kills

Check that tracking is not paused and the mod observed the boss death. Floating armor stand rewards and pet claims are scanned for **45 seconds after death**. The tracked item must be supported; the pet label needs a supported rarity or an explicit name such as **Legendary Tarantula Pet**.

Check **Fights** for the outcome. A missing kill may be correct if the damage summary is absent, late, or below your minimum. Known-spawn counts and qualifying kills are different metrics.

Use **Edit drops** for a specific fight or **Adjust** for a manual correction. When reporting a detection bug, include the exact server message or floating name, its quantity and rarity color, and whether it appeared in Drops or Fights.

## Missing rare-drop titles

Enable `/arachne rng on` and try `/arachne rng test legendary`. Arachne's Fang and Epic/Legendary Tarantula drops are supported. Unpriced items show a title without a value. Menus and F1 hide titles and pause their lifetime; returning to normal gameplay should show a queued title. Leaving the tracking context clears old notices.

## Unexpected profit

Inspect **Settings → Prices & salvage**, the selected Bazaar mode, and unpriced items. Check whether armor and Arack use NPC or salvage valuation. Changing a price does not change past entries until **Reprice session** is used.

The mod estimates values before Bazaar tax. Spawned rewards that you did not collect and other marked purse gains can affect the estimate. Review the fight breakdown and correct missed or unwanted entries.

## Save errors

Saves live in the Minecraft instance's `config/arachneledger` folder. An error blocks further mutations to avoid overwriting data after a failed save. Close Minecraft, back up that folder, and check `logs/latest.log` for the persistence error. Restore write access or available disk space before restarting.

Do not delete your ledger as a first troubleshooting step. Include the mod version and error text in a [GitHub issue](https://github.com/olliehope/arachne-ledger/issues). Review diagnostics before sharing them because visible player names may be included.
