#!/usr/bin/env python3
#
# Copyright (C) 2016 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License. You may obtain a copy of
# the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations under
# the License.

import logging

import mobly.controllers.android_device_lib.sl4a_client as sl4a_client
from mobly.controllers.android_device import AndroidDevice
from mobly.controllers.android_device_lib.adb import AdbError
from mobly.controllers.android_device_lib.jsonrpc_client_base import AppRestoreConnectionError
from mobly.controllers.android_device_lib.services.sl4a_service import Sl4aService


class FakeFuture:
    """
    A fake Future object to override default mobly behavior
    """

    def set_result(self, result):
        logging.debug("Setting fake result {}".format(result))


def setup_sl4a(device: AndroidDevice, server_port: int, forwarded_port: int):
    """
    A method that setups up SL4A instance on mobly
    :param device: an AndroidDevice instance
    :param server_port: Preferred server port used by SL4A on Android device
    :param forwarded_port: Preferred server port number forwarded from Android to
                           the host PC via adb for SL4A connections
    :return: None
    """
    sl4a_client._DEVICE_SIDE_PORT = server_port
    sl4a_client._APP_START_WAIT_TIME = 0.5
    if device.sl4a is not None:
        device.log.error("SL4A is not none when registering")
    device.services.register('sl4a', Sl4aService, start_service=False)
    # Start the SL4A service and event dispatcher
    try:
        device.sl4a.start()
    except AppRestoreConnectionError as exp:
        device.log.debug("AppRestoreConnectionError {}".format(exp))
    # Pause the dispatcher, but do not stop the service
    try:
        device.sl4a.pause()
    except AdbError as exp:
        device.log.debug("Failed to pause() {}".format(exp))
    sl4a_client._APP_START_WAIT_TIME = 2 * 60
    # Restart the service with a new host port
    device.sl4a.restore_app_connection(port=forwarded_port)


def teardown_sl4a(device: AndroidDevice):
    """
    A method to tear down SL4A interface on mobly
    :param device: an AndroidDevice instance that already contains SL4a
    :return: None
    """
    if device.sl4a.is_alive:
        # Both self.dut.sl4a and self.dut.sl4a.ed._sl4a are Sl4aClient instances
        # If we do not set host_port to None here, host_poart will be removed twice from Android
        # The 2nd removal will trigger and exception that spam the test result
        # TODO: Resolve this issue in mobly
        device.log.info("Clearing host_port to prevent mobly crash {}".format(device.sl4a._sl4a_client.host_port))
        device.sl4a._sl4a_client.host_port = None
        # Moreover concurrent.Future.set_result() should never be called from thread that is
        # waiting for the future. However, mobly calls it and cause InvalidStateError when it
        # tries to do that after the thread pool has stopped, overriding it here
        # TODO: Resolve this issue in mobly
        try:
            device.sl4a.ed.poller = FakeFuture()
        except Exception as e:
            print(e)
    try:
        # Guarded by is_alive internally
        device.sl4a.stop()
    except AdbError as exp:
        device.log.warning("Failed top stop()".format(exp))
