"""Command-line test runner script for running Bluetooth tests.

This module allows users to initiate Bluetooth test targets and run them against
specified DUTs (devices-under-test) using a simple command line interface.
"""

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import base64

from absl import app
from absl import flags
from absl import logging

# Internal import
# Internal import
# Internal import
# Internal import
# Internal import

FLAGS = flags.FLAGS

flags.DEFINE_multi_string('bt_test', None, 'Bluetooth test to run.')
flags.DEFINE_multi_string('bt_dut', None,
                          'Bluetooth device to allocate for tests.')


# Valid config keys for the --bt_test command line flag.
BT_TEST_CONFIG_KEYS = {'target'}

# Valid config keys for the --bt_dut (device-under-test) command line flag.
BT_DUT_CONFIG_KEYS = {'hardware'}

TEST_FLAGS = ('--notest_loasd --test_output=streamed '
              '--test_arg=--param_dut_config=%s')


class Error(Exception):
  """Base class for module exceptions."""
  pass


class TestConfigError(Error):
  """Raised when --bt_test config flags are specified incorrectly."""
  pass


class DutConfigError(Error):
  """Raised when --bt_dut config flags are specified incorrectly."""
  pass


def validate_bt_test_flags(flag_value):
  """Validates the format of specified --bt_test flags.

  Args:
    flag_value: string, the config flag value for a given --bt_test flag.

  Returns:
    bool, True if --bt_test flags have been specified correctly.
  """
  if not flag_value:
    logging.error('No tests specified! Please specify at least one '
                  'test using the --bt_test flag.')
    return False
  for test in flag_value:
    config_args = test.split(',')
    for config_arg in config_args:
      if config_arg.split('=')[0] not in BT_TEST_CONFIG_KEYS:
        logging.error('--bt_test config key "%s" is invalid!',
                      config_arg.split('=')[0])
        return False
  return True


def validate_bt_dut_flags(flag_value):
  """Validates the format of specified --bt_dut flags.

  Args:
    flag_value: string, the config flag value for a given --bt_dut flag.

  Returns:
    bool, True if --bt_dut flags have been specified correctly.
  """
  if not flag_value:
    logging.error('No DUTs specified! Please specify at least one '
                  'DUT using the --bt_dut flag.')
    return False
  for dut in flag_value:
    config_args = dut.split(',')
    for config_arg in config_args:
      if config_arg.split('=')[0] not in BT_DUT_CONFIG_KEYS:
        logging.error('--bt_dut config key "%s" is invalid!',
                      config_arg.split('=')[0])
        return False
  return True

flags.register_validator(
    'bt_test', validate_bt_test_flags,
    ('Invalid --bt_test configuration specified!'
     ' Valid configuration fields include: %s')
    % BT_TEST_CONFIG_KEYS)


flags.register_validator(
    'bt_dut', validate_bt_dut_flags,
    ('Invalid --bt_dut configuration specified!'
     ' Valid configuration fields include: %s')
    % BT_DUT_CONFIG_KEYS)


def parse_flag_value(flag_value):
  """Parses a config flag value string into a dict.

  Example input: 'target=//tests:bluetooth_pairing_test'
  Example output: {'target': '//tests:bluetooth_pairing_test'}

  Args:
    flag_value: string, the config flag value for a given flag.

  Returns:
    dict, A dict object representation of a config flag value.
  """
  config_dict = {}
  config_args = flag_value.split(',')
  for config_arg in config_args:
    config_dict[config_arg.split('=')[0]] = config_arg.split('=')[1]
  return config_dict


def get_device_type(gateway_stub, dut_config_dict):
  """Determines a device type based on a device query.

  Args:
    gateway_stub: An RPC2 stub object.
    dut_config_dict: dict, A dict of device config args.

  Returns:
    string, The MobileHarness device type.

  Raises:
    DutConfigError: If --bt_dut flag(s) are incorrectly specified.
  """
  device_query_filter = device_query_pb2.DeviceQueryFilter()
  device_query_filter.type_regex.append('AndroidRealDevice')
  for dut_config_key in dut_config_dict:
    dimension_filter = device_query_filter.dimension_filter.add()
    dimension_filter.name = dut_config_key
    dimension_filter.value_regex = dut_config_dict[dut_config_key]
  request = gateway_service_pb2.QueryDeviceRequest(
      device_query_filter=device_query_filter)
  response = gateway_stub.QueryDevice(request)
  if response.device_query_result.device_info:
    return 'AndroidRealDevice'

  device_query_filter.ClearField('type_regex')
  device_query_filter.type_regex.append('TestbedDevice')
  request = gateway_service_pb2.QueryDeviceRequest(
      device_query_filter=device_query_filter)
  response = gateway_stub.QueryDevice(request)
  if response.device_query_result.device_info:
    return 'TestbedDevice'

  raise DutConfigError('Invalid --bt_dut config specified: %s' %
                       dut_config_dict)


def generate_dut_configs(gateway_stub):
  """Generates a unicode string specifying the desired DUT configurations.

  Args:
    gateway_stub: An RPC2 stub object.

  Returns:
    string, Unicode string specifying DUT configurations.

  Raises:
    DutConfigError: If --bt_dut flag(s) are incorrectly specified.
  """
  dut_list = job_config_pb2.JobConfig().DeviceList()
  dut_config_dict_list = [parse_flag_value(value) for value in FLAGS.bt_dut]

  for dut_config_dict in dut_config_dict_list:
    dut_config_dict['pool'] = 'bluetooth-iop'
    dut = job_config_pb2.JobConfig().SubDeviceSpec()
    if 'hardware' not in dut_config_dict:
      raise DutConfigError('Must specify hardware name for bt_dut: %s' %
                           dut_config_dict)
    dut.type = get_device_type(gateway_stub, dut_config_dict)
    for config_key in dut_config_dict:
      dut.dimensions.content[config_key] = dut_config_dict[config_key]
    dut_list.sub_device_spec.append(dut)
  logging.info(base64.b64encode(dut_list.SerializeToString()).decode('utf-8'))
  return base64.b64encode(dut_list.SerializeToString()).decode('utf-8')


def generate_blaze_targets(session_config, gateway_stub):
  """Generates and appends blaze test targets to a MobileHarness session.

  Args:
    session_config: The SessionConfig object to append blaze test targets to.
    gateway_stub: An RPC2 stub object.

  Raises:
     TestConfigError: If --bt_test flag(s) are incorrectly specified.
  """
  test_config_dict_list = [parse_flag_value(value) for value in FLAGS.bt_test]

  for test_config_dict in test_config_dict_list:
    target = setting_pb2.BlazeTarget()
    if 'target' not in test_config_dict:
      raise TestConfigError('Must specify a target for bt_test: %s' %
                            test_config_dict)
    target.target_name = test_config_dict['target']
    target.test_flags = TEST_FLAGS % generate_dut_configs(gateway_stub)
    session_config.blaze_target.append(target)


def run_session():
  """Runs a configured test session.

  Returns:
    A RunSessionResponse object.
  """
  session_config = setting_pb2.SessionConfig()
  channel = rpcutil.GetNewChannel('blade:mobileharness-gateway')
  gateway_stub = gateway_service_pb2.GatewayService.NewRPC2Stub(channel=channel)
  generate_blaze_targets(session_config, gateway_stub)
  request = gateway_service_pb2.RunSessionRequest()
  request.session_config.CopyFrom(session_config)
  response = gateway_stub.RunSession(request)
  logging.info('Sponge link: %s', response.sponge)
  logging.info('Session ID: %s', response.session_id)
  return response


def main(argv):
  logging.use_python_logging()
  del argv
  run_session()

if __name__ == '__main__':
  flags.mark_flag_as_required('bt_test')
  flags.mark_flag_as_required('bt_dut')
  app.run(main)
