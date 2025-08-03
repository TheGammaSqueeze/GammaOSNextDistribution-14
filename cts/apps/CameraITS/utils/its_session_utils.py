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
"""Utility functions to form an ItsSession and perform various camera actions.
"""


import collections
import json
import logging
import math
import os
import socket
import subprocess
import sys
import time
import unicodedata

from mobly.controllers.android_device_lib import adb
import numpy

import camera_properties_utils
import capture_request_utils
import error_util
import image_processing_utils
import its_device_utils
import opencv_processing_utils
import ui_interaction_utils

ANDROID13_API_LEVEL = 33
ANDROID14_API_LEVEL = 34
ANDROID15_API_LEVEL = 35
CHART_DISTANCE_NO_SCALING = 0
IMAGE_FORMAT_JPEG = 256
IMAGE_FORMAT_YUV_420_888 = 35
JCA_CAPTURE_PATH_TAG = 'JCA_CAPTURE_PATH'
JCA_CAPTURE_STATUS_TAG = 'JCA_CAPTURE_STATUS'
LOAD_SCENE_DELAY_SEC = 3
SCALING_TO_FILE_ATOL = 0.01
SINGLE_CAPTURE_NCAP = 1
SUB_CAMERA_SEPARATOR = '.'
DEFAULT_TABLET_BRIGHTNESS = 192  # 8-bit tablet 75% brightness
ELEVEN_BIT_TABLET_BRIGHTNESS = 1024  # 50% brightness for Xiaomi tablets
ELEVEN_BIT_TABLET_NAMES = ('nabu',)
LEGACY_TABLET_BRIGHTNESS = 96
LEGACY_TABLET_NAME = 'dragon'
TABLET_REQUIREMENTS_URL = 'https://source.android.com/docs/compatibility/cts/camera-its-box#tablet-requirements'
BRIGHTNESS_ERROR_MSG = ('Tablet brightness not set as per '
                        f'{TABLET_REQUIREMENTS_URL} in the config file')
VIDEO_SCENES = ('scene_video',)
NOT_YET_MANDATED_MESSAGE = 'Not yet mandated test'
RESULT_OK_STATUS = '-1'

_VALIDATE_LIGHTING_PATCH_H = 0.05
_VALIDATE_LIGHTING_PATCH_W = 0.05
_VALIDATE_LIGHTING_REGIONS = {
    'top-left': (0, 0),
    'top-right': (0, 1-_VALIDATE_LIGHTING_PATCH_H),
    'bottom-left': (1-_VALIDATE_LIGHTING_PATCH_W, 0),
    'bottom-right': (1-_VALIDATE_LIGHTING_PATCH_W,
                     1-_VALIDATE_LIGHTING_PATCH_H),
}
_VALIDATE_LIGHTING_THRESH = 0.05  # Determined empirically from scene[1:6] tests
_VALIDATE_LIGHTING_THRESH_DARK = 0.15  # Determined empirically for night test
_CMD_NAME_STR = 'cmdName'
_OBJ_VALUE_STR = 'objValue'
_STR_VALUE_STR = 'strValue'
_TAG_STR = 'tag'
_CAMERA_ID_STR = 'cameraId'
_USE_CASE_CROPPED_RAW = 6
_EXTRA_TIMEOUT_FACTOR = 10


def validate_tablet_brightness(tablet_name, brightness):
  """Ensures tablet brightness is set according to documentation.

  https://source.android.com/docs/compatibility/cts/camera-its-box#tablet-requirements
  Args:
    tablet_name: tablet product name specified by `ro.build.product`.
    brightness: brightness specified by config file.
  """
  name_to_brightness = {
      LEGACY_TABLET_NAME: LEGACY_TABLET_BRIGHTNESS,
  }
  for name in ELEVEN_BIT_TABLET_NAMES:
    name_to_brightness[name] = ELEVEN_BIT_TABLET_BRIGHTNESS
  if tablet_name in name_to_brightness:
    if brightness != name_to_brightness[tablet_name]:
      raise AssertionError(BRIGHTNESS_ERROR_MSG)
  else:
    if brightness != DEFAULT_TABLET_BRIGHTNESS:
      raise AssertionError(BRIGHTNESS_ERROR_MSG)


def check_apk_installed(device_id, package_name):
  """Verifies that an APK is installed on a given device.

  Args:
    device_id: str; ID of the device.
    package_name: str; name of the package that should be installed.
  """
  verify_cts_cmd = (
      f'adb -s {device_id} shell pm list packages | '
      f'grep {package_name}'
  )
  bytes_output = subprocess.check_output(
      verify_cts_cmd, stderr=subprocess.STDOUT, shell=True
  )
  output = str(bytes_output.decode('utf-8')).strip()
  if package_name not in output:
    raise AssertionError(
        f'{package_name} not installed on device {device_id}!'
    )


class ItsSession(object):
  """Controls a device over adb to run ITS scripts.

    The script importing this module (on the host machine) prepares JSON
    objects encoding CaptureRequests, specifying sets of parameters to use
    when capturing an image using the Camera2 APIs. This class encapsulates
    sending the requests to the device, monitoring the device's progress, and
    copying the resultant captures back to the host machine when done. TCP
    forwarded over adb is the transport mechanism used.

    The device must have CtsVerifier.apk installed.

    Attributes:
        sock: The open socket.
  """

  # Open a connection to localhost:<host_port>, forwarded to port 6000 on the
  # device. <host_port> is determined at run-time to support multiple
  # connected devices.
  IPADDR = '127.0.0.1'
  REMOTE_PORT = 6000
  BUFFER_SIZE = 4096

  # LOCK_PORT is used as a mutex lock to protect the list of forwarded ports
  # among all processes. The script assumes LOCK_PORT is available and will
  # try to use ports between CLIENT_PORT_START and
  # CLIENT_PORT_START+MAX_NUM_PORTS-1 on host for ITS sessions.
  CLIENT_PORT_START = 6000
  MAX_NUM_PORTS = 100
  LOCK_PORT = CLIENT_PORT_START + MAX_NUM_PORTS

  # Seconds timeout on each socket operation.
  SOCK_TIMEOUT = 20.0
  # Seconds timeout on performance measurement socket operation
  SOCK_TIMEOUT_FOR_PERF_MEASURE = 40.0
  # Seconds timeout on preview recording socket operation.
  SOCK_TIMEOUT_PREVIEW = 30.0  # test_imu_drift is 30s

  # Additional timeout in seconds when ITS service is doing more complicated
  # operations, for example: issuing warmup requests before actual capture.
  EXTRA_SOCK_TIMEOUT = 5.0

  PACKAGE = 'com.android.cts.verifier.camera.its'
  INTENT_START = 'com.android.cts.verifier.camera.its.START'

  # This string must be in sync with ItsService. Updated when interface
  # between script and ItsService is changed.
  ITS_SERVICE_VERSION = '1.0'

  SEC_TO_NSEC = 1000*1000*1000.0
  adb = 'adb -d'

  # Predefine camera props. Save props extracted from the function,
  # "get_camera_properties".
  props = None

  IMAGE_FORMAT_LIST_1 = [
      'jpegImage', 'rawImage', 'raw10Image', 'raw12Image', 'rawStatsImage',
      'dngImage', 'y8Image', 'jpeg_rImage',
      'rawQuadBayerImage', 'rawQuadBayerStatsImage',
      'raw10StatsImage', 'raw10QuadBayerStatsImage', 'raw10QuadBayerImage'
  ]

  IMAGE_FORMAT_LIST_2 = [
      'jpegImage', 'rawImage', 'raw10Image', 'raw12Image', 'rawStatsImage',
      'yuvImage', 'jpeg_rImage',
      'rawQuadBayerImage', 'rawQuadBayerStatsImage',
      'raw10StatsImage', 'raw10QuadBayerStatsImage', 'raw10QuadBayerImage'
  ]

  CAP_JPEG = {'format': 'jpeg'}
  CAP_RAW = {'format': 'raw'}
  CAP_CROPPED_RAW = {'format': 'raw', 'useCase': _USE_CASE_CROPPED_RAW}
  CAP_YUV = {'format': 'yuv'}
  CAP_RAW_YUV = [{'format': 'raw'}, {'format': 'yuv'}]

  def __init_socket_port(self):
    """Initialize the socket port for the host to forward requests to the device.

    This method assumes localhost's LOCK_PORT is available and will try to
    use ports between CLIENT_PORT_START and CLIENT_PORT_START+MAX_NUM_PORTS-1
    """
    num_retries = 100
    retry_wait_time_sec = 0.05

    # Bind a socket to use as mutex lock
    socket_lock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    for i in range(num_retries):
      try:
        socket_lock.bind((ItsSession.IPADDR, ItsSession.LOCK_PORT))
        break
      except (socket.error, socket.timeout) as socket_issue:
        if i == num_retries - 1:
          raise error_util.CameraItsError(
              self._device_id, 'socket lock returns error') from socket_issue
        else:
          time.sleep(retry_wait_time_sec)

    # Check if a port is already assigned to the device.
    command = 'adb forward --list'
    proc = subprocess.Popen(command.split(), stdout=subprocess.PIPE)
    # pylint: disable=unused-variable
    output, error = proc.communicate()
    port = None
    used_ports = []
    for line  in output.decode('utf-8').split(os.linesep):
      # each line should be formatted as:
      # "<device_id> tcp:<host_port> tcp:<remote_port>"
      forward_info = line.split()
      if len(forward_info) >= 3 and len(
          forward_info[1]) > 4 and forward_info[1][:4] == 'tcp:' and len(
              forward_info[2]) > 4 and forward_info[2][:4] == 'tcp:':
        local_p = int(forward_info[1][4:])
        remote_p = int(forward_info[2][4:])
        if forward_info[
            0] == self._device_id and remote_p == ItsSession.REMOTE_PORT:
          port = local_p
          break
        else:
          used_ports.append(local_p)

      # Find the first available port if no port is assigned to the device.
    if port is None:
      for p in range(ItsSession.CLIENT_PORT_START,
                     ItsSession.CLIENT_PORT_START + ItsSession.MAX_NUM_PORTS):
        if self.check_port_availability(p, used_ports):
          port = p
          break

    if port is None:
      raise error_util.CameraItsError(self._device_id,
                                      ' cannot find an available ' + 'port')

    # Release the socket as mutex unlock
    socket_lock.close()

    # Connect to the socket
    self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    self.sock.connect((self.IPADDR, port))
    self.sock.settimeout(self.SOCK_TIMEOUT)

  def check_port_availability(self, check_port, used_ports):
    """Check if the port is available or not.

    Args:
      check_port: Port to check for availability
      used_ports: List of used ports

    Returns:
     True if the given port is available and can be assigned to the device.
    """
    if check_port not in used_ports:
      # Try to run "adb forward" with the port
      command = ('%s forward tcp:%d tcp:%d' %
                 (self.adb, check_port, self.REMOTE_PORT))
      proc = subprocess.Popen(
          command.split(), stdout=subprocess.PIPE, stderr=subprocess.PIPE)
      error = proc.communicate()[1]

      # Check if there is no error
      if error is None or error.find('error'.encode()) < 0:
        return True
      else:
        return False

  def __wait_for_service(self):
    """Wait for ItsService to be ready and reboot the device if needed.

    This also includes the optional reboot handling: if the user
    provides a "reboot" or "reboot=N" arg, then reboot the device,
    waiting for N seconds (default 30) before returning.
    """

    for s in sys.argv[1:]:
      if s[:6] == 'reboot':
        duration = 30
        if len(s) > 7 and s[6] == '=':
          duration = int(s[7:])
        logging.debug('Rebooting device')
        its_device_utils.run(f'{self.adb} reboot')
        its_device_utils.run(f'{self.adb} wait-for-device')
        time.sleep(duration)
        logging.debug('Reboot complete')

    # Flush logcat so following code won't be misled by previous
    # 'ItsService ready' log.
    its_device_utils.run(f'{self.adb} logcat -c')
    time.sleep(1)

    its_device_utils.run(
        f'{self.adb} shell am force-stop --user 0 {self.PACKAGE}')
    its_device_utils.run(
        f'{self.adb} shell am start-foreground-service --user 0 '
        f'-t text/plain -a {self.INTENT_START}'
    )

    # Wait until the socket is ready to accept a connection.
    proc = subprocess.Popen(
        self.adb.split() + ['logcat'], stdout=subprocess.PIPE)
    logcat = proc.stdout
    while True:
      line = logcat.readline().strip()
      if line.find(b'ItsService ready') >= 0:
        break
    proc.kill()
    proc.communicate()

  def __init__(self, device_id=None, camera_id=None, hidden_physical_id=None,
               override_to_portrait=None):
    self._camera_id = camera_id
    self._device_id = device_id
    self._hidden_physical_id = hidden_physical_id
    self._override_to_portrait = override_to_portrait

    # Initialize device id and adb command.
    self.adb = 'adb -s ' + self._device_id
    self.__wait_for_service()
    self.__init_socket_port()

  def __enter__(self):
    self.__close_camera()
    self.__open_camera()
    return self

  def __exit__(self, exec_type, exec_value, exec_traceback):
    if hasattr(self, 'sock') and self.sock:
      self.__close_camera()
      self.sock.close()
    return False

  def override_with_hidden_physical_camera_props(self, props):
    """Check that it is a valid sub-camera backing the logical camera.

    If current session is for a hidden physical camera, check that it is a valid
    sub-camera backing the logical camera, override self.props, and return the
    characteristics of sub-camera. Otherwise, return "props" directly.

    Args:
     props: Camera properties object.

    Returns:
     The properties of the hidden physical camera if possible.
    """
    if self._hidden_physical_id:
      if not camera_properties_utils.logical_multi_camera(props):
        raise AssertionError(f'{self._camera_id} is not a logical multi-camera')
      physical_ids = camera_properties_utils.logical_multi_camera_physical_ids(
          props)
      if self._hidden_physical_id not in physical_ids:
        raise AssertionError(f'{self._hidden_physical_id} is not a hidden '
                             f'sub-camera of {self._camera_id}')
      props = self.get_camera_properties_by_id(self._hidden_physical_id)
      self.props = props
    return props

  def get_camera_properties(self):
    """Get the camera properties object for the device.

    Returns:
     The Python dictionary object for the CameraProperties object.
    """
    cmd = {}
    cmd[_CMD_NAME_STR] = 'getCameraProperties'
    self.sock.send(json.dumps(cmd).encode() + '\n'.encode())
    data, _ = self.__read_response_from_socket()
    if data[_TAG_STR] != 'cameraProperties':
      raise error_util.CameraItsError('Invalid command response')
    self.props = data[_OBJ_VALUE_STR]['cameraProperties']
    return data[_OBJ_VALUE_STR]['cameraProperties']

  def get_camera_properties_by_id(self, camera_id, override_to_portrait=None):
    """Get the camera properties object for device with camera_id.

    Args:
     camera_id: The ID string of the camera
     override_to_portrait: Optional value for overrideToPortrait

    Returns:
     The Python dictionary object for the CameraProperties object. Empty
     if no such device exists.
    """
    cmd = {}
    cmd[_CMD_NAME_STR] = 'getCameraPropertiesById'
    cmd[_CAMERA_ID_STR] = camera_id
    if override_to_portrait is not None:
      cmd['overrideToPortrait'] = override_to_portrait
    self.sock.send(json.dumps(cmd).encode() + '\n'.encode())
    data, _ = self.__read_response_from_socket()
    if data[_TAG_STR] != 'cameraProperties':
      raise error_util.CameraItsError('Invalid command response')
    return data[_OBJ_VALUE_STR]['cameraProperties']

  def __read_response_from_socket(self):
    """Reads a line (newline-terminated) string serialization of JSON object.

    Returns:
     Deserialized json obj.
    """
    chars = []
    while not chars or chars[-1] != '\n':
      ch = self.sock.recv(1).decode('utf-8')
      if not ch:
        # Socket was probably closed; otherwise don't get empty strings
        raise error_util.CameraItsError('Problem with socket on device side')
      chars.append(ch)
    line = ''.join(chars)
    jobj = json.loads(line)
    # Optionally read a binary buffer of a fixed size.
    buf = None
    if 'bufValueSize' in jobj:
      n = jobj['bufValueSize']
      buf = bytearray(n)
      view = memoryview(buf)
      while n > 0:
        nbytes = self.sock.recv_into(view, n)
        view = view[nbytes:]
        n -= nbytes
      buf = numpy.frombuffer(buf, dtype=numpy.uint8)
    return jobj, buf

  def __open_camera(self):
    """Get the camera ID to open if it is an argument as a single camera.

    This allows passing camera=# to individual tests at command line
    and camera=#,#,# or an no camera argv with tools/run_all_tests.py.
    In case the camera is a logical multi-camera, to run ITS on the
    hidden physical sub-camera, pass camera=[logical ID]:[physical ID]
    to an individual test at the command line, and same applies to multiple
    camera IDs for tools/run_all_tests.py: camera=#,#:#,#:#,#
    """
    if not self._camera_id:
      self._camera_id = 0
      for s in sys.argv[1:]:
        if s[:7] == 'camera=' and len(s) > 7:
          camera_ids = s[7:].split(',')
          camera_id_combos = parse_camera_ids(camera_ids)
          if len(camera_id_combos) == 1:
            self._camera_id = camera_id_combos[0].id
            self._hidden_physical_id = camera_id_combos[0].sub_id

    logging.debug('Opening camera: %s', self._camera_id)
    cmd = {_CMD_NAME_STR: 'open', _CAMERA_ID_STR: self._camera_id}
    if self._override_to_portrait is not None:
      cmd['overrideToPortrait'] = self._override_to_portrait
    self.sock.send(json.dumps(cmd).encode() + '\n'.encode())
    data, _ = self.__read_response_from_socket()
    if data[_TAG_STR] != 'cameraOpened':
      raise error_util.CameraItsError('Invalid command response')

  def __close_camera(self):
    cmd = {_CMD_NAME_STR: 'close'}
    self.sock.send(json.dumps(cmd).encode() + '\n'.encode())
    data, _ = self.__read_response_from_socket()
    if data[_TAG_STR] != 'cameraClosed':
      raise error_util.CameraItsError('Invalid command response')

  def zoom_ratio_within_range(self, zoom_ratio):
    """Determine if a given zoom ratio is within device zoom range.

    Args:
      zoom_ratio: float; zoom ratio requested
    Returns:
      Boolean: True, if zoom_ratio inside device range. False otherwise.
    """
    zoom_range = self.props['android.control.zoomRatioRange']
    return zoom_ratio >= zoom_range[0] and zoom_ratio <= zoom_range[1]

  def get_sensors(self):
    """Get all sensors on the device.

    Returns:
       A Python dictionary that returns keys and booleans for each sensor.
    """
    cmd = {}
    cmd[_CMD_NAME_STR] = 'checkSensorExistence'
    self.sock.send(json.dumps(cmd).encode() + '\n'.encode())
    data, _ = self.__read_response_from_socket()
    if data[_TAG_STR] != 'sensorExistence':
      raise error_util.CameraItsError('Invalid response for command: %s' %
                                      cmd[_CMD_NAME_STR])
    return data[_OBJ_VALUE_STR]

  def start_sensor_events(self):
    """Start collecting sensor events on the device.

    See get_sensor_events for more info.

    Returns:
       Nothing.
    """
    cmd = {}
    cmd[_CMD_NAME_STR] = 'startSensorEvents'
    self.sock.send(json.dumps(cmd).encode() + '\n'.encode())
    data, _ = self.__read_response_from_socket()
    if data[_TAG_STR] != 'sensorEventsStarted':
      raise error_util.CameraItsError('Invalid response for command: %s' %
                                      cmd[_CMD_NAME_STR])

  def get_sensor_events(self):
    """Get a trace of all sensor events on the device.

        The trace starts when the start_sensor_events function is called. If
        the test runs for a long time after this call, then the device's
        internal memory can fill up. Calling get_sensor_events gets all events
        from the device, and then stops the device from collecting events and
        clears the internal buffer; to start again, the start_sensor_events
        call must be used again.

        Events from the accelerometer, compass, and gyro are returned; each
        has a timestamp and x,y,z values.

        Note that sensor events are only produced if the device isn't in its
        standby mode (i.e.) if the screen is on.

    Returns:
            A Python dictionary with three keys ("accel", "mag", "gyro") each
            of which maps to a list of objects containing "time","x","y","z"
            keys.
    """
    cmd = {}
    cmd[_CMD_NAME_STR] = 'getSensorEvents'
    self.sock.send(json.dumps(cmd).encode() + '\n'.encode())
    timeout = self.SOCK_TIMEOUT + self.EXTRA_SOCK_TIMEOUT
    self.sock.settimeout(timeout)
    data, _ = self.__read_response_from_socket()
    if data[_TAG_STR] != 'sensorEvents':
      raise error_util.CameraItsError('Invalid response for command: %s ' %
                                      cmd[_CMD_NAME_STR])
    self.sock.settimeout(self.SOCK_TIMEOUT)
    return data[_OBJ_VALUE_STR]

  def get_camera_ids(self):
    """Returns the list of all camera_ids.

    Returns:
      List of camera ids on the device.
    """
    cmd = {'cmdName': 'getCameraIds'}
    self.sock.send(json.dumps(cmd).encode() + '\n'.encode())
    timeout = self.SOCK_TIMEOUT + self.EXTRA_SOCK_TIMEOUT
    self.sock.settimeout(timeout)
    data, _ = self.__read_response_from_socket()
    if data['tag'] != 'cameraIds':
      raise error_util.CameraItsError('Invalid command response')
    return data['objValue']

  def get_camera_name(self):
    """Gets the camera name.

    Returns:
      The camera name with camera id and/or hidden physical id.
    """
    if self._hidden_physical_id:
      return f'{self._camera_id}.{self._hidden_physical_id}'
    else:
      return self._camera_id

  def get_unavailable_physical_cameras(self, camera_id):
    """Get the unavailable physical cameras ids.

    Args:
      camera_id: int; device id
    Returns:
      List of all physical camera ids which are unavailable.
    """
    cmd = {_CMD_NAME_STR: 'doGetUnavailablePhysicalCameras',
           _CAMERA_ID_STR: camera_id}
    self.sock.send(json.dumps(cmd).encode() + '\n'.encode())
    timeout = self.SOCK_TIMEOUT + self.EXTRA_SOCK_TIMEOUT
    self.sock.settimeout(timeout)
    data, _ = self.__read_response_from_socket()
    if data[_TAG_STR] != 'unavailablePhysicalCameras':
      raise error_util.CameraItsError('Invalid command response')
    return data[_OBJ_VALUE_STR]

  def is_hlg10_recording_supported(self, profile_id):
    """Query whether the camera device supports HLG10 video recording.

    Args:
      profile_id: int; profile id corresponding to the quality level.
    Returns:
      Boolean: True, if device supports HLG10 video recording, False in
      all other cases.
    """
    cmd = {}
    cmd[_CMD_NAME_STR] = 'isHLG10Supported'
    cmd[_CAMERA_ID_STR] = self._camera_id
    cmd['profileId'] = profile_id
    self.sock.send(json.dumps(cmd).encode() + '\n'.encode())

    data, _ = self.__read_response_from_socket()
    if data[_TAG_STR] != 'hlg10Response':
      raise error_util.CameraItsError('Failed to query HLG10 support')
    return data[_STR_VALUE_STR] == 'true'

  def is_p3_capture_supported(self):
    """Query whether the camera device supports P3 image capture.

    Returns:
      Boolean: True, if device supports P3 image capture, False in
      all other cases.
    """
    cmd = {}
    cmd[_CMD_NAME_STR] = 'isP3Supported'
    cmd[_CAMERA_ID_STR] = self._camera_id
    self.sock.send(json.dumps(cmd).encode() + '\n'.encode())

    data, _ = self.__read_response_from_socket()
    if data[_TAG_STR] != 'p3Response':
      raise error_util.CameraItsError('Failed to query P3 support')
    return data[_STR_VALUE_STR] == 'true'

  def is_landscape_to_portrait_enabled(self):
    """Query whether the device has enabled the landscape to portrait property.

    Returns:
      Boolean: True, if the device has the system property enabled. False
      otherwise.
    """
    cmd = {}
    cmd[_CMD_NAME_STR] = 'isLandscapeToPortraitEnabled'
    self.sock.send(json.dumps(cmd).encode() + '\n'.encode())

    data, _ = self.__read_response_from_socket()
    if data[_TAG_STR] != 'landscapeToPortraitEnabledResponse':
      raise error_util.CameraItsError(
          'Failed to query landscape to portrait system property')
    return data[_STR_VALUE_STR] == 'true'

  def get_supported_video_sizes_capped(self, camera_id):
    """Get the supported video sizes for camera id.

    Args:
      camera_id: int; device id
    Returns:
      Sorted list of supported video sizes.
    """

    cmd = {
        _CMD_NAME_STR: 'doGetSupportedVideoSizesCapped',
        _CAMERA_ID_STR: camera_id,
    }
    self.sock.send(json.dumps(cmd).encode() + '\n'.encode())
    timeout = self.SOCK_TIMEOUT + self.EXTRA_SOCK_TIMEOUT
    self.sock.settimeout(timeout)
    data, _ = self.__read_response_from_socket()
    if data[_TAG_STR] != 'supportedVideoSizes':
      raise error_util.CameraItsError('Invalid command response')
    if not data[_STR_VALUE_STR]:
      raise error_util.CameraItsError('No supported video sizes')
    return data[_STR_VALUE_STR].split(';')

  def do_basic_recording(self, profile_id, quality, duration,
                         video_stabilization_mode=0, hlg10_enabled=False,
                         zoom_ratio=None, ae_target_fps_min=None,
                         ae_target_fps_max=None):
    """Issue a recording request and read back the video recording object.

    The recording will be done with the format specified in quality. These
    quality levels correspond to the profiles listed in CamcorderProfile.
    The duration is the time in seconds for which the video will be recorded.
    The recorded object consists of a path on the device at which the
    recorded video is saved.

    Args:
      profile_id: int; profile id corresponding to the quality level.
      quality: Video recording quality such as High, Low, VGA.
      duration: The time in seconds for which the video will be recorded.
      video_stabilization_mode: Video stabilization mode ON/OFF. Value can be
      0: 'OFF', 1: 'ON', 2: 'PREVIEW'
      hlg10_enabled: boolean: True Enable 10-bit HLG video recording, False
      record using the regular SDR profile
      zoom_ratio: float; zoom ratio. None if default zoom
      ae_target_fps_min: int; CONTROL_AE_TARGET_FPS_RANGE min. Set if not None
      ae_target_fps_max: int; CONTROL_AE_TARGET_FPS_RANGE max. Set if not None
    Returns:
      video_recorded_object: The recorded object returned from ItsService which
      contains path at which the recording is saved on the device, quality of
      the recorded video, video size of the recorded video, video frame rate
      and 'hlg10' if 'hlg10_enabled' is set to True.
      Ex:
      VideoRecordingObject: {
        'tag': 'recordingResponse',
        'objValue': {
          'recordedOutputPath':
            '/storage/emulated/0/Android/data/com.android.cts.verifier'
            '/files/VideoITS/VID_20220324_080414_0_CIF_352x288.mp4',
          'quality': 'CIF',
          'videoFrameRate': 30,
          'videoSize': '352x288'
        }
      }
    """
    cmd = {_CMD_NAME_STR: 'doBasicRecording', _CAMERA_ID_STR: self._camera_id,
           'profileId': profile_id, 'quality': quality,
           'recordingDuration': duration,
           'videoStabilizationMode': video_stabilization_mode,
           'hlg10Enabled': hlg10_enabled}
    if zoom_ratio:
      if self.zoom_ratio_within_range(zoom_ratio):
        cmd['zoomRatio'] = zoom_ratio
      else:
        raise AssertionError(f'Zoom ratio {zoom_ratio} out of range')
    if ae_target_fps_min and ae_target_fps_max:
      cmd['aeTargetFpsMin'] = ae_target_fps_min
      cmd['aeTargetFpsMax'] = ae_target_fps_max
    self.sock.send(json.dumps(cmd).encode() + '\n'.encode())
    timeout = self.SOCK_TIMEOUT + self.EXTRA_SOCK_TIMEOUT
    self.sock.settimeout(timeout)
    data, _ = self.__read_response_from_socket()
    if data[_TAG_STR] != 'recordingResponse':
      raise error_util.CameraItsError(
          f'Invalid response for command: {cmd[_CMD_NAME_STR]}')
    return data[_OBJ_VALUE_STR]

  def _execute_preview_recording(self, cmd):
    """Send preview recording command over socket and retrieve output object.

    Args:
      cmd: dict; Mapping from command key to corresponding value
    Returns:
      video_recorded_object: The recorded object returned from ItsService which
      contains path at which the recording is saved on the device, quality of
      the recorded video which is always set to "preview", video size of the
      recorded video, video frame rate.
      Ex:
      VideoRecordingObject: {
        'tag': 'recordingResponse',
        'objValue': {
          'recordedOutputPath': '/storage/emulated/0/Android/data/'
                                'com.android.cts.verifier/files/VideoITS/'
                                'VID_20220324_080414_0_CIF_352x288.mp4',
          'quality': 'preview',
          'videoSize': '352x288'
        }
      }
    """
    self.sock.send(json.dumps(cmd).encode() + '\n'.encode())
    timeout = (self.SOCK_TIMEOUT_PREVIEW +
               self.EXTRA_SOCK_TIMEOUT * _EXTRA_TIMEOUT_FACTOR)
    self.sock.settimeout(timeout)

    data, _ = self.__read_response_from_socket()
    logging.debug('VideoRecordingObject: %s', str(data))
    if data[_TAG_STR] != 'recordingResponse':
      raise error_util.CameraItsError(
          f'Invalid response from command{cmd[_CMD_NAME_STR]}')
    return data[_OBJ_VALUE_STR]

  def do_preview_recording(self, video_size, duration, stabilize, ois=False,
                           zoom_ratio=None, ae_target_fps_min=None,
                           ae_target_fps_max=None, hlg10_enabled=False):
    """Issue a preview request and read back the preview recording object.

    The resolution of the preview and its recording will be determined by
    video_size. The duration is the time in seconds for which the preview will
    be recorded. The recorded object consists of a path on the device at
    which the recorded video is saved.

    Args:
      video_size: str; Preview resolution at which to record. ex. "1920x1080"
      duration: int; The time in seconds for which the video will be recorded.
      stabilize: boolean; Whether the preview should be stabilized or not
      ois: boolean; Whether the preview should be optically stabilized or not
      zoom_ratio: float; static zoom ratio. None if default zoom
      ae_target_fps_min: int; CONTROL_AE_TARGET_FPS_RANGE min. Set if not None
      ae_target_fps_max: int; CONTROL_AE_TARGET_FPS_RANGE max. Set if not None
      hlg10_enabled: boolean; True Eanable 10-bit HLG video recording, False
                              record using the regular SDK profile.
    Returns:
      video_recorded_object: The recorded object returned from ItsService
    """
    cmd = {
        _CMD_NAME_STR: 'doPreviewRecording',
        _CAMERA_ID_STR: self._camera_id,
        'videoSize': video_size,
        'recordingDuration': duration,
        'stabilize': stabilize,
        'ois': ois,
        'hlg10Enabled': hlg10_enabled,
    }
    if zoom_ratio:
      if self.zoom_ratio_within_range(zoom_ratio):
        cmd['zoomRatio'] = zoom_ratio
      else:
        raise AssertionError(f'Zoom ratio {zoom_ratio} out of range')
    if ae_target_fps_min and ae_target_fps_max:
      cmd['aeTargetFpsMin'] = ae_target_fps_min
      cmd['aeTargetFpsMax'] = ae_target_fps_max
    return self._execute_preview_recording(cmd)

  def do_preview_recording_with_dynamic_zoom(self, video_size, stabilize,
                                             sweep_zoom,
                                             ae_target_fps_min=None,
                                             ae_target_fps_max=None):
    """Issue a preview request with dynamic zoom and read back output object.

    The resolution of the preview and its recording will be determined by
    video_size. The duration will be determined by the duration at each zoom
    ratio and the total number of zoom ratios. The recorded object consists
    of a path on the device at which the recorded video is saved.

    Args:
      video_size: str; Preview resolution at which to record. ex. "1920x1080"
      stabilize: boolean; Whether the preview should be stabilized or not
      sweep_zoom: tuple of (zoom_start, zoom_end, step_size, step_duration).
        Used to control zoom ratio during recording.
        zoom_start (float) is the starting zoom ratio during recording
        zoom_end (float) is the ending zoom ratio during recording
        step_size (float) is the step for zoom ratio during recording
        step_duration (float) sleep in ms between zoom ratios
      ae_target_fps_min: int; CONTROL_AE_TARGET_FPS_RANGE min. Set if not None
      ae_target_fps_max: int; CONTROL_AE_TARGET_FPS_RANGE max. Set if not None
    Returns:
      video_recorded_object: The recorded object returned from ItsService
    """
    cmd = {
        _CMD_NAME_STR: 'doPreviewRecording',
        _CAMERA_ID_STR: self._camera_id,
        'videoSize': video_size,
        'recordingDuration': 0,  # for interoperability
        'stabilize': stabilize,
        'ois': False
    }
    zoom_start, zoom_end, step_size, step_duration = sweep_zoom
    if (not self.zoom_ratio_within_range(zoom_start) or
        not self.zoom_ratio_within_range(zoom_end)):
      raise AssertionError(
          f'Starting zoom ratio {zoom_start} or '
          f'ending zoom ratio {zoom_end} out of range'
      )
    if zoom_start > zoom_end or step_size < 0:
      raise NotImplementedError('Only increasing zoom ratios are supported')
    cmd['zoomStart'] = zoom_start
    cmd['zoomEnd'] = zoom_end
    cmd['stepSize'] = step_size
    cmd['stepDuration'] = step_duration
    cmd['hlg10Enabled'] = False
    if ae_target_fps_min and ae_target_fps_max:
      cmd['aeTargetFpsMin'] = ae_target_fps_min
      cmd['aeTargetFpsMax'] = ae_target_fps_max
    return self._execute_preview_recording(cmd)

  def do_preview_recording_with_dynamic_three_a_region(self, video_size,
                                                       stabilize,
                                                       three_a_regions,
                                                       three_a_region_duration,
                                                       ae_target_fps_min=None,
                                                       ae_target_fps_max=None):
    """Issue a preview request with dynamic 3A region and read back output object.

    The resolution of the preview and its recording will be determined by
    video_size. The recorded object consists of a path on the device at which
    the recorded video is saved.

    Args:
      video_size: str; Preview resolution at which to record. ex. "1920x1080"
      stabilize: boolean; Whether the preview should be stabilized.
      three_a_regions: dictionary of (threeARegionStart/Change/End).
        Used to control 3A region during recording.
        threeARegionStart (metering rectangle) starting 3A region of recording.
        threeARegionChange (metering rectangle) changed 3A region from start.
        threeARegionEnd (metering rectangle) ending 3A region of recording.
      three_a_region_duration: float; sleep in ms between 3A regions.
      ae_target_fps_min: int; If not none, set CONTROL_AE_TARGET_FPS_RANGE min.
      ae_target_fps_max: int; If not none, set CONTROL_AE_TARGET_FPS_RANGE max.
    Returns:
      video_recorded_object: The recorded object returned from ItsService.
    """
    cmd = {
        _CMD_NAME_STR: 'doPreviewRecording',
        _CAMERA_ID_STR: self._camera_id,
        'videoSize': video_size,
        'recordingDuration': 0,  # set to 0 to avoid JSONException
        'stabilize': stabilize,
        'ois': False,
        'threeARegionDuration': three_a_region_duration
    }

    cmd['threeARegionStart'] = three_a_regions['threeARegionStart']
    cmd['threeARegionChange'] = three_a_regions['threeARegionChange']
    cmd['threeARegionEnd'] = three_a_regions['threeARegionEnd']
    cmd['hlg10Enabled'] = False
    if ae_target_fps_min and ae_target_fps_max:
      cmd['aeTargetFpsMin'] = ae_target_fps_min
      cmd['aeTargetFpsMax'] = ae_target_fps_max
    return self._execute_preview_recording(cmd)

  def get_supported_video_qualities(self, camera_id):
    """Get all supported video qualities for this camera device.

    ie. ['480:4', '1080:6', '2160:8', '720:5', 'CIF:3', 'HIGH:1', 'LOW:0',
         'QCIF:2', 'QVGA:7']

    Args:
      camera_id: device id
    Returns:
      List of all supported video qualities and corresponding profileIds.
    """
    cmd = {}
    cmd[_CMD_NAME_STR] = 'getSupportedVideoQualities'
    cmd[_CAMERA_ID_STR] = camera_id
    self.sock.send(json.dumps(cmd).encode() + '\n'.encode())
    data, _ = self.__read_response_from_socket()
    if data[_TAG_STR] != 'supportedVideoQualities':
      raise error_util.CameraItsError('Invalid command response')
    return data[_STR_VALUE_STR].split(';')[:-1]  # remove the last appended ';'

  def get_supported_preview_sizes(self, camera_id):
    """Get all supported preview resolutions for this camera device.

    ie. ['640x480', '800x600', '1280x720', '1440x1080', '1920x1080']

    Note: resolutions are sorted by width x height in ascending order

    Args:
      camera_id: int; device id
    Returns:
      List of all supported video resolutions in ascending order.
    """
    cmd = {
        _CMD_NAME_STR: 'getSupportedPreviewSizes',
        _CAMERA_ID_STR: camera_id
    }
    self.sock.send(json.dumps(cmd).encode() + '\n'.encode())
    timeout = self.SOCK_TIMEOUT + self.EXTRA_SOCK_TIMEOUT
    self.sock.settimeout(timeout)
    data, _ = self.__read_response_from_socket()
    if data[_TAG_STR] != 'supportedPreviewSizes':
      raise error_util.CameraItsError('Invalid command response')
    if not data[_STR_VALUE_STR]:
      raise error_util.CameraItsError('No supported preview sizes')
    return data[_STR_VALUE_STR].split(';')

  def get_queryable_stream_combinations(self):
    """Get all queryable stream combinations for this camera device.

    This function parses the queryable stream combinations string
    returned from ItsService. The return value includes both the
    string and the parsed result.

    One example of the queryable stream combination string is:

    'priv:1920x1080+jpeg:4032x2268;priv:1280x720+priv:1280x720'

    which can be parsed to:

    [
      {
       "name": "priv:1920x1080+jpeg:4032x2268",
       "combination": [
                        {
                         "format": "priv",
                         "size": "1920x1080"
                        }
                        {
                         "format": "jpeg",
                         "size": "4032x2268"
                        }
                      ]
      }
      {
       "name": "priv:1280x720+priv:1280x720",
       "combination": [
                        {
                         "format": "priv",
                         "size": "1280x720"
                        },
                        {
                         "format": "priv",
                         "size": "1280x720"
                        }
                      ]
      }
    ]

    Returns:
      Tuple of:
      - queryable stream combination string, and
      - parsed stream combinations
    """
    cmd = {
        _CMD_NAME_STR: 'getQueryableStreamCombinations',
    }
    self.sock.send(json.dumps(cmd).encode() + '\n'.encode())
    timeout = self.SOCK_TIMEOUT + self.EXTRA_SOCK_TIMEOUT
    self.sock.settimeout(timeout)
    data, _ = self.__read_response_from_socket()
    if data[_TAG_STR] != 'queryableStreamCombinations':
      raise error_util.CameraItsError('Invalid command response')
    if not data[_STR_VALUE_STR]:
      raise error_util.CameraItsError('No queryable stream combinations')

    # Parse the stream combination string
    combinations = [{
        'name': c, 'combination': [
            {'format': s.split(':')[0],
             'size': s.split(':')[1]} for s in c.split('+')]}
                    for c in data[_STR_VALUE_STR].split(';')]

    return data[_STR_VALUE_STR], combinations

  def get_supported_extensions(self, camera_id):
    """Get all supported camera extensions for this camera device.

    ie. [EXTENSION_AUTOMATIC, EXTENSION_BOKEH,
         EXTENSION_FACE_RETOUCH, EXTENSION_HDR, EXTENSION_NIGHT]
    where EXTENSION_AUTOMATIC is 0, EXTENSION_BOKEH is 1, etc.

    Args:
      camera_id: int; device ID
    Returns:
      List of all supported extensions (as int) in ascending order.
    """
    cmd = {
        'cmdName': 'getSupportedExtensions',
        'cameraId': camera_id
    }
    self.sock.send(json.dumps(cmd).encode() + '\n'.encode())
    timeout = self.SOCK_TIMEOUT + self.EXTRA_SOCK_TIMEOUT
    self.sock.settimeout(timeout)
    data, _ = self.__read_response_from_socket()
    if data['tag'] != 'supportedExtensions':
      raise error_util.CameraItsError('Invalid command response')
    if not data['strValue']:
      raise error_util.CameraItsError('No supported extensions')
    return [int(x) for x in str(data['strValue'][1:-1]).split(', ') if x]

  def get_supported_extension_sizes(self, camera_id, extension, image_format):
    """Get all supported camera sizes for this camera, extension, and format.

    Sorts in ascending order according to area, i.e.
    ['640x480', '800x600', '1280x720', '1440x1080', '1920x1080']

    Args:
      camera_id: int; device ID
      extension: int; the integer value of the extension.
      image_format: int; the integer value of the format.
    Returns:
      List of sizes supported for this camera, extension, and format.
    """
    cmd = {
        'cmdName': 'getSupportedExtensionSizes',
        'cameraId': camera_id,
        'extension': extension,
        'format': image_format
    }
    self.sock.send(json.dumps(cmd).encode() + '\n'.encode())
    timeout = self.SOCK_TIMEOUT + self.EXTRA_SOCK_TIMEOUT
    self.sock.settimeout(timeout)
    data, _ = self.__read_response_from_socket()
    if data[_TAG_STR] != 'supportedExtensionSizes':
      raise error_util.CameraItsError('Invalid command response')
    if not data[_STR_VALUE_STR]:
      logging.debug('No supported extension sizes')
      return ''
    return data[_STR_VALUE_STR].split(';')

  def get_display_size(self):
    """Get the display size of the screen.

    Returns:
      The size of the display resolution in pixels.
    """
    cmd = {
        'cmdName': 'getDisplaySize'
    }
    self.sock.send(json.dumps(cmd).encode() + '\n'.encode())
    timeout = self.SOCK_TIMEOUT + self.EXTRA_SOCK_TIMEOUT
    self.sock.settimeout(timeout)
    data, _ = self.__read_response_from_socket()
    if data['tag'] != 'displaySize':
      raise error_util.CameraItsError('Invalid command response')
    if not data['strValue']:
      raise error_util.CameraItsError('No display size')
    return data['strValue'].split('x')

  def get_max_camcorder_profile_size(self, camera_id):
    """Get the maximum camcorder profile size for this camera device.

    Args:
      camera_id: int; device id
    Returns:
      The maximum size among all camcorder profiles supported by this camera.
    """
    cmd = {
        'cmdName': 'getMaxCamcorderProfileSize',
        'cameraId': camera_id
    }
    self.sock.send(json.dumps(cmd).encode() + '\n'.encode())
    timeout = self.SOCK_TIMEOUT + self.EXTRA_SOCK_TIMEOUT
    self.sock.settimeout(timeout)
    data, _ = self.__read_response_from_socket()
    if data['tag'] != 'maxCamcorderProfileSize':
      raise error_util.CameraItsError('Invalid command response')
    if not data['strValue']:
      raise error_util.CameraItsError('No max camcorder profile size')
    return data['strValue'].split('x')

  def do_simple_capture(self, cmd, out_surface):
    """Issue single capture request via command and read back image/metadata.

    Args:
      cmd: Dictionary specifying command name, requests, and output surface.
      out_surface: Dictionary describing output surface.
    Returns:
      An object which contains following fields:
      * data: the image data as a numpy array of bytes.
      * width: the width of the captured image.
      * height: the height of the captured image.
      * format: image format
      * metadata: the capture result object
    """
    fmt = out_surface['format'] if 'format' in out_surface else 'yuv'
    if fmt == 'jpg': fmt = 'jpeg'

    # we only have 1 capture request and 1 surface by definition.
    ncap = SINGLE_CAPTURE_NCAP

    cam_id = None
    bufs = {}
    yuv_bufs = {}
    if self._hidden_physical_id:
      out_surface['physicalCamera'] = self._hidden_physical_id

    if 'physicalCamera' in out_surface:
      cam_id = out_surface['physicalCamera']
    else:
      cam_id = self._camera_id

    bufs[cam_id] = {
        'raw': [],
        'raw10': [],
        'raw12': [],
        'rawStats': [],
        'dng': [],
        'jpeg': [],
        'y8': [],
        'rawQuadBayer': [],
        'rawQuadBayerStats': [],
        'raw10Stats': [],
        'raw10QuadBayerStats': [],
        'raw10QuadBayer': [],
    }

    # Only allow yuv output to multiple targets
    yuv_surface = None
    if cam_id == self._camera_id:
      if 'physicalCamera' not in out_surface:
        if out_surface['format'] == 'yuv':
          yuv_surface = out_surface
    else:
      if ('physicalCamera' in out_surface and
          out_surface['physicalCamera'] == cam_id):
        if out_surface['format'] == 'yuv':
          yuv_surface = out_surface

    # Compute the buffer size of YUV targets
    yuv_maxsize_1d = 0
    if yuv_surface is not None:
      if ('width' not in yuv_surface and 'height' not in yuv_surface):
        if self.props is None:
          raise error_util.CameraItsError('Camera props are unavailable')
        yuv_maxsize_2d = capture_request_utils.get_available_output_sizes(
            'yuv', self.props)[0]
        # YUV420 size = 1.5 bytes per pixel
        yuv_maxsize_1d = (yuv_maxsize_2d[0] * yuv_maxsize_2d[1] * 3) // 2
      if 'width' in yuv_surface and 'height' in yuv_surface:
        yuv_size = (yuv_surface['width'] * yuv_surface['height'] * 3) // 2
      else:
        yuv_size = yuv_maxsize_1d

      yuv_bufs[cam_id] = {yuv_size: []}

    cam_ids = self._camera_id
    self.sock.settimeout(self.SOCK_TIMEOUT + self.EXTRA_SOCK_TIMEOUT)
    self.sock.send(json.dumps(cmd).encode() + '\n'.encode())

    nbufs = 0
    md = None
    physical_md = None
    width = None
    height = None
    capture_results_returned = False
    while (nbufs < ncap) or (not capture_results_returned):
      json_obj, buf = self.__read_response_from_socket()
      if (json_obj[_TAG_STR] in ItsSession.IMAGE_FORMAT_LIST_1 and
          buf is not None):
        fmt = json_obj[_TAG_STR][:-5]
        bufs[self._camera_id][fmt].append(buf)
        nbufs += 1
      elif json_obj[_TAG_STR] == 'yuvImage':
        buf_size = numpy.product(buf.shape)
        yuv_bufs[self._camera_id][buf_size].append(buf)
        nbufs += 1
      elif json_obj[_TAG_STR] == 'captureResults':
        capture_results_returned = True
        md = json_obj[_OBJ_VALUE_STR]['captureResult']
        physical_md = json_obj[_OBJ_VALUE_STR]['physicalResults']
        outputs = json_obj[_OBJ_VALUE_STR]['outputs']
        returned_fmt = outputs[0]['format']
        if fmt != returned_fmt:
          raise AssertionError(
              f'Incorrect format. Requested: {fmt}, '
              f'Received: {returned_fmt}')
        width = outputs[0]['width']
        height = outputs[0]['height']
        requested_width = out_surface['width']
        requested_height = out_surface['height']
        if requested_width != width or requested_height != height:
          raise AssertionError(
              'Incorrect size. '
              f'Requested: {requested_width}x{requested_height}, '
              f'Received: {width}x{height}')
      else:
        tag_string = unicodedata.normalize('NFKD', json_obj[_TAG_STR]).encode(
            'ascii', 'ignore')
        for x in ItsSession.IMAGE_FORMAT_LIST_2:
          x = bytes(x, encoding='utf-8')
          if tag_string.startswith(x):
            if x == b'yuvImage':
              physical_id = json_obj[_TAG_STR][len(x):]
              if physical_id in cam_ids:
                buf_size = numpy.product(buf.shape)
                yuv_bufs[physical_id][buf_size].append(buf)
                nbufs += 1
            else:
              physical_id = json_obj[_TAG_STR][len(x):]
              if physical_id in cam_ids:
                fmt = x[:-5].decode('UTF-8')
                bufs[physical_id][fmt].append(buf)
                nbufs += 1

    if 'physicalCamera' in out_surface:
      cam_id = out_surface['physicalCamera']
    else:
      cam_id = self._camera_id
    ret = {'width': width, 'height': height, 'format': fmt}
    if cam_id == self._camera_id:
      ret['metadata'] = md
    else:
      if cam_id in physical_md:
        ret['metadata'] = physical_md[cam_id]

    if fmt == 'yuv':
      buf_size = (width * height * 3) // 2
      ret['data'] = yuv_bufs[cam_id][buf_size][0]
    else:
      ret['data'] = bufs[cam_id][fmt][0]

    return ret

  def do_jca_capture(self, dut, log_path, flash, facing):
    """Take a capture using JCA, modifying capture settings using the UI.

    Selects UI elements to modify settings, and presses the capture button.
    Reads response from socket containing the capture path, and
    pulls the image from the DUT.

    This method is included here because an ITS session is needed to retrieve
    the capture path from the device.

    Args:
      dut: An Android controller device object.
      log_path: str; log path to save screenshots.
      flash: str; constant describing the desired flash mode.
        Acceptable values: 'OFF' and 'AUTO'.
      facing: str; constant describing the direction the camera lens faces.
        Acceptable values: camera_properties_utils.LENS_FACING[BACK, FRONT]
    Returns:
      The host-side path of the capture.
    """
    ui_interaction_utils.open_jca_viewfinder(dut, log_path)
    ui_interaction_utils.switch_jca_camera(dut, log_path, facing)
    # Bring up settings, switch flash mode, and close settings
    dut.ui(res=ui_interaction_utils.QUICK_SETTINGS_RESOURCE_ID).click()
    if flash not in ui_interaction_utils.FLASH_MODE_TO_CLICKS:
      raise ValueError(f'Flash mode {flash} not supported')
    for _ in range(ui_interaction_utils.FLASH_MODE_TO_CLICKS[flash]):
      dut.ui(res=ui_interaction_utils.QUICK_SET_FLASH_RESOURCE_ID).click()
    dut.take_screenshot(log_path, prefix='flash_mode_set')
    dut.ui(res=ui_interaction_utils.QUICK_SETTINGS_RESOURCE_ID).click()
    # Take capture
    dut.ui(res=ui_interaction_utils.CAPTURE_BUTTON_RESOURCE_ID).click()
    return self.get_and_pull_jca_capture(dut, log_path)

  def get_and_pull_jca_capture(self, dut, log_path):
    """Retrieves a capture path from the socket and pulls capture to host.

    Args:
      dut: An Android controller device object.
      log_path: str; log path to save screenshots.
    Returns:
      The host-side path of the capture.
    Raises:
      CameraItsError: If unexpected data is retrieved from the socket.
    """
    capture_path, capture_status = None, None
    while not capture_path or not capture_status:
      data, _ = self.__read_response_from_socket()
      if data[_TAG_STR] == JCA_CAPTURE_PATH_TAG:
        capture_path = data[_STR_VALUE_STR]
      elif data[_TAG_STR] == JCA_CAPTURE_STATUS_TAG:
        capture_status = data[_STR_VALUE_STR]
      else:
        raise error_util.CameraItsError(
            f'Invalid response {data[_TAG_STR]} for JCA capture')
    if capture_status != RESULT_OK_STATUS:
      logging.error('Capture failed! Expected status %d, received %d',
                    RESULT_OK_STATUS, capture_status)
    logging.debug('capture path: %s', capture_path)
    _, capture_name = os.path.split(capture_path)
    its_device_utils.run(f'adb -s {dut.serial} pull {capture_path} {log_path}')
    return os.path.join(log_path, capture_name)

  def do_capture_with_flash(self,
                            preview_request_start,
                            preview_request_idle,
                            still_capture_req,
                            out_surface):
    """Issue capture request with flash and read back the image and metadata.

    Captures a single image with still_capture_req as capture request
    with flash. It triggers the precapture sequence with preview request
    preview_request_start with capture intent preview by setting aePrecapture
    trigger to Start. This is followed by repeated preview requests
    preview_request_idle with aePrecaptureTrigger set to IDLE.
    Once the AE is converged, a single image is captured still_capture_req
    during which the flash must be fired.
    Note: The part where we read output data from socket is cloned from
    do_capture and will be consolidated in U.

    Args:
      preview_request_start: Preview request with aePrecaptureTrigger set to
        Start
      preview_request_idle: Preview request with aePrecaptureTrigger set to Idle
      still_capture_req: Single still capture request.
      out_surface: Specifications of the output image formats and
        sizes to use for capture. Supports yuv and jpeg.
    Returns:
      An object which contains following fields:
      * data: the image data as a numpy array of bytes.
      * width: the width of the captured image.
      * height: the height of the captured image.
      * format: image format
      * metadata: the capture result object
    """
    cmd = {}
    cmd[_CMD_NAME_STR] = 'doCaptureWithFlash'
    cmd['previewRequestStart'] = [preview_request_start]
    cmd['previewRequestIdle'] = [preview_request_idle]
    cmd['stillCaptureRequest'] = [still_capture_req]
    cmd['outputSurfaces'] = [out_surface]

    logging.debug('Capturing image with ON_AUTO_FLASH.')
    return self.do_simple_capture(cmd, out_surface)

  def do_capture_with_extensions(self,
                                 cap_request,
                                 extension,
                                 out_surface):
    """Issue extension capture request(s), and read back image(s) and metadata.

    Args:
      cap_request: The Python dict/list specifying the capture(s), which will be
        converted to JSON and sent to the device.
      extension: The extension to be requested.
      out_surface: specifications of the output image format and
        size to use for the capture.

    Returns:
      An object, list of objects, or list of lists of objects, where each
      object contains the following fields:
      * data: the image data as a numpy array of bytes.
      * width: the width of the captured image.
      * height: the height of the captured image.
      * format: image the format, in [
                        "yuv","jpeg","raw","raw10","raw12","rawStats","dng"].
      * metadata: the capture result object (Python dictionary).
    """
    cmd = {}
    cmd[_CMD_NAME_STR] = 'doCaptureWithExtensions'
    cmd['repeatRequests'] = []
    cmd['captureRequests'] = [cap_request]
    cmd['extension'] = extension
    cmd['outputSurfaces'] = [out_surface]

    logging.debug('Capturing image with EXTENSIONS.')
    return self.do_simple_capture(cmd, out_surface)

  def do_capture(self,
                 cap_request,
                 out_surfaces=None,
                 reprocess_format=None,
                 repeat_request=None,
                 reuse_session=False):
    """Issue capture request(s), and read back the image(s) and metadata.

    The main top-level function for capturing one or more images using the
    device. Captures a single image if cap_request is a single object, and
    captures a burst if it is a list of objects.

    The optional repeat_request field can be used to assign a repeating
    request list ran in background for 3 seconds to warm up the capturing
    pipeline before start capturing. The repeat_requests will be ran on a
    640x480 YUV surface without sending any data back. The caller needs to
    make sure the stream configuration defined by out_surfaces and
    repeat_request are valid or do_capture may fail because device does not
    support such stream configuration.

    The out_surfaces field can specify the width(s), height(s), and
    format(s) of the captured image. The formats may be "yuv", "jpeg",
    "dng", "raw", "raw10", "raw12", "rawStats" or "y8". The default is a
    YUV420 frame ("yuv") corresponding to a full sensor frame.

    1. Optionally the out_surfaces field can specify physical camera id(s) if
    the current camera device is a logical multi-camera. The physical camera
    id must refer to a physical camera backing this logical camera device.
    2. Optionally The output_surfaces field can also specify the use case(s) if
    the current camera device has STREAM_USE_CASE capability.

    Note that one or more surfaces can be specified, allowing a capture to
    request images back in multiple formats (e.g.) raw+yuv, raw+jpeg,
    yuv+jpeg, raw+yuv+jpeg. If the size is omitted for a surface, the
    default is the largest resolution available for the format of that
    surface. At most one output surface can be specified for a given format,
    and raw+dng, raw10+dng, and raw+raw10 are not supported as combinations.

    If reprocess_format is not None, for each request, an intermediate
    buffer of the given reprocess_format will be captured from camera and
    the intermediate buffer will be reprocessed to the output surfaces. The
    following settings will be turned off when capturing the intermediate
    buffer and will be applied when reprocessing the intermediate buffer.
    1. android.noiseReduction.mode
    2. android.edge.mode
    3. android.reprocess.effectiveExposureFactor

    Supported reprocess format are "yuv" and "private". Supported output
    surface formats when reprocessing is enabled are "yuv" and "jpeg".

    Example of a single capture request:

    {
     "android.sensor.exposureTime": 100*1000*1000,
     "android.sensor.sensitivity": 100
    }

    Example of a list of capture requests:
    [
     {
       "android.sensor.exposureTime": 100*1000*1000,
       "android.sensor.sensitivity": 100
     },
    {
      "android.sensor.exposureTime": 100*1000*1000,
       "android.sensor.sensitivity": 200
     }
    ]

    Example of output surface specifications:
    {
     "width": 640,
     "height": 480,
     "format": "yuv"
    }
    [
     {
       "format": "jpeg"
     },
     {
       "format": "raw"
     }
    ]

    The following variables defined in this class are shortcuts for
    specifying one or more formats where each output is the full size for
    that format; they can be used as values for the out_surfaces arguments:

    CAP_RAW
    CAP_DNG
    CAP_YUV
    CAP_JPEG
    CAP_RAW_YUV
    CAP_DNG_YUV
    CAP_RAW_JPEG
    CAP_DNG_JPEG
    CAP_YUV_JPEG
    CAP_RAW_YUV_JPEG
    CAP_DNG_YUV_JPEG

    If multiple formats are specified, then this function returns multiple
    capture objects, one for each requested format. If multiple formats and
    multiple captures (i.e. a burst) are specified, then this function
    returns multiple lists of capture objects. In both cases, the order of
    the returned objects matches the order of the requested formats in the
    out_surfaces parameter. For example:

    yuv_cap = do_capture(req1)
    yuv_cap = do_capture(req1,yuv_fmt)
    yuv_cap, raw_cap = do_capture(req1, [yuv_fmt,raw_fmt])
    yuv_caps = do_capture([req1,req2], yuv_fmt)
    yuv_caps, raw_caps = do_capture([req1,req2], [yuv_fmt,raw_fmt])

    The "rawStats" format processes the raw image and returns a new image
    of statistics from the raw image. The format takes additional keys,
    "gridWidth" and "gridHeight" which are size of grid cells in a 2D grid
    of the raw image. For each grid cell, the mean and variance of each raw
    channel is computed, and the do_capture call returns two 4-element float
    images of dimensions (rawWidth / gridWidth, rawHeight / gridHeight),
    concatenated back-to-back, where the first image contains the 4-channel
    means and the second contains the 4-channel variances. Note that only
    pixels in the active array crop region are used; pixels outside this
    region (for example optical black rows) are cropped out before the
    gridding and statistics computation is performed.

    For the rawStats format, if the gridWidth is not provided then the raw
    image width is used as the default, and similarly for gridHeight. With
    this, the following is an example of a output description that computes
    the mean and variance across each image row:
    {
      "gridHeight": 1,
      "format": "rawStats"
    }

    Args:
      cap_request: The Python dict/list specifying the capture(s), which will be
        converted to JSON and sent to the device.
      out_surfaces: (Optional) specifications of the output image formats and
        sizes to use for each capture.
      reprocess_format: (Optional) The reprocessing format. If not
        None,reprocessing will be enabled.
      repeat_request: Repeating request list.
      reuse_session: True if ItsService.java should try to use
        the existing CameraCaptureSession.

    Returns:
      An object, list of objects, or list of lists of objects, where each
      object contains the following fields:
      * data: the image data as a numpy array of bytes.
      * width: the width of the captured image.
      * height: the height of the captured image.
      * format: image the format, in [
                        "yuv","jpeg","raw","raw10","raw12","rawStats","dng"].
      * metadata: the capture result object (Python dictionary).
    """
    cmd = {}
    if reprocess_format is not None:
      if repeat_request is not None:
        raise error_util.CameraItsError(
            'repeating request + reprocessing is not supported')
      cmd[_CMD_NAME_STR] = 'doReprocessCapture'
      cmd['reprocessFormat'] = reprocess_format
    else:
      cmd[_CMD_NAME_STR] = 'doCapture'

    if repeat_request is None:
      cmd['repeatRequests'] = []
    elif not isinstance(repeat_request, list):
      cmd['repeatRequests'] = [repeat_request]
    else:
      cmd['repeatRequests'] = repeat_request

    if not isinstance(cap_request, list):
      cmd['captureRequests'] = [cap_request]
    else:
      cmd['captureRequests'] = cap_request

    if out_surfaces is not None:
      if not isinstance(out_surfaces, list):
        cmd['outputSurfaces'] = [out_surfaces]
      else:
        cmd['outputSurfaces'] = out_surfaces
      formats = [
          c['format'] if 'format' in c else 'yuv' for c in cmd['outputSurfaces']
      ]
      formats = [s if s != 'jpg' else 'jpeg' for s in formats]
    else:
      max_yuv_size = capture_request_utils.get_available_output_sizes(
          'yuv', self.props)[0]
      formats = ['yuv']
      cmd['outputSurfaces'] = [{
          'format': 'yuv',
          'width': max_yuv_size[0],
          'height': max_yuv_size[1]
      }]
    cmd['reuseSession'] = reuse_session

    ncap = len(cmd['captureRequests'])
    nsurf = 1 if out_surfaces is None else len(cmd['outputSurfaces'])

    cam_ids = []
    bufs = {}
    yuv_bufs = {}
    for i, s in enumerate(cmd['outputSurfaces']):
      if self._hidden_physical_id:
        s['physicalCamera'] = self._hidden_physical_id

      if 'physicalCamera' in s:
        cam_id = s['physicalCamera']
      else:
        cam_id = self._camera_id

      if cam_id not in cam_ids:
        cam_ids.append(cam_id)
        bufs[cam_id] = {
            'raw': [],
            'raw10': [],
            'raw12': [],
            'rawStats': [],
            'dng': [],
            'jpeg': [],
            'jpeg_r': [],
            'y8': [],
            'rawQuadBayer': [],
            'rawQuadBayerStats': [],
            'raw10Stats': [],
            'raw10QuadBayerStats': [],
            'raw10QuadBayer': [],
        }

    for cam_id in cam_ids:
       # Only allow yuv output to multiple targets
      if cam_id == self._camera_id:
        yuv_surfaces = [
            s for s in cmd['outputSurfaces']
            if s['format'] == 'yuv' and 'physicalCamera' not in s
        ]
        formats_for_id = [
            s['format']
            for s in cmd['outputSurfaces']
            if 'physicalCamera' not in s
        ]
      else:
        yuv_surfaces = [
            s for s in cmd['outputSurfaces'] if s['format'] == 'yuv' and
            'physicalCamera' in s and s['physicalCamera'] == cam_id
        ]
        formats_for_id = [
            s['format']
            for s in cmd['outputSurfaces']
            if 'physicalCamera' in s and s['physicalCamera'] == cam_id
        ]

      n_yuv = len(yuv_surfaces)
      # Compute the buffer size of YUV targets
      yuv_maxsize_1d = 0
      for s in yuv_surfaces:
        if ('width' not in s and 'height' not in s):
          if self.props is None:
            raise error_util.CameraItsError('Camera props are unavailable')
          yuv_maxsize_2d = capture_request_utils.get_available_output_sizes(
              'yuv', self.props)[0]
          # YUV420 size = 1.5 bytes per pixel
          yuv_maxsize_1d = (yuv_maxsize_2d[0] * yuv_maxsize_2d[1] * 3) // 2
          break
      yuv_sizes = [
          (c['width'] * c['height'] * 3) // 2
          if 'width' in c and 'height' in c else yuv_maxsize_1d
          for c in yuv_surfaces
      ]
      # Currently we don't pass enough metadata from ItsService to distinguish
      # different yuv stream of same buffer size
      if len(yuv_sizes) != len(set(yuv_sizes)):
        raise error_util.CameraItsError(
            'ITS does not support yuv outputs of same buffer size')
      if len(formats_for_id) > len(set(formats_for_id)):
        if n_yuv != len(formats_for_id) - len(set(formats_for_id)) + 1:
          raise error_util.CameraItsError('Duplicate format requested')

      yuv_bufs[cam_id] = {size: [] for size in yuv_sizes}

    raw_formats = 0
    raw_formats += 1 if 'dng' in formats else 0
    raw_formats += 1 if 'raw' in formats else 0
    raw_formats += 1 if 'raw10' in formats else 0
    raw_formats += 1 if 'raw12' in formats else 0
    raw_formats += 1 if 'rawStats' in formats else 0
    raw_formats += 1 if 'rawQuadBayer' in formats else 0
    raw_formats += 1 if 'rawQuadBayerStats' in formats else 0
    raw_formats += 1 if 'raw10Stats' in formats else 0
    raw_formats += 1 if 'raw10QuadBayer' in formats else 0
    raw_formats += 1 if 'raw10QuadBayerStats' in formats else 0

    if raw_formats > 1:
      raise error_util.CameraItsError('Different raw formats not supported')

    # Detect long exposure time and set timeout accordingly
    longest_exp_time = 0
    for req in cmd['captureRequests']:
      if 'android.sensor.exposureTime' in req and req[
          'android.sensor.exposureTime'] > longest_exp_time:
        longest_exp_time = req['android.sensor.exposureTime']

    extended_timeout = longest_exp_time // self.SEC_TO_NSEC + self.SOCK_TIMEOUT
    if repeat_request:
      extended_timeout += self.EXTRA_SOCK_TIMEOUT
    self.sock.settimeout(extended_timeout)

    logging.debug('Capturing %d frame%s with %d format%s [%s]', ncap,
                  's' if ncap > 1 else '', nsurf, 's' if nsurf > 1 else '',
                  ','.join(formats))
    self.sock.send(json.dumps(cmd).encode() + '\n'.encode())

    # Wait for ncap*nsurf images and ncap metadata responses.
    # Assume that captures come out in the same order as requested in
    # the burst, however individual images of different formats can come
    # out in any order for that capture.
    nbufs = 0
    mds = []
    physical_mds = []
    widths = None
    heights = None
    while nbufs < ncap * nsurf or len(mds) < ncap:
      json_obj, buf = self.__read_response_from_socket()
      if (json_obj[_TAG_STR] in ItsSession.IMAGE_FORMAT_LIST_1 and
          buf is not None):
        fmt = json_obj[_TAG_STR][:-5]
        bufs[self._camera_id][fmt].append(buf)
        nbufs += 1
      # Physical camera is appended to the tag string of a private capture
      elif json_obj[_TAG_STR].startswith('privImage'):
        # The private image format buffers are opaque to camera clients
        # and cannot be accessed.
        nbufs += 1
      elif json_obj[_TAG_STR] == 'yuvImage':
        buf_size = numpy.product(buf.shape)
        yuv_bufs[self._camera_id][buf_size].append(buf)
        nbufs += 1
      elif json_obj[_TAG_STR] == 'captureResults':
        mds.append(json_obj[_OBJ_VALUE_STR]['captureResult'])
        physical_mds.append(json_obj[_OBJ_VALUE_STR]['physicalResults'])
        outputs = json_obj[_OBJ_VALUE_STR]['outputs']
        widths = [out['width'] for out in outputs]
        heights = [out['height'] for out in outputs]
      else:
        tag_string = unicodedata.normalize('NFKD', json_obj[_TAG_STR]).encode(
            'ascii', 'ignore')
        for x in ItsSession.IMAGE_FORMAT_LIST_2:
          x = bytes(x, encoding='utf-8')
          if tag_string.startswith(x):
            if x == b'yuvImage':
              physical_id = json_obj[_TAG_STR][len(x):]
              if physical_id in cam_ids:
                buf_size = numpy.product(buf.shape)
                yuv_bufs[physical_id][buf_size].append(buf)
                nbufs += 1
            else:
              physical_id = json_obj[_TAG_STR][len(x):]
              if physical_id in cam_ids:
                fmt = x[:-5].decode('UTF-8')
                bufs[physical_id][fmt].append(buf)
                nbufs += 1
    rets = []
    for j, fmt in enumerate(formats):
      objs = []
      if 'physicalCamera' in cmd['outputSurfaces'][j]:
        cam_id = cmd['outputSurfaces'][j]['physicalCamera']
      else:
        cam_id = self._camera_id

      for i in range(ncap):
        obj = {}
        obj['width'] = widths[j]
        obj['height'] = heights[j]
        obj['format'] = fmt
        if cam_id == self._camera_id:
          obj['metadata'] = mds[i]
        else:
          for physical_md in physical_mds[i]:
            if cam_id in physical_md:
              obj['metadata'] = physical_md[cam_id]
              break

        if fmt == 'yuv':
          buf_size = (widths[j] * heights[j] * 3) // 2
          obj['data'] = yuv_bufs[cam_id][buf_size][i]
        elif fmt != 'priv':
          obj['data'] = bufs[cam_id][fmt][i]
        objs.append(obj)
      rets.append(objs if ncap > 1 else objs[0])
    self.sock.settimeout(self.SOCK_TIMEOUT)
    if len(rets) > 1 or (isinstance(rets[0], dict) and
                         isinstance(cap_request, list)):
      return rets
    else:
      return rets[0]

  def do_vibrate(self, pattern):
    """Cause the device to vibrate to a specific pattern.

    Args:
      pattern: Durations (ms) for which to turn on or off the vibrator.
      The first value indicates the number of milliseconds to wait
      before turning the vibrator on. The next value indicates the
      number of milliseconds for which to keep the vibrator on
      before turning it off. Subsequent values alternate between
      durations in milliseconds to turn the vibrator off or to turn
      the vibrator on.

    Returns:
      Nothing.
    """
    cmd = {}
    cmd[_CMD_NAME_STR] = 'doVibrate'
    cmd['pattern'] = pattern
    self.sock.send(json.dumps(cmd).encode() + '\n'.encode())
    data, _ = self.__read_response_from_socket()
    if data[_TAG_STR] != 'vibrationStarted':
      raise error_util.CameraItsError('Invalid response for command: %s' %
                                      cmd[_CMD_NAME_STR])

  def set_audio_restriction(self, mode):
    """Set the audio restriction mode for this camera device.

    Args:
     mode: int; the audio restriction mode. See CameraDevice.java for valid
     value.
    Returns:
     Nothing.
    """
    cmd = {}
    cmd[_CMD_NAME_STR] = 'setAudioRestriction'
    cmd['mode'] = mode
    self.sock.send(json.dumps(cmd).encode() + '\n'.encode())
    data, _ = self.__read_response_from_socket()
    if data[_TAG_STR] != 'audioRestrictionSet':
      raise error_util.CameraItsError('Invalid response for command: %s' %
                                      cmd[_CMD_NAME_STR])

  # pylint: disable=dangerous-default-value
  def do_3a(self,
            regions_ae=[[0, 0, 1, 1, 1]],
            regions_awb=[[0, 0, 1, 1, 1]],
            regions_af=[[0, 0, 1, 1, 1]],
            do_ae=True,
            do_awb=True,
            do_af=True,
            lock_ae=False,
            lock_awb=False,
            get_results=False,
            ev_comp=0,
            auto_flash=False,
            mono_camera=False,
            zoom_ratio=None,
            out_surfaces=None,
            repeat_request=None):
    """Perform a 3A operation on the device.

    Triggers some or all of AE, AWB, and AF, and returns once they have
    converged. Uses the vendor 3A that is implemented inside the HAL.
    Note: do_awb is always enabled regardless of do_awb flag

    Throws an assertion if 3A fails to converge.

    Args:
      regions_ae: List of weighted AE regions.
      regions_awb: List of weighted AWB regions.
      regions_af: List of weighted AF regions.
      do_ae: Trigger AE and wait for it to converge.
      do_awb: Wait for AWB to converge.
      do_af: Trigger AF and wait for it to converge.
      lock_ae: Request AE lock after convergence, and wait for it.
      lock_awb: Request AWB lock after convergence, and wait for it.
      get_results: Return the 3A results from this function.
      ev_comp: An EV compensation value to use when running AE.
      auto_flash: AE control boolean to enable auto flash.
      mono_camera: Boolean for monochrome camera.
      zoom_ratio: Zoom ratio. None if default zoom
      out_surfaces: dict; see do_capture() for specifications on out_surfaces.
        CameraCaptureSession will only be reused if out_surfaces is specified.
      repeat_request: repeating request list.
        See do_capture() for specifications on repeat_request.

      Region format in args:
         Arguments are lists of weighted regions; each weighted region is a
         list of 5 values, [x, y, w, h, wgt], and each argument is a list of
         these 5-value lists. The coordinates are given as normalized
         rectangles (x, y, w, h) specifying the region. For example:
         [[0.0, 0.0, 1.0, 0.5, 5], [0.0, 0.5, 1.0, 0.5, 10]].
         Weights are non-negative integers.

    Returns:
      Five values are returned if get_results is true:
      * AE sensitivity; None if do_ae is False
      * AE exposure time; None if do_ae is False
      * AWB gains (list);
      * AWB transform (list);
      * AF focus position; None if do_af is false
      Otherwise, it returns five None values.
    """
    logging.debug('Running vendor 3A on device')
    cmd = {}
    cmd[_CMD_NAME_STR] = 'do3A'
    reuse_session = False
    if out_surfaces is not None:
      reuse_session = True
      if not isinstance(out_surfaces, list):
        cmd['outputSurfaces'] = [out_surfaces]
      else:
        cmd['outputSurfaces'] = out_surfaces
    if repeat_request is None:
      cmd['repeatRequests'] = []
    elif not isinstance(repeat_request, list):
      cmd['repeatRequests'] = [repeat_request]
    else:
      cmd['repeatRequests'] = repeat_request

    cmd['regions'] = {
        'ae': sum(regions_ae, []),
        'awb': sum(regions_awb, []),
        'af': sum(regions_af, [])
    }
    cmd['triggers'] = {'ae': do_ae, 'af': do_af}
    if lock_ae:
      cmd['aeLock'] = True
    if lock_awb:
      cmd['awbLock'] = True
    if ev_comp != 0:
      cmd['evComp'] = ev_comp
    if auto_flash:
      cmd['autoFlash'] = True
    if self._hidden_physical_id:
      cmd['physicalId'] = self._hidden_physical_id
    if zoom_ratio:
      if self.zoom_ratio_within_range(zoom_ratio):
        cmd['zoomRatio'] = zoom_ratio
      else:
        raise AssertionError(f'Zoom ratio {zoom_ratio} out of range')
    cmd['reuseSession'] = reuse_session
    self.sock.send(json.dumps(cmd).encode() + '\n'.encode())

    # Wait for each specified 3A to converge.
    ae_sens = None
    ae_exp = None
    awb_gains = None
    awb_transform = None
    af_dist = None
    converged = False
    while True:
      data, _ = self.__read_response_from_socket()
      vals = data[_STR_VALUE_STR].split()
      if data[_TAG_STR] == 'aeResult':
        if do_ae:
          ae_sens, ae_exp = [int(i) for i in vals]
      elif data[_TAG_STR] == 'afResult':
        if do_af:
          af_dist = float(vals[0])
      elif data[_TAG_STR] == 'awbResult':
        awb_gains = [float(f) for f in vals[:4]]
        awb_transform = [float(f) for f in vals[4:]]
      elif data[_TAG_STR] == '3aConverged':
        converged = True
      elif data[_TAG_STR] == '3aDone':
        break
      else:
        raise error_util.CameraItsError('Invalid command response')
    if converged and not get_results:
      return None, None, None, None, None
    if (do_ae and ae_sens is None or
        (not mono_camera and do_awb and awb_gains is None) or
        do_af and af_dist is None or not converged):
      raise error_util.CameraItsError('3A failed to converge')
    return ae_sens, ae_exp, awb_gains, awb_transform, af_dist

  def calc_camera_fov(self, props):
    """Determine the camera field of view from internal params.

    Args:
      props: Camera properties object.

    Returns:
      camera_fov: string; field of view for camera.
    """

    focal_ls = props['android.lens.info.availableFocalLengths']
    if len(focal_ls) > 1:
      logging.debug('Doing capture to determine logical camera focal length')
      cap = self.do_capture(capture_request_utils.auto_capture_request())
      focal_l = cap['metadata']['android.lens.focalLength']
    else:
      focal_l = focal_ls[0]

    sensor_size = props['android.sensor.info.physicalSize']
    diag = math.sqrt(sensor_size['height']**2 + sensor_size['width']**2)
    try:
      fov = str(round(2 * math.degrees(math.atan(diag / (2 * focal_l))), 2))
    except ValueError:
      fov = str(0)
    logging.debug('Calculated FoV: %s', fov)
    return fov

  def get_file_name_to_load(self, chart_distance, camera_fov, scene):
    """Get the image to load on the tablet depending on fov and chart_distance.

    Args:
     chart_distance: float; distance in cm from camera of displayed chart
     camera_fov: float; camera field of view.
     scene: String; Scene to be used in the test.

    Returns:
     file_name: file name to display on the tablet.

    """
    chart_scaling = opencv_processing_utils.calc_chart_scaling(
        chart_distance, camera_fov)
    if math.isclose(
        chart_scaling,
        opencv_processing_utils.SCALE_WIDE_IN_22CM_RIG,
        abs_tol=SCALING_TO_FILE_ATOL):
      file_name = f'{scene}_{opencv_processing_utils.SCALE_WIDE_IN_22CM_RIG}x_scaled.png'
    elif math.isclose(
        chart_scaling,
        opencv_processing_utils.SCALE_TELE_IN_22CM_RIG,
        abs_tol=SCALING_TO_FILE_ATOL):
      file_name = f'{scene}_{opencv_processing_utils.SCALE_TELE_IN_22CM_RIG}x_scaled.png'
    elif math.isclose(
        chart_scaling,
        opencv_processing_utils.SCALE_TELE25_IN_31CM_RIG,
        abs_tol=SCALING_TO_FILE_ATOL):
      file_name = f'{scene}_{opencv_processing_utils.SCALE_TELE25_IN_31CM_RIG}x_scaled.png'
    elif math.isclose(
        chart_scaling,
        opencv_processing_utils.SCALE_TELE40_IN_31CM_RIG,
        abs_tol=SCALING_TO_FILE_ATOL):
      file_name = f'{scene}_{opencv_processing_utils.SCALE_TELE40_IN_31CM_RIG}x_scaled.png'
    elif math.isclose(
        chart_scaling,
        opencv_processing_utils.SCALE_TELE_IN_31CM_RIG,
        abs_tol=SCALING_TO_FILE_ATOL):
      file_name = f'{scene}_{opencv_processing_utils.SCALE_TELE_IN_31CM_RIG}x_scaled.png'
    else:
      file_name = f'{scene}.png'
    logging.debug('Scene to load: %s', file_name)
    return file_name

  def is_stream_combination_supported(self, out_surfaces, settings=None):
    """Query whether out_surfaces combination and settings are supported by the camera device.

    This function hooks up to the isSessionConfigurationSupported()/
    isSessionConfigurationWithSettingsSupported() camera API
    to query whether a particular stream combination and settings are supported.

    Args:
      out_surfaces: dict; see do_capture() for specifications on out_surfaces.
      settings: dict; optional capture request settings metadata.

    Returns:
      Boolean
    """
    cmd = {}
    cmd[_CMD_NAME_STR] = 'isStreamCombinationSupported'
    cmd[_CAMERA_ID_STR] = self._camera_id

    if not isinstance(out_surfaces, list):
      cmd['outputSurfaces'] = [out_surfaces]
    else:
      cmd['outputSurfaces'] = out_surfaces

    if settings:
      cmd['settings'] = settings

    self.sock.send(json.dumps(cmd).encode() + '\n'.encode())

    data, _ = self.__read_response_from_socket()
    if data[_TAG_STR] != 'streamCombinationSupport':
      raise error_util.CameraItsError('Failed to query stream combination')

    return data[_STR_VALUE_STR] == 'supportedCombination'

  def is_camera_privacy_mode_supported(self):
    """Query whether the mobile device supports camera privacy mode.

    This function checks whether the mobile device has FEATURE_CAMERA_TOGGLE
    feature support, which indicates the camera device can run in privacy mode.

    Returns:
      Boolean
    """
    cmd = {}
    cmd[_CMD_NAME_STR] = 'isCameraPrivacyModeSupported'
    self.sock.send(json.dumps(cmd).encode() + '\n'.encode())

    data, _ = self.__read_response_from_socket()
    if data[_TAG_STR] != 'cameraPrivacyModeSupport':
      raise error_util.CameraItsError('Failed to query camera privacy mode'
                                      ' support')
    return data[_STR_VALUE_STR] == 'true'

  def is_primary_camera(self):
    """Query whether the camera device is a primary rear/front camera.

    A primary rear/front facing camera is a camera device with the lowest
    camera Id for that facing.

    Returns:
      Boolean
    """
    cmd = {}
    cmd[_CMD_NAME_STR] = 'isPrimaryCamera'
    cmd[_CAMERA_ID_STR] = self._camera_id
    self.sock.send(json.dumps(cmd).encode() + '\n'.encode())

    data, _ = self.__read_response_from_socket()
    if data[_TAG_STR] != 'primaryCamera':
      raise error_util.CameraItsError('Failed to query primary camera')
    return data[_STR_VALUE_STR] == 'true'

  def is_performance_class(self):
    """Query whether the mobile device is an R or S performance class device.

    Returns:
      Boolean
    """
    cmd = {}
    cmd[_CMD_NAME_STR] = 'isPerformanceClass'
    self.sock.send(json.dumps(cmd).encode() + '\n'.encode())

    data, _ = self.__read_response_from_socket()
    if data[_TAG_STR] != 'performanceClass':
      raise error_util.CameraItsError('Failed to query performance class')
    return data[_STR_VALUE_STR] == 'true'

  def measure_camera_launch_ms(self):
    """Measure camera launch latency in millisecond, from open to first frame.

    Returns:
      Camera launch latency from camera open to receipt of first frame
    """
    cmd = {}
    cmd[_CMD_NAME_STR] = 'measureCameraLaunchMs'
    cmd[_CAMERA_ID_STR] = self._camera_id
    self.sock.send(json.dumps(cmd).encode() + '\n'.encode())

    timeout = self.SOCK_TIMEOUT_FOR_PERF_MEASURE
    self.sock.settimeout(timeout)
    data, _ = self.__read_response_from_socket()
    self.sock.settimeout(self.SOCK_TIMEOUT)

    if data[_TAG_STR] != 'cameraLaunchMs':
      raise error_util.CameraItsError('Failed to measure camera launch latency')
    return float(data[_STR_VALUE_STR])

  def measure_camera_1080p_jpeg_capture_ms(self):
    """Measure camera 1080P jpeg capture latency in milliseconds.

    Returns:
      Camera jpeg capture latency in milliseconds
    """
    cmd = {}
    cmd[_CMD_NAME_STR] = 'measureCamera1080pJpegCaptureMs'
    cmd[_CAMERA_ID_STR] = self._camera_id
    self.sock.send(json.dumps(cmd).encode() + '\n'.encode())

    timeout = self.SOCK_TIMEOUT_FOR_PERF_MEASURE
    self.sock.settimeout(timeout)
    data, _ = self.__read_response_from_socket()
    self.sock.settimeout(self.SOCK_TIMEOUT)

    if data[_TAG_STR] != 'camera1080pJpegCaptureMs':
      raise error_util.CameraItsError(
          'Failed to measure camera 1080p jpeg capture latency')
    return float(data[_STR_VALUE_STR])

  def _camera_id_to_props(self):
    """Return the properties of each camera ID."""
    unparsed_ids = self.get_camera_ids().get('cameraIdArray', [])
    parsed_ids = parse_camera_ids(unparsed_ids)
    id_to_props = {}
    for unparsed_id, id_combo in zip(unparsed_ids, parsed_ids):
      if id_combo.sub_id is None:
        props = self.get_camera_properties_by_id(id_combo.id)
      else:
        props = self.get_camera_properties_by_id(id_combo.sub_id)
      id_to_props[unparsed_id] = props
    if not id_to_props:
      raise AssertionError('No camera IDs were found.')
    return id_to_props

  def has_ultrawide_camera(self, facing):
    """Return if device has an ultrawide camera facing the same direction.

    Args:
      facing: constant describing the direction the camera device lens faces.

    Returns:
      True if the device has an ultrawide camera facing in that direction.
    """
    camera_ids = self.get_camera_ids()
    primary_rear_camera_id = camera_ids.get('primaryRearCameraId', '')
    primary_front_camera_id = camera_ids.get('primaryFrontCameraId', '')
    if facing == camera_properties_utils.LENS_FACING['BACK']:
      primary_camera_id = primary_rear_camera_id
    elif facing == camera_properties_utils.LENS_FACING['FRONT']:
      primary_camera_id = primary_front_camera_id
    else:
      raise NotImplementedError('Cameras not facing either front or back '
                                'are currently unsupported.')
    id_to_props = self._camera_id_to_props()
    fov_and_facing = collections.namedtuple('FovAndFacing', ['fov', 'facing'])
    id_to_fov_facing = {
        unparsed_id: fov_and_facing(
            self.calc_camera_fov(props), props['android.lens.facing']
        )
        for unparsed_id, props in id_to_props.items()
    }
    logging.debug('IDs to (FOVs, facing): %s', id_to_fov_facing)
    primary_camera_fov, primary_camera_facing = id_to_fov_facing[
        primary_camera_id]
    for unparsed_id, fov_facing_combo in id_to_fov_facing.items():
      if (float(fov_facing_combo.fov) > float(primary_camera_fov) and
          fov_facing_combo.facing == primary_camera_facing and
          unparsed_id != primary_camera_id):
        logging.debug('Ultrawide camera found with ID %s and FoV %.3f. '
                      'Primary camera has ID %s and FoV: %.3f.',
                      unparsed_id, float(fov_facing_combo.fov),
                      primary_camera_id, float(primary_camera_fov))
        return True
    return False

  def get_facing_to_ids(self):
    """Returns mapping from lens facing to list of corresponding camera IDs."""
    id_to_props = self._camera_id_to_props()
    facing_to_ids = collections.defaultdict(list)
    for unparsed_id, props in id_to_props.items():
      facing_to_ids[props['android.lens.facing']].append(unparsed_id)
    for ids in facing_to_ids.values():
      ids.sort()
    logging.debug('Facing to camera IDs: %s', facing_to_ids)
    return facing_to_ids


def parse_camera_ids(ids):
  """Parse the string of camera IDs into array of CameraIdCombo tuples.

  Args:
   ids: List of camera ids.

  Returns:
   Array of CameraIdCombo
  """
  camera_id_combo = collections.namedtuple('CameraIdCombo', ['id', 'sub_id'])
  id_combos = []
  for one_id in ids:
    one_combo = one_id.split(SUB_CAMERA_SEPARATOR)
    if len(one_combo) == 1:
      id_combos.append(camera_id_combo(one_combo[0], None))
    elif len(one_combo) == 2:
      id_combos.append(camera_id_combo(one_combo[0], one_combo[1]))
    else:
      raise AssertionError('Camera id parameters must be either ID or '
                           f'ID{SUB_CAMERA_SEPARATOR}SUB_ID')
  return id_combos


def do_capture_with_latency(cam, req, sync_latency, fmt=None):
  """Helper function to take enough frames to allow sync latency.

  Args:
    cam: camera object
    req: request for camera
    sync_latency: integer number of frames
    fmt: format for the capture
  Returns:
    single capture with the unsettled frames discarded
  """
  caps = cam.do_capture([req]*(sync_latency+1), fmt)
  return caps[-1]


def load_scene(cam, props, scene, tablet, chart_distance, lighting_check=True,
               log_path=None):
  """Load the scene for the camera based on the FOV.

  Args:
    cam: camera object
    props: camera properties
    scene: scene to be loaded
    tablet: tablet to load scene on
    chart_distance: distance to tablet
    lighting_check: Boolean for lighting check enabled
    log_path: [Optional] path to store artifacts
  """
  if not tablet:
    logging.info('Manual run: no tablet to load scene on.')
    return
  # Calculate camera_fov, which determines the image/video to load on tablet.
  camera_fov = cam.calc_camera_fov(props)
  file_name = cam.get_file_name_to_load(chart_distance, camera_fov, scene)
  if 'scene' not in file_name:
    file_name = f'scene{file_name}'
  if scene in VIDEO_SCENES:
    root_file_name, _ = os.path.splitext(file_name)
    file_name = root_file_name + '.mp4'
  logging.debug('Displaying %s on the tablet', file_name)

  # Display the image/video on the tablet using the default media player.
  view_file_type = 'image/png' if scene not in VIDEO_SCENES else 'video/mp4'
  uri_prefix = 'file://mnt' if scene not in VIDEO_SCENES else ''
  tablet.adb.shell(
      f'am start -a android.intent.action.VIEW -t {view_file_type} '
      f'-d {uri_prefix}/sdcard/Download/{file_name}')
  time.sleep(LOAD_SCENE_DELAY_SEC)
  rfov_camera_in_rfov_box = (
      math.isclose(
          chart_distance,
          opencv_processing_utils.CHART_DISTANCE_31CM, rel_tol=0.1) and
      opencv_processing_utils.FOV_THRESH_TELE <= float(camera_fov)
      <= opencv_processing_utils.FOV_THRESH_UW)
  wfov_camera_in_wfov_box = (
      math.isclose(
          chart_distance,
          opencv_processing_utils.CHART_DISTANCE_22CM, rel_tol=0.1) and
      float(camera_fov) > opencv_processing_utils.FOV_THRESH_UW)
  if (rfov_camera_in_rfov_box or wfov_camera_in_wfov_box) and lighting_check:
    cam.do_3a()
    cap = cam.do_capture(
        capture_request_utils.auto_capture_request(), cam.CAP_YUV)
    y_plane, _, _ = image_processing_utils.convert_capture_to_planes(cap)
    validate_lighting(y_plane, scene, log_path=log_path)


def validate_lighting(y_plane, scene, state='ON', log_path=None,
                      tablet_state='ON'):
  """Validates the lighting level in scene corners based on empirical values.

  Args:
    y_plane: Y plane of YUV image
    scene: scene name
    state: string 'ON' or 'OFF'
    log_path: [Optional] path to store artifacts
    tablet_state: string 'ON' or 'OFF'

  Returns:
    boolean True if lighting validated, else raise AssertionError
  """
  logging.debug('Validating lighting levels.')
  file_name = f'validate_lighting_{scene}.jpg'
  if log_path:
    file_name = os.path.join(log_path, f'validate_lighting_{scene}.jpg')

  if tablet_state == 'OFF':
    validate_lighting_thresh = _VALIDATE_LIGHTING_THRESH_DARK
  else:
    validate_lighting_thresh = _VALIDATE_LIGHTING_THRESH

  # Test patches from each corner.
  for location, coordinates in _VALIDATE_LIGHTING_REGIONS.items():
    patch = image_processing_utils.get_image_patch(
        y_plane, coordinates[0], coordinates[1],
        _VALIDATE_LIGHTING_PATCH_W, _VALIDATE_LIGHTING_PATCH_H)
    y_mean = image_processing_utils.compute_image_means(patch)[0]
    logging.debug('%s corner Y mean: %.3f', location, y_mean)
    if state == 'ON':
      if y_mean > validate_lighting_thresh:
        logging.debug('Lights ON in test rig.')
        return True
      else:
        image_processing_utils.write_image(y_plane, file_name)
        raise AssertionError('Lights OFF in test rig. Turn ON and retry.')
    elif state == 'OFF':
      if y_mean < validate_lighting_thresh:
        logging.debug('Lights OFF in test rig.')
        return True
      else:
        image_processing_utils.write_image(y_plane, file_name)
        raise AssertionError('Lights ON in test rig. Turn OFF and retry.')
    else:
      raise AssertionError('Invalid lighting state string. '
                           "Valid strings: 'ON', 'OFF'.")


def get_build_sdk_version(device_id):
  """Return the int build version of the device."""
  cmd = f'adb -s {device_id} shell getprop ro.build.version.sdk'
  try:
    build_sdk_version = int(subprocess.check_output(cmd.split()).rstrip())
    logging.debug('Build SDK version: %d', build_sdk_version)
  except (subprocess.CalledProcessError, ValueError) as exp_errors:
    raise AssertionError('No build_sdk_version.') from exp_errors
  return build_sdk_version


def get_first_api_level(device_id):
  """Return the int value for the first API level of the device."""
  cmd = f'adb -s {device_id} shell getprop ro.product.first_api_level'
  try:
    first_api_level = int(subprocess.check_output(cmd.split()).rstrip())
    logging.debug('First API level: %d', first_api_level)
  except (subprocess.CalledProcessError, ValueError):
    logging.error('No first_api_level. Setting to build version.')
    first_api_level = get_build_sdk_version(device_id)
  return first_api_level


def get_vendor_api_level(device_id):
  """Return the int value for the vendor API level of the device."""
  cmd = f'adb -s {device_id} shell getprop ro.vendor.api_level'
  try:
    vendor_api_level = int(subprocess.check_output(cmd.split()).rstrip())
    logging.debug('First vendor API level: %d', vendor_api_level)
  except (subprocess.CalledProcessError, ValueError):
    logging.error('No vendor_api_level. Setting to build version.')
    vendor_api_level = get_build_sdk_version(device_id)
  return vendor_api_level


def get_media_performance_class(device_id):
  """Return the int value for the media performance class of the device."""
  cmd = (f'adb -s {device_id} shell '
         'getprop ro.odm.build.media_performance_class')
  try:
    media_performance_class = int(
        subprocess.check_output(cmd.split()).rstrip())
    logging.debug('Media performance class: %d', media_performance_class)
  except (subprocess.CalledProcessError, ValueError):
    logging.debug('No media performance class. Setting to 0.')
    media_performance_class = 0
  return media_performance_class


def raise_mpc_assertion_error(required_mpc, test_name, found_mpc):
  raise AssertionError(f'With MPC >= {required_mpc}, {test_name} must be run. '
                       f'Found MPC: {found_mpc}')


def stop_video_playback(tablet):
  """Force-stop activities used for video playback on the tablet.

  Args:
    tablet: a controller object for the ITS tablet.
  """
  try:
    activities_unencoded = tablet.adb.shell(
        ['dumpsys', 'activity', 'recents', '|',
         'grep', '"baseIntent=Intent.*act=android.intent.action"']
    )
  except adb.AdbError as e:
    logging.warning('ADB error when finding intent activities: %s. '
                    'Please close the default video player manually.', e)
    return
  activity_lines = (
      str(activities_unencoded.decode('utf-8')).strip().splitlines()
  )
  for activity_line in activity_lines:
    activity = activity_line.split('cmp=')[-1].split('/')[0]
    try:
      tablet.adb.shell(['am', 'force-stop', activity])
    except adb.AdbError as e:
      logging.warning('ADB error when killing intent activity %s: %s. '
                      'Please close the default video player manually.',
                      activity, e)


def raise_not_yet_mandated_error(message, api_level, mandated_api_level):
  if api_level >= mandated_api_level:
    raise AssertionError(
        f'Test is mandated for API level {mandated_api_level} or above. '
        f'Found API level {api_level}.\n\n{message}'
    )
  else:
    raise AssertionError(f'{NOT_YET_MANDATED_MESSAGE}\n\n{message}')
