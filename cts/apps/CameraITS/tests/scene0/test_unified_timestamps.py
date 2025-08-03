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
"""CameraITS test for unified timestamp for image and motion sensor events."""

import logging
import time


from mobly import test_runner

import its_base_test
import camera_properties_utils
import capture_request_utils
import its_session_utils

_SENSOR_EVENTS_WAIT_TIME = 2  # seconds


class UnifiedTimeStampTest(its_base_test.ItsBaseTest):
  """Test if image and motion sensor events are in the same time domain.
  """

  def test_unified_timestamps(self):
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)

      # Only run test if the appropriate properties are claimed.
      camera_properties_utils.skip_unless(
          camera_properties_utils.sensor_fusion(props) and
          camera_properties_utils.backward_compatible(props))

      # Get the timestamp of a captured image.
      if camera_properties_utils.manual_sensor(props):
        req, fmt = capture_request_utils.get_fastest_manual_capture_settings(
            props)
      else:
        req, fmt = capture_request_utils.get_fastest_auto_capture_settings(
            props)
      cap = cam.do_capture(req, fmt)
      ts_image0 = cap['metadata']['android.sensor.timestamp']

      # Get the timestamps of motion events.
      logging.debug('Reading sensor measurements')
      sensors = cam.get_sensors()
      cam.start_sensor_events()
      time.sleep(_SENSOR_EVENTS_WAIT_TIME)  # run sensors to get events
      events = cam.get_sensor_events()
      ts_sensor_first = {}
      ts_sensor_last = {}
      for sensor, existing in sensors.items():
      # Vibrator doesn't generate outputs: b/142653973
        if existing and sensor != 'vibrator':
          if not events[sensor]:
            raise AssertionError(f'{sensor} has no events!')
          ts_sensor_first[sensor] = events[sensor][0]['time']
          ts_sensor_last[sensor] = events[sensor][-1]['time']

      # Get the timestamp of another image.
      cap = cam.do_capture(req, fmt)
      ts_image1 = cap['metadata']['android.sensor.timestamp']

      logging.debug('Image timestamps: %s , %s', ts_image0, ts_image1)

      # The motion timestamps must be between the two image timestamps.
      for sensor, existing in sensors.items():
        if existing and sensor != 'vibrator':
          logging.debug('%s timestamps: %d %d', sensor, ts_sensor_first[sensor],
                        ts_sensor_last[sensor])
          if (not ts_image0 < ts_sensor_first[sensor] < ts_image1 or
              not ts_image0 < ts_sensor_last[sensor] < ts_image1):
            raise AssertionError(
                f'{sensor} times not bounded by camera! camera: '
                f'{ts_image0}:{ts_image1}, {sensor}: '
                f'{ts_sensor_first[sensor]}:{ts_sensor_last[sensor]}')


if __name__ == '__main__':
  test_runner.main()
