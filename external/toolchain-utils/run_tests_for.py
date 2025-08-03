#!/usr/bin/env python3
# Copyright 2019 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Runs tests for the given input files.

Tries its best to autodetect all tests based on path name without being *too*
aggressive.

In short, there's a small set of directories in which, if you make any change,
all of the tests in those directories get run. Additionally, if you change a
python file named foo, it'll run foo_test.py or foo_unittest.py if either of
those exist.

All tests are run in parallel.
"""

# NOTE: An alternative mentioned on the initial CL for this
# https://chromium-review.googlesource.com/c/chromiumos/third_party/toolchain-utils/+/1516414
# is pytest. It looks like that brings some complexity (and makes use outside
# of the chroot a bit more obnoxious?), but might be worth exploring if this
# starts to grow quite complex on its own.


import argparse
import collections
import multiprocessing.pool
import os
import shlex
import signal
import subprocess
import sys
from typing import Optional, Tuple


TestSpec = collections.namedtuple("TestSpec", ["directory", "command"])

# List of python scripts that are not test with relative path to
# toolchain-utils.
non_test_py_files = {
    "debug_info_test/debug_info_test.py",
}


def _make_relative_to_toolchain_utils(toolchain_utils, path):
    """Cleans & makes a path relative to toolchain_utils.

    Raises if that path isn't under toolchain_utils.
    """
    # abspath has the nice property that it removes any markers like './'.
    as_abs = os.path.abspath(path)
    result = os.path.relpath(as_abs, start=toolchain_utils)

    if result.startswith("../"):
        raise ValueError("Non toolchain-utils directory found: %s" % result)
    return result


def _filter_python_tests(test_files, toolchain_utils):
    """Returns all files that are real python tests."""
    python_tests = []
    for test_file in test_files:
        rel_path = _make_relative_to_toolchain_utils(toolchain_utils, test_file)
        if rel_path not in non_test_py_files:
            python_tests.append(_python_test_to_spec(test_file))
        else:
            print("## %s ... NON_TEST_PY_FILE" % rel_path)
    return python_tests


def _gather_python_tests_in(rel_subdir, toolchain_utils):
    """Returns all files that appear to be Python tests in a given directory."""
    subdir = os.path.join(toolchain_utils, rel_subdir)
    test_files = (
        os.path.join(subdir, file_name)
        for file_name in os.listdir(subdir)
        if file_name.endswith("_test.py") or file_name.endswith("_unittest.py")
    )
    return _filter_python_tests(test_files, toolchain_utils)


def _run_test(test_spec: TestSpec, timeout: int) -> Tuple[Optional[int], str]:
    """Runs a test.

    Returns a tuple indicating the process' exit code, and the combined
    stdout+stderr of the process. If the exit code is None, the process timed
    out.
    """
    # Each subprocess gets its own process group, since many of these tests
    # spawn subprocesses for a variety of reasons. If these tests time out, we
    # want to be able to clean up all of the children swiftly.
    # pylint: disable=subprocess-popen-preexec-fn
    with subprocess.Popen(
        test_spec.command,
        cwd=test_spec.directory,
        stdin=subprocess.DEVNULL,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        encoding="utf-8",
        # TODO(b/296616854): This is unsafe, and we should use
        # process_group=0 when we have upgraded to Python 3.11.
        preexec_fn=lambda: os.setpgid(0, 0),
    ) as p:
        child_pgid = p.pid
        try:
            out, _ = p.communicate(timeout=timeout)
            return p.returncode, out
        except BaseException as e:
            # Try to shut the processes down gracefully.
            os.killpg(child_pgid, signal.SIGINT)
            try:
                # 2 seconds is arbitrary, but given that these are unittests,
                # should be plenty of time for them to shut down.
                p.wait(timeout=2)
            except subprocess.TimeoutExpired:
                os.killpg(child_pgid, signal.SIGKILL)
            except:
                os.killpg(child_pgid, signal.SIGKILL)
                raise

            if isinstance(e, subprocess.TimeoutExpired):
                # We just killed the entire process group. This should complete
                # ~immediately. If it doesn't, something is very wrong.
                out, _ = p.communicate(timeout=5)
                return (None, out)
            raise


def _python_test_to_spec(test_file):
    """Given a .py file, convert it to a TestSpec."""
    # Run tests in the directory they exist in, since some of them are sensitive
    # to that.
    test_directory = os.path.dirname(os.path.abspath(test_file))
    file_name = os.path.basename(test_file)

    if os.access(test_file, os.X_OK):
        command = ["./" + file_name]
    else:
        # Assume the user wanted py3.
        command = ["python3", file_name]

    return TestSpec(directory=test_directory, command=command)


def _autodetect_python_tests_for(test_file, toolchain_utils):
    """Given a test file, detect if there may be related tests."""
    if not test_file.endswith(".py"):
        return []

    test_prefixes = ("test_", "unittest_")
    test_suffixes = ("_test.py", "_unittest.py")

    test_file_name = os.path.basename(test_file)
    test_file_is_a_test = any(
        test_file_name.startswith(x) for x in test_prefixes
    ) or any(test_file_name.endswith(x) for x in test_suffixes)

    if test_file_is_a_test:
        test_files = [test_file]
    else:
        test_file_no_suffix = test_file[:-3]
        candidates = [test_file_no_suffix + x for x in test_suffixes]

        dir_name = os.path.dirname(test_file)
        candidates += (
            os.path.join(dir_name, x + test_file_name) for x in test_prefixes
        )
        test_files = (x for x in candidates if os.path.exists(x))
    return _filter_python_tests(test_files, toolchain_utils)


def _run_test_scripts(pool, all_tests, timeout, show_successful_output=False):
    """Runs a list of TestSpecs. Returns whether all of them succeeded."""
    results = [
        pool.apply_async(_run_test, (test, timeout)) for test in all_tests
    ]

    failures = []
    for i, (test, future) in enumerate(zip(all_tests, results)):
        # Add a bit more spacing between outputs.
        if show_successful_output and i:
            print("\n")

        pretty_test = shlex.join(test.command)
        pretty_directory = os.path.relpath(test.directory)
        if pretty_directory == ".":
            test_message = pretty_test
        else:
            test_message = "%s in %s/" % (pretty_test, pretty_directory)

        print("## %s ... " % test_message, end="")
        # Be sure that the users sees which test is running.
        sys.stdout.flush()

        exit_code, stdout = future.get()
        if exit_code == 0:
            print("PASS")
            is_failure = False
        else:
            print("TIMEOUT" if exit_code is None else "FAIL")
            failures.append(test_message)
            is_failure = True

        if show_successful_output or is_failure:
            if stdout:
                print("-- Stdout:\n", stdout)
            else:
                print("-- No stdout was produced.")

    if failures:
        word = "tests" if len(failures) > 1 else "test"
        print(f"{len(failures)} {word} failed:")
        for failure in failures:
            print(f"\t{failure}")

    return not failures


def _compress_list(l):
    """Removes consecutive duplicate elements from |l|.

    >>> _compress_list([])
    []
    >>> _compress_list([1, 1])
    [1]
    >>> _compress_list([1, 2, 1])
    [1, 2, 1]
    """
    result = []
    for e in l:
        if result and result[-1] == e:
            continue
        result.append(e)
    return result


def _fix_python_path(toolchain_utils):
    pypath = os.environ.get("PYTHONPATH", "")
    if pypath:
        pypath = ":" + pypath
    os.environ["PYTHONPATH"] = toolchain_utils + pypath


def _find_forced_subdir_python_tests(test_paths, toolchain_utils):
    assert all(os.path.isabs(path) for path in test_paths)

    # Directories under toolchain_utils for which any change will cause all
    # tests in that directory to be rerun. Includes changes in subdirectories.
    all_dirs = {
        "crosperf",
        "cros_utils",
    }

    relative_paths = [
        _make_relative_to_toolchain_utils(toolchain_utils, path)
        for path in test_paths
    ]

    gather_test_dirs = set()

    for path in relative_paths:
        top_level_dir = path.split("/")[0]
        if top_level_dir in all_dirs:
            gather_test_dirs.add(top_level_dir)

    results = []
    for d in sorted(gather_test_dirs):
        results += _gather_python_tests_in(d, toolchain_utils)
    return results


def _find_go_tests(test_paths):
    """Returns TestSpecs for the go folders of the given files"""
    assert all(os.path.isabs(path) for path in test_paths)

    dirs_with_gofiles = set(
        os.path.dirname(p) for p in test_paths if p.endswith(".go")
    )
    command = ["go", "test", "-vet=all"]
    # Note: We sort the directories to be deterministic.
    return [
        TestSpec(directory=d, command=command)
        for d in sorted(dirs_with_gofiles)
    ]


def main(argv):
    default_toolchain_utils = os.path.abspath(os.path.dirname(__file__))

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--show_all_output",
        action="store_true",
        help="show stdout of successful tests",
    )
    parser.add_argument(
        "--toolchain_utils",
        default=default_toolchain_utils,
        help="directory of toolchain-utils. Often auto-detected",
    )
    parser.add_argument(
        "file", nargs="*", help="a file that we should run tests for"
    )
    parser.add_argument(
        "--timeout",
        default=120,
        type=int,
        help="Time to allow a test to execute before timing it out, in "
        "seconds.",
    )
    args = parser.parse_args(argv)

    modified_files = [os.path.abspath(f) for f in args.file]
    show_all_output = args.show_all_output
    toolchain_utils = args.toolchain_utils

    if not modified_files:
        print("No files given. Exit.")
        return 0

    _fix_python_path(toolchain_utils)

    tests_to_run = _find_forced_subdir_python_tests(
        modified_files, toolchain_utils
    )
    for f in modified_files:
        tests_to_run += _autodetect_python_tests_for(f, toolchain_utils)
    tests_to_run += _find_go_tests(modified_files)

    # TestSpecs have lists, so we can't use a set. We'd likely want to keep them
    # sorted for determinism anyway.
    tests_to_run.sort()
    tests_to_run = _compress_list(tests_to_run)

    with multiprocessing.pool.ThreadPool() as pool:
        success = _run_test_scripts(
            pool, tests_to_run, args.timeout, show_all_output
        )
    return 0 if success else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
