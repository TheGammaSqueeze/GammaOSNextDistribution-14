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
"""Verify HDR is activated correctly for extension captures."""


import logging
import os.path
import time

import cv2
from mobly import test_runner
import numpy as np
from scipy import ndimage

import its_base_test
import camera_properties_utils
import capture_request_utils
import image_processing_utils
import its_session_utils
import lighting_control_utils
import opencv_processing_utils

_NAME = os.path.splitext(os.path.basename(__file__))[0]
_EXTENSION_HDR = 3
_TABLET_BRIGHTNESS = '12'  # Highest minimum brightness on a supported tablet

_FMT_NAME = 'jpg'
_WIDTH = 1920
_HEIGHT = 1080

_MIN_QRCODE_AREA = 0.01  # Reject squares smaller than 1% of image
_QR_CODE_VALUE = 'CameraITS'
_CONTRAST_ARANGE = (1, 10, 0.01)
_CONTOUR_INDEX = -1  # Draw all contours as per opencv convention
_BGR_RED = (0, 0, 255)
_CONTOUR_LINE_THICKNESS = 3

_DURATION_DIFF_TOL = 0.5  # HDR ON captures must take 0.5 seconds longer
_GRADIENT_TOL = 0.15  # Largest HDR gradient must be at most 15% of non-HDR


def extract_tile(img, file_stem_with_suffix):
  """Extracts a white square from an image and processes it for analysis.

  Args:
    img: An RGB image
    file_stem_with_suffix: Filename describing image format and HDR activation.
  Returns:
    openCV image representing the QR code
  """
  img *= 255  # openCV needs [0:255] images
  square = opencv_processing_utils.find_white_square(
      img, _MIN_QRCODE_AREA)
  tile = image_processing_utils.get_image_patch(
      img,
      square['left']/img.shape[1],
      square['top']/img.shape[0],
      square['w']/img.shape[1],
      square['h']/img.shape[0]
  )
  tile = tile.astype(np.uint8)
  tile = tile[:, :, ::-1]  # RGB --> BGR for cv2
  tile = cv2.cvtColor(tile, cv2.COLOR_BGR2GRAY)  # Convert to grayscale

  # Rotate tile to reduce scene variation
  h, w = tile.shape[:2]
  center_x, center_y = w // 2, h // 2
  rotation_matrix = cv2.getRotationMatrix2D((center_x, center_y),
                                            square['angle'], 1.0)
  tile = cv2.warpAffine(tile, rotation_matrix, (w, h))
  cv2.imwrite(f'{file_stem_with_suffix}_tile.png', tile)
  return tile


def analyze_qr_code(img, file_stem_with_suffix):
  """Analyze gradient across ROI and detect/decode its QR code from an image.

  Attempts to detect and decode a QR code from the image represented by img,
  after converting to grayscale and rotating the code to be in line with
  the x and y axes. Then, if even detection fails, modifies the contrast of
  the image until the QR code is detectable. Measures the gradient across
  the code by finding the length of the largest contour found by openCV.

  Args:
    img: An RGB image
    file_stem_with_suffix: Filename describing image format and HDR activation.

  Returns:
    detection_object: Union[str, bool], describes decoded data or detection
    lowest_successful_alpha: float, contrast where QR code was detected/decoded
    contour_length: int, length of largest contour in gradient image
  """
  tile = extract_tile(img, file_stem_with_suffix)

  # Find gradient
  sobel_x = ndimage.sobel(tile, axis=0, mode='constant')
  sobel_y = ndimage.sobel(tile, axis=1, mode='constant')
  sobel = np.float32(np.hypot(sobel_x, sobel_y))

  # Find largest contour in gradient image
  contour = max(
      opencv_processing_utils.find_all_contours(np.uint8(sobel)), key=len)
  contour_length = len(contour)

  # Draw contour (need a color image for visibility)
  sobel_bgr = cv2.cvtColor(sobel, cv2.COLOR_GRAY2BGR)
  contour_image = cv2.drawContours(sobel_bgr, contour, _CONTOUR_INDEX,
                                   _BGR_RED, _CONTOUR_LINE_THICKNESS)
  cv2.imwrite(f'{file_stem_with_suffix}_sobel_contour.png', contour_image)

  # Try to detect QR code
  detection_object = None
  lowest_successful_alpha = None
  qr_detector = cv2.QRCodeDetector()

  # See if original tile is detectable
  qr_code, _, _ = qr_detector.detectAndDecode(tile)
  if qr_code and qr_code == _QR_CODE_VALUE:
    logging.debug('Decoded correct QR code: %s without contrast changes',
                  _QR_CODE_VALUE)
    return qr_code, 0.0, contour_length
  else:
    qr_code, _ = qr_detector.detect(tile)
    if qr_code:
      detection_object = qr_code
      lowest_successful_alpha = 0.0
      logging.debug('Detected QR code without contrast changes')

  # Modify contrast (not brightness) to see if QR code detectable/decodable
  for a in np.arange(*_CONTRAST_ARANGE):
    qr_tile = cv2.convertScaleAbs(tile, alpha=a, beta=0)
    qr_code, _, _ = qr_detector.detectAndDecode(qr_tile)
    if qr_code and qr_code == _QR_CODE_VALUE:
      logging.debug('Decoded correct QR code: %s at alpha of %.2f',
                    _QR_CODE_VALUE, a)
      return qr_code, a, contour_length
    elif qr_code:
      logging.debug('Decoded other QR code: %s', qr_code)
    else:
      # If QR code already detected, only try to decode.
      if detection_object:
        continue
      qr_code, _ = qr_detector.detect(qr_tile)
      if qr_code:
        logging.debug('Detected QR code at alpha of %.2f', a)
        detection_object = qr_code
        lowest_successful_alpha = a

  return detection_object, lowest_successful_alpha, contour_length


class HdrExtensionTest(its_base_test.ItsBaseTest):
  """Tests HDR extension under dark lighting conditions.

  Takes capture with and without HDR extension activated.
  Verifies that QR code on the right is lit evenly,
  or can be decoded/detected with the HDR extension on.
  """

  def test_hdr(self):
    # Handle subdirectory
    self.scene = 'scene_hdr'
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      test_name = os.path.join(self.log_path, _NAME)

      # Determine camera supported extensions
      supported_extensions = cam.get_supported_extensions(self.camera_id)
      logging.debug('Supported extensions: %s', supported_extensions)

      # Check SKIP conditions
      first_api_level = its_session_utils.get_first_api_level(self.dut.serial)
      camera_properties_utils.skip_unless(
          _EXTENSION_HDR in supported_extensions and
          first_api_level >= its_session_utils.ANDROID14_API_LEVEL)

      # Establish connection with lighting controller
      arduino_serial_port = lighting_control_utils.lighting_control(
          self.lighting_cntl, self.lighting_ch)

      # Turn OFF lights to darken scene
      lighting_control_utils.set_lighting_state(
          arduino_serial_port, self.lighting_ch, 'OFF')

      # Check that tablet is connected and turn it off to validate lighting
      self.turn_off_tablet()

      # Validate lighting
      cam.do_3a(do_af=False)
      cap = cam.do_capture(
          capture_request_utils.auto_capture_request(), cam.CAP_YUV)
      y_plane, _, _ = image_processing_utils.convert_capture_to_planes(cap)
      its_session_utils.validate_lighting(
          y_plane, self.scene, state='OFF', log_path=self.log_path,
          tablet_state='OFF')

      self.setup_tablet()
      self.set_screen_brightness(_TABLET_BRIGHTNESS)

      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet, self.chart_distance,
          lighting_check=False, log_path=self.log_path)

      file_stem = f'{test_name}_{_FMT_NAME}_{_WIDTH}x{_HEIGHT}'

      # Take capture without HDR extension activated as baseline
      logging.debug('Taking capture without HDR extension')
      out_surfaces = {'format': _FMT_NAME, 'width': _WIDTH, 'height': _HEIGHT}
      cam.do_3a()
      req = capture_request_utils.auto_capture_request()
      no_hdr_start_of_capture = time.time()
      no_hdr_cap = cam.do_capture(req, out_surfaces)
      no_hdr_end_of_capture = time.time()
      no_hdr_capture_duration = no_hdr_end_of_capture - no_hdr_start_of_capture
      logging.debug('no HDR cap duration: %.2f', no_hdr_capture_duration)
      logging.debug('no HDR cap metadata: %s', no_hdr_cap['metadata'])
      no_hdr_img = image_processing_utils.convert_capture_to_rgb_image(
          no_hdr_cap)
      image_processing_utils.write_image(
          no_hdr_img, f'{file_stem}_no_HDR.jpg')

      # Take capture with HDR extension
      logging.debug('Taking capture with HDR extension')
      out_surfaces = {'format': _FMT_NAME, 'width': _WIDTH, 'height': _HEIGHT}
      cam.do_3a()
      req = capture_request_utils.auto_capture_request()
      hdr_start_of_capture = time.time()
      hdr_cap = cam.do_capture_with_extensions(
          req, _EXTENSION_HDR, out_surfaces)
      hdr_end_of_capture = time.time()
      hdr_capture_duration = hdr_end_of_capture - hdr_start_of_capture
      logging.debug('HDR cap duration: %.2f', hdr_capture_duration)
      logging.debug('HDR cap metadata: %s', hdr_cap['metadata'])
      hdr_img = image_processing_utils.convert_capture_to_rgb_image(
          hdr_cap)
      image_processing_utils.write_image(hdr_img, f'{file_stem}_HDR.jpg')

      # Attempt to decode QR code with and without HDR
      format_optional_float = lambda x: f'{x:.2f}' if x is not None else 'None'
      logging.debug('Attempting to detect and decode QR code without HDR')
      no_hdr_detection_object, no_hdr_alpha, no_hdr_length = analyze_qr_code(
          no_hdr_img, f'{file_stem}_no_HDR')
      logging.debug('No HDR code: %s, No HDR alpha: %s, '
                    'No HDR contour length: %d',
                    no_hdr_detection_object,
                    format_optional_float(no_hdr_alpha),
                    no_hdr_length)
      logging.debug('Attempting to detect and decode QR code with HDR')
      hdr_detection_object, hdr_alpha, hdr_length = analyze_qr_code(
          hdr_img, f'{file_stem}_HDR')
      logging.debug('HDR code: %s, HDR alpha: %s, HDR contour length: %d',
                    hdr_detection_object,
                    format_optional_float(hdr_alpha),
                    hdr_length)

      # Assert correct behavior
      failure_messages = []
      # Decoding QR code with HDR -> PASS
      if hdr_detection_object != _QR_CODE_VALUE:
        if hdr_alpha is None:  # Allow hdr_alpha to be falsy (0.0)
          failure_messages.append(
              'Unable to detect QR code with HDR extension')
        if (no_hdr_alpha is not None and
            hdr_alpha is not None and
            no_hdr_alpha < hdr_alpha):
          failure_messages.append('QR code was found at a lower contrast with '
                                  f'HDR off ({no_hdr_alpha}) than with HDR on '
                                  f'({hdr_alpha})')
        if no_hdr_length > 0 and hdr_length / no_hdr_length > _GRADIENT_TOL:
          failure_messages.append(
              ('HDR gradient was not significantly '
               'smaller than gradient without HDR. '
               'Largest HDR gradient contour perimeter was '
               f'{hdr_length / no_hdr_length} of '
               'the size of largest non-HDR contour length, '
               f'expected to be at least {_GRADIENT_TOL}')
          )
        else:
          # If HDR gradient is better, allow PASS to account for cv2 flakiness
          if failure_messages:
            logging.error('\n'.join(failure_messages))
            failure_messages = []

      # Compare capture durations
      duration_diff = hdr_capture_duration - no_hdr_capture_duration
      if duration_diff < _DURATION_DIFF_TOL:
        failure_messages.append('Capture with HDR did not take '
                                'significantly more time than '
                                'capture without HDR! '
                                f'Difference: {duration_diff:.2f}, '
                                f'Expected: {_DURATION_DIFF_TOL}')

      if failure_messages:
        raise AssertionError('\n'.join(failure_messages))


if __name__ == '__main__':
  test_runner.main()
