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
"""Verifies image is not flipped or mirrored."""


import logging
import os

import cv2
from mobly import test_runner
import numpy as np

import its_base_test
import camera_properties_utils
import capture_request_utils
import image_processing_utils
import its_session_utils
import opencv_processing_utils

_CHART_ORIENTATIONS = ['nominal', 'flip', 'mirror', 'rotate']
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_PATCH_H = 0.5  # center 50%
_PATCH_W = 0.5
_PATCH_X = 0.5 - _PATCH_W/2
_PATCH_Y = 0.5 - _PATCH_H/2
_VGA_W, _VGA_H = 640, 480


def test_flip_mirror_impl(cam, props, fmt, chart, first_api_level,
                          name_with_log_path):

  """Return if image is flipped or mirrored.

  Args:
   cam: An open its session.
   props: Properties of cam.
   fmt: dict; capture format.
   chart: Object with chart properties.
   first_api_level: int; first API level value.
   name_with_log_path: file with log_path to save the captured image.

  Returns:
    boolean: True if flipped, False if not
  """
  # get a local copy of the chart template
  template = cv2.imread(opencv_processing_utils.CHART_FILE, cv2.IMREAD_ANYDEPTH)

  # take img, crop chart, scale and prep for cv2 template match
  cam.do_3a()
  req = capture_request_utils.auto_capture_request()
  cap = cam.do_capture(req, fmt)
  y, _, _ = image_processing_utils.convert_capture_to_planes(cap, props)
  y = image_processing_utils.rotate_img_per_argv(y)
  patch = image_processing_utils.get_image_patch(y, chart.xnorm, chart.ynorm,
                                                 chart.wnorm, chart.hnorm)
  patch = 255 * opencv_processing_utils.gray_scale_img(patch)
  patch = opencv_processing_utils.scale_img(
      patch.astype(np.uint8), chart.scale)

  # check image has content
  if np.max(patch)-np.min(patch) < 255/8:
    raise AssertionError('Image patch has no content! Check setup.')

  # save full image & patch
  image_processing_utils.write_image(template[:, :, np.newaxis] / 255.0,
                                     f'{name_with_log_path}_template.jpg')
  image_processing_utils.write_image(patch[:, :, np.newaxis] / 255.0,
                                     f'{name_with_log_path}_scene_patch.jpg')

  # crop center areas and strip off any extra rows/columns
  template = image_processing_utils.get_image_patch(
      template, _PATCH_X, _PATCH_Y, _PATCH_W, _PATCH_H)
  patch = image_processing_utils.get_image_patch(
      patch, _PATCH_X, _PATCH_Y, _PATCH_W, _PATCH_H)
  patch = patch[0:min(patch.shape[0], template.shape[0]),
                0:min(patch.shape[1], template.shape[1])]
  comp_chart = patch

  # determine optimum orientation
  opts = []
  imgs = []
  for orientation in _CHART_ORIENTATIONS:
    if orientation == 'flip':
      comp_chart = np.flipud(patch)
    elif orientation == 'mirror':
      comp_chart = np.fliplr(patch)
    elif orientation == 'rotate':
      comp_chart = np.flipud(np.fliplr(patch))
    correlation = cv2.matchTemplate(comp_chart, template, cv2.TM_CCOEFF)
    _, opt_val, _, _ = cv2.minMaxLoc(correlation)
    imgs.append(comp_chart)
    logging.debug('%s correlation value: %d', orientation, opt_val)
    opts.append(opt_val)

  # assert correct behavior
  if opts[0] != max(opts):  # 'nominal' is not best orientation
    for i, orientation in enumerate(_CHART_ORIENTATIONS):
      cv2.imwrite(f'{name_with_log_path}_{orientation}.jpg', imgs[i])

    if first_api_level < its_session_utils.ANDROID15_API_LEVEL:
      if opts[3] != max(opts):  # allow 'rotated' < ANDROID15
        raise AssertionError(
            f'Optimum orientation is {_CHART_ORIENTATIONS[np.argmax(opts)]}')
      else:
        logging.warning('Image rotated 180 degrees. Tablet might be rotated.')
    else:  # no rotation >= ANDROID15
      raise AssertionError(
          f'Optimum orientation is {_CHART_ORIENTATIONS[np.argmax(opts)]}')


class FlipMirrorTest(its_base_test.ItsBaseTest):
  """Test to verify if the image is flipped or mirrored."""

  def test_flip_mirror(self):
    """Test if image is properly oriented."""
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      name_with_log_path = os.path.join(self.log_path, _NAME)
      first_api_level = its_session_utils.get_first_api_level(self.dut.serial)

      # check SKIP conditions
      camera_properties_utils.skip_unless(
          not camera_properties_utils.mono_camera(props))

      # load chart for scene
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet, self.chart_distance)

      # initialize chart class and locate chart in scene
      chart = opencv_processing_utils.Chart(
          cam, props, self.log_path, distance=self.chart_distance)
      fmt = {'format': 'yuv', 'width': _VGA_W, 'height': _VGA_H}

      # test that image is not flipped, mirrored, or rotated
      test_flip_mirror_impl(cam, props, fmt, chart, first_api_level,
                            name_with_log_path)


if __name__ == '__main__':
  test_runner.main()
