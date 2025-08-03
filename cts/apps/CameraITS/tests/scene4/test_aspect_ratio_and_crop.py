# Copyright 2015 The Android Open Source Project
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
"""Validate aspect ratio, crop and FoV vs format."""


import logging
import math
import os.path
from mobly import test_runner

import its_base_test
import camera_properties_utils
import capture_request_utils
import image_fov_utils
import image_processing_utils
import its_session_utils
import opencv_processing_utils

_ANDROID11_API_LEVEL = 30
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_PREVIEW_SIZE = (1920, 1080)

# Before API level 30, only resolutions with the following listed aspect ratio
# are checked. Device launched after API level 30 will need to pass the test
# for all advertised resolutions. Device launched before API level 30 just
# needs to pass the test for all resolutions within these aspect ratios.
_AR_CHECKED_PRE_API_30 = ('4:3', '16:9', '18:9')
_AR_DIFF_ATOL = 0.01
# If RAW reference capture aspect ratio is ~4:3 or ~16:9, use JPEG, else RAW
_AR_FOR_JPEG_REFERENCE = (4/3, 16/9)


def _check_skip_conditions(first_api_level, props):
  """Check the skip conditions based on first API level."""
  if first_api_level < _ANDROID11_API_LEVEL:  # Original constraint.
    camera_properties_utils.skip_unless(camera_properties_utils.read_3a(props))
  else:  # Loosen from read_3a to enable LIMITED coverage.
    camera_properties_utils.skip_unless(
        camera_properties_utils.ae_lock(props) and
        camera_properties_utils.awb_lock(props))


def _check_basic_correctness(cap, fmt_iter, w_iter, h_iter):
  """Check the capture for basic correctness."""
  if cap['format'] != fmt_iter:
    raise AssertionError
  if cap['width'] != w_iter:
    raise AssertionError
  if cap['height'] != h_iter:
    raise AssertionError


def _create_format_list():
  """Create format list for multiple capture objects.

  Do multi-capture of 'iter' and 'cmpr'. Iterate through all the available
  sizes of 'iter', and only use the size specified for 'cmpr'.
  The 'cmpr' capture is only used so that we have multiple capture target
  instead of just one, which should help catching more potential issues.
  The test doesn't look into the output of 'cmpr' images at all.
  The 'iter_max' or 'cmpr_size' key defines the maximal size being iterated
  or selected for the 'iter' and 'cmpr' stream accordingly. None means no
  upper bound is specified.

  Args:
    None

  Returns:
    format_list
  """
  format_list = []
  format_list.append({'iter': 'jpeg_r', 'iter_max': None,
                      'cmpr': 'priv', 'cmpr_size': _PREVIEW_SIZE})
  format_list.append({'iter': 'yuv', 'iter_max': None,
                      'cmpr': 'yuv', 'cmpr_size': _PREVIEW_SIZE})
  format_list.append({'iter': 'yuv', 'iter_max': _PREVIEW_SIZE,
                      'cmpr': 'jpeg', 'cmpr_size': None})
  format_list.append({'iter': 'yuv', 'iter_max': _PREVIEW_SIZE,
                      'cmpr': 'raw', 'cmpr_size': None})
  format_list.append({'iter': 'jpeg', 'iter_max': None,
                      'cmpr': 'raw', 'cmpr_size': None})
  format_list.append({'iter': 'jpeg', 'iter_max': None,
                      'cmpr': 'yuv', 'cmpr_size': _PREVIEW_SIZE})
  return format_list


def _print_failed_test_results(failed_ar, failed_fov, failed_crop,
                               first_api_level, level_3):
  """Print failed test results."""
  if failed_ar:
    logging.error('Aspect ratio test summary')
    logging.error('Images failed in the aspect ratio test:')
    logging.error('Aspect ratio value: width / height')
    for fa in failed_ar:
      logging.error('%s', fa)

  if failed_fov:
    logging.error('FoV test summary')
    logging.error('Images failed in the FoV test:')
    for fov in failed_fov:
      logging.error('%s', str(fov))

  if failed_crop:
    logging.error('Crop test summary')
    logging.error('Images failed in the crop test:')
    logging.error('Circle center (H x V) relative to the image center.')
    for fc in failed_crop:
      logging.error('%s', fc)
  if failed_ar:
    raise RuntimeError
  if failed_fov:
    raise RuntimeError
  if first_api_level > _ANDROID11_API_LEVEL:
    if failed_crop:  # failed_crop = [] if run_crop_test = False.
      raise RuntimeError
  else:
    if failed_crop and level_3:
      raise RuntimeError


def _is_checked_aspect_ratio(first_api_level, w, h):
  """Determine if format aspect ratio is a checked on based of first_API."""
  if first_api_level >= _ANDROID11_API_LEVEL:
    return True

  for ar_check in _AR_CHECKED_PRE_API_30:
    match_ar_list = [float(x) for x in ar_check.split(':')]
    match_ar = match_ar_list[0] / match_ar_list[1]
    if math.isclose(w / h, match_ar, abs_tol=_AR_DIFF_ATOL):
      return True

  return False


class AspectRatioAndCropTest(its_base_test.ItsBaseTest):
  """Test aspect ratio/field of view/cropping for each tested fmt combinations.

  This test checks for:
    1. Aspect ratio: images are not stretched
    2. Crop: center of images is not shifted
    3. FOV: images cropped to keep maximum possible FOV with only 1 dimension
       (horizontal or veritical) cropped.

  Aspect ratio and FOV test runs on level3, full and limited devices.
  Crop test only runs on level3 and full devices.

  The test chart is a black circle inside a black square. When raw capture is
  available, set the height vs. width ratio of the circle in the full-frame
  raw as ground truth. In an ideal setup such ratio should be very close to
  1.0, but here we just use the value derived from full resolution RAW as
  ground truth to account for the possibility that the chart is not well
  positioned to be precisely parallel to image sensor plane.
  The test then compares the ground truth ratio with the same ratio measured
  on images captured using different stream combinations of varying formats
  ('jpeg' and 'yuv') and resolutions.
  If raw capture is unavailable, a full resolution JPEG image is used to setup
  ground truth. In this case, the ground truth aspect ratio is defined as 1.0
  and it is the tester's responsibility to make sure the test chart is
  properly positioned so the detected circles indeed have aspect ratio close
  to 1.0 assuming no bugs causing image stretched.

  The aspect ratio test checks the aspect ratio of the detected circle and
  it will fail if the aspect ratio differs too much from the ground truth
  aspect ratio mentioned above.

  The FOV test examines the ratio between the detected circle area and the
  image size. When the aspect ratio of the test image is the same as the
  ground truth image, the ratio should be very close to the ground truth
  value. When the aspect ratio is different, the difference is factored in
  per the expectation of the Camera2 API specification, which mandates the
  FOV reduction from full sensor area must only occur in one dimension:
  horizontally or vertically, and never both. For example, let's say a sensor
  has a 16:10 full sensor FOV. For all 16:10 output images there should be no
  FOV reduction on them. For 16:9 output images the FOV should be vertically
  cropped by 9/10. For 4:3 output images the FOV should be cropped
  horizontally instead and the ratio (r) can be calculated as follows:
      (16 * r) / 10 = 4 / 3 => r = 40 / 48 = 0.8333
  Say the circle is covering x percent of the 16:10 sensor on the full 16:10
  FOV, and assume the circle in the center will never be cut in any output
  sizes (this can be achieved by picking the right size and position of the
  test circle), the from above cropping expectation we can derive on a 16:9
  output image the circle will cover (x / 0.9) percent of the 16:9 image; on
  a 4:3 output image the circle will cover (x / 0.8333) percent of the 4:3
  image.

  The crop test checks that the center of any output image remains aligned
  with center of sensor's active area, no matter what kind of cropping or
  scaling is applied. The test verifies that by checking the relative vector
  from the image center to the center of detected circle remains unchanged.
  The relative part is normalized by the detected circle size to account for
  scaling effect.
  """

  def test_aspect_ratio_and_crop(self):
    logging.debug('Starting %s', _NAME)
    failed_ar = []  # Streams failed the aspect ratio test.
    failed_crop = []  # Streams failed the crop test.
    failed_fov = []  # Streams that fail FoV test.
    format_list = _create_format_list()

    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      fls_logical = props['android.lens.info.availableFocalLengths']
      logging.debug('logical available focal lengths: %s', str(fls_logical))
      props = cam.override_with_hidden_physical_camera_props(props)
      fls_physical = props['android.lens.info.availableFocalLengths']
      logging.debug('physical available focal lengths: %s', str(fls_physical))
      logging.debug('minimum focus distance (diopters): %.2f',
                    props['android.lens.info.minimumFocusDistance'])
      name_with_log_path = os.path.join(self.log_path, _NAME)
      if self.hidden_physical_id:
        logging.debug('Testing camera: %s.%s',
                      self.camera_id, self.hidden_physical_id)

      # Check SKIP conditions.
      first_api_level = its_session_utils.get_first_api_level(self.dut.serial)
      _check_skip_conditions(first_api_level, props)

      # Load chart for scene.
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet, self.chart_distance)

      # Determine camera capabilities.
      full_or_better = camera_properties_utils.full_or_better(props)
      level3 = camera_properties_utils.level3(props)
      raw_avlb = camera_properties_utils.raw16(props)

      # Converge 3A.
      if camera_properties_utils.manual_sensor(props):
        logging.debug('Manual sensor, using manual capture request')
        s, e, _, _, f_d = cam.do_3a(get_results=True)
        req = capture_request_utils.manual_capture_request(
            s, e, f_distance=f_d)
      else:
        logging.debug('Using auto capture request')
        cam.do_3a()
        req = capture_request_utils.auto_capture_request()

      # For main camera: if RAW available, use it as ground truth, else JPEG
      # For physical sub-camera: if RAW available, only use if not 4:3 or 16:9
      use_raw_fov = False
      if raw_avlb:
        pixel_array_w = props['android.sensor.info.pixelArraySize']['width']
        pixel_array_h = props['android.sensor.info.pixelArraySize']['height']
        logging.debug('Pixel array size: %dx%d', pixel_array_w, pixel_array_h)
        raw_aspect_ratio = pixel_array_w / pixel_array_h
        use_raw_fov = (
            fls_physical == fls_logical or not
            any(math.isclose(raw_aspect_ratio, jpeg_ar, abs_tol=_AR_DIFF_ATOL)
                for jpeg_ar in _AR_FOR_JPEG_REFERENCE)
        )

      ref_fov, cc_ct_gt, aspect_ratio_gt = (
          image_fov_utils.find_fov_reference(
              cam, req, props, use_raw_fov, name_with_log_path))

      run_crop_test = full_or_better and raw_avlb
      if run_crop_test:
        # Normalize the circle size to 1/4 of the image size, so that
        # circle size won't affect the crop test result
        crop_thresh_factor = ((min(ref_fov['w'], ref_fov['h']) / 4.0) /
                              max(ref_fov['circle_w'], ref_fov['circle_h']))
      else:
        logging.debug('Crop test skipped')

      # Take pictures of each settings with all the image sizes available.
      for fmt in format_list:
        fmt_iter = fmt['iter']
        fmt_cmpr = fmt['cmpr']
        # Get the size of 'cmpr'.
        sizes = capture_request_utils.get_available_output_sizes(
            fmt_cmpr, props, fmt['cmpr_size'])
        if not sizes:  # Device might not support RAW.
          continue
        w_cmpr, h_cmpr = sizes[0][0], sizes[0][1]
        for size_iter in capture_request_utils.get_available_output_sizes(
            fmt_iter, props, fmt['iter_max']):
          w_iter, h_iter = size_iter[0], size_iter[1]
          # Skip same format/size combination: ITS doesn't handle that properly.
          if w_iter*h_iter == w_cmpr*h_cmpr and fmt_iter == fmt_cmpr:
            continue
          out_surface = [{'width': w_iter, 'height': h_iter,
                          'format': fmt_iter}]
          out_surface.append({'width': w_cmpr, 'height': h_cmpr,
                              'format': fmt_cmpr})
          cap = cam.do_capture(req, out_surface)[0]
          _check_basic_correctness(cap, fmt_iter, w_iter, h_iter)
          logging.debug('Captured %s with %s %dx%d. Compared size: %dx%d',
                        fmt_iter, fmt_cmpr, w_iter, h_iter, w_cmpr, h_cmpr)
          img = image_processing_utils.convert_capture_to_rgb_image(cap)
          img *= 255  # cv2 uses [0, 255].
          img_name = f'{name_with_log_path}_{fmt_iter}_with_{fmt_cmpr}_w{w_iter}_h{h_iter}.png'
          circle = opencv_processing_utils.find_circle(
              img, img_name, image_fov_utils.CIRCLE_MIN_AREA,
              image_fov_utils.CIRCLE_COLOR)
          opencv_processing_utils.append_circle_center_to_img(
              circle, img, img_name)

          # Check pass/fail for fov coverage for all fmts in AR_CHECKED
          img /= 255  # image_processing_utils uses [0, 1].
          if _is_checked_aspect_ratio(first_api_level, w_iter, h_iter):
            fov_chk_msg = image_fov_utils.check_fov(
                circle, ref_fov, w_iter, h_iter)
            if fov_chk_msg:
              failed_fov.append(fov_chk_msg)
              image_processing_utils.write_image(img, img_name, True)

          # Check pass/fail for aspect ratio.
          ar_chk_msg = image_fov_utils.check_ar(
              circle, aspect_ratio_gt, w_iter, h_iter,
              f'{fmt_iter} with {fmt_cmpr}')
          if ar_chk_msg:
            failed_ar.append(ar_chk_msg)
            image_processing_utils.write_image(img, img_name, True)

          # Check pass/fail for crop.
          if run_crop_test:
            crop_chk_msg = image_fov_utils.check_crop(
                circle, cc_ct_gt, w_iter, h_iter,
                f'{fmt_iter} with {fmt_cmpr}', crop_thresh_factor)
            if crop_chk_msg:
              failed_crop.append(crop_chk_msg)
              image_processing_utils.write_image(img, img_name, True)

      # Print any failed test results.
      _print_failed_test_results(failed_ar, failed_fov, failed_crop,
                                 first_api_level, level3)

if __name__ == '__main__':
  test_runner.main()
