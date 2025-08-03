#!/usr/bin/env python3
#
# Copyright 2020 - The Android Open Source Project
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

"""Asuite plugin deployment."""
import os
import subprocess

from aidegen.lib import common_util
from aidegen.lib import config

_ASK_INSTALL_PLUGIN = """\nAsuite plugin is a new tool with following features:
    -Atest UI widget. For more information: go/atest_plugin
    -Code search integration. For more information and locate build module: go/android-platform-plugin
Would you like to install the Asuite plugin? (Yes/no/auto)"""
_ASK_UPGRADE_PLUGIN = ('\nAsuite plugin has a new version. Would you like to '
                       'upgrade Asuite plugin? (Yes/no/auto)')
_YES_RESPONSE = 'Thank you, Asuit plugin will be installed in IntelliJ.'
_NO_RESPONSE = ('Thank you, if you want to install Asuite plugin, please use '
                'aidegen --plugin.')
_AUTO_RESPONSE = ('Thank you, Asuit plugin will be installed in IntelliJ, and '
                  'automatically updated to the newest version.')
_THANKS_UPGRADE = 'Thank you for upgrading the Asuite plugin.'
_NO_NEED_UPGRADE = 'Awesome! You have the newest Asuite plugin.'
_SELECTION_ITEM = {'yes': 'yes', 'no': 'no', 'auto': 'auto', 'y': 'yes',
                   'n': 'no', 'a': 'auto', '': 'yes'}


class PluginDeployment:
    """The util class of Asuite plugin deployment.

    Usage:
        PluginDeployment.install_asuite_plugin()
        It will start installation process.

    Attributes:
        is_internal: True if the user is a internal user.
    """

    def __init__(self):
        """PluginDeployment initialize."""
        self.is_internal = self._is_internal_user()

    def install_asuite_plugin(self):
        """It is the main entry function for installing Asuite plugin."""

    def _ask_for_install(self):
        """Asks the user to install the Asuite plugin."""
        input_data = input(_ASK_INSTALL_PLUGIN)
        while input_data.lower() not in _SELECTION_ITEM.keys():
            input_data = input(_ASK_INSTALL_PLUGIN)
        choice = _SELECTION_ITEM.get(input_data)
        self._user_selection = choice
        if choice == 'no':
            print(_NO_RESPONSE)
        else:
            self._copy_jars()
            if choice == 'yes':
                print(_YES_RESPONSE)
            else:
                print(_AUTO_RESPONSE)

    def _ask_for_upgrade(self):
        """Asks the user to upgrade the Asuite plugin."""

    def _copy_jars(self):
        """Copies jars to IntelliJ plugin folders."""

    def _build_jars(self):
        """builds jars to IntelliJ plugin folders."""
        asuite_plugin_path = os.path.join(common_util.get_android_root_dir(),
                                          'tools/asuite/asuite_plugin/')
        asuite_plugin_gradle_path = os.path.join(asuite_plugin_path, 'gradlew')
        cmd = [asuite_plugin_gradle_path, 'build']
        subprocess.check_call(cmd, cwd=asuite_plugin_path)

    def _is_plugin_installed(self):
        """Checks if the user has installed Asuite plugin before.

        Return:
            True if the user has installed Asuite plugin.
        """

    def _is_version_up_to_date(self):
        """Checks if all plugins' versions are up to date or not.

        Return:
            True if all plugins' versions are up to date.
        """

    @property
    def _user_selection(self):
        """Reads the user's selection from config file.

        Return:
            A string of the user's selection: yes/no/auto.
        """
        with config.AidegenConfig() as aconf:
            return aconf.plugin_preference

    @_user_selection.setter
    def _user_selection(self, selection):
        """Writes the user's selection to config file.

        Args:
            selection: A string of the user's selection: yes/no/auto.
        """
        with config.AidegenConfig() as aconf:
            aconf.plugin_preference = selection

    @staticmethod
    def _is_internal_user():
        """Checks if the user is internal user or external user.

        Return:
            True if the user is a internal user.
        """
        return True
