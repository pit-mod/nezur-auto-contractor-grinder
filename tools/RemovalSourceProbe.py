from pathlib import Path
import sys,json,hashlib
r=Path(sys.argv[1]);b=r/'src/main/java/com/nezurstandalone';focus=(b/'module/impl/render/Focus.java').read_text(encoding='utf-8');chrome=(b/'gui/GuiChrome.java').read_text(encoding='utf-8')
print(json.dumps({'kos_module_present':(b/'module/impl/render/KOSList.java').is_file(),'guild_ui_present':(b/'gui/GuildManagerGUI.java').is_file(),'focus_kos_dependency':'KOSManager' in focus,'show_kos_setting':'Show KOS' in focus,'guilds_tab':'GUILDS' in chrome,'focus_sha256':hashlib.sha256((b/'module/impl/render/Focus.java').read_bytes()).hexdigest()},sort_keys=True))
