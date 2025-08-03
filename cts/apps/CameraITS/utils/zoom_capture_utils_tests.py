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
"""Tests for zoom_capture_utils."""


import unittest

import zoom_capture_utils


class ZoomCaptureUtilsTest(unittest.TestCase):
  """Unit tests for this module."""

  def test_verify_zoom_results(self):
    """Unit test for verify_zoom_results method."""
    # Create ideal zoom results
    focal_length = 1
    img_size = (640, 480)
    zoom_max = 4
    zoom_min = 1
    offset_tol = 0.1
    radius_tol = 0.1
    zoom_results = {}
    for i in (zoom_min, zoom_min+1, zoom_max-1, zoom_max):
      circle = [img_size[0]/2, img_size[1]/2, i]  # x, y, r
      zoom_results[i-1] = {'z': i, 'circle': circle, 'r_tol': radius_tol,
                           'o_tol': offset_tol, 'fl': focal_length}

    # Check basic functions
    self.assertTrue(zoom_capture_utils.verify_zoom_results(
        zoom_results, img_size, zoom_max, zoom_min))

    # Check not enough zoom
    self.assertFalse(zoom_capture_utils.verify_zoom_results(
        zoom_results, img_size, zoom_max+1, zoom_min))

    # Check wrong zoom
    zoom_results[zoom_max-1]['z'] = zoom_max+1
    self.assertFalse(zoom_capture_utils.verify_zoom_results(
        zoom_results, img_size, zoom_max, zoom_min))
    zoom_results[zoom_max-1]['z'] = zoom_max

    # Check wrong offset
    zoom_results[zoom_max-1]['circle'][0] = img_size[0]
    self.assertFalse(zoom_capture_utils.verify_zoom_results(
        zoom_results, img_size, zoom_max, zoom_min))


if __name__ == '__main__':
  unittest.main()
