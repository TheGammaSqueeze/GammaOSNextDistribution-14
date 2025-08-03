#!/usr/bin/python3

import glob
import os
import re
import sys

project_path = os.getcwd()
exit_code = 0

def report_error(msg, *args):
  global exit_code
  sys.stderr.write("\033[31mError:\033[0m ")
  sys.stderr.write((msg % args))
  sys.stderr.write("\n")
  exit_code = 1

def find_java_files(dir):
  for root, path, files in os.walk(dir):
    for name in files:
      if name.endswith(".java"):
        yield os.path.sep.join([root, name])

def get_java_package(file):
  with open(file, "rt") as f:
    for line in f:
      m = re.match(r'\s*package\s+(.+);', line)
      if m:
        return m.group(1)
  return None

def verify_package_path(file, srcdir):
  assert file.startswith(srcdir)
  package = get_java_package(file)
  if package is None:
    report_error("can't find package declaration in %s", file)

  dir_package = ".".join(file[len(srcdir):].split(os.path.sep)[:-1])
  if dir_package != package:
    report_error("package declaration in %s does not match expected package based on it's path: '%s' != '%s'", file, package, dir_package)


java_src_dir = os.path.sep.join([project_path, "java", ""])
javatests_src_dir = os.path.sep.join([project_path, "javatests", ""])


for java in find_java_files(project_path):
  if java.startswith(java_src_dir):
    verify_package_path(java, java_src_dir)
  elif java.startswith(javatests_src_dir):
    verify_package_path(java, javatests_src_dir)
  else:
    report_error("java file not inside java/ or javatests/: %s", java)

if exit_code != 0:
  sys.stderr.write("All java files in this project should go in java/ or javatests/ in a directory matching its package.\n")

sys.exit(exit_code)
