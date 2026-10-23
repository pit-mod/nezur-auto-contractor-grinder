# Standalone automation sync — 17 October 2026

The current `nezur/` automation implementation was compared with this independent standalone
project. The automation sync is scoped to `auto contractor and grinder/`. Its namespace, mod identity,
eight-module registry, free configuration paths, bundled Mixin/Forge loader, and lack of a
licensing subsystem are retained. No installed game or remote release was modified.

The comparison covered all **175 original Java files**: 110 matched the reference after namespace
normalization, 63 differed, and two were standalone-only. `verification/sync-20261017/comparison-before.tsv`
records every file and both hashes. Historical generated/backup trees were not used as primary source.
The result contains 195 Java files. This is a scoped integration, not a replacement of the standalone
with the full premium client.

## Compared behavior and resulting changes

| Area | Previous standalone / difference | Result |
|---|---|---|
| Grinder combat | Legacy camera CombatAura plus a separate Pit crosshair clicker | Internal `GrinderCombat` adapts current KillAura macro selection and the shared reported-ray/native driver; one selected combat stream. Legacy mode remains available through the same helper. No KillAura module row. |
| Contractor combat | Starts Grinder and temporarily changes its settings | Each bot has independent `CombatOptions`. Contractor lends its options/aim preference by owner and session. Release, disable and session changes return to Grinder's own saved settings. |
| Targeting | Older sticky/TTK picker only | KillAura mode adds Single/Switch, Health/Distance/Hurt Time/FOV/Smart sorting, bounded target/attack range, FOV, team/bot filters and fresh body visibility. Far targets are navigation destinations, not attack targets. Friends, truce, Focus, contract rules and player identity remain enforced. |
| Driver switching | No mode choice | `Use KillAura` defaults true in both bots. Switching stops both previous drivers, cancels their intents and clears targeting state before the chosen driver starts. Disabled mode retains camera combat, legacy CPS/reach/aim/TTK settings and Classic NPC policy. |
| Physics/rotation/input | Camera-only native input hooks | Current reported orientation, movement correction, sprint/jump orientation, native picking and pre-physics hooks are integrated through three additional Mixins. Native reach, current slot/item, ownership and target are rechecked before invocation. |
| Rate/scheduling | Older native budget and scheduler | Shared rolling/short-window budget, physical-input handoff, actual outgoing attack observation and native cadence; missed timing does not become a burst backlog. |
| NPC actions | Crosshair attack could differ from the previously selected NPC | Exact intended NPC/world/mode/reach is validated at invocation. Classic keeps its exact native entity interaction. |
| Contractor inventory/shops | Predicted moves, timed menu advancement and recovery retries | Owned GUI/item resources, exact native transaction IDs, expected menu/server evidence, acknowledged sword/apple/armor movement. Unknown paid/inventory results stop for inspection instead of blind repetition. |
| Contractor apples | Fixed delay could count as successful consumption; held use could continue | First decrement ends use; actual server inventory/objective evidence is needed to continue. Slot ownership remains until the result decision. |
| Grinder loadouts | Earlier speculative perk/killstreak cache/attempt logic | Current `LoadoutPlanner`, gold observer and confirmation contracts, live chooser revalidation, price/availability guards, temporary perk-lease suspension and startup revalidation are integrated. |
| Prestige/idle recovery | Earlier wallet/prestige and idle routing | Earned-gold observer, Overdrive controller, returning-to-mid state, nearby combat takeover and current event/recovery state handling are integrated. |
| Mystic stow | Chained local predictions and generic pause releasing slot ownership | Own screen/slot retained through exact acknowledged transfers; unknown/rejected or replaced-screen pending work stops. |
| AutoHeal | Unconfirmed use could retry and start again against the same unchanged slot | Unknown outcomes remain uncertain; no blind replay. Local prediction alone cannot clear uncertainty. Actual result, meaningful item/context change or explicit restart is required. |
| Manual input | Same-slot reselection could retain restoration rights | Native hotbar press/scroll invalidates temporary restoration and queued work, even for the same slot. Physical keys survive cleanup; manual movement releases synthetic walking/jumping. |
| Corrections/lifecycle | Incomplete shared interruption boundary | After vanilla applies/acknowledges S08: invalidate session/pending work, release controls and reset rotation/path assumptions. Resume requires at least 250 ms and three player ticks. The correction-count stop has been removed globally, including AutoHeal while Grinder is off. Only interrupted unconfirmed inventory work uses the correction-related stop; per-packet cleanup and settling bounds remain. |
| Diagnostics | Earlier overlay could enable persistent logging without the opt-in setting | Canonical shared recorder, independent opt-in owners, ordered requests and a 128-entry correction prelude. Bounded queue/fields and three rotating files; routine transaction preludes are limited to once per five seconds while every event and immediate correction snapshot is retained. No external telemetry. |
| Free-specific gold collection | 64-block default/128 maximum, bounded sidestep/jump recovery, blocked-drop exclusion, no thin-population swaps | Preserved, including the actual `tickGold` and population exemptions. Gold disappearance remains navigation evidence, not objective completion. |
| Free-specific selection/sneak | Vile-only fallback, configurable sneak radius/delay | Preserved. Ordinary contracts retain population behavior. Legacy TTK values keep their saved setting names. |
| Unrelated premium systems | Missing licensing, KOS/Guilds, separate combat/other automation modules | Not imported. Packet-delay modules, Telebow, swaps, care/sewer bots and JumpReset cannot run in this registry, so their unrelated module adapters are not added. Fake Combo/cosmetic combat features are excluded as requested. |

## Configuration

Both bots expose `Use KillAura` (default on). Useful KillAura settings appear only in that mode.
Turning it off exposes the legacy controls. No ninth module is registered. Contractor's delegated
options are session-scoped; changing its mode never overwrites Grinder's saved choice.

AutoGrinder's basic view shows quick setup, the combat controls, core movement/population options,
perks, Overdrive and HUD. `Show Advanced Settings` reveals event options, timers, diagnostics and
legacy TTK tuning. Only rendering is filtered: hidden settings remain in serialization/config loading.

`Quick Setup` applies when the user chooses a profile. It selects the matching combat preset,
turns on KillAura mode, Auto Weapon, Idle Retreat and AutoHeal, and turns Only Event Mode off.
Existing perk/shop choices, event choices and contract priorities are not reset. With no world,
AutoHeal's requested state is saved and its normal loaded-state lifecycle activates it in game.

| Profile | CPS min–max | Rotation speed | Target range | FOV | Attack range |
|---|---|---|---|---|---|
| Conservative | 6–8 | 8 | 3.5 | 180° | 3 |
| Balanced | 8–12 | 12 | 4.5 | 360° | 3 |
| Aggressive | 12–14 | 20 | 5 | 360° | 3 |

Profiles are scheduling/selection choices, not ban-risk estimates. Target range includes walking
destinations; it does not extend native melee reach. Manual edits mark the combat preset Custom.
Passive config/preset restoration is explicitly marked so it restores exact saved fields without
executing Quick Setup, enabling another module, or silently reapplying a profile over custom values.

## Overdrive screenshot reproduction

The screenshot is Pit Classic with `Status: Overdrive`, `Streak: 53` and `Strength: V (5s)`.
The earlier command controller depended on an own-player activation chat grammar; it could miss
that message or be enabled after it. The new path reads the active, connected Pit sidebar. Formatting
is stripped and the exact Status value is recognized. Streak count and Strength alone do not trigger it.

Enable `Oof on Overdrive` to use this feature; the option remains off by default. Chat remains a
fallback. Both sources share one request, actual-send/backoff handling, GUI/healing/lease guards,
bounded command lifetime and response wait. Repeated status lines cannot requeue it. A separate
sidebar latch survives ordinary death/transfer cleanup until an observed non-Overdrive status,
preventing a lingering pre-death line from starting another death. Explicit Grinder restart resets
that latch. The command is never treated as proof that death/respawn actually happened.

## Follow-up: correction-count stop removed

A later live capture showed same-Pit rollbacks while alive, distinct from the earlier respawns.
The user's final request removes the three-correction rule completely. The counter, checkbox,
warning, module-dependent fallback and now-unneeded death/respawn exemption were deleted.
Corrections cannot turn modules off merely because they repeat. This applies with Grinder,
Contractor, AutoHeal alone, and the other affected modules in the shared native build.
Each correction still releases stale actions and waits at least 250 ms and three ticks.
Interruptions of unconfirmed inventory operations still stop for inspection. This is a change
in client response and does not identify or eliminate the server's rollback cause.

Routine transaction snapshots remain limited to once per five seconds while every event and
immediate correction capture is retained. New build evidence is recorded in the shared
`nezur/verification/correction-stop-removal-20261017/` follow-up.

## Validation

Final commands:

```powershell
.\gradlew.bat build verifyStandalone auditClasspath
python tools/run-forge-smoke.py
```

**Build and verification passed.** Detailed command output and final artifact hashes are under
`verification/sync-20261017/`; `validation.md` records the final digest and exact results.

| Check | Result / scope |
|---|---|
| Actual module settings/ownership/UI/profile/Overdrive probe | 1,218 checks: eight rows, default modes, conditional controls, independent Contractor handoff, session invalidation, bounds, passive restoration, captured sidebar and duplicate/timeout behavior |
| Actual healing/native slot/evidence integration | 450 checks, excluding the premium-only authorization-loss case because this standalone has no licensing subsystem |
| Actual internal combat helper | 21 controlled-API checks: one stream, death/unload/visibility, friends/truce/Focus/team/bot, distant navigation, healing, GUI, exclusive Contractor, inventory and mode/session/disable boundaries |
| Production interruption/result/diagnostic policies | Repeated-correction settling regression plus ownership, exact transaction correlation, manual override and bounded diagnostic/flood checks; see the latest follow-up for totals |
| Loadout planner/startup/gold/confirmation | 96 / 132 / 144 / 83 assertions |
| Overdrive chat/lifecycle/backoff | 58 checks plus the screenshot/sidebar probe above |
| Existing standalone checks | Real constructors, headless GUI/config roundtrip, Vile-only fallback, contract/snapshot/cooldown grammar, event/raffle/ticket/recovery and loader isolation pass |
| Free gold/sneak regressions | Pass against retained production method bodies/policies |
| Real packaged Forge bootstrap | All nine Mixin target classes load; camera/input and reported-rotation/movement hooks applied. Stops before graphical game startup. |

The combat fixture controls Minecraft APIs and observes a driver sink; the actual native input and
slot/evidence classes are tested separately, and the final packaged Mixins are loaded by real Forge.
This is not a live multiplayer or visual gameplay test. Server menu grammar, actual latency/collisions,
rollback causation and anti-cheat acceptance remain unverified. An S08 alone does not identify a cause.

Initial compile/integration failures were resolved rather than excluded: missing helper dependencies,
renamed combat-driver references, UTF-8 adapter handling, the new RETURNING_TO_MID test expectation,
and the headless Forge event-bus activation limitation. The final suites run with the normal build.
The older loader-isolation fixture prints known logger/FML initialization diagnostics while its asserted
isolation checks pass; the separate production Forge bootstrap also passes.

## Review artifacts and preservation

`baseline/` and `initial-sha256.json` preserve the pre-edit standalone source/tools/build/docs.
`comparison-before.tsv`, the selected method diffs, `changed-files.json` and `changes.patch` make
the update reviewable against those bytes instead of the workspace's already-dirty Git HEAD.
The initial comparison left the `nezur/` reference unchanged. A subsequent user-reported stop was reproduced as a death/respawn counting problem and fixed in both the shared source and this adaptation; its separate baseline and validation are in `nezur/verification/respawn-correction-20261017/` and `nezur/docs/respawn-correction-2026-10-17.md`. Other sibling projects were not edited. The original
eight-module registry, free config directory, authentication-free behavior and removed KOS/Guild UI
are checked again. Generated outputs remain under `build/` and are not primary source.

## Final loadout/Overdrive parity follow-up

The later purchase-completion fix and shared sidebar controller are now integrated in both editions. The five loadout suites total 576 checks and Overdrive has 88 checks. See [the follow-up](loadout-parity-2026-10-17.md) and `verification/loadout-parity-20261017/shared-parity.json` for the 20 matching shared files/sections.
