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
"""CameraITS test to measure jitter in camera timestamps."""

import logging
import os.path

import matplotlib
from matplotlib import pylab
from mobly import test_runner

import its_base_test
import camera_properties_utils
import capture_request_utils
import its_session_utils

_NS_TO_MS = 1.0E-6
_NAME = os.path.basename(__file__).split('.')[0]
_NUM_FRAMES = 50
_START_FRAME = 2  # 1 frame delay to allow faster latency to 1st frame
_TEST_FPS = 30  # frames per second
# PASS/FAIL thresholds
_MIN_AVG_FRAME_DELTA = 30  # at least 30ms delta between frames
_MAX_INIT_FRAME_DELTA = 100  # no more than 100ms between first 2 frames
_MAX_VAR_FRAME_DELTA = 0.01  # variance of frame deltas
_MAX_FRAME_DELTA_JITTER = 0.3  # max ms gap from the average frame delta


class JitterTest(its_base_test.ItsBaseTest):
  """Measure jitter in camera timestamps."""

  def test_jitter(self):
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      camera_properties_utils.skip_unless(
          camera_properties_utils.manual_sensor(props) and
          camera_properties_utils.sensor_fusion(props))

      req, fmt = capture_request_utils.get_fastest_manual_capture_settings(
          props)
      req['android.control.aeTargetFpsRange'] = [_TEST_FPS, _TEST_FPS]
      caps = cam.do_capture([req] * _NUM_FRAMES, [fmt])

      # Log the millisecond delta between the start of each exposure
      tstamps = [c['metadata']['android.sensor.timestamp'] for c in caps]
      if (tstamps[1]-tstamps[0])*_NS_TO_MS > _MAX_INIT_FRAME_DELTA:
        raise AssertionError('Initial frame timestamp delta too great! '
                             f'tstamp[1]: {tstamps[1]}ms, '
                             f'tstamp[0]: {tstamps[0]}ms, '
                             f'ATOL: {_MAX_INIT_FRAME_DELTA}ms')
      deltas = [
          tstamps[i] - tstamps[i-1] for i in range(_START_FRAME, len(tstamps))
      ]
      deltas_ms = [d * _NS_TO_MS for d in deltas]
      avg = sum(deltas_ms) / len(deltas_ms)
      var = sum([d * d for d in deltas_ms]) / len(deltas_ms) - avg * avg
      range0 = min(deltas_ms) - avg
      range1 = max(deltas_ms) - avg

      logging.debug('Average: %s', avg)
      logging.debug('Variance: %s', var)
      logging.debug('Jitter range: %s to %s', range0, range1)

      # Draw a plot.
      pylab.figure()
      pylab.plot(range(len(deltas_ms)), deltas_ms, '-bo')
      pylab.title(_NAME)
      pylab.xlabel('frame number')
      pylab.ylabel('jitter (ms)')
      name_with_log_path = os.path.join(self.log_path, _NAME)
      matplotlib.pyplot.savefig(f'{name_with_log_path}_deltas.png')

      # Test for pass/fail.
      if avg <= _MIN_AVG_FRAME_DELTA:
        raise AssertionError(f'avg: {avg:.4f}ms, TOL: {_MIN_AVG_FRAME_DELTA}ms')
      if var >= _MAX_VAR_FRAME_DELTA:
        raise AssertionError(f'var: {var:.4f}ms, TOL: {_MAX_VAR_FRAME_DELTA}ms')
      if (abs(range0) >= _MAX_FRAME_DELTA_JITTER or
          abs(range1) >= _MAX_FRAME_DELTA_JITTER):
        raise AssertionError(f'range0: {range0:.4f}ms, range1: {range1:.4f}ms, '
                             f'TOL: {_MAX_FRAME_DELTA_JITTER}')


if __name__ == '__main__':
  test_runner.main()
