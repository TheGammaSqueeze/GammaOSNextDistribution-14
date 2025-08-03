# Copyright 2016 The Android Open Source Project
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
"""Test lens shading and color uniformity with diffuser over camera."""


import logging
import math
import os.path

import cv2
from mobly import test_runner
import numpy

import its_base_test
import camera_properties_utils
import capture_request_utils
import image_processing_utils
import its_session_utils

_NAME = os.path.basename(__file__).split('.')[0]
_NSEC_TO_MSEC = 1E-6

# List to create NUM-1 blocks around the center block for sampling grid in image
_NUM_RADIUS = 8
_BLOCK_R = 1/2/(_NUM_RADIUS*2-1)  # 'radius' of block (x/2 & y/2 in rel values)
_BLOCK_POSITION_LIST = numpy.arange(_BLOCK_R, 1/2, _BLOCK_R*2)

# Thresholds for PASS/FAIL
_THRESH_SHADING_CT = 0.9  # len shading allowance for center
_THRESH_SHADING_CN = 0.6  # len shading allowance for corner
_THRESH_SHADING_HIGH = 0.2  # max allowed % for patch to be brighter than center
_THRESH_UNIFORMITY = 0.2  # uniformity allowance

# cv2 drawing colors
_CV2_RED = (1, 0, 0)   # blocks failed the test
_CV2_GREEN = (0, 0.7, 0.3)   # blocks passed the test


def _calc_block_lens_shading_thresh_l(
    block_center_x, block_center_y, center_luma, img_w, img_h, dist_max):
  dist_to_img_center = math.sqrt(pow(abs(block_center_x-0.5)*img_w, 2) +
                                 pow(abs(block_center_y-0.5)*img_h, 2))
  return ((_THRESH_SHADING_CT - _THRESH_SHADING_CN) *
          (1 - dist_to_img_center/dist_max) + _THRESH_SHADING_CN) * center_luma


def _calc_color_plane_ratios(img_rgb):
  """Calculate R/G and B/G ratios."""
  img_g_plus_delta = img_rgb[:, :, 1] + 0.001  # in case G channel has 0 value.
  img_r_g = img_rgb[:, :, 0] / img_g_plus_delta
  img_b_g = img_rgb[:, :, 2] / img_g_plus_delta
  return img_r_g, img_b_g


def _create_block_center_vals(block_center):
  """Create lists of x and y values for sub-block centers."""
  num_sample = int(((1-block_center*2)/_BLOCK_R/2 + 1).item())
  center_xs = numpy.concatenate(
      (numpy.arange(block_center, 1-block_center+_BLOCK_R, _BLOCK_R*2),
       block_center*numpy.ones((num_sample-1)),
       (1-block_center)*numpy.ones((num_sample-1)),
       numpy.arange(block_center, 1-block_center+_BLOCK_R, _BLOCK_R*2)))
  center_ys = numpy.concatenate(
      (block_center*numpy.ones(num_sample+1),
       numpy.arange(block_center+_BLOCK_R*2, 1-block_center, _BLOCK_R*2),
       numpy.arange(block_center+_BLOCK_R*2, 1-block_center, _BLOCK_R*2),
       (1-block_center)*numpy.ones(num_sample+1)))
  return zip(center_xs, center_ys)


def _assert_results(ls_test_failed, cu_test_failed, center_luma, ls_thresh_h):
  """Check the lens shading and color uniformity results."""
  if ls_test_failed:
    logging.error('Lens shading test summary')
    logging.error('Center block average Y value: %.3f', center_luma)
    logging.error('Blocks failed in the lens shading test:')
    for block in ls_test_failed:
      top, bottom, left, right = block['position']
      logging.error('Block[top: %d, bottom: %d, left: %d, right: %d]; '
                    'avg Y value: %.3f; valid range: %.3f ~ %.3f', top, bottom,
                    left, right, block['val'], block['thresh_l'], ls_thresh_h)
  if cu_test_failed:
    logging.error('Color uniformity test summary')
    logging.error('Valid color uniformity range: 0 ~ %.2f', _THRESH_UNIFORMITY)
    logging.error('Areas that failed the color uniformity test:')
    for rd in cu_test_failed:
      logging.error('Radius position: %.3f; R/G uniformity: %.3f; B/G '
                    'uniformity: %.3f', rd['position'], rd['uniformity_r_g'],
                    rd['uniformity_b_g'])
  if ls_test_failed:
    raise AssertionError('Lens shading test failed.')
  if cu_test_failed:
    raise AssertionError('Color uniformity test failed.')


def _draw_legend(img, texts, text_org, font_scale, text_offset, color,
                 line_width):
  """Draw legend on an image.

  Args:
    img: Numpy float image array in RGB, with pixel values in [0,1].
    texts: List of legends. Each element in the list is a line of legend.
    text_org: Tuple of the bottom left corner of the text position in
              pixels, horizontal and vertical.
    font_scale: Float number. Font scale of the basic font size.
    text_offset: Text line width in pixels.
    color: Text color in rgb value.
    line_width: Text line width in pixels.
  """
  for text in texts:
    cv2.putText(img, text, (text_org[0], text_org[1]),
                cv2.FONT_HERSHEY_SIMPLEX, font_scale, color, line_width)
    text_org[1] += text_offset


class LensShadingAndColorUniformityTest(its_base_test.ItsBaseTest):
  """Test lens shading correction and uniform scene is evenly distributed.

  Test runs with a diffuser (manually) placed in front of the camera.
  Performs this test on a YUV frame with auto 3A. Lens shading is evaluated
  based on the Y channel. Measure the average Y value for each sample block
  specified, and then determine PASS/FAIL by comparing with the center Y value.

  Evaluates the color uniformity in R/G and B/G color space. At specified
  radius of the image, the variance of R/G and B/G values need to be less than
  a threshold in order to pass the test.
  """

  def test_lens_shading_and_color_uniformity(self):

    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      debug_mode = self.debug_mode
      name_with_log_path = os.path.join(self.log_path, _NAME)

      # Check SKIP conditions.
      camera_properties_utils.skip_unless(
          camera_properties_utils.ae_lock(props) and
          camera_properties_utils.awb_lock(props))

      if camera_properties_utils.read_3a(props):
        # Converge 3A and get the estimates.
        sens, exp, awb_gains, awb_xform, _ = cam.do_3a(
            get_results=True, do_af=False, lock_ae=True, lock_awb=True)
        logging.debug('AE sensitivity: %d, exp: %dms', sens, exp*_NSEC_TO_MSEC)
        logging.debug('AWB gains: %s', str(awb_gains))
        logging.debug('AWB transform: %s', str(awb_xform))

      req = capture_request_utils.auto_capture_request()
      w, h = capture_request_utils.get_available_output_sizes('yuv', props)[0]
      out_surface = {'format': 'yuv', 'width': w, 'height': h}
      if debug_mode:
        out_surfaces = [{'format': 'raw'}, out_surface]
        cap_raw, cap = cam.do_capture(req, out_surfaces)
        img_raw = image_processing_utils.convert_capture_to_rgb_image(
            cap_raw, props=props)
        image_processing_utils.write_image(
            img_raw, f'{name_with_log_path}_raw.png', True)
        logging.debug('Captured RAW %dx%d', img_raw.shape[1], img_raw.shape[0])
      else:
        cap = cam.do_capture(req, out_surface)
      logging.debug('Captured YUV %dx%d', w, h)
      # Get Y channel
      img_y = image_processing_utils.convert_capture_to_planes(cap)[0]
      image_processing_utils.write_image(
          img_y, f'{name_with_log_path}_y_plane.png', True)
      # Convert RGB image & calculate R/G, R/B ratioed images
      img_rgb = image_processing_utils.convert_capture_to_rgb_image(cap)
      img_r_g, img_b_g = _calc_color_plane_ratios(img_rgb)

      # Make copies for images with legends and set legend parameters.
      img_lens_shading = numpy.copy(img_rgb)
      img_uniformity = numpy.copy(img_rgb)
      line_width = max(2, int(max(h, w)/500))  # line width of legend
      font_scale = line_width / 7.0   # font scale of the basic font size
      font_line_width = int(line_width/2)
      text_height = cv2.getTextSize('gf', cv2.FONT_HERSHEY_SIMPLEX,
                                    font_scale, line_width)[0][1]
      text_offset = int(text_height*1.5)

      # Calculate center block average Y, R/G, and B/G values.
      top = int((0.5-_BLOCK_R)*h)
      bottom = int((0.5+_BLOCK_R)*h)
      left = int((0.5-_BLOCK_R)*w)
      right = int((0.5+_BLOCK_R)*w)
      center_luma = numpy.mean(img_y[top:bottom, left:right])
      center_r_g = numpy.mean(img_r_g[top:bottom, left:right])
      center_b_g = numpy.mean(img_b_g[top:bottom, left:right])

      # Add center patch legend to lens shading and color uniformity images
      cv2.rectangle(img_lens_shading, (left, top), (right, bottom), _CV2_GREEN,
                    line_width)
      _draw_legend(img_lens_shading, [f'Y: {center_luma}:.2f'],
                   [left+text_offset, bottom-text_offset],
                   font_scale, text_offset, _CV2_GREEN, font_line_width)

      cv2.rectangle(img_uniformity, (left, top), (right, bottom), _CV2_GREEN,
                    line_width)
      _draw_legend(img_uniformity,
                   [f'R/G: {center_r_g}:.2f', f'B/G: {center_b_g}:.2f'],
                   [left+text_offset, bottom-text_offset*2],
                   font_scale, text_offset, _CV2_GREEN, font_line_width)

      # Evaluate Y, R/G, and B/G for each block
      ls_test_failed = []
      cu_test_failed = []
      ls_thresh_h = center_luma * (1 + _THRESH_SHADING_HIGH)
      dist_max = math.sqrt(pow(w, 2)+pow(h, 2))/2
      for position in _BLOCK_POSITION_LIST:
        # Create sample block centers' positions in all directions around center
        block_centers = _create_block_center_vals(position)

        blocks_info = []
        max_r_g = 0
        min_r_g = float('inf')
        max_b_g = 0
        min_b_g = float('inf')
        for block_center_x, block_center_y in block_centers:
          top = int((block_center_y-_BLOCK_R)*h)
          bottom = int((block_center_y+_BLOCK_R)*h)
          left = int((block_center_x-_BLOCK_R)*w)
          right = int((block_center_x+_BLOCK_R)*w)

          # Compute block average values and running mins and maxes
          block_y = numpy.mean(img_y[top:bottom, left:right])
          block_r_g = numpy.mean(img_r_g[top:bottom, left:right])
          block_b_g = numpy.mean(img_b_g[top:bottom, left:right])
          max_r_g = max(max_r_g, block_r_g)
          min_r_g = min(min_r_g, block_r_g)
          max_b_g = max(max_b_g, block_b_g)
          min_b_g = min(min_b_g, block_b_g)
          blocks_info.append({'position': [top, bottom, left, right],
                              'block_r_g': block_r_g,
                              'block_b_g': block_b_g})
          # Check lens shading
          ls_thresh_l = _calc_block_lens_shading_thresh_l(
              block_center_x, block_center_y, center_luma, w, h, dist_max)

          if not ls_thresh_h > block_y > ls_thresh_l:
            ls_test_failed.append({'position': [top, bottom, left, right],
                                   'val': block_y,
                                   'thresh_l': ls_thresh_l})
            legend_color = _CV2_RED
          else:
            legend_color = _CV2_GREEN

          # Overlay legend rectangle on lens shading image.
          text_bottom = bottom - text_offset
          cv2.rectangle(img_lens_shading, (left, top), (right, bottom),
                        legend_color, line_width)
          _draw_legend(img_lens_shading, [f'Y: {block_y:.2f}'],
                       [left+text_offset, text_bottom], font_scale,
                       text_offset, legend_color, int(line_width/2))

        # Check color uniformity
        uniformity_r_g = (max_r_g-min_r_g) / center_r_g
        uniformity_b_g = (max_b_g-min_b_g) / center_b_g
        if (uniformity_r_g > _THRESH_UNIFORMITY or
            uniformity_b_g > _THRESH_UNIFORMITY):
          cu_test_failed.append({'position': position,
                                 'uniformity_r_g': uniformity_r_g,
                                 'uniformity_b_g': uniformity_b_g})
          legend_color = _CV2_RED
        else:
          legend_color = _CV2_GREEN

        # Overlay legend blocks on uniformity image based on PASS/FAIL above.
        for block in blocks_info:
          top, bottom, left, right = block['position']
          cv2.rectangle(img_uniformity, (left, top), (right, bottom),
                        legend_color, line_width)
          texts = [f"R/G: {block['block_r_g']:.2f}",
                   f"B/G: {block['block_b_g']:.2f}"]
          text_bottom = bottom - text_offset * 2
          _draw_legend(img_uniformity, texts,
                       [left+text_offset, text_bottom], font_scale,
                       text_offset, legend_color, font_line_width)

      # Save images
      image_processing_utils.write_image(
          img_uniformity, f'{name_with_log_path}_color_uniformity_result.png',
          True)
      image_processing_utils.write_image(
          img_lens_shading, f'{name_with_log_path}_lens_shading_result.png',
          True)

      # Assert results
      _assert_results(ls_test_failed, cu_test_failed, center_luma, ls_thresh_h)


if __name__ == '__main__':
  test_runner.main()
