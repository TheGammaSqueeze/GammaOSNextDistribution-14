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
"""Script to run sensor_fusion repeated times."""

import logging
import os
import os.path
import shutil
import subprocess
import sys
import tempfile

import numpy as np

import run_all_tests  # from same tools directory as run_sensor_fusion.py

_CORR_DIST_THRESH_MAX = 0.005  # must match value in test_sensor_fusion.py
_NUM_RUNS = 1
_TEST_BED_SENSOR_FUSION = 'TEST_BED_SENSOR_FUSION'
_TIME_SHIFT_MATCH = 'Best correlation of '


def find_time_shift(out_file_path):
  """Search through a test run's test_log.DEBUG for the best time shift.

  Args:
    out_file_path: File path for stdout logs to search through

  Returns:
    Float num of best time shift, if one is found. Otherwise, None.
  """
  line = find_matching_line(out_file_path, _TIME_SHIFT_MATCH)
  if line is None:
    return None
  else:
    words = line.split(' ')
    time_shift = float(words[-1][:-3])  # strip off 'ms'
    fit_corr = float(words[-5])
    return {'time_shift': time_shift, 'corr': fit_corr}


def find_matching_line(file_path, match_string):
  """Search each line in the file at 'file_path' for match_string.

  Args:
      file_path: File path for file being searched
      match_string: Sting used to match against lines

  Returns:
      The first matching line. If none exists, returns None.
  """
  with open(file_path) as f:
    for line in f:
      if match_string in line:
        return line
  return None


def main():
  """Run the sensor_fusion test for stastical purposes.

    Script should be run from the top-level CameraITS directory.
    All parameters expect 'num_runs' are defined in config.yml.
    num_runs is defined at run time with 'num_runs=<int>'
    'camera_id' can be over-written at command line to allow different
    camera_ids facing the same direction to be tested.

    ie. python tools/run_all_tests.py num_runs=10  # n=10 w/ config.yml cam
        python tools/run_all_tests.py camera=0 num_runs=10  # n=10 w/ cam[0]
        python tools/run_all_tests.py camera=0.4 num_runs=10 # n=10 w/ cam[0.4]

    Command line arguments:
        camera_id: camera_id or list of camera_ids.
        num_runs: integer number of runs to get statistical values

    All other config values are stored in config.yml file.
  """
  logging.basicConfig(level=logging.INFO)
  # Make output directories to hold the generated files.
  topdir = tempfile.mkdtemp(prefix='CameraITS_')
  subprocess.call(['chmod', 'g+rx', topdir])

  camera_id_combos = []

  # Override camera with cmd line values if available
  num_runs = _NUM_RUNS
  get_argv_vals = lambda x: x.split('=')[1]
  for s in list(sys.argv[1:]):
    if 'camera=' in s:
      camera_id_combos = str(get_argv_vals(s)).split(',')
    elif 'num_runs=' in s:
      num_runs = int(get_argv_vals(s))

  # Read config file and extract relevant TestBed
  config_file_contents = run_all_tests.get_config_file_contents()
  for i in config_file_contents['TestBeds']:
    if i['Name'] != _TEST_BED_SENSOR_FUSION:
      config_file_contents['TestBeds'].remove(i)

  # Get test parameters from config file
  test_params_content = run_all_tests.get_test_params(config_file_contents)
  if not camera_id_combos:
    camera_id_combos = test_params_content['camera'].split(',')
  debug = test_params_content['debug_mode']
  fps = test_params_content['fps']
  img_size = test_params_content['img_size']

  # Get dut id
  device_id = run_all_tests.get_device_serial_number(
      'dut', config_file_contents)

  # Log run info
  logging.info('Running sensor_fusion on device: %s, camera: %s',
               device_id, camera_id_combos)
  logging.info('Saving output files to: %s', topdir)

  for camera_id in camera_id_combos:
    time_shifts = []
    # A subdir in topdir will be created for each camera_id.
    test_params_content['camera'] = camera_id
    test_params_content['scene'] = 'sensor_fusion'
    config_file_contents['TestBeds'][0]['TestParams'] = test_params_content
    os.mkdir(os.path.join(topdir, camera_id))

    # Add the MoblyParams to config.yml file store camera_id test results.
    mobly_output_logs_path = os.path.join(topdir, camera_id)
    mobly_scene_output_logs_path = os.path.join(
        mobly_output_logs_path, 'sensor_fusion')
    mobly_params_dict = {
        'MoblyParams': {
            'LogPath': mobly_scene_output_logs_path
        }
    }
    config_file_contents.update(mobly_params_dict)
    logging.debug('Config file contents: %s', config_file_contents)
    tmp_yml_file_name = run_all_tests.get_updated_yml_file(config_file_contents)
    logging.info('Using %s as temporary config yml file', tmp_yml_file_name)

    # Run tests
    logging.info('%d runs for test_sensor_fusion with camera %s',
                 num_runs, camera_id)
    logging.info('FPS: %d, img size: %s', fps, img_size)
    for _ in range(num_runs):
      cmd = ['python',
             os.path.join(os.environ['CAMERA_ITS_TOP'], 'tests',
                          'sensor_fusion', 'test_sensor_fusion.py'),
             '-c',
             f'{tmp_yml_file_name}'
             ]
      # pylint: disable=subprocess-run-check
      with open(run_all_tests.MOBLY_TEST_SUMMARY_TXT_FILE, 'w') as fp:
        output = subprocess.run(cmd, stdout=fp)
      # pylint: enable=subprocess-run-check

      with open(run_all_tests.MOBLY_TEST_SUMMARY_TXT_FILE, 'r') as _:
        if output.returncode == 0:
          return_string = 'PASS'
        else:
          return_string = 'FAIL'

      with open(run_all_tests.MOBLY_TEST_SUMMARY_TXT_FILE, 'r') as file:
        content = file.read()
        lines = content.splitlines()
        for one_line in lines:
          if 'root_output_path:' in one_line:
            root_output_path = one_line.split(':')[1].strip()
        file.close()

        os.remove(run_all_tests.MOBLY_TEST_SUMMARY_TXT_FILE)
        file_name = os.path.join(root_output_path, 'test_log.DEBUG')
        time_shift = find_time_shift(file_name)
        if time_shift is not None:
          logging.info('%s time_shift: %.4f ms, corr: %.6f', return_string,
                       time_shift['time_shift'], time_shift['corr'])
          if time_shift['corr'] < _CORR_DIST_THRESH_MAX:
            time_shifts.append(time_shift)
          else:
            logging.info('Correlation distance too large. Not used for stats.')
        else:
          logging.info('time_shift not found')

    # Summarize results with stats
    times = [t['time_shift'] for t in time_shifts]
    logging.info('runs: %d, time_shift mean: %.4f, sigma: %.4f',
                 len(times), np.mean(times), np.std(times))

    # Delete temporary yml file after run.
    tmp_yml_file = os.path.join(run_all_tests.YAML_FILE_DIR, tmp_yml_file_name)
    os.remove(tmp_yml_file)

  # Delete temporary image files after run.
  if debug == 'False':
    logging.info('Removing tmp dir %s to save space.', topdir)
    shutil.rmtree(topdir)

  logging.info('Test completed.')
if __name__ == '__main__':
  main()

