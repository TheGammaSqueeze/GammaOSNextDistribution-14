# Copyright 2024 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

from mobly import base_test
from mobly import test_runner
from mobly import utils
from mobly.asserts import assert_not_in
from mobly.controllers import android_device


class MultiDeviceInstrumentationTest(base_test.BaseTestClass):

  def setup_class(self):
    self.devices = self.register_controller(android_device)

  def test_in_parallel(self):
    def run_instrument_cmd(device_idx, device):
      result = device.adb.shell((
          f'am instrument -w -e position {device_idx} '
          + 'android.test.wifi.nsd/androidx.test.runner.AndroidJUnitRunner'
      ))
      assert_not_in('FAIL', result.decode(), f'Failed in device {device_idx}')

    utils.concurrent_exec(
        run_instrument_cmd,
        [*enumerate(self.devices)],
        max_workers=2,
        raise_on_exception=True,
    )


if __name__ == '__main__':
  if '--' in sys.argv:
    index = sys.argv.index('--')
    sys.argv = sys.argv[:1] + sys.argv[index + 1 :]

  test_runner.main()
