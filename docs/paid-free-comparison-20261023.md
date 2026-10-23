# Paid Nezur versus free AutoContractor + AutoGrinder

Compared the current local `nezur/` and `auto contractor and grinder/` source projects on 23 October 2026. This describes local code and artifacts, not a claim about which public release has been uploaded. Namespace-only differences were ignored. No paid source files were changed by this port.

## Shared bot features

Both include AutoGrinder combat/navigation, idle retreat, thin-lobby recovery, AutoHeal, AutoReconnect with lowest-player Classic selection, Auto Prestige, Auto Perk, Auto Killstreak, Night Quests, mystic pickup/stow, integrated dragon-egg support and `/oof` on Overdrive. Both include Spire, Squads, Blockhead, Robbery and Raffle automation, the shared combat-area margin and Rage Pit entry restrictions. Free already had the combat presets/internal KillAura driver and compact AutoGrinder dashboard before this update.

Both contractors offer the same supported objectives and priorities: player kills, chain kills, kill streaks, bounties, ground gold, sneak kills, no-armor/no-perk kills, Kung Fu/fist kills, diamond-sword kills and golden apples. Both support daily/weekly quest starts, equipment purchasing and temporary perk preparation/restoration. This does not imply unsupported fishing/bow/lava objectives become executable.

## Remaining intentional product/integration differences

| Area | Paid Nezur | Free standalone |
|---|---|---|
| Package | Full client and its broader module registry | Eight modules: AutoContractor, AutoGrinder, AutoHeal, AutoReconnect, Pathfinder, ClickGUI, Focus and HUD |
| Licensing | Paid authorization/device-bound licensing | No auth source, login UI or entitlement gate |
| Platforms present in these projects | Forge 1.8.9 and native Ornithe/Fabric build | Forge 1.8.9 build |
| Combat configuration | Uses the client KillAura module and its live settings | Internal driver; independent settings for each bot, Use KillAura toggle and legacy mode |
| Setup UI | Full-client settings | Conservative/Balanced/Aggressive Quick Setup and Show Advanced Settings |
| Extra combat integrations | Optional Myau bridge, full KillAura cosmetics/Fake Combo and full-client hooks | Not imported |
| Routing variants | Nezur/Roni route selection and separate RoniAutoGrinder | Standalone route service and normal AutoGrinder |
| Lists/other automation | Full Players/KOS/Guild-related UI and other registered bots/helpers | Focus retained; no KOS/Guild UI or unrelated premium bots |
| Dragon eggs | Integrated delegation to the full client's Auto Dragon Egg module; separate module/ESP available | Existing guarded, visible, single-right-click Grinder driver; no separate egg module or ESP. Egg scanning and stationary-egg rejection are shared. Support now defaults off in both. |
| Gold scan | 32-block default, 64 maximum | 64-block default, 128 maximum |
| Gold recovery | Ordinary gold approach | Bounded sidestep/jump recovery and temporary blocked-drop exclusion |
| Gold/population handling | Ordinary low-population behavior | Gold contracts retain population-swap exemptions |
| Sneak objective | 100 ms policy delay; radius follows KillAura attack range | Configurable 0–500 ms delay and 1–3.5-block safety radius |
| Unsupported fallback offers | May choose any clickable fallback offer, including one with no Vile | Fallback still requires a Vile reward |
| Identity/configuration | Nezur namespace/configuration | Separate standalone namespace/configuration and diagnostics directory |

## Newer shared automation changes ported to free

Before this port the free checkout lacked these recent changes. They are now implemented in the free source:

- Pit Classic contract scoreboard recognition, quest/menu content confirmation and pending-choice handling.
- Live player-streak progress for Kill Streak contracts; accepted streak identity survives a generic Kills 0/15 line. Chain Kills remains separate. Local streak alone does not manufacture server completion.
- One terminal completion cleanup for server chat/objective completion, duplicate suppression, stale-sidebar/menu protection, cancellation of owned work and restoration before stopping the engine.
- Compact/detailed AutoContractor dashboard, scaling, editor preview, selected-choice/NPC/equipment/event context and completed-contract count. No standalone current-player-streak tile.
- Classic mystic-drop message handling/queuing, correct Fresh identity matching, no ordinary-boots fallback, bounded missing-target handling and immediate owned-path cleanup after authoritative inventory receipt.
- Prestige/NPC lease waiting instead of repeated false aborts, plus the exact Hypixel prestige confirmation evidence handling.
- Classic quest/reward/kill/assist/gold parsing and improved death counting in the Grinder HUD.
- TDM automatic teammate protection and observed-hit assist targeting: switch away after an attributed damage response, preserve tags across visibility loss and reopen targets on death/new-life evidence.
- Care-package hold for intentional lobby swaps, requester-lifetime cancellation and bounded world-scoped map detection.
- Current egg-index improvements and block-revision-aware visible-point combat aiming.
- Opt-in local game/menu/inventory diagnostics in the standalone diagnostics directory.

The standalone's original combat options, Quick Setup, Vile-only fallback, sneak controls and gold recovery were deliberately preserved. Licensing, Myau/Roni integration, cosmetics and unrelated paid modules were not imported.

## Verification/evidence

Original touched files and SHA-256 records are under `verification/automation-sync-20261023/before/` and `port-manifest.json`. Targeted tests use production methods/classes for completion, streak reading, HUD rendering, captured Classic menus, mystic cleanup, NPC access and TDM life/attribution behavior. Existing standalone settings/ownership, inventory, gold, sneak, event and map tests remain required. Final build/startup results are recorded beside the manifest.

Final validation passed: `gradlew.bat build verifyStandalone auditClasspath --offline`, the production Forge-only bootstrap (nine actual target classes plus input/camera/reported-rotation hooks), and the targeted port checks. Existing standalone tests also passed, including settings/ownership/profiles, inventory, gold collection, sneak behavior, map caching and Vile-only fallback. This is compilation/regression/startup validation; no multiplayer client was launched. The final JAR is `build/libs/Nezur-AutoContractor-Grinder-1.5.3.jar`; its SHA-256 and all results are in `verification/automation-sync-20261023/validation.json`.
