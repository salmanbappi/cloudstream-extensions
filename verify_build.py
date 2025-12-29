import os
import re

def check_kotlin_file(file_path):
    with open(file_path, 'r') as f:
        lines = f.readlines()
    
    errors = []
    for i, line in enumerate(lines):
        # 1. Check for unescaped backslashes in Regex("...")
        if 'Regex("' in line and '"""' not in line:
            # Look for \ followed by anything other than \ or "
            if re.search(r'Regex\(".*?[^\\]\\.[^\\"].*?"\)', line):
                errors.append(f"Line {i+1}: Possible unescaped backslash in Regex string literal.")

        # 2. Check for unbalanced parentheses in single line listOf
        if 'listOf(' in line and line.count('(') < line.count(')'):
             errors.append(f"Line {i+1}: Possible extra closing parenthesis.")

    return errors

if __name__ == "__main__":
    providers = [
        "cloudstream-extensions/Dflix/src/main/kotlin/com/cloudstream/extensions/dflix/DflixProvider.kt",
        "cloudstream-extensions/DhakaFlix/src/main/kotlin/com/cloudstream/extensions/dhakaflix/DhakaFlixProvider.kt",
        "cloudstream-extensions/FtpBd/src/main/kotlin/com/cloudstream/extensions/ftpbd/FtpBdProvider.kt"
    ]
    
    all_ok = True
    for p in providers:
        if os.path.exists(p):
            print(f"Checking {p}...")
            errors = check_kotlin_file(p)
            if errors:
                for e in errors:
                    print(f"  [ERROR] {e}")
                all_ok = False
            else:
                print("  [OK]")
    
    if not all_ok:
        exit(1)
    print("\nAll files passed basic syntax check.")