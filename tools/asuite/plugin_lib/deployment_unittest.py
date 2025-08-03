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

"""Unittests for deployment."""
import os
import shutil
import subprocess
import tempfile
import unittest
from unittest import mock

from plugin_lib.deployment import PluginDeployment


# pylint: disable=protected-access
from aidegen.lib import config


class DeploymentUnittests(unittest.TestCase):
    """Unit tests for deployment.py."""

    _TMP_DIR = None

    def setUp(self):
        """Prepare the testdata related path."""
        DeploymentUnittests._TMP_DIR = tempfile.mkdtemp()
        config.AidegenConfig._CONFIG_DIR = os.path.join(
            DeploymentUnittests._TMP_DIR, '.config', 'asuite', 'aidegen')
        config.AidegenConfig._CONFIG_FILE_PATH = os.path.join(
            config.AidegenConfig._CONFIG_DIR,
            config.AidegenConfig._DEFAULT_CONFIG_FILE)

    def tearDown(self):
        """Clear the testdata related path."""
        shutil.rmtree(DeploymentUnittests._TMP_DIR)

    @mock.patch('builtins.input')
    def test_ask_for_install(self, mock_input):
        """Test _ask_for_install."""
        mock_input.return_value = 'y'
        PluginDeployment()._ask_for_install()
        self.assertTrue(mock_input.call)

    @mock.patch.object(subprocess, 'check_call')
    def test_build_jars(self, mock_check_call):
        """Test _build_jars."""
        PluginDeployment()._build_jars()
        self.assertTrue(mock_check_call.call)

    def test_write_read_selection(self):
        """Test _read_selection and _write_selection."""
        PluginDeployment._user_selection = 'yes'
        self.assertEqual(PluginDeployment._user_selection, 'yes')
        PluginDeployment._user_selection = 'no'
        self.assertEqual(PluginDeployment._user_selection, 'no')
