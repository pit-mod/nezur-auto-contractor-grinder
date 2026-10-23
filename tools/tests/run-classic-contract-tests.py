from pathlib import Path
import subprocess
root=Path(__file__).resolve().parents[2];out=root/'build/classic-parser-tests';out.mkdir(exist_ok=True)
j=Path('C:/Program Files/Eclipse Adoptium/jdk-8.0.482.8-hotspot/bin')
files=['src/main/java/com/nezurstandalone/contract/ContractScoreboard.java','src/main/java/com/nezurstandalone/contract/ContractOffer.java','src/main/java/com/nezurstandalone/contract/ContractSelector.java','tools/tests/PitClassicScoreboardTest.java','tools/tests/PitClassicMenusTest.java']
subprocess.run([str(j/'javac.exe'),'-encoding','UTF-8','-d',str(out)]+[str(root/f) for f in files],check=True)
for name in ['PitClassicScoreboardTest','PitClassicMenusTest']:
 subprocess.run([str(j/'java.exe'),'-cp',str(out),name,str(root/'tools/tests/fixtures/pit-classic-contract-menus-20261021.txt')],check=True)
