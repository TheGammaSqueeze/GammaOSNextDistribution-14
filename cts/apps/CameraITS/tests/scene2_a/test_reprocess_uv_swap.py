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
"""Verifies U and V are not swapped during reprocessing."""


import logging
import os.path
from mobly import test_runner

import its_base_test
import camera_properties_utils
import capture_request_utils
import image_processing_utils
import its_session_utils

_NAME = os.path.splitext(os.path.basename(__file__))[0]
_NR_MODES = {'OFF': 0, 'FAST': 1, 'HQ': 2, 'MIN': 3, 'ZSL': 4}
_EDGE_MODES = {'OFF': 0, 'FAST': 1, 'HQ': 2, 'ZSL': 3}


def calc_rgb_uv_swap(cap_no_re, cap_re, re_mode, name_with_log_path,
                     reprocess_format, reprocess_type):
  """Determine the likelihood of a UV swap due to reprocessing.

  Args:
    cap_no_re: Camera capture object without reprocessing.
    cap_re: Camera capture object with reprocessing.
    re_mode: Integer reprocess mode index.
    name_with_log_path: Test name with path for storage.
    reprocess_format: The reprocess format string.
    reprocess_type: The type of reprocessing as a string (e.g. "nr", "ee").

  Returns:
    A tuple of sums of absolute differences for the swapped and non-swapped
    comparisons.
  """
  suffix = f'{reprocess_type}={re_mode}_reprocess_fmt='
  suffix += f'{reprocess_format}_fmt=jpg.jpg'
  img_no_re = image_processing_utils.decompress_jpeg_to_yuv_image(cap_no_re)
  image_processing_utils.write_image(
      img_no_re, f'{name_with_log_path}_no_{suffix}', is_yuv=True)

  img_re = image_processing_utils.decompress_jpeg_to_yuv_image(cap_re)
  image_processing_utils.write_image(
      img_re, f'{name_with_log_path}_{suffix}', is_yuv=True)

  # Generate a UV-swapped copy of the reprocessed image.
  img_re_swap = img_re[:, :, [0, 2, 1]]

  # Calculate the sums of absolute difference with and without the U and V
  # channels swapped
  sad_swap = image_processing_utils.compute_image_sad(img_no_re, img_re_swap)
  sad_no_swap = image_processing_utils.compute_image_sad(img_no_re, img_re)

  return (sad_swap, sad_no_swap)


class ReprocessUvSwapTest(its_base_test.ItsBaseTest):
  """Test for UV swap during reprocessing requests.

  Uses JPEG captures as the output format.

  Determines which reprocessing formats are available among 'yuv' and 'private'.
  For each reprocessing format:
    Captures without reprocessing.
    Captures in supported reprocessed modes.
    Calculates the SAD (Sum of Absolute Differences) between the two, with
      and without UV swap.
    If the SAD is smaller when U and V are swapped, fails the test.
    Noise reduction (NR) modes:
      OFF, FAST, High Quality (HQ), Minimal (MIN), and zero shutter lag (ZSL)

    Proper behavior:
      The U and V planes should not be swapped.
  """

  def test_reprocess_noise_reduction(self):
    logging.debug('Starting %s:test_reprocess_noise_reduction', _NAME)
    logging.debug('NR_MODES: %s', str(_NR_MODES))

    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      camera_properties_utils.skip_unless(
          camera_properties_utils.compute_target_exposure(props) and
          camera_properties_utils.per_frame_control(props) and
          camera_properties_utils.noise_reduction_mode(props, 0) and
          (camera_properties_utils.yuv_reprocess(props) or
           camera_properties_utils.private_reprocess(props)))
      log_path = self.log_path
      name_with_log_path = os.path.join(log_path, _NAME)

      # Load chart for scene
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet, self.chart_distance)

      # If reprocessing is supported, ZSL NR mode must be available
      if not camera_properties_utils.noise_reduction_mode(
          props, _NR_MODES['ZSL']):
        raise KeyError('Reprocessing supported, so ZSL must be supported.')

      reprocess_formats = camera_properties_utils.get_reprocess_formats(props)

      # Capture for each available reprocess format
      uv_swaps = {}
      size = capture_request_utils.get_available_output_sizes('jpg', props)[0]
      out_surface = {'width': size[0], 'height': size[1], 'format': 'jpg'}
      for reprocess_format in reprocess_formats:
        logging.debug('Reprocess format: %s', reprocess_format)
        uv_swaps[reprocess_format] = {}

        # Capture for each mode
        for nr_mode in tuple(_NR_MODES.values()):
          # Skip unavailable modes
          if not camera_properties_utils.noise_reduction_mode(props, nr_mode):
            uv_swaps[reprocess_format][nr_mode] = (0, 0)
            continue

          # Create req, do caps and determine UV swap likelihood
          req = capture_request_utils.auto_capture_request()
          req['android.noiseReduction.mode'] = nr_mode
          cam.do_3a()
          caps_no_nr = cam.do_capture([req], out_surface)
          caps = cam.do_capture([req], out_surface, reprocess_format)

          sad_swap, sad_no_swap = calc_rgb_uv_swap(
              caps_no_nr[0]['data'], caps[0]['data'], nr_mode,
              name_with_log_path, reprocess_format, 'nr')
          uv_swaps[reprocess_format][nr_mode] = (sad_swap, sad_no_swap)

      # Fail all instances where swapping the U and V channels results in an
      # image which is closer to the non-reprocessed capture.
      num_fail = 0
      num_tests = 0
      for reprocess_format, nr_mode_dict in uv_swaps.items():
        for nr_mode, (sad_swap, sad_no_swap) in nr_mode_dict.items():
          num_tests += 1
          if sad_swap < sad_no_swap:
            num_fail += 1
            logging.error('REPROCESS_FMT: %s, '
                          'NR_MODE: %d, '
                          'SAD_SWAP: %.2f, '
                          'SAD_NO_SWAP: %.2f',
                          reprocess_format, nr_mode, sad_swap, sad_no_swap)

      if num_fail > 0:
        raise AssertionError(f'Number of fails: {num_fail} / {num_tests}')

  def test_reprocess_edge_enhancement(self):
    logging.debug('Starting %s:test_reprocess_edge_enhancement', _NAME)
    logging.debug('EDGE_MODES: %s', str(_EDGE_MODES))

    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      camera_properties_utils.skip_unless(
          camera_properties_utils.read_3a(props) and
          camera_properties_utils.per_frame_control(props) and
          camera_properties_utils.edge_mode(props, 0) and
          (camera_properties_utils.yuv_reprocess(props) or
           camera_properties_utils.private_reprocess(props)))
      log_path = self.log_path
      name_with_log_path = os.path.join(log_path, _NAME)

      # Load chart for scene
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet, self.chart_distance)

      # If reprocessing is supported, ZSL EE mode must be available
      if not camera_properties_utils.edge_mode(props, _EDGE_MODES['ZSL']):
        raise KeyError('Reprocessing supported, so ZSL must be supported.')

      reprocess_formats = camera_properties_utils.get_reprocess_formats(props)

      # Capture for each available reprocess format
      uv_swaps = {}
      size = capture_request_utils.get_available_output_sizes('jpg', props)[0]
      out_surface = {'width': size[0], 'height': size[1], 'format': 'jpg'}
      for reprocess_format in reprocess_formats:
        logging.debug('Reprocess format: %s', reprocess_format)
        uv_swaps[reprocess_format] = {}

        # Capture for each mode
        for edge_mode in tuple(_EDGE_MODES.values()):
          # Skip unavailable modes
          if not camera_properties_utils.edge_mode(props, edge_mode):
            uv_swaps[reprocess_format][edge_mode] = (0, 0)
            continue

          # Create req, do caps and determine UV swap likelihood
          req = capture_request_utils.auto_capture_request()
          req['android.edge.mode'] = edge_mode
          cam.do_3a()
          caps_no_ee = cam.do_capture([req], out_surface)
          caps = cam.do_capture([req], out_surface, reprocess_format)

          sad_swap, sad_no_swap = calc_rgb_uv_swap(
              caps_no_ee[0]['data'], caps[0]['data'], edge_mode,
              name_with_log_path, reprocess_format, 'ee')
          uv_swaps[reprocess_format][edge_mode] = (sad_swap, sad_no_swap)

      # Fail all instances where swapping the U and V channels results in an
      # image which is closer to the non-reprocessed capture.
      num_fail = 0
      num_tests = 0
      for reprocess_format, edge_mode_dict in uv_swaps.items():
        for edge_mode, (sad_swap, sad_no_swap) in edge_mode_dict.items():
          num_tests += 1
          if sad_swap < sad_no_swap:
            num_fail += 1
            logging.error('REPROCESS_FMT: %s, '
                          'EDGE_MODE: %d, '
                          'SAD_SWAP: %.2f, '
                          'SAD_NO_SWAP: %.2f',
                          reprocess_format, edge_mode, sad_swap, sad_no_swap)

      if num_fail > 0:
        raise AssertionError(f'Number of fails: {num_fail} / {num_tests}')

  def test_reprocess_jpeg(self):
    logging.debug('Starting %s:test_reprocess_jpeg', _NAME)

    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      camera_properties_utils.skip_unless(
          camera_properties_utils.per_frame_control(props) and
          camera_properties_utils.jpeg_orientation(props) and
          (camera_properties_utils.yuv_reprocess(props) or
           camera_properties_utils.private_reprocess(props)))
      log_path = self.log_path
      name_with_log_path = os.path.join(log_path, _NAME)
      applied_orientation = 90

      # Load chart for scene
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet, self.chart_distance)

      reprocess_formats = camera_properties_utils.get_reprocess_formats(props)

      # Capture for each available reprocess format
      uv_swaps = {}
      size = capture_request_utils.get_available_output_sizes('jpg', props)[0]
      out_surface = {'width': size[0], 'height': size[1], 'format': 'jpg'}
      for reprocess_format in reprocess_formats:
        logging.debug('Reprocess format: %s', reprocess_format)

        # Create req, do caps and determine UV swap likelihood
        req = capture_request_utils.auto_capture_request()
        req['android.jpeg.orientation'] = applied_orientation
        cam.do_3a()
        caps_no_jpeg = cam.do_capture([req], out_surface)
        caps = cam.do_capture([req], out_surface, reprocess_format)

        sad_swap, sad_no_swap = calc_rgb_uv_swap(
            caps_no_jpeg[0]['data'], caps[0]['data'], applied_orientation,
            name_with_log_path, reprocess_format, 'orientation')
        uv_swaps[reprocess_format] = (sad_swap, sad_no_swap)

      # Fail all instances where swapping the U and V channels results in an
      # image which is closer to the non-reprocessed capture.
      num_fail = 0
      num_tests = 0
      for reprocess_format, (sad_swap, sad_no_swap) in uv_swaps.items():
        num_tests += 1
        if sad_swap < sad_no_swap:
          num_fail += 1
          logging.error('REPROCESS_FMT: %s, '
                        'ORIENTATION: %d, '
                        'SAD_SWAP: %.2f, '
                        'SAD_NO_SWAP: %.2f',
                        reprocess_format, applied_orientation, sad_swap,
                        sad_no_swap)

      if num_fail > 0:
        raise AssertionError(f'Number of fails: {num_fail} / {num_tests}')

if __name__ == '__main__':
  test_runner.main()

