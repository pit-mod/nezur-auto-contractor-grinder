from pathlib import Path
import re,sys,json
r=Path(sys.argv[1]); java=r/'src/main/java'; p=next(java.glob('com/*/module/Module.java')); s=p.read_text(encoding='utf-8')
result={'auth_gate_in_isToggled': bool(re.search(r'isToggled\(\).*AuthManager',s)), 'auth_source_files':len(list(java.glob('com/*/auth/*.java'))), 'login_gui_files':len(list(java.glob('com/*/gui/*Auth*.java'))), 'module_hash':__import__('hashlib').sha256(p.read_bytes()).hexdigest()}
print(json.dumps(result,sort_keys=True))
