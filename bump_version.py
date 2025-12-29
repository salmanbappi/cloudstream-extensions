import re
import os
import subprocess

def get_changed_modules():
    # Detect which modules have changes compared to origin/main
    try:
        output = subprocess.check_output(['git', 'diff', '--name-only', 'origin/main']).decode('utf-8')
        changed_files = output.splitlines()
        
        modules = set()
        for f in changed_files:
            if f.startswith('Dflix/'): modules.add('Dflix')
            if f.startswith('DhakaFlix/'): modules.add('DhakaFlix')
            if f.startswith('FtpBd/'): modules.add('FtpBd')
        return list(modules)
    except:
        # Fallback: if git fails, assume all changed or check local status
        output = subprocess.check_output(['git', 'status', '--porcelain']).decode('utf-8')
        changed_files = output.splitlines()
        modules = set()
        for line in changed_files:
            f = line[3:]
            if f.startswith('Dflix/'): modules.add('Dflix')
            if f.startswith('DhakaFlix/'): modules.add('DhakaFlix')
            if f.startswith('FtpBd/'): modules.add('FtpBd')
        return list(modules)

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
    else:
        print(f"No version found in {file_path}")

# Main logic
base_dir = "cloudstream-extensions"
changed_modules = get_changed_modules()

if not changed_modules:
    print("No modules changed. Skipping bump.")
else:
    print(f"Detected changes in: {changed_modules}")
    for ext in changed_modules:
        gradle_file = os.path.join(base_dir, ext, "build.gradle.kts")
        if os.path.exists(gradle_file):
            bump_version(gradle_file)
        else:
            print(f"File not found: {gradle_file}")
