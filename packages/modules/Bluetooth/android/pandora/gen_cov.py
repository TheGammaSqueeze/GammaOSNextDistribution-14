#!/usr/bin/env python

import argparse
from datetime import datetime
import os
from pathlib import Path
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

JAVA_UNIT_TESTS = 'test/mts/tools/mts-tradefed/res/config/mts-bluetooth-tests-list-shard-01.xml'
NATIVE_UNIT_TESTS = 'test/mts/tools/mts-tradefed/res/config/mts-bluetooth-tests-list-shard-02.xml'
DO_NOT_RETRY_TESTS = {
  'CtsBluetoothTestCases',
  'GoogleBluetoothInstrumentationTests',
}
MAX_TRIES = 3


def run_pts_bot(logs_out):
  run_pts_bot_cmd = [
      # atest command with verbose mode.
      'atest',
      '-d',
      '-v',
      'pts-bot',
      # Coverage tool chains and specify that coverage should be flush to the
      # disk between each tests.
      '--',
      '--coverage',
      '--coverage-toolchain JACOCO',
      '--coverage-toolchain CLANG',
      '--coverage-flush',
  ]
  with open(f'{logs_out}/pts_bot.txt', 'w') as f:
    subprocess.run(run_pts_bot_cmd, stdout=f, stderr=subprocess.STDOUT)


def list_unit_tests():
  android_build_top = os.getenv('ANDROID_BUILD_TOP')

  unit_tests = []
  java_unit_xml = ET.parse(f'{android_build_top}/{JAVA_UNIT_TESTS}')
  for child in java_unit_xml.getroot():
    value = child.attrib['value']
    if 'enable:true' in value:
      test = value.replace(':enable:true', '')
      unit_tests.append(test)

  native_unit_xml = ET.parse(f'{android_build_top}/{NATIVE_UNIT_TESTS}')
  for child in native_unit_xml.getroot():
    value = child.attrib['value']
    if 'enable:true' in value:
      test = value.replace(':enable:true', '')
      unit_tests.append(test)

  return unit_tests


def run_unit_test(test, logs_out):
  print(f'Test started: {test}')

  # Env variables necessary for native unit tests.
  env = os.environ.copy()
  env['CLANG_COVERAGE_CONTINUOUS_MODE'] = 'true'
  env['CLANG_COVERAGE'] = 'true'
  env['NATIVE_COVERAGE_PATHS'] = 'packages/modules/Bluetooth'
  run_test_cmd = [
      # atest command with verbose mode.
      'atest',
      '-d',
      '-v',
      test,
      # Coverage tool chains and specify that coverage should be flush to the
      # disk between each tests.
      '--',
      '--coverage',
      '--coverage-toolchain JACOCO',
      '--coverage-toolchain CLANG',
      '--coverage-flush',
      # Allows tests to use hidden APIs.
      '--test-arg ',
      'com.android.compatibility.testtype.LibcoreTest:hidden-api-checks:false',
      '--test-arg ',
      'com.android.tradefed.testtype.AndroidJUnitTest:hidden-api-checks:false',
      '--test-arg ',
      'com.android.tradefed.testtype.InstrumentationTest:hidden-api-checks:false',
      '--skip-system-status-check ',
      'com.android.tradefed.suite.checker.ShellStatusChecker',
  ]

  try_count = 1
  while (try_count == 1 or test not in DO_NOT_RETRY_TESTS) and try_count <= MAX_TRIES:
    with open(f'{logs_out}/{test}_{try_count}.txt', 'w') as f:
      if try_count > 1: print(f'Retrying {test}: count = {try_count}')
      returncode = subprocess.run(
          run_test_cmd, env=env, stdout=f, stderr=subprocess.STDOUT).returncode
      if returncode == 0: break
      try_count += 1

  print(
      f'Test ended [{"Success" if returncode == 0 else "Failed"}]: {test}')


def pull_and_rename_trace_for_test(test, trace):
  date = datetime.now().strftime("%Y%m%d")
  temp_trace = Path('temp_trace')
  subprocess.run(['adb', 'pull', '/data/misc/trace', temp_trace])
  for child in temp_trace.iterdir():
    child = child.rename(f'{child.parent}/{date}_{test}_{child.name}')
    shutil.copy(child, trace)
  shutil.rmtree(temp_trace, ignore_errors=True)


def generate_java_coverage(bt_apex_name, trace_path, coverage_out):

  out = os.getenv('OUT')
  android_host_out = os.getenv('ANDROID_HOST_OUT')

  java_coverage_out = Path(f'{coverage_out}/java')
  temp_path = Path(f'{coverage_out}/temp')
  if temp_path.exists():
    shutil.rmtree(temp_path, ignore_errors=True)
  temp_path.mkdir()

  framework_jar_path = Path(
      f'{out}/obj/PACKAGING/jacoco_intermediates/JAVA_LIBRARIES/framework-bluetooth.{bt_apex_name}_intermediates'
  )
  service_jar_path = Path(
      f'{out}/obj/PACKAGING/jacoco_intermediates/JAVA_LIBRARIES/service-bluetooth.{bt_apex_name}_intermediates'
  )
  app_jar_path = Path(
      f'{out}/obj/PACKAGING/jacoco_intermediates/ETC/Bluetooth{"Google" if "com.google" in bt_apex_name else ""}.{bt_apex_name}_intermediates'
  )

  # From google3/configs/wireless/android/testing/atp/prod/mainline-engprod/templates/modules/bluetooth.gcl.
  framework_exclude_classes = [
      # Exclude statically linked & jarjar'ed classes.
      '**/com/android/bluetooth/x/**/*.class',
      # Exclude AIDL generated interfaces.
      '**/android/bluetooth/I*$Default.class',
      '**/android/bluetooth/**/I*$Default.class',
      '**/android/bluetooth/I*$Stub.class',
      '**/android/bluetooth/**/I*$Stub.class',
      '**/android/bluetooth/I*$Stub$Proxy.class',
      '**/android/bluetooth/**/I*$Stub$Proxy.class',
      # Exclude annotations.
      '**/android/bluetooth/annotation/**/*.class',
  ]
  service_exclude_classes = [
      # Exclude statically linked & jarjar'ed classes.
      '**/android/support/**/*.class',
      '**/androidx/**/*.class',
      '**/com/android/bluetooth/x/**/*.class',
      '**/com/android/internal/**/*.class',
      '**/com/google/**/*.class',
      '**/kotlin/**/*.class',
      '**/kotlinx/**/*.class',
      '**/org/**/*.class',
  ]
  app_exclude_classes = [
      # Exclude statically linked & jarjar'ed classes.
      '**/android/hardware/**/*.class',
      '**/android/hidl/**/*.class',
      '**/android/net/**/*.class',
      '**/android/support/**/*.class',
      '**/androidx/**/*.class',
      '**/com/android/bluetooth/x/**/*.class',
      '**/com/android/internal/**/*.class',
      '**/com/android/obex/**/*.class',
      '**/com/android/vcard/**/*.class',
      '**/com/google/**/*.class',
      '**/kotlin/**/*.class',
      '**/kotlinx/**/*.class',
      '**/javax/**/*.class',
      '**/org/**/*.class',
      # Exclude SIM Access Profile (SAP) which is being deprecated.
      '**/com/android/bluetooth/sap/*.class',
      # Added for local runs.
      '**/com/android/bluetooth/**/BluetoothMetrics*.class',
      '**/com/android/bluetooth/**/R*.class',
  ]

  # Merged ec files.
  merged_ec_path = Path(f'{temp_path}/merged.ec')
  subprocess.run((
      f'java -jar {android_host_out}/framework/jacoco-cli.jar merge {trace_path.absolute()}/*.ec '
      f'--destfile {merged_ec_path.absolute()}'),
                 shell=True)

  # Copy and extract jar files.
  framework_temp_path = Path(f'{temp_path}/{framework_jar_path.name}')
  service_temp_path = Path(f'{temp_path}/{service_jar_path.name}')
  app_temp_path = Path(f'{temp_path}/{app_jar_path.name}')

  shutil.copytree(framework_jar_path, framework_temp_path)
  shutil.copytree(service_jar_path, service_temp_path)
  shutil.copytree(app_jar_path, app_temp_path)

  current_dir_path = Path.cwd()
  for p in [framework_temp_path, service_temp_path, app_temp_path]:
    os.chdir(p.absolute())
    os.system('jar xf jacoco-report-classes.jar')
    os.chdir(current_dir_path)

  os.remove(f'{framework_temp_path}/jacoco-report-classes.jar')
  os.remove(f'{service_temp_path}/jacoco-report-classes.jar')
  os.remove(f'{app_temp_path}/jacoco-report-classes.jar')

  # Generate coverage report.
  exclude_classes = []
  for glob in framework_exclude_classes:
    exclude_classes.extend(list(framework_temp_path.glob(glob)))
  for glob in service_exclude_classes:
    exclude_classes.extend(list(service_temp_path.glob(glob)))
  for glob in app_exclude_classes:
    exclude_classes.extend(list(app_temp_path.glob(glob)))

  for c in exclude_classes:
    if c.exists():
      os.remove(c.absolute())

  gen_java_cov_report_cmd = [
      f'java',
      f'-jar',
      f'{android_host_out}/framework/jacoco-cli.jar',
      f'report',
      f'{merged_ec_path.absolute()}',
      f'--classfiles',
      f'{temp_path.absolute()}',
      f'--html',
      f'{java_coverage_out.absolute()}',
      f'--name',
      f'{java_coverage_out.absolute()}.html',
  ]
  subprocess.run(gen_java_cov_report_cmd)

  # Cleanup.
  shutil.rmtree(temp_path, ignore_errors=True)


def generate_native_coverage(bt_apex_name, trace_path, coverage_out):

  out = os.getenv('OUT')
  android_build_top = os.getenv('ANDROID_BUILD_TOP')

  native_coverage_out = Path(f'{coverage_out}/native')
  temp_path = Path(f'{coverage_out}/temp')
  if temp_path.exists():
    shutil.rmtree(temp_path, ignore_errors=True)
  temp_path.mkdir()

  # From google3/configs/wireless/android/testing/atp/prod/mainline-engprod/templates/modules/bluetooth.gcl.
  exclude_files = {
      'android/',
      # Exclude AIDLs definition and generated interfaces.
      'system/.*_aidl.*',
      'system/binder/',
      # Exclude tests.
      'system/.*_test.*',
      'system/.*_mock.*',
      'system/.*_unittest.*',
      'system/blueberry/',
      'system/test/',
      # Exclude config and doc.
      'system/build/',
      'system/conf/',
      'system/doc/',
      # Exclude (currently) unused GD code.
      'system/gd/att/',
      'system/gd/l2cap/',
      'system/gd/neighbor/',
      'system/gd/rust/',
      'system/gd/security/',
      # Exclude legacy AVRCP implementation (to be removed, current AVRCP
      # implementation is in packages/modules/Bluetooth/system/profile/avrcp)
      'system/stack/avrc/',
      # Exclude audio HIDL since AIDL is used instead today (in
      # packages/modules/Bluetooth/system/audio_hal_interface/aidl)
      'system/audio_hal_interface/hidl/',
  }

  # Merge profdata files.
  profdata_path = Path(f'{temp_path}/coverage.profdata')
  subprocess.run(
      f'llvm-profdata merge --sparse -o {profdata_path.absolute()} {trace_path.absolute()}/*.profraw',
      shell=True)

  gen_native_cov_report_cmd = [
      f'llvm-cov',
      f'show',
      f'-format=html',
      f'-output-dir={native_coverage_out.absolute()}',
      f'-instr-profile={profdata_path.absolute()}',
      f'{out}/symbols/apex/{bt_apex_name}/lib64/libbluetooth_jni.so',
      f'-path-equivalence=/proc/self/cwd,{android_build_top}',
      f'/proc/self/cwd/packages/modules/Bluetooth',
  ]
  for f in exclude_files:
    gen_native_cov_report_cmd.append(f'-ignore-filename-regex={f}')
  subprocess.run(gen_native_cov_report_cmd, cwd=android_build_top)

  # Cleanup.
  shutil.rmtree(temp_path, ignore_errors=True)


if __name__ == '__main__':

  parser = argparse.ArgumentParser()
  parser.add_argument(
      '--apex-name',
      default='com.android.btservices',
      help='bluetooth apex name. Default: com.android.btservices')
  parser.add_argument(
      '--java', action='store_true', help='generate Java coverage')
  parser.add_argument(
      '--native', action='store_true', help='generate native coverage')
  parser.add_argument(
      '--out',
      type=str,
      default='out_coverage',
      help='out directory for coverage reports. Default: ./out_coverage')
  parser.add_argument(
      '--trace',
      type=str,
      default='trace',
      help='trace directory with .ec and .profraw files. Default: ./trace')
  parser.add_argument(
      '--full-report',
      action='store_true',
      help='run all tests and compute coverage report')
  args = parser.parse_args()

  coverage_out = Path(args.out)
  shutil.rmtree(coverage_out, ignore_errors=True)
  coverage_out.mkdir()

  if not args.full_report:
    trace_path = Path(args.trace)
    if (not trace_path.exists() or not trace_path.is_dir()):
      sys.exit('Trace directory does not exist')

    if (args.java):
      generate_java_coverage(args.apex_name, trace_path, coverage_out)
    if (args.native):
      generate_native_coverage(args.apex_name, trace_path, coverage_out)

  else:

    # Output logs directory
    logs_out = Path('logs_bt_tests')
    logs_out.mkdir(exist_ok=True)

    # Compute Pandora tests coverage
    coverage_out_pandora = Path(f'{coverage_out}/pandora')
    coverage_out_pandora.mkdir()
    trace_pandora = Path('trace_pandora')
    shutil.rmtree(trace_pandora, ignore_errors=True)
    trace_pandora.mkdir()
    subprocess.run(['adb', 'shell', 'rm', '/data/misc/trace/*'])
    run_pts_bot(logs_out)
    pull_and_rename_trace_for_test('pts_bot', trace_pandora)

    generate_java_coverage(args.apex_name, trace_pandora, coverage_out_pandora)
    generate_native_coverage(args.apex_name, trace_pandora, coverage_out_pandora)

    # Compute unit tests coverage
    coverage_out_unit = Path(f'{coverage_out}/unit')
    coverage_out_unit.mkdir()
    trace_unit = Path('trace_unit')
    shutil.rmtree(trace_unit, ignore_errors=True)
    trace_unit.mkdir()

    unit_tests = list_unit_tests()
    for test in unit_tests:
      subprocess.run(['adb', 'shell', 'rm', '/data/misc/trace/*'])
      run_unit_test(test, logs_out)
      pull_and_rename_trace_for_test(test, trace_unit)

    generate_java_coverage(args.apex_name, trace_unit, coverage_out_unit)
    generate_native_coverage(args.apex_name, trace_unit, coverage_out_unit)

    # Compute all tests coverage
    coverage_out_mainline = Path(f'{coverage_out}/mainline')
    coverage_out_mainline.mkdir()
    trace_mainline = Path('trace_mainline')
    shutil.rmtree(trace_mainline, ignore_errors=True)
    trace_mainline.mkdir()
    for child in trace_pandora.iterdir():
      shutil.copy(child, trace_mainline)
    for child in trace_unit.iterdir():
      shutil.copy(child, trace_mainline)

    generate_java_coverage(args.apex_name, trace_mainline, coverage_out_mainline)
    generate_native_coverage(args.apex_name, trace_mainline, coverage_out_mainline)
