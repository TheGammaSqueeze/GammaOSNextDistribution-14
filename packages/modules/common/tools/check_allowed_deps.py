#!/usr/bin/python3
""" Script to enforce certain requirements on commits that modify allowed_deps.txt

For more info, go/apex-allowed-deps-error
"""

import re
import subprocess
import sys

sha = sys.argv[1]

AllowedDepsTxt = "build/allowed_deps.txt"

DisableAllowedDepsCheckKey = "No-Allowed-Deps-Check"
ExpectedKeys = set(["Apex-Size-Increase", "Previous-Platform-Support", "Aosp-First", "Test-Info"])

def get_deps(allowed_deps):
  """ Parse allowed_deps.txt contents returning just dependency names """
  deps = set()
  for line in allowed_deps:
    if line.startswith('#'):
      continue
    if len(line.strip()) == 0:
      continue
    dep = line[:line.find("(")]
    deps.add(dep)
  return deps


commit_msg = subprocess.run(["git", "show", "--no-patch", "--format=%B", sha],
  capture_output=True, check=True, text=True).stdout.splitlines()

commit_msg_keys = set()
for line in commit_msg:
  key_match = re.match(r'(\S+):', line)
  if key_match:
    commit_msg_keys.add(key_match.group(1))
if DisableAllowedDepsCheckKey in commit_msg_keys:
  # we are disabled
  sys.exit(0)

missing_keys = ExpectedKeys - commit_msg_keys

if not missing_keys:
  # Nothing to verify
  sys.exit(0)


git_show = subprocess.run(["git", "show", "--name-only", "--format=", sha],
  capture_output=True, check=True, text=True)
files = set(git_show.stdout.split("\n"))
if AllowedDepsTxt not in files:
  # nothing to check
  sys.exit(0)

before = subprocess.run(["git", "show", "%s^:%s" % (sha, AllowedDepsTxt)],
  capture_output=True, check=True, text=True).stdout.splitlines()
after = subprocess.run(["git", "show", "%s:%s" % (sha, AllowedDepsTxt)],
  capture_output=True, check=True, text=True).stdout.splitlines()


before_deps = get_deps(before)
after_deps = get_deps(after)
added = after_deps - before_deps
if len(added) == 0:
  # no new deps added, all good. Maybe just some minSdkVersion changed.
  sys.exit(0)

sys.stderr.write(
"""
\033[91m\033[1mError:\033[0m\033[1m You have added to allowed_deps.txt without providing necessary extra information\033[0m

Added deps:
%s

Missing information from the commit message:
%s

See go/apex-allowed-deps-error for more details.

To disable this check, please add "%s: <reason>" to your commit message.
""" % (
  "\n".join([("  %s" % a) for a in added]),
  "\n".join([("  %s:" % k) for k in missing_keys]),
  DisableAllowedDepsCheckKey
  ))
sys.exit(1)
