#!/usr/bin/env python3
#
#   Copyright 2019 - The Android Open Source Project
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

import importlib
import logging
import os
import traceback
import signal
import subprocess

from blueberry.facade import rootservice_pb2 as facade_rootservice
from functools import wraps
from grpc import RpcError

from blueberry.tests.gd.cert.async_subprocess_logger import AsyncSubprocessLogger
from blueberry.tests.gd.cert.context import append_test_context, get_current_context, pop_test_context, ContextLevel
from blueberry.tests.gd.cert.gd_device import MOBLY_CONTROLLER_CONFIG_NAME as CONTROLLER_CONFIG_NAME
from blueberry.tests.gd.cert.os_utils import get_gd_root
from blueberry.tests.gd.cert.os_utils import read_crash_snippet_and_log_tail
from blueberry.tests.gd.cert.os_utils import is_subprocess_alive
from blueberry.tests.gd.cert.os_utils import make_ports_available
from blueberry.tests.gd.cert.os_utils import TerminalColor
from blueberry.tests.gd.cert.tracelogger import TraceLogger

from mobly import asserts, signals
from mobly import base_test


class Timeout:

    def __init__(self, seconds=1, error_message='Timeout'):
        self.seconds = seconds
        self.error_message = error_message

    def handle_timeout(self, signum, frame):
        raise TimeoutError(self.error_message)

    def __enter__(self):
        signal.signal(signal.SIGALRM, self.handle_timeout)
        signal.alarm(self.seconds)

    def __exit__(self, type, value, traceback):
        signal.alarm(0)


class GdBaseTestClass(base_test.BaseTestClass):

    FUNCTION_CALL_TIMEOUT_SECONDS = 5
    SUBPROCESS_WAIT_TIMEOUT_SECONDS = 10

    def setup_class(self, dut_module, cert_module):
        self.dut_module = dut_module
        self.cert_module = cert_module
        self.log = TraceLogger(logging.getLogger())
        self.dut_coverage_info = None
        self.cert_coverage_info = None

    def teardown_class(self):
        # assume each test runs the same binary for dut and cert
        # generate coverage report after running all tests in a class
        if self.dut_coverage_info:
            self.dut.generate_coverage_report_for_host(self.dut_coverage_info)
            self.dut_coverage_info = None
        if self.cert_coverage_info:
            self.cert.generate_coverage_report_for_host(self.cert_coverage_info)
            self.cert_coverage_info = None

    def set_controller_properties_path(self, path):
        GD_DIR = os.path.join(os.getcwd(), os.pardir)
        self.controller_properties_file = os.path.join(GD_DIR, path)

    def setup_test(self):
        append_test_context(test_class_name=self.TAG, test_name=self.current_test_info.name)
        self.log_path_base = get_current_context().get_full_output_path()
        self.verbose_mode = bool(self.user_params.get('verbose_mode', False))
        for config in self.controller_configs[CONTROLLER_CONFIG_NAME]:
            config['verbose_mode'] = self.verbose_mode

        try:
            controller_properties_file = self.controller_properties_file
        except AttributeError:
            controller_properties_file = ''

        self.setup_rootcanal(controller_properties_file)

        # Parse and construct GD device objects
        self.register_controller(importlib.import_module('blueberry.tests.gd.cert.gd_device'), builtin=True)
        self.dut = self.gd_device[1]
        self.cert = self.gd_device[0]
        if self.dut.host_only_device:
            new_dut_coverage_info = self.dut.get_coverage_info()
            if self.dut_coverage_info:
                asserts.assert_true(self.dut_coverage_info == new_dut_coverage_info,
                                    msg="DUT coverage info must be the same for each test run, old: {}, new: {}".format(
                                        self.dut_coverage_info, new_dut_coverage_info))
            self.dut_coverage_info = new_dut_coverage_info
        if self.cert.host_only_device:
            new_cert_coverage_info = self.cert.get_coverage_info()
            if self.cert_coverage_info:
                asserts.assert_true(
                    self.cert_coverage_info == new_cert_coverage_info,
                    msg="CERT coverage info must be the same for each test run, old: {}, new: {}".format(
                        self.cert_coverage_info, new_cert_coverage_info))
            self.cert_coverage_info = new_cert_coverage_info

        try:
            self.dut.rootservice.StartStack(
                facade_rootservice.StartStackRequest(
                    module_under_test=facade_rootservice.BluetoothModule.Value(self.dut_module)))
        except RpcError as rpc_error:
            asserts.fail("Failed to start DUT stack, RpcError={!r}".format(rpc_error))
        try:
            self.cert.rootservice.StartStack(
                facade_rootservice.StartStackRequest(
                    module_under_test=facade_rootservice.BluetoothModule.Value(self.cert_module)))
        except RpcError as rpc_error:
            asserts.fail("Failed to start CERT stack, RpcError={!r}".format(rpc_error))
        self.dut.wait_channel_ready()
        self.cert.wait_channel_ready()

    def teardown_test(self):
        stack = ""
        try:
            with Timeout(seconds=self.FUNCTION_CALL_TIMEOUT_SECONDS):
                stack = "CERT"
                self.cert.rootservice.StopStack(facade_rootservice.StopStackRequest())
                stack = "DUT"
                self.dut.rootservice.StopStack(facade_rootservice.StopStackRequest())
        except RpcError as rpc_error:
            asserts.fail("Failed to stop {} stack, RpcError={!r}".format(stack, rpc_error))
        except TimeoutError:
            logging.error("Failed to stop {} stack in {} s".format(stack, self.FUNCTION_CALL_TIMEOUT_SECONDS))
        finally:
            # Destroy GD device objects
            self._controller_manager.unregister_controllers()
            self.teardown_rootcanal()
            pop_test_context()

    def setup_rootcanal(self, controller_properties_file=''):
        # Start root-canal if needed
        self.rootcanal_running = False
        self.rootcanal_logpath = None
        self.rootcanal_process = None
        self.rootcanal_logger = None
        if 'rootcanal' in self.controller_configs:
            self.rootcanal_running = True
            # Get root canal binary
            rootcanal = os.path.join(get_gd_root(), "root-canal")
            asserts.assert_true(os.path.isfile(rootcanal), "Root canal does not exist at %s" % rootcanal)

            # Get root canal log
            self.rootcanal_logpath = os.path.join(self.log_path_base, 'rootcanal_logs.txt')
            # Make sure ports are available
            rootcanal_config = self.controller_configs['rootcanal']
            rootcanal_test_port = int(rootcanal_config.get("test_port", "6401"))
            rootcanal_hci_port = int(rootcanal_config.get("hci_port", "6402"))
            rootcanal_link_layer_port = int(rootcanal_config.get("link_layer_port", "6403"))
            asserts.assert_true(
                make_ports_available((rootcanal_test_port, rootcanal_hci_port, rootcanal_link_layer_port)),
                "Failed to free ports rootcanal_test_port={}, rootcanal_hci_port={}, rootcanal_link_layer_port={}".
                format(rootcanal_test_port, rootcanal_hci_port, rootcanal_link_layer_port))

            # Start root canal process
            rootcanal_cmd = [
                rootcanal, '-test_port',
                str(rootcanal_test_port), '-hci_port',
                str(rootcanal_hci_port), '-link_port',
                str(rootcanal_link_layer_port), '-controller_properties_file=' + controller_properties_file
            ]
            self.log.debug("Running %s" % " ".join(rootcanal_cmd))
            self.rootcanal_process = subprocess.Popen(rootcanal_cmd,
                                                      cwd=get_gd_root(),
                                                      env=os.environ.copy(),
                                                      stdout=subprocess.PIPE,
                                                      stderr=subprocess.STDOUT,
                                                      universal_newlines=True)

            asserts.assert_true(self.rootcanal_process, msg="Cannot start root-canal at " + str(rootcanal))
            asserts.assert_true(is_subprocess_alive(self.rootcanal_process),
                                msg="root-canal stopped immediately after running")

            self.rootcanal_logger = AsyncSubprocessLogger(self.rootcanal_process, [self.rootcanal_logpath],
                                                          log_to_stdout=self.verbose_mode,
                                                          tag="rootcanal",
                                                          color=TerminalColor.MAGENTA)

            # Modify the device config to include the correct root-canal port
            for gd_device_config in self.controller_configs.get("GdDevice"):
                gd_device_config["rootcanal_port"] = str(rootcanal_hci_port)

    def teardown_rootcanal(self):
        if self.rootcanal_running:
            stop_signal = signal.SIGINT
            self.rootcanal_process.send_signal(stop_signal)
            try:
                return_code = self.rootcanal_process.wait(timeout=self.SUBPROCESS_WAIT_TIMEOUT_SECONDS)
            except subprocess.TimeoutExpired:
                logging.error("Failed to interrupt root canal via SIGINT, sending SIGKILL")
                stop_signal = signal.SIGKILL
                self.rootcanal_process.kill()
                try:
                    return_code = self.rootcanal_process.wait(timeout=self.SUBPROCESS_WAIT_TIMEOUT_SECONDS)
                except subprocess.TimeoutExpired:
                    logging.error("Failed to kill root canal")
                    return_code = -65536
            if return_code != 0 and return_code != -stop_signal:
                logging.error("rootcanal stopped with code: %d" % return_code)
            self.rootcanal_logger.stop()

    @staticmethod
    def get_module_reference_name(a_module):
        """Returns the module's module's submodule name as reference name.

        Args:
            a_module: Any module. Ideally, a controller module.
        Returns:
            A string corresponding to the module's name.
        """
        return a_module.__name__.split('.')[-1]

    def register_controller(self, controller_module, required=True, builtin=False):
        """Registers an controller module for a test class. Invokes Mobly's
        implementation of register_controller.
        """
        module_ref_name = self.get_module_reference_name(controller_module)
        module_config_name = controller_module.MOBLY_CONTROLLER_CONFIG_NAME

        # Get controller objects from Mobly's register_controller
        controllers = self._controller_manager.register_controller(controller_module, required=required)
        if not controllers:
            return None

        # Log controller information
        # Implementation of "get_info" is optional for a controller module.
        if hasattr(controller_module, "get_info"):
            controller_info = controller_module.get_info(controllers)
            self.log.info("Controller %s: %s", module_config_name, controller_info)

        if builtin:
            setattr(self, module_ref_name, controllers)
        return controllers

    def __getattribute__(self, name):
        attr = super().__getattribute__(name)
        if not callable(attr) or not GdBaseTestClass.__is_entry_function(name):
            return attr

        @wraps(attr)
        def __wrapped(*args, **kwargs):
            try:
                return attr(*args, **kwargs)
            except RpcError as e:
                exception_info = "".join(traceback.format_exception(e.__class__, e, e.__traceback__))
                raise signals.TestFailure("RpcError during test\n\nRpcError:\n\n%s\n%s" %
                                          (exception_info, self.__dump_crashes()))

        return __wrapped

    __ENTRY_METHODS = {"setup_class", "teardown_class", "setup_test", "teardown_test"}

    @staticmethod
    def __is_entry_function(name):
        return name.startswith("test_") or name in GdBaseTestClass.__ENTRY_METHODS

    def __dump_crashes(self):
        """
        return: formatted stack traces if found, or last few lines of log
        """
        dut_crash, dut_log_tail = self.dut.get_crash_snippet_and_log_tail()
        cert_crash, cert_log_tail = self.cert.get_crash_snippet_and_log_tail()
        rootcanal_crash = None
        rootcanal_log_tail = None
        if self.rootcanal_running and not is_subprocess_alive(self.rootcanal_process):
            rootcanal_crash, roocanal_log_tail = read_crash_snippet_and_log_tail(self.rootcanal_logpath)

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
