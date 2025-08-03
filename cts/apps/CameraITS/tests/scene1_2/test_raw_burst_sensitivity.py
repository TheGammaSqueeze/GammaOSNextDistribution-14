# Copyright 2014 The Android Open Source Project
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
"""Verifies RAW sensitivity burst."""


import logging
import os.path
import matplotlib
from matplotlib import pylab
from mobly import test_runner

import its_base_test
import camera_properties_utils
import capture_request_utils
import image_processing_utils
import its_session_utils

_GR_PLANE_IDX = 1  # GR plane index in RGGB data
_IMG_STATS_GRID = 9  # find used to find the center 11.11%
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_NUM_STEPS = 5
_VAR_THRESH = 1.01  # each shot must be 1% noisier than previous


def define_raw_stats_fmt(props):
  """Defines the format using camera props active array width and height."""
  aax = props['android.sensor.info.preCorrectionActiveArraySize']['left']
  aay = props['android.sensor.info.preCorrectionActiveArraySize']['top']
  aaw = props['android.sensor.info.preCorrectionActiveArraySize']['right'] - aax
  aah = props[
      'android.sensor.info.preCorrectionActiveArraySize']['bottom'] - aay

  return {'format': 'rawStats',
          'gridWidth': aaw // _IMG_STATS_GRID,
          'gridHeight': aah // _IMG_STATS_GRID}


class RawBurstSensitivityTest(its_base_test.ItsBaseTest):
  """Captures a set of RAW images with increasing sensitivity & measures noise.

  Sensitivity range (gain) is determined from camera properties and limited to
  the analog sensitivity range as captures are RAW only in a burst. Digital
  sensitivity range from props['android.sensor.info.sensitivityRange'] is not
  used.

  Uses RawStats capture format to speed up processing. RawStats defines a grid
  over the RAW image and returns average and variance of requested areas.
  white_level is found from camera to normalize variance values from RawStats.

  Noise (image variance) of center patch should increase with increasing
  sensitivity.
  """

  def test_raw_burst_sensitivity(self):
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      camera_properties_utils.skip_unless(
          camera_properties_utils.raw16(props) and
          camera_properties_utils.manual_sensor(props) and
          camera_properties_utils.read_3a(props) and
          camera_properties_utils.per_frame_control(props) and
          not camera_properties_utils.mono_camera(props))
      name_with_log_path = os.path.join(self.log_path, _NAME)

      # Load chart for scene
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet,
          its_session_utils.CHART_DISTANCE_NO_SCALING)

      # Find sensitivity range and create capture requests
      sens_min, _ = props['android.sensor.info.sensitivityRange']
      sens_max = props['android.sensor.maxAnalogSensitivity']
      sens_step = (sens_max - sens_min) // _NUM_STEPS
      # Intentionally blur images for noise measurements
      sens_ae, exp_ae, _, _, _ = cam.do_3a(do_af=False, get_results=True)
      sens_exp_prod = sens_ae * exp_ae
      reqs = []
      settings = []
      for sens in range(sens_min, sens_max, sens_step):
        exp = int(sens_exp_prod / float(sens))
        req = capture_request_utils.manual_capture_request(sens, exp, 0)
        reqs.append(req)
        settings.append((sens, exp))

      # Get rawStats capture format
      fmt = define_raw_stats_fmt(props)

      # Do captures
      caps = cam.do_capture(reqs, fmt)

      # Extract variances from each shot
      variances = []
      for i, cap in enumerate(caps):
        (sens, exp) = settings[i]

        # Find white_level for RawStats normalization
        white_level = float(props['android.sensor.info.whiteLevel'])
        _, var_image = image_processing_utils.unpack_rawstats_capture(cap)
        cfa_idxs = image_processing_utils.get_canonical_cfa_order(props)
        var = var_image[_IMG_STATS_GRID//2, _IMG_STATS_GRID//2,
                        cfa_idxs[_GR_PLANE_IDX]]/white_level**2
        variances.append(var)
        logging.debug('s=%d, e=%d, var=%e', sens, exp, var)

      # Create a plot
      x = range(len(variances))
      pylab.figure(_NAME)
      pylab.plot(x, variances, '-ro')
      pylab.xticks(x)
      pylab.ticklabel_format(style='sci', axis='y', scilimits=(-6, -6))
      pylab.xlabel('Setting Combination')
      pylab.ylabel('Image Center Patch Variance')
      pylab.title(_NAME)
      matplotlib.pyplot.savefig(
          f'{name_with_log_path}_variances.png')

      # Assert each shot is noisier than previous and save img on FAIL
      for i in x[0:-1]:
        if variances[i] >= variances[i+1] / _VAR_THRESH:
          image_processing_utils.capture_scene_image(
              cam, props, name_with_log_path)
          raise AssertionError(
              f'variances [i]: {variances[i] :.5f}, [i+1]: '
              f'{variances[i+1]:.5f}, THRESH: {_VAR_THRESH}')

if __name__ == '__main__':
  test_runner.main()
