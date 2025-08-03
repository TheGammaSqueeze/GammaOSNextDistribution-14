#!/usr/bin/env python3
# -*- coding: utf-8 -*-
#
# Copyright 2021 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Script to make / directory on chromebook writable.

This script updates a remote chromebook to make the / directory writable."
"""


__author__ = "cmtice@google.com (Caroline Tice)"

import argparse
import os
import sys
import time

from cros_utils import command_executer
from cros_utils import locks
from cros_utils import logger
from cros_utils import machines
from cros_utils import misc


lock_file = "/tmp/image_chromeos_lock/image_chromeos_lock"


def Usage(parser, message):
    print("ERROR: %s" % message)
    parser.print_help()
    sys.exit(0)


def RebootChromebook(chromeos_root, remote, cmd_executer):
    cmd = "sudo reboot"
    cmd_executer.CrosRunCommand(
        cmd, chromeos_root=chromeos_root, machine=remote
    )
    time.sleep(10)
    success = False
    for _ in range(1, 10):
        if machines.MachineIsPingable(remote):
            success = True
            break
        time.sleep(1)
    return success


def ParseOutput(output):
    # See comment in FindPartitionNum.
    lines = output.split("\n")
    num_str = "-1"
    for line in lines:
        l = line.strip()
        words = l.split()
        if (
            len(words) > 2
            and words[0] == "sudo"
            and words[1] == "/usr/share/vboot/bin/make_dev_ssd.sh"
            and words[-2] == "--partitions"
        ):
            num_str = words[-1]
            break
    num = int(num_str)

    return num


def FindPartitionNum(chromeos_root, remote, logs, cmd_executer):
    partition_cmd = (
        "/usr/share/vboot/bin/make_dev_ssd.sh " "--remove_rootfs_verification"
    )
    _, output, _ = cmd_executer.CrosRunCommandWOutput(
        partition_cmd,
        chromeos_root=chromeos_root,
        machine=remote,
        terminated_timeout=10,
    )

    # The command above, with no --partitions flag, should return output
    # in the following form:

    # make_dev_ssd.sh: INFO: checking system firmware...
    #
    #  ERROR: YOU ARE TRYING TO MODIFY THE LIVE SYSTEM IMAGE /dev/mmcblk0.
    #
    #  The system may become unusable after that change, especially when you have
    #  some auto updates in progress. To make it safer, we suggest you to only
    #  change the partition you have booted with. To do that, re-execute this
    #  command as:
    #
    #  sudo /usr/share/vboot/bin/make_dev_ssd.sh  --partitions 4
    #
    #  If you are sure to modify other partition, please invoke the command again
    #  and explicitly assign only one target partition for each time
    # (--partitions N )
    #
    # make_dev_ssd.sh: ERROR: IMAGE /dev/mmcblk0 IS NOT MODIFIED.

    # We pass this output to the ParseOutput function where it finds the 'sudo'
    # line with the partition number and returns the partition number.

    num = ParseOutput(output)

    if num == -1:
        logs.LogOutput('Failed to find partition number in "%s"' % output)
    return num


def TryRemoveRootfsFromPartition(
    chromeos_root, remote, cmd_executer, partition_num
):
    partition_cmd = (
        "/usr/share/vboot/bin/make_dev_ssd.sh "
        "--remove_rootfs_verification --partition %d" % partition_num
    )
    ret = cmd_executer.CrosRunCommand(
        partition_cmd,
        chromeos_root=chromeos_root,
        machine=remote,
        terminated_timeout=10,
    )
    return ret


def TryRemountPartitionAsRW(chromeos_root, remote, cmd_executer):
    command = "sudo mount -o remount,rw /"
    ret = cmd_executer.CrosRunCommand(
        command,
        chromeos_root=chromeos_root,
        machine=remote,
        terminated_timeout=10,
    )
    return ret


def Main(argv):
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "-c",
        "--chromeos_root",
        dest="chromeos_root",
        help="Target directory for ChromeOS installation.",
    )
    parser.add_argument("-r", "--remote", dest="remote", help="Target device.")
    parser.add_argument(
        "-n",
        "--no_lock",
        dest="no_lock",
        default=False,
        action="store_true",
        help="Do not attempt to lock remote before imaging.  "
        "This option should only be used in cases where the "
        "exclusive lock has already been acquired (e.g. in "
        "a script that calls this one).",
    )

    options = parser.parse_args(argv[1:])

    # Common initializations
    log_level = "average"
    cmd_executer = command_executer.GetCommandExecuter(log_level=log_level)
    l = logger.GetLogger()

    if options.chromeos_root is None:
        Usage(parser, "--chromeos_root must be set")

    if options.remote is None:
        Usage(parser, "--remote must be set")

    options.chromeos_root = os.path.expanduser(options.chromeos_root)

    try:
        should_unlock = False
        if not options.no_lock:
            try:
                _ = locks.AcquireLock(
                    list(options.remote.split()), options.chromeos_root
                )
                should_unlock = True
            except Exception as e:
                raise RuntimeError("Error acquiring machine: %s" % str(e))

        # Workaround for crosbug.com/35684.
        os.chmod(misc.GetChromeOSKeyFile(options.chromeos_root), 0o600)

        if log_level == "average":
            cmd_executer.SetLogLevel("verbose")

        if not machines.MachineIsPingable(options.remote):
            raise RuntimeError(
                "Machine %s does not appear to be up." % options.remote
            )

        ret = TryRemountPartitionAsRW(
            options.chromeos_root, options.remote, cmd_executer
        )

        if ret != 0:
            l.LogOutput(
                "Initial mount command failed. Looking for root partition"
                " number."
            )
            part_num = FindPartitionNum(
                options.chromeos_root, options.remote, l, cmd_executer
            )
            if part_num != -1:
                l.LogOutput(
                    "Attempting to remove rootfs verification on partition %d"
                    % part_num
                )
                ret = TryRemoveRootfsFromPartition(
                    options.chromeos_root,
                    options.remote,
                    cmd_executer,
                    part_num,
                )
                if ret == 0:
                    l.LogOutput(
                        "Succeeded in removing roofs verification from"
                        " partition %d. Rebooting..." % part_num
                    )
                    if not RebootChromebook(
                        options.chromeos_root, options.remote, cmd_executer
                    ):
                        raise RuntimeError("Chromebook failed to reboot.")
                    l.LogOutput(
                        "Reboot succeeded. Attempting to remount partition."
                    )
                    ret = TryRemountPartitionAsRW(
                        options.chromeos_root, options.remote, cmd_executer
                    )
                    if ret == 0:
                        l.LogOutput("Re-mounted / as writable.")
                    else:
                        l.LogOutput("Re-mount failed. / is not writable.")
                else:
                    l.LogOutput(
                        "Failed to remove rootfs verification from partition"
                        " %d." % part_num
                    )
        else:
            l.LogOutput("Re-mounted / as writable.")

        l.LogOutput("Exiting.")

    finally:
        if should_unlock:
            locks.ReleaseLock(
                list(options.remote.split()), options.chromeos_root
            )

    return ret


if __name__ == "__main__":
    retval = Main(sys.argv)
    sys.exit(retval)
