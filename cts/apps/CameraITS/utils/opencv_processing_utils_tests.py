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
"""Tests for opencv_processing_utils."""


import math
import os
import unittest

import cv2

import opencv_processing_utils


class Cv2ImageProcessingUtilsTests(unittest.TestCase):
  """Unit tests for this module."""

  def test_get_angle_identify_rotated_chessboard_angle(self):
    """Unit test to check extracted angles from images."""
    # Array of the image files and angles containing rotated chessboards.
    test_cases = [
        ('', 0),
        ('_15_ccw', -15),
        ('_30_ccw', -30),
        ('_45_ccw', -45),
        ('_60_ccw', -60),
        ('_75_ccw', -75),
    ]
    test_fails = ''

    # For each rotated image pair (normal, wide), check angle against expected.
    for suffix, angle in test_cases:
      # Define image paths.
      normal_img_path = os.path.join(
          opencv_processing_utils.TEST_IMG_DIR,
          f'rotated_chessboards/normal{suffix}.jpg')
      wide_img_path = os.path.join(
          opencv_processing_utils.TEST_IMG_DIR,
          f'rotated_chessboards/wide{suffix}.jpg')

      # Load and color-convert images.
      normal_img = cv2.cvtColor(cv2.imread(normal_img_path), cv2.COLOR_BGR2GRAY)
      wide_img = cv2.cvtColor(cv2.imread(wide_img_path), cv2.COLOR_BGR2GRAY)

      # Assert angle as expected.
      normal = opencv_processing_utils.get_angle(normal_img)
      wide = opencv_processing_utils.get_angle(wide_img)
      valid_angles = (angle, angle+90)  # try both angle & +90 due to squares
      e_msg = (f'\n Rotation angle test failed: {angle}, extracted normal: '
               f'{normal:.2f}, wide: {wide:.2f}, valid_angles: {valid_angles}')
      matched_angles = False
      for a in valid_angles:
        if (math.isclose(normal, a,
                         abs_tol=opencv_processing_utils.ANGLE_CHECK_TOL) and
            math.isclose(wide, a,
                         abs_tol=opencv_processing_utils.ANGLE_CHECK_TOL)):
          matched_angles = True

      if not matched_angles:
        test_fails += e_msg

    self.assertEqual(len(test_fails), 0, test_fails)


if __name__ == '__main__':
  unittest.main()
