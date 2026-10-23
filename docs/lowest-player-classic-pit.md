# Lowest Player Classic Pit

AutoReconnect includes **Lowest Player Classic Pit**, enabled by default. With AutoReconnect enabled on pitclassic.net or a subdomain, it sends `/play pit list`, waits for `Available Pit lobbies:` and the returned server/player rows, and joins the least populated listed instance using `/play pit <id>`. In the supplied capture this is `/play pit au-pit-1` (3 players).

Selection runs during lobby recovery or once at spawn after enabling/connecting. It does not periodically hop while grinding. Equal minimum counts keep the current instance when recognizable; other ties keep list order. Zero-player instances are eligible. The current server is read from the dated sidebar instance line. A new lobby recovery requests a new list.

The flow requires a current Classic network connection and system-channel response following an accepted list request. Player-chat prefixes, lobby names, malformed counts and arbitrary command text cannot become target commands. It collects rows for 100 ms after the last valid row, then joins immediately through the Classic-specific command gate. GUI/item/lease and command backoff gates defer sends; refusal does not advance. Missing/invalid lists fall back to the existing generic join after three seconds. Transfers get a bounded five-second observation window and at most three attempts, with a 100 ms retry gap. Disabling the option/module or changing connections drops stale data. Disabling this option restores ordinary AutoReconnect recovery; the option itself does not turn the module on.

The same controller, module integration and regression test are used in main Forge, native Ornithe and standalone Forge. `lowestClassicPitTest` is part of the main build, `LowestClassicPitTest` runs in standalone verification, and Ornithe runs it against the signed native JAR. Tests include the captured eight-server list, ties/current instance, empty servers, streamed and colored rows, command refusal, safe-state deferral, timeouts, stale connections and bounded transfer recovery. Live transfer with the new artifact remains unverified.

## Fast reconnect and bot delegation follow-up

The reported failure was reproducible with the real command gate: `/play pit list` was classified as a travel command, leaving a ten-second pending-command hold while the selector expired its choice earlier. Grinder could then send a generic `/play pit` and land on a different instance. Classic exact Pit list/join commands replace the old ten-second hold with four-second minimum spacing; explicit server throttle replies retain their backoff. Ordinary command behavior elsewhere keeps its existing policy.

AutoGrinder and AutoContractor now enable AutoReconnect for lobby, Limbo and disconnect recovery instead of sending their own network join commands. Requested lobby swaps are handed to AutoReconnect as well. Contractor recognizes Classic Pit arrivals through the common location tracker. AutoReconnect also handles being enabled after the disconnect screen was already opened, so delegation cannot miss that GUI event. Repeated enable requests leave a live server-list selection intact.

The shared selector/gate test has 106 checks, including a real CommandGate request-list-to-target reproduction at four seconds. Standalone's actual-module probe exercises Grinder delegation, idempotent enable, Contractor disconnect delegation and reconnect timer initialization. No new live transfer has been triggered by the agent.

Latest timing adjustment: Classic Pit list/join commands now have a four-second minimum spacing, shared across owners and preserved across session changes. The selected server stays valid while that delay elapses. Bot delegation to AutoReconnect remains unchanged.
