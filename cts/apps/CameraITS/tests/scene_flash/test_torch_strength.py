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
"""Verify flash strength control in TORCH mode works correctly during camera use."""

import logging
import os.path

from mobly import test_runner
import numpy as np
import its_base_test
import camera_properties_utils
import capture_request_utils
import image_processing_utils
import its_session_utils
import lighting_control_utils

_AE_MODE_FLASH_CONTROL = (0, 1)
_AE_MODES = {0: 'OFF', 1: 'ON', 2: 'ON_AUTO_FLASH', 3: 'ON_ALWAYS_FLASH',
             4: 'ON_AUTO_FLASH_REDEYE', 5: 'ON_EXTERNAL_FLASH'}
_AE_STATES = {0: 'INACTIVE', 1: 'SEARCHING', 2: 'CONVERGED', 3: 'LOCKED',
              4: 'FLASH_REQUIRED', 5: 'PRECAPTURE'}
_BRIGHTNESS_MEAN_ATOL = 5  # Tolerance for brightness mean
_BURST_LEN = 5
_CAPTURE_INTENT_PREVIEW = 1
_CAPTURE_INTENT_STILL_CAPTURE = 2
_FLASH_STATES = {0: 'FLASH_STATE_UNAVAILABLE', 1: 'FLASH_STATE_CHARGING',
                 2: 'FLASH_STATE_READY', 3: 'FLASH_STATE_FIRED',
                 4: 'FLASH_STATE_PARTIAL'}
_FORMAT_NAME = 'yuv'
_IMG_SIZE = (640, 360)
_MAX_SINGLE_STRENGTH_PROP_KEY = 'android.flash.singleStrengthMaxLevel'
_MAX_TORCH_STRENGTH_PROP_KEY = 'android.flash.torchStrengthMaxLevel'
_PATCH_H = 0.25  # center 25%
_PATCH_W = 0.25
_PATCH_X = 0.5-_PATCH_W/2
_PATCH_Y = 0.5-_PATCH_H/2
_SINGLE_STRENGTH_CONTROL_THRESHOLD = 1
_STRENGTH_STEPS = 3  # Steps of flash strengths to be tested
_TEST_NAME = os.path.splitext(os.path.basename(__file__))[0]
_TESTING_AE_MODES = (0, 1, 2)
_TORCH_MODE = 2
_TORCH_STRENGTH_CONTROL_THRESHOLD = 1
_TORCH_STRENGTH_MIN = 0


# TODO: b/327018456 - Centralize flash_strength functions in utils
def _take_captures(out_surfaces, cam, img_name_prefix, ae_mode, torch_strength):
  """Takes video captures and returns the captured images.

  Args:
    out_surfaces: list; valid output surfaces for caps.
    cam: ItsSession util object.
    img_name_prefix: image name to be saved, log_path included.
    ae_mode: AE mode to be tested with.
    torch_strength: Flash strength that flash should be fired with.
      Note that 0 is for baseline capture.

  Returns:
    caps: list of capture objects as described by cam.do_capture().
  """
  cam.do_3a(do_af=False)
  # Take base image without flash
  if torch_strength == 0:
    cap_req = capture_request_utils.auto_capture_request()
    cap_req[
        'android.control.captureIntent'] = _CAPTURE_INTENT_STILL_CAPTURE
    cap_req['android.control.aeMode'] = 0  # AE_MODE_OFF
    cap = cam.do_capture(cap_req, out_surfaces)
    return [cap]

  # Take multiple still captures with torch strength
  else:
    cap_req = capture_request_utils.auto_capture_request()
    cap_req['android.control.aeMode'] = ae_mode
    cap_req['android.control.captureIntent'] = _CAPTURE_INTENT_PREVIEW
    cap_req['android.control.aeLock'] = True
    cap_req['android.flash.mode'] = _TORCH_MODE
    cap_req['android.flash.strengthLevel'] = torch_strength
    reqs = [cap_req] * _BURST_LEN
    caps = cam.do_capture(reqs, out_surfaces)
    for i, cap in enumerate(caps):
      img = image_processing_utils.convert_capture_to_rgb_image(cap)
      # Save captured image
      image_processing_utils.write_image(img, f'{img_name_prefix}{i}.jpg')
    return caps


def _get_img_patch_mean(caps, props):
  """Evaluate captured image by extracting means in the center patch.

  Args:
    caps: captured list of image object as defined by
      ItsSessionUtils.do_capture().
    props: Camera properties object.

  Returns:
    mean: (list of float64) calculated means of Y plane center patch.
  """
  flash_means = []
  for cap in caps:
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
    logging.debug('FLASH_STRENGTH: %s', metadata['android.flash.strengthLevel'])

    flash_exp_x_iso = exp*iso
    y, _, _ = image_processing_utils.convert_capture_to_planes(
        cap, props)
    patch = image_processing_utils.get_image_patch(
        y, _PATCH_X, _PATCH_Y, _PATCH_W, _PATCH_H)
    flash_mean = image_processing_utils.compute_image_means(
        patch)[0]*255
    flash_means.append(flash_mean)

    logging.debug('Flash exposure X ISO %d', flash_exp_x_iso)
    logging.debug('Flash frames Y mean: %.4f', flash_mean)
  return flash_means


def _compare_means(formats_means, ae_mode, flash_strengths):
  """Compares the means of the captured images at different strength levels.

  If AE_MODE is ON/OFF, capture should show mean differences
  in flash strengths. If AE_MODE is ON_AUTO_FLASH, flash
  strength should be overwritten hence no mean difference in captures.

  Args:
    formats_means: list of calculated means of image center patches of all req.
    ae_mode: requested AE mode during testing.
    flash_strengths: list of flash strength values requested during testing.

  Returns:
    failure_messages: (list of string) list of error messages.
  """

  failure_messages = []
  strength_means = [np.average(x) for x in formats_means]
  # Intentionally omitting frame-to-frame sameness check of last burst
  for i, burst_means in enumerate(formats_means[:-1]):
    # Check for strength brightness with averages of same strength captures
    if (strength_means[i] >= strength_means[i+1] and
        ae_mode in _AE_MODE_FLASH_CONTROL):
      msg = (
          f'Capture with AE_CONTROL_MODE OFF/ON. AE_MODE: {ae_mode}; '
          f'Strength {flash_strengths[i]} mean: {strength_means[i]}; '
          f'Strength {flash_strengths[i+1]} mean: {strength_means[i+1]}; '
          f'Mean of {flash_strengths[i+1]} should be brighter than '
          f'Mean of {flash_strengths[i]}!'
      )
      failure_messages.append(msg)
    for j in range(len(burst_means)-1):
      # Check for frame-to-frame sameness
      diff = abs(burst_means[j] - burst_means[j+1])
      if diff > _BRIGHTNESS_MEAN_ATOL:
        if ae_mode in _AE_MODE_FLASH_CONTROL:
          msg = (
              f'Capture with AE_CONTROL_MODE OFF/ON. AE_MODE: {ae_mode}; '
              f'Strength {flash_strengths[i]} capture {j} mean: '
              f'{burst_means[j]},'
              f'Strength {flash_strengths[i+1]} capture {j+1} mean: '
              f'{burst_means[j+1]}, '
              f'Torch strength is not consistent between captures '
              f'Diff: {diff}; TOL: {_BRIGHTNESS_MEAN_ATOL}'
          )
        else:
          msg = (
              f'Capture with AE_CONTROL_MODE ON_AUTO_FLASH. '
              f'Strength {flash_strengths[i]} mean: {burst_means[j]}, '
              f'Strength {flash_strengths[i+1]} mean: '
              f'{burst_means[j+1]}. '
              f'Diff: {diff}; TOL: {_BRIGHTNESS_MEAN_ATOL}'
          )
        failure_messages.append(msg)

  return failure_messages


class TorchStrengthTest(its_base_test.ItsBaseTest):
  """Test if torch strength control feature works as intended."""

  def test_torch_strength(self):
    name_with_path = os.path.join(self.log_path, _TEST_NAME)

    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)

      # check SKIP conditions
      first_api_level = its_session_utils.get_first_api_level(
          self.dut.serial)
      max_flash_strength = props[_MAX_SINGLE_STRENGTH_PROP_KEY]
      max_torch_strength = props[_MAX_TORCH_STRENGTH_PROP_KEY]
      camera_properties_utils.skip_unless(
          camera_properties_utils.flash(props) and
          first_api_level >= its_session_utils.ANDROID15_API_LEVEL and
          max_flash_strength > _SINGLE_STRENGTH_CONTROL_THRESHOLD and
          max_torch_strength > _TORCH_STRENGTH_CONTROL_THRESHOLD)

      # establish connection with lighting controller
      arduino_serial_port = lighting_control_utils.lighting_control(
          self.lighting_cntl, self.lighting_ch)

      # turn OFF lights to darken scene
      lighting_control_utils.set_lighting_state(
          arduino_serial_port, self.lighting_ch, 'OFF')

      failure_messages = []
      # testing at 80% of max strength
      max_torch_strength = max_torch_strength * 0.8
      # list with no torch (baseline), linear strength steps, 0.8 max strength
      torch_strengths = [max_torch_strength*i/_STRENGTH_STEPS for i in
                         range(_STRENGTH_STEPS)]
      torch_strengths.append(max_torch_strength)
      logging.debug('Testing flash strengths: %s', torch_strengths)
      for ae_mode in _TESTING_AE_MODES:
        formats_means = []
        for strength in torch_strengths:
          if (_TORCH_STRENGTH_MIN < strength <=
              _TORCH_STRENGTH_CONTROL_THRESHOLD):
            logging.debug('Torch strength value <= %d, test case ignored',
                          _TORCH_STRENGTH_CONTROL_THRESHOLD)
          else:
            # naming images to be captured
            img_name_prefix = (
                f'{name_with_path}_ae_mode={ae_mode}_'
                f'torch_strength={strength}_'
            )
            # defining out_surfaces
            width, height = _IMG_SIZE
            out_surfaces = {'format': _FORMAT_NAME,
                            'width': width, 'height': height}
            # take capture and evaluate
            caps = _take_captures(
                out_surfaces, cam, img_name_prefix, ae_mode, strength)
            formats_means.append(_get_img_patch_mean(caps, props))

        # Compare means and compose failure messages
        failure_messages += _compare_means(formats_means,
                                           ae_mode, torch_strengths)

    # turn the lights back on
    lighting_control_utils.set_lighting_state(
        arduino_serial_port, self.lighting_ch, 'ON')

    # assert correct behavior and print error message(s)
    if failure_messages:
      raise AssertionError('\n'.join(failure_messages))

if __name__ == '__main__':
  test_runner.main()

