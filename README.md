# Want the premium Nezur Client?
## [JOIN OUR DISCORD — PREMIUM NEZUR](https://discord.gg/bxBtnG3Fwx)

**Looking for the premium version of the mod? Check our Discord:** **https://discord.gg/bxBtnG3Fwx**

---

# Nezur AutoContractor + AutoGrinder — Free


# YOU SHOULD NOT GET BANNED BY ANTICHEAT, BUT STAFF WILL BAN YOU, USE AT OWN RISK
A standalone **Minecraft Forge 1.8.9** mod with AutoContractor and AutoGrinder.

## Source update — 1.5.2

The source now includes the latest premium AutoGrinder and AutoContractor improvements:

- Auto Blockhead, Auto Robbery and Auto Raffle settings in AutoGrinder (off by default).
- Improved Spire entry, dragon-egg approach and nearby target selection.
- Daily/weekly quest clicks continue through menus that only confirm in chat.
- Reconnect to the last supported Pit server, with lobby/Limbo recovery and transfer grace.
- Physical mouse movement takes camera priority over automation.

The free gold-contract recovery, sneak-hit cadence and Vile-only fallback are preserved.
Build from source below to use 1.5.2; the download instructions refer to the existing 1.5.1 release.

## Startup crash hotfix — 1.5.1

If 1.5 crashes with `IMixinService` missing, **replace the old JAR with 1.5.1**.

## Download and install

1. Download **Nezur-AutoContractor-Grinder-1.5.1.jar** from [Releases](https://github.com/pit-mod/nezur-auto-contractor-grinder/releases/latest).
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

Windows:
```powershell
.\gradlew.bat clean build verifyStandalone
```

Linux/macOS:
```sh
./gradlew clean build verifyStandalone
```

The build uses Gradle 4.4.1 and ForgeGradle 2.1. Output: `build/libs/Nezur-AutoContractor-Grinder-1.5.2.jar`. Mixin is bundled; the original Nezur source checkout is not required.

## Verification

Compilation, retained module initialization, headless GUI/config checks, contract, event, ticket-progress, reconnect-location and camera-priority regressions passed. Gold-collection and sneak-policy checks passed. The 1.5.2 build was checked through the production Forge 1.8.9 startup pipeline with only Nezur in mods; all six Mixin target classes loaded and the Minecraft input and camera hooks were confirmed applied. The smoke test stops before starting the graphical client and does not prove live gameplay or anti-cheat safety.

## Premium Nezur

For the premium version and community, [join our Discord](https://discord.gg/bxBtnG3Fwx).
