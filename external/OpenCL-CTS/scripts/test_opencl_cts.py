#!/usr/bin/env python3

from typing import List, Optional, Tuple

import argparse
import os
import pipes
import subprocess
import sys
import unittest

ANDROID_RUNNER_REQUIRED_VERBOSITY = 2


def parse_args(args: Optional[List[str]] = None) -> argparse.Namespace:
  parser = argparse.ArgumentParser()
  parser.add_argument('test_name', type=str, help="Name of the test")
  parser.add_argument('binary_path', type=str,
                      help="Full path to the binary on device")
  parser.add_argument('--subtests', type=str, nargs='*',
                      help="Specific subtests to run")
  parser.add_argument('--test_args', type=str, nargs='*',
                      help="Unfiltered arguments to pass to the run command")

  args = parser.parse_args(args)
  args.subtests = args.subtests or []
  args.test_args = args.test_args or []

  return args


def run_command(command: str) -> Tuple[int, str, str]:
  serial_number = os.environ.get("ANDROID_SERIAL", "")
  if not serial_number:
    raise "$ANDROID_SERIAL is empty, device must be specified"

  full_command = ["adb", "-s", serial_number, "shell", command]
  ret = subprocess.run(
      full_command, capture_output=True, universal_newlines=True)
  return ret.returncode, ret.stdout, ret.stderr


def get_all_subtests(binary_path: str) -> List[str]:
  retcode, output, _ = run_command(f'{binary_path} --help')

  test_name_line = "Test names"
  index = output.find(test_name_line)
  if index == -1:
    return []

  test_names_output = output[index:]
  test_names = []
  # Skip the first line which starts with "Test names"
  for test_name in test_names_output.splitlines()[1:]:
    if not test_name.startswith((" ", "\t")):
      break
    test_names.append(test_name.strip())

  return test_names


def get_subtests(binary_path: str, subtests: List[str]) -> List[str]:
  all_subtests = set(get_all_subtests(binary_path))
  if not subtests:
    return all_subtests

  subtests = set(subtests)
  selected_subtests = subtests & all_subtests
  remaining_subtests = subtests - all_subtests

  if remaining_subtests:
    print("Could not find subtests: {}".format(', '.join(remaining_subtests)),
          file=sys.stderr)

  return sorted(list(selected_subtests))


class OpenCLTest(unittest.TestCase):

  def __init__(self, test_name: str, binary_path: str, args: List[str]):

    self._test_name = test_name
    self._binary_path = binary_path
    self._args = args

    self.command = " ".join(
        [self._binary_path, self._test_name] +
        list(map(pipes.quote, self._args))
    )

    self.test_func_name = self._test_name
    setattr(self, self.test_func_name, self.genericTest)

    super().__init__(methodName=self.test_func_name)

  def genericTest(self):
    retcode, output, oerror = run_command(self.command)

    # TODO(layog): CTS currently return non-zero return code if the
    # implementation is missing for some API even if the API is not supported by
    # the version reported by the driver. Need to patch upstream.
    missing_line = f"ERROR: Test '{self._test_name}' is missing implementation"
    if missing_line in output or missing_line in oerror:
      self.skipTest(f"{self._test_name} API not available in the driver")

    self.assertFalse(retcode, "Test exited with non-zero status")

    # TODO(b/158646251): Update upstream to exit with proper error code
    passed_line = "PASSED test."
    self.assertTrue(passed_line in output)


def main():
  """main entrypoint for test runner"""
  args = parse_args(sys.argv[1:])

  # HACK: Name hack to report the actual test name
  OpenCLTest.__name__ = args.test_name
  OpenCLTest.__qualname__ = args.test_name

  suite = unittest.TestSuite()
  subtests = get_subtests(args.binary_path, args.subtests)
  for subtest in subtests:
    suite.addTest(OpenCLTest(subtest, args.binary_path, args.test_args))

  runner = unittest.TextTestRunner(
      stream=sys.stderr, verbosity=ANDROID_RUNNER_REQUIRED_VERBOSITY)
  runner.run(suite)


if __name__ == "__main__":
  main()
