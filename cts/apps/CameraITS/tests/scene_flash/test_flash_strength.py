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
"""Verify manual flash strength control (SINGLE capture mode) works correctly."""

import logging
import os.path

from mobly import test_runner
import camera_properties_utils
import capture_request_utils
import image_processing_utils
import its_base_test
import its_session_utils
import lighting_control_utils

_TESTING_AE_MODES = (0, 1, 2)
_AE_MODE_FLASH_CONTROL = (0, 1)
_AE_MODES = {0: 'OFF', 1: 'ON', 2: 'ON_AUTO_FLASH', 3: 'ON_ALWAYS_FLASH',
             4: 'ON_AUTO_FLASH_REDEYE', 5: 'ON_EXTERNAL_FLASH'}
_AE_STATES = {0: 'INACTIVE', 1: 'SEARCHING', 2: 'CONVERGED', 3: 'LOCKED',
              4: 'FLASH_REQUIRED', 5: 'PRECAPTURE'}
_FLASH_STATES = {0: 'FLASH_STATE_UNAVAILABLE', 1: 'FLASH_STATE_CHARGING',
                 2: 'FLASH_STATE_READY', 3: 'FLASH_STATE_FIRED',
                 4: 'FLASH_STATE_PARTIAL'}
_FORMAT_NAME = 'yuv'
_IMG_SIZE = (640, 480)
_PATCH_H = 0.25  # center 25%
_PATCH_W = 0.25
_PATCH_X = 0.5-_PATCH_W/2
_PATCH_Y = 0.5-_PATCH_H/2
_TEST_NAME = os.path.splitext(os.path.basename(__file__))[0]
_CAPTURE_INTENT_STILL_CAPTURE = 2
_MAX_FLASH_STRENGTH = 'android.flash.singleStrengthMaxLevel'
_MAX_TORCH_STRENGTH = 'android.flash.torchStrengthMaxLevel'
_BRIGHTNESS_MEAN_TOL = 5  # Tolerance for brightness mean
_STRENGTH_STEPS = 3  # Steps of flash strengths to be tested


def _take_captures(out_surfaces, cam, img_name, ae_mode, strength=0):
  """Takes captures and returns the captured image.

  Args:
    out_surfaces: list; valid output surfaces for caps.
    cam: ItsSession util object.
    img_name: image name to be saved.
    ae_mode: AE mode to be tested with.
    strength: Flash strength that flash should be fired with.
      Note that 0 is for baseline capture.

  Returns:
    cap: captured image object as defined by
      ItsSessionUtils.do_capture().
  """
  cam.do_3a(do_af=False)
  # Take base image without flash
  if strength == 0:
    cap_req = capture_request_utils.auto_capture_request()
    cap_req[
        'android.control.captureIntent'] = _CAPTURE_INTENT_STILL_CAPTURE
    cap_req['android.control.aeMode'] = 0
    cap = cam.do_capture(cap_req, out_surfaces)
  # Take capture with flash strength
  else:
    cap = capture_request_utils.take_captures_with_flash_strength(
        cam, out_surfaces, ae_mode, strength)

  img = image_processing_utils.convert_capture_to_rgb_image(cap)
  # Save captured image
  image_processing_utils.write_image(img, img_name)
  return cap


def _get_mean(cap, props):
  """Evaluate captured image by extracting means in the center patch.

  Args:
    cap: captured image object as defined by
      ItsSessionUtils.do_capture().
    props: Camera properties object.

  Returns:
    mean: (float64) calculated mean of image center patch.
  """
  metadata = cap['metadata']
  exp = int(metadata['android.sensor.exposureTime'])
  iso = int(metadata['android.sensor.sensitivity'])
  flash_exp_x_iso = []
  logging.debug('cap ISO: %d, exp: %d ns', iso, exp)
  logging.debug('AE_MODE (cap): %s',
                _AE_MODES[metadata['android.control.aeMode']])
  ae_state = _AE_STATES[metadata['android.control.aeState']]
  logging.debug('AE_STATE (cap): %s', ae_state)
  flash_state = _FLASH_STATES[metadata['android.flash.state']]
  logging.debug('FLASH_STATE: %s', flash_state)

  flash_exp_x_iso = exp*iso
  y, _, _ = image_processing_utils.convert_capture_to_planes(
      cap, props)
  patch = image_processing_utils.get_image_patch(
      y, _PATCH_X, _PATCH_Y, _PATCH_W, _PATCH_H)
  flash_mean = image_processing_utils.compute_image_means(
      patch)[0]*255
  flash_grad = image_processing_utils.compute_image_max_gradients(
      patch)[0]*255

  # log results
  logging.debug('Flash exposure X ISO %d', flash_exp_x_iso)
  logging.debug('Flash frames Y grad: %.4f', flash_grad)
  logging.debug('Flash frames Y mean: %.4f', flash_mean)
  return flash_mean


def _compare_means(formats_means, ae_mode, flash_strengths):
  """Analyzes test results and generates failure messages.

  If AE_MODE is ON/OFF, capture should show mean differences
  in flash strengths. If AE_MODE is ON_AUTO_FLASH, flash
  strength should be overwritten hence no mean difference in captures.

  Args:
    formats_means: list of calculated means of image center patches.
    ae_mode: requested AE mode during testing.
    flash_strengths: list of flash strength values requested during testing.

  Returns:
    failure_messages: (list of string) list of error messages.
  """
  failure_messages = []
  if ae_mode in _AE_MODE_FLASH_CONTROL:
    for mean in range(1, len(formats_means)-1):
      if formats_means[mean] >= formats_means[mean+1]:
        msg = (
            f'Capture with AE_CONTROL_MODE OFF/ON. '
            f'{flash_strengths[mean]} mean: {formats_means[mean]}, '
            f'{flash_strengths[mean+1]} mean: '
            f'{formats_means[mean+1]}. '
            f'{flash_strengths[mean+1]} should be brighter than '
            f'{flash_strengths[mean]}'
        )
        failure_messages.append(msg)
  else:
    for mean in range(1, len(formats_means)-1):
      diff = abs(formats_means[mean] - formats_means[mean+1])
      if diff > _BRIGHTNESS_MEAN_TOL:
        msg = (
            f'Capture with AE_CONTROL_MODE ON_AUTO_FLASH. '
            f'{flash_strengths[mean]} mean: {formats_means[mean]}, '
            f'{flash_strengths[mean+1]} mean: '
            f'{formats_means[mean+1]}. '
            f'Diff: {diff}; TOL: {_BRIGHTNESS_MEAN_TOL}'
        )
        failure_messages.append(msg)
  return failure_messages


class FlashStrengthTest(its_base_test.ItsBaseTest):
  """Test if flash strength control (SINGLE capture mode) feature works as intended."""

  def test_flash_strength(self):
    name_with_path = os.path.join(self.log_path, _TEST_NAME)

    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)

      # check SKIP conditions
      vendor_api_level = its_session_utils.get_vendor_api_level(
          self.dut.serial)
      max_flash_strength = props[_MAX_FLASH_STRENGTH]
      max_torch_strength = props[_MAX_TORCH_STRENGTH]
      camera_properties_utils.skip_unless(
          camera_properties_utils.flash(props) and
          vendor_api_level >= its_session_utils.ANDROID15_API_LEVEL and
          max_flash_strength > 1 and max_torch_strength > 1)
      # establish connection with lighting controller
      arduino_serial_port = lighting_control_utils.lighting_control(
          self.lighting_cntl, self.lighting_ch)

      # turn OFF lights to darken scene
      lighting_control_utils.set_lighting_state(
          arduino_serial_port, self.lighting_ch, 'OFF')

      failure_messages = []
      # list with no flash (baseline), linear strength steps, max strength
      flash_strengths = [max_flash_strength*i/_STRENGTH_STEPS for i in
                         range(_STRENGTH_STEPS)]
      flash_strengths.append(max_flash_strength)
      logging.debug('Testing flash strengths: %s', flash_strengths)
      # loop through ae modes to be tested
      for ae_mode in _TESTING_AE_MODES:
        formats_means = []
        # loop through flash strengths
        for strength in flash_strengths:
          if 0 < strength <= 1:
            logging.debug('Flash strength value <=1, test case ignored')
          else:
            # naming images to be captured
            img_name = f'{name_with_path}_ae_mode={ae_mode}_flash_strength={strength}.jpg'
            # defining out_surfaces
            width, height = _IMG_SIZE
            out_surfaces = {'format': _FORMAT_NAME,
                            'width': width, 'height': height}
            # take capture and evaluate
            cap = _take_captures(out_surfaces, cam, img_name, ae_mode, strength)
            formats_means.append(_get_mean(cap, props))

        # Compare means and assert PASS/FAIL
        failure_messages += _compare_means(formats_means,
                                           ae_mode, flash_strengths)

    # turn the lights back on
    lighting_control_utils.set_lighting_state(
        arduino_serial_port, self.lighting_ch, 'ON')
    if failure_messages:
      raise AssertionError('\n'.join(failure_messages))

if __name__ == '__main__':
  test_runner.main()
