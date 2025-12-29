import re
import os

def bump_version(file_path):
    with open(file_path, 'r') as f:
        content = f.read()

    # Regex for "version = 123"
    version_pattern = r'(version\s*=\s*)(\d+)'

    def increment_version(match):
        return f"{match.group(1)}{int(match.group(2)) + 1}"

    if re.search(version_pattern, content):
        content = re.sub(version_pattern, increment_version, content)
        print(f"Bumped version in {file_path}")
    else:
        print(f"No version found in {file_path}")

    with open(file_path, 'w') as f:
        f.write(content)

# Bump versions for all extensions
extensions = ["Dflix", "DhakaFlix", "FtpBd"]
base_dir = "cloudstream-extensions"

for ext in extensions:
    gradle_file = os.path.join(base_dir, ext, "build.gradle.kts")
    if os.path.exists(gradle_file):
        bump_version(gradle_file)
    else:
        print(f"File not found: {gradle_file}")