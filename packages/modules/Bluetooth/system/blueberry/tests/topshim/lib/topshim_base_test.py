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

import asyncio
import importlib
import logging
import os
import signal
import subprocess

from blueberry.tests.gd.cert.async_subprocess_logger import AsyncSubprocessLogger
from blueberry.tests.gd.cert.context import get_current_context
from blueberry.tests.gd.cert.os_utils import get_gd_root
from blueberry.tests.gd.cert.os_utils import get_gd_root
from blueberry.tests.gd.cert.os_utils import read_crash_snippet_and_log_tail
from blueberry.tests.gd.cert.os_utils import is_subprocess_alive
from blueberry.tests.gd.cert.os_utils import make_ports_available
from blueberry.tests.gd.cert.os_utils import TerminalColor
from blueberry.tests.gd.cert.tracelogger import TraceLogger
from blueberry.tests.gd.cert.truth import assertThat
from blueberry.tests.topshim.lib.adapter_client import AdapterClient
from blueberry.tests.topshim.lib.async_closable import asyncSafeClose
from blueberry.tests.topshim.lib.gatt_client import GattClient
from blueberry.tests.topshim.lib.hf_client_client import HfClientClient
from blueberry.tests.topshim.lib.hfp_client import HfpClient
from blueberry.tests.topshim.lib.security_client import SecurityClient
from blueberry.tests.topshim.lib.topshim_device import TopshimDevice

from mobly import asserts
from mobly import base_test

CONTROLLER_CONFIG_NAME = "GdDevice"


def _setup_class_core(verbose_mode, log_path_base, controller_configs):
    info = {}
    info['controller_configs'] = controller_configs

    # Start root-canal if needed
    info['rootcanal_running'] = False
    info['rootcanal_logpath'] = None
    info['rootcanal_process'] = None
    info['rootcanal_logger'] = None
    if 'rootcanal' not in info['controller_configs']:
        return
    info['rootcanal_running'] = True
    # Get root canal binary
    rootcanal = os.path.join(get_gd_root(), "root-canal")
    info['rootcanal'] = rootcanal
    info['rootcanal_exist'] = os.path.isfile(rootcanal)
    if not os.path.isfile(rootcanal):
        return info
    # Get root canal log
    rootcanal_logpath = os.path.join(log_path_base, 'rootcanal_logs.txt')
    info['rootcanal_logpath'] = rootcanal_logpath
    # Make sure ports are available
    rootcanal_config = info['controller_configs']['rootcanal']
    rootcanal_test_port = int(rootcanal_config.get("test_port", "6401"))
    rootcanal_hci_port = int(rootcanal_config.get("hci_port", "6402"))
    rootcanal_link_layer_port = int(rootcanal_config.get("link_layer_port", "6403"))

    info['make_rootcanal_ports_available'] = make_ports_available(
        (rootcanal_test_port, rootcanal_hci_port, rootcanal_link_layer_port))
    if not make_ports_available((rootcanal_test_port, rootcanal_hci_port, rootcanal_link_layer_port)):
        return info

    # Start root canal process
    rootcanal_cmd = [rootcanal, str(rootcanal_test_port), str(rootcanal_hci_port), str(rootcanal_link_layer_port)]
    info['rootcanal_cmd'] = rootcanal_cmd

    rootcanal_process = subprocess.Popen(rootcanal_cmd,
                                         cwd=get_gd_root(),
                                         env=os.environ.copy(),
                                         stdout=subprocess.PIPE,
                                         stderr=subprocess.STDOUT,
                                         universal_newlines=True)

    info['rootcanal_process'] = rootcanal_process
    if rootcanal_process:
        info['is_rootcanal_process_started'] = True
    else:
        info['is_rootcanal_process_started'] = False
        return info
    info['is_subprocess_alive'] = is_subprocess_alive(rootcanal_process)
    if not is_subprocess_alive(rootcanal_process):
        info['is_subprocess_alive'] = False
        return info

    info['rootcanal_logger'] = AsyncSubprocessLogger(rootcanal_process, [rootcanal_logpath],
                                                     log_to_stdout=verbose_mode,
                                                     tag="rootcanal",
                                                     color=TerminalColor.MAGENTA)

    # Modify the device config to include the correct root-canal port
    for gd_device_config in info['controller_configs'].get("GdDevice"):
        gd_device_config["rootcanal_port"] = str(rootcanal_hci_port)

    return info


def _teardown_class_core(rootcanal_running, rootcanal_process, rootcanal_logger, subprocess_wait_timeout_seconds):
    if rootcanal_running:
        stop_signal = signal.SIGINT
        rootcanal_process.send_signal(stop_signal)
        try:
            return_code = rootcanal_process.wait(timeout=subprocess_wait_timeout_seconds)
        except subprocess.TimeoutExpired:
            logging.error("Failed to interrupt root canal via SIGINT, sending SIGKILL")
            stop_signal = signal.SIGKILL
            rootcanal_process.kill()
            try:
                return_code = rootcanal_process.wait(timeout=subprocess_wait_timeout_seconds)
            except subprocess.TimeoutExpired:
                logging.error("Failed to kill root canal")
                return_code = -65536
        if return_code != 0 and return_code != -stop_signal:
            logging.error("rootcanal stopped with code: %d" % return_code)
        rootcanal_logger.stop()


def dump_crashes_core(dut, cert, rootcanal_running, rootcanal_process, rootcanal_logpath):
    dut_crash, dut_log_tail = dut.get_crash_snippet_and_log_tail()
    cert_crash, cert_log_tail = cert.get_crash_snippet_and_log_tail()
    rootcanal_crash = None
    rootcanal_log_tail = None
    if rootcanal_running and not is_subprocess_alive(rootcanal_process):
        rootcanal_crash, roocanal_log_tail = read_crash_snippet_and_log_tail(rootcanal_logpath)

    crash_detail = ""
    if dut_crash or cert_crash or rootcanal_crash:
        if rootcanal_crash:
            crash_detail += "rootcanal crashed:\n\n%s\n\n" % rootcanal_crash
        if dut_crash:
            crash_detail += "dut stack crashed:\n\n%s\n\n" % dut_crash
        if cert_crash:
            crash_detail += "cert stack crashed:\n\n%s\n\n" % cert_crash
    else:
        if rootcanal_log_tail:
            crash_detail += "rootcanal log tail:\n\n%s\n\n" % rootcanal_log_tail
        if dut_log_tail:
            crash_detail += "dut log tail:\n\n%s\n\n" % dut_log_tail
        if cert_log_tail:
            crash_detail += "cert log tail:\n\n%s\n\n" % cert_log_tail

    return crash_detail


class TopshimBaseTest(base_test.BaseTestClass):

    __dut = None
    __cert = None

    async def __setup_adapter(self):
        dut_adapter = AdapterClient(port=self.dut_port)
        cert_adapter = AdapterClient(port=self.cert_port)
        started = await dut_adapter._verify_adapter_started()
        assertThat(started).isTrue()
        started = started and await cert_adapter._verify_adapter_started()
        assertThat(started).isTrue()
        self.__dut = TopshimDevice(dut_adapter, GattClient(port=self.dut_port),
                                   SecurityClient(dut_adapter, port=self.dut_port),
                                   HfpClient(port=self.dut_port),
                                   HfClientClient(port=self.dut_port))
        self.__cert = TopshimDevice(cert_adapter, GattClient(port=self.cert_port),
                                    SecurityClient(cert_adapter, port=self.cert_port),
                                    HfpClient(port=self.cert_port),
                                    HfClientClient(port=self.cert_port))
        return started

    async def __teardown_adapter(self):
        await asyncSafeClose(self.__dut)
        await asyncSafeClose(self.__cert)

    def dut(self):
        """
        Get a handle on the DUT device
        """
        return self.__dut

    def cert(self):
        """
        Get a handle on the CERT device
        """
        return self.__cert

    def setup_class(self):
        """
        Configure rootcanal and setup test parameters
        """
        self.log = TraceLogger(logging.getLogger())
        self.log_path_base = get_current_context().get_full_output_path()
        self.verbose_mode = bool(self.user_params.get('verbose_mode', False))
        for config in self.controller_configs[CONTROLLER_CONFIG_NAME]:
            config['verbose_mode'] = self.verbose_mode

        self.info = _setup_class_core(verbose_mode=self.verbose_mode,
                                      log_path_base=self.log_path_base,
                                      controller_configs=self.controller_configs)
        self.rootcanal_running = self.info['rootcanal_running']
        self.rootcanal_logpath = self.info['rootcanal_logpath']
        self.rootcanal_process = self.info['rootcanal_process']
        self.rootcanal_logger = self.info['rootcanal_logger']

        asserts.assert_true(self.info['rootcanal_exist'], "Root canal does not exist at %s" % self.info['rootcanal'])
        asserts.assert_true(self.info['make_rootcanal_ports_available'], "Failed to make root canal ports available")

        self.log.debug("Running %s" % " ".join(self.info['rootcanal_cmd']))
        asserts.assert_true(self.info['is_rootcanal_process_started'],
                            msg="Cannot start root-canal at " + str(self.info['rootcanal']))
        asserts.assert_true(self.info['is_subprocess_alive'], msg="root-canal stopped immediately after running")

        self.controller_configs = self.info['controller_configs']

        controllers = self.register_controller(importlib.import_module('blueberry.tests.topshim.lib.topshim_device'))
        self.cert_port = controllers[0].grpc_port
        self.dut_port = controllers[1].grpc_port
        asyncio.set_event_loop(asyncio.new_event_loop())
        asyncio.get_event_loop().run_until_complete(self.__setup_adapter())

    def teardown_class(self):
        _teardown_class_core(rootcanal_running=self.rootcanal_running,
                             rootcanal_process=self.rootcanal_process,
                             rootcanal_logger=self.rootcanal_logger,
                             subprocess_wait_timeout_seconds=1)
        asyncio.get_event_loop().run_until_complete(self.__teardown_adapter())
