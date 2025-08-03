# Copyright 2019 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Helpers/wrappers for the subprocess module for migration to python3."""

import subprocess


def CheckCommand(cmd):
    """Executes the command using Popen()."""
    with subprocess.Popen(
        cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, encoding="utf-8"
    ) as cmd_obj:
        stdout, _ = cmd_obj.communicate()

    if cmd_obj.returncode:
        print(stdout)
        raise subprocess.CalledProcessError(cmd_obj.returncode, cmd)


def check_output(cmd, cwd=None):
    """Wrapper for pre-python3 subprocess.check_output()."""

    return subprocess.check_output(cmd, encoding="utf-8", cwd=cwd)


def check_call(cmd, cwd=None):
    """Wrapper for pre-python3 subprocess.check_call()."""

    subprocess.check_call(cmd, encoding="utf-8", cwd=cwd)


# FIXME: CTRL+C does not work when executing a command inside the chroot via
# `cros_sdk`.
def ChrootRunCommand(chroot_path, cmd, verbose=False):
    """Runs the command inside the chroot."""

    exec_chroot_cmd = ["cros_sdk", "--"]
    exec_chroot_cmd.extend(cmd)

    return ExecCommandAndCaptureOutput(
        exec_chroot_cmd, cwd=chroot_path, verbose=verbose
    )


def ExecCommandAndCaptureOutput(cmd, cwd=None, verbose=False):
    """Executes the command and prints to stdout if possible."""

    out = check_output(cmd, cwd=cwd).rstrip()

    if verbose and out:
        print(out)

    return out
