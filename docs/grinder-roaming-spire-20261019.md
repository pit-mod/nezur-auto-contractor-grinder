# Grinder roaming and Spire combat — 2026-10-19

Ordinary grinder combat accepts positions within 20 horizontal blocks of the canonical Pit boundary. Circular maps use an expanded radius; rectangular maps use distance to the rectangle with rounded corners. Spawn, Overspawn and Sewer retain their zone identity and are excluded. Rectangle map height limits stay intact. A player within this margin is handled by combat rather than the other-zone return/oof branch, and an already-active idle return is cancelled there. The core still supports the configured idle retreat; positions beyond the margin retain ordinary recovery. AutoContractor uses the same grinder, including the wider sneak-kill region; server-enforced fist-in-mid requirements remain strict.

Rage Pit disables automatic return-to-mid routes. Outside the core, combat uses direct chasing instead of obstacle/Roni pathfinding back toward the Pit. Existing return routes are dropped, including transitions into Rage; event completion restores ordinary region behavior.

Combat jump input now accepts active Spire fighting as well as the normal combat area. The existing on-ground, cooldown, visibility, no-menu, no-liquid and no-sneak checks still apply. Canonical AutoWalker, the Forge compile overlay, and the standalone walker all receive the change; overlay hashes are refreshed deliberately.

Spire only accepts enemies within 3.5 blocks vertically of the player's feet, which allows normal jumps/knockback and excludes adjacent tower floors. Selection prioritizes enemies in attack reach, then shorter distance and lower health, with a modest current-target bias to avoid jitter and stable ID ties. The selector is used for both combat and navigation targets, and is re-evaluated by the native combat driver. Normal grinder/custom Roni selection remains separate. Both standalone combat adapters support the same Spire policy.

GrinderMovementTest covers all five map margins, height/diagonal boundaries, Rage return suppression, normal idle return, jumping/floor tolerance and Spire targeting changes. Runtime multiplayer movement was not exercised by the automated checks.
