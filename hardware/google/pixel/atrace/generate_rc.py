#!/usr/bin/env python3

import argparse

parser = argparse.ArgumentParser("generate_rc.py", description="Generates an .rc files that fixes the permissions for all the ftrace events listed in the input atrace_categories.txt file")
parser.add_argument("filename", help="Path to the atrace_categories.txt file")

args = parser.parse_args()

print("# Sets permission for vendor ftrace events")
print("on late-init")

with open(args.filename, 'r') as f:
  for line in f:
    line = line.rstrip('\n')
    if line.startswith(' ') or line.startswith('\t'):
      path = line.lstrip(" \t")
      print("    chmod 0666 /sys/kernel/debug/tracing/events/{}/enable".format(path))
      print("    chmod 0666 /sys/kernel/tracing/events/{}/enable".format(path))
    else:
      print ("    # {} trace points".format(line))
