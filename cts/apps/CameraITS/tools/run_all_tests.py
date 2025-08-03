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

import glob
import json
import logging
import os
import os.path
import re
import subprocess
import sys
import tempfile
import time
import types

import camera_properties_utils
import capture_request_utils
import image_processing_utils
import its_device_utils
import its_session_utils
import lighting_control_utils
import numpy as np
import yaml


YAML_FILE_DIR = os.environ['CAMERA_ITS_TOP']
CONFIG_FILE = os.path.join(YAML_FILE_DIR, 'config.yml')
TEST_KEY_TABLET = 'tablet'
TEST_KEY_SENSOR_FUSION = 'sensor_fusion'
LOAD_SCENE_DELAY = 1  # seconds
ACTIVITY_START_WAIT = 1.5  # seconds
MERGE_RESULTS_TIMEOUT = 3600  # seconds

NUM_TRIES = 2
RESULT_PASS = 'PASS'
RESULT_FAIL = 'FAIL'
RESULT_NOT_EXECUTED = 'NOT_EXECUTED'
RESULT_KEY = 'result'
METRICS_KEY = 'mpc_metrics'
PERFORMANCE_KEY = 'performance_metrics'
SUMMARY_KEY = 'summary'
RESULT_VALUES = (RESULT_PASS, RESULT_FAIL, RESULT_NOT_EXECUTED)
CTS_VERIFIER_PACKAGE_NAME = 'com.android.cts.verifier'
ACTION_ITS_RESULT = 'com.android.cts.verifier.camera.its.ACTION_ITS_RESULT'
EXTRA_VERSION = 'camera.its.extra.VERSION'
CURRENT_ITS_VERSION = '1.0'  # version number to sync with CtsVerifier
EXTRA_CAMERA_ID = 'camera.its.extra.CAMERA_ID'
EXTRA_RESULTS = 'camera.its.extra.RESULTS'
TIME_KEY_START = 'start'
TIME_KEY_END = 'end'
VALID_CONTROLLERS = ('arduino', 'canakit')
_FRONT_CAMERA_ID = '1'
# recover replaced '_' in scene def
_INT_STR_DICT = types.MappingProxyType({'11': '1_1', '12': '1_2'})
_MAIN_TESTBED = 0
_PROPERTIES_TO_MATCH = (
    'ro.product.model', 'ro.product.name', 'ro.build.display.id', 'ro.revision'
)

# Scenes that can be automated through tablet display
# Notes on scene names:
#   scene*_1/2/... are same scene split to load balance run times for scenes
#   scene*_a/b/... are similar scenes that share one or more tests
_TABLET_SCENES = (
    'scene0', 'scene1_1', 'scene1_2', 'scene2_a', 'scene2_b', 'scene2_c',
    'scene2_d', 'scene2_e', 'scene2_f', 'scene3', 'scene4', 'scene6', 'scene7',
    os.path.join('scene_extensions', 'scene_hdr'),
    os.path.join('scene_extensions', 'scene_night'),
    'scene_video',
)

# Scenes that use the 'sensor_fusion' test rig
_MOTION_SCENES = ('sensor_fusion',)

# Scenes that uses lighting control
_FLASH_SCENES = ('scene_flash',)

# Scenes that uses checkerboard as chart
_CHECKERBOARD_SCENES = ('sensor_fusion', 'scene_flash',)

# Scenes that have to be run manually regardless of configuration
_MANUAL_SCENES = ('scene5',)

# Scene extensions
_EXTENSIONS_SCENES = (os.path.join('scene_extensions', 'scene_hdr'),
                      os.path.join('scene_extensions', 'scene_night'),
                      )

# All possible scenes
_ALL_SCENES = _TABLET_SCENES + _MANUAL_SCENES + _MOTION_SCENES + _FLASH_SCENES

# Scenes that are logically grouped and can be called as group
_GROUPED_SCENES = types.MappingProxyType({
        'scene1': ('scene1_1', 'scene1_2'),
        'scene2': ('scene2_a', 'scene2_b', 'scene2_c', 'scene2_d', 'scene2_e',
                   'scene2_f')
})

# Scene requirements for manual testing.
_SCENE_REQ = types.MappingProxyType({
    'scene0': None,
    'scene1_1': 'A grey card covering at least the middle 30% of the scene',
    'scene1_2': 'A grey card covering at least the middle 30% of the scene',
    'scene2_a': 'The picture with 3 faces in tests/scene2_a/scene2_a.png',
    'scene2_b': 'The picture with 3 faces in tests/scene2_b/scene2_b.png',
    'scene2_c': 'The picture with 3 faces in tests/scene2_c/scene2_c.png',
    'scene2_d': 'The picture with 3 faces in tests/scene2_d/scene2_d.png',
    'scene2_e': 'The picture with 3 faces in tests/scene2_e/scene2_e.png',
    'scene2_f': 'The picture with 3 faces in tests/scene2_f/scene2_f.png',
    'scene3': 'The ISO12233 chart',
    'scene4': 'A test chart of a circle covering at least the middle 50% of '
              'the scene. See tests/scene4/scene4.png',
    'scene5': 'Capture images with a diffuser attached to the camera. See '
              'source.android.com/docs/compatibility/cts/camera-its-tests#scene5/diffuser '  # pylint: disable line-too-long
              'for more details',
    'scene6': 'A grid of black circles on a white background. '
              'See tests/scene6/scene6.png',
    'scene7': 'The picture with 4 different colors, slanted edge and'
              '4 ArUco markers. See tests/scene7/scene7.png',
    # Use os.path to avoid confusion on other platforms
    os.path.join('scene_extensions', 'scene_hdr'): (
        'A tablet displayed scene with a face on the left '
        'and a low-contrast QR code on the right. '
        'See tests/scene_extensions/scene_hdr/scene_hdr.png'
    ),
    os.path.join('scene_extensions', 'scene_night'): (
        'A tablet displayed scene with a white circle '
        'and four smaller circles inside of it. '
        'See tests/scene_extensions/scene_night/scene_night.png'
    ),
    'sensor_fusion': 'A checkerboard pattern for phone to rotate in front of '
                     'in tests/sensor_fusion/checkerboard.pdf\n'
                     'See tests/sensor_fusion/SensorFusion.pdf for detailed '
                     'instructions.\nNote that this test will be skipped '
                     'on devices not supporting REALTIME camera timestamp.',
    'scene_flash': 'A checkerboard pattern chart with lights off.',
    'scene_video': 'A tablet displayed scene with a series of circles moving '
                   'at different simulated frame rates. '
                   'See tests/scene_video/scene_video.mp4',
})

SUB_CAMERA_TESTS = types.MappingProxyType({
    'scene0': (
        'test_jitter',
        'test_metadata',
        'test_request_capture_match',
        'test_sensor_events',
        'test_solid_color_test_pattern',
        'test_unified_timestamps',
    ),
    'scene1_1': (
        'test_burst_capture',
        'test_burst_sameness_manual',
        'test_dng_noise_model',
        'test_exposure_x_iso',
        'test_linearity',
    ),
    'scene1_2': (
        'test_raw_exposure',
        'test_raw_sensitivity',
        'test_yuv_jpeg_all',
        'test_yuv_plus_raw',
    ),
    'scene2_a': (
        'test_num_faces',
    ),
    'scene2_b': (
        'test_yuv_jpeg_capture_sameness',
    ),
    'scene4': (
        'test_aspect_ratio_and_crop',
    ),
    'scene_video': (
        'test_preview_frame_drop',
    ),
    'sensor_fusion': (
        'test_sensor_fusion',
    ),
})

_LIGHTING_CONTROL_TESTS = (
    'test_auto_flash.py',
    'test_preview_min_frame_rate.py',
    'test_led_snapshot.py',
    'test_night_extension.py',
    'test_hdr_extension.py',
    )

_DST_SCENE_DIR = '/sdcard/Download/'
MOBLY_TEST_SUMMARY_TXT_FILE = 'test_mobly_summary.txt'


def report_result(device_id, camera_id, results):
  """Sends a pass/fail result to the device, via an intent.

  Args:
   device_id: The ID string of the device to report the results to.
   camera_id: The ID string of the camera for which to report pass/fail.
   results: a dictionary contains all ITS scenes as key and result/summary of
            current ITS run. See test_report_result unit test for an example.
  """
  adb = f'adb -s {device_id}'
  its_device_utils.start_its_test_activity(device_id)
  time.sleep(ACTIVITY_START_WAIT)

  # Validate/process results argument
  for scene in results:
    if RESULT_KEY not in results[scene]:
      raise ValueError(f'ITS result not found for {scene}')
    if results[scene][RESULT_KEY] not in RESULT_VALUES:
      raise ValueError(f'Unknown ITS result for {scene}: {results[RESULT_KEY]}')
    if SUMMARY_KEY in results[scene]:
      device_summary_path = f'/sdcard/its_camera{camera_id}_{scene}.txt'
      its_device_utils.run(
          f'{adb} push {results[scene][SUMMARY_KEY]} {device_summary_path}')
      results[scene][SUMMARY_KEY] = device_summary_path

  json_results = json.dumps(results)
  cmd = (f"{adb} shell am broadcast -a {ACTION_ITS_RESULT} --es {EXTRA_VERSION}"
         f" {CURRENT_ITS_VERSION} --es {EXTRA_CAMERA_ID} {camera_id} --es "
         f"{EXTRA_RESULTS} \'{json_results}\'")
  its_device_utils.run(cmd)


def write_result(testbed_index, device_id, camera_id, results):
  """Writes results to a temporary file for merging.

  Args:
    testbed_index: the index of a finished testbed.
    device_id: the ID string of the device that created results.
    camera_id: the ID string of the camera of the device.
    results: a dictionary that contains all ITS scenes as key
             and result/summary of current ITS run.
  """
  result = {'device_id': device_id, 'results': results}
  file_name = f'testbed_{testbed_index}_camera_{camera_id}.tmp'
  with open(file_name, 'w') as f:
    json.dump(result, f)


def parse_testbeds(completed_testbeds):
  """Parses completed testbeds and yields device_id, camera_id, and results.

  Args:
    completed_testbeds: an iterable of completed testbed indices.
  Yields:
    device_id: the device associated with the testbed.
    camera_id: one of the camera_ids associated with the testbed.
    results: the dictionary with scenes and result/summary of testbed's run.
  """
  for i in completed_testbeds:
    for file_name in glob.glob(f'testbed_{i}_camera_*.tmp'):
      camera_id = file_name.split('camera_')[1].split('.tmp')[0]
      device_id = ''
      results = {}
      with open(file_name, 'r') as f:
        testbed_data = json.load(f)
        device_id = testbed_data['device_id']
        results = testbed_data['results']
      if not device_id or not results:
        raise ValueError(f'device_id or results for {file_name} not found.')
      yield device_id, camera_id, results


def get_device_property(device_id, property_name):
  """Get property of a given device.

  Args:
    device_id: the ID string of a device.
    property_name: the desired property string.
  Returns:
    The value of the property.
  """
  property_cmd = f'adb -s {device_id} shell getprop {property_name}'
  raw_output = subprocess.check_output(
      property_cmd, stderr=subprocess.STDOUT, shell=True)
  return str(raw_output.decode('utf-8')).strip()


def are_devices_similar(device_id_1, device_id_2):
  """Checks if key dimensions are the same between devices.

  Args:
    device_id_1: the ID string of the _MAIN_TESTBED device.
    device_id_2: the ID string of another device.
  Returns:
    True if both devices share key dimensions.
  """
  for property_to_match in _PROPERTIES_TO_MATCH:
    property_value_1 = get_device_property(device_id_1, property_to_match)
    property_value_2 = get_device_property(device_id_2, property_to_match)
    if property_value_1 != property_value_2:
      logging.error('%s does not match %s for %s',
                    property_value_1, property_value_2, property_to_match)
      return False
  return True


def load_scenes_on_tablet(scene, tablet_id):
  """Copies scenes onto the tablet before running the tests.

  Args:
    scene: Name of the scene to copy image files.
    tablet_id: adb id of tablet
  """
  logging.info('Copying files to tablet: %s', tablet_id)
  scene_dir = os.listdir(
      os.path.join(os.environ['CAMERA_ITS_TOP'], 'tests', scene))
  for file_name in scene_dir:
    if file_name.endswith('.png') or file_name.endswith('.mp4'):
      src_scene_file = os.path.join(os.environ['CAMERA_ITS_TOP'], 'tests',
                                    scene, file_name)
      cmd = f'adb -s {tablet_id} push {src_scene_file} {_DST_SCENE_DIR}'
      subprocess.Popen(cmd.split())
  time.sleep(LOAD_SCENE_DELAY)
  logging.info('Finished copying files to tablet.')


def check_manual_scenes(device_id, camera_id, scene, out_path):
  """Halt run to change scenes.

  Args:
    device_id: id of device
    camera_id: id of camera
    scene: Name of the scene to copy image files.
    out_path: output file location
  """
  with its_session_utils.ItsSession(
      device_id=device_id,
      camera_id=camera_id) as cam:
    props = cam.get_camera_properties()
    props = cam.override_with_hidden_physical_camera_props(props)

    while True:
      input(f'\n Press <ENTER> after positioning camera {camera_id} with '
            f'{scene}.\n The scene setup should be: \n  {_SCENE_REQ[scene]}\n')
      # Converge 3A prior to capture
      if scene == 'scene5':
        cam.do_3a(do_af=False, lock_ae=camera_properties_utils.ae_lock(props),
                  lock_awb=camera_properties_utils.awb_lock(props))
      else:
        cam.do_3a()
      req, fmt = capture_request_utils.get_fastest_auto_capture_settings(props)
      logging.info('Capturing an image to check the test scene')
      cap = cam.do_capture(req, fmt)
      img = image_processing_utils.convert_capture_to_rgb_image(cap)
      img_name = os.path.join(out_path, f'test_{scene}.jpg')
      logging.info('Please check scene setup in %s', img_name)
      image_processing_utils.write_image(img, img_name)
      choice = input(f'Is the image okay for ITS {scene}? (Y/N)').lower()
      if choice == 'y':
        break


def get_config_file_contents():
  """Read the config file contents from a YML file.

  Args:
    None

  Returns:
    config_file_contents: a dict read from config.yml
  """
  with open(CONFIG_FILE) as file:
    config_file_contents = yaml.safe_load(file)
  return config_file_contents


def get_test_params(config_file_contents):
  """Reads the config file parameters.

  Args:
    config_file_contents: dict read from config.yml file

  Returns:
    dict of test parameters
  """
  test_params = None
  for _, j in config_file_contents.items():
    for datadict in j:
      test_params = datadict.get('TestParams')
  return test_params


def get_device_serial_number(device, config_file_contents):
  """Returns the serial number of the device with label from the config file.

  The config file contains TestBeds dictionary which contains Controllers and
  Android Device dicts.The two devices used by the test per box are listed
  here labels dut and tablet. Parse through the nested TestBeds dict to get
  the Android device details.

  Args:
    device: String device label as specified in config file.dut/tablet
    config_file_contents: dict read from config.yml file
  """

  for _, j in config_file_contents.items():
    for datadict in j:
      android_device_contents = datadict.get('Controllers')
      for device_dict in android_device_contents.get('AndroidDevice'):
        for _, label in device_dict.items():
          if label == 'tablet':
            tablet_device_id = str(device_dict.get('serial'))
          if label == 'dut':
            dut_device_id = str(device_dict.get('serial'))
  if device == 'tablet':
    return tablet_device_id
  else:
    return dut_device_id


def get_updated_yml_file(yml_file_contents):
  """Create a new yml file and write the testbed contents in it.

  This testbed file is per box and contains all the parameters and
  device id used by the mobly tests.

  Args:
   yml_file_contents: Data to write in yml file.

  Returns:
    Updated yml file contents.
  """
  os.chmod(YAML_FILE_DIR, 0o755)
  file_descriptor, new_yaml_file = tempfile.mkstemp(
      suffix='.yml', prefix='config_', dir=YAML_FILE_DIR)
  os.close(file_descriptor)
  with open(new_yaml_file, 'w') as f:
    yaml.dump(yml_file_contents, stream=f, default_flow_style=False)
  new_yaml_file_name = os.path.basename(new_yaml_file)
  return new_yaml_file_name


def enable_external_storage(device_id):
  """Override apk mode to allow write to external storage.

  Args:
    device_id: Serial number of the device.

  """
  cmd = (f'adb -s {device_id} shell appops '
         'set com.android.cts.verifier MANAGE_EXTERNAL_STORAGE allow')
  its_device_utils.run(cmd)


def get_available_cameras(device_id, camera_id):
  """Get available camera devices in the current state.

  Args:
    device_id: Serial number of the device.
    camera_id: Logical camera_id

  Returns:
    List of all the available camera_ids.
  """
  with its_session_utils.ItsSession(
      device_id=device_id,
      camera_id=camera_id) as cam:
    props = cam.get_camera_properties()
    props = cam.override_with_hidden_physical_camera_props(props)
    unavailable_physical_cameras = cam.get_unavailable_physical_cameras(
        camera_id)
    unavailable_physical_ids = unavailable_physical_cameras[
        'unavailablePhysicalCamerasArray']
    output = cam.get_camera_ids()
    all_camera_ids = output['cameraIdArray']
    # Concat camera_id, physical camera_id and sub camera separator
    unavailable_physical_ids = [f'{camera_id}.{s}'
                                for s in unavailable_physical_ids]
    for i in unavailable_physical_ids:
      if i in all_camera_ids:
        all_camera_ids.remove(i)
    logging.debug('available camera ids: %s', all_camera_ids)
  return all_camera_ids


def get_unavailable_physical_cameras(device_id, camera_id):
  """Get unavailable physical cameras in the current state.

  Args:
    device_id: Serial number of the device.
    camera_id: Logical camera device id

  Returns:
    List of all the unavailable camera_ids.
  """
  with its_session_utils.ItsSession(
      device_id=device_id,
      camera_id=camera_id) as cam:
    unavailable_physical_cameras = cam.get_unavailable_physical_cameras(
        camera_id)
    unavailable_physical_ids = unavailable_physical_cameras[
        'unavailablePhysicalCamerasArray']
    unavailable_physical_ids = [f'{camera_id}.{s}'
                                for s in unavailable_physical_ids]
    logging.debug('Unavailable physical camera ids: %s',
                  unavailable_physical_ids)
  return unavailable_physical_ids


def is_device_folded(device_id):
  """Returns True if the foldable device is in folded state.

  Args:
    device_id: Serial number of the foldable device.
  """
  cmd = (f'adb -s {device_id} shell cmd device_state state')
  result = subprocess.getoutput(cmd)
  if 'CLOSE' in result:
    return True
  return False


def main():
  """Run all the Camera ITS automated tests.

    Script should be run from the top-level CameraITS directory.

    Command line arguments:
        camera:  the camera(s) to be tested. Use comma to separate multiple
                 camera Ids. Ex: "camera=0,1" or "camera=1"
        scenes:  the test scene(s) to be executed. Use comma to separate
                 multiple scenes. Ex: "scenes=scene0,scene1_1" or
                 "scenes=0,1_1,sensor_fusion" (sceneX can be abbreviated by X
                 where X is scene name minus 'scene')
  """
  logging.basicConfig(level=logging.INFO)
  # Make output directories to hold the generated files.
  topdir = tempfile.mkdtemp(prefix='CameraITS_')
  try:
    subprocess.call(['chmod', 'g+rx', topdir])
  except OSError as e:
    logging.info(repr(e))

  scenes = []
  camera_id_combos = []
  testbed_index = None
  num_testbeds = None
  # Override camera, scenes and testbed with cmd line values if available
  for s in list(sys.argv[1:]):
    if 'scenes=' in s:
      scenes = s.split('=')[1].split(',')
    elif 'camera=' in s:
      camera_id_combos = s.split('=')[1].split(',')
    elif 'testbed_index=' in s:
      testbed_index = int(s.split('=')[1])
    elif 'num_testbeds=' in s:
      num_testbeds = int(s.split('=')[1])
    else:
      raise ValueError(f'Unknown argument {s}')
  if testbed_index is None and num_testbeds is not None:
    raise ValueError(
        'testbed_index must be specified if num_testbeds is specified.')
  if (testbed_index is not None and num_testbeds is not None and
      testbed_index >= num_testbeds):
    raise ValueError('testbed_index must be less than num_testbeds. '
                     'testbed_index starts at 0.')

  # Prepend 'scene' if not specified at cmd line
  for i, s in enumerate(scenes):
    if (not s.startswith('scene') and
        not s.startswith(('checkerboard', 'sensor_fusion',
                          'flash', '<scene-name>'))):
      scenes[i] = f'scene{s}'
    if s.startswith('flash') or s.startswith('extensions'):
      scenes[i] = f'scene_{s}'
    # Handle scene_extensions
    if s.startswith('hdr') or s.startswith('night'):
      scenes[i] = f'scene_extensions/scene_{s}'
    if s.startswith('scene_hdr') or s.startswith('scene_night'):
      scenes[i] = f'scene_extensions/{s}'

  # Read config file and extract relevant TestBed
  config_file_contents = get_config_file_contents()
  if testbed_index is None:
    for i in config_file_contents['TestBeds']:
      if (scenes == ['sensor_fusion'] or scenes == ['checkerboard'] or
          scenes == ['scene_flash']):
        if TEST_KEY_SENSOR_FUSION not in i['Name'].lower():
          config_file_contents['TestBeds'].remove(i)
      else:
        if TEST_KEY_SENSOR_FUSION in i['Name'].lower():
          config_file_contents['TestBeds'].remove(i)
  else:
    config_file_contents = {
        'TestBeds': [config_file_contents['TestBeds'][testbed_index]]
    }

  # Get test parameters from config file
  test_params_content = get_test_params(config_file_contents)
  if not camera_id_combos:
    camera_id_combos = str(test_params_content['camera']).split(',')
  if not scenes:
    scenes = str(test_params_content['scene']).split(',')
    scenes = [_INT_STR_DICT.get(n, n) for n in scenes]  # recover '1_1' & '1_2'

  device_id = get_device_serial_number('dut', config_file_contents)
  # Enable external storage on DUT to send summary report to CtsVerifier.apk
  enable_external_storage(device_id)

  # Verify that CTS Verifier is installed
  its_session_utils.check_apk_installed(device_id, CTS_VERIFIER_PACKAGE_NAME)
  # Check whether the dut is foldable or not
  testing_foldable_device = True if test_params_content[
      'foldable_device'] == 'True' else False
  available_camera_ids_to_test_foldable = []
  if testing_foldable_device:
    logging.debug('Testing foldable device.')
    # Check the state of foldable device. True if device is folded,
    # false if the device is opened.
    device_folded = is_device_folded(device_id)
    # list of available camera_ids to be tested in device state
    available_camera_ids_to_test_foldable = get_available_cameras(
        device_id, _FRONT_CAMERA_ID)

  config_file_test_key = config_file_contents['TestBeds'][0]['Name'].lower()
  logging.info('Saving %s output files to: %s', config_file_test_key, topdir)
  if TEST_KEY_TABLET in config_file_test_key:
    tablet_id = get_device_serial_number('tablet', config_file_contents)
    tablet_name_cmd = f'adb -s {tablet_id} shell getprop ro.build.product'
    raw_output = subprocess.check_output(
        tablet_name_cmd, stderr=subprocess.STDOUT, shell=True)
    tablet_name = str(raw_output.decode('utf-8')).strip()
    logging.debug('Tablet name: %s', tablet_name)
    brightness = test_params_content['brightness']
    its_session_utils.validate_tablet_brightness(tablet_name, brightness)
  else:
    tablet_id = None

  testing_sensor_fusion_with_controller = False
  if TEST_KEY_SENSOR_FUSION in config_file_test_key:
    if test_params_content['rotator_cntl'].lower() in VALID_CONTROLLERS:
      testing_sensor_fusion_with_controller = True

  testing_flash_with_controller = False
  if (test_params_content.get('lighting_cntl', 'None').lower() == 'arduino' and
      'manual' not in config_file_test_key):
    testing_flash_with_controller = True

  # Expand GROUPED_SCENES and remove any duplicates
  scenes = [_GROUPED_SCENES[s] if s in _GROUPED_SCENES else s for s in scenes]
  scenes = np.hstack(scenes).tolist()
  scenes = sorted(set(scenes), key=scenes.index)
  # List of scenes to be executed in folded state will have '_folded'
  # prefix. This will help distinguish the test results from folded vs
  # open device state for front camera_ids.
  folded_device_scenes = []
  for scene in scenes:
    folded_device_scenes.append(f'{scene}_folded')

  logging.info('Running ITS on device: %s, camera(s): %s, scene(s): %s',
               device_id, camera_id_combos, scenes)

  # Determine if manual run
  if tablet_id is not None and not set(scenes).intersection(_MANUAL_SCENES):
    auto_scene_switch = True
  else:
    auto_scene_switch = False
    logging.info('Manual, checkerboard scenes, or scene5 testing.')

  folded_prompted = False
  opened_prompted = False
  for camera_id in camera_id_combos:
    test_params_content['camera'] = camera_id
    results = {}
    unav_cameras = []
    # Get the list of unavailable cameras in current device state.
    # These camera_ids should not be tested in current device state.
    if testing_foldable_device:
      unav_cameras = get_unavailable_physical_cameras(
          device_id, _FRONT_CAMERA_ID)

    if testing_foldable_device:
      device_state = 'folded' if device_folded else 'opened'

    testing_folded_front_camera = (testing_foldable_device and
                                   device_folded and
                                   _FRONT_CAMERA_ID in camera_id)

    # Raise an assertion error if there is any camera unavailable in
    # current device state. Usually scenes with suffix 'folded' will
    # be executed in folded state.
    if (testing_foldable_device and
        _FRONT_CAMERA_ID in camera_id and camera_id in unav_cameras):
      raise AssertionError(
          f'Camera {camera_id} is unavailable in device state {device_state}'
          f' and cannot be tested with device {device_state}!')

    if (testing_folded_front_camera and camera_id not in unav_cameras
        and not folded_prompted):
      folded_prompted = True
      input('\nYou are testing a foldable device in folded state. '
            'Please make sure the device is folded and press <ENTER> '
            'after positioning properly.\n')

    if (testing_foldable_device and
        not device_folded and _FRONT_CAMERA_ID in camera_id and
        camera_id not in unav_cameras and not opened_prompted):
      opened_prompted = True
      input('\nYou are testing a foldable device in opened state. '
            'Please make sure the device is unfolded and press <ENTER> '
            'after positioning properly.\n')

    # Run through all scenes if user does not supply one and config file doesn't
    # have specific scene name listed.
    if its_session_utils.SUB_CAMERA_SEPARATOR in camera_id:
      possible_scenes = list(SUB_CAMERA_TESTS.keys())
      if auto_scene_switch:
        possible_scenes.remove('sensor_fusion')
    else:
      if 'checkerboard' in scenes:
        possible_scenes = _CHECKERBOARD_SCENES
      elif 'scene_flash' in scenes:
        possible_scenes = _FLASH_SCENES
      elif 'scene_extensions' in scenes:
        possible_scenes = _EXTENSIONS_SCENES
      else:
        possible_scenes = _TABLET_SCENES if auto_scene_switch else _ALL_SCENES

    if ('<scene-name>' in scenes or 'checkerboard' in scenes or
        'scene_extensions' in scenes):
      per_camera_scenes = possible_scenes
    else:
      # Validate user input scene names
      per_camera_scenes = []
      for s in scenes:
        if s in possible_scenes:
          per_camera_scenes.append(s)
      if not per_camera_scenes:
        raise ValueError('No valid scene specified for this camera.')

    # Folded state scenes will have 'folded' suffix only for
    # front cameras since rear cameras are common in both folded
    # and unfolded state.
    foldable_per_camera_scenes = []
    if testing_folded_front_camera:
      if camera_id not in available_camera_ids_to_test_foldable:
        raise AssertionError(f'camera {camera_id} is not available.')
      for s in per_camera_scenes:
        foldable_per_camera_scenes.append(f'{s}_folded')

    if foldable_per_camera_scenes:
      per_camera_scenes = foldable_per_camera_scenes

    logging.info('camera: %s, scene(s): %s', camera_id, per_camera_scenes)

    if testing_folded_front_camera:
      all_scenes = [f'{s}_folded' for s in _ALL_SCENES]
    else:
      all_scenes = _ALL_SCENES

    for s in all_scenes:
      results[s] = {RESULT_KEY: RESULT_NOT_EXECUTED}

      # assert device folded testing scenes with suffix 'folded'
      if testing_foldable_device and 'folded' in s:
        if not device_folded:
          raise AssertionError('Device should be folded during'
                               ' testing scenes with suffix "folded"')

    # A subdir in topdir will be created for each camera_id. All scene test
    # output logs for each camera id will be stored in this subdir.
    # This output log path is a mobly param : LogPath
    camera_id_str = (
        camera_id.replace(its_session_utils.SUB_CAMERA_SEPARATOR, '_')
    )
    mobly_output_logs_path = os.path.join(topdir, f'cam_id_{camera_id_str}')
    os.mkdir(mobly_output_logs_path)
    tot_pass = 0
    for s in per_camera_scenes:
      results[s]['TEST_STATUS'] = []
      results[s][METRICS_KEY] = []
      results[s][PERFORMANCE_KEY] = []

      # unit is millisecond for execution time record in CtsVerifier
      scene_start_time = int(round(time.time() * 1000))
      scene_test_summary = f'Cam{camera_id} {s}' + '\n'
      mobly_scene_output_logs_path = os.path.join(mobly_output_logs_path, s)

      # Since test directories do not have 'folded' in the name, we need
      # to remove that suffix for the path of the scenes to be loaded
      # on the tablets
      testing_scene = s
      if 'folded' in s:
        testing_scene = s.split('_folded')[0]
      test_params_content['scene'] = testing_scene
      test_params_content['scene_with_suffix'] = s

      if auto_scene_switch:
        # Copy scene images onto the tablet
        if 'scene0' not in testing_scene:
          load_scenes_on_tablet(testing_scene, tablet_id)
      else:
        # Check manual scenes for correctness
        if ('scene0' not in testing_scene and
            not testing_sensor_fusion_with_controller):
          check_manual_scenes(device_id, camera_id, testing_scene,
                              mobly_output_logs_path)

      scene_test_list = []
      config_file_contents['TestBeds'][0]['TestParams'] = test_params_content
      # Add the MoblyParams to config.yml file with the path to store camera_id
      # test results. This is a separate dict other than TestBeds.
      mobly_params_dict = {
          'MoblyParams': {
              'LogPath': mobly_scene_output_logs_path
          }
      }
      config_file_contents.update(mobly_params_dict)
      logging.debug('Final config file contents: %s', config_file_contents)
      new_yml_file_name = get_updated_yml_file(config_file_contents)
      logging.info('Using %s as temporary config yml file', new_yml_file_name)
      if camera_id.rfind(its_session_utils.SUB_CAMERA_SEPARATOR) == -1:
        scene_dir = os.listdir(
            os.path.join(os.environ['CAMERA_ITS_TOP'], 'tests', testing_scene))
        for file_name in scene_dir:
          if file_name.endswith('.py') and 'test' in file_name:
            scene_test_list.append(file_name)
      else:  # sub-camera
        if SUB_CAMERA_TESTS.get(testing_scene):
          scene_test_list = [f'{test}.py' for test in SUB_CAMERA_TESTS[
              testing_scene]]
        else:
          scene_test_list = []
      scene_test_list.sort()

      # Run tests for scene
      logging.info('Running tests for %s with camera %s',
                   testing_scene, camera_id)
      num_pass = 0
      num_skip = 0
      num_not_mandated_fail = 0
      num_fail = 0
      for test in scene_test_list:
        # Handle repeated test
        if 'tests/' in test:
          cmd = [
              'python3',
              os.path.join(os.environ['CAMERA_ITS_TOP'], test), '-c',
              f'{new_yml_file_name}'
          ]
        else:
          cmd = [
              'python3',
              os.path.join(os.environ['CAMERA_ITS_TOP'], 'tests',
                           testing_scene, test),
              '-c',
              f'{new_yml_file_name}'
          ]
        for num_try in range(NUM_TRIES):
          # Handle manual lighting control redirected stdout in test
          if (test in _LIGHTING_CONTROL_TESTS and
              not testing_flash_with_controller):
            print('Turn lights OFF in rig and press <ENTER> to continue.')

          # pylint: disable=subprocess-run-check
          with open(
              os.path.join(topdir, MOBLY_TEST_SUMMARY_TXT_FILE), 'w') as fp:
            output = subprocess.run(cmd, stdout=fp)
          # pylint: enable=subprocess-run-check

          # Parse mobly logs to determine PASS/FAIL(*)/SKIP & socket FAILs
          with open(
              os.path.join(topdir, MOBLY_TEST_SUMMARY_TXT_FILE), 'r') as file:
            test_code = output.returncode
            test_skipped = False
            test_not_yet_mandated = False
            test_mpc_req = ''
            perf_test_metrics = ''
            content = file.read()

            # Find media performance class logging
            lines = content.splitlines()
            for one_line in lines:
              # regular expression pattern must match
              # MPC12_CAMERA_LAUNCH_PATTERN or MPC12_JPEG_CAPTURE_PATTERN in
              # ItsTestActivity.java.
              mpc_string_match = re.search(
                  '^(1080p_jpeg_capture_time_ms:|camera_launch_time_ms:)',
                  one_line)
              if mpc_string_match:
                test_mpc_req = one_line
                break

            for one_line in lines:
              # regular expression pattern must match in ItsTestActivity.java.
              perf_metrics_string_match = re.search(
                  '^test.*:',
                  one_line)
              if perf_metrics_string_match:
                perf_test_metrics = one_line
                # each test can add multiple metrics
                results[s][PERFORMANCE_KEY].append(perf_test_metrics)

            if 'Test skipped' in content:
              return_string = 'SKIP '
              num_skip += 1
              test_skipped = True
              break

            if its_session_utils.NOT_YET_MANDATED_MESSAGE in content:
              return_string = 'FAIL*'
              num_not_mandated_fail += 1
              test_not_yet_mandated = True
              break

            if test_code == 0 and not test_skipped:
              return_string = 'PASS '
              num_pass += 1
              break

            if test_code == 1 and not test_not_yet_mandated:
              return_string = 'FAIL '
              if 'Problem with socket' in content and num_try != NUM_TRIES-1:
                logging.info('Retry %s/%s', s, test)
              else:
                num_fail += 1
                break
            os.remove(os.path.join(topdir, MOBLY_TEST_SUMMARY_TXT_FILE))
        status_prefix = ''
        if testbed_index is not None:
          status_prefix = config_file_test_key + ':'
        logging.info('%s%s %s/%s', status_prefix, return_string, s, test)
        test_name = test.split('/')[-1].split('.')[0]
        results[s]['TEST_STATUS'].append({
            'test': test_name,
            'status': return_string.strip()})
        if test_mpc_req:
          results[s][METRICS_KEY].append(test_mpc_req)
        msg_short = f'{return_string} {test}'
        scene_test_summary += msg_short + '\n'
        if (test in _LIGHTING_CONTROL_TESTS and
            not testing_flash_with_controller):
          print('Turn lights ON in rig and press <ENTER> to continue.')

      # unit is millisecond for execution time record in CtsVerifier
      scene_end_time = int(round(time.time() * 1000))
      skip_string = ''
      tot_tests = len(scene_test_list)
      tot_tests_run = tot_tests - num_skip
      if tot_tests_run != 0:
        tests_passed_ratio = (num_pass + num_not_mandated_fail) / tot_tests_run
      else:
        tests_passed_ratio = (num_pass + num_not_mandated_fail) / 100.0
      tests_passed_ratio_format = f'{(100 * tests_passed_ratio):.1f}%'
      if num_skip > 0:
        skip_string = f",{num_skip} test{'s' if num_skip > 1 else ''} skipped"
      test_result = (f'{num_pass + num_not_mandated_fail} / {tot_tests_run} '
                     f'tests passed ({tests_passed_ratio_format}){skip_string}')
      logging.info(test_result)
      if num_not_mandated_fail > 0:
        logging.info('(*) %s not_yet_mandated tests failed',
                     num_not_mandated_fail)

      tot_pass += num_pass
      logging.info('scene tests: %s, Total tests passed: %s', tot_tests,
                   tot_pass)
      if tot_tests > 0:
        logging.info('%s compatibility score: %.f/100\n',
                     s, 100 * num_pass / tot_tests)
        scene_test_summary_path = os.path.join(mobly_scene_output_logs_path,
                                               'scene_test_summary.txt')
        with open(scene_test_summary_path, 'w') as f:
          f.write(scene_test_summary)
        results[s][RESULT_KEY] = (RESULT_PASS if num_fail == 0 else RESULT_FAIL)
        results[s][SUMMARY_KEY] = scene_test_summary_path
        results[s][TIME_KEY_START] = scene_start_time
        results[s][TIME_KEY_END] = scene_end_time
      else:
        logging.info('%s compatibility score: 0/100\n')

      # Delete temporary yml file after scene run.
      new_yaml_file_path = os.path.join(YAML_FILE_DIR, new_yml_file_name)
      os.remove(new_yaml_file_path)

    # Log results per camera
    if num_testbeds is None or testbed_index == _MAIN_TESTBED:
      logging.info('Reporting camera %s ITS results to CtsVerifier', camera_id)
      logging.info('ITS results to CtsVerifier: %s', results)
      report_result(device_id, camera_id, results)
    else:
      write_result(testbed_index, device_id, camera_id, results)

  logging.info('Test execution completed.')

  # Power down tablet
  if tablet_id:
    cmd = f'adb -s {tablet_id} shell input keyevent KEYCODE_POWER'
    subprocess.Popen(cmd.split())

  # establish connection with lighting controller
  lighting_cntl = test_params_content.get('lighting_cntl', 'None')
  lighting_ch = test_params_content.get('lighting_ch', 'None')
  arduino_serial_port = lighting_control_utils.lighting_control(
      lighting_cntl, lighting_ch)

  # turn OFF lights
  lighting_control_utils.set_lighting_state(
      arduino_serial_port, lighting_ch, 'OFF')

  if num_testbeds is not None:
    if testbed_index == _MAIN_TESTBED:
      logging.info('Waiting for all testbeds to finish.')
      start = time.time()
      completed_testbeds = set()
      while time.time() < start + MERGE_RESULTS_TIMEOUT:
        for i in range(num_testbeds):
          if os.path.isfile(f'testbed_{i}_completed.tmp'):
            start = time.time()
            completed_testbeds.add(i)
        # Already reported _MAIN_TESTBED's results.
        if len(completed_testbeds) == num_testbeds - 1:
          logging.info('All testbeds completed, merging results.')
          for parsed_id, parsed_camera, parsed_results in (
              parse_testbeds(completed_testbeds)):
            logging.debug('Parsed id: %s, parsed cam: %s, parsed results: %s',
                          parsed_id, parsed_camera, parsed_results)
            if not are_devices_similar(device_id, parsed_id):
              logging.error('Device %s and device %s are not the same '
                            'model/type/build/revision.',
                            device_id, parsed_id)
              return
            report_result(device_id, parsed_camera, parsed_results)
          for temp_file in glob.glob('testbed_*.tmp'):
            os.remove(temp_file)
          break
      else:
        logging.error('No testbeds finished in the last %d seconds, '
                      'but still expected data. '
                      'Completed testbed indices: %s, '
                      'expected number of testbeds: %d',
                      MERGE_RESULTS_TIMEOUT, list(completed_testbeds),
                      num_testbeds)
    else:
      with open(f'testbed_{testbed_index}_completed.tmp', 'w') as _:
        pass

if __name__ == '__main__':
  main()
