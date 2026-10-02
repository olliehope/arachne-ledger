# Roadmap

This is a prioritized list of proposals. These features are **not implemented** unless the current README and changelog say otherwise; no release dates are promised. The existing mod already has fight history/corrections, automatic Bazaar pricing, and rare-drop titles. The work below extends those features rather than claiming them as future first-time additions.

## First: validate real farming sessions

The local client and deterministic fixtures verify rendering, interaction, accounting, and known message formats. They do not replace a live Hypixel session. The 1.5.0 validation did not join Hypixel, and its live Bazaar request failed.

| Priority | Proposed work | Evidence needed |
| --- | --- | --- |
| 1 | Live Sanctuary, spawn/death, and damage validation | Compare captured sidebar, server messages, active time, skip reasons, and fight records in an actual session. Confirm the 10,000 boundary and 60-second idle rule. |
| 1 | Reward ownership and duplicate correctness | Exercise stand-first/pickup-first order, delayed/partial quantities, remaining old labels, fast consecutive deaths, warps, and your own versus another player's placements. Identify limits of personal reward ownership before broadening detection. |
| 1 | Live Bazaar and fallback acceptance | Confirm a successful refresh from the public endpoint, chosen valuation fields, stale display, manual/zero overrides, disabling auto, and retaining saved prices on failure. |

Server-format fixes should come with redacted examples and a deterministic regression. Do not loosen a parser solely to make a single screenshot match if that can accept other players' chat or mob labels.

## Then: make results easier to interpret

- **Configurable rare titles and sound:** Per-item enablement, duration, scale/position, rarity filters, and an optional local sound. Keep server titles intact, show recorded value clearly, and deduplicate before notification.
- **Drop statistics and pace without RNG:** Per-item counts, rates, observed frequency, and a separate projection excluding explicitly classified rare drops. Keep total profit and the existing observed projection available; explain sample size and which items the alternate pace excludes.
- **Inventory confirmation:** Distinguish a spawned reward label from a confirmed receipt, reconcile partial/stacked pickups, and make leaving a reward behind visible. Research available client signals first; inventory deltas alone can include unrelated items and must not silently double revenue.
- **Valuation receipts:** Record valuation basis, price timestamp, and any tax assumption alongside a receipt; optionally let a user record actual proceeds. Keep estimated loot value and realized sale income distinguishable to avoid counting both.

## Then: strengthen editing and long history

- **Journal undo/redo with an audit trail:** Replace destructive latest-entry removal with reversible corrections that show what changed, when, and why. Preserve session boundaries, stable identities, historical prices, and migration from existing entries.
- **Long-history performance:** Measure large ledgers before introducing indexes, revision-based caches, pagination, or graph downsampling. Keep exact accounting/export data even if the view is summarized. The newest-50 display limit is not a storage-retention policy.
- **Clearer detection reports:** A small opt-in diagnostic report for the last relevant events and reward pairing decisions could make missed/duplicate reports reproducible without sharing an entire account ledger.

## Research before committing scope

Auction House valuations for pets, automatic SkyBlock profile identification, guaranteed personal hologram ownership, and realized-sale detection need reliable signals and clear privacy/performance tradeoffs. They remain research topics; the mod currently uses manual values where Bazaar data is unavailable and manual profile switching.

Public distribution work is tracked in [RELEASING.md](RELEASING.md). This roadmap does not schedule tasks, create public releases, or promise automatic publishing. New contributors can start with the extension recipes in [ARCHITECTURE.md](ARCHITECTURE.md).
