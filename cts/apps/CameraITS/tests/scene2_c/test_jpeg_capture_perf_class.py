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
"""Verify jpeg capture latency for both front and back primary cameras.
"""

from mobly import test_runner

import its_base_test
import camera_properties_utils
import its_session_utils

# This must match MPC12_JPEG_CAPTURE_THRESHOLD in ItsTestActivity.java
_JPEG_CAPTURE_S_PERFORMANCE_CLASS_THRESHOLD = 1000  # ms


class JpegCaptureSPerfClassTest(its_base_test.ItsBaseTest):
  """Test jpeg capture latency for S performance class as specified in CDD.

  [2.2.7.2/7.5/H-1-5] MUST have camera2 JPEG capture latency < 1000ms for 1080p
  resolution as measured by the CTS camera PerformanceTest under ITS lighting
  conditions (3000K) for both primary cameras.
  """

  def test_jpeg_capture(self):
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

    # Create an Its session without opening the camera to test camera jpeg
    # capture latency because the test opens camera internally
    cam = its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id)

    jpeg_capture_ms = cam.measure_camera_1080p_jpeg_capture_ms()

    # Assert jpeg capture time if device claims performance class
    if (cam.is_performance_class() and
        jpeg_capture_ms >= _JPEG_CAPTURE_S_PERFORMANCE_CLASS_THRESHOLD):
      raise AssertionError(f'1080p_jpeg_capture_time_ms: {jpeg_capture_ms}, '
                           f'THRESH: '
                           f'{_JPEG_CAPTURE_S_PERFORMANCE_CLASS_THRESHOLD}')

    # Log jpeg capture time so that the corresponding MPC level can be written
    # to report log. Text must match MPC12_JPEG_CAPTURE_PATTERN in
    # ItsTestActivity.java.
    print(f'1080p_jpeg_capture_time_ms:{jpeg_capture_ms}')

if __name__ == '__main__':
  test_runner.main()
