# AutoGrinder dashboard

The HUD now uses a measured, compact dashboard in place of the fixed debug table.
`HUD Scale` controls its size (default 0.90, range 0.60–1.40). It also fits itself
to small windows. The HUD editor renders the actual dashboard and uses its real
dimensions for dragging and snapping.

## Always visible

- XP and gold earned: totals with hourly rates below them.
- Fresh drops: total supported Mystic/Fresh drop announcements, with an hourly rate.
- Night Quests: completed quests and confirmed Vile rewards.
- Current state, map/zone, player level, and session elapsed time.
- Health, current gold balance, mid population, and current streak.
- A live target's name, health/absorption, distance, and health bar; otherwise a useful activity message.
- Observed kills, assists, deaths, and any earned renown in a compact footer.

Movement, healing/position pauses, next upgrade requirements, command backoff,
and work/break countdowns appear when relevant. Unknown gold stays unknown.
An empty, locked desired perk slot does not consume a row.

## Context panels

Only active events produce an event panel. Available server sidebar fields
(time left, score, rank, team, souls, winnings, etc.) supplement the bot's current
plan; missing values do not produce empty rows.

- Spire: current floor, plan, entry countdown, and observed event elapsed time.
- Squads: rank, top-seven goal, hold/swap plan, and the active swap check countdown.
- Raffle: held-ticket progress toward deposit and collection/deposit state.
- Robbery: rank, hunt/hold plan, and movement state.
- Blockhead: painting/powerup target, observed respawn timer, powerups seen,
  Quicktrail time remaining, and diamond armor when equipped.
- Dragon Egg: interaction plan, distance, and the stationary-egg policy.
- Rage Pit/TDM/other named sidebar events: current plan and observed server fields.
- Night Quest: observed kill progress, live server progress when available,
  elapsed time, completed quests, confirmed Vile and quest XP rewards.
- NPC work: pending perk/killstreak, confirmation stage, and a retry countdown.
- Prestige: observed earned-gold requirement, progress, and remaining gold.

## Meaning of the counters

Totals are in-memory observations during this module instance's enabled runs;
they survive toggling and world changes and reset when the client restarts.
Elapsed time retains the existing session clock from first enable, including
disabled time. Rates use that elapsed time and warm up for the first 30 seconds.
Gold earned is gross reported income, not the change in wallet after purchases.
Fresh drops count server announcements, not proof that the dropped item was collected.
Night Quest rewards count accepted completion messages, never assumed rewards.
Kill progress is explicitly labeled **Kills observed** because chat observations
can differ from the server's quest rules or a missed start message.

The snapshot refreshes at most four times a second (immediately after a state/world
change). Rendering reuses it; the new HUD adds no block/entity scans or movement commands.

## Validation

`gradlew.bat grinderDashboardTest --offline` executes the production renderer
against a headless drawing surface with Minecraft's real bitmap font. Nine
fixtures cover combat, Night Quest, Spire, Squads, Raffle, Robbery, Blockhead,
NPC/Prestige, and unusually long names. Checks enforce measured height and text
bounds. PNG previews are under `verification/grinder-dashboard-20261020`.
`GrinderHudStatsTest` covers quest rewards, kill/assist filtering, deaths, and
number/time formatting. These checks do not replace observation on a live server.

The same renderer, counters, and snapshot logic are shared with the standalone
edition; `tools/check-grinder-dashboard-parity.py` in that project verifies them.
