# Gold-ingot objective changes

This reuses AutoContractor.tickGold, its scoreboard lifecycle, the existing grinder spawn exit, PathfinderManager, MovementKeys, JumpController, and generic /oof movement recovery. No separate bot loop, raw attack/command/movement packets or speculative local progress counter was added.

## Population
Gold offers were already selectable without a minimum player count. However AutoContractor.handleConnection and GrinderEngine.shouldLobbySwap could still start a thin-lobby swap. Both now explicitly ignore population for active COLLECT_GOLD_INGOTS. An existing engine population-swap state is canceled for this objective. Normal combat-contract population behavior and network reconnect/rejoin handling are unchanged. The separately configured Low Gold Lobby Swap remains available; that tests visible gold density, not middle-player count.

## Search
Gold Scan Radius now defaults to 64 blocks and accepts 8..128 instead of 8..64. Existing saved custom values remain respected; the bundled preset already selects 64. Only server-loaded drop entities can be detected; the client cannot see unreceived/unloaded drops.

## Nearby pickup recovery
Near a still-live ingot, look toward it and walk forward when aligned, as before. If grounded and the intended movement makes no measurable horizontal/distance progress for 1200 ms, stop forward pushing and sidestep left for 400 ms with a short guarded jump request. If still blocked, try right once. After two unsuccessful attempts, release owned movement, reject that drop for 8 seconds, clear its path and immediately rescan/repath to another available drop. GUI/manual control/item use reset local recovery. Despawn/death/contract cleanup reset its state. JumpController still enforces grounding, headroom and ownership. Generic five-second /oof recovery remains the broader path fallback; local recovery resets its watchdog to avoid premature /oof.

Actual progress remains ContractScoreboard.current/target. Drop disappearance is navigation evidence only, never proof of contract progress.

## Verification
With a Java 8 JAVA_HOME: `python tools/tests/gold-collection-regression.py`. Tests compile the actual tickGold/liveGold/shouldLobbySwap/population method bodies with deterministic Minecraft/input stubs, plus the actual GoldPickupRecovery class. They verify empty-mid behavior, unchanged ordinary-contract swapping, a 48-block target, close stuck sidesteps and bounded retargeting, despawn retargeting, unchanged scoreboard progress, GUI release and movement/airborne/target-change handling.

Local verification records retain BASELINE/MODIFIED/ROLLBACK commands, literal outputs, statuses and hashes. Rollback restores the exact saved AutoContractor and GrinderEngine source on a disposable project copy; the original behavior is reproduced, then the fix is reapplied. Final-JAR helper tests and the existing standalone constructor, contract and Forge/Mixin loading checks are run before publishing.

Scope: free standalone; port of the tested paid-candidate gold changes. No paid source, installed profiles, licensing or unrelated contract behavior is changed. No live server gameplay was tested for this fix.
