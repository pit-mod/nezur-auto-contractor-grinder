# 1.5.5 — Contractor recovery and current automation

- Update map/region detection and share loaded spawn/mid landing geometry across both bots.
- Confirm TDM assists from named server hit bars, including opponents protected by Bonk.
- Add safer NPC approach, contracts remaining, unsupported-contract behavior and context-specific HUD designs.
- Require a golden apple before accepting its contract; use a nine-second default cooldown from `/oof` dispatch.
- Recover a closed owned armor inventory without replaying its pending action.
- Preserve fresh-pants restoration after no-armor completion across death/respawn; skip when absent.
- Isolate Contractor loadouts from normal Auto Perk/Auto Killstreak maintenance and improve verified purchase accounting.
- Recover stalled raffle navigation and prevent overlapping automation HUDs.
- Preserve free Quick Setup, independent combat settings, gold recovery, sneak options and Vile-only fallback.
- No authentication, licensing or additional premium modules added.

Minecraft Forge 1.8.9, Java 8. Replace the previous standalone JAR in your mods folder.
[Integration and validation details](docs/automation-sync-20261103.md).

# 1.5.4 — Automation and Pit Classic update

- Improve Pit Classic scoreboard/menu recognition and server-confirmed contract choices.
- Track actual Kill Streak contract progress, including generic Kills sidebar lines and death resets.
- Clear completed contracts and stale actions immediately; restore temporary perk changes.
- Add the compact AutoContractor dashboard, completed-contract counter, Compact/Detailed modes and HUD scaling/editor preview.
- Improve Classic Fresh-drop recognition and immediately cancel pickup paths after collection.
- Improve prestige NPC ownership waiting and exact confirmation handling.
- Add automatic TDM teammate protection and assist-oriented target switching.
- Pause intentional lobby swaps during care packages and cancel requests when their owner stops.
- Improve Classic reward/death statistics, map caching, egg scanning and opt-in diagnostics.
- Include internal KillAura/legacy selection, independent per-bot combat settings, setup presets and the shared loadout/Overdrive improvements from the local 1.5.3 development work.
- Preserve free Vile-only fallback, expanded gold scanning/recovery, configurable sneak controls and all eight standalone modules.
- No login, license requirement or additional premium modules added.

Minecraft Forge 1.8.9, Java 8. Replace the previous standalone JAR in your mods folder.

Validation: standalone initialization, settings/configuration, inventory, loadouts, event/reconnect, gold/sneak, Classic/contract/HUD/TDM regressions and the real Forge/Mixin startup check. No multiplayer gameplay was launched during validation.

# 1.5.2 — Premium automation improvements

- Add opt-in Blockhead painting/powerup routing, Robbery targeting/rank banking and Raffle ticket collection/deposit.
- Improve Spire entry detection, late entry from spawn and stale boss-countdown handling.
- Prefer nearby combat targets before distant TTK candidates; retain contract target bands.
- Improve dragon-egg spawn departure, direct approach, obstruction recovery and fresh-raycast interaction.
- Handle daily/weekly quest starts without requiring a menu transition or repeating clicks during one visit.
- Share Pit location observations across reconnect/grinder/contractor flows, allow transfer grace before treating an empty sidebar as Limbo, and reconnect to the last supported server.
- Apply automated camera output after physical mouse look; manual input temporarily owns the camera.
- Retain the free gold-contract pickup recovery/population exemptions, sneak-click cadence, Vile-only fallback, standalone loader and config isolation.
- Add event/ticket/location/camera regressions and verify the camera Mixin through the production Forge startup smoke test.
- Release JAR: `Nezur-AutoContractor-Grinder-1.5.2.jar` (Forge 1.8.9).

# 1.5.1 — Standalone startup hotfix

- Fixes IMixinService/ClassNotFoundException when Nezur is the only installed mod.
- Makes the bundled Mixin JAR accessible to LaunchWrapper's parent before bootstrap; handles jar-entry code-source URLs.
- Adds an isolated classloader startup regression to verifyStandalone.
- Production Forge startup smoke test passes with only Nezur; all five target classes load and the Minecraft input Mixin is applied.
- No login/license restriction or additional mod dependency introduced.

# 1.5 — Free standalone release

- Includes AutoContractor, AutoGrinder and six supporting modules/settings panels.
- Removes KeyAuth, license/HWID gates and login dialogs.
- Removes KOS/Players List and Guilds; Focus has no KOS dependency.
- Preserves Nezur configs and Nezur- shared codes.
- Bundles Mixin and includes headless/config/contract validation.
