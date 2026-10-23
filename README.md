AutoGrinder now has a compact dashboard with session totals, Fresh drops, Night Quest rewards, and contextual event panels. [Dashboard settings and counter definitions](docs/grinder-dashboard-20261020.md).

## Update — 1.5.4

The free bots now include the current Classic scoreboard/menu handling, Fresh pickup path cleanup,
prestige access/confirmation fixes, live Kill Streak contract progress, terminal completion/perk
restoration, TDM assist targeting/team protection and care-package lobby-swap hold. AutoContractor
now uses a compact dynamic dashboard with a completed-contract counter, Compact/Detailed modes,
scaling and a matching HUD-editor preview.

The free Quick Setup, independent combat settings, Vile-only fallback, sneak controls and expanded
gold pickup/recovery behavior are preserved. No authentication, licensing or unrelated paid modules
were added. [Full paid/free comparison and port scope](docs/paid-free-comparison-20261023.md).

# Want the premium Nezur Client?
## [JOIN OUR DISCORD — PREMIUM NEZUR](https://discord.gg/bxBtnG3Fwx)

**Looking for the premium version of the mod? Check our Discord:** **https://discord.gg/bxBtnG3Fwx**

---

# Nezur AutoContractor + AutoGrinder — Free


A standalone **Minecraft Forge 1.8.9** mod with AutoContractor and AutoGrinder.

## Combat setup included in 1.5.4

Both bots have **Use KillAura**, enabled by default. KillAura is an internal combat driver;
it is not a separate module row. Its targeting, CPS, range, aim and team/bot controls appear
when enabled. Turning it off exposes the legacy combat controls. Contractor temporarily
uses its own combat choice and settings without overwriting Grinder's saved choices.
Fake Combo and cosmetic combat features are not included.

For a quick start, choose **AutoGrinder → Quick Setup → Conservative, Balanced or Aggressive**.
This applies the matching combat profile, enables Auto Weapon, Idle Retreat and AutoHeal,
and turns Only Event Mode off. It leaves your perk/shop selections and contract priorities alone.
Enable **Show Advanced Settings** for event details, timers, diagnostics and legacy TTK tuning.
Settings remain saved while hidden. Quick Setup applies when explicitly selected; loading a
saved configuration does not reapply it or enable another module.

| Profile | CPS | Aim speed | Target scan | FOV |
|---|---|---|---|---|
| Conservative | 6–8 | 8 | 3.5 blocks | 180° |
| Balanced | 8–12 | 12 | 4.5 blocks | 360° |
| Aggressive | 12–14 | 20 | 5 blocks | 360° |

Actual attack range remains at most **3 blocks** in every profile; target scan range also
serves navigation and never grants an attack at that distance. These names describe behavior,
not measured ban probabilities. Changing individual combat settings marks the combat profile Custom.

Enable **Oof on Overdrive** to detect `Status: Overdrive` from the connected Pit sidebar,
including the Pit Classic layout. Chat detection remains available. GUI/healing/transaction
guards and command backoff still apply. Repeated sidebar updates do not repeat `/oof`,
and a lingering pre-death Overdrive line cannot start another death loop.

The update also carries the reviewed inventory confirmation, unknown-heal handling, exact NPC
targeting, manual takeover, correction suspension and bounded opt-in diagnostic fixes.
Diagnostics: **Show Advanced Settings → Debug Logging** on Grinder, or **Action Diagnostics**
on Contractor; output is under `logs/nezur-acg-diagnostics/` in the running game directory.

See [the comparison and validation report](docs/automation-sync-2026-10-17.md).

## Update — 1.5.2

This release includes the latest premium AutoGrinder and AutoContractor improvements:

- Auto Blockhead, Auto Robbery and Auto Raffle settings in AutoGrinder (off by default).
- Improved Spire entry, dragon-egg approach and nearby target selection.
- Daily/weekly quest clicks continue through menus that only confirm in chat.
- Reconnect to the last supported Pit server, with lobby/Limbo recovery and transfer grace.
- Physical mouse movement takes camera priority over automation.

The free gold-contract recovery, sneak-hit cadence and Vile-only fallback are preserved.
Download the latest 1.5.4 JAR from Releases below.

## Startup crash hotfix — 1.5.1

If 1.5 crashes with `IMixinService` missing, **replace the old JAR with 1.5.1**.

## Download and install

1. Download **Nezur-AutoContractor-Grinder-1.5.4.jar** from [Releases](https://github.com/pit-mod/nezur-auto-contractor-grinder/releases/latest).
2. Install Minecraft **Forge 1.8.9**.
3. Put the JAR in the Minecraft instance's `mods` folder. Remove older standalone copies, then restart Minecraft.
4. Press **Right Shift** to open the module menu.

Use this standalone mod instead of loading it alongside the full Nezur Client. Use automation only where it is permitted.

## Included modules

- AutoContractor
- AutoGrinder
- AutoHeal
- AutoReconnect
- Pathfinder settings
- ClickGUI settings
- Focus
- HUD

KOS/Players List and Guilds are not included. Focus works without KOS.

## Build from source

Requires a **JDK 8** installation; set `JAVA_HOME` to its directory.
Python 3 must also be available as `python` for the build verification tasks.

Windows:
```powershell
.\gradlew.bat clean build verifyStandalone
```

Linux/macOS:
```sh
./gradlew clean build verifyStandalone
```

The build uses Gradle 4.4.1 and ForgeGradle 2.1. Output: `build/libs/Nezur-AutoContractor-Grinder-1.5.4.jar`. Mixin is bundled; the original Nezur source checkout is not required.

## Verification

Compilation, retained module initialization, headless GUI/config checks, contract, event, ticket-progress, reconnect-location and camera-priority regressions passed. Gold-collection and sneak-policy checks passed. The 1.5.4 release is checked through the production Forge 1.8.9 startup pipeline with only Nezur in mods; all nine Mixin target classes loaded and the Minecraft input and camera hooks were confirmed applied. The smoke test stops before starting the graphical client and does not prove live gameplay or anti-cheat safety.

The local update additionally checks all nine Mixin target classes, reported rotation/movement
hooks, actual healing and internal combat interactions, profile restoration, the captured
Overdrive sidebar, loadout confirmation and correction/ownership boundaries. Run
`gradlew.bat build verifyStandalone auditClasspath`, then `python tools/run-forge-smoke.py`
on this workstation for the packaged Forge bootstrap check. The latter needs the Forge/vanilla
1.8.9 SDK jars installed in PrismLauncher libraries; it does not launch a graphical game.

## Premium Nezur

For the premium version and community, [join our Discord](https://discord.gg/bxBtnG3Fwx).



1.5.3 correction-stop removal: the three-corrections rule, its checkbox and the fallback affecting AutoHeal have been deleted globally. Every correction still pauses and clears stale actions; interruptions of unconfirmed inventory operations still stop. Server rollback causes remain unknown.

Latest shared automation fixes are also synchronized with `../nezur`: purchase-to-chooser completion, a separate free equip, slot verification and the identical sidebar/chat Overdrive controller. See [the parity follow-up](docs/loadout-parity-2026-10-17.md). The built standalone output is `build/libs/Nezur-AutoContractor-Grinder-1.5.4.jar`.

AutoReconnect now has **Lowest Player Classic Pit**, on by default: it requests the live server list and joins the least populated instance during recovery or at spawn. See [behavior and validation](docs/lowest-player-classic-pit.md).
