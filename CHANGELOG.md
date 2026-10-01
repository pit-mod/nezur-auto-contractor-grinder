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
