# Free automation update — 1.5.5

This update integrates the current AutoContractor and normal AutoGrinder changes
into the standalone Forge 1.8.9 edition. It retains the eight-module registry,
independent combat settings, Quick Setup, configurable sneak controls, Vile-only
fallback and the free edition's expanded gold pickup/recovery behavior.

## Included changes

- Current map markers and ordered region definitions, including spawn, sewer,
  faction areas and loaded mid landing positions. Grinder and Contractor use the
  same centers and spawn checks and wait for map detection before navigation.
- TDM assist confirmation from each inbound named target/heart action bar. A Bonk
  hurt animation alone does not confirm an assist; pending attacks remain bounded
  and opponents can be retried when no valid hit confirmation arrives.
- Contractor NPC approach with an inert/empty hand; daily contracts remaining;
  configurable behavior for unsupported objectives; context-specific dashboards
  with Dashboard/Compact/Detailed styles.
- An initial golden apple is required before accepting an apple contract. The
  default cooldown is nine seconds measured from the dispatched `/oof`; eating an
  apple does not restart that cooldown. Existing custom saved values remain valid.
- No-armor preparation preserves a pending armor action if its owned player screen
  is closed, reopening the same screen without resending the click. Exact result
  confirmation and rejection/timeout handling remain enforced. Recovery of a
  repeatedly interrupted action is bounded.
- After a no-armor completion, pending fresh-pants restoration survives death and
  respawn and takes priority over the next contract. Any fresh mystic color is
  eligible; missing pants skip the step and already equipped leggings are preserved.
- Contractor-owned automation suppresses the normal grinder's perk/killstreak
  maintenance while retaining explicit temporary contract loadout preparation and
  restoration. Paid purchase confirmation handles concurrent gold earnings using
  verified result and fresh wallet evidence.
- Raffle routes detect stalled progress and resume navigation; nearby event and
  spawn transitions use the current map geometry. HUDs honor measured overlap and
  saved vertical order, including a one-pixel overlap.

The standalone still uses its own internal combat driver and integrated egg driver.
This update does not add the full client's authentication, premium module registry,
separate RoniAutoGrinder, Myau bridge, external route backend, GUI Move or cosmetic
combat modules. No new analytics or external telemetry is introduced.

## Verification

The build's normal checks include real module initialization/settings, combat
ownership, loadout purchase/equip confirmation, reconnect, inventory action and
manual takeover boundaries, gold/sneak behavior and map/navigation fixtures. Added
coverage includes named TDM feedback/Bonk, raffle progress, context dashboards,
post-completion respawn, fresh pants, closed armor screens and HUD stacking.

The standalone source audit verifies no auth source/login UI and no KOS/Guild UI.
The release is built with Java 8 and the existing bundled Mixin loader. Automated
checks do not constitute a live multiplayer or anticheat acceptance test.
