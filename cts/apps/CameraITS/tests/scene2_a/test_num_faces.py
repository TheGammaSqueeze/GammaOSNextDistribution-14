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
"""Verifies 3 faces with different skin tones are detected."""


import logging
import os.path

import cv2
from mobly import test_runner

import its_base_test
import camera_properties_utils
import capture_request_utils
import image_processing_utils
import its_session_utils
import opencv_processing_utils

_CV2_FACE_SCALE_FACTOR = 1.05  # 5% step for resizing image to find face
_CV2_FACE_MIN_NEIGHBORS = 4  # recommended 3-6: higher for less faces
_CV2_GREEN = (0, 1, 0)
_FD_MODE_OFF, _FD_MODE_SIMPLE, _FD_MODE_FULL = 0, 1, 2
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_NUM_FACES = 3
_NUM_TEST_FRAMES = 20
_TEST_REQUIRED_MPC = 34
_W, _H = 640, 480


def check_face_bounding_box(rect, aw, ah, index):
  """Checks face bounding box is within the active array area.

  Args:
    rect: dict; with face bounding box information
    aw: int; active array width
    ah: int; active array height
    index: int to designate face number
  """
  logging.debug('Checking bounding box in face %d: %s', index, str(rect))
  if (rect['top'] >= rect['bottom'] or
      rect['left'] >= rect['right']):
    raise AssertionError('Face coordinates incorrect! '
                         f" t: {rect['top']}, b: {rect['bottom']}, "
                         f" l: {rect['left']}, r: {rect['right']}")
  if (not 0 <= rect['top'] <= ah or
      not 0 <= rect['bottom'] <= ah):
    raise AssertionError('Face top/bottom outside of image height! '
                         f"t: {rect['top']}, b: {rect['bottom']}, "
                         f"h: {ah}")
  if (not 0 <= rect['left'] <= aw or
      not 0 <= rect['right'] <= aw):
    raise AssertionError('Face left/right outside of image width! '
                         f"l: {rect['left']}, r: {rect['right']}, "
                         f" w: {aw}")


def check_face_landmarks(face, fd_mode, index):
  """Checks face landmarks fall within face bounding box.

  Face ID should be -1 for SIMPLE and unique for FULL
  Args:
    face: dict from face detection algorithm
    fd_mode: int of face detection mode
    index: int to designate face number
  """
  logging.debug('Checking landmarks in face %d: %s', index, str(face))
  if fd_mode == _FD_MODE_SIMPLE:
    if 'leftEye' in face or 'rightEye' in face:
      raise AssertionError('Eyes not supported in FD_MODE_SIMPLE.')
    if 'mouth' in face:
      raise AssertionError('Mouth not supported in FD_MODE_SIMPLE.')
    if face['id'] != -1:
      raise AssertionError('face_id should be -1 in FD_MODE_SIMPLE.')
  elif fd_mode == _FD_MODE_FULL:
    l, r = face['bounds']['left'], face['bounds']['right']
    t, b = face['bounds']['top'], face['bounds']['bottom']
    l_eye_x, l_eye_y = face['leftEye']['x'], face['leftEye']['y']
    r_eye_x, r_eye_y = face['rightEye']['x'], face['rightEye']['y']
    mouth_x, mouth_y = face['mouth']['x'], face['mouth']['y']
    if not l <= l_eye_x <= r:
      raise AssertionError(f'Face l: {l}, r: {r}, left eye x: {l_eye_x}')
    if not t <= l_eye_y <= b:
      raise AssertionError(f'Face t: {t}, b: {b}, left eye y: {l_eye_y}')
    if not l <= r_eye_x <= r:
      raise AssertionError(f'Face l: {l}, r: {r}, right eye x: {r_eye_x}')
    if not t <= r_eye_y <= b:
      raise AssertionError(f'Face t: {t}, b: {b}, right eye y: {r_eye_y}')
    if not l <= mouth_x <= r:
      raise AssertionError(f'Face l: {l}, r: {r}, mouth x: {mouth_x}')
    if not t <= mouth_y <= b:
      raise AssertionError(f'Face t: {t}, b: {b}, mouth y: {mouth_y}')
  else:
    raise AssertionError(f'Unknown face detection mode: {fd_mode}.')


class NumFacesTest(its_base_test.ItsBaseTest):
  """Test face detection with different skin tones.
  """

  def test_num_faces(self):
    """Test face detection."""
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)

      # Load chart for scene
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet, self.chart_distance,
          log_path=self.log_path)

      # Check media performance class
      should_run = camera_properties_utils.face_detect(props)
      media_performance_class = its_session_utils.get_media_performance_class(
          self.dut.serial)
      if media_performance_class >= _TEST_REQUIRED_MPC and not should_run:
        its_session_utils.raise_mpc_assertion_error(
            _TEST_REQUIRED_MPC, _NAME, media_performance_class)

      # Check skip conditions
      camera_properties_utils.skip_unless(should_run)
      mono_camera = camera_properties_utils.mono_camera(props)
      fd_modes = props['android.statistics.info.availableFaceDetectModes']
      a = props['android.sensor.info.activeArraySize']
      aw, ah = a['right'] - a['left'], a['bottom'] - a['top']
      logging.debug('active array size: %s', str(a))
      file_name_stem = os.path.join(self.log_path, _NAME)

      cam.do_3a(mono_camera=mono_camera)

      for fd_mode in fd_modes:
        logging.debug('face detection mode: %d', fd_mode)
        if not _FD_MODE_OFF <= fd_mode <= _FD_MODE_FULL:
          raise AssertionError(f'FD mode {fd_mode} not in MODES! '
                               f'OFF: {_FD_MODE_OFF}, FULL: {_FD_MODE_FULL}')
        req = capture_request_utils.auto_capture_request()
        req['android.statistics.faceDetectMode'] = fd_mode
        fmt = {'format': 'yuv', 'width': _W, 'height': _H}
        caps = cam.do_capture([req]*_NUM_TEST_FRAMES, fmt)
        for i, cap in enumerate(caps):
          fd_mode_cap = cap['metadata']['android.statistics.faceDetectMode']
          if fd_mode_cap != fd_mode:
            raise AssertionError(f'metadata {fd_mode_cap} != req {fd_mode}')

          faces = cap['metadata']['android.statistics.faces']
          # 0 faces should be returned for OFF mode
          if fd_mode == _FD_MODE_OFF:
            if faces:
              raise AssertionError(f'Error: faces detected in OFF: {faces}')
            continue
          # Face detection could take several frames to warm up,
          # but should detect the correct number of faces in last frame
          if i == _NUM_TEST_FRAMES - 1:
            img = image_processing_utils.convert_capture_to_rgb_image(
                cap, props=props)
            fnd_faces = len(faces)
            logging.debug('Found %d face(s), expected %d.',
                          fnd_faces, _NUM_FACES)

            # draw boxes around faces in green
            crop_region = cap['metadata']['android.scaler.cropRegion']
            faces_cropped = opencv_processing_utils.correct_faces_for_crop(
                faces, img, crop_region)
            for (l, r, t, b) in faces_cropped:
              cv2.rectangle(img, (l, t), (r, b), _CV2_GREEN, 2)

            # Save image with green rectangles
            img_name = f'{file_name_stem}_fd_mode_{fd_mode}.jpg'
            image_processing_utils.write_image(img, img_name)
            if fnd_faces != _NUM_FACES:
              raise AssertionError('Wrong num of faces found! Found: '
                                   f'{fnd_faces}, expected: {_NUM_FACES}')
            # Reasonable scores for faces
            face_scores = [face['score'] for face in faces]
            for score in face_scores:
              if not 1 <= score <= 100:
                raise AssertionError(f'score not between [1:100]! {score}')

            # Face bounds should be within active array
            face_rectangles = [face['bounds'] for face in faces]
            for j, rect in enumerate(face_rectangles):
              check_face_bounding_box(rect, aw, ah, j)

            # Face landmarks (if provided) are within face bounding box
            if (its_session_utils.get_first_api_level(self.dut.serial) >=
                its_session_utils.ANDROID14_API_LEVEL):
              for k, face in enumerate(faces):
                check_face_landmarks(face, fd_mode, k)

            # Match location of opencv and face detection mode faces
            if self.scene == 'scene2_d':
              logging.debug('Match face centers between opencv & faces')
              faces_opencv = opencv_processing_utils.find_opencv_faces(
                  img, _CV2_FACE_SCALE_FACTOR, _CV2_FACE_MIN_NEIGHBORS)
              if fd_mode:  # non-zero value for ON
                opencv_processing_utils.match_face_locations(
                    faces_cropped, faces_opencv, img, img_name)

          if not faces:
            continue
          logging.debug('Frame %d face metadata:', i)
          logging.debug(' Faces: %s', str(faces))


if __name__ == '__main__':
  test_runner.main()
