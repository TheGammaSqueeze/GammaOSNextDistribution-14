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
"""Tests for image_processing_utils."""


import math
import os
import random
import unittest

import cv2
import numpy
from PIL import Image

import image_processing_utils


class ImageProcessingUtilsTest(unittest.TestCase):
  """Unit tests for this module."""
  _SQRT_2 = numpy.sqrt(2)
  _YUV_FULL_SCALE = 1023

  def test_unpack_raw10_image(self):
    """Unit test for unpack_raw10_image.

    RAW10 bit packing format
            bit 7   bit 6   bit 5   bit 4   bit 3   bit 2   bit 1   bit 0
    Byte 0: P0[9]   P0[8]   P0[7]   P0[6]   P0[5]   P0[4]   P0[3]   P0[2]
    Byte 1: P1[9]   P1[8]   P1[7]   P1[6]   P1[5]   P1[4]   P1[3]   P1[2]
    Byte 2: P2[9]   P2[8]   P2[7]   P2[6]   P2[5]   P2[4]   P2[3]   P2[2]
    Byte 3: P3[9]   P3[8]   P3[7]   P3[6]   P3[5]   P3[4]   P3[3]   P3[2]
    Byte 4: P3[1]   P3[0]   P2[1]   P2[0]   P1[1]   P1[0]   P0[1]   P0[0]
    """
    # Test using a random 4x4 10-bit image
    img_w, img_h = 4, 4
    check_list = random.sample(range(0, 1024), img_h*img_w)
    img_check = numpy.array(check_list).reshape(img_h, img_w)

    # Pack bits
    for row_start in range(0, len(check_list), img_w):
      msbs = []
      lsbs = ''
      for pixel in range(img_w):
        val = numpy.binary_repr(check_list[row_start+pixel], 10)
        msbs.append(int(val[:8], base=2))
        lsbs = val[8:] + lsbs
      packed = msbs
      packed.append(int(lsbs, base=2))
      chunk_raw10 = numpy.array(packed, dtype='uint8').reshape(1, 5)
      if row_start == 0:
        img_raw10 = chunk_raw10
      else:
        img_raw10 = numpy.vstack((img_raw10, chunk_raw10))

    # Unpack and check against original
    self.assertTrue(numpy.array_equal(
        image_processing_utils.unpack_raw10_image(img_raw10),
        img_check))

  def test_compute_image_sharpness(self):
    """Unit test for compute_img_sharpness.

    Tests by using PNG of ISO12233 chart and blurring intentionally.
    'sharpness' should drop off by sqrt(2) for 2x blur of image.

    We do one level of initial blur as PNG image is not perfect.
    """
    blur_levels = [2, 4, 8]
    chart_file = os.path.join(
        image_processing_utils.TEST_IMG_DIR, 'ISO12233.png')
    chart = cv2.imread(chart_file, cv2.IMREAD_ANYDEPTH)
    white_level = numpy.amax(chart).astype(float)
    sharpness = {}
    for blur in blur_levels:
      chart_blurred = cv2.blur(chart, (blur, blur))
      chart_blurred = chart_blurred[:, :, numpy.newaxis]
      sharpness[blur] = (self._YUV_FULL_SCALE
                         * image_processing_utils.compute_image_sharpness(
                             chart_blurred / white_level))

    for i in range(len(blur_levels)-1):
      self.assertTrue(math.isclose(
          sharpness[blur_levels[i]]/sharpness[blur_levels[i+1]], self._SQRT_2,
          abs_tol=0.1))

  def test_apply_lut_to_image(self):
    """Unit test for apply_lut_to_image.

    Test by using a canned set of values on a 1x1 pixel image.
    The look-up table should double the value of the index: lut[x] = x*2
    """
    ref_image = [0.1, 0.2, 0.3]
    lut_max = 65536
    lut = numpy.array([i*2 for i in range(lut_max)])
    x = numpy.array(ref_image).reshape((1, 1, 3))
    y = image_processing_utils.apply_lut_to_image(x, lut).reshape(3).tolist()
    y_ref = [i*2 for i in ref_image]
    self.assertTrue(numpy.allclose(y, y_ref, atol=1/lut_max))

  def test_p3_img_has_wide_gamut(self):
    # (255, 0, 0) and (0, 255, 0) in sRGB converted to Display P3
    srgb_red = numpy.array([[[234, 51, 35]]], dtype='uint8')
    srgb_green = numpy.array([[[117, 252, 76]]], dtype='uint8')

    # Maximum blue is the same in both sRGB and Display P3
    blue = numpy.array([[[0, 0, 255]]], dtype='uint8')

    # Max red and green in Display P3
    p3_red = numpy.array([[[255, 0, 0]]], dtype='uint8')
    p3_green = numpy.array([[[0, 255, 0]]], dtype='uint8')

    self.assertFalse(image_processing_utils.p3_img_has_wide_gamut(
        Image.fromarray(srgb_red)))

    self.assertFalse(image_processing_utils.p3_img_has_wide_gamut(
        Image.fromarray(srgb_green)))

    self.assertFalse(image_processing_utils.p3_img_has_wide_gamut(
        Image.fromarray(blue)))

    self.assertTrue(image_processing_utils.p3_img_has_wide_gamut(
        Image.fromarray(p3_red)))

    self.assertTrue(image_processing_utils.p3_img_has_wide_gamut(
        Image.fromarray(p3_green)))

if __name__ == '__main__':
  unittest.main()
