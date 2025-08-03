# Copyright 2015 The Android Open Source Project
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
"""Verifies android.noiseReduction.mode applied for reprocessing reqs."""


import logging
import math
import os.path
import matplotlib
from matplotlib import pylab
from mobly import test_runner
import numpy as np

import its_base_test
import camera_properties_utils
import capture_request_utils
import image_processing_utils
import its_session_utils
import target_exposure_utils

_COLORS = ('R', 'G', 'B')
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_NR_MODES = {'OFF': 0, 'FAST': 1, 'HQ': 2, 'MIN': 3, 'ZSL': 4}
_NR_MODES_LIST = tuple(_NR_MODES.values())
_NUM_FRAMES = 4
_PATCH_H = 0.1  # center 10%
_PATCH_W = 0.1
_PATCH_X = 0.5 - _PATCH_W/2
_PATCH_Y = 0.5 - _PATCH_H/2
_SNR_TOL = 3  # unit in dB


def calc_rgb_snr(cap, frame, nr_mode, name_with_log_path):
  """Calculate the RGB SNRs from a capture center patch.

  Args:
    cap: Camera capture object.
    frame: Integer frame number.
    nr_mode: Integer noise reduction mode index.
    name_with_log_path: Test name with path for storage

  Returns:
    RGB SNRs.
  """
  img = image_processing_utils.decompress_jpeg_to_rgb_image(cap)
  if frame == 0:  # save 1st frame
    image_processing_utils.write_image(
        img, f'{name_with_log_path}_high_gain_nr={nr_mode}_fmt=jpg.jpg')
  patch = image_processing_utils.get_image_patch(
      img, _PATCH_X, _PATCH_Y, _PATCH_W, _PATCH_H)
  return image_processing_utils.compute_image_snrs(patch)


def create_plot(snrs, reprocess_format, name_with_log_path):
  """create plot from data.

  Args:
    snrs: RGB SNR data from NR_MODES captures.
    reprocess_format: String of 'yuv' or 'private'.
    name_with_log_path: Test name with path for storage.
  """
  pylab.figure(reprocess_format)
  for ch, color in enumerate(_COLORS):
    pylab.plot(_NR_MODES_LIST, snrs[ch], f'-{color.lower()}o')
  pylab.title(f'{_NAME} ({reprocess_format})')
  pylab.xlabel(f'{str(_NR_MODES)[1:-1]}')  # strip '{' '}' off string
  pylab.ylabel('SNR (dB)')
  pylab.xticks(_NR_MODES_LIST)
  matplotlib.pyplot.savefig(
      f'{name_with_log_path}_plot_{reprocess_format}_SNRs.png')


class ReprocessNoiseReductionTest(its_base_test.ItsBaseTest):
  """Test android.noiseReduction.mode is applied for reprocessing requests.

  Uses JPEG captures for the reprocessing as YUV captures are not available.
  Uses high analog gain to ensure the captured images are noisy.

  Determines which reprocessing formats are available among 'yuv' and 'private'.
  For each reprocessing format:
    Captures in supported reprocessed modes.
    Averages _NUM_FRAMES to account for frame-to-frame variation.
    Logs min/max of captures for debug if gross outlier.
    Noise reduction (NR) modes:
      OFF, FAST, High Quality (HQ), Minimal (MIN), and zero shutter lag (ZSL)

    Proper behavior:
      FAST >= OFF, HQ >= FAST, HQ >> OFF
      if MIN mode supported: MIN >= OFF, HQ >= MIN, ZSL ~ MIN
      else: ZSL ~ OFF
  """

  def test_reprocess_noise_reduction(self):
    logging.debug('Starting %s', _NAME)
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

      # Load chart for scene.
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet,
          its_session_utils.CHART_DISTANCE_NO_SCALING)

      # If reprocessing is supported, ZSL NR mode must be available.
      if not camera_properties_utils.noise_reduction_mode(
          props, _NR_MODES['ZSL']):
        raise KeyError('Reprocessing supported, so ZSL must be supported.')

      reprocess_formats = []
      if camera_properties_utils.yuv_reprocess(props):
        reprocess_formats.append('yuv')
      if camera_properties_utils.private_reprocess(props):
        reprocess_formats.append('private')

      size = capture_request_utils.get_available_output_sizes('jpg', props)[0]
      out_surface = {'width': size[0], 'height': size[1], 'format': 'jpg'}
      for reprocess_format in reprocess_formats:
        logging.debug('Reprocess format: %s', reprocess_format)
        # List of variances for R, G, B.
        snrs = [[], [], []]
        nr_modes_reported = []

        # Capture for each mode.
        exp, sens = target_exposure_utils.get_target_exposure_combos(
            log_path, cam)['maxSensitivity']
        for nr_mode in _NR_MODES_LIST:
          # Skip unavailable modes
          if not camera_properties_utils.noise_reduction_mode(props, nr_mode):
            nr_modes_reported.append(nr_mode)
            for ch, _ in enumerate(_COLORS):
              snrs[ch].append(0)
            continue

          # Create req, do caps and calc center SNRs.
          rgb_snr_list = []
          nr_modes_reported.append(nr_mode)
          req = capture_request_utils.manual_capture_request(sens, exp)
          req['android.noiseReduction.mode'] = nr_mode
          caps = cam.do_capture(
              [req]*_NUM_FRAMES, out_surface, reprocess_format)
          for i in range(_NUM_FRAMES):
            rgb_snr_list.append(calc_rgb_snr(caps[i]['data'], i, nr_mode,
                                             name_with_log_path))

          r_snrs = [rgb[0] for rgb in rgb_snr_list]
          g_snrs = [rgb[1] for rgb in rgb_snr_list]
          b_snrs = [rgb[2] for rgb in rgb_snr_list]
          rgb_avg_snrs = [np.mean(r_snrs), np.mean(g_snrs), np.mean(b_snrs)]
          for ch, x_snrs in enumerate([r_snrs, g_snrs, b_snrs]):
            snrs[ch].append(rgb_avg_snrs[ch])
            logging.debug(
                'NR mode %d %s SNR avg: %.2f min: %.2f, max: %.2f', nr_mode,
                _COLORS[ch], rgb_avg_snrs[ch], min(x_snrs), max(x_snrs))

        # Plot data.
        create_plot(snrs, reprocess_format, name_with_log_path)

        # Assert proper behavior.
        if nr_modes_reported != list(_NR_MODES_LIST):
          raise KeyError('Reported modes: '
                         f'{nr_modes_reported}. Expected: {_NR_MODES_LIST}.')
        for j, _ in enumerate(_COLORS):
          # OFF < FAST + TOL
          if snrs[j][_NR_MODES['OFF']] >= snrs[j][_NR_MODES['FAST']]+_SNR_TOL:
            raise AssertionError(f'FAST: {snrs[j][_NR_MODES["FAST"]]:.2f}, '
                                 f'OFF: {snrs[j][_NR_MODES["OFF"]]:.2f}, '
                                 f'TOL: {_SNR_TOL}')

          # FAST < HQ + TOL
          if snrs[j][_NR_MODES['FAST']] >= snrs[j][_NR_MODES['HQ']]+_SNR_TOL:
            raise AssertionError(f'HQ: {snrs[j][_NR_MODES["HQ"]]:.2f}, '
                                 f'FAST: {snrs[j][_NR_MODES["FAST"]]:.2f}, '
                                 f'TOL: {_SNR_TOL}')

          # HQ > OFF
          if snrs[j][_NR_MODES['HQ']] <= snrs[j][_NR_MODES['OFF']]:
            raise AssertionError(f'HQ: {snrs[j][_NR_MODES["HQ"]]:.2f}, '
                                 f'OFF: {snrs[j][_NR_MODES["OFF"]]:.2f}')

          if camera_properties_utils.noise_reduction_mode(
              props, _NR_MODES['MIN']):
            # OFF < MIN + TOL
            if snrs[j][_NR_MODES['OFF']] >= snrs[j][_NR_MODES['MIN']]+_SNR_TOL:
              raise AssertionError(f'MIN: {snrs[j][_NR_MODES["MIN"]]:.2f}, '
                                   f'OFF: {snrs[j][_NR_MODES["OFF"]]:.2f}, '
                                   f'TOL: {_SNR_TOL}')

            # MIN < HQ + TOL
            if snrs[j][_NR_MODES['MIN']] >= snrs[j][_NR_MODES['HQ']]+_SNR_TOL:
              raise AssertionError(f'MIN: {snrs[j][_NR_MODES["MIN"]]:.2f}, '
                                   f'HQ: {snrs[j][_NR_MODES["HQ"]]:.2f}, '
                                   f'TOL: {_SNR_TOL}')

            # ZSL ~ MIN
            if not math.isclose(
                snrs[j][_NR_MODES['ZSL']], snrs[j][_NR_MODES['MIN']],
                abs_tol=_SNR_TOL):
              raise AssertionError(f'ZSL: {snrs[j][_NR_MODES["ZSL"]]:.2f}, '
                                   f'MIN: {snrs[j][_NR_MODES["MIN"]]:.2f}, '
                                   f'TOL: {_SNR_TOL}')
          else:
            # ZSL ~ OFF
            if not math.isclose(
                snrs[j][_NR_MODES['ZSL']], snrs[j][_NR_MODES['OFF']],
                abs_tol=_SNR_TOL):
              raise AssertionError(f'ZSL: {snrs[j][_NR_MODES["ZSL"]]:.2f}, '
                                   f'OFF: {snrs[j][_NR_MODES["OFF"]]:.2f}, '
                                   f'TOL: {_SNR_TOL}')

if __name__ == '__main__':
  test_runner.main()

