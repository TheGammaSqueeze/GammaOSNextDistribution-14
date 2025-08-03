# Copyright 2022 The Android Open Source Project
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
"""Validate video aspect ratio, crop and FoV vs format."""

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
import video_processing_utils

_NAME = os.path.splitext(os.path.basename(__file__))[0]
_VIDEO_RECORDING_DURATION_SECONDS = 3
_FOV_PERCENT_RTOL = 0.15  # Relative tolerance on circle FoV % to expected.
_AR_CHECKED_PRE_API_30 = ('4:3', '16:9', '18:9')
_AR_DIFF_ATOL = 0.01
_AR_FOR_JPEG_REFERENCE = (4/3, 16/9)
_MAX_8BIT_IMGS = 255
_MAX_10BIT_IMGS = 1023


def _print_failed_test_results(failed_ar, failed_fov, failed_crop, quality):
  """Print failed test results."""
  if failed_ar:
    logging.error('Aspect ratio test summary for quality: %s', quality)
    logging.error('Images failed in the aspect ratio test:')
    logging.error('Aspect ratio value: width / height')
    for fa in failed_ar:
      logging.error('%s', fa)

  if failed_fov:
    logging.error('FoV test summary for quality: %s', quality)
    logging.error('Images failed in the FoV test:')
    for fov in failed_fov:
      logging.error('%s', str(fov))

  if failed_crop:
    logging.error('Crop test summary for quality: %s', quality)
    logging.error('Images failed in the crop test:')
    logging.error('Circle center (H x V) relative to the image center.')
    for fc in failed_crop:
      logging.error('%s', fc)


class VideoAspectRatioAndCropTest(its_base_test.ItsBaseTest):
  """Test aspect ratio/field of view/cropping for each tested fmt.

    This test checks for:
    1. Aspect ratio: images are not stretched
    2. Crop: center of images is not shifted
    3. FOV: images cropped to keep maximum possible FOV with only 1 dimension
       (horizontal or veritical) cropped.

  Video recording will be done using the SDR profile as well as HLG10
  if available.

  The test video is a black circle on a white background.

  When RAW capture is available, set the height vs. width ratio of the circle in
  the full-frame RAW as ground truth. In an ideal setup such ratio should be
  very close to 1.0, but here we just use the value derived from full resolution
  RAW as ground truth to account for the possibility that the chart is not
  well positioned to be precisely parallel to image sensor plane.
  The test then compares the ground truth ratio with the same ratio measured
  on videos captured using different formats.

  If RAW capture is unavailable, a full resolution JPEG image is used to setup
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

  def test_video_aspect_ratio_and_crop(self):
    logging.debug('Starting %s', _NAME)
    failed_ar = []  # Streams failed the aspect ratio test.
    failed_crop = []  # Streams failed the crop test.
    failed_fov = []  # Streams that fail FoV test.

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
      name_with_log_path = os.path.join(self.log_path, _NAME)

      # Check SKIP conditions.
      first_api_level = its_session_utils.get_first_api_level(self.dut.serial)
      camera_properties_utils.skip_unless(
          first_api_level >= its_session_utils.ANDROID13_API_LEVEL)

      # Load scene.
      its_session_utils.load_scene(cam, props, self.scene,
                                   self.tablet, self.chart_distance)

      # Determine camera capabilities.
      supported_video_qualities = cam.get_supported_video_qualities(
          self.camera_id)
      logging.debug('Supported video qualities: %s', supported_video_qualities)
      full_or_better = camera_properties_utils.full_or_better(props)
      raw_avlb = camera_properties_utils.raw16(props)

      # Converge 3A.
      cam.do_3a()
      req = capture_request_utils.auto_capture_request()

      # For main camera: if RAW available, use it as ground truth, else JPEG
      # For physical sub-camera: if RAW available, only use if not 4:3 or 16:9
      if raw_avlb:
        pixel_array_w = props['android.sensor.info.pixelArraySize']['width']
        pixel_array_h = props['android.sensor.info.pixelArraySize']['height']
        logging.debug('Pixel array size: %dx%d', pixel_array_w, pixel_array_h)
        raw_aspect_ratio = pixel_array_w / pixel_array_h
        if (fls_physical == fls_logical or not
            any(math.isclose(raw_aspect_ratio, jpeg_ar, abs_tol=_AR_DIFF_ATOL)
                for jpeg_ar in _AR_FOR_JPEG_REFERENCE)):
          logging.debug('RAW')
          use_raw_fov = True
        else:
          logging.debug('RAW available, but using JPEG as ground truth')
          use_raw_fov = False
      else:
        logging.debug('JPEG')
        use_raw_fov = False

      ref_fov, cc_ct_gt, aspect_ratio_gt = image_fov_utils.find_fov_reference(
          cam, req, props, use_raw_fov, name_with_log_path)

      run_crop_test = full_or_better and raw_avlb

      # Log ffmpeg version being used.
      video_processing_utils.log_ffmpeg_version()

      for quality_profile_id_pair in supported_video_qualities:
        quality = quality_profile_id_pair.split(':')[0]
        profile_id = quality_profile_id_pair.split(':')[-1]
        # Check if we support testing this quality.
        if quality in video_processing_utils.ITS_SUPPORTED_QUALITIES:
          logging.debug('Testing video recording for quality: %s', quality)
          hlg10_params = [False]
          hlg10_supported = cam.is_hlg10_recording_supported(profile_id)
          logging.debug('HLG10 supported: %s', hlg10_supported)
          if hlg10_supported:
            hlg10_params.append(hlg10_supported)

          for hlg10_param in hlg10_params:
            video_recording_obj = cam.do_basic_recording(
                profile_id, quality, _VIDEO_RECORDING_DURATION_SECONDS, 0,
                hlg10_param)
            logging.debug('video_recording_obj: %s', video_recording_obj)
            # TODO(ruchamk): Modify video recording object to send videoFrame
            # width and height instead of videoSize to avoid string operation
            # here.
            video_size = video_recording_obj['videoSize']
            width = int(video_size.split('x')[0])
            height = int(video_size.split('x')[-1])

            # Pull the video recording file from the device.
            self.dut.adb.pull([video_recording_obj['recordedOutputPath'],
                               self.log_path])
            logging.debug('Recorded video is available at: %s',
                          self.log_path)
            video_file_name = video_recording_obj[
                'recordedOutputPath'].split('/')[-1]
            logging.debug('video_file_name: %s', video_file_name)

            # Validate colorspace
            colorspace = video_processing_utils.get_video_colorspace(
                self.log_path, video_file_name)
            if (hlg10_param and
                video_processing_utils.COLORSPACE_HDR not in colorspace):
              raise AssertionError('colorspace check failed for HDR.')
            if (not hlg10_param and
                video_processing_utils.COLORSPACE_SDR not in colorspace):
              raise AssertionError('colorspace check failed for SDR.')
            logging.debug('Colorspace test passed, video colorspace is %s',
                          colorspace)

            # Extract last key frame as numpy image
            last_key_frame = (
                video_processing_utils.extract_last_key_frame_from_recording(
                    self.log_path, video_file_name)
            )

            # Check FoV
            ref_img_name = (f'{name_with_log_path}_{quality}'
                            f'_w{width}_h{height}_circle.png')
            circle = opencv_processing_utils.find_circle(
                last_key_frame, ref_img_name, image_fov_utils.CIRCLE_MIN_AREA,
                image_fov_utils.CIRCLE_COLOR)

            opencv_processing_utils.append_circle_center_to_img(
                circle, last_key_frame, ref_img_name)

            max_img_value = _MAX_8BIT_IMGS
            if hlg10_param:
              max_img_value = _MAX_10BIT_IMGS

            # Check pass/fail for fov coverage for all fmts in AR_CHECKED
            img_name_stem = f'{name_with_log_path}_{quality}_w{width}_h{height}'
            fov_chk_msg = image_fov_utils.check_fov(
                circle, ref_fov, width, height)
            if fov_chk_msg:
              img_name = f'{img_name_stem}_fov.png'
              fov_chk_quality_msg = f'Quality: {quality} {fov_chk_msg}'
              failed_fov.append(fov_chk_quality_msg)
              image_processing_utils.write_image(
                  last_key_frame/max_img_value, img_name, True)

            # Check pass/fail for aspect ratio.
            ar_chk_msg = image_fov_utils.check_ar(
                circle, aspect_ratio_gt, width, height,
                f'{quality}')
            if ar_chk_msg:
              img_name = f'{img_name_stem}_ar.png'
              failed_ar.append(ar_chk_msg)
              image_processing_utils.write_image(
                  last_key_frame/max_img_value, img_name, True)

            # Check pass/fail for crop.
            if run_crop_test:
              # Normalize the circle size to 1/4 of the image size, so that
              # circle size won't affect the crop test result
              crop_thresh_factor = ((min(ref_fov['w'], ref_fov['h']) / 4.0) /
                                    max(ref_fov['circle_w'],
                                        ref_fov['circle_h']))
              crop_chk_msg = image_fov_utils.check_crop(
                  circle, cc_ct_gt, width, height,
                  f'{quality}', crop_thresh_factor)
              if crop_chk_msg:
                crop_img_name = f'{img_name_stem}_crop.png'
                failed_crop.append(crop_chk_msg)
                image_processing_utils.write_image(last_key_frame/max_img_value,
                                                   crop_img_name, True)
            else:
              logging.debug('Crop test skipped')

      # Print any failed test results.
      _print_failed_test_results(failed_ar, failed_fov, failed_crop, quality)
      e_msg = ''
      if failed_ar:
        e_msg = 'Aspect ratio '
      if failed_fov:
        e_msg += 'FoV '
      if failed_crop:
        e_msg += 'Crop '
      if e_msg:
        raise AssertionError(f'{e_msg}check failed.')

if __name__ == '__main__':
  test_runner.main()
