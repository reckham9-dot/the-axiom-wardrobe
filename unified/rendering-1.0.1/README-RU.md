# Rendering 1.0.1 — mappings for Unified RC1

Previous status `Configured mappings=0/13` directly explained why no cosmetics could be applied: the plugin had no HMCCosmetics IDs configured.

This directory contains 13 **real, namespaced** cosmetic IDs for HMCCosmetics 2.9.3 and exact corresponding Unified mappings. Actual rendering requires a separate **AxiomCosmetics-RESOURCEPACK-1.21.11.zip** from the linked conversation delivery installed in `plugins/ResourcePackManager/mixer/` with the archive root `pack.mcmeta` and `assets/axiomcosmetics/`. Minecraft Java resource format is 75.0.

Source files in this repo are not enough to render without that generated ZIP. Avoid previous nested test packs. Test on Paper in SURVIVAL, after downloading merged pack; do not enable cosmetic HELMET in creative mode. These 13 are visual equipment, no native armor/elytra items. Aura remains the legacy AxiomWardrobe effect.

**Deployment caution:** update `backend.mappings` in the existing `plugins/AxiomWardrobeUnified/config.yml`; `saveDefaultConfig` does not overwrite an already-existing empty config. Add `cosmetics/axiom_wardrobe.yml` to `plugins/HMCCosmetics/cosmetics/`. Full restart and `/rspm reload`. Then `/axiomwardrobe reload`, `/axiomwardrobe status` should report 13/13; a visible model additionally requires client pack loaded and the user to choose a cosmetic. The adapter increments `applied` for API calls; it does not prove client render.

No changes are made to AxiomBridge, MySQL, website or current signed skins. This integration has not yet passed a live client render test.