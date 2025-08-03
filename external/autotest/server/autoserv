#!/usr/bin/python3 -u

import os
import sys
import subprocess


dir_name = os.path.dirname(os.path.abspath(__file__))
test_name = None
suite_name = None

if "-l" in sys.argv:
    try:
        label = sys.argv[sys.argv.index('-l') + 1]
    except IndexError:
        raise Exception("No job name followed -l flag")
    label_sections = label.split("/")
    if len(label_sections) > 1:
        test_name = label_sections[-1]
    if len(label_sections) > 2:
        suite_name = label_sections[-2]
elif "--control-name" in sys.argv:
    try:
        test_name = sys.argv[sys.argv.index('--control-name') + 1]
    except IndexError:
        raise Exception("No test name followed --control-name flag")

os.environ["PY_VERSION"] = "3"

exit(subprocess.call([os.path.join(dir_name, '_autoserv')] + sys.argv[1:]))
