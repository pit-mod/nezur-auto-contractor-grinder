"""Read-only comparison with the sibling Nezur source; optional, not a build dependency."""
from pathlib import Path
import hashlib
import json

ROOT = Path(__file__).resolve().parents[1]
REFERENCE = ROOT.parent / 'nezur'
TARGET = ROOT / 'src/main/java/com/nezurstandalone'
SOURCE = REFERENCE / 'src/main/java/com/nezur'
OUT = ROOT / 'verification/loadout-parity-20261017'
OUT.mkdir(parents=True, exist_ok=True)

def read(path):
    return path.read_text(encoding='utf-8').replace('\r\n', '\n')

def normalized(path):
    return read(path).replace('com.nezur.', 'com.nezurstandalone.')

def digest(text):
    return hashlib.sha256(text.encode('utf-8')).hexdigest()

results = []

def compare(name, source, target):
    if source != target:
        raise AssertionError('Shared behavior differs: ' + name)
    results.append({'name': name, 'normalized_sha256': digest(source), 'match': True})

for name in ['engine/LoadoutPlanner.java', 'engine/LoadoutGoldObserver.java', 'engine/LoadoutContext.java',
             'engine/LoadoutConfirmation.java', 'engine/LoadoutMenus.java', 'engine/UpgradeNpcMatcher.java', 'engine/PitCombatArea.java', 'engine/SpireTargeting.java', 'utils/PitMapManager.java', 'utils/SidebarText.java',
             'control/OverdriveOof.java', 'utils/MenuTransition.java', 'control/ServerMessages.java',
             'control/Lifecycle.java', 'control/StationaryEggs.java', 'control/EggLocations.java', 'utils/DragonEggTracker.java', 'utils/NearestEggScan.java',
             'control/LowestClassicPit.java', 'control/CommandGate.java', 'module/impl/player/AutoReconnect.java']:
    compare(name, normalized(SOURCE / name), read(TARGET / name))

for name in ['LoadoutPlannerTest.java', 'LoadoutStartupTest.java', 'LoadoutGoldObserverTest.java',
             'LoadoutConfirmationTest.java', 'LoadoutCompletionTest.java', 'OverdriveOofTest.java', 'LowestClassicPitTest.java', 'StationaryEggsTest.java', 'UpgradeNpcMatcherTest.java', 'GrinderMovementTest.java', 'SidebarTextTest.java', 'LoadoutContextTest.java']:
    compare('test/' + name, normalized(REFERENCE / 'tools/tests' / name), read(ROOT / 'tools' / name))

source = normalized(SOURCE / 'engine/GrinderEngine.java')
target = read(TARGET / 'engine/GrinderEngine.java')
sections = [
    ('shop navigation, interaction and matching', '    private boolean startPrestigeProcess()', '    // ---- perk GUI'),
    ('dragon egg handling', '    // ---- dragon egg', '    // ---- mystic pickup'),
    ('loadout gold/requirements', '    private void refreshLoadoutGold(', '    private void observeLoadoutPacket('),
    ('packet observation and all managed menu handlers', '    private void observeLoadoutPacket(', '    private void refreshPerkAccount('),
    ('failure and menu classification', '    private void failMenu(', '    private boolean ownsMenu('),
    ('server response gating and guarded native clicks', '    private boolean menuReady(', '    private int findAction('),
    ('slot label parsing', '    private int findManagedSlotIndex(', '    /** Menu index of the "Perk Slot #n"'),
    ('sidebar detection and command dispatch', '        boolean overdriveInPit=', '        // Night-quest expiry'),
    ('delegated lobby and Limbo recovery', '    private void handleLobbyReconnect()', '    // ---- dragon egg'),
    ('idle return handoff', '    private boolean handleIdleReturn(', '    private void '),
]
for name, start, end in sections:
    def section(text):
        a = text.index(start)
        return text[a:text.index(end, a + len(start))]
    compare('GrinderEngine/' + name, section(source), section(target))

report = {'shared_sections_matching': len(results), 'checks': results,
          'scope': 'Shared loadout/Overdrive/menu implementation, tests and idle return. '
                   'Whole projects have separate namespaces, combat/module adapters and free-contract options.'}
(OUT / 'shared-parity.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
print('Shared automation parity: PASS sections=' + str(len(results)))
