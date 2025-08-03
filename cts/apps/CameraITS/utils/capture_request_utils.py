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
"""Utility functions to create custom capture requests."""


import logging
import math

_COMMON_IMG_ARS = (4/3, 16/9)
_COMMON_IMG_ARS_ATOL = 0.01
_MAX_YUV_SIZE = (1920, 1080)
_MIN_YUV_SIZE = (640, 360)
_VGA_W, _VGA_H = 640, 480
_CAPTURE_INTENT_STILL_CAPTURE = 2
_AE_MODE_ON_AUTO_FLASH = 2
_CAPTURE_INTENT_PREVIEW = 1
_AE_PRECAPTURE_TRIGGER_START = 1
_AE_PRECAPTURE_TRIGGER_IDLE = 0
_FLASH_MODE_SINGLE = 1


def is_common_aspect_ratio(size):
  """Returns if aspect ratio is a 4:3 or 16:9.

  Args:
    size: tuple of image (w, h)

  Returns:
    Boolean
  """
  for aspect_ratio in _COMMON_IMG_ARS:
    if math.isclose(size[0]/size[1], aspect_ratio,
                    abs_tol=_COMMON_IMG_ARS_ATOL):
      return True
  return False


def auto_capture_request(linear_tonemap=False, props=None, do_af=True,
                         do_autoframing=False, zoom_ratio=None):
  """Returns a capture request with everything set to auto.

  Args:
   linear_tonemap: [Optional] boolean whether linear tonemap should be used.
   props: [Optional] object from its_session_utils.get_camera_properties().
          Must present when linear_tonemap is True.
   do_af: [Optional] boolean whether af mode should be active.
   do_autoframing: [Optional] boolean whether autoframing should be active.
   zoom_ratio: [Optional] zoom ratio to be set in the capture request.

  Returns:
    Auto capture request, ready to be passed to the
    its_session_utils.device.do_capture()
  """
  req = {
      'android.control.mode': 1,
      'android.control.aeMode': 1,
      'android.control.awbMode': 1,
      'android.control.afMode': 1 if do_af else 0,
      'android.colorCorrection.mode': 1,
      'android.shading.mode': 1,
      'android.tonemap.mode': 1,
      'android.lens.opticalStabilizationMode': 0,
      'android.control.videoStabilizationMode': 0,
  }
  if do_autoframing:
    req['android.control.autoframing'] = 1
  if not do_af:
    req['android.lens.focusDistance'] = 0.0
  if zoom_ratio:
    req['android.control.zoomRatio'] = zoom_ratio
  if linear_tonemap:
    if props is None:
      raise AssertionError('props is None with linear_tonemap.')
    # CONTRAST_CURVE mode
    if 0 in props['android.tonemap.availableToneMapModes']:
      logging.debug('CONTRAST_CURVE tonemap mode')
      req['android.tonemap.mode'] = 0
      req['android.tonemap.curve'] = {
          'red': [0.0, 0.0, 1.0, 1.0],  # coordinate pairs: x0, y0, x1, y1
          'green': [0.0, 0.0, 1.0, 1.0],
          'blue': [0.0, 0.0, 1.0, 1.0]
      }
    # GAMMA_VALUE mode
    elif 3 in props['android.tonemap.availableToneMapModes']:
      logging.debug('GAMMA_VALUE tonemap mode')
      req['android.tonemap.mode'] = 3
      req['android.tonemap.gamma'] = 1.0
    else:
      raise AssertionError('Linear tonemap is not supported')
  return req


def manual_capture_request(sensitivity,
                           exp_time,
                           f_distance=0.0,
                           linear_tonemap=False,
                           props=None):
  """Returns a capture request with everything set to manual.

  Uses identity/unit color correction, and the default tonemap curve.
  Optionally, the tonemap can be specified as being linear.

  Args:
   sensitivity: The sensitivity value to populate the request with.
   exp_time: The exposure time, in nanoseconds, to populate the request with.
   f_distance: The focus distance to populate the request with.
   linear_tonemap: [Optional] whether a linear tonemap should be used in this
     request.
   props: [Optional] the object returned from
     its_session_utils.get_camera_properties(). Must present when linear_tonemap
     is True.

  Returns:
    The default manual capture request, ready to be passed to the
    its_session_utils.device.do_capture function.
  """
  req = {
      'android.control.captureIntent': 6,
      'android.control.mode': 0,
      'android.control.aeMode': 0,
      'android.control.awbMode': 0,
      'android.control.afMode': 0,
      'android.control.effectMode': 0,
      'android.sensor.sensitivity': sensitivity,
      'android.sensor.exposureTime': exp_time,
      'android.colorCorrection.mode': 0,
      'android.colorCorrection.transform':
          int_to_rational([1, 0, 0, 0, 1, 0, 0, 0, 1]),
      'android.colorCorrection.gains': [1, 1, 1, 1],
      'android.lens.focusDistance': f_distance,
      'android.tonemap.mode': 1,
      'android.shading.mode': 1,
      'android.lens.opticalStabilizationMode': 0,
      'android.control.videoStabilizationMode': 0,
  }
  if linear_tonemap:
    if props is None:
      raise AssertionError('props is None.')
    # CONTRAST_CURVE mode
    if 0 in props['android.tonemap.availableToneMapModes']:
      logging.debug('CONTRAST_CURVE tonemap mode')
      req['android.tonemap.mode'] = 0
      req['android.tonemap.curve'] = {
          'red': [0.0, 0.0, 1.0, 1.0],
          'green': [0.0, 0.0, 1.0, 1.0],
          'blue': [0.0, 0.0, 1.0, 1.0]
      }
    # GAMMA_VALUE mode
    elif 3 in props['android.tonemap.availableToneMapModes']:
      logging.debug('GAMMA_VALUE tonemap mode')
      req['android.tonemap.mode'] = 3
      req['android.tonemap.gamma'] = 1.0
    else:
      raise AssertionError('Linear tonemap is not supported')
  return req


def get_available_output_sizes(fmt, props, max_size=None, match_ar_size=None):
  """Return a sorted list of available output sizes for a given format.

  Args:
   fmt: the output format, as a string in ['jpg', 'yuv', 'raw', 'raw10',
     'raw12', 'y8'].
   props: the object returned from its_session_utils.get_camera_properties().
   max_size: (Optional) A (w,h) tuple.Sizes larger than max_size (either w or h)
     will be discarded.
   match_ar_size: (Optional) A (w,h) tuple.Sizes not matching the aspect ratio
     of match_ar_size will be discarded.

  Returns:
    A sorted list of (w,h) tuples (sorted large-to-small).
  """
  ar_tolerance = 0.03
  fmt_codes = {
      'raw': 0x20,
      'raw10': 0x25,
      'raw12': 0x26,
      'yuv': 0x23,
      'jpg': 0x100,
      'jpeg': 0x100,
      'jpeg_r': 0x1005,
      'priv': 0x22,
      'y8': 0x20203859
  }
  configs = props[
      'android.scaler.streamConfigurationMap']['availableStreamConfigurations']
  fmt_configs = [cfg for cfg in configs if cfg['format'] == fmt_codes[fmt]]
  out_configs = [cfg for cfg in fmt_configs if not cfg['input']]
  out_sizes = [(cfg['width'], cfg['height']) for cfg in out_configs]
  if max_size:
    max_size = [int(i) for i in max_size]
    out_sizes = [
        s for s in out_sizes if s[0] <= max_size[0] and s[1] <= max_size[1]
    ]
  if match_ar_size:
    ar = match_ar_size[0] / match_ar_size[1]
    out_sizes = [
        s for s in out_sizes if abs(ar - s[0] / float(s[1])) <= ar_tolerance
    ]
  out_sizes.sort(reverse=True, key=lambda s: s[0])  # 1st pass, sort by width
  out_sizes.sort(reverse=True, key=lambda s: s[0] * s[1])  # sort by area
  return out_sizes


def float_to_rational(f, denom=128):
  """Function to convert Python floats to Camera2 rationals.

  Args:
    f: python float or list of floats.
    denom: (Optional) the denominator to use in the output rationals.

  Returns:
    Python dictionary or list of dictionaries representing the given
    float(s) as rationals.
  """
  if isinstance(f, list):
    return [{'numerator': math.floor(val*denom+0.5), 'denominator': denom}
            for val in f]
  else:
    return {'numerator': math.floor(f*denom+0.5), 'denominator': denom}


def rational_to_float(r):
  """Function to convert Camera2 rational objects to Python floats.

  Args:
   r: Rational or list of rationals, as Python dictionaries.

  Returns:
   Float or list of floats.
  """
  if isinstance(r, list):
    return [float(val['numerator']) / float(val['denominator']) for val in r]
  else:
    return float(r['numerator']) / float(r['denominator'])


def get_fastest_manual_capture_settings(props):
  """Returns a capture request and format spec for the fastest manual capture.

  Args:
     props: the object returned from its_session_utils.get_camera_properties().

  Returns:
    Two values, the first is a capture request, and the second is an output
    format specification, for the fastest possible (legal) capture that
    can be performed on this device (with the smallest output size).
  """
  fmt = 'yuv'
  size = get_available_output_sizes(fmt, props)[-1]
  out_spec = {'format': fmt, 'width': size[0], 'height': size[1]}
  s = min(props['android.sensor.info.sensitivityRange'])
  e = min(props['android.sensor.info.exposureTimeRange'])
  req = manual_capture_request(s, e)

  turn_slow_filters_off(props, req)

  return req, out_spec


def get_fastest_auto_capture_settings(props):
  """Returns a capture request and format spec for the fastest auto capture.

  Args:
     props: the object returned from its_session_utils.get_camera_properties().

  Returns:
      Two values, the first is a capture request, and the second is an output
      format specification, for the fastest possible (legal) capture that
      can be performed on this device (with the smallest output size).
  """
  fmt = 'yuv'
  size = get_available_output_sizes(fmt, props)[-1]
  out_spec = {'format': fmt, 'width': size[0], 'height': size[1]}
  req = auto_capture_request()

  turn_slow_filters_off(props, req)

  return req, out_spec


def fastest_auto_capture_request(props):
  """Return an auto capture request for the fastest capture.

  Args:
    props: the object returned from its.device.get_camera_properties().

  Returns:
    A capture request with everything set to auto and all filters that
    may slow down capture set to OFF or FAST if possible
  """
  req = auto_capture_request()
  turn_slow_filters_off(props, req)
  return req


def turn_slow_filters_off(props, req):
  """Turn filters that may slow FPS down to OFF or FAST in input request.

   This function modifies the request argument, such that filters that may
   reduce the frames-per-second throughput of the camera device will be set to
   OFF or FAST if possible.

  Args:
    props: the object returned from its_session_utils.get_camera_properties().
    req: the input request.

  Returns:
    Nothing.
  """
  set_filter_off_or_fast_if_possible(
      props, req, 'android.noiseReduction.availableNoiseReductionModes',
      'android.noiseReduction.mode')
  set_filter_off_or_fast_if_possible(
      props, req, 'android.colorCorrection.availableAberrationModes',
      'android.colorCorrection.aberrationMode')
  if 'camera.characteristics.keys' in props:
    chars_keys = props['camera.characteristics.keys']
    hot_pixel_modes = 'android.hotPixel.availableHotPixelModes' in chars_keys
    edge_modes = 'android.edge.availableEdgeModes' in chars_keys
  if 'camera.characteristics.requestKeys' in props:
    req_keys = props['camera.characteristics.requestKeys']
    hot_pixel_mode = 'android.hotPixel.mode' in req_keys
    edge_mode = 'android.edge.mode' in req_keys
  if hot_pixel_modes and hot_pixel_mode:
    set_filter_off_or_fast_if_possible(
        props, req, 'android.hotPixel.availableHotPixelModes',
        'android.hotPixel.mode')
  if edge_modes and edge_mode:
    set_filter_off_or_fast_if_possible(props, req,
                                       'android.edge.availableEdgeModes',
                                       'android.edge.mode')


def set_filter_off_or_fast_if_possible(props, req, available_modes, filter_key):
  """Check and set controlKey to off or fast in req.

  Args:
    props: the object returned from its.device.get_camera_properties().
    req: the input request. filter will be set to OFF or FAST if possible.
    available_modes: the key to check available modes.
    filter_key: the filter key

  Returns:
    Nothing.
  """
  if available_modes in props:
    if 0 in props[available_modes]:
      req[filter_key] = 0
    elif 1 in props[available_modes]:
      req[filter_key] = 1


def int_to_rational(i):
  """Function to convert Python integers to Camera2 rationals.

  Args:
   i: Python integer or list of integers.

  Returns:
    Python dictionary or list of dictionaries representing the given int(s)
    as rationals with denominator=1.
  """
  if isinstance(i, list):
    return [{'numerator': val, 'denominator': 1} for val in i]
  else:
    return {'numerator': i, 'denominator': 1}


def get_largest_yuv_format(props, match_ar=None):
  """Return a capture request and format spec for the largest yuv size.

  Args:
    props: object returned from camera_properties_utils.get_camera_properties().
    match_ar: (Optional) a (w, h) tuple. Aspect ratio to match during search.

  Returns:
    fmt:   an output format specification for the largest possible yuv format
           for this device.
  """
  size = get_available_output_sizes('yuv', props, match_ar_size=match_ar)[0]
  fmt = {'format': 'yuv', 'width': size[0], 'height': size[1]}

  return fmt


def get_smallest_yuv_format(props, match_ar=None):
  """Return a capture request and format spec for the smallest yuv size.

  Args:
    props: object returned from camera_properties_utils.get_camera_properties().
    match_ar: (Optional) a (w, h) tuple. Aspect ratio to match during search.

  Returns:
    fmt:   an output format specification for the smallest possible yuv format
           for this device.
  """
  size = get_available_output_sizes('yuv', props, match_ar_size=match_ar)[-1]
  fmt = {'format': 'yuv', 'width': size[0], 'height': size[1]}

  return fmt


def get_near_vga_yuv_format(props, match_ar=None):
  """Return a capture request and format spec for the smallest yuv size.

  Args:
    props: object returned from camera_properties_utils.get_camera_properties().
    match_ar: (Optional) a (w, h) tuple. Aspect ratio to match during search.

  Returns:
    fmt: an output format specification for the smallest possible yuv format
           for this device.
  """
  sizes = get_available_output_sizes('yuv', props, match_ar_size=match_ar)
  logging.debug('Available YUV sizes: %s', sizes)
  max_area = _MAX_YUV_SIZE[1] * _MAX_YUV_SIZE[0]
  min_area = _MIN_YUV_SIZE[1] * _MIN_YUV_SIZE[0]

  fmt = {'format': 'yuv', 'width': _VGA_W, 'height': _VGA_H}
  for size in sizes:
    fmt_area = size[0]*size[1]
    if fmt_area < min_area or fmt_area > max_area:
      continue
    fmt['width'], fmt['height'] = size[0], size[1]
  logging.debug('YUV format selected: %s', fmt)

  return fmt


def get_largest_jpeg_format(props, match_ar=None):
  """Return a capture request and format spec for the largest jpeg size.

  Args:
    props: object returned from camera_properties_utils.get_camera_properties().
    match_ar: (Optional) a (w, h) tuple. Aspect ratio to match during search.

  Returns:
    fmt:   an output format specification for the largest possible jpeg format
           for this device.
  """
  size = get_available_output_sizes('jpeg', props, match_ar_size=match_ar)[0]
  fmt = {'format': 'jpeg', 'width': size[0], 'height': size[1]}

  return fmt


def get_max_digital_zoom(props):
  """Returns the maximum amount of zooming possible by the camera device.

  Args:
    props: the object returned from its.device.get_camera_properties().

  Return:
    A float indicating the maximum amount of zoom possible by the camera device.
  """

  max_z = 1.0
  if 'android.scaler.availableMaxDigitalZoom' in props:
    max_z = props['android.scaler.availableMaxDigitalZoom']

  return max_z


def take_captures_with_flash(cam, out_surface):
  """Takes capture with auto flash ON.

  Runs precapture sequence by setting the aePrecapture trigger to
  START and capture intent set to Preview and then take the capture
  with flash.
  Args:
    cam: ItsSession object
    out_surface: Specifications of the output image format and
      size to use for the capture.

  Returns:
    cap: An object which contains following fields:
      * data: the image data as a numpy array of bytes.
      * width: the width of the captured image.
      * height: the height of the captured image.
      * format: image format
      * metadata: the capture result object
  """

  preview_req_start = auto_capture_request()
  preview_req_start[
      'android.control.aeMode'] = _AE_MODE_ON_AUTO_FLASH
  preview_req_start[
      'android.control.captureIntent'] = _CAPTURE_INTENT_PREVIEW
  preview_req_start[
      'android.control.aePrecaptureTrigger'] = _AE_PRECAPTURE_TRIGGER_START
  # Repeat preview requests with aePrecapture set to IDLE
  # until AE is converged.
  preview_req_idle = auto_capture_request()
  preview_req_idle[
      'android.control.aeMode'] = _AE_MODE_ON_AUTO_FLASH
  preview_req_idle[
      'android.control.captureIntent'] = _CAPTURE_INTENT_PREVIEW
  preview_req_idle[
      'android.control.aePrecaptureTrigger'] = _AE_PRECAPTURE_TRIGGER_IDLE
  # Single still capture request.
  still_capture_req = auto_capture_request()
  still_capture_req[
      'android.control.aeMode'] = _AE_MODE_ON_AUTO_FLASH
  still_capture_req[
      'android.control.captureIntent'] = _CAPTURE_INTENT_STILL_CAPTURE
  still_capture_req[
      'android.control.aePrecaptureTrigger'] = _AE_PRECAPTURE_TRIGGER_IDLE
  cap = cam.do_capture_with_flash(preview_req_start,
                                  preview_req_idle,
                                  still_capture_req, out_surface)
  return cap


def take_captures_with_flash_strength(cam, out_surface, ae_mode, strength):
  """Takes capture with desired flash strength.

  Runs precapture sequence by setting the aePrecapture trigger to
  START and capture intent set to Preview.
  Then, take the capture with set flash strength.
  Args:
    cam: ItsSession object
    out_surface: Specifications of the output image format and
      size to use for the capture.
    ae_mode: AE_mode
    strength: flash strength

  Returns:
    cap: An object which contains following fields:
      * data: the image data as a numpy array of bytes.
      * width: the width of the captured image.
      * height: the height of the captured image.
      * format: image format
      * metadata: the capture result object
  """
  preview_req_start = auto_capture_request()
  preview_req_start[
      'android.control.aeMode'] = _AE_MODE_ON_AUTO_FLASH
  preview_req_start[
      'android.control.captureIntent'] = _CAPTURE_INTENT_PREVIEW
  preview_req_start[
      'android.control.aePrecaptureTrigger'] = _AE_PRECAPTURE_TRIGGER_START
  # Repeat preview requests with aePrecapture set to IDLE
  # until AE is converged.
  preview_req_idle = auto_capture_request()
  preview_req_idle[
      'android.control.aeMode'] = _AE_MODE_ON_AUTO_FLASH
  preview_req_idle[
      'android.control.captureIntent'] = _CAPTURE_INTENT_PREVIEW
  preview_req_idle[
      'android.control.aePrecaptureTrigger'] = _AE_PRECAPTURE_TRIGGER_IDLE
  # Single still capture request.
  still_capture_req = auto_capture_request()
  still_capture_req[
      'android.control.aeMode'] = ae_mode
  still_capture_req[
      'android.flash.mode'] = _FLASH_MODE_SINGLE
  still_capture_req[
      'android.control.captureIntent'] = _CAPTURE_INTENT_STILL_CAPTURE
  still_capture_req[
      'android.control.aePrecaptureTrigger'] = _AE_PRECAPTURE_TRIGGER_IDLE
  still_capture_req[
      'android.flash.strengthLevel'] = strength
  cap = cam.do_capture_with_flash(preview_req_start,
                                  preview_req_idle,
                                  still_capture_req, out_surface)
  return cap

