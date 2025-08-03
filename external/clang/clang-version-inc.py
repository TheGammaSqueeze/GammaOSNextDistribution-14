#!/usr/bin/env python3

import os
import sys

import version

version_string = '%s.%s.%s' % (version.major, version.minor, version.patch)

if len(sys.argv) < 2:
    print(f"Usage: {sys.argv[0]} <Version.inc.in>", file=sys.stderr)
    sys.exit(1)

with open(sys.argv[1], 'r') as f:
    replacements = {
        '@CLANG_VERSION@': version_string,
        '@CLANG_VERSION_MAJOR@': version.major,
        '@CLANG_VERSION_MINOR@': version.minor,
        '@CLANG_VERSION_PATCHLEVEL@': version.patch,
        '@CLANG_HAS_VERSION_PATCHLEVEL@': '1',
    }

    for line in f:
        line = line.strip()
        for replace, to in replacements.items():
            line = line.replace(replace, to)
        print(line)
