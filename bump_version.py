import re
import os
import subprocess

def get_changed_modules():
    try:
        # Check local uncommitted changes
        output = subprocess.check_output(['git', 'status', '--porcelain']).decode('utf-8')
        changed_files = output.splitlines()
        
        modules = set()
        for line in changed_files:
            f = line[3:]
            if f.startswith('Dflix/'): modules.add('Dflix')
            if f.startswith('DhakaFlix/'): modules.add('DhakaFlix')
            if f.startswith('FtpBd/'): modules.add('FtpBd')
        return list(modules)
    except:
        return []

def bump_version(file_path):
    with open(file_path, 'r') as f:
        content = f.read()
    version_pattern = r'(version\s*=\s*)(\d+)'
    def increment_version(match):
        return f"{match.group(1)}{int(match.group(2)) + 1}"
    if re.search(version_pattern, content):
        content = re.sub(version_pattern, increment_version, content)
        print(f"Bumped version in {file_path}")
        with open(file_path, 'w') as f:
            f.write(content)

if __name__ == "__main__":
    changed = get_changed_modules()
    if not changed:
        print("No changes detected in modules. Skipping bump.")
    else:
        print(f"Modules with changes: {changed}")
        for mod in changed:
            gradle = f"{mod}/build.gradle.kts"
            if os.path.exists(gradle):
                bump_version(gradle)
            else:
                print(f"Warning: {gradle} not found")