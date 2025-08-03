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
"""Verifies AE state machine when using precapture trigger."""


import logging
import os
from mobly import test_runner

import its_base_test
import camera_properties_utils
import capture_request_utils
import its_session_utils
import target_exposure_utils

_AE_INACTIVE = 0
_AE_SEARCHING = 1
_AE_CONVERGED = 2
_AE_LOCKED = 3  # not used in this test
_AE_FLASHREQUIRED = 4  # not used in this test
_AE_PRECAPTURE = 5
_FRAMES_AE_DISABLED = 5
_FRAMES_PER_ITERATION = 8
_ITERATIONS_TO_CONVERGE = 5
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_START_AE_PRECAP_TRIG = 1
_STOP_AE_PRECAP_TRIG = 0


class AePrecaptureTest(its_base_test.ItsBaseTest):
  """Test the AE state machine when using the precapture trigger.
  """

  def test_ae_precapture(self):
    logging.debug('Starting %s', _NAME)
    logging.debug('AE_INACTIVE: %d', _AE_INACTIVE)
    logging.debug('AE_SEARCHING: %d', _AE_SEARCHING)
    logging.debug('AE_CONVERGED: %d', _AE_CONVERGED)
    logging.debug('AE_PRECAPTURE: %d', _AE_PRECAPTURE)

    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)

      # Check SKIP conditions
      camera_properties_utils.skip_unless(
          camera_properties_utils.compute_target_exposure(props) and
          camera_properties_utils.per_frame_control(props))

      # Load chart for scene
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet, self.chart_distance)

      _, fmt = capture_request_utils.get_fastest_manual_capture_settings(props)

      # Capture 5 manual requests with AE disabled and the last request
      # has an AE precapture trigger (which should be ignored since AE is
      # disabled).
      logging.debug('Manual captures')
      manual_reqs = []
      e, s = target_exposure_utils.get_target_exposure_combos(
          self.log_path, cam)['midExposureTime']
      manual_req = capture_request_utils.manual_capture_request(s, e)
      manual_req['android.control.aeMode'] = _AE_INACTIVE
      manual_reqs += [manual_req] * (_FRAMES_AE_DISABLED-1)
      precap_req = capture_request_utils.manual_capture_request(s, e)
      precap_req['android.control.aeMode'] = _AE_INACTIVE
      precap_req['android.control.aePrecaptureTrigger'] = _START_AE_PRECAP_TRIG
      manual_reqs.append(precap_req)
      caps = cam.do_capture(manual_reqs, fmt)
      for i, cap in enumerate(caps):
        state = cap['metadata']['android.control.aeState']
        msg = f'AE state after manual request {i}: {state}'
        logging.debug('%s', msg)
        if state != _AE_INACTIVE:
          raise AssertionError(f'{msg} AE_INACTIVE: {_AE_INACTIVE}')

      # Capture auto request and verify the AE state: no trigger.
      logging.debug('Auto capture')
      auto_req = capture_request_utils.auto_capture_request()
      auto_req['android.control.aeMode'] = _AE_SEARCHING
      cap = cam.do_capture(auto_req, fmt)
      state = cap['metadata']['android.control.aeState']
      msg = f'AE state after auto request: {state}'
      logging.debug('%s', msg)
      if state not in [_AE_SEARCHING, _AE_CONVERGED]:
        raise AssertionError(f'{msg} AE_SEARCHING: {_AE_SEARCHING}, '
                             f'AE_CONVERGED: {_AE_CONVERGED}')

      # Capture auto request with a precapture trigger.
      logging.debug('Auto capture with precapture trigger')
      auto_req['android.control.aePrecaptureTrigger'] = _START_AE_PRECAP_TRIG
      cap = cam.do_capture(auto_req, fmt)
      state = cap['metadata']['android.control.aeState']
      msg = f'AE state after auto request with precapture trigger: {state}'
      logging.debug('%s', msg)
      if state not in [_AE_SEARCHING, _AE_CONVERGED, _AE_PRECAPTURE]:
        raise AssertionError(f'{msg} AE_SEARCHING: {_AE_SEARCHING}, '
                             f'AE_CONVERGED: {_AE_CONVERGED}, '
                             f'AE_PRECAPTURE: {_AE_PRECAPTURE}')

      # Capture some more auto requests, and AE should converge.
      logging.debug('Additional auto captures')
      auto_req['android.control.aePrecaptureTrigger'] = _STOP_AE_PRECAP_TRIG
      for _ in range(_ITERATIONS_TO_CONVERGE):
        caps = cam.do_capture([auto_req] * _FRAMES_PER_ITERATION, fmt)
        state = caps[-1]['metadata']['android.control.aeState']
        msg = f'AE state after auto request: {state}'
        logging.debug('%s', msg)
        if state == _AE_CONVERGED:
          return
      if state != _AE_CONVERGED:
        raise AssertionError(f'{msg}  AE_CONVERGED: {_AE_CONVERGED}')

if __name__ == '__main__':
  test_runner.main()
