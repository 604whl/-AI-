#!/usr/bin/env python3
import sys, subprocess, re

SECRET_PATTERNS = [
    re.compile(r'sk_live_[a-zA-Z0-9]{20,}'),
    re.compile(r'sk_test_[a-zA-Z0-9]{20,}'),
]

BLOCK_EXTENSIONS = ['.java', '.kt', '.py', '.js', '.ts', '.go', '.rb', '.php', '.cs', '.md', '.yml', '.yaml', '.json', '.properties', '.env']

def get_staged_files():
    r = subprocess.run(['git', 'diff', '--cached', '--name-only'], capture_output=True, text=True, encoding='utf-8', errors='replace')
    return [f.strip() for f in r.stdout.strip().split('\n') if f.strip()]

def check_file(filepath):
    if not any(filepath.endswith(ext) for ext in BLOCK_EXTENSIONS):
        return []
    # 仓库内文件一律 UTF-8；Windows 默认 GBK 会因中文注释抛 UnicodeDecodeError
    r = subprocess.run(['git', 'show', f':{filepath}'], capture_output=True, text=True, encoding='utf-8', errors='replace')
    if r.returncode != 0 or r.stdout is None:
        return []
    matches = []
    for i, line in enumerate(r.stdout.split('\n'), 1):
        for pattern in SECRET_PATTERNS:
            if pattern.search(line):
                matches.append((filepath, i, line.strip()))
    return matches

def main():
    files = get_staged_files()
    all_matches = []
    for filepath in files:
        all_matches.extend(check_file(filepath))
    if all_matches:
        print('PRE-COMMIT BLOCKED: detected API Key pattern!')
        for filepath, line_no, line_content in all_matches:
            print(f'  {filepath}:{line_no} -> {line_content[:80]}')
        print('Suggestion: Use sk_TEST_xxx or sk_MOCK_xxx instead of sk_live_/sk_test_')
        sys.exit(1)
    else:
        print('No API Key patterns detected')
        sys.exit(0)

if __name__ == '__main__':
    main()