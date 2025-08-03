"""Base test class for Blueberry."""

import importlib
import re
from typing import Union

from mobly import base_test
from mobly import records
from mobly import signals
from mobly.controllers import android_device
from mobly.controllers.android_device_lib import adb

from blueberry.controllers import derived_bt_device
from blueberry.decorators import android_bluetooth_client_decorator
from blueberry.utils import android_bluetooth_decorator


class BlueberryBaseTest(base_test.BaseTestClass):
  """Base test class for all Blueberry tests to inherit from.

  This class assists with device setup for device logging and other pre test
  setup required for Bluetooth tests.
  """

  def __init__(self, configs):
    super().__init__(configs)
    self._upload_test_report = None
    self.capture_bugreport_on_fail = None
    self.android_devices = None
    self.derived_bt_devices = None
    self.ignore_device_setup_failures = None
    self._test_metrics = []

  def setup_generated_tests(self):
    """Generates multiple the same tests for pilot run.

    This is used to let developers can easily pilot run their tests many times,
    help them to check test stability and reliability. If need to use this,
    please add a flag called "test_iterations" to TestParams in Mobly test
    configuration and its value is number of test methods to be generated. The
    naming rule of test method on Sponge is such as the following example:
      test_send_file_via_bluetooth_opp_1_of_50
      test_send_file_via_bluetooth_opp_2_of_50
      test_send_file_via_bluetooth_opp_3_of_50
      ...
      test_send_file_via_bluetooth_opp_50_of_50

    Don't use "test_case_selector" when using "test_iterations", and please use
    "test_method_selector" to replace it.
    """
    test_iterations = int(self.user_params.get('test_iterations', 0))
    if test_iterations < 2:
      return

    test_method_selector = self.user_params.get('test_method_selector', 'all')
    existing_test_names = self.get_existing_test_names()

    selected_test_names = None
    if test_method_selector == 'all':
      selected_test_names = existing_test_names
    else:
      selected_test_names = test_method_selector.split(' ')
      # Check if selected test methods exist in the test class.
      for test_name in selected_test_names:
        if test_name not in existing_test_names:
          raise base_test.Error('%s does not have test method "%s".' %
                                (self.TAG, test_name))

    for test_name in selected_test_names:
      test_method = getattr(self.__class__, test_name)
      # List of (<new test name>, <test method>).
      test_arg_sets = [('%s_%s_of_%s' % (test_name, i + 1, test_iterations),
                        test_method) for i in range(test_iterations)]
      # pylint: disable=cell-var-from-loop
      self.generate_tests(
          test_logic=lambda _, test: test(self),
          name_func=lambda name, _: name,
          arg_sets=test_arg_sets)

    # Delete origin test methods in order to avoid below situation:
    #   test_send_file_via_bluetooth_opp  <-- origin test method
    #   test_send_file_via_bluetooth_opp_1_of_50
    #   test_send_file_via_bluetooth_opp_2_of_50
    for test_name in existing_test_names:
      delattr(self.__class__, test_name)

  def setup_class(self):
    """Setup class is called before running any tests."""
    super(BlueberryBaseTest, self).setup_class()
    self._upload_test_report = int(self.user_params.get(
        'upload_test_report', 0))
    # Inits Spanner Utils if need to upload the test reports to Spanner.
    if self._upload_test_report:
      self._init_spanner_utils()
    self.capture_bugreport_on_fail = int(self.user_params.get(
        'capture_bugreport_on_fail', 0))
    self.ignore_device_setup_failures = int(self.user_params.get(
        'ignore_device_setup_failures', 0))
    self.enable_bluetooth_verbose_logging = int(self.user_params.get(
        'enable_bluetooth_verbose_logging', 0))
    self.enable_hci_snoop_logging = int(self.user_params.get(
        'enable_hci_snoop_logging', 0))
    self.increase_logger_buffers = int(self.user_params.get(
        'increase_logger_buffers', 0))
    self.enable_all_bluetooth_logging = int(self.user_params.get(
        'enable_all_bluetooth_logging', 0))

    # base test should include the test between primary device with Bluetooth
    # peripheral device.
    self.android_devices = self.register_controller(
        android_device, required=False)

    # In the case of no android_device assigned, at least 2 derived_bt_device
    # is required.
    if self.android_devices is None:
      self.derived_bt_devices = self.register_controller(
          module=derived_bt_device, min_number=2)
    else:
      self.derived_bt_devices = self.register_controller(
          module=derived_bt_device, required=False)

    if self.derived_bt_devices is None:
      self.derived_bt_devices = []
    else:
      for derived_device in self.derived_bt_devices:
        derived_device.set_user_params(self.user_params)
        derived_device.setup()

    self.android_devices = [
        android_bluetooth_decorator.AndroidBluetoothDecorator(device)
        for device in self.android_devices
    ]
    for device in self.android_devices:
      device.set_user_params(self.user_params)

    for device in self.android_devices:
      need_restart_bluetooth = False
      if (self.enable_bluetooth_verbose_logging or
          self.enable_all_bluetooth_logging):
        if self.set_bt_trc_level_verbose(device):
          need_restart_bluetooth = True
      if self.enable_hci_snoop_logging or self.enable_all_bluetooth_logging:
        if self.set_btsnooplogmode_full(device):
          need_restart_bluetooth = True
      if self.increase_logger_buffers or self.enable_all_bluetooth_logging:
        self.set_logger_buffer_size_16m(device)

      # Restarts Bluetooth to take BT VERBOSE and HCI Snoop logging effect.
      if need_restart_bluetooth:
        device.log.info('Restarting Bluetooth by airplane mode...')
        self.restart_bluetooth_by_airplane_mode(device)

    self.client_decorators = self.user_params.get('sync_decorator', [])
    if self.client_decorators:
      self.client_decorators = self.client_decorators.split(',')

    self.target_decorators = self.user_params.get('target_decorator', [])
    if self.target_decorators:
      self.target_decorators = self.target_decorators.split(',')

    for decorator in self.client_decorators:
      self.android_devices[0] = android_bluetooth_client_decorator.decorate(
          self.android_devices[0], decorator)

    for num_devices in range(1, len(self.android_devices)):
      for decorator in self.target_decorators:
        self.android_devices[
            num_devices] = android_bluetooth_client_decorator.decorate(
                self.android_devices[num_devices], decorator)

  def on_pass(self, record):
    """This method is called when a test passed."""
    if self._upload_test_report:
      self._upload_test_report_to_spanner(record.result)

  def on_fail(self, record):
    """This method is called when a test failure."""
    if self._upload_test_report:
      self._upload_test_report_to_spanner(record.result)

    # Capture bugreports on fail if enabled.
    if self.capture_bugreport_on_fail:
      devices = self.android_devices
      # Also capture bugreport of AndroidBtTargetDevice.
      for d in self.derived_bt_devices:
        if hasattr(d, 'take_bug_report'):
          devices = devices + [d]
      android_device.take_bug_reports(
          devices,
          record.test_name,
          record.begin_time,
          destination=self.current_test_info.output_path)

  def _init_spanner_utils(self) -> None:
    """Imports spanner_utils and creates SpannerUtils object."""
    spanner_utils_module = importlib.import_module(
        'blueberry.utils.spanner_utils')
    self._spanner_utils = spanner_utils_module.SpannerUtils(
        test_class_name=self.__class__.__name__,
        mh_sponge_link=self.user_params['mh_sponge_link'])

  def _upload_test_report_to_spanner(
      self,
      result: records.TestResultEnums) -> None:
    """Uploads the test report to Spanner.

    Args:
      result: Result of this test.
    """
    self._spanner_utils.create_test_report_proto(
        current_test_info=self.current_test_info,
        primary_device=self.android_devices[0],
        companion_devices=self.derived_bt_devices,
        test_metrics=self._test_metrics)
    self._test_metrics.clear()
    test_report = self._spanner_utils.write_test_report_proto(result=result)
    # Shows the test report on Sponge properties for debugging.
    self.record_data({
        'Test Name': self.current_test_info.name,
        'sponge_properties': {'test_report': test_report},
    })

  def record_test_metric(
      self,
      metric_name: str,
      metric_value: Union[int, float]) -> None:
    """Records a test metric to Spanner.

    Args:
      metric_name: Name of the metric.
      metric_value: Value of the metric.
    """
    if not self._upload_test_report:
      return
    self._test_metrics.append(
        self._spanner_utils.create_metric_proto(metric_name, metric_value))

  def set_logger_buffer_size_16m(self, device):
    """Sets all logger sizes per log buffer to 16M."""
    device.log.info('Setting all logger sizes per log buffer to 16M...')
    # Logger buffer info:
    # https://developer.android.com/studio/command-line/logcat#alternativeBuffers
    logger_buffers = ['main', 'system', 'crash', 'radio', 'events', 'kernel']
    for buffer in logger_buffers:  # pylint: disable=redefined-builtin
      device.adb.shell('logcat -b %s -G 16M' % buffer)
      buffer_size = device.adb.shell('logcat -b %s -g' % buffer)
      if isinstance(buffer_size, bytes):
        buffer_size = buffer_size.decode()
      if 'ring buffer is 16' in buffer_size:
        device.log.info('Successfully set "%s" buffer size to 16M.' % buffer)
      else:
        msg = 'Failed to set "%s" buffer size to 16M.' % buffer
        if not self.ignore_device_setup_failures:
          raise signals.TestError(msg)
        device.log.warning(msg)

  def set_bt_trc_level_verbose(self, device):
    """Modifies etc/bluetooth/bt_stack.conf to enable Bluetooth VERBOSE log."""
    device.log.info('Enabling Bluetooth VERBOSE logging...')
    bt_stack_conf = device.adb.shell('cat etc/bluetooth/bt_stack.conf')
    if isinstance(bt_stack_conf, bytes):
      bt_stack_conf = bt_stack_conf.decode()
    # Check if 19 trace level settings are set to 6(VERBOSE). E.g. TRC_HCI=6.
    if len(re.findall('TRC.*=[6]', bt_stack_conf)) == 19:
      device.log.info('Bluetooth VERBOSE logging has already enabled.')
      return False
    # Suggest to use AndroidDeviceSettingsDecorator to disable verity and then
    # reboot (b/140277443).
    device.disable_verity_check()
    device.adb.remount()
    try:
      device.adb.shell(r'sed -i "s/\(TRC.*=\)2/\16/g;s/#\(LoggingV=--v=\)0/\13'
                       '/" etc/bluetooth/bt_stack.conf')
      device.log.info('Successfully enabled Bluetooth VERBOSE Logging.')
      return True
    except adb.AdbError:
      msg = 'Failed to enable Bluetooth VERBOSE Logging.'
      if not self.ignore_device_setup_failures:
        raise signals.TestError(msg)
      device.log.warning(msg)
      return False

  def set_btsnooplogmode_full(self, device):
    """Enables bluetooth snoop logging."""
    device.log.info('Enabling Bluetooth HCI Snoop logging...')
    device.adb.shell('setprop persist.bluetooth.btsnooplogmode full')
    out = device.adb.shell('getprop persist.bluetooth.btsnooplogmode')
    if isinstance(out, bytes):
      out = out.decode()
    # The expected output is "full/n".
    if 'full' in out:
      device.log.info('Successfully enabled Bluetooth HCI Snoop Logging.')
      return True
    msg = 'Failed to enable Bluetooth HCI Snoop Logging.'
    if not self.ignore_device_setup_failures:
      raise signals.TestError(msg)
    device.log.warning(msg)
    return False

  def restart_bluetooth_by_airplane_mode(self, device):
    """Restarts bluetooth by airplane mode."""
    device.enable_airplane_mode(3)
    device.disable_airplane_mode(3)
