# Stationary dragon eggs

AutoGrinder and Dragon Egg ESP share a timer for each observed egg position. An egg continuously present at the same position for 25 seconds is excluded from local/indexed target selection, queued interactions, boxes and tracers. Other eggs moving or entering the scan radius do not reset its age. Ignored eggs remain ignored while unchanged.

Removal, unloading or a new world clears the observation; an egg at a new position starts its own timer. Module toggles and same-world session corrections do not reset the timer. Only loaded blocks are inspected. Existing grinder approach/stuck-navigation timers remain separate.

The standalone edition shares the grinder helpers and behavior; it does not add an ESP module. StationaryEggsTest checks the exact 25-second boundary, continued exclusion, independent positions, removal/reappearance, world reset, and selection of a fresh egg beyond an ignored nearer egg. The test runs in both Forge checks and against the signed Ornithe artifact.
