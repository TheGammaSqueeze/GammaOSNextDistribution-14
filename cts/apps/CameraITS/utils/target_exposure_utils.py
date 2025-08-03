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
"""Utility functions to calculate targeted exposures based on camera properties.
"""

import json
import logging
import os
import sys

import capture_request_utils
import image_processing_utils
import its_session_utils

_CACHE_FILENAME = 'its.target.cfg'
_REGION_3A = [[0.45, 0.45, 0.1, 0.1, 1]]


def get_target_exposure_combos(output_path, its_session=None):
  """Get a set of legal combinations of target (exposure time, sensitivity).

  Gets the target exposure value, which is a product of sensitivity (ISO) and
  exposure time, and returns equivalent tuples of (exposure time,sensitivity)
  that are all legal and that correspond to the four extrema in this 2D param
  space, as well as to two "middle" points.

  Will open a device session if its_session is None.

  Args:
   output_path: String, path where the target.cfg file will be saved.
   its_session: Optional, holding an open device session.

  Returns:
    Object containing six legal (exposure time, sensitivity) tuples, keyed
    by the following strings:
    'minExposureTime'
    'midExposureTime'
    'maxExposureTime'
    'minSensitivity'
    'midSensitivity'
    'maxSensitivity'
  """
  target_config_filename = os.path.join(output_path, _CACHE_FILENAME)

  if its_session is None:
    with its_session_utils.ItsSession() as cam:
      exp_sens_prod = get_target_exposure(target_config_filename, cam)
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
  else:
    exp_sens_prod = get_target_exposure(target_config_filename, its_session)
    props = its_session.get_camera_properties()
    props = its_session.override_with_hidden_physical_camera_props(props)

  sens_range = props['android.sensor.info.sensitivityRange']
  exp_time_range = props['android.sensor.info.exposureTimeRange']
  logging.debug('Target exposure*sensitivity: %d', exp_sens_prod)
  logging.debug('sensor exp time range: %s', str(exp_time_range))
  logging.debug('sensor sensitivity range: %s', str(sens_range))

  # Combo 1: smallest legal exposure time.
  e1_expt = exp_time_range[0]
  e1_sens = int(exp_sens_prod / e1_expt)
  if e1_sens > sens_range[1]:
    e1_sens = sens_range[1]
    e1_expt = int(exp_sens_prod / e1_sens)
  logging.debug('minExposureTime e: %d, s: %d', e1_expt, e1_sens)

  # Combo 2: largest legal exposure time.
  e2_expt = exp_time_range[1]
  e2_sens = int(exp_sens_prod / e2_expt)
  if e2_sens < sens_range[0]:
    e2_sens = sens_range[0]
    e2_expt = int(exp_sens_prod / e2_sens)
  logging.debug('maxExposureTime e: %d, s: %d', e2_expt, e2_sens)

  # Combo 3: smallest legal sensitivity.
  e3_sens = sens_range[0]
  e3_expt = int(exp_sens_prod / e3_sens)
  if e3_expt > exp_time_range[1]:
    e3_expt = exp_time_range[1]
    e3_sens = int(exp_sens_prod / e3_expt)
  logging.debug('minSensitivity e: %d, s: %d', e3_expt, e3_sens)

  # Combo 4: largest legal sensitivity.
  e4_sens = sens_range[1]
  e4_expt = int(exp_sens_prod / e4_sens)
  if e4_expt < exp_time_range[0]:
    e4_expt = exp_time_range[0]
    e4_sens = int(exp_sens_prod / e4_expt)
  logging.debug('maxSensitivity e: %d, s: %d', e4_expt, e4_sens)

  # Combo 5: middle exposure time.
  e5_expt = int((exp_time_range[0] + exp_time_range[1]) / 2.0)
  e5_sens = int(exp_sens_prod / e5_expt)
  if e5_sens > sens_range[1]:
    e5_sens = sens_range[1]
    e5_expt = int(exp_sens_prod / e5_sens)
  if e5_sens < sens_range[0]:
    e5_sens = sens_range[0]
    e5_expt = int(exp_sens_prod / e5_sens)
  logging.debug('midExposureTime e: %d, s: %d', e5_expt, e5_sens)

  # Combo 6: middle sensitivity.
  e6_sens = int((sens_range[0] + sens_range[1]) / 2.0)
  e6_expt = int(exp_sens_prod / e6_sens)
  if e6_expt > exp_time_range[1]:
    e6_expt = exp_time_range[1]
    e6_sens = int(exp_sens_prod / e6_expt)
  if e6_expt < exp_time_range[0]:
    e6_expt = exp_time_range[0]
    e6_sens = int(exp_sens_prod / e6_expt)
  logging.debug('midSensitivity e: %d, s: %d', e6_expt, e6_sens)

  return {
      'minExposureTime': (e1_expt, e1_sens),
      'maxExposureTime': (e2_expt, e2_sens),
      'minSensitivity': (e3_expt, e3_sens),
      'maxSensitivity': (e4_expt, e4_sens),
      'midExposureTime': (e5_expt, e5_sens),
      'midSensitivity': (e6_expt, e6_sens)
  }


def get_target_exposure(target_config_filename, its_session=None):
  """Get the target exposure to use.

  If there is a cached value and if the "target" command line parameter is
  present, then return the cached value. Otherwise, measure a new value from
  the scene, cache it, then return it.

  Args:
    target_config_filename: String, target config file name.
    its_session: Optional, holding an open device session.

  Returns:
    The target exposure*sensitivity value.
  """
  cached_exposure = None
  for s in sys.argv[1:]:
    if s == 'target':
      cached_exposure = get_cached_target_exposure(target_config_filename)
  if cached_exposure is not None:
    logging.debug('Using cached target exposure')
    return cached_exposure
  if its_session is None:
    with its_session_utils.ItsSession() as cam:
      measured_exposure = do_target_exposure_measurement(cam)
  else:
    measured_exposure = do_target_exposure_measurement(its_session)
  set_cached_target_exposure(target_config_filename, measured_exposure)
  return measured_exposure


def set_cached_target_exposure(target_config_filename, exposure):
  """Saves the given exposure value to a cached location.

  Once a value is cached, a call to get_cached_target_exposure will return
  the value, even from a subsequent test/script run. That is, the value is
  persisted.

  The value is persisted in a JSON file in the current directory (from which
  the script calling this function is run).

  Args:
   target_config_filename: String, target config file name.
   exposure: The value to cache.
  """
  logging.debug('Setting cached target exposure')
  with open(target_config_filename, 'w') as f:
    f.write(json.dumps({'exposure': exposure}))


def get_cached_target_exposure(target_config_filename):
  """Get the cached exposure value.

  Args:
   target_config_filename: String, target config file name.

  Returns:
    The cached exposure value, or None if there is no valid cached value.
  """
  try:
    with open(target_config_filename, 'r') as f:
      o = json.load(f)
      return o['exposure']
  except IOError:
    return None


def do_target_exposure_measurement(its_session):
  """Use device 3A and captured shots to determine scene exposure.

    Creates a new ITS device session (so this function should not be called
    while another session to the device is open).

    Assumes that the camera is pointed at a scene that is reasonably uniform
    and reasonably lit -- that is, an appropriate target for running the ITS
    tests that assume such uniformity.

    Measures the scene using device 3A and then by taking a shot to hone in on
    the exact exposure level that will result in a center 10% by 10% patch of
    the scene having a intensity level of 0.5 (in the pixel range of [0,1])
    when a linear tonemap is used. That is, the pixels coming off the sensor
    should be at approximately 50% intensity (however note that it's actually
    the luma value in the YUV image that is being targeted to 50%).

    The computed exposure value is the product of the sensitivity (ISO) and
    exposure time (ns) to achieve that sensor exposure level.

  Args:
    its_session: Holds an open device session.

  Returns:
    The measured product of sensitivity and exposure time that results in
    the luma channel of captured shots having an intensity of 0.5.
  """
  logging.debug('Measuring target exposure')

  # Get AE+AWB lock first, so the auto values in the capture result are
  # populated properly.
  sens, exp_time, gains, xform, _ = its_session.do_3a(
      _REGION_3A, _REGION_3A, _REGION_3A, do_af=False, get_results=True)

  # Convert the transform to rational.
  xform_rat = [{'numerator': int(100 * x), 'denominator': 100} for x in xform]

  # Linear tonemap
  tmap = sum([[i / 63.0, i / 63.0] for i in range(64)], [])

  # Capture a manual shot with this exposure, using a linear tonemap.
  # Use the gains+transform returned by the AWB pass.
  req = capture_request_utils.manual_capture_request(sens, exp_time)
  req['android.tonemap.mode'] = 0
  req['android.tonemap.curve'] = {'red': tmap, 'green': tmap, 'blue': tmap}
  req['android.colorCorrection.transform'] = xform_rat
  req['android.colorCorrection.gains'] = gains
  cap = its_session.do_capture(req)

  # Compute the mean luma of a center patch.
  yimg, _, _ = image_processing_utils.convert_capture_to_planes(
      cap)
  tile = image_processing_utils.get_image_patch(yimg, 0.45, 0.45, 0.1, 0.1)
  luma_mean = image_processing_utils.compute_image_means(tile)
  logging.debug('Target exposure cap luma: %.4f', luma_mean[0])

  # Compute the exposure value that would result in a luma of 0.5.
  return sens * exp_time * 0.5 / luma_mean[0]

