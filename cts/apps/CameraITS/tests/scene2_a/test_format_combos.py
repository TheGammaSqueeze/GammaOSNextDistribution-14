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
"""Verifies different combinations of output formats."""


import logging
import os

from mobly import test_runner

import its_base_test
import camera_properties_utils
import capture_request_utils
import its_session_utils
import target_exposure_utils


_AUTO_REQUEST_MODE = 1
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_STOP_AT_FIRST_FAILURE = False  # change to True to have test break @ 1st FAIL


class FormatCombosTest(its_base_test.ItsBaseTest):
  """Test different combinations of output formats.

  Note the test does not require a specific target but does perform
  both automatic and manual captures so it requires a fixed scene
  where 3A can converge.
  """

  def test_format_combos(self):
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:

      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)

      # Load chart for scene
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet, self.chart_distance)

      successes = []
      failures = []

      # Up to 2 possible request types: auto and manual
      req_aut = capture_request_utils.auto_capture_request()
      reqs = [req_aut]
      if camera_properties_utils.compute_target_exposure(props):
        e, s = target_exposure_utils.get_target_exposure_combos(
            self.log_path, cam)['midExposureTime']
        req_man = capture_request_utils.manual_capture_request(s, e)
        reqs.append(req_man)

      # Up to 10 different combos of output formats.
      # Some are single surfaces and some are multiple surfaces.
      wyuv, hyuv = capture_request_utils.get_available_output_sizes(
          'yuv', props)[-1]
      wjpg, hjpg = capture_request_utils.get_available_output_sizes(
          'jpg', props)[-1]
      fmt_yuv_prev = {'format': 'yuv', 'width': wyuv, 'height': hyuv}
      fmt_yuv_full = {'format': 'yuv'}
      fmt_jpg_prev = {'format': 'jpeg', 'width': wjpg, 'height': hjpg}
      fmt_jpg_full = {'format': 'jpeg'}
      fmt_combos = [
          [fmt_yuv_prev],
          [fmt_yuv_full],
          [fmt_jpg_prev],
          [fmt_jpg_full],
          [fmt_yuv_prev, fmt_jpg_prev],
          [fmt_yuv_prev, fmt_jpg_full],
      ]
      if camera_properties_utils.raw16(props):
        fmt_raw_full = {'format': 'raw'}
        fmt_combos.extend([
            [fmt_raw_full],
            [fmt_yuv_prev, fmt_raw_full],
            [fmt_yuv_prev, fmt_jpg_prev, fmt_raw_full],
            [fmt_yuv_prev, fmt_jpg_full, fmt_raw_full]])

      if camera_properties_utils.y8(props):
        wy8, hy8 = capture_request_utils.get_available_output_sizes(
            'y8', props)[-1]
        fmt_y8_prev = {'format': 'y8', 'width': wy8, 'height': hy8}
        fmt_y8_full = {'format': 'y8'}
        fmt_combos.extend([
            [fmt_y8_prev],
            [fmt_y8_full]])

      # Two different burst lengths: single frame and 3 frames.
      burst_lens = [1, 3]

      # Run through 2xlen(fmt_combos)x2 different combinations.
      n = 0
      for r, req in enumerate(reqs):
        if req['android.control.mode'] == _AUTO_REQUEST_MODE:
          req_str = 'auto'
        else:
          req_str = 'manual'
        for f, fmt_combo in enumerate(fmt_combos):
          for b, burst_len in enumerate(burst_lens):
            try:
              cam.do_capture([req] * burst_len, fmt_combo)
              successes.append((n, r, f, b))
              logging.debug('Success[%02d]', n)
              logging.debug('req: %s', req_str)
              logging.debug('fmt: %s', str(fmt_combo))
              logging.debug('burst_len: %d\n', burst_len)

            except Exception as e:  # pylint: disable=broad-except
              logging.error(e)
              logging.error('Failure[%02d]', n)
              logging.debug('req: %s', req_str)
              logging.error('fmt: %s', str(fmt_combo))
              logging.error('burst_len: %d\n', burst_len)
              failures.append((n, r, f, b))
              if _STOP_AT_FIRST_FAILURE:
                raise AssertionError(
                    f'Capture fail at combo req: {req_str}, fmt: {fmt_combo}, '
                    f'burst: {burst_len}') from e
            n += 1

      num_fail = len(failures)
      num_success = len(successes)
      num_total = len(reqs)*len(fmt_combos)*len(burst_lens)
      num_not_run = num_total - num_success - num_fail

      logging.debug('Success: %d / %d', num_success, num_total)

      # assert all combinations successfully capture
      if num_fail != 0:
        raise AssertionError(f'Number of fails: {num_fail} / {num_total}')
      if num_not_run > 0:
        raise AssertionError(f'Number of combos not run: {num_not_run}')

if __name__ == '__main__':
  test_runner.main()
