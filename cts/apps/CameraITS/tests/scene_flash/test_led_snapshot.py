# Copyright 2023 The Android Open Source Project
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
"""Verify that the LED snapshot works correctly."""

import logging
import os.path

import camera_properties_utils
import capture_request_utils
import image_processing_utils
import its_base_test
import its_session_utils
import lighting_control_utils
from mobly import test_runner

_AE_MODES = {0: 'OFF', 1: 'ON', 2: 'ON_AUTO_FLASH', 3: 'ON_ALWAYS_FLASH',
             4: 'ON_AUTO_FLASH_REDEYE', 5: 'ON_EXTERNAL_FLASH'}
_AE_STATES = {0: 'INACTIVE', 1: 'SEARCHING', 2: 'CONVERGED', 3: 'LOCKED',
              4: 'FLASH_REQUIRED', 5: 'PRECAPTURE'}
_FLASH_STATES = {0: 'FLASH_STATE_UNAVAILABLE', 1: 'FLASH_STATE_CHARGING',
                 2: 'FLASH_STATE_READY', 3: 'FLASH_STATE_FIRED',
                 4: 'FLASH_STATE_PARTIAL'}
_FORMAT_NAMES = ('jpeg', 'yuv')
_IMG_SIZES = ((640, 480), (640, 360))
_VGA_SIZE = (640, 480)
_CH_FULL_SCALE = 255
_TEST_NAME = os.path.splitext(os.path.basename(__file__))[0]
_AE_MODE_ON_AUTO_FLASH = 2
_CAPTURE_INTENT_PREVIEW = 1
_CAPTURE_INTENT_STILL_CAPTURE = 2
_AE_PRECAPTURE_TRIGGER_START = 1
_AE_PRECAPTURE_TRIGGER_IDLE = 0
_FLASH_MEAN_MIN = 50
_FLASH_MEAN_MAX = 200
_WB_MIN = 0.8
_WB_MAX = 1.2
_COLOR_CHANNELS = ('R', 'G', 'B')


def _take_captures(out_surfaces, cam, img_name, flash=False):
  """Takes captures and returns the captured image.

  Args:
    out_surfaces:
    cam: ItsSession util object
    img_name: image name to be saved.
    flash: True if the capture needs to be taken with Flash ON

  Returns:
    cap: captured image object as defined by
    ItsSessionUtils.do_capture()
  """
  cam.do_3a(do_af=False)
  if not flash:
    cap_req = capture_request_utils.auto_capture_request()
    cap_req[
        'android.control.captureIntent'] = _CAPTURE_INTENT_STILL_CAPTURE
    cap = cam.do_capture(cap_req, out_surfaces)
  else:
    cap = capture_request_utils.take_captures_with_flash(cam, out_surfaces)

  img = image_processing_utils.convert_capture_to_rgb_image(cap)
  # Save captured image
  image_processing_utils.write_image(img, img_name)
  return cap


def _is_color_mean_valid(means, color_channel, fmt_name, width, height):
  """Checks if the mean for color_channel is within the range.

  Computes means for color_channel specified and checks whether
  it is within the acceptable range.
  Args:
    means: list of means in float
    color_channel: String; values must be one of the color
      channels in _COLOR_CHANNELS
    fmt_name: Format to be tested
    width: width of the image to be tested
    height: height of the image to be tested

  Returns:
    True if the color mean is within the range and returns False
    if invalid.
  """
  if color_channel not in _COLOR_CHANNELS:
    raise AssertionError('Invalid color_channel.')

  if color_channel == 'R':
    color_mean = means[0]
  elif color_channel == 'G':
    color_mean = means[1]
  else:
    color_mean = means[2]

  if not _FLASH_MEAN_MIN <= color_mean <= _FLASH_MEAN_MAX:
    logging.debug('Flash image mean %s not'
                  ' within limits for channel %s.'
                  ' Format: %s,'
                  ' Size: %sx%s', color_mean, color_channel,
                  fmt_name, width, height)
    return False
  else:
    return True


class LedSnapshotTest(its_base_test.ItsBaseTest):
  """Tests if LED snapshot works correctly.

  In this test we capture the failure that the LED snapshot is not too dark,
  too bright or producing a strange color tint.

  During the test 3 images are captured for each format in _FORMAT_NAMES
  and size in _IMG_SIZES:
  1. Lights ON, AUTO_FLASH set to OFF -> Baseline capture without any flash.
  2. Lights OFF, AUTO_FLASH set to OFF -> Ensures dark lighting conditions
     to trigger the flash.
  3. Lights OFF, AUTO_FLASH set to ON -> Still capture with flash

  For all the 3 pictures we compute the image means and log them.
  For the capture with flash triggered, we compare the mean to be within the
  minimum and maximum threshold level. The capture with flash should not be too
  dark or too bright.
  In order to ensure the white balance, the ratio of R/G and B/G is also
  compared to be within the pre-decided threshold level.
  Failures will be reported if any of the measuremenet is out of range.
  """

  def test_led_snapshot(self):
    test_name = os.path.join(self.log_path, _TEST_NAME)

    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)

      # check SKIP conditions
      first_api_level = its_session_utils.get_first_api_level(
          self.dut.serial)
      camera_properties_utils.skip_unless(
          camera_properties_utils.flash(props) and
          first_api_level >= its_session_utils.ANDROID14_API_LEVEL)
      failure_messages = []
      # establish connection with lighting controller
      arduino_serial_port = lighting_control_utils.lighting_control(
          self.lighting_cntl, self.lighting_ch)
      for fmt_name in _FORMAT_NAMES:
        for size in _IMG_SIZES:
          width, height = size
          if not (fmt_name == 'yuv' and size == _VGA_SIZE):
            output_sizes = capture_request_utils.get_available_output_sizes(
                fmt_name, props, match_ar_size=size)
            if not output_sizes:
              if size != _VGA_SIZE:
                logging.debug('No output sizes for format %s, size %sx%s',
                              fmt_name, width, height)
                continue
              else:
                raise AssertionError(f'No output sizes for format {fmt_name}, '
                                     f'size {width}x{height}')
            # pick smallest size out of available output sizes
            width, height = output_sizes[-1]

          out_surfaces = {'format': fmt_name, 'width': width, 'height': height}
          logging.debug(
              'Testing %s format, size: %dx%d', fmt_name, width, height)

          # take capture with lights on - no flash
          logging.debug(
              'Taking reference frame with lights on and no flash.')
          img_prefix = f'{test_name}_{fmt_name}_{width}x{height}'
          light_on_img_name = f'{img_prefix}_lights_on.jpg'
          _take_captures(out_surfaces, cam, light_on_img_name, flash=False)

          # turn OFF lights to darken scene
          lighting_control_utils.set_lighting_state(
              arduino_serial_port, self.lighting_ch, 'OFF')

          # take capture with no flash as baseline
          logging.debug(
              'Taking reference frame with lights off and no auto-flash.')
          no_flash_req = capture_request_utils.auto_capture_request()
          no_flash_req[
              'android.control.captureIntent'] = _CAPTURE_INTENT_STILL_CAPTURE
          no_flash_img_name = f'{img_prefix}_no_flash.jpg'
          _take_captures(out_surfaces, cam, no_flash_img_name, flash=False)

          # take capture with auto flash enabled
          logging.debug('Taking capture with auto flash enabled.')
          flash_fired = False
          flash_img_name = f'{img_prefix}_flash.jpg'
          cap = _take_captures(out_surfaces, cam, flash_img_name, flash=True)
          img = image_processing_utils.convert_capture_to_rgb_image(cap)

          # evaluate captured image
          metadata = cap['metadata']
          exp = int(metadata['android.sensor.exposureTime'])
          iso = int(metadata['android.sensor.sensitivity'])
          logging.debug('cap ISO: %d, exp: %d ns', iso, exp)
          logging.debug('AE_MODE (cap): %s',
                        _AE_MODES[metadata['android.control.aeMode']])
          ae_state = _AE_STATES[metadata['android.control.aeState']]
          logging.debug('AE_STATE (cap): %s', ae_state)
          flash_state = _FLASH_STATES[metadata['android.flash.state']]
          logging.debug('FLASH_STATE: %s', flash_state)
          if flash_state == 'FLASH_STATE_FIRED':
            logging.debug('Flash fired')
            flash_fired = True
            flash_means = image_processing_utils.compute_image_means(img)
            logging.debug('Image means with flash: %s', flash_means)
            flash_means = [i * _CH_FULL_SCALE for i in flash_means]
            logging.debug('Flash capture rgb means: %s', flash_means)

            # Verify that R/G and B/G ratios are within the limits
            r_g_ratio = flash_means[0]/ flash_means[1]
            logging.debug('R/G ratio: %s fmt: %s, WxH: %sx%s',
                          r_g_ratio, fmt_name, width, height)
            b_g_ratio = flash_means[2]/flash_means[1]
            logging.debug('B/G ratio: %s fmt: %s, WxH: %sx%s',
                          b_g_ratio, fmt_name, width, height)

            if not _WB_MIN <= r_g_ratio <= _WB_MAX:
              failure_messages.append(f'R/G ratio: {r_g_ratio} not within'
                                      f' the limits. Format: {fmt_name},'
                                      f' Size: {width}x{height}')
            if not _WB_MIN <= b_g_ratio <= _WB_MAX:
              failure_messages.append(f'B/G ratio: {r_g_ratio} not within'
                                      f' the limits. Format: {fmt_name},'
                                      f' Size: {width}x{height}')

            # Check whether the image means for each color channel is
            # within the limits or not.
            valid_color = True
            for color in _COLOR_CHANNELS:
              valid_color = _is_color_mean_valid(flash_means, color,
                                                 fmt_name, width, height)
              if not valid_color:
                failure_messages.append(
                    f'Flash image mean not within limits for channel {color}.'
                    f' Format: {fmt_name},Size: {width}x{height}')

          if not flash_fired:
            raise AssertionError(
                'Flash was not fired. Format:{fmt_name}, Size:{width}x{height}')

          # turn the lights back on
          lighting_control_utils.set_lighting_state(
              arduino_serial_port, self.lighting_ch, 'ON')

      # assert correct behavior for all formats
      if failure_messages:
        raise AssertionError('\n'.join(failure_messages))

if __name__ == '__main__':
  test_runner.main()
