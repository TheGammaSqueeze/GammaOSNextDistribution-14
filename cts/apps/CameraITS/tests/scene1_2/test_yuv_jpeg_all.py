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
"""Verifies YUV & JPEG image captures have similar brightness."""


import logging
import os.path
import matplotlib
from matplotlib import pylab
import matplotlib.lines as mlines
from matplotlib.ticker import MaxNLocator
from mobly import test_runner

import its_base_test
import camera_properties_utils
import capture_request_utils
import image_processing_utils
import its_session_utils
import target_exposure_utils

_JPG_STR = 'jpg'
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_PATCH_H = 0.1  # center 10%
_PATCH_W = 0.1
_PATCH_X = 0.5 - _PATCH_W/2
_PATCH_Y = 0.5 - _PATCH_H/2
_PLOT_ALPHA = 0.5
_PLOT_MARKER_SIZE = 8
_PLOT_LEGEND_CIRCLE_SIZE = 10
_PLOT_LEGEND_TRIANGLE_SIZE = 6
_THRESHOLD_MAX_RMS_DIFF = 0.03
_YUV_STR = 'yuv'


def do_capture_and_extract_rgb_means(
    req, cam, props, size, img_type, index, name_with_log_path, debug):
  """Do capture and extra rgb_means of center patch.

  Args:
    req: capture request
    cam: camera object
    props: camera properties dict
    size: [width, height]
    img_type: string of 'yuv' or 'jpeg'
    index: index to track capture number of img_type
    name_with_log_path: file name and location for saving image
    debug: boolean to flag saving captured images

  Returns:
    rgb: center patch RGB means
    img: RGB image array
  """
  out_surface = {'width': size[0], 'height': size[1], 'format': img_type}
  if camera_properties_utils.stream_use_case(props):
    out_surface['useCase'] = camera_properties_utils.USE_CASE_STILL_CAPTURE
  logging.debug('output surface: %s', str(out_surface))
  if debug and camera_properties_utils.raw(props):
    out_surfaces = [{'format': 'raw'}, out_surface]
    cap_raw, cap = cam.do_capture(req, out_surfaces)
    img_raw = image_processing_utils.convert_capture_to_rgb_image(
        cap_raw, props=props)
    image_processing_utils.write_image(
        img_raw,
        f'{name_with_log_path}_raw_{img_type}_w{size[0]}_h{size[1]}.png', True)
  else:
    cap = cam.do_capture(req, out_surface)
  logging.debug('e_cap: %d, s_cap: %d, f_distance: %s',
                cap['metadata']['android.sensor.exposureTime'],
                cap['metadata']['android.sensor.sensitivity'],
                cap['metadata']['android.lens.focusDistance'])
  if img_type == _JPG_STR:
    if cap['format'] != 'jpeg':
      raise AssertionError(f"{cap['format']} != jpeg")
    img = image_processing_utils.decompress_jpeg_to_rgb_image(cap['data'])
  else:
    if cap['format'] != img_type:
      raise AssertionError(f"{cap['format']} != {img_type}")
    img = image_processing_utils.convert_capture_to_rgb_image(cap)
  if cap['width'] != size[0]:
    raise AssertionError(f"{cap['width']} != {size[0]}")
  if cap['height'] != size[1]:
    raise AssertionError(f"{cap['height']} != {size[1]}")

  if img_type == _JPG_STR:
    if img.shape[0] != size[1]:
      raise AssertionError(f'{img.shape[0]} != {size[1]}')
    if img.shape[1] != size[0]:
      raise AssertionError(f'{img.shape[1]} != {size[0]}')
    if img.shape[2] != 3:
      raise AssertionError(f'{img.shape[2]} != 3')
  patch = image_processing_utils.get_image_patch(
      img, _PATCH_X, _PATCH_Y, _PATCH_W, _PATCH_H)
  rgb = image_processing_utils.compute_image_means(patch)
  logging.debug('Captured %s %dx%d rgb = %s, format number = %d',
                img_type, cap['width'], cap['height'], str(rgb), index)
  return rgb, img


class YuvJpegAllTest(its_base_test.ItsBaseTest):
  """Test reported sizes & fmts for YUV & JPEG caps return similar images."""

  def test_yuv_jpeg_all(self):
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)

      log_path = self.log_path
      debug = self.debug_mode
      name_with_log_path = os.path.join(log_path, _NAME)

      # Check SKIP conditions
      camera_properties_utils.skip_unless(
          camera_properties_utils.linear_tonemap(props))

      # Load chart for scene
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet,
          its_session_utils.CHART_DISTANCE_NO_SCALING)

      # If device supports target exposure computation, use manual capture.
      # Otherwise, do 3A, then use an auto request.
      # Both requests use a linear tonemap and focus distance of 0.0
      # so that the YUV and JPEG should look the same
      # (once converted by the image_processing_utils).
      if camera_properties_utils.compute_target_exposure(props):
        logging.debug('Using manual capture request')
        e, s = target_exposure_utils.get_target_exposure_combos(
            log_path, cam)['midExposureTime']
        logging.debug('e_req: %d, s_req: %d', e, s)
        req = capture_request_utils.manual_capture_request(
            s, e, 0.0, True, props)
        match_ar = None
      else:
        logging.debug('Using auto capture request')
        cam.do_3a(do_af=False)
        req = capture_request_utils.auto_capture_request(
            linear_tonemap=True, props=props, do_af=False)
        largest_yuv = capture_request_utils.get_largest_yuv_format(props)
        match_ar = (largest_yuv['width'], largest_yuv['height'])

      yuv_rgbs = []
      yuv_imgs = []
      for i, size in enumerate(
          capture_request_utils.get_available_output_sizes(
              _YUV_STR, props, match_ar_size=match_ar)):
        yuv_rgb, yuv_img = do_capture_and_extract_rgb_means(
            req, cam, props, size, _YUV_STR, i, name_with_log_path, debug)
        yuv_rgbs.append(yuv_rgb)
        yuv_imgs.append(yuv_img)

      jpg_rgbs = []
      jpg_imgs = []
      for i, size in enumerate(
          capture_request_utils.get_available_output_sizes(
              _JPG_STR, props, match_ar_size=match_ar)):
        jpg_rgb, jpg_img = do_capture_and_extract_rgb_means(
            req, cam, props, size, _JPG_STR, i, name_with_log_path, debug)
        jpg_rgbs.append(jpg_rgb)
        jpg_imgs.append(jpg_img)

      # Plot means vs format
      pylab.figure(_NAME)
      pylab.title(_NAME)
      yuv_index = range(len(yuv_rgbs))
      jpg_index = range(len(jpg_rgbs))
      pylab.plot(yuv_index, [rgb[0] for rgb in yuv_rgbs],
                 '-ro', alpha=_PLOT_ALPHA, markersize=_PLOT_MARKER_SIZE)
      pylab.plot(yuv_index, [rgb[1] for rgb in yuv_rgbs],
                 '-go', alpha=_PLOT_ALPHA, markersize=_PLOT_MARKER_SIZE)
      pylab.plot(yuv_index, [rgb[2] for rgb in yuv_rgbs],
                 '-bo', alpha=_PLOT_ALPHA, markersize=_PLOT_MARKER_SIZE)
      pylab.plot(jpg_index, [rgb[0] for rgb in jpg_rgbs],
                 '-r^', alpha=_PLOT_ALPHA, markersize=_PLOT_MARKER_SIZE)
      pylab.plot(jpg_index, [rgb[1] for rgb in jpg_rgbs],
                 '-g^', alpha=_PLOT_ALPHA, markersize=_PLOT_MARKER_SIZE)
      pylab.plot(jpg_index, [rgb[2] for rgb in jpg_rgbs],
                 '-b^', alpha=_PLOT_ALPHA, markersize=_PLOT_MARKER_SIZE)
      pylab.ylim([0, 1])
      ax = pylab.gca()
      ax.xaxis.set_major_locator(MaxNLocator(integer=True))  # x-axis integers
      yuv_marker = mlines.Line2D([], [], linestyle='None',
                                 color='black', marker='.',
                                 markersize=_PLOT_LEGEND_CIRCLE_SIZE,
                                 label='YUV')
      jpg_marker = mlines.Line2D([], [], linestyle='None',
                                 color='black', marker='^',
                                 markersize=_PLOT_LEGEND_TRIANGLE_SIZE,
                                 label='JPEG')
      ax.legend(handles=[yuv_marker, jpg_marker])
      pylab.xlabel('format number')
      pylab.ylabel('RGB avg [0, 1]')
      matplotlib.pyplot.savefig(f'{name_with_log_path}_plot_means.png')

      # Assert all captures are similar in RGB space using rgbs[0] as ref.
      rgbs = yuv_rgbs + jpg_rgbs
      max_diff = 0
      for rgb_i in rgbs[1:]:
        rms_diff = image_processing_utils.compute_image_rms_difference_1d(
            rgbs[0], rgb_i)  # use first capture as reference
        max_diff = max(max_diff, rms_diff)
      msg = f'Max RMS difference: {max_diff:.4f}'
      logging.debug('%s', msg)
      if max_diff >= _THRESHOLD_MAX_RMS_DIFF:
        for img in yuv_imgs:
          image_processing_utils.write_image(
              img,
              f'{name_with_log_path}_yuv_{img.shape[1]}x{img.shape[0]}.png'
          )
        for img in jpg_imgs:
          image_processing_utils.write_image(
              img,
              f'{name_with_log_path}_jpg_{img.shape[1]}x{img.shape[0]}.png'
          )
        raise AssertionError(f'{msg} spec: {_THRESHOLD_MAX_RMS_DIFF}')

if __name__ == '__main__':
  test_runner.main()
