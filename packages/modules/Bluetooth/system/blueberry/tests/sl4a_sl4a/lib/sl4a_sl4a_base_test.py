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

import logging
import os
import traceback
from functools import wraps

from blueberry.tests.gd.cert.closable import safeClose
from blueberry.tests.gd.cert.context import get_current_context
from blueberry.tests.gd_sl4a.lib.ble_lib import BleLib
from blueberry.tests.gd_sl4a.lib.ble_lib import disable_bluetooth
from blueberry.tests.gd_sl4a.lib.ble_lib import enable_bluetooth
from blueberry.tests.sl4a_sl4a.lib.le_advertiser import LeAdvertiser
from blueberry.tests.sl4a_sl4a.lib.le_scanner import LeScanner
from blueberry.tests.sl4a_sl4a.lib.l2cap import L2cap
from blueberry.tests.sl4a_sl4a.lib.security import Security
from blueberry.utils.mobly_sl4a_utils import setup_sl4a
from blueberry.utils.mobly_sl4a_utils import teardown_sl4a
from grpc import RpcError
from mobly import signals
from mobly.base_test import BaseTestClass
from mobly.controllers import android_device
from mobly.controllers.android_device import MOBLY_CONTROLLER_CONFIG_NAME as ANDROID_DEVICE_CONFIG_NAME
from mobly.controllers.android_device_lib.adb import AdbError


class Sl4aSl4aBaseTestClass(BaseTestClass):

    # DUT
    dut_advertiser_ = None
    dut_scanner_ = None
    dut_security_ = None
    dut_l2cap_ = None

    # CERT
    cert_advertiser_ = None
    cert_scanner_ = None
    cert_security_ = None
    cert_l2cap_ = None

    SUBPROCESS_WAIT_TIMEOUT_SECONDS = 10

    def setup_class(self):
        self.log_path_base = get_current_context().get_full_output_path()
        self.verbose_mode = bool(self.user_params.get('verbose_mode', False))

        # Parse and construct Android device objects
        self.android_devices = self.register_controller(android_device, required=True)

        # Setup SL4A for dut, overriding default mobly port settings
        self.dut = self.android_devices[0]
        server_port = int(self.controller_configs[ANDROID_DEVICE_CONFIG_NAME][0]['server_port'])
        forwarded_port = int(self.controller_configs[ANDROID_DEVICE_CONFIG_NAME][0]['forwarded_port'])
        setup_sl4a(self.dut, server_port, forwarded_port)

        # Setup SL4A for cert, overriding default mobly port settings
        self.cert = self.android_devices[1]
        server_port = int(self.controller_configs[ANDROID_DEVICE_CONFIG_NAME][1]['server_port'])
        forwarded_port = int(self.controller_configs[ANDROID_DEVICE_CONFIG_NAME][1]['forwarded_port'])
        setup_sl4a(self.cert, server_port, forwarded_port)

        # Enable full btsnoop log
        self.dut.adb.root()
        self.dut.adb.shell("setprop persist.bluetooth.btsnooplogmode full")
        getprop_result = self.dut.adb.getprop("persist.bluetooth.btsnooplogmode")
        if getprop_result is None or ("full" not in getprop_result.lower()):
            self.dut.log.warning("Failed to enable Bluetooth HCI Snoop Logging on DUT, mode is {}"
                                 .format(getprop_result))
        self.cert.adb.root()
        self.cert.adb.shell("setprop persist.bluetooth.btsnooplogmode full")
        getprop_result = self.cert.adb.getprop("persist.bluetooth.btsnooplogmode")
        if getprop_result is None or ("full" not in getprop_result.lower()):
            self.cert.log.warning("Failed to enable Bluetooth HCI Snoop Logging on CERT, mode is {}"
                                  .format(getprop_result))

        self.ble = BleLib(dut=self.dut)

    def teardown_class(self):
        teardown_sl4a(self.cert)
        teardown_sl4a(self.dut)
        super().teardown_class()

    def setup_device_for_test(self, device):
        device.ed.clear_all_events()
        device.sl4a.setScreenTimeout(500)
        device.sl4a.wakeUpNow()

        # Always start tests with Bluetooth enabled and BLE disabled.
        device.sl4a.bluetoothDisableBLE()
        disable_bluetooth(device.sl4a, device.ed)
        # Enable full verbose logging for Bluetooth
        device.adb.shell("device_config put bluetooth INIT_logging_debug_enabled_for_all true")
        # Then enable Bluetooth
        enable_bluetooth(device.sl4a, device.ed)
        device.sl4a.bluetoothDisableBLE()

    def setup_test(self):
        self.setup_device_for_test(self.dut)
        self.setup_device_for_test(self.cert)
        self.dut_advertiser_ = LeAdvertiser(self.dut)
        self.dut_scanner_ = LeScanner(self.dut)
        self.dut_security_ = Security(self.dut)
        self.dut_l2cap_ = L2cap(self.dut)
        self.cert_advertiser_ = LeAdvertiser(self.cert)
        self.cert_scanner_ = LeScanner(self.cert)
        self.cert_security_ = Security(self.cert)
        self.cert_l2cap_ = L2cap(self.cert)
        return True

    def teardown_test(self):
        # Go ahead and remove everything before turning off the stack
        safeClose(self.dut_advertiser_)
        safeClose(self.dut_scanner_)
        safeClose(self.dut_security_)
        safeClose(self.dut_l2cap_)
        safeClose(self.cert_advertiser_)
        safeClose(self.cert_scanner_)
        safeClose(self.cert_security_)
        safeClose(self.cert_l2cap_)
        self.dut_advertiser_ = None
        self.dut_scanner_ = None
        self.dut_security_ = None
        self.cert_advertiser_ = None
        self.cert_l2cap_ = None
        self.cert_scanner_ = None
        self.cert_security_ = None

        # Make sure BLE is disabled and Bluetooth is disabled after test
        self.dut.sl4a.bluetoothDisableBLE()
        disable_bluetooth(self.dut.sl4a, self.dut.ed)
        self.cert.sl4a.bluetoothDisableBLE()
        disable_bluetooth(self.cert.sl4a, self.cert.ed)

        current_test_dir = get_current_context().get_full_output_path()

        # Pull DUT logs
        self.pull_logs(current_test_dir, self.dut)

        # Pull CERT logs
        self.pull_logs(current_test_dir, self.cert)
        return True

    def pull_logs(self, base_dir, device):
        try:
            device.adb.pull([
                "/data/misc/bluetooth/logs/btsnoop_hci.log",
                os.path.join(base_dir, "DUT_%s_btsnoop_hci.log" % device.serial)
            ])
            device.adb.pull([
                "/data/misc/bluedroid/bt_config.conf",
                os.path.join(base_dir, "DUT_%s_bt_config.conf" % device.serial)
            ])
            device.adb.pull(
                ["/data/misc/bluedroid/bt_config.bak",
                 os.path.join(base_dir, "DUT_%s_bt_config.bak" % device.serial)])
        except AdbError as error:
            logging.warning("Failed to pull logs from DUT: " + str(error))

    def __getattribute__(self, name):
        attr = super().__getattribute__(name)
        if not callable(attr) or not Sl4aSl4aBaseTestClass.__is_entry_function(name):
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
        return name.startswith("test_") or name in Sl4aSl4aBaseTestClass.__ENTRY_METHODS
