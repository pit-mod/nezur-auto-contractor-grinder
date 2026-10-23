"""Load the final JAR with real Forge/Mixin in a build-local sandbox, stopping before graphical startup."""
from pathlib import Path
import json
import os
import shutil
import subprocess

root=Path(__file__).resolve().parents[1]
build=root/'build/forge-smoke'
build.mkdir(parents=True,exist_ok=True)
java=Path('C:/Program Files/Eclipse Adoptium/jdk-8.0.482.8-hotspot/bin')
# Run gradlew auditClasspath after building. The actual Forge universal and vanilla
# distribution are needed for this production-loader fixture, not the patched ForgeBin.
runtime=(root/'tools/runtime-classpath.txt').read_text(encoding='utf-8-sig').strip().split(';')
dependencies=[p for p in runtime if Path(p).is_file() and p.endswith('.jar')
              and 'org.spongepowered' not in p and 'forgeBin-' not in p]
sdk=Path(os.environ['APPDATA'])/'PrismLauncher/libraries'
universal=sdk/'net/minecraftforge/forge/1.8.9-11.15.1.2318-1.8.9/forge-1.8.9-11.15.1.2318-1.8.9-universal.jar'
vanilla=sdk/'com/mojang/minecraft/1.8.9/minecraft-1.8.9-client.jar'
assert universal.is_file() and vanilla.is_file(), 'Production Forge/vanilla SDK required in PrismLauncher libraries'
dependencies.extend([str(universal),str(vanilla)])
dependencies=list(dict.fromkeys(dependencies))
classes=build/'classes';classes.mkdir(exist_ok=True)
compilecp=';'.join(dependencies)
args=['-encoding','UTF-8','-cp',compilecp,'-d',str(classes),
      str(root/'tools/ForgeOnlyEntrypoint.java'),str(root/'tools/ForgeOnlyTweaker.java')]
argfile=build/'javac.args'
argfile.write_text('\n'.join('"'+a.replace('\\','/')+'"' for a in args),encoding='utf-8')
subprocess.run([str(java/'javac.exe'),'@'+str(argfile)],check=True)
game=build/'game';mods=game/'mods';mods.mkdir(parents=True,exist_ok=True)
artifact=root/'build/libs/Nezur-AutoContractor-Grinder-1.5.4.jar'
shutil.copy2(artifact,mods/artifact.name)
command=[str(java/'java.exe'),'-Dmixin.debug.export=true','-cp',';'.join([str(classes),*dependencies]),
         'net.minecraft.launchwrapper.Launch','--tweakClass','com.nezurtest.ForgeOnlyTweaker',
         '--gameDir',str(game),'--version','1.8.9']
with (build/'launch.log').open('w',encoding='utf-8') as log:
    result=subprocess.run(command,cwd=game,stdout=log,stderr=subprocess.STDOUT,timeout=90)
output=(build/'launch.log').read_text(encoding='utf-8',errors='replace')
assert result.returncode==0 and 'REAL_FORGE_ONLY_NEZUR_BOOTSTRAP=PASS' in output, output[-16000:]
assert 'production_reported_rotation_movement_hooks=PASS' in output
print('\n'.join(line for line in output.splitlines() if 'production_' in line or 'REAL_FORGE_ONLY' in line))
print('FORGE_SMOKE=PASS final packaged JAR, nine target classes; no graphical client or multiplayer launched')
