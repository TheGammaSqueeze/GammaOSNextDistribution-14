# Copyright 2013 The Android Open Source Project
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
"""Verifies valid data return from CaptureResult objects."""


import logging
import math
import os.path
import matplotlib.pyplot
from mobly import test_runner
# mplot3 is required for 3D plots in draw_lsc_plot() though not called directly.
from mpl_toolkits import mplot3d  # pylint: disable=unused-import
import numpy as np

import its_base_test
import camera_properties_utils
import capture_request_utils
import its_session_utils

_AWB_GAINS_NUM = 4
_AWB_XFORM_NUM = 9
_ISCLOSE_ATOL = 0.05  # not for absolute ==, but if something grossly wrong
_MANUAL_AWB_GAINS = [1, 1.5, 2.0, 3.0]
_MANUAL_AWB_XFORM = capture_request_utils.float_to_rational([-1.5, -1.0, -0.5,
                                                             0.0, 0.5, 1.0,
                                                             1.5, 2.0, 3.0])
# The camera HAL may not support different gains for two G channels.
_MANUAL_GAINS_OK = [[1, 1.5, 2.0, 3.0],
                    [1, 1.5, 1.5, 3.0],
                    [1, 2.0, 2.0, 3.0]]
_MANUAL_TONEMAP = [0, 0, 1, 1]  # Linear tonemap
_MANUAL_REGION = [{'x': 8, 'y': 8, 'width': 128, 'height': 128, 'weight': 1}]
_NAME = os.path.splitext(os.path.basename(__file__))[0]


def is_close_rational(n1, n2):
  return math.isclose(capture_request_utils.rational_to_float(n1),
                      capture_request_utils.rational_to_float(n2),
                      abs_tol=_ISCLOSE_ATOL)


def draw_lsc_plot(lsc_map_w, lsc_map_h, lsc_map, name, log_path):
  """Creates Lens Shading Correction plot."""
  for ch in range(4):
    fig = matplotlib.pyplot.figure()
    ax = fig.add_subplot(projection='3d')
    xs = np.array([range(lsc_map_w)] * lsc_map_h).reshape(lsc_map_h, lsc_map_w)
    ys = np.array([[i]*lsc_map_w for i in range(lsc_map_h)]).reshape(
        lsc_map_h, lsc_map_w)
    zs = np.array(lsc_map[ch::4]).reshape(lsc_map_h, lsc_map_w)
    name_with_log_path = os.path.join(log_path, _NAME)
    ax.plot_wireframe(xs, ys, zs)
    matplotlib.pyplot.savefig(
        f'{name_with_log_path}_plot_lsc_{name}_ch{ch}.png')


def metadata_checks(metadata, props):
  """Common checks on AWB color correction matrix.

  Args:
    metadata: capture metadata
    props: camera properties
  """
  awb_gains = metadata['android.colorCorrection.gains']
  awb_xform = metadata['android.colorCorrection.transform']
  logging.debug('AWB gains: %s', str(awb_gains))
  logging.debug('AWB transform: %s', str(
      [capture_request_utils.rational_to_float(t) for t in awb_xform]))
  if props['android.control.maxRegionsAe'] > 0:
    logging.debug('AE region: %s', str(metadata['android.control.aeRegions']))
  if props['android.control.maxRegionsAf'] > 0:
    logging.debug('AF region: %s', str(metadata['android.control.afRegions']))
  if props['android.control.maxRegionsAwb'] > 0:
    logging.debug('AWB region: %s', str(metadata['android.control.awbRegions']))

  # Color correction gains and transform should be the same size
  if len(awb_gains) != _AWB_GAINS_NUM:
    raise AssertionError(f'AWB gains wrong length! {awb_gains}')
  if len(awb_xform) != _AWB_XFORM_NUM:
    raise AssertionError(f'AWB transform wrong length! {awb_xform}')


def test_auto(cam, props, log_path):
  """Do auto capture and test values.

  Args:
    cam: camera object
    props: camera properties
    log_path: path for plot directory
  """
  logging.debug('Testing auto capture results')
  req = capture_request_utils.auto_capture_request()
  req['android.statistics.lensShadingMapMode'] = 1
  sync_latency = camera_properties_utils.sync_latency(props)

  # Get 3A lock first, so auto values in capture result are populated properly.
  mono_camera = camera_properties_utils.mono_camera(props)
  cam.do_3a(do_af=False, mono_camera=mono_camera)

  # Do capture
  cap = its_session_utils.do_capture_with_latency(cam, req, sync_latency)
  metadata = cap['metadata']

  ctrl_mode = metadata['android.control.mode']
  logging.debug('Control mode: %d', ctrl_mode)
  if ctrl_mode != 1:
    raise AssertionError(f'ctrl_mode != 1: {ctrl_mode}')

  # Color correction gain and transform must be valid.
  metadata_checks(metadata, props)
  awb_gains = metadata['android.colorCorrection.gains']
  awb_xform = metadata['android.colorCorrection.transform']
  if not all([g > 0 for g in awb_gains]):
    raise AssertionError(f'AWB gains has negative terms: {awb_gains}')
  if not all([t['denominator'] != 0 for t in awb_xform]):
    raise AssertionError(f'AWB transform has 0 denominators: {awb_xform}')

  # Color correction should not match the manual settings.
  if np.allclose(awb_gains, _MANUAL_AWB_GAINS, atol=_ISCLOSE_ATOL):
    raise AssertionError('Manual and automatic AWB gains are same! '
                         f'manual: {_MANUAL_AWB_GAINS}, auto: {awb_gains}, '
                         f'ATOL: {_ISCLOSE_ATOL}')
  if all([is_close_rational(awb_xform[i], _MANUAL_AWB_XFORM[i])
          for i in range(_AWB_XFORM_NUM)]):
    raise AssertionError('Manual and automatic AWB transforms are same! '
                         f'manual: {_MANUAL_AWB_XFORM}, auto: {awb_xform}, '
                         f'ATOL: {_ISCLOSE_ATOL}')

  # Exposure time must be valid.
  exp_time = metadata['android.sensor.exposureTime']
  if exp_time <= 0:
    raise AssertionError(f'exposure time is <= 0! {exp_time}')

  # Draw lens shading correction map
  lsc_obj = metadata['android.statistics.lensShadingCorrectionMap']
  lsc_map = lsc_obj['map']
  lsc_map_w = lsc_obj['width']
  lsc_map_h = lsc_obj['height']
  logging.debug('LSC map: %dx%d, %s', lsc_map_w, lsc_map_h, str(lsc_map[:8]))
  draw_lsc_plot(lsc_map_w, lsc_map_h, lsc_map, 'auto', log_path)


def test_manual(cam, props, log_path):
  """Do manual capture and test results.

  Args:
    cam: camera object
    props: camera properties
    log_path: path for plot directory
  """
  logging.debug('Testing manual capture results')
  exp_min = min(props['android.sensor.info.exposureTimeRange'])
  sens_min = min(props['android.sensor.info.sensitivityRange'])
  sync_latency = camera_properties_utils.sync_latency(props)
  req = {
      'android.control.mode': 0,
      'android.control.aeMode': 0,
      'android.control.awbMode': 0,
      'android.control.afMode': 0,
      'android.sensor.sensitivity': sens_min,
      'android.sensor.exposureTime': exp_min,
      'android.colorCorrection.mode': 0,
      'android.colorCorrection.transform': _MANUAL_AWB_XFORM,
      'android.colorCorrection.gains': _MANUAL_AWB_GAINS,
      'android.tonemap.mode': 0,
      'android.tonemap.curve': {'red': _MANUAL_TONEMAP,
                                'green': _MANUAL_TONEMAP,
                                'blue': _MANUAL_TONEMAP},
      'android.control.aeRegions': _MANUAL_REGION,
      'android.control.afRegions': _MANUAL_REGION,
      'android.control.awbRegions': _MANUAL_REGION,
      'android.statistics.lensShadingMapMode': 1
      }
  cap = its_session_utils.do_capture_with_latency(cam, req, sync_latency)
  metadata = cap['metadata']

  ctrl_mode = metadata['android.control.mode']
  logging.debug('Control mode: %d', ctrl_mode)
  if ctrl_mode != 0:
    raise AssertionError(f'ctrl_mode: {ctrl_mode}')

  # Color correction gains and transform should be the same size and
  # values as the manually set values.
  metadata_checks(metadata, props)
  awb_gains = metadata['android.colorCorrection.gains']
  awb_xform = metadata['android.colorCorrection.transform']
  if not (all([math.isclose(awb_gains[i], _MANUAL_GAINS_OK[0][i],
                            abs_tol=_ISCLOSE_ATOL)
               for i in range(_AWB_GAINS_NUM)]) or
          all([math.isclose(awb_gains[i], _MANUAL_GAINS_OK[1][i],
                            abs_tol=_ISCLOSE_ATOL)
               for i in range(_AWB_GAINS_NUM)]) or
          all([math.isclose(awb_gains[i], _MANUAL_GAINS_OK[2][i],
                            abs_tol=_ISCLOSE_ATOL)
               for i in range(_AWB_GAINS_NUM)])):
    raise AssertionError('request/capture mismatch in AWB gains! '
                         f'req: {_MANUAL_GAINS_OK}, cap: {awb_gains}, '
                         f'ATOL: {_ISCLOSE_ATOL}')
  if not (all([is_close_rational(awb_xform[i], _MANUAL_AWB_XFORM[i])
               for i in range(_AWB_XFORM_NUM)])):
    raise AssertionError('request/capture mismatch in AWB transforms! '
                         f'req: {_MANUAL_AWB_XFORM}, cap: {awb_xform}, '
                         f'ATOL: {_ISCLOSE_ATOL}')

  # The returned tonemap must be linear.
  curves = [metadata['android.tonemap.curve']['red'],
            metadata['android.tonemap.curve']['green'],
            metadata['android.tonemap.curve']['blue']]
  logging.debug('Tonemap: %s', str(curves[0][1::16]))
  for _, c in enumerate(curves):
    if not c:
      raise AssertionError('c in curves is empty.')
    if not all([math.isclose(c[i], c[i+1], abs_tol=_ISCLOSE_ATOL)
                for i in range(0, len(c), 2)]):
      raise AssertionError(f"tonemap 'RGB'[i] is not linear! {c}")

  # Exposure time must be close to the requested exposure time.
  exp_time = metadata['android.sensor.exposureTime']
  if not math.isclose(exp_time, exp_min, abs_tol=_ISCLOSE_ATOL/1E-06):
    raise AssertionError('request/capture exposure time mismatch! '
                         f'req: {exp_min}, cap: {exp_time}, '
                         f'ATOL: {_ISCLOSE_ATOL/1E-6}')

  # Lens shading map must be valid
  lsc_obj = metadata['android.statistics.lensShadingCorrectionMap']
  lsc_map = lsc_obj['map']
  lsc_map_w = lsc_obj['width']
  lsc_map_h = lsc_obj['height']
  logging.debug('LSC map: %dx%d, %s', lsc_map_w, lsc_map_h, str(lsc_map[:8]))
  if not (lsc_map_w > 0 and lsc_map_h > 0 and
          lsc_map_w*lsc_map_h*4 == len(lsc_map)):
    raise AssertionError(f'Incorrect lens shading map size! {lsc_map}')
  if not all([m >= 1 for m in lsc_map]):
    raise AssertionError(f'Lens shading map has negative vals! {lsc_map}')

  # Draw lens shading correction map
  draw_lsc_plot(lsc_map_w, lsc_map_h, lsc_map, 'manual', log_path)


class CaptureResult(its_base_test.ItsBaseTest):
  """Test that valid data comes back in CaptureResult objects."""

  def test_capture_result(self):
    logging.debug('Starting %s', _NAME)
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)

      # Check SKIP conditions
      camera_properties_utils.skip_unless(
          camera_properties_utils.manual_sensor(props) and
          camera_properties_utils.manual_post_proc(props) and
          camera_properties_utils.per_frame_control(props))

      # Load chart for scene
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet, self.chart_distance)

      # Run tests. Run auto, then manual, then auto. Check correct metadata
      # values and ensure manual settings do not leak into auto captures.
      test_auto(cam, props, self.log_path)
      test_manual(cam, props, self.log_path)
      test_auto(cam, props, self.log_path)


if __name__ == '__main__':
  test_runner.main()

