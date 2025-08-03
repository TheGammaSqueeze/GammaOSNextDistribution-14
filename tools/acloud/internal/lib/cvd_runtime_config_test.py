#!/usr/bin/env python
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
"""Tests for cvd_runtime_config class."""

import os
import unittest

from unittest import mock

from acloud import errors
from acloud.internal.lib import cvd_runtime_config as cf_cfg
from acloud.internal.lib import driver_test_lib


class CvdRuntimeconfigTest(driver_test_lib.BaseDriverTest):
    """Test CvdRuntimeConfig."""

    CF_RUNTIME_CONFIG = """
{"x_display" : ":20",
 "display_configs" :
 [
  {
   "dpi" : 320,
   "x_res" : 720,
   "y_res" : 1280
  }
 ],
 "instances": {
   "2":{
       "adb_ip_and_port": "127.0.0.1:6520",
       "adb_host_port": 6520,
       "instance_dir": "/path-to-instance-dir",
       "crosvm_binary" : "/home/vsoc-01/bin/crosvm",
       "vnc_server_port": 6444
   }
 }
}
"""

    CF_RUNTIME_CONFIG_WEBRTC = """
{"x_display" : ":20",
 "display_configs" :
 [
  {
   "dpi" : 320,
   "x_res" : 720,
   "y_res" : 1280
  }
 ],
 "instances" : {
   "1":{
       "adb_ip_and_port": "127.0.0.1:6520",
       "adb_host_port": 6520,
       "instance_dir": "/path-to-instance-dir",
       "vnc_server_port": 6444,
       "virtual_disk_paths": ["/path-to-image"]
   }
 },
 "enable_webrtc" : true,
 "vnc_server_binary" : "/home/vsoc-01/bin/vnc_server",
 "crosvm_binary" : "/home/vsoc-01/bin/crosvm",
 "webrtc_assets_dir" : "/home/vsoc-01/usr/share/webrtc/assets",
 "webrtc_binary" : "/home/vsoc-01/bin/webRTC",
 "webrtc_certs_dir" : "/home/vsoc-01/usr/share/webrtc/certs",
 "webrtc_public_ip" : "127.0.0.1"
}
"""

    CF_RUNTIME_CONFIG_NO_INSTANCES = """
{"x_display" : ":20",
 "display_configs" :
 [
  {
   "dpi" : 320,
   "x_res" : 720,
   "y_res" : 1280
  }
 ],
 "instance_dir" : "fake_instance_dir",
 "instances": {}
}
"""


    # pylint: disable=protected-access, no-member
    def testGetCuttlefishRuntimeConfig(self):
        """Test GetCuttlefishRuntimeConfig."""
        # Should raise error when file does not exist.
        self.Patch(os.path, "exists", return_value=False)
        # Verify return data.
        self.Patch(os.path, "exists", return_value=True)
        expected_dict = {
                         'display_configs': [{'dpi': 320, 'x_res': 720, 'y_res': 1280}],
                         'x_display': ':20',
                         'instances':
                             {'2':
                                  {'adb_ip_and_port': '127.0.0.1:6520',
                                   'crosvm_binary': '/home/vsoc-01/bin/crosvm',
                                   'adb_host_port': 6520,
                                   'instance_dir': '/path-to-instance-dir',
                                   'vnc_server_port': 6444}
                             },
                        }
        mock_open = mock.mock_open(read_data=self.CF_RUNTIME_CONFIG)
        cf_cfg_path = "/fake-path/local-instance-2/fake.config"
        with mock.patch("builtins.open", mock_open):
            fake_cvd_runtime_config = cf_cfg.CvdRuntimeConfig(cf_cfg_path)
            self.assertEqual(fake_cvd_runtime_config._config_dict, expected_dict)
            self.assertEqual(fake_cvd_runtime_config.enable_webrtc, None)
            self.assertEqual(fake_cvd_runtime_config.config_path,
                             "/fake-path/local-instance-2/fake.config")
            self.assertEqual(fake_cvd_runtime_config.instance_id, "2")

        # Test read runtime config from raw_data and webrtc AVD.
        self.Patch(cf_cfg, "_GetIdFromInstanceDirStr")
        fake_cvd_runtime_config_webrtc = cf_cfg.CvdRuntimeConfig(
            raw_data=self.CF_RUNTIME_CONFIG_WEBRTC)
        cf_cfg._GetIdFromInstanceDirStr.assert_not_called()
        self.assertEqual(fake_cvd_runtime_config_webrtc.config_path, None)
        self.assertEqual(fake_cvd_runtime_config_webrtc.instance_id, "1")
        self.assertEqual(fake_cvd_runtime_config_webrtc.instance_ids, ["1"])
        self.assertEqual(fake_cvd_runtime_config_webrtc.enable_webrtc, True)
        self.assertEqual(fake_cvd_runtime_config_webrtc.display_configs,
                         [{'dpi': 320, 'x_res': 720, 'y_res': 1280}])
        self.assertEqual(fake_cvd_runtime_config_webrtc.adb_ip_port, "127.0.0.1:6520")
        self.assertEqual(fake_cvd_runtime_config_webrtc.instance_dir, "/path-to-instance-dir")
        self.assertEqual(fake_cvd_runtime_config_webrtc.vnc_port, 6444)
        self.assertEqual(fake_cvd_runtime_config_webrtc.adb_port, 6520)
        self.assertEqual(fake_cvd_runtime_config_webrtc.virtual_disk_paths, ['/path-to-image'])
        self.assertEqual(fake_cvd_runtime_config_webrtc.cvd_tools_path, "/home/vsoc-01/bin")

        # Test read runtime config with no instances data.
        fake_cvd_runtime_config_no_instances = cf_cfg.CvdRuntimeConfig(
            raw_data=self.CF_RUNTIME_CONFIG_NO_INSTANCES)
        self.assertEqual(fake_cvd_runtime_config_no_instances.instance_id, "1")
        self.assertEqual(fake_cvd_runtime_config_no_instances.instance_ids, ["1"])

        # Test exception with no config file and no raw_data.
        self.assertRaises(errors.ConfigError,
                          cf_cfg.CvdRuntimeConfig,
                          config_path=None,
                          raw_data=None)


class CvdRuntimeconfigFunctionTest(driver_test_lib.BaseDriverTest):
    """Test CvdRuntimeconfigFunctionTest class."""

    # pylint: disable=protected-access
    def testGetIdFromInstanceDirStr(self):
        """Test GetIdFromInstanceDirStr."""
        fake_instance_dir = "/path-to-instance-dir"
        self.assertEqual(cf_cfg._GetIdFromInstanceDirStr(fake_instance_dir), None)

        fake_instance_dir = "/fake-path/local-instance-1/"
        self.assertEqual(cf_cfg._GetIdFromInstanceDirStr(fake_instance_dir), "1")

        fake_home_path = "/home/fake_user/"
        self.Patch(os.path, 'expanduser', return_value=fake_home_path)
        fake_instance_dir = "/home/fake_user/local-instance/"
        self.assertEqual(cf_cfg._GetIdFromInstanceDirStr(fake_instance_dir), "1")


if __name__ == "__main__":
    unittest.main()
