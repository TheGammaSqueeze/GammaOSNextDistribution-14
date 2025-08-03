#!/usr/bin/env python3

# Copyright (C) 2020 The Android Open Source Project

# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import os
import unittest
from xml.etree import ElementTree
import subprocess
import sys

VERBOSE = True
TEST_CONFIG = os.path.join(os.path.dirname(__file__), "test_igt_gpu_tools.xml")

""" Extracts tests from AndroidTest.xml compatible file
    param: xmlfile str containing path to AndroidTest.xml compatible xml file
"""
def extract_tests_from_xml(xmlfile):
    tree = ElementTree.parse(xmlfile)
    root = tree.getroot()
    return [(o.attrib['key'], o.attrib['value'])
            for o in root.findall("./target_preparer/option")
            if o.attrib['name'] == "push-file"]

def run_command(command):
    serial_number = os.environ.get("ANDROID_SERIAL", "")
    if not serial_number:
        raise "$ANDROID_SERIAL is empty, device must be specified"
    full_command = ["adb", "-s", serial_number, "shell"] + command
    if VERBOSE:
        print("+" + " ".join(full_command))
    ret = subprocess.run(full_command, capture_output=True, universal_newlines=True)
    if VERBOSE:
        print(ret.stdout)
    return ret.stdout

class IGTGpuToolsBinary():
    """Harness object for a specific IGT GPU test binary. """

    """ param: ondevice_test_path The IGT binary path on device """
    def __init__(self, ondevice_test_path):
        self._path = ondevice_test_path
        subtests = run_command([self._path, "--list-subtests"])
        self._subtests = list(filter(lambda x: x != "", subtests.split("\n")))

    """lists subtests detected in tests.
       return: list of strings indicating subtests found in binary.
    """
    def subtests(self):
        return self._subtests;

    """Runs a subtest.

        param: subtest_name One of the subtests listed by self.subtest()
        return: a subprocess.CompletedProcess
    """
    def run_subtest(self, subtest_name):
        if subtest_name not in self._subtests:
            error
        return run_command([self._path, "--run-subtest", subtest_name])

class IGTGpuToolsTest(unittest.TestCase):
    """Tests for DRM/KMS using Intel Graphics Tests suite"""

    """ param: subtest A valid subtest for the binary
        param: the IGT binary to run as a unit test.
    """
    def __init__(self, subtest, binary):
        self._subtest_name = subtest
        self._test_binary = binary
        setattr(self, self._subtest_name, self.runTest)
        super(IGTGpuToolsTest, self).__init__(methodName=self._subtest_name)

    """ Runs the test set in the constructor """
    def runTest(self):
        output = self._test_binary.run_subtest(self._subtest_name)
        for line in output.split("\n"):
            if "Subtest" not in line:
                continue;
            if "SKIP" in line:
                self.skipTest("{} - skipped".format(self._subtest_name))
                break
            if "SUCCESS" in line:
                break
            if "FAIL" in line:
                self.fail(output)
                break
            self.fail("could not parse test output; test runner failure")

ANDROID_RUNNER_REQUIRED_VERBOSITY = 2

def main():
    """main entrypoint for test runner"""

    runner = unittest.TextTestRunner(stream=sys.stderr, verbosity=ANDROID_RUNNER_REQUIRED_VERBOSITY)
    suite = unittest.TestSuite()
    for test, test_path in extract_tests_from_xml(TEST_CONFIG):
        print("Found IGT-GPU-tools test {}".format(test))
        subsuite = unittest.TestSuite()
        binary = IGTGpuToolsBinary(test_path)
        for subtest in binary.subtests():
            print("\tFound subtest {}".format(subtest))
            subsuite.addTest(IGTGpuToolsTest(subtest, binary))
        suite.addTest(subsuite)
    runner.run(suite)

if __name__=="__main__":
    main()
