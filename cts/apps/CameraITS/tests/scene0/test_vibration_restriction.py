# Copyright 2019 The Android Open Source Project
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
"""Test to see if vibrations can be muted by camera-audio-restriction API."""

import logging
import math
import time

from mobly import test_runner
import numpy as np

import its_base_test
import camera_properties_utils
import its_session_utils


# if the var(x) > var(stable) * this threshold, then device is considered
# vibrated.Test results shows the variance difference is larger for higher
# sampling frequency.This threshold is good enough for 50hz samples.
_THRESHOLD_VIBRATION_VAR = 10.0

# Match CameraDevice.java constant
_AUDIO_RESTRICTION_VIBRATION = 1

# The sleep time between vibrator on/off to avoid getting some residual
# vibrations
_SLEEP_BETWEEN_SAMPLES_SEC = 0.5
# The sleep time to collect sensor samples
_SLEEP_COLLECT_SAMPLES_SEC = 1.0
_PATTERN_MS = [0, 1000]


def calc_magnitude(e):
  x = e['x']
  y = e['y']
  z = e['z']
  return math.sqrt(x * x + y * y + z * z)


class VibrationRestrictionTest(its_base_test.ItsBaseTest):
  """Test vibrations can be muted by the camera audio restriction API."""

  def test_vibration_restriction(self):
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      sensors = cam.get_sensors()

      camera_properties_utils.skip_unless(
          sensors.get('accel') and sensors.get('vibrator'))

      cam.start_sensor_events()
      cam.do_vibrate(_PATTERN_MS)
      test_length_second = sum(_PATTERN_MS) / 1000
      time.sleep(test_length_second)
      events = cam.get_sensor_events()
      logging.debug('Accelerometer events over %ds: %d ', test_length_second,
                    len(events['accel']))
      times_ms = [e['time'] / float(1e6) for e in events['accel']]
      t0 = times_ms[0]
      times_ms = [t - t0 for t in times_ms]
      magnitudes = [calc_magnitude(e) for e in events['accel']]
      var_w_vibration = np.var(magnitudes)

      time.sleep(_SLEEP_BETWEEN_SAMPLES_SEC)
      cam.start_sensor_events()
      time.sleep(_SLEEP_COLLECT_SAMPLES_SEC)
      events = cam.get_sensor_events()
      magnitudes = [calc_magnitude(e) for e in events['accel']]
      var_wo_vibration = np.var(magnitudes)

      if var_w_vibration < var_wo_vibration * _THRESHOLD_VIBRATION_VAR:
        logging.debug(
            'Warning: unable to detect vibration, variance w/wo'
            'vibration too close: %f/%f. Make sure device is on'
            'non-dampening surface', var_w_vibration, var_wo_vibration)

      time.sleep(_SLEEP_BETWEEN_SAMPLES_SEC)
      cam.start_sensor_events()
      cam.set_audio_restriction(_AUDIO_RESTRICTION_VIBRATION)
      cam.do_vibrate(_PATTERN_MS)
      time.sleep(_SLEEP_COLLECT_SAMPLES_SEC)
      events = cam.get_sensor_events()
      magnitudes = [calc_magnitude(e) for e in events['accel']]
      var_w_vibration_restricted = np.var(magnitudes)

      logging.debug(
          'Accel variance with/without/restricted vibration (%f, %f, %f)',
          var_w_vibration, var_wo_vibration, var_w_vibration_restricted)

      vibration_variance = var_w_vibration_restricted < (
          var_wo_vibration * _THRESHOLD_VIBRATION_VAR)
      if not vibration_variance:
        raise AssertionError('Device vibrated while vibration is muted.')


if __name__ == '__main__':
  test_runner.main()
