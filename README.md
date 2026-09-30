# Want the premium Nezur Client?
## [JOIN OUR DISCORD — PREMIUM NEZUR](https://discord.gg/bxBtnG3Fwx)

**Looking for the premium version of the mod? Check our Discord:** **https://discord.gg/bxBtnG3Fwx**

---

# Nezur AutoContractor + AutoGrinder — Free

A standalone **Minecraft Forge 1.8.9** mod with AutoContractor and AutoGrinder. **No KeyAuth, license key, paid login or HWID restriction.**

## Download and install

1. Download **Nezur-AutoContractor-Grinder-1.5.jar** from [Releases](https://github.com/pit-mod/nezur-auto-contractor-grinder/releases/latest).
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

## Configs

The Nezur config GUI supports saving/loading presets, sharing/importing **`Nezur-`** codes, module settings and HUD positions. Open **Right Shift → Configs**. Local files are stored in **`config/nezur-acg/`**, separately from the full client. Private API/config files are never part of this repository.

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

The build uses Gradle 4.4.1 and ForgeGradle 2.1. Output: `build/libs/Nezur-AutoContractor-Grinder-1.5.jar`. Mixin is bundled; the original Nezur source checkout is not required.

## Verification

Compilation, retained module initialization, headless GUI/config checks and contract regression tests passed. These checks do not prove live server behavior or anti-cheat safety.

## Premium Nezur

For the premium version and community, [join our Discord](https://discord.gg/bxBtnG3Fwx).
