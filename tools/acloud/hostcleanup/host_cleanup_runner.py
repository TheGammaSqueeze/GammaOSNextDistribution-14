# Copyright 2021 - The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
r"""host cleanup runner

A host cleanup sub task runner will cleanup host to a pristine state.
"""

from __future__ import print_function

import logging
import os
import subprocess
import textwrap

from acloud.internal import constants
from acloud.internal.lib import utils
from acloud.setup import base_task_runner
from acloud.setup import setup_common

logger = logging.getLogger(__name__)

_PARAGRAPH_BREAK = "="
_PURGE_PACKAGE_CMD = "sudo apt-get purge --assume-yes %s"
_UNINSTALL_SUCCESS_MSG = "Package(s) [%s] have uninstalled."


class BasePurger(base_task_runner.BaseTaskRunner):
    """Subtask base runner class for hostcleanup."""

    PURGE_MESSAGE_TITLE = ""
    PURGE_MESSAGE = ""

    cmds = []
    purge_packages = []

    def ShouldRun(self):
        """Check if required packages are all uninstalled.

        Returns:
            Boolean, True if command list not null.
        """
        if not utils.IsSupportedPlatform():
            return False

        if self.cmds:
            return True

        utils.PrintColorString(
            "[%s]: don't have to process." % self.PURGE_MESSAGE_TITLE,
            utils.TextColors.WARNING)
        return False

    def _Run(self):
        """Run purge commands."""
        utils.PrintColorString("Below commands will be run: \n%s" %
                               "\n".join(self.cmds))

        answer_client = utils.InteractWithQuestion(
            "\nPress 'y' to continue or anything else to do it myself[y/N]: ",
            utils.TextColors.WARNING)
        if answer_client not in constants.USER_ANSWER_YES:
            return

        for cmd in self.cmds:
            try:
                setup_common.CheckCmdOutput(cmd,
                                            shell=True,
                                            stderr=subprocess.STDOUT)
            except subprocess.CalledProcessError as cpe:
                logger.error("Run command [%s] failed: %s",
                             cmd, cpe.output)

        utils.PrintColorString((_UNINSTALL_SUCCESS_MSG %
                                ",".join(self.purge_packages)),
                               utils.TextColors.OKGREEN)

    def PrintPurgeMessage(self):
        """Print purge message"""
        # define the layout of message.
        console_width = int(os.popen('stty size', 'r').read().split()[1])
        break_width = int(console_width / 2)

        # start to print purge message.
        print("\n" + _PARAGRAPH_BREAK * break_width)
        print(" [%s] " % self.PURGE_MESSAGE_TITLE)
        print(textwrap.fill(
            self.PURGE_MESSAGE,
            break_width - 2,
            initial_indent=" ",
            subsequent_indent=" "))
        print(_PARAGRAPH_BREAK * break_width + "\n")


class PackagesUninstaller(BasePurger):
    """Subtask base runner class for uninstalling packages."""

    PURGE_MESSAGE_TITLE = "Uninstalling packages"
    PURGE_MESSAGE = ("This will uninstall packages installed previously "
                     "through \"acloud setup --host-setup\"")

    def __init__(self):
        """Initialize."""
        packages = []
        packages.extend(constants.AVD_REQUIRED_PKGS)
        packages.extend(constants.BASE_REQUIRED_PKGS)
        packages.append(constants.CUTTLEFISH_COMMOM_PKG)

        self.purge_packages = [pkg for pkg in packages
                               if setup_common.PackageInstalled(pkg)]

        self.cmds = [
            _PURGE_PACKAGE_CMD % pkg for pkg in self.purge_packages]

        self.PrintPurgeMessage()
