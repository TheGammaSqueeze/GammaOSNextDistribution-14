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
"""Tests for image_fov_utils."""

import math
import unittest

import image_fov_utils


class ImageFovUtilsTest(unittest.TestCase):
  """Unit tests for this module."""

  def test_calc_expected_circle_image_ratio(self):
    """Unit test for calc_expected_circle_image_ratio.

    Test by using 5% area circle in VGA cropped to nHD format
    """
    ref_fov = {'w': 640, 'h': 480, 'percent': 5}
    # nHD format cut down
    img_w, img_h = 640, 360
    nhd = image_fov_utils.calc_expected_circle_image_ratio(
        ref_fov, img_w, img_h)
    self.assertTrue(math.isclose(nhd, 5*480/360, abs_tol=0.01))

  def test_check_ar(self):
    """Unit test for aspect ratio check."""
    # Circle true
    circle = {'w': 1, 'h': 1}
    ar_gt = 1.0
    w, h = 640, 480
    e_msg_stem = 'check_ar_true'
    e_msg = image_fov_utils.check_ar(circle, ar_gt, w, h, e_msg_stem)
    self.assertIsNone(e_msg)

    # Circle false
    circle = {'w': 2, 'h': 1}
    e_msg_stem = 'check_ar_false'
    e_msg = image_fov_utils.check_ar(circle, ar_gt, w, h, e_msg_stem)
    self.assertIn('check_ar_false', e_msg)

  def test_check_crop(self):
    """Unit test for crop check."""
    # Crop true
    circle = {'w': 100, 'h': 100, 'x_offset': 1, 'y_offset': 1}
    cc_gt = {'hori': 1.0, 'vert': 1.0}
    w, h = 640, 480
    e_msg_stem = 'check_crop_true'
    crop_thresh_factor = 1
    e_msg = image_fov_utils.check_crop(circle, cc_gt, w, h,
                                       e_msg_stem, crop_thresh_factor)
    self.assertIsNone(e_msg)

    # Crop false
    circle = {'w': 100, 'h': 100, 'x_offset': 2, 'y_offset': 1}
    e_msg_stem = 'check_crop_false'
    e_msg = image_fov_utils.check_crop(circle, cc_gt, w, h,
                                       e_msg_stem, crop_thresh_factor)
    self.assertIn('check_crop_false', e_msg)

if __name__ == '__main__':
  unittest.main()

