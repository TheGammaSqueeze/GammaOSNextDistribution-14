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
"""Verifies android.shading.mode parameter is applied."""


import logging
import os.path
import matplotlib
from matplotlib import pylab
from mobly import test_runner
import numpy as np

import its_base_test
import camera_properties_utils
import capture_request_utils
import its_session_utils

_NAME = os.path.splitext(os.path.basename(__file__))[0]
_NUM_FRAMES = 4  # number of frames for temporal info to settle
_NUM_SWITCH_LOOPS = 3
_SHADING_MODES = {0: 'LSC_OFF', 1: 'LSC_FAST', 2: 'LSC_HQ'}
_NUM_SHADING_MODES = len(_SHADING_MODES)
_THRESHOLD_DIFF_RATIO = 0.15
_VGA_W, _VGA_H = 640, 480


def create_plots(shading_maps, reference_maps, num_map_gains, log_path):
  """Create 2 panel plot from data."""
  for mode in range(_NUM_SHADING_MODES):
    for i in range(_NUM_SWITCH_LOOPS):
      pylab.clf()
      pylab.figure(figsize=(5, 5))
      pylab.subplot(2, 1, 1)
      pylab.plot(range(num_map_gains), shading_maps[mode][i], '-r.',
                 label='shading', alpha=0.7)
      pylab.plot(range(num_map_gains), reference_maps[mode], '-g.',
                 label='ref', alpha=0.7)
      pylab.xlim([0, num_map_gains])
      pylab.ylim([0.9, 4.0])
      name_suffix = f'ls_maps_mode_{mode}_loop_{i}'
      pylab.title(f'{_NAME}_{name_suffix}')
      pylab.xlabel('Map gains')
      pylab.ylabel('Lens shading maps')
      pylab.legend(loc='upper center', numpoints=1, fancybox=True)

      pylab.subplot(2, 1, 2)
      shading_ref_ratio = np.divide(
          shading_maps[mode][i], reference_maps[mode])
      pylab.plot(range(num_map_gains), shading_ref_ratio, '-b.', clip_on=False)
      pylab.xlim([0, num_map_gains])
      pylab.ylim([1.0-_THRESHOLD_DIFF_RATIO, 1.0+_THRESHOLD_DIFF_RATIO])
      pylab.title('Shading/reference Maps Ratio vs Gain')
      pylab.xlabel('Map gains')
      pylab.ylabel('Shading/reference maps ratio')

      pylab.tight_layout()
      matplotlib.pyplot.savefig(
          f'{os.path.join(log_path, _NAME)}_{name_suffix}.png')


class ParamShadingModeTest(its_base_test.ItsBaseTest):
  """Test that the android.shading.mode param is applied.

  Switches shading modes and checks that the lens shading maps are
  modified as expected.

  Lens shading correction modes are OFF=0, FAST=1, and HQ=2.

  Uses VGA sized captures to reduce some USB bandwidth overhead since we are
  only looking at output metadata in this test.

  First asserts all modes are supported. Then runs 2 captures.

  cap1: switches shading modes several times and gets reference maps
  cap2: gets the lens shading maps while switching modes in 1 session

  Creates plots of reference maps and shading maps.

  Asserts proper behavior:
    1. Lens shading maps with OFF are all 1.0
    2. Lens shading maps with FAST are similar after switching shading modes
    3. Lens shading maps with HQ are similar after switching shading modes.
  """

  def test_param_shading_mode(self):
    logging.debug('Starting %s', _NAME)
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      camera_properties_utils.skip_unless(
          camera_properties_utils.per_frame_control(props) and
          camera_properties_utils.lsc_map(props) and
          camera_properties_utils.lsc_off(props))
      log_path = self.log_path

      # Load chart for scene
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet, self.chart_distance)

      # lsc devices support all modes
      if set(props.get('android.shading.availableModes')) != set(
          _SHADING_MODES.keys()):
        raise KeyError(
            f"Available modes: {props.get('android.shading.availableModes')}, "
            f'SHADING_MODEs: {[*_SHADING_MODES]}.')

      # define fmt
      mono_camera = camera_properties_utils.mono_camera(props)
      cam.do_3a(mono_camera=mono_camera)
      cap_fmt = {'format': 'yuv', 'width': _VGA_W, 'height': _VGA_H}
      logging.debug('Capture format: %s', str(cap_fmt))

      # cap1
      reference_maps = [[] for mode in range(_NUM_SHADING_MODES)]
      num_map_gains = 0
      for mode in range(1, _NUM_SHADING_MODES):
        req = capture_request_utils.auto_capture_request()
        req['android.statistics.lensShadingMapMode'] = 1
        req['android.shading.mode'] = mode
        cap_res = cam.do_capture(
            [req]*_NUM_FRAMES, cap_fmt)[_NUM_FRAMES-1]['metadata']
        lsc_map = cap_res['android.statistics.lensShadingCorrectionMap']
        if not lsc_map.get('width') or not lsc_map.get('height'):
          raise KeyError('width or height not in LSC map.')
        if mode == 1:
          num_map_gains = lsc_map['width'] * lsc_map['height'] * 4
          reference_maps[0] = [1.0] * num_map_gains
        reference_maps[mode] = lsc_map['map']

      # cap2
      reqs = []
      for i in range(_NUM_SWITCH_LOOPS):
        for mode in range(_NUM_SHADING_MODES):
          for _ in range(_NUM_FRAMES):
            req = capture_request_utils.auto_capture_request()
            req['android.statistics.lensShadingMapMode'] = 1
            req['android.shading.mode'] = mode
            reqs.append(req)
      caps = cam.do_capture(reqs, cap_fmt)

      # Populate shading maps from cap2 results
      shading_maps = [[[] for loop in range(_NUM_SWITCH_LOOPS)]
                      for mode in range(_NUM_SHADING_MODES)]
      for i in range(len(caps)//_NUM_FRAMES):
        shading_maps[i%_NUM_SHADING_MODES][i//_NUM_SWITCH_LOOPS] = caps[
            (i+1)*_NUM_FRAMES-1]['metadata'][
                'android.statistics.lensShadingCorrectionMap']['map']

      # Plot the shading and reference maps
      create_plots(shading_maps, reference_maps, num_map_gains, log_path)

      # Assert proper behavior
      for mode in range(_NUM_SHADING_MODES):
        if mode == 0:
          logging.debug('Verifying lens shading maps with mode %s are all 1.0',
                        _SHADING_MODES[mode])
        else:
          logging.debug('Verifying lens shading maps with mode %s are similar',
                        _SHADING_MODES[mode])
        for i in range(_NUM_SWITCH_LOOPS):
          if not (np.allclose(shading_maps[mode][i], reference_maps[mode],
                              rtol=_THRESHOLD_DIFF_RATIO)):
            raise AssertionError(f'FAIL mode: {_SHADING_MODES[mode]}, '
                                 f'loop: {i}, THRESH: {_THRESHOLD_DIFF_RATIO}')

if __name__ == '__main__':
  test_runner.main()
