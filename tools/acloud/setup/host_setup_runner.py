#!/usr/bin/env python
#
# Copyright 2018 - The Android Open Source Project
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
r"""host setup runner

A setup sub task runner to support setting up the local host for AVD local
instance.
"""

from __future__ import print_function

import getpass
import logging
import os
import shutil
import sys
import tempfile

from acloud.internal import constants
from acloud.internal.lib import utils
from acloud.setup import base_task_runner
from acloud.setup import setup_common
from acloud.setup import mkcert


logger = logging.getLogger(__name__)

_CF_COMMOM_FOLDER = "cf-common"

_INTEL = "intel"
_AMD = "amd"
_KVM_INTEL = "kvm_intel"
_KVM_AMD = "kvm_amd"
_LIST_OF_INTEL_MODULES = [_KVM_INTEL, "kvm"]
_LIST_OF_AMD_MODULES = [_KVM_AMD, "kvm"]
_DICT_MODULES = {_INTEL: _LIST_OF_INTEL_MODULES, _AMD: _LIST_OF_AMD_MODULES}
_INTEL_COMMANDS = [
    "sudo rmmod kvm_intel || true", "sudo rmmod kvm || true",
    "sudo modprobe kvm", "sudo modprobe kvm_intel"]
_AMD_COMMANDS = [
    "sudo rmmod kvm_amd || true", "sudo rmmod kvm|| true", "sudo modprobe kvm",
    "sudo modprobe kvm_amd"]
_DICT_SETUP_CMDS = {_INTEL: _INTEL_COMMANDS, _AMD: _AMD_COMMANDS}
_UPDATE_APT_GET_CMD = "sudo apt-get update"
_INSTALL_CUTTLEFISH_COMMOM_CMD = [
    "git clone https://github.com/google/android-cuttlefish.git {git_folder}",
    "cd {git_folder}/base",
    "debuild -i -us -uc -b",
    "cd ../frontend",
    "debuild -i -us -uc -b",
    "sudo dpkg -i ../cuttlefish-base_*_*64.deb || sudo apt-get install -f",
    "sudo dpkg -i ../cuttlefish-user_*_*64.deb || sudo apt-get install -f",
    "sudo dpkg -i ../cuttlefish-common_*_*64.deb || sudo apt-get install -f"]
_INSTALL_CUTTLEFISH_COMMOM_MSG = ("\nStart to install cuttlefish-common :\n%s"
                                  "\nEnter 'y' to continue, otherwise N or "
                                  "enter to exit: ")


class BasePkgInstaller(base_task_runner.BaseTaskRunner):
    """Subtask base runner class for installing packages."""

    # List of packages for child classes to override.
    PACKAGES = []

    def ShouldRun(self):
        """Check if required packages are all installed.

        Returns:
            Boolean, True if required packages are not installed.
        """
        if not utils.IsSupportedPlatform():
            return False

        # Any required package is not installed or not up-to-date will need to
        # run installation task.
        for pkg_name in self.PACKAGES:
            if not setup_common.PackageInstalled(pkg_name):
                return True

        return False

    def _Run(self):
        """Install specified packages."""
        cmd = "\n".join(
            [setup_common.PKG_INSTALL_CMD % pkg
             for pkg in self.PACKAGES
             if not setup_common.PackageInstalled(pkg)])

        if not utils.GetUserAnswerYes("\nStart to install package(s):\n%s"
                                      "\nEnter 'y' to continue, otherwise N or "
                                      "enter to exit: " % cmd):
            sys.exit(constants.EXIT_BY_USER)

        setup_common.CheckCmdOutput(_UPDATE_APT_GET_CMD, shell=True)
        for pkg in self.PACKAGES:
            setup_common.InstallPackage(pkg)

        logger.info("All package(s) installed now.")


class AvdPkgInstaller(BasePkgInstaller):
    """Subtask runner class for installing packages for local instances."""

    WELCOME_MESSAGE_TITLE = ("Install required packages for host setup for "
                             "local instances")
    WELCOME_MESSAGE = ("This step will walk you through the required packages "
                       "installation for running Android cuttlefish devices "
                       "on your host.")
    PACKAGES = constants.AVD_REQUIRED_PKGS


class HostBasePkgInstaller(BasePkgInstaller):
    """Subtask runner class for installing base host packages."""

    WELCOME_MESSAGE_TITLE = "Install base packages on the host"
    WELCOME_MESSAGE = ("This step will walk you through the base packages "
                       "installation for your host.")
    PACKAGES = constants.BASE_REQUIRED_PKGS


class CuttlefishCommonPkgInstaller(base_task_runner.BaseTaskRunner):
    """Subtask base runner class for installing cuttlefish-common."""

    WELCOME_MESSAGE_TITLE = "Install cuttlefish-common packages on the host"
    WELCOME_MESSAGE = ("This step will walk you through the cuttlefish-common "
                       "packages installation for your host.")

    def ShouldRun(self):
        """Check if cuttlefish-common package is installed.

        Returns:
            Boolean, True if cuttlefish-common is not installed.
        """
        if not utils.IsSupportedPlatform():
            return False

        # Any required package is not installed or not up-to-date will need to
        # run installation task.
        if not setup_common.PackageInstalled(constants.CUTTLEFISH_COMMOM_PKG):
            return True
        return False

    def _Run(self):
        """Install cuttlefilsh-common packages."""
        if setup_common.IsPackageInAptList(constants.CUTTLEFISH_COMMOM_PKG):
            cmd = setup_common.PKG_INSTALL_CMD % constants.CUTTLEFISH_COMMOM_PKG
            if not utils.GetUserAnswerYes(_INSTALL_CUTTLEFISH_COMMOM_MSG % cmd):
                sys.exit(constants.EXIT_BY_USER)
            setup_common.InstallPackage(constants.CUTTLEFISH_COMMOM_PKG)
            return

        # Install cuttlefish-common from github.
        cf_common_path = os.path.join(tempfile.mkdtemp(), _CF_COMMOM_FOLDER)
        logger.debug("cuttlefish-common path: %s", cf_common_path)
        cmd = "\n".join(sub_cmd.format(git_folder=cf_common_path)
                        for sub_cmd in _INSTALL_CUTTLEFISH_COMMOM_CMD)
        try:
            if not utils.GetUserAnswerYes(_INSTALL_CUTTLEFISH_COMMOM_MSG % cmd):
                sys.exit(constants.EXIT_BY_USER)
            setup_common.CheckCmdOutput(cmd, shell=True)
        finally:
            shutil.rmtree(os.path.dirname(cf_common_path))


class LocalCAHostSetup(base_task_runner.BaseTaskRunner):
    """Subtask class that setup host for setup local CA."""

    WELCOME_MESSAGE_TITLE = "Local CA Host Environment Setup"
    WELCOME_MESSAGE = ("This step will walk you through the local CA setup "
                       "to your host for assuring a secure localhost url "
                       "connection when launching an AVD over webrtc.")

    def ShouldRun(self):
        """Check if the local CA is setup or not.

        Returns:
            Boolean, True if local CA is ready.
        """
        if not utils.IsSupportedPlatform():
            return False

        return not mkcert.IsRootCAReady()

    def _Run(self):
        """Setup host environment for the local CA."""
        if not utils.GetUserAnswerYes("\nStart to setup the local CA:\n"
                                      "\nEnter 'y' to continue, otherwise N or "
                                      "enter to exit: "):
            sys.exit(constants.EXIT_BY_USER)

        mkcert.Install()
        logger.info("The local CA '%s.pem' is installed now.",
                    constants.SSL_CA_NAME)


class CuttlefishHostSetup(base_task_runner.BaseTaskRunner):
    """Subtask class that setup host for cuttlefish."""

    WELCOME_MESSAGE_TITLE = "Host Environment Setup"
    WELCOME_MESSAGE = (
        "This step will help you to setup environment for running Android "
        "cuttlefish devices on your host. That includes adding user to kvm "
        "related groups and checking required linux modules."
    )

    def ShouldRun(self):
        """Check host user groups and modules.

         Returns:
             Boolean: False if user is in all required groups and all modules
                      are reloaded.
         """
        if not utils.IsSupportedPlatform():
            return False

        return not (utils.CheckUserInGroups(constants.LIST_CF_USER_GROUPS)
                    and self._CheckLoadedModules(
                        _DICT_MODULES.get(self._GetProcessorType())))

    @staticmethod
    def _GetProcessorType():
        """Get the processor type.

        Returns:
            The processor type of the host. e.g. amd, intel.
        """
        lsmod_output = setup_common.CheckCmdOutput("lsmod", print_cmd=False)
        current_modules = [r.split()[0] for r in lsmod_output.splitlines()]
        if _KVM_AMD in current_modules:
            return _AMD
        return _INTEL

    @staticmethod
    def _CheckLoadedModules(module_list):
        """Check if the modules are all in use.

        Args:
            module_list: The list of module name.

        Returns:
            True if all modules are in use.
        """
        logger.info("Checking if modules are loaded: %s", module_list)
        lsmod_output = setup_common.CheckCmdOutput("lsmod", print_cmd=False)
        current_modules = [r.split()[0] for r in lsmod_output.splitlines()]
        all_modules_present = True
        for module in module_list:
            if module not in current_modules:
                logger.info("missing module: %s", module)
                all_modules_present = False
        return all_modules_present

    def _Run(self):
        """Setup host environment for local cuttlefish instance support."""
        # TODO: provide --uid args to let user use prefered username
        username = getpass.getuser()
        setup_cmds = _DICT_SETUP_CMDS.get(self._GetProcessorType())
        for group in constants.LIST_CF_USER_GROUPS:
            setup_cmds.append("sudo usermod -aG %s % s" % (group, username))

        print("Below commands will be run:")
        for setup_cmd in setup_cmds:
            print(setup_cmd)

        if self._ConfirmContinue():
            for setup_cmd in setup_cmds:
                setup_common.CheckCmdOutput(setup_cmd, shell=True)
            print("Host environment setup has done!")

    @staticmethod
    def _ConfirmContinue():
        """Ask user if they want to continue.

        Returns:
            True if user answer yes.
        """
        answer_client = utils.InteractWithQuestion(
            "\nEnter 'y' to continue, otherwise N or enter to exit: ",
            utils.TextColors.WARNING)
        return answer_client in constants.USER_ANSWER_YES
