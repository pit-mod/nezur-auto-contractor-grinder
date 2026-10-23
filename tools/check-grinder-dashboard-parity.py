"""Read-only verification of the shared dashboard; edition-specific combat stays separate."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
reference = root.parent / 'nezur'
source = reference / 'src/main/java/com/nezur'
target = root / 'src/main/java/com/nezurstandalone'

def read(path):
    return path.read_text(encoding='utf-8').replace('com.nezur.', 'com.nezurstandalone.')

checks = 0
def compare(label, a, b):
    global checks
    assert a == b, 'Dashboard behavior differs: ' + label
    checks += 1

for name in ['gui/GrinderDashboard.java', 'engine/GrinderHudStats.java']:
    compare(name, read(source / name), read(target / name))
compare('reward regressions', read(reference / 'tools/tests/GrinderHudStatsTest.java'),
        read(root / 'tools/GrinderHudStatsTest.java'))

def section(text, start, end):
    offset = text.index(start)
    return text[offset:text.index(end, offset + len(start))]

a, b = read(source / 'engine/GrinderEngine.java'), read(target / 'engine/GrinderEngine.java')
a = a.replace('return roniStyle?"roni-autogrinder":"autogrinder";', 'return "autogrinder";')
compare('snapshot, event panels, scaling and renderer',
        section(a, '    // Compact, measured dashboard.', '    private int stateColor('),
        section(b, '    // Compact, measured dashboard.', '    private int stateColor('))
compare('Blockhead read-only observations',
        section(read(source / 'engine/BlockheadController.java'), '    /** Read-only HUD evidence;', '    public void chat('),
        section(read(target / 'engine/BlockheadController.java'), '    /** Read-only HUD evidence;', '    public void chat('))
for text in [a, b]:
    for hook in ['hudStats.life(', 'hudStats.chat(msg,nightQuestActive)', 'hudStats.questStart(msg)',
                 'hudStats.questDone(msg,', 'addSettings(showHud, hudScale, hudAnimations)']:
        # Settings are added with each edition's other settings; require the scale exactly once.
        if hook.startswith('addSettings'):
            assert text.count('new NumberSetting("HUD Scale"') == 1
        else:
            assert text.count(hook) == 1, 'HUD observation hook missing or duplicated: ' + hook
        checks += 1
print('Grinder dashboard parity: PASS checks=' + str(checks))
