#!/usr/bin/env sh
# Validates that all generated outputs match main.star and also checks that
# the star files are correctly formatted.
#
# TODO(reader) - Integrate this sensibly with //tools/test_presubmit.py  :)
lucicfg validate main.star -log-level debug
