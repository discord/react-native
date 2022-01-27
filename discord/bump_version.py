#!/usr/bin/env python3

from pathlib import Path
import re
import subprocess

VERSION_MATCHER = re.compile(r'^(.*)-discord-(\d*)$')

status_bytes = subprocess.check_output(['git', 'status', '--porcelain'])
status = status_bytes.decode('utf-8').strip()
assert status == '', f'Detected changed files, please remove or commit.\n\n{status}'

root_bytes = subprocess.check_output(['git', 'rev-parse', '--show-toplevel'])
root = root_bytes.decode('utf-8').strip()
android_path = Path(root) / "ReactAndroid"
props_path = android_path / "gradle.properties"

version = None
property_lines = [line.strip() for line in props_path.read_text().splitlines()]
for line in property_lines:
    if line.startswith("VERSION_NAME="):
        version = line.split('=')[1]

assert version, "unable to find current version"

matches = VERSION_MATCHER.match(version)
assert matches, f'{version} did not match expected format, X.Y.Z-discord-N'

upstream = matches[1]
local = int(matches[2])

new_version = f'{upstream}-discord-{local + 1}'

with open(props_path, 'w') as f:
    for line in property_lines:
        if line.startswith("VERSION_NAME="):
            f.write(f'VERSION_NAME={new_version}\n')
        else:
            f.write(f'{line}\n')


branch_name_bytes = subprocess.check_output(['git', 'symbolic-ref', '--short', 'HEAD'])
branch_name = branch_name_bytes.decode('utf-8').strip()

subprocess.check_call(['../gradlew', 'publishReleasePublicationToDiscordRepository'], cwd=android_path.absolute())

subprocess.check_call(['git', 'add', props_path.absolute()])
subprocess.check_call(['git', 'commit', '-m', f'Bumping to version {new_version}'])
subprocess.check_call(['git', 'push', 'origin', branch_name])

print(f'NEW TAGGED VERSION: {new_version}')
