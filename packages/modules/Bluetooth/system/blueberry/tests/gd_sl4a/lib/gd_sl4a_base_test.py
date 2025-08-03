#!/usr/bin/env python3
#
#   Copyright 2021 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

import traceback
import os
import logging

from functools import wraps
from grpc import RpcError

from mobly import signals
from mobly.base_test import BaseTestClass
from mobly.controllers.android_device_lib.adb import AdbError
from mobly.controllers import android_device
from mobly.controllers.android_device import MOBLY_CONTROLLER_CONFIG_NAME as ANDROID_DEVICE_COFNIG_NAME

from blueberry.utils.mobly_sl4a_utils import setup_sl4a
from blueberry.utils.mobly_sl4a_utils import teardown_sl4a
from blueberry.tests.gd.cert.context import get_current_context
from blueberry.tests.gd.cert.gd_device import MOBLY_CONTROLLER_CONFIG_NAME as GD_DEVICE_CONFIG_NAME
from blueberry.tests.gd_sl4a.lib.ble_lib import enable_bluetooth, disable_bluetooth, BleLib
from blueberry.facade import rootservice_pb2 as facade_rootservice
from blueberry.tests.gd.cert import gd_device
from blueberry.utils.bt_test_utils import clear_bonded_devices


class GdSl4aBaseTestClass(BaseTestClass):

    SUBPROCESS_WAIT_TIMEOUT_SECONDS = 10

    def setup_class(self, cert_module):
        self.log_path_base = get_current_context().get_full_output_path()
        self.verbose_mode = bool(self.user_params.get('verbose_mode', False))
        for config in self.controller_configs[GD_DEVICE_CONFIG_NAME]:
            config['verbose_mode'] = self.verbose_mode
        self.cert_module = cert_module

        # Parse and construct GD device objects
        self.gd_devices = self.register_controller(gd_device, required=True)
        self.cert = self.gd_devices[0]

        # Parse and construct Android device objects
        self.android_devices = self.register_controller(android_device, required=True)
        server_port = int(self.controller_configs[ANDROID_DEVICE_COFNIG_NAME][0]['server_port'])
        forwarded_port = int(self.controller_configs[ANDROID_DEVICE_COFNIG_NAME][0]['forwarded_port'])
        self.dut = self.android_devices[0]
        setup_sl4a(self.dut, server_port, forwarded_port)

        # Enable full btsnoop log
        self.dut.adb.root()
        self.dut.adb.shell("setprop persist.bluetooth.btsnooplogmode full")
        getprop_result = self.dut.adb.getprop("persist.bluetooth.btsnooplogmode")
        if getprop_result is None or ("full" not in getprop_result.lower()):
            self.dut.log.warning(
                "Failed to enable Bluetooth Hci Snoop Logging, getprop returned {}".format(getprop_result))

        self.ble = BleLib(dut=self.dut)

    def teardown_class(self):
        teardown_sl4a(self.dut)

    def setup_test(self):
        self.cert.rootservice.StartStack(
            facade_rootservice.StartStackRequest(
                module_under_test=facade_rootservice.BluetoothModule.Value(self.cert_module),))
        self.cert.wait_channel_ready()

        self.timer_list = []
        self.dut.ed.clear_all_events()
        self.dut.sl4a.setScreenTimeout(500)
        self.dut.sl4a.wakeUpNow()

        # Always start tests with Bluetooth enabled and BLE disabled.
        self.dut.sl4a.bluetoothDisableBLE()
        disable_bluetooth(self.dut.sl4a, self.dut.ed)
        # Enable full verbose logging for Bluetooth
        self.dut.adb.shell("device_config put bluetooth INIT_logging_debug_enabled_for_all true")
        # Then enable Bluetooth
        enable_bluetooth(self.dut.sl4a, self.dut.ed)
        self.dut.sl4a.bluetoothDisableBLE()
        clear_bonded_devices(self.dut)
        return True

    def teardown_test(self):
        clear_bonded_devices(self.dut)
        # Make sure BLE is disabled and Bluetooth is disabled after test
        self.dut.sl4a.bluetoothDisableBLE()
        disable_bluetooth(self.dut.sl4a, self.dut.ed)
        try:
            self.cert.rootservice.StopStack(facade_rootservice.StopStackRequest())
        except Exception:
            logging.error("Failed to stop CERT stack")

        # TODO: split cert logcat logs into individual tests
        current_test_dir = get_current_context().get_full_output_path()

        # Pull DUT logs
        self.pull_dut_logs(current_test_dir)

        # Pull CERT logs
        self.cert.pull_logs(current_test_dir)

    def pull_dut_logs(self, base_dir):
        try:
            self.dut.adb.pull([
                "/data/misc/bluetooth/logs/btsnoop_hci.log",
                os.path.join(base_dir, "DUT_%s_btsnoop_hci.log" % self.dut.serial)
            ])
            self.dut.adb.pull([
                "/data/misc/bluedroid/bt_config.conf",
                os.path.join(base_dir, "DUT_%s_bt_config.conf" % self.dut.serial)
            ])
            self.dut.adb.pull([
                "/data/misc/bluedroid/bt_config.bak",
                os.path.join(base_dir, "DUT_%s_bt_config.bak" % self.dut.serial)
            ])
        except AdbError as error:
            logging.warning("Failed to pull logs from DUT: " + str(error))

    def __getattribute__(self, name):
        attr = super().__getattribute__(name)
        if not callable(attr) or not GdSl4aBaseTestClass.__is_entry_function(name):
            return attr

        @wraps(attr)
        def __wrapped(*args, **kwargs):
            try:
                return attr(*args, **kwargs)
            except RpcError as e:
                exception_info = "".join(traceback.format_exception(e.__class__, e, e.__traceback__))
                raise signals.TestFailure("RpcError during test\n\nRpcError:\n\n%s" % (exception_info))

        return __wrapped

    __ENTRY_METHODS = {"setup_class", "teardown_class", "setup_test", "teardown_test"}

    @staticmethod
    def __is_entry_function(name):
        return name.startswith("test_") or name in GdSl4aBaseTestClass.__ENTRY_METHODS
