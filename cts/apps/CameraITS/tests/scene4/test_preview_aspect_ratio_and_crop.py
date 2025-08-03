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
"""Validate preview aspect ratio, crop and FoV vs format."""

import logging
import os

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
_VIDEO_DURATION = 3  # seconds
_MAX_8BIT_IMGS = 255


def _collect_data(cam, preview_size):
  """Capture a preview video from the device.

  Captures camera preview frames from the passed device.

  Args:
    cam: camera object
    preview_size: str; preview resolution. ex. '1920x1080'

  Returns:
    recording object as described by cam.do_preview_recording
  """

  recording_obj = cam.do_preview_recording(preview_size, _VIDEO_DURATION, False)
  logging.debug('Recorded output path: %s', recording_obj['recordedOutputPath'])
  logging.debug('Tested quality: %s', recording_obj['quality'])

  return recording_obj


def _print_failed_test_results(failed_ar, failed_fov, failed_crop):
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


class PreviewAspectRatioAndCropTest(its_base_test.ItsBaseTest):
  """Test preview aspect ratio/field of view/cropping for each tested fmt.

    This test checks for:
    1. Aspect ratio: images are not stretched
    2. Crop: center of images is not shifted
    3. FOV: images cropped to keep maximum possible FOV with only 1 dimension
       (horizontal or veritical) cropped.



  The test preview is a black circle on a white background.

  When RAW capture is available, set the height vs. width ratio of the circle in
  the full-frame RAW as ground truth. In an ideal setup such ratio should be
  very close to 1.0, but here we just use the value derived from full resolution
  RAW as ground truth to account for the possibility that the chart is not well
  positioned to be precisely parallel to image sensor plane.
  The test then compares the ground truth ratio with the same ratio measured
  on previews captured using different formats.

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

  def test_preview_aspect_ratio_and_crop(self):
    log_path = self.log_path
    video_processing_utils.log_ffmpeg_version()

    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      failed_ar = []  # Streams failed the aspect ratio test
      failed_crop = []  # Streams failed the crop test
      failed_fov = []  # Streams that fail FoV test
      props = cam.get_camera_properties()
      fls_logical = props['android.lens.info.availableFocalLengths']
      logging.debug('logical available focal lengths: %s', str(fls_logical))
      props = cam.override_with_hidden_physical_camera_props(props)
      fls_physical = props['android.lens.info.availableFocalLengths']
      logging.debug('physical available focal lengths: %s', str(fls_physical))
      name_with_log_path = f'{os.path.join(self.log_path, _NAME)}'

      # Check SKIP conditions
      first_api_level = its_session_utils.get_first_api_level(self.dut.serial)
      camera_properties_utils.skip_unless(
          first_api_level >= its_session_utils.ANDROID14_API_LEVEL)

      # Load scene
      its_session_utils.load_scene(cam, props, self.scene,
                                   self.tablet, self.chart_distance)
      # Raise error if not FRONT or REAR facing camera
      camera_properties_utils.check_front_or_rear_camera(props)

      # List of preview resolutions to test
      supported_preview_sizes = cam.get_supported_preview_sizes(self.camera_id)
      for size in video_processing_utils.LOW_RESOLUTION_SIZES:
        if size in supported_preview_sizes:
          supported_preview_sizes.remove(size)
      logging.debug('Supported preview resolutions: %s',
                    supported_preview_sizes)
      raw_avlb = camera_properties_utils.raw16(props)
      full_or_better = camera_properties_utils.full_or_better(props)

      # Converge 3A
      cam.do_3a()
      req = capture_request_utils.auto_capture_request()
      if raw_avlb and (fls_physical == fls_logical):
        logging.debug('RAW')
        raw_bool = True
      else:
        logging.debug('JPEG')
        raw_bool = False
      ref_fov, cc_ct_gt, aspect_ratio_gt = image_fov_utils.find_fov_reference(
          cam, req, props, raw_bool, name_with_log_path)

      run_crop_test = full_or_better and raw_avlb

      # Check if we support testing this preview size
      for preview_size in supported_preview_sizes:
        logging.debug('Testing preview recording for size: %s', preview_size)
        # recording preview
        preview_rec_obj = _collect_data(cam, preview_size)

        # Grab the recording from DUT
        self.dut.adb.pull([preview_rec_obj['recordedOutputPath'], log_path])
        preview_file_name = (
            preview_rec_obj['recordedOutputPath'].split('/')[-1])
        logging.debug('preview_file_name: %s', preview_file_name)
        preview_size = preview_rec_obj['videoSize']
        width = int(preview_size.split('x')[0])
        height = int(preview_size.split('x')[-1])

        # Extract last key frame as numpy image
        last_key_frame = (
            video_processing_utils.extract_last_key_frame_from_recording(
                self.log_path, preview_file_name)
        )

        # Check FoV
        ref_img_name = (f'{name_with_log_path}_{preview_size}_circle.png')
        circle = opencv_processing_utils.find_circle(
            last_key_frame, ref_img_name, image_fov_utils.CIRCLE_MIN_AREA,
            image_fov_utils.CIRCLE_COLOR)

        opencv_processing_utils.append_circle_center_to_img(
            circle, last_key_frame, ref_img_name)

        max_img_value = _MAX_8BIT_IMGS

        # Check pass/fail for fov coverage for all fmts in AR_CHECKED
        img_name_stem = f'{name_with_log_path}_{preview_size}'
        fov_chk_msg = image_fov_utils.check_fov(
            circle, ref_fov, width, height)
        if fov_chk_msg:
          img_name = f'{img_name_stem}_fov.png'
          fov_chk_preview_msg = f'Preview Size: {preview_size} {fov_chk_msg}'
          failed_fov.append(fov_chk_preview_msg)
          image_processing_utils.write_image(
              last_key_frame/max_img_value, img_name, True)

        # Check pass/fail for aspect ratio
        ar_chk_msg = image_fov_utils.check_ar(
            circle, aspect_ratio_gt, width, height,
            f'{preview_size}')
        if ar_chk_msg:
          img_name = f'{img_name_stem}_ar.png'
          failed_ar.append(ar_chk_msg)
          image_processing_utils.write_image(
              last_key_frame/max_img_value, img_name, True)

        # Check pass/fail for crop
        if run_crop_test:
          # Normalize the circle size to 1/4 of the image size, so that
          # circle size won't affect the crop test result
          crop_thresh_factor = ((min(ref_fov['w'], ref_fov['h']) / 4.0) /
                                max(ref_fov['circle_w'],
                                    ref_fov['circle_h']))
          crop_chk_msg = image_fov_utils.check_crop(
              circle, cc_ct_gt, width, height,
              f'{preview_size}', crop_thresh_factor)
          if crop_chk_msg:
            crop_img_name = f'{img_name_stem}_crop.png'
            failed_crop.append(crop_chk_msg)
            image_processing_utils.write_image(last_key_frame/max_img_value,
                                               crop_img_name, True)
        else:
          logging.debug('Crop test skipped')

    # Print any failed test results
    _print_failed_test_results(failed_ar, failed_fov, failed_crop)

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
