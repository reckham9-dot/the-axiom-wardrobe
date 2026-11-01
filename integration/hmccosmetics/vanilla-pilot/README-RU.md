# SECURITY BLOCKER — Vanilla pilot is withdrawn

**2026-11-01. DO NOT INSTALL OR USE THE EARLIER THE AXIOM VANILLA PILOT ZIP.**

Live user testing showed that visually equipped ELYTRA could be extracted from equipment and used to fly. The same exposure applies to test helmets: in CREATIVE a client may materialize virtual packet-equipment items into ordinary inventory items. Neither packet-based appearance nor settings to remove loose cosmetics is a guarantee against this.

## Immediate server containment

1. Disable the pilot in every active world; stop awarding any pilot cosmetic.
2. As the affected player: `/cosmetics unapply HELMET` and `/cosmetics unapply CHESTPLATE`. These commands **do not reclaim items already extracted**.
3. Stop Paper and remove these *two test files only* if installed:
   - `plugins/HMCCosmetics/cosmetics/axiom_vanilla_pilot.yml`
   - `plugins/HMCCosmetics/menus/axiom_vanilla_pilot.yml`
   Preserve copies outside `plugins/` before deleting.
4. Restart Paper; inspect the impacted test account inventory and ender chest, and CreativeManager saved inventory if applicable, for illicitly spawned test armor/elytra. Remove only identified duplicate items; do not wipe a player's legitimate equipment.
5. Restrict cosmetic armor slots in Creative and Spectator. Under `cosmetic-settings:`, set `disabled-gamemode.enabled: true` with `gamemodes: [CREATIVE, SPECTATOR]`; also consider `destroy-loose-cosmetics: true` as **defense in depth only**, not a complete fix. These settings cannot remove previously minted items.
6. Do not grant the pilot permission nodes to ordinary players. Disable or remove pilot commands from menus.
7. Check that flying elytra / test armor cannot be created or transferred in a clean test account after removal.

## Design gate for upcoming The Axiom cosmetics

- No test using `ELYTRA`, functional armor, tridents or other gameplay-capable vanilla items as visual token.
- BACKPACK cosmetic engine has a separate display-entity mechanism but still needs client testing and resource models with valid licensing.
- Guard any Creative inventory materialization independently before supporting cosmetic item-based head slots.
- Use an empty/stub menu for category navigation until the renderer is tested, not wearable equipment that can drop.

The previous `axiom_vanilla_pilot.yml` and its menu **have been deleted from this GitHub branch**. Old downloaded ZIP files remain unsafe and must not be reused.

Source: upstream HMCCosmetics remapped commit 92d9193f05f08d083c06be1f2597fc4016940b80; `CosmeticArmorType` sends virtual equipment packets, `PaperPlayerGameListener` skips creative inventory sync, and `destroy-loose-cosmetics` only processes some inventory click cases. This observed issue is NOT proof of a flaw in AxiomBridge or server economy itself.
