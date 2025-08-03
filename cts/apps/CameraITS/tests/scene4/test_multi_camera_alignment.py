# Copyright 2018 The Android Open Source Project
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
"""Verify multi-camera alignment using internal parameters."""


import logging
import math
import os.path

import cv2
from mobly import test_runner
import numpy as np

import its_base_test
import camera_properties_utils
import capture_request_utils
import image_processing_utils
import its_session_utils
import opencv_processing_utils

_ALIGN_TOL_MM = 5.0  # mm
_ALIGN_TOL = 0.01  # multiplied by sensor diagonal to convert to pixels
_CHART_DISTANCE_RTOL = 0.1
_CIRCLE_COLOR = 0  # [0: black, 255: white]
_CIRCLE_MIN_AREA = 0.005  # multiplied by image size
_CIRCLE_RTOL = 0.1  # 10%
_CM_TO_M = 1E-2
_FMT_CODE_RAW = 0x20
_FMT_CODE_YUV = 0x23
_M_TO_MM = 1E3
_MM_TO_UM = 1E3
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_REFERENCE_GYRO = 1
_REFERENCE_UNDEFINED = 2
_TEST_REQUIRED_MPC = 33
_TRANS_MATRIX_REF = np.array([0, 0, 0])  # translation matrix for ref cam is 000


def convert_cap_and_prep_img(cap, props, fmt, img_name):
  """Convert the capture to an RGB image and prep image.

  Args:
    cap: capture element
    props: dict of capture properties
    fmt: capture format ('raw' or 'yuv')
    img_name: name to save image as

  Returns:
    img uint8 numpy array
  """

  img = image_processing_utils.convert_capture_to_rgb_image(cap, props=props)

  # save image
  image_processing_utils.write_image(img, img_name)

  # convert [0, 1] image to [0, 255] and cast as uint8
  img = image_processing_utils.convert_image_to_uint8(img)

  # scale to match calibration data if RAW
  if fmt == 'raw':
    img = cv2.resize(img, None, fx=2, fy=2)

  return img


def calc_pixel_size(props):
  ar = props['android.sensor.info.pixelArraySize']
  sensor_size = props['android.sensor.info.physicalSize']
  pixel_size_w = sensor_size['width'] / ar['width']
  pixel_size_h = sensor_size['height'] / ar['height']
  logging.debug('pixel size(um): %.2f x %.2f',
                pixel_size_w * _MM_TO_UM, pixel_size_h * _MM_TO_UM)
  return (pixel_size_w + pixel_size_h) / 2 * _MM_TO_UM


def select_ids_to_test(ids, props, chart_distance):
  """Determine the best 2 cameras to test for the rig used.

  Cameras are pre-filtered to only include supportable cameras.
  Supportable cameras are: YUV(RGB), RAW(Bayer)

  Args:
    ids: unicode string; physical camera ids
    props: dict; physical camera properties dictionary
    chart_distance: float; distance to chart in meters
  Returns:
    test_ids to be tested
  """
  chart_distance = abs(chart_distance)*100  # convert M to CM
  test_ids = []
  for i in ids:
    sensor_size = props[i]['android.sensor.info.physicalSize']
    focal_l = props[i]['android.lens.info.availableFocalLengths'][0]
    diag = math.sqrt(sensor_size['height'] ** 2 + sensor_size['width'] ** 2)
    fov = round(2 * math.degrees(math.atan(diag / (2 * focal_l))), 2)
    logging.debug('Camera: %s, FoV: %.2f, chart_distance: %.1fcm', i, fov,
                  chart_distance)
    # determine best combo with rig used or recommend different rig
    if (opencv_processing_utils.FOV_THRESH_TELE < fov <
        opencv_processing_utils.FOV_THRESH_UW):
      test_ids.append(i)  # RFoV camera
    elif fov < opencv_processing_utils.FOV_THRESH_TELE40:
      logging.debug('Skipping camera. Not appropriate multi-camera testing.')
      continue  # super-TELE camera
    elif (fov <= opencv_processing_utils.FOV_THRESH_TELE and
          math.isclose(chart_distance,
                       opencv_processing_utils.CHART_DISTANCE_31CM,
                       rel_tol=_CHART_DISTANCE_RTOL)):
      test_ids.append(i)  # TELE camera in RFoV rig
    elif (fov >= opencv_processing_utils.FOV_THRESH_UW and
          math.isclose(chart_distance,
                       opencv_processing_utils.CHART_DISTANCE_22CM,
                       rel_tol=_CHART_DISTANCE_RTOL)):
      test_ids.append(i)  # WFoV camera in WFoV rig
    else:
      logging.debug('Skipping camera. Not appropriate for test rig.')

  if len(test_ids) < 2:
    raise AssertionError('Error: started with 2+ cameras, reduced to <2 based '
                         f'on FoVs. Wrong test rig? test_ids: {test_ids}')
  return test_ids[0:2]


def determine_valid_out_surfaces(cam, props, fmt, cap_camera_ids, sizes):
  """Determine a valid output surfaces for captures.

  Args:
    cam:                obj; camera object
    props:              dict; props for the physical cameras
    fmt:                str; capture format ('yuv' or 'raw')
    cap_camera_ids:     list; camera capture ids
    sizes:              dict; valid physical sizes for the cap_camera_ids

  Returns:
    valid out_surfaces
  """
  valid_stream_combo = False

  # try simultaneous capture
  w, h = capture_request_utils.get_available_output_sizes('yuv', props)[0]
  out_surfaces = [{'format': 'yuv', 'width': w, 'height': h},
                  {'format': fmt, 'physicalCamera': cap_camera_ids[0],
                   'width': sizes[cap_camera_ids[0]][0],
                   'height': sizes[cap_camera_ids[0]][1]},
                  {'format': fmt, 'physicalCamera': cap_camera_ids[1],
                   'width': sizes[cap_camera_ids[1]][0],
                   'height': sizes[cap_camera_ids[1]][1]},]
  valid_stream_combo = cam.is_stream_combination_supported(out_surfaces)

  # try each camera individually
  if not valid_stream_combo:
    out_surfaces = []
    for cap_id in cap_camera_ids:
      out_surface = {'format': fmt, 'physicalCamera': cap_id,
                     'width': sizes[cap_id][0],
                     'height': sizes[cap_id][1]}
      valid_stream_combo = cam.is_stream_combination_supported(out_surface)
      if valid_stream_combo:
        out_surfaces.append(out_surface)
      else:
        camera_properties_utils.skip_unless(valid_stream_combo)

  return out_surfaces


def take_images(cam, caps, props, fmt, cap_camera_ids, out_surfaces):
  """Capture images.

  Args:
    cam: obj; camera object
    caps: dict; capture results indexed by (fmt, id)
    props: dict; props for the physical cameras
    fmt: str; capture format ('yuv' or 'raw')
    cap_camera_ids: list; camera capture ids
    out_surfaces: list; valid output surfaces for caps

  Returns:
    caps: dict; capture information indexed by (fmt, cap_id)
  """

  logging.debug('out_surfaces: %s', str(out_surfaces))
  if len(out_surfaces) == 3:  # do simultaneous capture
    # Do 3A without getting the values
    cam.do_3a(lock_ae=True, lock_awb=True)
    req = capture_request_utils.auto_capture_request(props=props, do_af=True)
    _, caps[(fmt,
             cap_camera_ids[0])], caps[(fmt,
                                        cap_camera_ids[1])] = cam.do_capture(
                                            req, out_surfaces)

  else:  # step through cameras individually
    for i, out_surface in enumerate(out_surfaces):
      # Do 3A without getting the values
      cam.do_3a(lock_ae=True, lock_awb=True)
      req = capture_request_utils.auto_capture_request(props=props, do_af=True)
      caps[(fmt, cap_camera_ids[i])] = cam.do_capture(req, out_surface)

  return caps


def undo_zoom(cap, circle):
  """Correct coordinates and size of circle for zoom.

  Assume that the maximum physical YUV image size is close to active array size.

  Args:
    cap: camera capture element
    circle: dict of circle values
  Returns:
    unzoomed circle dict
  """
  yuv_w = cap['width']
  yuv_h = cap['height']
  logging.debug('cap size: %d x %d', yuv_w, yuv_h)
  cr = cap['metadata']['android.scaler.cropRegion']
  cr_w = cr['right'] - cr['left']
  cr_h = cr['bottom'] - cr['top']

  # Offset due to aspect ratio difference of crop region and yuv
  # - fit yuv box inside of differently shaped cr box
  yuv_aspect = yuv_w / yuv_h
  relative_aspect = yuv_aspect / (cr_w/cr_h)
  if relative_aspect > 1:
    zoom_ratio = yuv_w / cr_w
    yuv_x = 0
    yuv_y = (cr_h - cr_w / yuv_aspect) / 2
  else:
    zoom_ratio = yuv_h / cr_h
    yuv_x = (cr_w - cr_h * yuv_aspect) / 2
    yuv_y = 0

  circle['x'] = cr['left'] + yuv_x + circle['x'] / zoom_ratio
  circle['y'] = cr['top'] + yuv_y + circle['y'] / zoom_ratio
  circle['r'] = circle['r'] / zoom_ratio

  logging.debug(' Calculated zoom ratio: %.3f', zoom_ratio)
  logging.debug(' Corrected circle X: %.2f', circle['x'])
  logging.debug(' Corrected circle Y: %.2f', circle['y'])
  logging.debug(' Corrected circle radius: %.2f', circle['r'])

  return circle


def convert_to_world_coordinates(x, y, r, t, k, z_w):
  """Convert x,y coordinates to world coordinates.

  Conversion equation is:
  A = [[x*r[2][0] - dot(k_row0, r_col0), x*r_[2][1] - dot(k_row0, r_col1)],
       [y*r[2][0] - dot(k_row1, r_col0), y*r_[2][1] - dot(k_row1, r_col1)]]
  b = [[z_w*dot(k_row0, r_col2) + dot(k_row0, t) - x*(r[2][2]*z_w + t[2])],
       [z_w*dot(k_row1, r_col2) + dot(k_row1, t) - y*(r[2][2]*z_w + t[2])]]

  [[x_w], [y_w]] = inv(A) * b

  Args:
    x: x location in pixel space
    y: y location in pixel space
    r: rotation matrix
    t: translation matrix
    k: intrinsic matrix
    z_w: z distance in world space

  Returns:
    x_w:    x in meters in world space
    y_w:    y in meters in world space
  """
  c_1 = r[2, 2] * z_w + t[2]
  k_x1 = np.dot(k[0, :], r[:, 0])
  k_x2 = np.dot(k[0, :], r[:, 1])
  k_x3 = z_w * np.dot(k[0, :], r[:, 2]) + np.dot(k[0, :], t)
  k_y1 = np.dot(k[1, :], r[:, 0])
  k_y2 = np.dot(k[1, :], r[:, 1])
  k_y3 = z_w * np.dot(k[1, :], r[:, 2]) + np.dot(k[1, :], t)

  a = np.array([[x*r[2][0]-k_x1, x*r[2][1]-k_x2],
                [y*r[2][0]-k_y1, y*r[2][1]-k_y2]])
  b = np.array([[k_x3-x*c_1], [k_y3-y*c_1]])
  return (float(x) for x in np.dot(np.linalg.inv(a), b))


def convert_to_image_coordinates(p_w, r, t, k):
  p_c = np.dot(r, p_w) + t
  p_h = np.dot(k, p_c)
  return p_h[0] / p_h[2], p_h[1] / p_h[2]


def define_reference_camera(pose_reference, cam_reference):
  """Determine the reference camera.

  Args:
    pose_reference: 0 for cameras, 1 for gyro
    cam_reference: dict with key of physical camera and value True/False
  Returns:
    i_ref: physical id of reference camera
    i_2nd: physical id of secondary camera
  """

  if pose_reference == _REFERENCE_GYRO:
    logging.debug('pose_reference is GYRO')
    keys = list(cam_reference.keys())
    i_ref = keys[0]  # pick first camera as ref
    i_2nd = keys[1]
  else:
    logging.debug('pose_reference is CAMERA')
    i_refs = [k for (k, v) in cam_reference.items() if v]
    i_ref = i_refs[0]
    if len(i_refs) > 1:
      logging.debug('Warning: more than 1 reference camera. Check translation '
                    'matrices. cam_reference: %s', str(cam_reference))
      i_2nd = i_refs[1]  # use second camera since both at same location
    else:
      i_2nd = next(k for (k, v) in cam_reference.items() if not v)
  return i_ref, i_2nd


class MultiCameraAlignmentTest(its_base_test.ItsBaseTest):

  """Test the multi camera system parameters related to camera spacing.

  Using the multi-camera physical cameras, take a picture of scene4
  (a black circle and surrounding square on a white background) with
  one of the physical cameras. Then find the circle center and radius. Using
  the parameters:
      android.lens.poseReference
      android.lens.poseTranslation
      android.lens.poseRotation
      android.lens.instrinsicCalibration
      android.lens.distortion (if available)
  project the circle center to the world coordinates for each camera.
  Compare the difference between the two cameras' circle centers in
  world coordinates.

  Reproject the world coordinates back to pixel coordinates and compare
  against originals as a correctness check.

  Compare the circle sizes if the focal lengths of the cameras are
  different using
      android.lens.availableFocalLengths.
  """

  def test_multi_camera_alignment(self):
    # capture images
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      name_with_log_path = os.path.join(self.log_path, _NAME)
      chart_distance = self.chart_distance * _CM_TO_M

      # check media performance class
      should_run = (camera_properties_utils.read_3a(props) and
                    camera_properties_utils.per_frame_control(props) and
                    camera_properties_utils.logical_multi_camera(props) and
                    camera_properties_utils.backward_compatible(props))
      media_performance_class = its_session_utils.get_media_performance_class(
          self.dut.serial)
      cameras_facing_same_direction = cam.get_facing_to_ids().get(
          props['android.lens.facing'], [])
      has_multiple_same_facing_cameras = len(cameras_facing_same_direction) > 1

      if (media_performance_class >= _TEST_REQUIRED_MPC and
          not should_run and
          cam.is_primary_camera() and
          has_multiple_same_facing_cameras and
          (props['android.lens.facing'] ==
           camera_properties_utils.LENS_FACING['BACK'])
          ):
        logging.error('Found multiple camera IDs %s facing in the same '
                      'direction as primary camera %s.',
                      cameras_facing_same_direction, self.camera_id)
        its_session_utils.raise_mpc_assertion_error(
            _TEST_REQUIRED_MPC, _NAME, media_performance_class)

      # check SKIP conditions
      camera_properties_utils.skip_unless(should_run)

      # load chart for scene
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet, self.chart_distance)

      pose_reference = props['android.lens.poseReference']

      # Convert chart_distance for lens facing back
      if (props['android.lens.facing'] ==
          camera_properties_utils.LENS_FACING['BACK']):
        # API spec defines +z is pointing out from screen
        logging.debug('lens facing BACK')
        chart_distance *= -1

      # find physical camera IDs
      ids = camera_properties_utils.logical_multi_camera_physical_ids(props)
      physical_props = {}
      physical_ids = []
      physical_raw_ids = []
      for i in ids:
        physical_props[i] = cam.get_camera_properties_by_id(i)
        if physical_props[i][
            'android.lens.poseReference'] == _REFERENCE_UNDEFINED:
          continue
        # find YUV+RGB capable physical cameras
        if (camera_properties_utils.backward_compatible(physical_props[i]) and
            not camera_properties_utils.mono_camera(physical_props[i])):
          physical_ids.append(i)
        # find RAW+RGB capable physical cameras
        if (camera_properties_utils.backward_compatible(physical_props[i]) and
            not camera_properties_utils.mono_camera(physical_props[i]) and
            camera_properties_utils.raw16(physical_props[i])):
          physical_raw_ids.append(i)

      # determine formats and select cameras
      fmts = ['yuv']
      if len(physical_raw_ids) >= 2:
        fmts.insert(0, 'raw')  # add RAW to analysis if enough cameras
        logging.debug('Selecting RAW+RGB supported cameras')
        physical_raw_ids = select_ids_to_test(physical_raw_ids, physical_props,
                                              chart_distance)
      logging.debug('Selecting YUV+RGB cameras')
      camera_properties_utils.skip_unless(len(physical_ids) >= 2)
      physical_ids = select_ids_to_test(physical_ids, physical_props,
                                        chart_distance)

      # do captures for valid formats
      caps = {}
      for i, fmt in enumerate(fmts):
        physical_sizes = {}
        capture_cam_ids = physical_ids
        fmt_code = _FMT_CODE_YUV
        if fmt == 'raw':
          capture_cam_ids = physical_raw_ids
          fmt_code = _FMT_CODE_RAW
        for physical_id in capture_cam_ids:
          configs = physical_props[physical_id][
              'android.scaler.streamConfigurationMap'][
                  'availableStreamConfigurations']
          fmt_configs = [cfg for cfg in configs if cfg['format'] == fmt_code]
          out_configs = [cfg for cfg in fmt_configs if not cfg['input']]
          out_sizes = [(cfg['width'], cfg['height']) for cfg in out_configs]
          physical_sizes[physical_id] = max(out_sizes, key=lambda item: item[1])

        out_surfaces = determine_valid_out_surfaces(
            cam, props, fmt, capture_cam_ids, physical_sizes)
        caps = take_images(
            cam, caps, physical_props, fmt, capture_cam_ids, out_surfaces)

    # process images for correctness
    for j, fmt in enumerate(fmts):
      size = {}
      k = {}
      cam_reference = {}
      r = {}
      t = {}
      circle = {}
      fl = {}
      sensor_diag = {}
      pixel_sizes = {}
      capture_cam_ids = physical_ids
      if fmt == 'raw':
        capture_cam_ids = physical_raw_ids
      logging.debug('Format: %s', str(fmt))
      for i in capture_cam_ids:
        # convert cap and prep image
        img_name = f'{name_with_log_path}_{fmt}_{i}.jpg'
        img = convert_cap_and_prep_img(
            caps[(fmt, i)], physical_props[i], fmt, img_name)
        size[i] = (caps[fmt, i]['width'], caps[fmt, i]['height'])

        # load parameters for each physical camera
        if j == 0:
          logging.debug('Camera %s', i)
        k[i] = camera_properties_utils.get_intrinsic_calibration(
            physical_props[i], j == 0)
        r[i] = camera_properties_utils.get_rotation_matrix(
            physical_props[i], j == 0)
        t[i] = camera_properties_utils.get_translation_matrix(
            physical_props[i], j == 0)

        # API spec defines poseTranslation as the world coordinate p_w_cam of
        # optics center. When applying [R|t] to go from world coordinates to
        # camera coordinates, we need -R*p_w_cam of the coordinate reported in
        # metadata.
        # ie. for a camera with optical center at world coordinate (5, 4, 3)
        # and identity rotation, to convert a world coordinate into the
        # camera's coordinate, we need a translation vector of [-5, -4, -3]
        # so that: [I|[-5, -4, -3]^T] * [5, 4, 3]^T = [0,0,0]^T
        t[i] = -1.0 * np.dot(r[i], t[i])

        if (t[i] == _TRANS_MATRIX_REF).all():
          cam_reference[i] = True
        else:
          cam_reference[i] = False

        # Correct lens distortion to image (if available) and save before/after
        if (camera_properties_utils.distortion_correction(physical_props[i]) and
            camera_properties_utils.intrinsic_calibration(physical_props[i]) and
            fmt == 'raw'):
          cv2_distort = camera_properties_utils.get_distortion_matrix(
              physical_props[i])
          if cv2_distort is None:
            raise AssertionError(f'Camera {i} has no distortion matrix!')
          if not np.any(cv2_distort):
            logging.warning('Camera %s has distortion matrix of all zeroes', i)
          image_processing_utils.write_image(
              img/255, f'{name_with_log_path}_{fmt}_{i}.jpg')
          img = cv2.undistort(img, k[i], cv2_distort)
          image_processing_utils.write_image(
              img/255, f'{name_with_log_path}_{fmt}_correct_{i}.jpg')

        # Find the circles in grayscale image
        circle[i] = opencv_processing_utils.find_circle(
            img, f'{name_with_log_path}_{fmt}_gray_{i}.jpg',
            _CIRCLE_MIN_AREA, _CIRCLE_COLOR)
        logging.debug('Circle radius %s: %.2f', format(i), circle[i]['r'])

        # Undo zoom to image (if applicable).
        if fmt == 'yuv':
          circle[i] = undo_zoom(caps[(fmt, i)], circle[i])

        # Find focal length, pixel & sensor size
        fl[i] = physical_props[i]['android.lens.info.availableFocalLengths'][0]
        pixel_sizes[i] = calc_pixel_size(physical_props[i])
        sensor_diag[i] = math.sqrt(size[i][0] ** 2 + size[i][1] ** 2)

      i_ref, i_2nd = define_reference_camera(pose_reference, cam_reference)
      logging.debug('reference camera: %s, secondary camera: %s', i_ref, i_2nd)

      # Convert circle centers to real world coordinates
      x_w = {}
      y_w = {}
      for i in [i_ref, i_2nd]:
        x_w[i], y_w[i] = convert_to_world_coordinates(
            circle[i]['x'], circle[i]['y'], r[i], t[i], k[i], chart_distance)

      # Back convert to image coordinates for correctness check
      x_p = {}
      y_p = {}
      x_p[i_2nd], y_p[i_2nd] = convert_to_image_coordinates(
          [x_w[i_ref], y_w[i_ref], chart_distance], r[i_2nd], t[i_2nd],
          k[i_2nd])
      x_p[i_ref], y_p[i_ref] = convert_to_image_coordinates(
          [x_w[i_2nd], y_w[i_2nd], chart_distance], r[i_ref], t[i_ref],
          k[i_ref])

      # Summarize results
      for i in [i_ref, i_2nd]:
        logging.debug(' Camera: %s', i)
        logging.debug(' x, y (pixels): %.1f, %.1f', circle[i]['x'],
                      circle[i]['y'])
        logging.debug(' x_w, y_w (mm): %.2f, %.2f', x_w[i] * 1.0E3,
                      y_w[i] * 1.0E3)
        logging.debug(' x_p, y_p (pixels): %.1f, %.1f', x_p[i], y_p[i])

      # Check center locations
      err_mm = np.linalg.norm(np.array([x_w[i_ref], y_w[i_ref]]) -
                              np.array([x_w[i_2nd], y_w[i_2nd]])) * _M_TO_MM
      logging.debug('Center location err (mm): %.2f', err_mm)
      if err_mm > _ALIGN_TOL_MM:
        raise AssertionError(
            f'Centers {i_ref} <-> {i_2nd} too different! '
            f'val={err_mm:.2f}, ATOL={_ALIGN_TOL_MM} mm')

      # Check projections back into pixel space
      for i in [i_ref, i_2nd]:
        err = np.linalg.norm(np.array([circle[i]['x'], circle[i]['y']]) -
                             np.array([x_p[i], y_p[i]]).reshape(1, -1))
        logging.debug('Camera %s projection error (pixels): %.1f', i, err)
        tol = _ALIGN_TOL * sensor_diag[i]
        if err >= tol:
          raise AssertionError(f'Camera {i} project location too different! '
                               f'diff={err:.2f}, ATOL={tol:.2f} pixels')

      # Check focal length and circle size if more than 1 focal length
      if len(fl) > 1:
        logging.debug('Circle radii (pixels); ref: %.1f, 2nd: %.1f',
                      circle[i_ref]['r'], circle[i_2nd]['r'])
        logging.debug('Focal lengths (diopters); ref: %.2f, 2nd: %.2f',
                      fl[i_ref], fl[i_2nd])
        logging.debug('Pixel size (um); ref: %.2f, 2nd: %.2f',
                      pixel_sizes[i_ref], pixel_sizes[i_2nd])
        if not math.isclose(circle[i_ref]['r']*pixel_sizes[i_ref]/fl[i_ref],
                            circle[i_2nd]['r']*pixel_sizes[i_2nd]/fl[i_2nd],
                            rel_tol=_CIRCLE_RTOL):
          raise AssertionError(
              f'Circle size scales improperly! RTOL: {_CIRCLE_RTOL} '
              'Metric: radius*pixel_size/focal_length should be equal.')

if __name__ == '__main__':
  test_runner.main()
