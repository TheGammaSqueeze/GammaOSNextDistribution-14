#!/usr/bin/env python3
#
#   Copyright 2020 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.
import unittest
from acts.controllers import power_monitor


class PowerMonitorTest(unittest.TestCase):

    def test_registry_gets_updated(self):
        power_monitor.update_registry({'a_key': 'a_value'})
        self.assertTrue('a_key' in power_monitor.get_registry())
        self.assertEqual('a_value', power_monitor.get_registry()['a_key'])

    def test_registry_disallows_mutation(self):
        power_monitor.update_registry({'a_key': 'a_value'})
        with self.assertRaises(power_monitor.ResourcesRegistryError):
            power_monitor.update_registry({'a_key': 'another_value'})

    def test_registry_allows_updates_with_same_values(self):
        power_monitor.update_registry({'a_key': 'a_value'})
        power_monitor.update_registry({'a_key': 'a_value'})

    def tearDown(self):
        super().tearDown()
        power_monitor._REGISTRY = {}


if __name__ == '__main__':
    unittest.main()
