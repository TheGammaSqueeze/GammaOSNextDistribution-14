# Copyright 2022 The Android Open Source Project
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
"""Image Field-of-View utilities for aspect ratio, crop, and FoV tests."""


import logging
import math

import cv2
import camera_properties_utils
import capture_request_utils
import image_processing_utils
import opencv_processing_utils

CIRCLE_COLOR = 0  # [0: black, 255: white]
CIRCLE_MIN_AREA = 0.01  # 1% of image size
FOV_PERCENT_RTOL = 0.15  # Relative tolerance on circle FoV % to expected.
LARGE_SIZE_IMAGE = 2000  # Size of a large image (compared against max(w, h))
THRESH_AR_L = 0.02  # Aspect ratio test threshold of large images
THRESH_AR_S = 0.075  # Aspect ratio test threshold of mini images
THRESH_CROP_L = 0.02  # Crop test threshold of large images
THRESH_CROP_S = 0.075  # Crop test threshold of mini images
THRESH_MIN_PIXEL = 4  # Crop test allowed offset


def calc_scaler_crop_region_ratio(scaler_crop_region, props):
  """Calculate ratio of scaler crop region area over active array area.

  Args:
    scaler_crop_region: Rect(left, top, right, bottom)
    props: camera properties

  Returns:
    ratio of scaler crop region area over active array area
  """
  a = props['android.sensor.info.activeArraySize']
  s = scaler_crop_region
  logging.debug('Active array size: %s', a)
  active_array_area = (a['right'] - a['left']) * (a['bottom'] - a['top'])
  scaler_crop_region_area = (s['right'] - s['left']) * (s['bottom'] - s['top'])
  crop_region_active_array_ratio = scaler_crop_region_area / active_array_area
  return crop_region_active_array_ratio


def check_fov(circle, ref_fov, w, h):
  """Check the FoV for correct size."""
  fov_percent = calc_circle_image_ratio(circle['r'], w, h)
  chk_percent = calc_expected_circle_image_ratio(ref_fov, w, h)
  if not math.isclose(fov_percent, chk_percent, rel_tol=FOV_PERCENT_RTOL):
    e_msg = (f'FoV %: {fov_percent:.2f}, Ref FoV %: {chk_percent:.2f}, '
             f'TOL={FOV_PERCENT_RTOL*100}%, img: {w}x{h}, ref: '
             f"{ref_fov['w']}x{ref_fov['h']}")
    return e_msg


def check_ar(circle, ar_gt, w, h, e_msg_stem):
  """Check the aspect ratio of the circle.

  size is the larger of w or h.
  if size >= LARGE_SIZE_IMAGE: use THRESH_AR_L
  elif size == 0 (extreme case): THRESH_AR_S
  elif 0 < image size < LARGE_SIZE_IMAGE: scale between THRESH_AR_S & AR_L

  Args:
    circle: dict with circle parameters
    ar_gt: aspect ratio ground truth to compare against
    w: width of image
    h: height of image
    e_msg_stem: customized string for error message

  Returns:
    error string if check fails
  """
  thresh_ar = max(THRESH_AR_L, THRESH_AR_S +
                  max(w, h) * (THRESH_AR_L-THRESH_AR_S) / LARGE_SIZE_IMAGE)
  ar = circle['w'] / circle['h']
  if not math.isclose(ar, ar_gt, abs_tol=thresh_ar):
    e_msg = (f'{e_msg_stem} {w}x{h}: aspect_ratio {ar:.3f}, '
             f'thresh {thresh_ar:.3f}')
    return e_msg


def check_crop(circle, cc_gt, w, h, e_msg_stem, crop_thresh_factor):
  """Check cropping.

  if size >= LARGE_SIZE_IMAGE: use thresh_crop_l
  elif size == 0 (extreme case): thresh_crop_s
  elif 0 < size < LARGE_SIZE_IMAGE: scale between thresh_crop_s & thresh_crop_l
  Also allow at least THRESH_MIN_PIXEL to prevent threshold being too tight
  for very small circle.

  Args:
    circle: dict of circle values
    cc_gt: circle center {'hori', 'vert'}  ground truth (ref'd to img center)
    w: width of image
    h: height of image
    e_msg_stem: text to customize error message
    crop_thresh_factor: scaling factor for crop thresholds

  Returns:
    error string if check fails
  """
  thresh_crop_l = THRESH_CROP_L * crop_thresh_factor
  thresh_crop_s = THRESH_CROP_S * crop_thresh_factor
  thresh_crop_hori = max(
      [thresh_crop_l,
       thresh_crop_s + w * (thresh_crop_l - thresh_crop_s) / LARGE_SIZE_IMAGE,
       THRESH_MIN_PIXEL / circle['w']])
  thresh_crop_vert = max(
      [thresh_crop_l,
       thresh_crop_s + h * (thresh_crop_l - thresh_crop_s) / LARGE_SIZE_IMAGE,
       THRESH_MIN_PIXEL / circle['h']])

  if (not math.isclose(circle['x_offset'], cc_gt['hori'],
                       abs_tol=thresh_crop_hori) or
      not math.isclose(circle['y_offset'], cc_gt['vert'],
                       abs_tol=thresh_crop_vert)):
    valid_x_range = (cc_gt['hori'] - thresh_crop_hori,
                     cc_gt['hori'] + thresh_crop_hori)
    valid_y_range = (cc_gt['vert'] - thresh_crop_vert,
                     cc_gt['vert'] + thresh_crop_vert)
    e_msg = (f'{e_msg_stem} {w}x{h} '
             f"offset X {circle['x_offset']:.3f}, Y {circle['y_offset']:.3f}, "
             f'valid X range: {valid_x_range[0]:.3f} ~ {valid_x_range[1]:.3f}, '
             f'valid Y range: {valid_y_range[0]:.3f} ~ {valid_y_range[1]:.3f}')
    return e_msg


def calc_expected_circle_image_ratio(ref_fov, img_w, img_h):
  """Determine the circle image area ratio in percentage for a given image size.

  Cropping happens either horizontally or vertically. In both cases crop results
  in the visble area reduced by a ratio r (r < 1) and the circle will in turn
  occupy ref_pct/r (percent) on the target image size.

  Args:
    ref_fov: dict with {fmt, % coverage, w, h, circle_w, circle_h}
    img_w: the image width
    img_h: the image height

  Returns:
    chk_percent: the expected circle image area ratio in percentage
  """
  ar_ref = ref_fov['w'] / ref_fov['h']
  ar_target = img_w / img_h

  r = ar_ref / ar_target
  if r < 1.0:
    r = 1.0 / r
  return ref_fov['percent'] * r


def calc_circle_image_ratio(radius, img_w, img_h):
  """Calculate the percent of area the input circle covers in input image.

  Args:
    radius: radius of circle
    img_w: int width of image
    img_h: int height of image
  Returns:
    fov_percent: float % of image covered by circle
  """
  return 100 * math.pi * math.pow(radius, 2) / (img_w * img_h)


def find_fov_reference(cam, req, props, raw_bool, ref_img_name_stem):
  """Determine the circle coverage of the image in reference image.

  Captures a full-frame RAW or JPEG and uses its aspect ratio and circle center
  location as ground truth for the other jpeg or yuv images.

  The intrinsics and distortion coefficients are meant for full-sized RAW,
  so convert_capture_to_rgb_image returns a 2x downsampled version, so resizes
  RGB back to full size.

  If the device supports lens distortion correction, applies the coefficients on
  the RAW image so it can be compared to YUV/JPEG outputs which are subject
  to the same correction via ISP.

  Finds circle size and location for reference values in calculations for other
  formats.

  Args:
    cam: camera object
    req: camera request
    props: camera properties
    raw_bool: True if RAW available
    ref_img_name_stem: test _NAME + location to save data

  Returns:
    ref_fov: dict with {fmt, % coverage, w, h, circle_w, circle_h}
    cc_ct_gt: circle center position relative to the center of image.
    aspect_ratio_gt: aspect ratio of the detected circle in float.
  """
  logging.debug('Creating references for fov_coverage')
  if raw_bool:
    logging.debug('Using RAW for reference')
    fmt_type = 'RAW'
    out_surface = {'format': 'raw'}
    cap = cam.do_capture(req, out_surface)
    logging.debug('Captured RAW %dx%d', cap['width'], cap['height'])
    img = image_processing_utils.convert_capture_to_rgb_image(
        cap, props=props)
    # Resize back up to full scale.
    img = cv2.resize(img, (0, 0), fx=2.0, fy=2.0)

    if (camera_properties_utils.distortion_correction(props) and
        camera_properties_utils.intrinsic_calibration(props)):
      logging.debug('Applying intrinsic calibration and distortion params')
      fd = float(cap['metadata']['android.lens.focalLength'])
      k = camera_properties_utils.get_intrinsic_calibration(props, True, fd)
      opencv_dist = camera_properties_utils.get_distortion_matrix(props)
      k_new = cv2.getOptimalNewCameraMatrix(
          k, opencv_dist, (img.shape[1], img.shape[0]), 0)[0]
      scale = max(k_new[0][0] / k[0][0], k_new[1][1] / k[1][1])
      if scale > 1:
        k_new[0][0] = k[0][0] * scale
        k_new[1][1] = k[1][1] * scale
        img = cv2.undistort(img, k, opencv_dist, None, k_new)
      else:
        img = cv2.undistort(img, k, opencv_dist)
    size = img.shape

  else:
    logging.debug('Using JPEG for reference')
    fmt_type = 'JPEG'
    ref_fov = {}
    fmt = capture_request_utils.get_largest_jpeg_format(props)
    cap = cam.do_capture(req, fmt)
    logging.debug('Captured JPEG %dx%d', cap['width'], cap['height'])
    img = image_processing_utils.convert_capture_to_rgb_image(cap, props)
    size = (cap['height'], cap['width'])

  # Get image size.
  w = size[1]
  h = size[0]
  img_name = f'{ref_img_name_stem}_{fmt_type}_w{w}_h{h}.png'
  image_processing_utils.write_image(img, img_name, True)

  # Find circle.
  img *= 255  # cv2 needs images between [0,255].
  circle = opencv_processing_utils.find_circle(
      img, img_name, CIRCLE_MIN_AREA, CIRCLE_COLOR)
  opencv_processing_utils.append_circle_center_to_img(circle, img, img_name)

  # Determine final return values.
  if fmt_type == 'RAW':
    aspect_ratio_gt = circle['w'] / circle['h']
  else:
    aspect_ratio_gt = 1.0
  cc_ct_gt = {'hori': circle['x_offset'], 'vert': circle['y_offset']}
  fov_percent = calc_circle_image_ratio(circle['r'], w, h)
  ref_fov = {}
  ref_fov['fmt'] = fmt_type
  ref_fov['percent'] = fov_percent
  ref_fov['w'] = w
  ref_fov['h'] = h
  ref_fov['circle_w'] = circle['w']
  ref_fov['circle_h'] = circle['h']
  logging.debug('Using %s reference: %s', fmt_type, str(ref_fov))
  return ref_fov, cc_ct_gt, aspect_ratio_gt
