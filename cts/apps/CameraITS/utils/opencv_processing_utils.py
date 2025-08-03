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
"""Image processing utilities using openCV."""


import logging
import math
import os
import pathlib
import cv2
import numpy
import scipy.spatial

import capture_request_utils
import error_util
import image_processing_utils

ANGLE_CHECK_TOL = 1  # degrees
ANGLE_NUM_MIN = 10  # Minimum number of angles for find_angle() to be valid


TEST_IMG_DIR = os.path.join(os.environ['CAMERA_ITS_TOP'], 'test_images')
CHART_FILE = os.path.join(TEST_IMG_DIR, 'ISO12233.png')
CHART_HEIGHT_31CM = 13.5  # cm
CHART_HEIGHT_22CM = 9.5  # cm
CHART_DISTANCE_31CM = 31.0  # cm
CHART_DISTANCE_22CM = 22.0  # cm
CHART_SCALE_RTOL = 0.1
CHART_SCALE_START = 0.65
CHART_SCALE_STOP = 1.35
CHART_SCALE_STEP = 0.025

CIRCLE_AR_ATOL = 0.1  # circle aspect ratio tolerance
CIRCLISH_ATOL = 0.10  # contour area vs ideal circle area & aspect ratio TOL
CIRCLISH_LOW_RES_ATOL = 0.15  # loosen for low res images
CIRCLE_MIN_PTS = 20
CIRCLE_RADIUS_NUMPTS_THRESH = 2  # contour num_pts/radius: empirically ~3x
CIRCLE_COLOR_ATOL = 0.05  # circle color fill tolerance
CIRCLE_LOCATION_VARIATION_RTOL = 0.05  # tolerance to remove similar circles

CV2_LINE_THICKNESS = 3  # line thickness for drawing on images
CV2_RED = (255, 0, 0)  # color in cv2 to draw lines
CV2_GREEN = (0, 1, 0)
CV2_THRESHOLD_BLOCK_SIZE = 11
CV2_THRESHOLD_CONSTANT = 2

CV2_HOME_DIRECTORY = os.path.dirname(cv2.__file__)
CV2_ALTERNATE_DIRECTORY = pathlib.Path(CV2_HOME_DIRECTORY).parents[3]
HAARCASCADE_FILE_NAME = 'haarcascade_frontalface_default.xml'

FACES_ALIGNED_MIN_NUM = 2
FACE_CENTER_MATCH_TOL_X = 10  # 10 pixels or ~1.5% in 640x480 image
FACE_CENTER_MATCH_TOL_Y = 20  # 20 pixels or ~4% in 640x480 image
FACE_CENTER_MIN_LOGGING_DIST = 50
FACE_MIN_CENTER_DELTA = 15

FOV_THRESH_TELE25 = 25
FOV_THRESH_TELE40 = 40
FOV_THRESH_TELE = 60
FOV_THRESH_UW = 90

IMAGE_ROTATION_THRESHOLD = 2

LOW_RES_IMG_THRESH = 320 * 240

RGB_GRAY_WEIGHTS = (0.299, 0.587, 0.114)  # RGB to Gray conversion matrix

SCALE_WIDE_IN_22CM_RIG = 0.67
SCALE_TELE_IN_22CM_RIG = 0.5
SCALE_TELE_IN_31CM_RIG = 0.67
SCALE_TELE40_IN_22CM_RIG = 0.33
SCALE_TELE40_IN_31CM_RIG = 0.5
SCALE_TELE25_IN_31CM_RIG = 0.33

SQUARE_AREA_MIN_REL = 0.05  # Minimum size for square relative to image area
SQUARE_CROP_MARGIN = 0  # Set to aid detection of QR codes
SQUARE_TOL = 0.05  # Square W vs H mismatch RTOL
SQUARISH_RTOL = 0.10
SQUARISH_AR_RTOL = 0.10

VGA_HEIGHT = 480
VGA_WIDTH = 640


def convert_to_gray(img):
  """Returns openCV grayscale image.

  Args:
    img: A numpy image.
  Returns:
    An openCV image converted to grayscale.
  """
  return numpy.dot(img[..., :3], RGB_GRAY_WEIGHTS)


def convert_to_y(img):
  """Returns a Y image from a BGR image.

  Args:
    img: An openCV image.
  Returns:
    An openCV image converted to Y.
  """
  y, _, _ = cv2.split(cv2.cvtColor(img, cv2.COLOR_BGR2YUV))
  return y


def binarize_image(img_gray):
  """Returns a binarized image based on cv2 thresholds.

  Args:
    img_gray: A grayscale openCV image.
  Returns:
    An openCV image binarized to 0 (black) and 255 (white).
  """
  _, img_bw = cv2.threshold(numpy.uint8(img_gray), 0, 255,
                            cv2.THRESH_BINARY + cv2.THRESH_OTSU)
  return img_bw


def _load_opencv_haarcascade_file():
  """Return Haar Cascade file for face detection."""
  for cv2_directory in (CV2_HOME_DIRECTORY, CV2_ALTERNATE_DIRECTORY,):
    for path, _, files in os.walk(cv2_directory):
      if HAARCASCADE_FILE_NAME in files:
        haarcascade_file = os.path.join(path, HAARCASCADE_FILE_NAME)
        logging.debug('Haar Cascade file location: %s', haarcascade_file)
        return haarcascade_file
  raise error_util.CameraItsError('haarcascade_frontalface_default.xml was '
                                  f'not found in {CV2_HOME_DIRECTORY} '
                                  f'or {CV2_ALTERNATE_DIRECTORY}')


def find_opencv_faces(img, scale_factor, min_neighbors):
  """Finds face rectangles with openCV.

  Args:
    img: numpy array; 3-D RBG image with [0,1] values
    scale_factor: float, specifies how much image size is reduced at each scale
    min_neighbors: int, specifies minimum number of neighbors to keep rectangle
  Returns:
    List of rectangles with faces
  """
  # prep opencv
  opencv_haarcascade_file = _load_opencv_haarcascade_file()
  face_cascade = cv2.CascadeClassifier(opencv_haarcascade_file)
  img_255 = img * 255
  img_gray = cv2.cvtColor(img_255.astype(numpy.uint8), cv2.COLOR_RGB2GRAY)

  # find face rectangles with opencv
  faces_opencv = face_cascade.detectMultiScale(
      img_gray, scale_factor, min_neighbors)
  logging.debug('%s', str(faces_opencv))
  return faces_opencv


def find_all_contours(img):
  cv2_version = cv2.__version__
  if cv2_version.startswith('3.'):  # OpenCV 3.x
    _, contours, _ = cv2.findContours(img, cv2.RETR_TREE,
                                      cv2.CHAIN_APPROX_SIMPLE)
  else:  # OpenCV 2.x and 4.x
    contours, _ = cv2.findContours(img, cv2.RETR_TREE, cv2.CHAIN_APPROX_SIMPLE)
  return contours


def calc_chart_scaling(chart_distance, camera_fov):
  """Returns charts scaling factor.

  Args:
   chart_distance: float; distance in cm from camera of displayed chart
   camera_fov: float; camera field of view.

  Returns:
   chart_scaling: float; scaling factor for chart
  """
  chart_scaling = 1.0
  fov = float(camera_fov)
  is_chart_distance_22cm = math.isclose(
      chart_distance, CHART_DISTANCE_22CM, rel_tol=CHART_SCALE_RTOL)
  is_chart_distance_31cm = math.isclose(
      chart_distance, CHART_DISTANCE_31CM, rel_tol=CHART_SCALE_RTOL)

  if FOV_THRESH_TELE < fov < FOV_THRESH_UW and is_chart_distance_22cm:
    chart_scaling = SCALE_WIDE_IN_22CM_RIG
  elif FOV_THRESH_TELE40 < fov <= FOV_THRESH_TELE and is_chart_distance_22cm:
    chart_scaling = SCALE_TELE_IN_22CM_RIG
  elif fov <= FOV_THRESH_TELE40 and is_chart_distance_22cm:
    chart_scaling = SCALE_TELE40_IN_22CM_RIG
  elif (fov <= FOV_THRESH_TELE25 and
        is_chart_distance_31cm or
        chart_distance > CHART_DISTANCE_31CM):
    chart_scaling = SCALE_TELE25_IN_31CM_RIG
  elif fov <= FOV_THRESH_TELE40 and is_chart_distance_31cm:
    chart_scaling = SCALE_TELE40_IN_31CM_RIG
  elif fov <= FOV_THRESH_TELE and is_chart_distance_31cm:
    chart_scaling = SCALE_TELE_IN_31CM_RIG
  return chart_scaling


def scale_img(img, scale=1.0):
  """Scale image based on a real number scale factor."""
  dim = (int(img.shape[1] * scale), int(img.shape[0] * scale))
  return cv2.resize(img.copy(), dim, interpolation=cv2.INTER_AREA)


def gray_scale_img(img):
  """Return gray scale version of image."""
  if len(img.shape) == 2:
    img_gray = img.copy()
  elif len(img.shape) == 3:
    if img.shape[2] == 1:
      img_gray = img[:, :, 0].copy()
    else:
      img_gray = cv2.cvtColor(img, cv2.COLOR_RGB2GRAY)
  return img_gray


class Chart(object):
  """Definition for chart object.

  Defines PNG reference file, chart, size, distance and scaling range.
  """

  def __init__(
      self,
      cam,
      props,
      log_path,
      chart_file=None,
      height=None,
      distance=None,
      scale_start=None,
      scale_stop=None,
      scale_step=None,
      rotation=None):
    """Initial constructor for class.

    Args:
     cam: open ITS session
     props: camera properties object
     log_path: log path to store the captured images.
     chart_file: str; absolute path to png file of chart
     height: float; height in cm of displayed chart
     distance: float; distance in cm from camera of displayed chart
     scale_start: float; start value for scaling for chart search
     scale_stop: float; stop value for scaling for chart search
     scale_step: float; step value for scaling for chart search
     rotation: clockwise rotation in degrees (multiple of 90) or None
    """
    self._file = chart_file or CHART_FILE
    if math.isclose(
        distance, CHART_DISTANCE_31CM, rel_tol=CHART_SCALE_RTOL):
      self._height = height or CHART_HEIGHT_31CM
      self._distance = distance
    else:
      self._height = height or CHART_HEIGHT_22CM
      self._distance = CHART_DISTANCE_22CM
    self._scale_start = scale_start or CHART_SCALE_START
    self._scale_stop = scale_stop or CHART_SCALE_STOP
    self._scale_step = scale_step or CHART_SCALE_STEP
    self.opt_val = None
    self.locate(cam, props, log_path, rotation)

  def _set_scale_factors_to_one(self):
    """Set scale factors to 1.0 for skipped tests."""
    self.wnorm = 1.0
    self.hnorm = 1.0
    self.xnorm = 0.0
    self.ynorm = 0.0
    self.scale = 1.0

  def _calc_scale_factors(self, cam, props, fmt, log_path, rotation):
    """Take an image with s, e, & fd to find the chart location.

    Args:
     cam: An open its session.
     props: Properties of cam
     fmt: Image format for the capture
     log_path: log path to save the captured images.
     rotation: clockwise rotation of template in degrees (multiple of 90) or
       None

    Returns:
      template: numpy array; chart template for locator
      img_3a: numpy array; RGB image for chart location
      scale_factor: float; scaling factor for chart search
    """
    req = capture_request_utils.auto_capture_request()
    cap_chart = image_processing_utils.stationary_lens_cap(cam, req, fmt)
    img_3a = image_processing_utils.convert_capture_to_rgb_image(
        cap_chart, props)
    img_3a = image_processing_utils.rotate_img_per_argv(img_3a)
    af_scene_name = os.path.join(log_path, 'af_scene.jpg')
    image_processing_utils.write_image(img_3a, af_scene_name)
    template = cv2.imread(self._file, cv2.IMREAD_ANYDEPTH)
    if rotation is not None:
      logging.debug('Rotating template by %d degrees', rotation)
      template = numpy.rot90(template, k=rotation / 90)
    focal_l = cap_chart['metadata']['android.lens.focalLength']
    pixel_pitch = (
        props['android.sensor.info.physicalSize']['height'] / img_3a.shape[0])
    logging.debug('Chart distance: %.2fcm', self._distance)
    logging.debug('Chart height: %.2fcm', self._height)
    logging.debug('Focal length: %.2fmm', focal_l)
    logging.debug('Pixel pitch: %.2fum', pixel_pitch * 1E3)
    logging.debug('Template width: %dpixels', template.shape[1])
    logging.debug('Template height: %dpixels', template.shape[0])
    chart_pixel_h = self._height * focal_l / (self._distance * pixel_pitch)
    scale_factor = template.shape[0] / chart_pixel_h
    if rotation == 90 or rotation == 270:
      # With the landscape to portrait override turned on, the width and height
      # of the active array, normally w x h, will be h x (w * (h/w)^2). Reduce
      # the applied scaling by the same factor to compensate for this, because
      # the chart will take up more of the scene. Assume w > h, since this is
      # meant for landscape sensors.
      rotate_physical_aspect = (
          props['android.sensor.info.physicalSize']['height'] /
          props['android.sensor.info.physicalSize']['width'])
      scale_factor *= rotate_physical_aspect ** 2
    logging.debug('Chart/image scale factor = %.2f', scale_factor)
    return template, img_3a, scale_factor

  def locate(self, cam, props, log_path, rotation):
    """Find the chart in the image, and append location to chart object.

    Args:
      cam: Open its session.
      props: Camera properties object.
      log_path: log path to store the captured images.
      rotation: clockwise rotation of template in degrees (multiple of 90) or
        None

    The values appended are:
    xnorm: float; [0, 1] left loc of chart in scene
    ynorm: float; [0, 1] top loc of chart in scene
    wnorm: float; [0, 1] width of chart in scene
    hnorm: float; [0, 1] height of chart in scene
    scale: float; scale factor to extract chart
    opt_val: float; The normalized match optimization value [0, 1]
    """
    fmt = {'format': 'yuv', 'width': VGA_WIDTH, 'height': VGA_HEIGHT}
    cam.do_3a()
    chart, scene, s_factor = self._calc_scale_factors(cam, props, fmt, log_path,
                                                      rotation)
    scale_start = self._scale_start * s_factor
    scale_stop = self._scale_stop * s_factor
    scale_step = self._scale_step * s_factor
    offset = scale_step / 2
    self.scale = s_factor
    logging.debug('scale start: %.3f, stop: %.3f, step: %.3f',
                  scale_start, scale_stop, scale_step)
    logging.debug('Used offset of %.3f to include stop value.', offset)
    max_match = []
    # check for normalized image
    if numpy.amax(scene) <= 1.0:
      scene = (scene * 255.0).astype(numpy.uint8)
    scene_gray = gray_scale_img(scene)
    logging.debug('Finding chart in scene...')
    for scale in numpy.arange(scale_start, scale_stop + offset, scale_step):
      scene_scaled = scale_img(scene_gray, scale)
      if (scene_scaled.shape[0] < chart.shape[0] or
          scene_scaled.shape[1] < chart.shape[1]):
        logging.debug(
            'Skipped scale %.3f. scene_scaled shape: %s, chart shape: %s',
            scale, scene_scaled.shape, chart.shape)
        continue
      result = cv2.matchTemplate(scene_scaled, chart, cv2.TM_CCOEFF_NORMED)
      _, opt_val, _, top_left_scaled = cv2.minMaxLoc(result)
      logging.debug(' scale factor: %.3f, opt val: %.3f', scale, opt_val)
      max_match.append((opt_val, scale, top_left_scaled))

    # determine if optimization results are valid
    opt_values = [x[0] for x in max_match]
    if not opt_values or (2.0 * min(opt_values) > max(opt_values)):
      estring = ('Warning: unable to find chart in scene!\n'
                 'Check camera distance and self-reported '
                 'pixel pitch, focal length and hyperfocal distance.')
      logging.warning(estring)
      self._set_scale_factors_to_one()
    else:
      if (max(opt_values) == opt_values[0] or
          max(opt_values) == opt_values[len(opt_values) - 1]):
        estring = ('Warning: Chart is at extreme range of locator.')
        logging.warning(estring)
      # find max and draw bbox
      matched_scale_and_loc = max(max_match, key=lambda x: x[0])
      self.opt_val = matched_scale_and_loc[0]
      self.scale = matched_scale_and_loc[1]
      logging.debug('Optimum scale factor: %.3f', self.scale)
      logging.debug('Opt val: %.3f', self.opt_val)
      top_left_scaled = matched_scale_and_loc[2]
      logging.debug('top_left_scaled: %d, %d', top_left_scaled[0],
                    top_left_scaled[1])
      h, w = chart.shape
      bottom_right_scaled = (top_left_scaled[0] + w, top_left_scaled[1] + h)
      logging.debug('bottom_right_scaled: %d, %d', bottom_right_scaled[0],
                    bottom_right_scaled[1])
      top_left = ((top_left_scaled[0] // self.scale),
                  (top_left_scaled[1] // self.scale))
      bottom_right = ((bottom_right_scaled[0] // self.scale),
                      (bottom_right_scaled[1] // self.scale))
      self.wnorm = ((bottom_right[0]) - top_left[0]) / scene.shape[1]
      self.hnorm = ((bottom_right[1]) - top_left[1]) / scene.shape[0]
      self.xnorm = (top_left[0]) / scene.shape[1]
      self.ynorm = (top_left[1]) / scene.shape[0]
      patch = image_processing_utils.get_image_patch(scene, self.xnorm,
                                                     self.ynorm, self.wnorm,
                                                     self.hnorm)
      template_scene_name = os.path.join(log_path, 'template_scene.jpg')
      image_processing_utils.write_image(patch, template_scene_name)


def component_shape(contour):
  """Measure the shape of a connected component.

  Args:
    contour: return from cv2.findContours. A list of pixel coordinates of
    the contour.

  Returns:
    The most left, right, top, bottom pixel location, height, width, and
    the center pixel location of the contour.
  """
  shape = {'left': numpy.inf, 'right': 0, 'top': numpy.inf, 'bottom': 0,
           'width': 0, 'height': 0, 'ctx': 0, 'cty': 0}
  for pt in contour:
    if pt[0][0] < shape['left']:
      shape['left'] = pt[0][0]
    if pt[0][0] > shape['right']:
      shape['right'] = pt[0][0]
    if pt[0][1] < shape['top']:
      shape['top'] = pt[0][1]
    if pt[0][1] > shape['bottom']:
      shape['bottom'] = pt[0][1]
  shape['width'] = shape['right'] - shape['left'] + 1
  shape['height'] = shape['bottom'] - shape['top'] + 1
  shape['ctx'] = (shape['left'] + shape['right']) // 2
  shape['cty'] = (shape['top'] + shape['bottom']) // 2
  return shape


def find_circle_fill_metric(shape, img_bw, color):
  """Find the proportion of points matching a desired color on a shape's axes.

  Args:
    shape: dictionary returned by component_shape(...)
    img_bw: binarized numpy image array
    color: int of [0 or 255] 0 is black, 255 is white
  Returns:
    float: number of x, y axis points matching color / total x, y axis points
  """
  matching = 0
  total = 0
  for y in range(shape['top'], shape['bottom']):
    total += 1
    matching += 1 if img_bw[y][shape['ctx']] == color else 0
  for x in range(shape['left'], shape['right']):
    total += 1
    matching += 1 if img_bw[shape['cty']][x] == color else 0
  logging.debug('Found %d matching points out of %d', matching, total)
  return matching / total


def find_circle(img, img_name, min_area, color, use_adaptive_threshold=False):
  """Find the circle in the test image.

  Args:
    img: numpy image array in RGB, with pixel values in [0,255].
    img_name: string with image info of format and size.
    min_area: float of minimum area of circle to find
    color: int of [0 or 255] 0 is black, 255 is white
    use_adaptive_threshold: True if binarization should use adaptive threshold.

  Returns:
    circle = {'x', 'y', 'r', 'w', 'h', 'x_offset', 'y_offset'}
  """
  circle = {}
  img_size = img.shape
  if img_size[0]*img_size[1] >= LOW_RES_IMG_THRESH:
    circlish_atol = CIRCLISH_ATOL
  else:
    circlish_atol = CIRCLISH_LOW_RES_ATOL

  # convert to gray-scale image and binarize using adaptive/global threshold
  if use_adaptive_threshold:
    img_gray = cv2.cvtColor(img.astype(numpy.uint8), cv2.COLOR_BGR2GRAY)
    img_bw = cv2.adaptiveThreshold(img_gray, 255, cv2.ADAPTIVE_THRESH_MEAN_C,
                                   cv2.THRESH_BINARY, CV2_THRESHOLD_BLOCK_SIZE,
                                   CV2_THRESHOLD_CONSTANT)
  else:
    img_gray = convert_to_gray(img)
    img_bw = binarize_image(img_gray)

  # find contours
  contours = find_all_contours(255-img_bw)

  # Check each contour and find the circle bigger than min_area
  num_circles = 0
  circle_contours = []
  logging.debug('Initial number of contours: %d', len(contours))
  min_circle_area = min_area * img_size[0] * img_size[1]
  logging.debug('Screening out circles w/ radius < %.1f (pixels) or %d pts.',
                math.sqrt(min_circle_area / math.pi), CIRCLE_MIN_PTS)
  for contour in contours:
    area = cv2.contourArea(contour)
    num_pts = len(contour)
    if (area > min_circle_area and num_pts >= CIRCLE_MIN_PTS):
      shape = component_shape(contour)
      radius = (shape['width'] + shape['height']) / 4
      colour = img_bw[shape['cty']][shape['ctx']]
      circlish = (math.pi * radius**2) / area
      aspect_ratio = shape['width'] / shape['height']
      fill = find_circle_fill_metric(shape, img_bw, color)
      logging.debug('Potential circle found. radius: %.2f, color: %d, '
                    'circlish: %.3f, ar: %.3f, pts: %d, fill metric: %.3f',
                    radius, colour, circlish, aspect_ratio, num_pts, fill)
      if (colour == color and
          math.isclose(1.0, circlish, abs_tol=circlish_atol) and
          math.isclose(1.0, aspect_ratio, abs_tol=CIRCLE_AR_ATOL) and
          num_pts/radius >= CIRCLE_RADIUS_NUMPTS_THRESH and
          math.isclose(1.0, fill, abs_tol=CIRCLE_COLOR_ATOL)):
        radii = [
            image_processing_utils.distance(
                (shape['ctx'], shape['cty']), numpy.squeeze(point))
            for point in contour
        ]
        minimum_radius, maximum_radius = min(radii), max(radii)
        logging.debug('Minimum radius: %.2f, maximum radius: %.2f',
                      minimum_radius, maximum_radius)
        if circle:
          old_circle_center = (circle['x'], circle['y'])
          new_circle_center = (shape['ctx'], shape['cty'])
          # Based on image height
          center_distance_atol = img_size[0]*CIRCLE_LOCATION_VARIATION_RTOL
          if math.isclose(
              image_processing_utils.distance(
                  old_circle_center, new_circle_center),
              0,
              abs_tol=center_distance_atol
          ) and maximum_radius - minimum_radius < circle['radius_spread']:
            logging.debug('Replacing the previously found circle. '
                          'Circle located at %s has a smaller radius spread '
                          'than the previously found circle at %s. '
                          'Current radius spread: %.2f, '
                          'previous radius spread: %.2f',
                          new_circle_center, old_circle_center,
                          maximum_radius - minimum_radius,
                          circle['radius_spread'])
            circle_contours.pop()
            circle = {}
            num_circles -= 1
        circle_contours.append(contour)

        # Populate circle dictionary
        circle['x'] = shape['ctx']
        circle['y'] = shape['cty']
        circle['r'] = (shape['width'] + shape['height']) / 4
        circle['w'] = float(shape['width'])
        circle['h'] = float(shape['height'])
        circle['x_offset'] = (shape['ctx'] - img_size[1]//2) / circle['w']
        circle['y_offset'] = (shape['cty'] - img_size[0]//2) / circle['h']
        circle['radius_spread'] = maximum_radius - minimum_radius
        logging.debug('Num pts: %d', num_pts)
        logging.debug('Aspect ratio: %.3f', aspect_ratio)
        logging.debug('Circlish value: %.3f', circlish)
        logging.debug('Location: %.1f x %.1f', circle['x'], circle['y'])
        logging.debug('Radius: %.3f', circle['r'])
        logging.debug('Circle center position wrt to image center: %.3fx%.3f',
                      circle['x_offset'], circle['y_offset'])
        num_circles += 1
        # if more than one circle found, break
        if num_circles == 2:
          break

  if num_circles == 0:
    image_processing_utils.write_image(img/255, img_name, True)
    if not use_adaptive_threshold:
      return find_circle(
          img, img_name, min_area, color, use_adaptive_threshold=True)
    else:
      raise AssertionError('No circle detected. '
                           'Please take pictures according to instructions.')

  if num_circles > 1:
    image_processing_utils.write_image(img/255, img_name, True)
    cv2.drawContours(img, circle_contours, -1, CV2_RED,
                     CV2_LINE_THICKNESS)
    img_name_parts = img_name.split('.')
    image_processing_utils.write_image(
        img/255, f'{img_name_parts[0]}_contours.{img_name_parts[1]}', True)
    if not use_adaptive_threshold:
      return find_circle(
          img, img_name, min_area, color, use_adaptive_threshold=True)
    raise AssertionError('More than 1 circle detected. '
                         'Background of scene may be too complex.')

  return circle


def append_circle_center_to_img(circle, img, img_name):
  """Append circle center and image center to image and save image.

  Draws line from circle center to image center and then labels end-points.
  Adjusts text positioning depending on circle center wrt image center.
  Moves text position left/right half of up/down movement for visual aesthetics.

  Args:
    circle: dict with circle location vals.
    img: numpy float image array in RGB, with pixel values in [0,255].
    img_name: string with image info of format and size.
  """
  line_width_scaling_factor = 500
  text_move_scaling_factor = 3
  img_size = img.shape
  img_center_x = img_size[1]//2
  img_center_y = img_size[0]//2

  # draw line from circle to image center
  line_width = int(max(1, max(img_size)//line_width_scaling_factor))
  font_size = line_width // 2
  move_text_dist = line_width * text_move_scaling_factor
  cv2.line(img, (circle['x'], circle['y']), (img_center_x, img_center_y),
           CV2_RED, line_width)

  # adjust text location
  move_text_right_circle = -1
  move_text_right_image = 2
  if circle['x'] > img_center_x:
    move_text_right_circle = 2
    move_text_right_image = -1

  move_text_down_circle = -1
  move_text_down_image = 4
  if circle['y'] > img_center_y:
    move_text_down_circle = 4
    move_text_down_image = -1

  # add circles to end points and label
  radius_pt = line_width * 2  # makes a dot 2x line width
  filled_pt = -1  # cv2 value for a filled circle
  # circle center
  cv2.circle(img, (circle['x'], circle['y']), radius_pt, CV2_RED, filled_pt)
  text_circle_x = move_text_dist * move_text_right_circle + circle['x']
  text_circle_y = move_text_dist * move_text_down_circle + circle['y']
  cv2.putText(img, 'circle center', (text_circle_x, text_circle_y),
              cv2.FONT_HERSHEY_SIMPLEX, font_size, CV2_RED, line_width)
  # image center
  cv2.circle(img, (img_center_x, img_center_y), radius_pt, CV2_RED, filled_pt)
  text_imgct_x = move_text_dist * move_text_right_image + img_center_x
  text_imgct_y = move_text_dist * move_text_down_image + img_center_y
  cv2.putText(img, 'image center', (text_imgct_x, text_imgct_y),
              cv2.FONT_HERSHEY_SIMPLEX, font_size, CV2_RED, line_width)
  image_processing_utils.write_image(img/255, img_name, True)  # [0, 1] values


def is_circle_cropped(circle, size):
  """Determine if a circle is cropped by edge of image.

  Args:
    circle: list [x, y, radius] of circle
    size: tuple (x, y) of size of img

  Returns:
    Boolean True if selected circle is cropped
  """

  cropped = False
  circle_x, circle_y = circle[0], circle[1]
  circle_r = circle[2]
  x_min, x_max = circle_x - circle_r, circle_x + circle_r
  y_min, y_max = circle_y - circle_r, circle_y + circle_r
  if x_min < 0 or y_min < 0 or x_max > size[0] or y_max > size[1]:
    cropped = True
  return cropped


def find_white_square(img, min_area):
  """Find the white square in the test image.

  Args:
    img: numpy image array in RGB, with pixel values in [0,255].
    min_area: float of minimum area of circle to find

  Returns:
    square = {'left', 'right', 'top', 'bottom', 'width', 'height'}
  """
  square = {}
  num_squares = 0
  img_size = img.shape

  # convert to gray-scale image
  img_gray = convert_to_gray(img)

  # otsu threshold to binarize the image
  img_bw = binarize_image(img_gray)

  # find contours
  contours = find_all_contours(img_bw)

  # Check each contour and find the square bigger than min_area
  logging.debug('Initial number of contours: %d', len(contours))
  min_area = img_size[0]*img_size[1]*min_area
  logging.debug('min_area: %.3f', min_area)
  for contour in contours:
    area = cv2.contourArea(contour)
    num_pts = len(contour)
    if (area > min_area and num_pts >= 4):
      shape = component_shape(contour)
      squarish = (shape['width'] * shape['height']) / area
      aspect_ratio = shape['width'] / shape['height']
      logging.debug('Potential square found. squarish: %.3f, ar: %.3f, pts: %d',
                    squarish, aspect_ratio, num_pts)
      if (math.isclose(1.0, squarish, abs_tol=SQUARISH_RTOL) and
          math.isclose(1.0, aspect_ratio, abs_tol=SQUARISH_AR_RTOL)):
        # Populate square dictionary
        angle = cv2.minAreaRect(contour)[-1]
        if angle < -45:
          angle += 90
        square['angle'] = angle
        square['left'] = shape['left'] - SQUARE_CROP_MARGIN
        square['right'] = shape['right'] + SQUARE_CROP_MARGIN
        square['top'] = shape['top'] - SQUARE_CROP_MARGIN
        square['bottom'] = shape['bottom'] + SQUARE_CROP_MARGIN
        square['w'] = shape['width'] + 2*SQUARE_CROP_MARGIN
        square['h'] = shape['height'] + 2*SQUARE_CROP_MARGIN
        num_squares += 1

  if num_squares == 0:
    raise AssertionError('No white square detected. '
                         'Please take pictures according to instructions.')
  if num_squares > 1:
    raise AssertionError('More than 1 white square detected. '
                         'Background of scene may be too complex.')
  return square


def get_angle(input_img):
  """Computes anglular inclination of chessboard in input_img.

  Args:
    input_img (2D numpy.ndarray): Grayscale image stored as a 2D numpy array.
  Returns:
    Median angle of squares in degrees identified in the image.

  Angle estimation algorithm description:
    Input: 2D grayscale image of chessboard.
    Output: Angle of rotation of chessboard perpendicular to
            chessboard. Assumes chessboard and camera are parallel to
            each other.

    1) Use adaptive threshold to make image binary
    2) Find countours
    3) Filter out small contours
    4) Filter out all non-square contours
    5) Compute most common square shape.
        The assumption here is that the most common square instances are the
        chessboard squares. We've shown that with our current tuning, we can
        robustly identify the squares on the sensor fusion chessboard.
    6) Return median angle of most common square shape.

  USAGE NOTE: This function has been tuned to work for the chessboard used in
  the sensor_fusion tests. See images in test_images/rotated_chessboard/ for
  sample captures. If this function is used with other chessboards, it may not
  work as expected.
  """
  # Tuning parameters
  square_area_min = (float)(input_img.shape[1] * SQUARE_AREA_MIN_REL)

  # Creates copy of image to avoid modifying original.
  img = numpy.array(input_img, copy=True)

  # Scale pixel values from 0-1 to 0-255
  img *= 255
  img = img.astype(numpy.uint8)
  img_thresh = cv2.adaptiveThreshold(
      img, 255, cv2.ADAPTIVE_THRESH_MEAN_C, cv2.THRESH_BINARY, 201, 2)

  # Find all contours.
  contours = find_all_contours(img_thresh)

  # Filter contours to squares only.
  square_contours = []
  for contour in contours:
    rect = cv2.minAreaRect(contour)
    _, (width, height), angle = rect

    # Skip non-squares
    if not math.isclose(width, height, rel_tol=SQUARE_TOL):
      continue

    # Remove very small contours: usually just tiny dots due to noise.
    area = cv2.contourArea(contour)
    if area < square_area_min:
      continue

    square_contours.append(contour)

  areas = []
  for contour in square_contours:
    area = cv2.contourArea(contour)
    areas.append(area)

  median_area = numpy.median(areas)

  filtered_squares = []
  filtered_angles = []
  for square in square_contours:
    area = cv2.contourArea(square)
    if not math.isclose(area, median_area, rel_tol=SQUARE_TOL):
      continue

    filtered_squares.append(square)
    _, (width, height), angle = cv2.minAreaRect(square)
    filtered_angles.append(angle)

  if len(filtered_angles) < ANGLE_NUM_MIN:
    logging.debug(
        'A frame had too few angles to be processed. '
        'Num of angles: %d, MIN: %d', len(filtered_angles), ANGLE_NUM_MIN)
    return None

  return numpy.median(filtered_angles)


def correct_faces_for_crop(faces, img, crop):
  """Correct face rectangles for sensor crop.

  Args:
    faces: list of dicts with face information
    img: np image array
    crop: dict of crop region size with 'top, right, left, bottom' as keys
  Returns:
    list of face locations (left, right, top, bottom) corrected
  """
  faces_corrected = []
  cw, ch = crop['right'] - crop['left'], crop['bottom'] - crop['top']
  logging.debug('crop region: %s', str(crop))
  w = img.shape[1]
  h = img.shape[0]
  for rect in [face['bounds'] for face in faces]:
    logging.debug('rect: %s', str(rect))
    left = int(round((rect['left'] - crop['left']) * w / cw))
    right = int(round((rect['right'] - crop['left']) * w / cw))
    top = int(round((rect['top'] - crop['top']) * h / ch))
    bottom = int(round((rect['bottom'] - crop['top']) * h / ch))
    faces_corrected.append([left, right, top, bottom])
  logging.debug('faces_corrected: %s', str(faces_corrected))
  return faces_corrected


def eliminate_duplicate_centers(coordinates_list):
  """Checks center coordinates of OpenCV's face rectangles.

  Method makes sure that the list of face rectangles' centers do not
  contain duplicates from the same face

  Args:
    coordinates_list: list; coordinates of face rectangles' centers
  Returns:
    non_duplicate_list: list; coordinates of face rectangles' centers
    without duplicates on the same face
  """
  output = set()

  for _, xy1 in enumerate(coordinates_list):
    for _, xy2 in enumerate(coordinates_list):
      if scipy.spatial.distance.euclidean(xy1, xy2) < FACE_MIN_CENTER_DELTA:
        continue
      if xy1 not in output:
        output.add(xy1)
      else:
        output.add(xy2)
  return list(output)


def match_face_locations(faces_cropped, faces_opencv, img, img_name):
  """Assert face locations between two methods.

  Method determines if center of opencv face boxes is within face detection
  face boxes. Using math.hypot to measure the distance between the centers,
  as math.dist is not available for python versions before 3.8.

  Args:
    faces_cropped: list of lists with (l, r, t, b) for each face.
    faces_opencv: list of lists with (x, y, w, h) for each face.
    img: numpy [0, 1] image array
    img_name: text string with path to image file
  """
  # turn faces_opencv into list of center locations
  faces_opencv_center = [(x+w//2, y+h//2) for (x, y, w, h) in faces_opencv]
  cropped_faces_centers = [
      ((l+r)//2, (t+b)//2) for (l, r, t, b) in faces_cropped]
  faces_opencv_center.sort(key=lambda t: [t[1], t[0]])
  cropped_faces_centers.sort(key=lambda t: [t[1], t[0]])
  logging.debug('cropped face centers: %s', str(cropped_faces_centers))
  logging.debug('opencv face center: %s', str(faces_opencv_center))
  faces_opencv_centers = []
  num_centers_aligned = 0

  # eliminate duplicate openCV face rectangles' centers the same face
  faces_opencv_centers = eliminate_duplicate_centers(faces_opencv_center)
  logging.debug('opencv face centers: %s', str(faces_opencv_centers))

  for (x, y) in faces_opencv_centers:
    for (x1, y1) in cropped_faces_centers:
      centers_dist = math.hypot(x-x1, y-y1)
      if centers_dist < FACE_CENTER_MIN_LOGGING_DIST:
        logging.debug('centers_dist: %.3f', centers_dist)
      if (abs(x-x1) < FACE_CENTER_MATCH_TOL_X and
          abs(y-y1) < FACE_CENTER_MATCH_TOL_Y):
        num_centers_aligned += 1

  # If test failed, save image with green AND OpenCV red rectangles
  image_processing_utils.write_image(img, img_name)
  if num_centers_aligned < FACES_ALIGNED_MIN_NUM:
    for (x, y, w, h) in faces_opencv:
      cv2.rectangle(img, (x, y), (x+w, y+h), tuple(numpy.array(CV2_RED)/255), 2)
      image_processing_utils.write_image(img, img_name)
      logging.debug('centered: %s', str(num_centers_aligned))
    raise AssertionError(f'Face rectangles in wrong location(s)!. '
                         f'Found {num_centers_aligned} rectangles near cropped '
                         f'face centers, expected {FACES_ALIGNED_MIN_NUM}')


def draw_green_boxes_around_faces(img, faces_cropped, img_name):
  """Correct face rectangles for sensor crop.

  Args:
    img: numpy [0, 1] image array
    faces_cropped: list of lists with (l, r, t, b) for each face
    img_name: text string with path to image file
  Returns:
    image with green rectangles
  """
  # draw boxes around faces in green and save image
  for (l, r, t, b) in faces_cropped:
    cv2.rectangle(img, (l, t), (r, b), CV2_GREEN, 2)
  image_processing_utils.write_image(img, img_name)


def find_aruco_markers(input_img, output_img_path):
  """Detects ArUco markers in the input_img.

  Finds ArUco markers in the input_img and draws the contours
  around them.
  Args:
    input_img: input img in numpy array with ArUco markers
      to be detected
    output_img_path: path of the image to be saved with contours
      around the markers detected
  Returns:
    corners: list of detected corners
    ids: list of int ids for each ArUco markers in the input_img
  """
  parameters = cv2.aruco.DetectorParameters_create()
  # ArUco markers used are 4x4
  aruco_dict = cv2.aruco.getPredefinedDictionary(cv2.aruco.DICT_4X4_100)
  corners, ids, _ = cv2.aruco.detectMarkers(
      input_img, aruco_dict, parameters=parameters)
  if ids is None:
    e_msg = 'ArUco markers not detected.'
    raise AssertionError(e_msg)
  logging.debug('Number of ArUco markers detected: %d', len(ids))
  logging.debug('IDs of the ArUco markers detected: %s', ids)
  logging.debug('Corners of the ArUco markers detected: %s', corners)
  cv2.aruco.drawDetectedMarkers(input_img, corners, ids)
  image_processing_utils.write_image(input_img/255, output_img_path)
  return corners, ids


def get_patch_from_aruco_markers(
    input_img, aruco_marker_corners, aruco_marker_ids):
  """Returns the rectangle patch from the aruco marker corners.

  Note: Refer to image used in scene7 for ArUco markers location.

  Args:
    input_img: input img in numpy array with ArUco markers
      to be detected
    aruco_marker_corners: array of aruco marker corner coordinates detected by
      opencv_processing_utils.find_aruco_markers
    aruco_marker_ids: array of ids of aruco markers detected by
      opencv_processing_utils.find_aruco_markers
  Returns:
    Numpy float image array of the rectangle patch
  """
  outer_rect_coordinates = {}
  for corner, marker_id in zip(aruco_marker_corners, aruco_marker_ids):
    corner = corner.reshape(4, 2)  # opencv returns 3D array
    index = marker_id[0]
    corner = numpy.roll(corner, 4)
    outer_rect_coordinates[index] = tuple(corner[index])

  # Ensure that the image is not rotated
  assert((outer_rect_coordinates[1][1] - outer_rect_coordinates[0][1])
         <= IMAGE_ROTATION_THRESHOLD)
  assert((outer_rect_coordinates[3][1] - outer_rect_coordinates[2][1])
         <= IMAGE_ROTATION_THRESHOLD)
  top_left = tuple(map(int, outer_rect_coordinates[0]))
  bottom_right = tuple(map(int, outer_rect_coordinates[2]))
  cv2.rectangle(input_img, top_left, bottom_right,
                tuple(numpy.array(CV2_RED)/255), CV2_LINE_THICKNESS)
  return input_img[top_left[1]:bottom_right[1],
                   top_left[0]:bottom_right[0]].copy()
