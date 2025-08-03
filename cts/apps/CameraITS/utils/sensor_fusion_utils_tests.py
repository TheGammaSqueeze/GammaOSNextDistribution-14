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
"""Tests for sensor_fusion_utils."""

import math
import unittest

import numpy as np
from scipy.optimize import fmin

import sensor_fusion_utils


class SensorFusionUtilsTests(unittest.TestCase):
  """Run a suite of unit tests on sensor_fusion_utils."""

  _CAM_FRAME_TIME = 30 * sensor_fusion_utils._MSEC_TO_NSEC  # Similar to 30FPS
  _CAM_ROT_AMPLITUDE = 0.04  # Empirical number for rotation per frame (rads/s).

  def _generate_pwl_waveform(self, pts, step, amplitude):
    """Helper function to generate piece wise linear waveform."""
    pwl_waveform = []
    for t in range(pts[0], pts[1], step):
      pwl_waveform.append(0)
    for t in range(pts[1], pts[2], step):
      pwl_waveform.append((t-pts[1])/(pts[2]-pts[1])*amplitude)
    for t in range(pts[2], pts[3], step):
      pwl_waveform.append(amplitude)
    for t in range(pts[3], pts[4], step):
      pwl_waveform.append((pts[4]-t)/(pts[4]-pts[3])*amplitude)
    for t in range(pts[4], pts[5], step):
      pwl_waveform.append(0)
    for t in range(pts[5], pts[6], step):
      pwl_waveform.append((-1*(t-pts[5])/(pts[6]-pts[5]))*amplitude)
    for t in range(pts[6], pts[7], step):
      pwl_waveform.append(-1*amplitude)
    for t in range(pts[7], pts[8], step):
      pwl_waveform.append((t-pts[8])/(pts[8]-pts[7])*amplitude)
    for t in range(pts[8], pts[9], step):
      pwl_waveform.append(0)
    return pwl_waveform

  def _generate_test_waveforms(self, gyro_sampling_rate, t_offset=0):
    """Define ideal camera/gryo behavior.

    Args:
      gyro_sampling_rate: Value in samples/sec.
      t_offset: Value in ns for gyro/camera timing offset.

    Returns:
      cam_times: numpy array of camera times N values long.
      cam_rots: numpy array of camera rotations N-1 values long.
      gyro_events: list of dicts of gyro events N*gyro_sampling_rate/30 long.

    Round trip for motor is ~2 seconds (~60 frames)
            1111111111111111
           i                i
          i                  i
         i                    i
     0000                      0000                      0000
                                   i                    i
                                    i                  i
                                     i                i
                                      -1-1-1-1-1-1-1-1
    t_0 t_1 t_2           t_3 t_4 t_5 t_6           t_7 t_8 t_9

    Note gyro waveform must extend +/- _CORR_TIME_OFFSET_MAX to enable shifting
    of camera waveform to find best correlation.

    """

    t_ramp = 4 * self._CAM_FRAME_TIME
    pts = {}
    pts[0] = 3 * self._CAM_FRAME_TIME
    pts[1] = pts[0] + 3 * self._CAM_FRAME_TIME
    pts[2] = pts[1] + t_ramp
    pts[3] = pts[2] + 32 * self._CAM_FRAME_TIME
    pts[4] = pts[3] + t_ramp
    pts[5] = pts[4] + 4 * self._CAM_FRAME_TIME
    pts[6] = pts[5] + t_ramp
    pts[7] = pts[6] + 32 * self._CAM_FRAME_TIME
    pts[8] = pts[7] + t_ramp
    pts[9] = pts[8] + 4 * self._CAM_FRAME_TIME
    cam_times = np.array(range(pts[0], pts[9], self._CAM_FRAME_TIME))
    cam_rots = self._generate_pwl_waveform(
        pts, self._CAM_FRAME_TIME, self._CAM_ROT_AMPLITUDE)
    cam_rots.pop()  # rots is N-1 for N length times.
    cam_rots = np.array(cam_rots)

    # Generate gyro waveform.
    gyro_step = int(round(
        sensor_fusion_utils._SEC_TO_NSEC/gyro_sampling_rate, 0))
    gyro_pts = {k: v+t_offset+self._CAM_FRAME_TIME//2 for k, v in pts.items()}
    gyro_pts[0] = 0  # adjust end pts to bound camera
    gyro_pts[9] += self._CAM_FRAME_TIME*2  # adjust end pt to bound camera
    gyro_rot_amplitude = (self._CAM_ROT_AMPLITUDE / self._CAM_FRAME_TIME
                          * sensor_fusion_utils._SEC_TO_NSEC)
    gyro_rots = self._generate_pwl_waveform(
        gyro_pts, gyro_step, gyro_rot_amplitude)

    # Create gyro events list of dicts.
    gyro_events = []
    for i, t in enumerate(range(gyro_pts[0], gyro_pts[9], gyro_step)):
      gyro_events.append({'time': t, 'z': gyro_rots[i]})

    return cam_times, cam_rots, gyro_events

  def test_get_gyro_rotations(self):
    """Tests that gyro rotations are masked properly by camera rotations.

    Note that waveform ideal waveform generation only works properly with
    integer multiples of frame rate.
    """
    # Run with different sampling rates to validate.
    for gyro_sampling_rate in [200, 1000]:  # 6x, 30x frame rate
      cam_times, cam_rots, gyro_events = self._generate_test_waveforms(
          gyro_sampling_rate)
      gyro_rots = sensor_fusion_utils.get_gyro_rotations(
          gyro_events, cam_times)
      e_msg = f'gyro sampling rate = {gyro_sampling_rate}\n'
      e_msg += f'cam_times = {list(cam_times)}\n'
      e_msg += f'cam_rots = {list(cam_rots)}\n'
      e_msg += f'gyro_rots = {list(gyro_rots)}'

      self.assertTrue(np.allclose(
          gyro_rots, cam_rots, atol=self._CAM_ROT_AMPLITUDE*0.10), e_msg)

  def test_get_best_alignment_offset(self):
    """Unit test for alignment offset check."""

    gyro_sampling_rate = 5000
    for t_offset_ms in [0, 1]:  # Run with different offsets to validate.
      t_offset = int(t_offset_ms * sensor_fusion_utils._MSEC_TO_NSEC)
      cam_times, cam_rots, gyro_events = self._generate_test_waveforms(
          gyro_sampling_rate, t_offset)

      (
          best_fit_offset, coeffs, x, y
      ) = sensor_fusion_utils.get_best_alignment_offset(
          cam_times, cam_rots, gyro_events)
      e_msg = f'best: {best_fit_offset} ms\n'
      e_msg += f'coeffs: {coeffs}\n'
      e_msg += f'x: {x}\n'
      e_msg += f'y: {y}'
      self.assertTrue(
          math.isclose(t_offset_ms, best_fit_offset, abs_tol=0.1), e_msg)

  def test_polynomial_from_coefficients(self):
    """Unit test to check polynomial function generated from coefficients."""
    # -2x^4 + 3x^3 + 4x^2 + 5x - 6
    function_1 = sensor_fusion_utils.polynomial_from_coefficients(
        [-2, 3, 4, 5, -6])
    # 0.3x^2 - 0.6x + 0.9
    function_2 = sensor_fusion_utils.polynomial_from_coefficients(
        [0.3, -0.6, 0.9])
    # -7x + 8
    function_3 = sensor_fusion_utils.polynomial_from_coefficients([-7, -8])
    for x in np.arange(-1.1, 1.1, 0.05):
      self.assertEqual(function_1(x),
                       -2 * x ** 4 + 3 * x ** 3 + 4 * x ** 2 + 5 * x + -6)
      self.assertEqual(function_2(x), 0.3 * x ** 2 + -0.6 * x + 0.9)
      self.assertEqual(function_3(x), -7 * x + -8)

  def test_smallest_absolute_minimum_of_polynomial(self):
    """Unit test for the derivative method to find minima."""
    # List of polynomials where an x near zero locally minimizes the function
    polynomials = ((-1e-9, 2e-9, 3e-10, 4e-11),
                   (2e-8, -3e-4, 4e-6),
                   (5, 6, -7, 8),
                   (1e-4, 2e-8, 3e-12),
                   # ideal data from historical results
                   (1.44638577e-05, -6.75789581e-06, 1.02377194e-05))
    for coefficients in polynomials:
      derivative_minimum = (
          sensor_fusion_utils.smallest_absolute_minimum_of_polynomial(
              coefficients))
      # x0=0 because the desired result is the smallest absolute minimum
      scipy_minimum = fmin(
          sensor_fusion_utils.polynomial_from_coefficients(coefficients),
          x0=0, xtol=1e-10)[0]
      self.assertAlmostEqual(
          derivative_minimum,
          scipy_minimum,
          msg='Minimum value for polynomial function described by'
              f' {coefficients} was expected to be'
              f' {derivative_minimum}, received'
              f' {scipy_minimum} via scipy.optimize.fmin.')


if __name__ == '__main__':
  unittest.main()
