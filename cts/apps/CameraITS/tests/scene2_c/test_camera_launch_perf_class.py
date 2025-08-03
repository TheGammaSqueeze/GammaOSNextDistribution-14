# Copyright 2021 The Android Open Source Project
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
"""Verify camera startup is < 600ms for both front and back primary cameras.
"""

from mobly import test_runner

import its_base_test
import camera_properties_utils
import its_session_utils

# This must match MPC12_CAMERA_LAUNCH_THRESHOLD in ItsTestActivity.java
_CAMERA_LAUNCH_S_PERFORMANCE_CLASS_THRESHOLD = 600  # ms


class CameraLaunchSPerfClassTest(its_base_test.ItsBaseTest):
  """Test camera launch latency for S performance class as specified in CDD.

  [2.2.7.2/7.5/H-1-6] MUST have camera2 startup latency (open camera to first
  preview frame) < 600ms as measured by the CTS camera PerformanceTest under
  ITS lighting conditions (3000K) for both primary cameras.
  """

  def test_camera_launch(self):
    # Open camera with "with" semantics to check skip condition and load chart
    #
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id) as cam:

      camera_properties_utils.skip_unless(
          cam.is_primary_camera())

      # Load chart for scene.
      props = cam.get_camera_properties()
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet, self.chart_distance)

    # Create an its session without opening the camera to test camera launch
    # latency
    cam = its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id)

    launch_ms = cam.measure_camera_launch_ms()

    # Assert launch time if device claims performance class
    if (cam.is_performance_class() and
        launch_ms >= _CAMERA_LAUNCH_S_PERFORMANCE_CLASS_THRESHOLD):
      raise AssertionError(f'camera_launch_time_ms: {launch_ms}, THRESH: '
                           f'{_CAMERA_LAUNCH_S_PERFORMANCE_CLASS_THRESHOLD}')

    # Log launch time, so that the corresponding MPC level can be written to
    # report log. Text must match MPC12_CAMERA_LAUNCH_PATTERN in
    # ItsTestActivity.java.
    print(f'camera_launch_time_ms:{launch_ms}')

if __name__ == '__main__':
  test_runner.main()
