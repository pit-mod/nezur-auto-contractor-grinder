# Live sidebar reconstruction — 2026-10-19

A read-only snapshot was taken from the running Forge client on the Minecraft client thread. No packets, commands, bot toggles or class retransformation were used. The installed JAR matched SHA-256 7ecd3346a2f4285cf5d53c344773fbc6c51fd0afc137759779a9ef78cfa3ed32.

The actual gold row was split as follows:

- Team prefix: `Gold: §65,699.54`
- Unique score entry: `⚽` (U+26BD)
- Team suffix: `§6g`
- Existing parser input: `Gold: 5,699.54⚽g`
- Existing observer/planner result: NaN / invalidated balance

The same server placed unique emoji entries inside level brackets, words in status lines, the footer and the server ID. Trimming only terminal decoration therefore did not address the live case.

SidebarText now identifies team score entries containing only formatting/decorative symbols. ScoreboardHelper.sidebarName omits the decorative symbols from such entries while preserving formatting codes and both full team fragments. Utils uses this semantic view for all sidebar parsers. Real numeric/text entry names and un-teamed rows remain intact; arbitrary text is never stripped out of money values. Raw canonicalName/canonicalParts are retained for diagnostics. This resolves the captured wallet and also restores level/status strings without weakening the price parser.

SidebarTextTest reproduces the exact live failure and tests every split of the wallet string with five observed/related markers, running each through the actual observer and planner. It also checks status/Overdrive text, level brackets, Classic color-only keys, preservation of real numeric/text entries and rejection of ambiguous mixed entries. 170 checks pass, and the test runs in both Forge projects and against the signed native Ornithe JAR.
