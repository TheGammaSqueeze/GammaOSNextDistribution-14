# Copyright 2013 The Android Open Source Project
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
"""Verifies android.flash.mode parameters is applied when set."""


import logging
import os.path
from mobly import test_runner
import numpy as np

import its_base_test
import camera_properties_utils
import capture_request_utils
import image_processing_utils
import its_session_utils
import target_exposure_utils

_FLASH_MODES = {'OFF': 0, 'SINGLE': 1, 'TORCH': 2}
_FLASH_STATES = {'UNAVAIL': 0, 'CHARGING': 1, 'READY': 2, 'FIRED': 3,
                 'PARTIAL': 4}
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_PATCH_H = 0.7  # center 70%
_PATCH_W = 0.7
_PATCH_X = 0.5 - _PATCH_W/2
_PATCH_Y = 0.5 - _PATCH_H/2
_GRADIENT_DELTA = 0.1  # used for tablet setups (tablet screen aborbs energy)
_MEAN_DELTA_FLASH = 0.1  # 10%  # used for reflective chart setups
_MEAN_DELTA_TORCH = 0.05  # 5%  # used for reflective chart setups


class ParamFlashModeTest(its_base_test.ItsBaseTest):
  """Test that the android.flash.mode parameter is applied."""

  def test_param_flash_mode(self):
    logging.debug('FLASH_MODES[OFF]: %d, [SINGLE]: %d, [TORCH]: %d',
                  _FLASH_MODES['OFF'], _FLASH_MODES['SINGLE'],
                  _FLASH_MODES['TORCH'])
    logging.debug(('FLASH_STATES[UNAVAIL]: %d, [CHARGING]: %d, [READY]: %d,'
                   '[FIRED] %d, [PARTIAL]: %d'), _FLASH_STATES['UNAVAIL'],
                  _FLASH_STATES['CHARGING'], _FLASH_STATES['READY'],
                  _FLASH_STATES['FIRED'], _FLASH_STATES['PARTIAL'])

    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      log_path = self.log_path
      file_name_stem = os.path.join(log_path, _NAME)

      # check SKIP conditions
      camera_properties_utils.skip_unless(
          camera_properties_utils.compute_target_exposure(props) and
          camera_properties_utils.flash(props))

      # Load chart for scene
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet,
          its_session_utils.CHART_DISTANCE_NO_SCALING)

      modes = []
      states = []
      patches = []

      # Manually set the exposure to be a little on the dark side, so that
      # it should be obvious whether the flash fired or not, and use a
      # linear tonemap.
      largest_yuv = capture_request_utils.get_largest_yuv_format(props)
      match_ar = (largest_yuv['width'], largest_yuv['height'])
      fmt = capture_request_utils.get_near_vga_yuv_format(
          props, match_ar=match_ar)
      sync_latency = camera_properties_utils.sync_latency(props)

      e, s = target_exposure_utils.get_target_exposure_combos(
          log_path, cam)['midExposureTime']
      e /= 2  # darken image slightly
      req = capture_request_utils.manual_capture_request(s, e, 0.0, True, props)

      for flash_mode in _FLASH_MODES.values():
        logging.debug('flash mode: %d', flash_mode)
        req['android.flash.mode'] = flash_mode
        cap = its_session_utils.do_capture_with_latency(
            cam, req, sync_latency, fmt)
        modes.append(cap['metadata']['android.flash.mode'])
        states.append(cap['metadata']['android.flash.state'])
        y, _, _ = image_processing_utils.convert_capture_to_planes(cap, props)
        image_processing_utils.write_image(
            y, f'{file_name_stem}_{flash_mode}.jpg')
        patch = image_processing_utils.get_image_patch(
            y, _PATCH_X, _PATCH_Y, _PATCH_W, _PATCH_H)
        image_processing_utils.write_image(
            patch, f'{file_name_stem}_{flash_mode}_patch.jpg')
        patches.append(patch)

      # Assert state behavior
      logging.debug('Reported modes: %s', str(modes))
      logging.debug('Reported states: %s', str(states))
      if modes != list(_FLASH_MODES.values()):
        raise AssertionError(f'modes != FLASH_MODES! {modes}')

      if states[_FLASH_MODES['OFF']] in [
          _FLASH_STATES['FIRED'], _FLASH_STATES['PARTIAL']]:
        raise AssertionError('flash state reported[OFF]: '
                             f"{states[_FLASH_MODES['OFF']]}")

      if states[_FLASH_MODES['SINGLE']] not in [
          _FLASH_STATES['FIRED'], _FLASH_STATES['PARTIAL']]:
        raise AssertionError('flash state reported[SINGLE]: '
                             f"{states[_FLASH_MODES['SINGLE']]}")

      if states[_FLASH_MODES['TORCH']] not in [
          _FLASH_STATES['FIRED'], _FLASH_STATES['PARTIAL']]:
        raise AssertionError('flash state reported[TORCH]: '
                             f"{states[_FLASH_MODES['TORCH']]}")

      # Compute image behavior: change between OFF & SINGLE
      single_diff = np.subtract(patches[_FLASH_MODES['SINGLE']],
                                patches[_FLASH_MODES['OFF']])
      single_mean = image_processing_utils.compute_image_means(
          single_diff)[0]
      single_grad = image_processing_utils.compute_image_max_gradients(
          single_diff)[0]
      image_processing_utils.write_image(
          single_diff, f'{file_name_stem}_single.jpg')
      logging.debug('mean(SINGLE-OFF): %.3f', single_mean)
      logging.debug('grad(SINGLE-OFF): %.3f', single_grad)

      # Compute image behavior: change between OFF & TORCH
      torch_diff = np.subtract(patches[_FLASH_MODES['TORCH']],
                               patches[_FLASH_MODES['OFF']])
      image_processing_utils.write_image(
          torch_diff, f'{file_name_stem}_torch.jpg')
      torch_mean = image_processing_utils.compute_image_means(
          torch_diff)[0]
      torch_grad = image_processing_utils.compute_image_max_gradients(
          torch_diff)[0]
      logging.debug('mean(TORCH-OFF): %.3f', torch_mean)
      logging.debug('grad(TORCH-OFF): %.3f', torch_grad)

      # Check correct behavior
      if not (single_grad > _GRADIENT_DELTA or
              single_mean > _MEAN_DELTA_FLASH):
        raise AssertionError(f'gradient SINGLE-OFF: {single_grad:.3f}, '
                             f'ATOL: {_GRADIENT_DELTA}, '
                             f'mean SINGLE-OFF {single_mean:.3f}, '
                             f'ATOL: {_MEAN_DELTA_FLASH}')
      if not (torch_grad > _GRADIENT_DELTA or
              torch_mean > _MEAN_DELTA_TORCH):
        raise AssertionError(f'gradient TORCH-OFF: {torch_grad:.3f}, '
                             f'ATOL: {_GRADIENT_DELTA}, '
                             f'mean TORCH-OFF {torch_mean:.3f}, '
                             f'ATOL: {_MEAN_DELTA_TORCH}')

if __name__ == '__main__':
  test_runner.main()

