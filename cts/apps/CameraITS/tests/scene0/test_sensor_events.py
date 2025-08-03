# Copyright 2014 The Android Open Source Project
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
"""CameraITS test for sensor events."""

import logging
import time

from mobly import test_runner

import its_base_test
import camera_properties_utils
import its_session_utils

_SENSOR_EVENTS_WAIT_TIME = 1  # seconds


class SensorEventTest(its_base_test.ItsBaseTest):
  """Basic test to query and print out sensor events.

  Test will only work if the screen is on (i.e.) the device isn't in standby.
  Pass if some of each event are received.
  """

  def test_sensor_events(self):
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      # Only run test if the appropriate caps are claimed.
      camera_properties_utils.skip_unless(
          camera_properties_utils.sensor_fusion(props))

      sensors = cam.get_sensors()
      cam.start_sensor_events()
      time.sleep(_SENSOR_EVENTS_WAIT_TIME)  # run sensors to get events
      events = cam.get_sensor_events()
      logging.debug('Events over 1s: %d gyro, %d accel, %d mag',
                    len(events['gyro']), len(events['accel']),
                    len(events['mag']))
      for key, existing in sensors.items():
        # Vibrator does not return any sensor event. b/142653973
        if existing and key != 'vibrator':
          # Check len(events[key]) > 0
          if not events[key]:
            raise AssertionError(f'Sensor {key} has no events!')


if __name__ == '__main__':
  test_runner.main()
