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
"""Verifies post RAW sensitivity boost."""


import logging
import math
import os.path
import matplotlib
from matplotlib import pylab
from mobly import test_runner

import its_base_test
import camera_properties_utils
import capture_request_utils
import error_util
import image_processing_utils
import its_session_utils
import target_exposure_utils

_COLORS = ('R', 'G', 'B')
_MAX_YUV_SIZE = (1920, 1080)
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_PATCH_H = 0.1  # center 10%
_PATCH_W = 0.1
_PATCH_X = 0.5 - _PATCH_W/2
_PATCH_Y = 0.5 - _PATCH_H/2
_RATIO_RTOL = 0.1  # +/-10% TOL on images vs expected values
_RAW_PIXEL_THRESH = 0.03  # Waive check if RAW [0, 1] value below this thresh


def create_requests(cam, props, log_path):
  """Create the requests and settings lists."""
  w, h = capture_request_utils.get_available_output_sizes(
      'yuv', props, _MAX_YUV_SIZE)[0]

  if camera_properties_utils.raw16(props):
    raw_format = 'raw'
  elif camera_properties_utils.raw10(props):
    raw_format = 'raw10'
  elif camera_properties_utils.raw12(props):
    raw_format = 'raw12'
  else:  # should not reach here
    raise error_util.Error('Cannot find available RAW output format')

  out_surfaces = [{'format': raw_format},
                  {'format': 'yuv', 'width': w, 'height': h}]
  sens_min, sens_max = props['android.sensor.info.sensitivityRange']
  sens_boost_min, sens_boost_max = props[
      'android.control.postRawSensitivityBoostRange']
  exp_target, sens_target = target_exposure_utils.get_target_exposure_combos(
      log_path, cam)['midSensitivity']

  reqs = []
  settings = []
  sens_boost = sens_boost_min
  while sens_boost <= sens_boost_max:
    sens_raw = int(round(sens_target * 100.0 / sens_boost))
    if sens_raw < sens_min or sens_raw > sens_max:
      break
    req = capture_request_utils.manual_capture_request(sens_raw, exp_target)
    req['android.control.postRawSensitivityBoost'] = sens_boost
    reqs.append(req)
    settings.append((sens_raw, sens_boost))
    if sens_boost == sens_boost_max:
      break
    sens_boost *= 2
    # Always try to test maximum sensitivity boost value
    if sens_boost > sens_boost_max:
      sens_boost = sens_boost_max

  return settings, reqs, out_surfaces


def compute_patch_means(cap, props, file_name):
  """Compute the RGB means for center patch of capture."""

  rgb_img = image_processing_utils.convert_capture_to_rgb_image(
      cap, props=props)
  patch = image_processing_utils.get_image_patch(
      rgb_img, _PATCH_X, _PATCH_Y, _PATCH_W, _PATCH_H)
  image_processing_utils.write_image(patch, file_name)
  return image_processing_utils.compute_image_means(patch)


def create_plots(idx, raw_means, yuv_means, name_with_log_path):
  """Create plots from data.

  Args:
    idx: capture request indices for x-axis.
    raw_means: array of RAW capture RGB converted means.
    yuv_means: array of YUV capture RGB converted means.
    name_with_log_path: file name & path to save files.
  """

  pylab.clf()
  for i, _ in enumerate(_COLORS):
    pylab.plot(idx, [ch[i] for ch in yuv_means], '-'+'rgb'[i]+'s', label='YUV',
               alpha=0.7)
    pylab.plot(idx, [ch[i] for ch in raw_means], '-'+'rgb'[i]+'o', label='RAW',
               alpha=0.7)
  pylab.ylim([0, 1])
  pylab.title(_NAME)
  pylab.xlabel('requests')
  pylab.ylabel('RGB means')
  pylab.legend(loc='lower right', numpoints=1, fancybox=True)
  matplotlib.pyplot.savefig(f'{name_with_log_path}_plot_means.png')


class PostRawSensitivityBoost(its_base_test.ItsBaseTest):
  """Check post RAW sensitivity boost.

  Captures a set of RAW/YUV images with different sensitivity/post RAW
  sensitivity boost combination and checks if output means match req settings

  RAW images should get brighter. YUV images should stay about the same.
    asserts RAW is ~2x brighter per step
    asserts YUV is about the same per step
  """

  def test_post_raw_sensitivity_boost(self):
    logging.debug('Starting %s', _NAME)
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      camera_properties_utils.skip_unless(
          camera_properties_utils.raw_output(props) and
          camera_properties_utils.post_raw_sensitivity_boost(props) and
          camera_properties_utils.compute_target_exposure(props) and
          camera_properties_utils.per_frame_control(props) and
          not camera_properties_utils.mono_camera(props))
      log_path = self.log_path
      name_with_log_path = os.path.join(log_path, _NAME)

      # Load chart for scene
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet,
          its_session_utils.CHART_DISTANCE_NO_SCALING)

      # Create reqs & do caps
      settings, reqs, out_surfaces = create_requests(cam, props, log_path)
      raw_caps, yuv_caps = cam.do_capture(reqs, out_surfaces)
      if not isinstance(raw_caps, list):
        raw_caps = [raw_caps]
      if not isinstance(yuv_caps, list):
        yuv_caps = [yuv_caps]

      # Extract data
      raw_means = []
      yuv_means = []
      for i in range(len(reqs)):
        sens, sens_boost = settings[i]
        sens_and_boost = f's={sens:04d}_boost={sens_boost:04d}'
        raw_file_name = f'{name_with_log_path}_raw_{sens_and_boost}.jpg'
        raw_means.append(compute_patch_means(raw_caps[i], props, raw_file_name))

        yuv_file_name = f'{name_with_log_path}_yuv_{sens_and_boost}.jpg'
        yuv_means.append(compute_patch_means(yuv_caps[i], props, yuv_file_name))

        logging.debug('s=%d, s_boost=%d: raw_means %s, yuv_means %s',
                      sens, sens_boost, str(raw_means[-1]), str(yuv_means[-1]))
      cap_idxs = range(len(reqs))

      # Create plots
      create_plots(cap_idxs, raw_means, yuv_means, name_with_log_path)

      # RAW asserts
      for step in range(1, len(reqs)):
        sens_prev, _ = settings[step - 1]
        sens, sens_boost = settings[step]
        expected_ratio = sens_prev / sens
        for ch, _ in enumerate(_COLORS):
          ratio_per_step = raw_means[step-1][ch] / raw_means[step][ch]
          logging.debug('Step: (%d, %d) %s channel: (%f, %f), ratio: %f,',
                        step - 1, step, _COLORS[ch], raw_means[step - 1][ch],
                        raw_means[step][ch], ratio_per_step)
          if raw_means[step][ch] <= _RAW_PIXEL_THRESH:
            continue
          if not math.isclose(ratio_per_step, expected_ratio,
                              rel_tol=_RATIO_RTOL):
            raise AssertionError(
                f'step: {step}, ratio: {ratio_per_step}, expected ratio: '
                f'{expected_ratio:.3f}, RTOL: {_RATIO_RTOL}')

      # YUV asserts
      for ch, _ in enumerate(_COLORS):
        vals = [val[ch] for val in yuv_means]
        for idx in cap_idxs:
          if raw_means[idx][ch] <= _RAW_PIXEL_THRESH:
            vals = vals[:idx]
        mean = sum(vals) / len(vals)
        logging.debug('%s channel vals %s mean %f', _COLORS[ch], vals, mean)
        for step in range(len(vals)):
          ratio_mean = vals[step] / mean
          if not math.isclose(1.0, ratio_mean, rel_tol=_RATIO_RTOL):
            raise AssertionError(
                f'Capture vs mean ratio: {ratio_mean}, RTOL: +/- {_RATIO_RTOL}')

if __name__ == '__main__':
  test_runner.main()
