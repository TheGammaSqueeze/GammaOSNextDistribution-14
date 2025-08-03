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

from abc import ABC
from abc import abstractmethod
from datetime import datetime
import inspect
import logging
import os
import pathlib
import shutil
import signal
import subprocess
import time
from typing import List

import grpc

from google.protobuf import empty_pb2 as empty_proto

from blueberry.tests.gd.cert import asserts
from blueberry.tests.gd.cert.adb import BlueberryAdbProxy
from blueberry.tests.gd.cert.adb import UTF_8
from blueberry.tests.gd.cert.async_subprocess_logger import AsyncSubprocessLogger
from blueberry.tests.gd.cert.context import get_current_context
from blueberry.tests.gd.cert.logging_client_interceptor import LoggingClientInterceptor
from blueberry.tests.gd.cert.os_utils import get_gd_root
from blueberry.tests.gd.cert.os_utils import read_crash_snippet_and_log_tail
from blueberry.tests.gd.cert.os_utils import is_subprocess_alive
from blueberry.tests.gd.cert.os_utils import make_ports_available
from blueberry.tests.gd.cert.os_utils import TerminalColor

from blueberry.facade import rootservice_pb2_grpc as facade_rootservice_pb2_grpc
from blueberry.facade.hal import hal_facade_pb2_grpc
from blueberry.facade.hci import hci_facade_pb2_grpc
from blueberry.facade.hci import acl_manager_facade_pb2_grpc
from blueberry.facade.hci import controller_facade_pb2_grpc
from blueberry.facade.hci import le_acl_manager_facade_pb2_grpc
from blueberry.facade.hci import le_advertising_manager_facade_pb2_grpc
from blueberry.facade.hci import le_initiator_address_facade_pb2_grpc
from blueberry.facade.hci import le_scanning_manager_facade_pb2_grpc
from blueberry.facade.l2cap.classic import facade_pb2_grpc as l2cap_facade_pb2_grpc
from blueberry.facade.l2cap.le import facade_pb2_grpc as l2cap_le_facade_pb2_grpc
from blueberry.facade.iso import facade_pb2_grpc as iso_facade_pb2_grpc
from blueberry.facade.neighbor import facade_pb2_grpc as neighbor_facade_pb2_grpc
from blueberry.facade.security import facade_pb2_grpc as security_facade_pb2_grpc
from blueberry.facade.shim import facade_pb2_grpc as shim_facade_pb2_grpc

from mobly import utils
from mobly.controllers.android_device_lib.adb import AdbError

ADB_FILE_NOT_EXIST_ERROR = "No such file or directory"
GRPC_START_TIMEOUT_SEC = 15
MOBLY_CONTROLLER_CONFIG_NAME = "GdDevice"
PORT_FORWARDING_ERROR_MSG_PREFIX = "During port forwarding cleanup: "
PULL_LOG_FILE_ERROR_MSG_PREFIX = "While trying to pull log files"


def create(configs):
    if not configs:
        raise Exception("Configuration is empty")
    elif not isinstance(configs, list):
        raise Exception("Configuration should be a list")
    return get_instances_with_configs(configs)


def destroy(devices):
    for device in devices:
        try:
            device.teardown()
        except:
            logging.exception("[%s] Failed to clean up properly due to" % device.label)


def get_info(devices):
    return []


def replace_vars(string, config):
    serial_number = config.get("serial_number")
    if serial_number is None:
        serial_number = ""
    rootcanal_port = config.get("rootcanal_port")
    if rootcanal_port is None:
        rootcanal_port = ""
    if serial_number == "DUT" or serial_number == "CERT":
        raise Exception("Did you forget to configure the serial number?")
    return string.replace("$GD_ROOT", get_gd_root()) \
        .replace("$(grpc_port)", config.get("grpc_port")) \
        .replace("$(grpc_root_server_port)", config.get("grpc_root_server_port")) \
        .replace("$(rootcanal_port)", rootcanal_port) \
        .replace("$(signal_port)", config.get("signal_port")) \
        .replace("$(serial_number)", serial_number)


def get_instances_with_configs(configs):
    print(configs)
    devices = []
    for config in configs:
        resolved_cmd = []
        for arg in config["cmd"]:
            logging.debug(arg)
            resolved_cmd.append(replace_vars(arg, config))
        verbose_mode = bool(config.get('verbose_mode', False))
        if config.get("serial_number"):
            device = GdAndroidDevice(config["grpc_port"], config["grpc_root_server_port"], config["signal_port"],
                                     resolved_cmd, config["label"], MOBLY_CONTROLLER_CONFIG_NAME, config["name"],
                                     config["serial_number"], verbose_mode)
        else:
            device = GdHostOnlyDevice(config["grpc_port"], config["grpc_root_server_port"], config["signal_port"],
                                      resolved_cmd, config["label"], MOBLY_CONTROLLER_CONFIG_NAME, config["name"],
                                      verbose_mode)
        device.setup()
        devices.append(device)
    return devices


class GdDeviceBase(ABC):
    """
    Base GD device class that covers common traits which assumes that the
    device must be driven by a driver-like backing process that takes following
    command line arguments:
    --grpc-port: main entry port for facade services
    --root-server-port: management port for starting and stopping services
    --btsnoop: path to btsnoop HCI log
    --signal-port: signaling port to indicate that backing process is started
    --rootcanal-port: root-canal HCI port, optional
    """

    WAIT_CHANNEL_READY_TIMEOUT_SECONDS = 10
    WAIT_SIGINT_TIMEOUT_SECONDS = 5
    WAIT_SIGKILL_TIMEOUT_SECONDS = 1

    def __init__(self, grpc_port: str, grpc_root_server_port: str, signal_port: str, cmd: List[str], label: str,
                 type_identifier: str, name: str, verbose_mode: bool):
        """Verify arguments and log path, initialize Base GD device, common traits
         for both device based and host only GD cert tests
        :param grpc_port: main gRPC service port
        :param grpc_root_server_port: gRPC root server port
        :param signal_port: signaling port for backing process start up
        :param cmd: list of arguments to run in backing process
        :param label: device label used in logs
        :param type_identifier: device type identifier used in logs
        :param name: name of device used in logs
        """
        # Must be at the first line of __init__ method
        values = locals()
        arguments = [values[arg] for arg in inspect.getfullargspec(GdDeviceBase.__init__).args if arg != "verbose_mode"]
        asserts.assert_true(all(arguments), "All arguments to GdDeviceBase must not be None nor empty")
        asserts.assert_true(all(cmd), "cmd list should not have None nor empty component")
        self.verbose_mode = verbose_mode
        self.host_only_device = False
        self.grpc_root_server_port = int(grpc_root_server_port)
        self.grpc_port = int(grpc_port)
        self.signal_port = int(signal_port)
        self.name = name
        self.type_identifier = type_identifier
        self.label = label
        self.log_path_base = get_current_context().get_full_output_path()
        self.test_runner_base_path = \
            get_current_context().get_base_output_path()
        self.backing_process_log_path = os.path.join(self.log_path_base,
                                                     '%s_%s_backing_logs.txt' % (self.type_identifier, self.label))
        if "--btsnoop=" not in " ".join(cmd):
            cmd.append("--btsnoop=%s" % os.path.join(self.log_path_base, '%s_btsnoop_hci.log' % self.label))
        if "--btsnooz=" not in " ".join(cmd):
            cmd.append("--btsnooz=%s" % os.path.join(self.log_path_base, '%s_btsnooz_hci.log' % self.label))
        if "--btconfig=" not in " ".join(cmd):
            cmd.append("--btconfig=%s" % os.path.join(self.log_path_base, '%s_bt_config.conf' % self.label))
        self.cmd = cmd
        self.environment = os.environ.copy()
        if "cert" in self.label:
            self.terminal_color = TerminalColor.BLUE
        else:
            self.terminal_color = TerminalColor.YELLOW

    def setup(self):
        """Set up this device for test, ensure signal port is available and backing process is started and alive,
        must run before using this device.
        - After calling this, teardown() must be called when test finishes
        - Should be executed after children classes' setup() methods
        :return:
        """
        # Start backing process
        logging.debug("[%s] Running %s %s" % (self.type_identifier, self.label, " ".join(self.cmd)))
        self.backing_process = subprocess.Popen(
            self.cmd,
            cwd=get_gd_root(),
            env=self.environment,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            universal_newlines=True)

        self.backing_process_logger = AsyncSubprocessLogger(
            self.backing_process, [self.backing_process_log_path],
            log_to_stdout=self.verbose_mode,
            tag=self.label,
            color=self.terminal_color)

        asserts.assert_true(
            self.backing_process, msg="[%s] failed to open backing process for %s" % (self.type_identifier, self.label))
        self.is_backing_process_alive = is_subprocess_alive(self.backing_process)

        asserts.assert_true(
            self.is_backing_process_alive,
            msg="[%s] backing process for %s died after starting" % (self.type_identifier, self.label))


        # If gRPC root server port is not specified, we can skip settings up the root server
        if self.grpc_root_server_port != -1:
            # Setup gRPC management channels
            self.grpc_root_server_channel = grpc.insecure_channel("localhost:%d" % self.grpc_root_server_port)

            self.grpc_root_server_ready = False

            try:
                logging.info("[%s] Waiting to connect to gRPC root server for %s, timeout is %d seconds" %
                             (self.type_identifier, self.label, GRPC_START_TIMEOUT_SEC))
                grpc.channel_ready_future(self.grpc_root_server_channel).result(timeout=GRPC_START_TIMEOUT_SEC)
                logging.info(
                    "[%s] Successfully connected to gRPC root server for %s" % (self.type_identifier, self.label))
                self.grpc_root_server_ready = True
            except grpc.FutureTimeoutError:
                logging.error("[%s] Failed to connect to gRPC root server for %s" % (self.type_identifier, self.label))

            asserts.assert_true(
                self.grpc_root_server_ready, msg="gRPC root server did not start after running " + " ".join(self.cmd))

        self.grpc_channel = grpc.insecure_channel("localhost:%d" % self.grpc_port)

        if self.verbose_mode:
            self.grpc_channel = grpc.intercept_channel(self.grpc_channel, LoggingClientInterceptor(self.label))

        # Establish services from facades
        if self.grpc_root_server_port != -1:
            self.rootservice = facade_rootservice_pb2_grpc.RootFacadeStub(self.grpc_root_server_channel)

        self.hal = hal_facade_pb2_grpc.HciHalFacadeStub(self.grpc_channel)
        self.controller_read_only_property = facade_rootservice_pb2_grpc.ReadOnlyPropertyStub(self.grpc_channel)
        self.hci = hci_facade_pb2_grpc.HciFacadeStub(self.grpc_channel)
        self.l2cap = l2cap_facade_pb2_grpc.L2capClassicModuleFacadeStub(self.grpc_channel)
        self.l2cap_le = l2cap_le_facade_pb2_grpc.L2capLeModuleFacadeStub(self.grpc_channel)
        self.iso = iso_facade_pb2_grpc.IsoModuleFacadeStub(self.grpc_channel)
        self.hci_acl_manager = acl_manager_facade_pb2_grpc.AclManagerFacadeStub(self.grpc_channel)
        self.hci_le_acl_manager = le_acl_manager_facade_pb2_grpc.LeAclManagerFacadeStub(self.grpc_channel)
        self.hci_le_initiator_address = le_initiator_address_facade_pb2_grpc.LeInitiatorAddressFacadeStub(
            self.grpc_channel)
        self.hci_controller = controller_facade_pb2_grpc.ControllerFacadeStub(self.grpc_channel)
        self.hci_controller.GetMacAddressSimple = lambda: self.hci_controller.GetMacAddress(empty_proto.Empty()).address
        self.hci_controller.GetLocalNameSimple = lambda: self.hci_controller.GetLocalName(empty_proto.Empty()).name
        self.hci_le_advertising_manager = le_advertising_manager_facade_pb2_grpc.LeAdvertisingManagerFacadeStub(
            self.grpc_channel)
        self.hci_le_scanning_manager = le_scanning_manager_facade_pb2_grpc.LeScanningManagerFacadeStub(
            self.grpc_channel)
        self.neighbor = neighbor_facade_pb2_grpc.NeighborFacadeStub(self.grpc_channel)
        self.security = security_facade_pb2_grpc.SecurityModuleFacadeStub(self.grpc_channel)
        self.shim = shim_facade_pb2_grpc.ShimFacadeStub(self.grpc_channel)

    def get_crash_snippet_and_log_tail(self):
        if is_subprocess_alive(self.backing_process):
            return None, None

        return read_crash_snippet_and_log_tail(self.backing_process_log_path)

    def teardown(self):
        """Tear down this device and clean up any resources.
        - Must be called after setup()
        - Should be executed before children classes' teardown()
        :return:
        """
        self.grpc_channel.close()
        if self.grpc_root_server_port != -1:
            self.grpc_root_server_channel.close()
        stop_signal = self.gracefully_stop_backing_process()
        try:
            if stop_signal == 0:
                raise RuntimeError("Failed to gracefully shutdown backing process")
            return_code = self.backing_process.wait(timeout=self.WAIT_SIGINT_TIMEOUT_SECONDS)
        except (subprocess.TimeoutExpired, RuntimeError):
            logging.error("[%s] Failed to interrupt backing process via SIGINT, sending SIGKILL" % self.label)
            stop_signal = signal.SIGKILL
            self.backing_process.kill()
            try:
                return_code = self.backing_process.wait(timeout=self.WAIT_SIGKILL_TIMEOUT_SECONDS)
            except subprocess.TimeoutExpired:
                logging.error("Failed to kill backing process")
                return_code = -65536
        if return_code not in [-stop_signal, 0]:
            logging.error("backing process %s stopped with code: %d" % (self.label, return_code))
        self.backing_process_logger.stop()

    def wait_channel_ready(self):
        future = grpc.channel_ready_future(self.grpc_channel)
        try:
            future.result(timeout=self.WAIT_CHANNEL_READY_TIMEOUT_SECONDS)
        except grpc.FutureTimeoutError:
            asserts.fail("[%s] wait channel ready timeout" % self.label)

    @abstractmethod
    def gracefully_stop_backing_process(self):
        return NotImplemented


class GdHostOnlyDevice(GdDeviceBase):
    """
    Host only device where the backing process is running on the host machine
    """

    def __init__(self, grpc_port: str, grpc_root_server_port: str, signal_port: str, cmd: List[str], label: str,
                 type_identifier: str, name: str, verbose_mode: bool):
        super().__init__(grpc_port, grpc_root_server_port, signal_port, cmd, label, MOBLY_CONTROLLER_CONFIG_NAME, name,
                         verbose_mode)
        self.host_only_device = True
        # Enable LLVM code coverage output for host only tests
        self.backing_process_profraw_path = pathlib.Path(self.log_path_base).joinpath(
            "%s_%s_backing_coverage.profraw" % (self.type_identifier, self.label))
        self.environment["LLVM_PROFILE_FILE"] = str(self.backing_process_profraw_path)
        llvm_binutils = pathlib.Path(get_gd_root()).joinpath("llvm_binutils").joinpath("bin")
        llvm_symbolizer = llvm_binutils.joinpath("llvm-symbolizer")
        if llvm_symbolizer.is_file():
            self.environment["ASAN_SYMBOLIZER_PATH"] = llvm_symbolizer
        else:
            logging.warning("[%s] Cannot find LLVM symbolizer at %s" % (self.label, str(llvm_symbolizer)))
        self.profdata_path = self.get_coverage_profdata_path_for_host(self.test_runner_base_path, self.type_identifier,
                                                                      self.label)

    def setup(self):
        # Ensure ports are available
        # Only check on host only test, for Android devices, these ports will
        # be opened on Android device and host machine ports will be occupied
        # by sshd or adb forwarding
        ports_needed = [self.grpc_port,
                        self.grpc_root_server_port] if self.grpc_root_server_port != -1 else [self.grpc_port]
        asserts.assert_true(
            make_ports_available(ports_needed), "[%s] Failed to make backing process ports available" % self.label)
        super().setup()

    def teardown(self):
        super().teardown()
        self.merge_coverage_profdata_for_host(self.backing_process_profraw_path, self.profdata_path, self.label)

    def get_coverage_info(self):
        """
        Get information needed for coverage reporting
        :return: a dictionary with all information needed for coverage reporting
        """
        return {
            "profdata_path": self.profdata_path,
            "label": self.label,
            "test_runner_base_path": self.test_runner_base_path,
            "type_identifier": self.type_identifier,
            "stack_bin": self.cmd[0]
        }

    def get_coverage_profdata_path_for_host(self, test_runner_base_path, type_identifier, label) -> pathlib.Path:
        return pathlib.Path(test_runner_base_path).parent.parent.joinpath(
            "%s_%s_backing_process_coverage.profdata" % (type_identifier, label))

    def merge_coverage_profdata_for_host(self, backing_process_profraw_path, profdata_path: pathlib.Path, label):
        if not backing_process_profraw_path.is_file():
            logging.info("[%s] Skip coverage report as there is no profraw file at %s" %
                         (label, str(backing_process_profraw_path)))
            return
        try:
            if backing_process_profraw_path.stat().st_size <= 0:
                logging.info("[%s] Skip coverage report as profraw file is empty at %s" %
                             (label, str(backing_process_profraw_path)))
                return
        except OSError:
            logging.info("[%s] Skip coverage report as profraw file is inaccessible at %s" %
                         (label, str(backing_process_profraw_path)))
            return
        llvm_binutils = pathlib.Path(get_gd_root()).joinpath("llvm_binutils").joinpath("bin")
        llvm_profdata = llvm_binutils.joinpath("llvm-profdata")
        if not llvm_profdata.is_file():
            logging.info("[%s] Skip coverage report as llvm-profdata is not found at %s" % (label, str(llvm_profdata)))
            return
        logging.info("[%s] Merging coverage profdata" % label)
        profdata_path_tmp = profdata_path.parent / (profdata_path.stem + "_tmp" + profdata_path.suffix)
        # Merge with existing profdata if possible
        profdata_cmd = [str(llvm_profdata), "merge", "-sparse", str(backing_process_profraw_path)]
        if profdata_path.is_file():
            profdata_cmd.append(str(profdata_path))
        profdata_cmd += ["-o", str(profdata_path_tmp)]
        logging.debug("Running llvm_profdata: %s" % " ".join(profdata_cmd))
        result = subprocess.run(profdata_cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        if result.returncode != 0:
            logging.warning("[%s] Failed to index profdata, cmd result: %r" % (label, result))
            profdata_path.unlink(missing_ok=True)
            return
        shutil.move(profdata_path_tmp, profdata_path)

    def generate_coverage_report_for_host(self, coverage_info):
        label = coverage_info["label"]
        test_runner_base_path = coverage_info["test_runner_base_path"]
        type_identifier = coverage_info["type_identifier"]
        profdata_path = coverage_info["profdata_path"]
        stack_bin = coverage_info["stack_bin"]
        llvm_binutils = pathlib.Path(get_gd_root()).joinpath("llvm_binutils").joinpath("bin")
        llvm_cov = llvm_binutils.joinpath("llvm-cov")
        if not llvm_cov.is_file():
            logging.info("[%s] Skip coverage report as llvm-cov is not found at %s" % (label, str(llvm_cov)))
            return
        logging.info("[%s] Generating coverage report in JSON" % label)
        coverage_result_path = pathlib.Path(test_runner_base_path).parent.parent.joinpath(
            "%s_%s_backing_process_coverage.json" % (type_identifier, label))
        with coverage_result_path.open("w") as coverage_result_file:
            llvm_cov_export_cmd = [
                str(llvm_cov), "export", "--format=text", "--ignore-filename-regex", "(external|out).*",
                "--instr-profile",
                str(profdata_path),
                str(stack_bin)
            ]
            logging.debug("Running llvm_cov export: %s" % " ".join(llvm_cov_export_cmd))
            result = subprocess.run(
                llvm_cov_export_cmd,
                stderr=subprocess.PIPE,
                stdout=coverage_result_file,
                cwd=os.path.join(get_gd_root()))
        if result.returncode != 0:
            logging.warning("[%s] Failed to generated coverage report, cmd result: %r" % (label, result))
            coverage_result_path.unlink(missing_ok=True)
            return
        logging.info("[%s] Generating coverage summary in text" % label)
        coverage_summary_path = pathlib.Path(test_runner_base_path).parent.parent.joinpath(
            "%s_%s_backing_process_coverage_summary.txt" % (type_identifier, label))
        with coverage_summary_path.open("w") as coverage_summary_file:
            llvm_cov_report_cmd = [
                str(llvm_cov), "report", "--ignore-filename-regex", "(external|out).*", "--instr-profile",
                str(profdata_path),
                str(stack_bin)
            ]
            logging.debug("Running llvm_cov report: %s" % " ".join(llvm_cov_report_cmd))
            result = subprocess.run(
                llvm_cov_report_cmd,
                stderr=subprocess.PIPE,
                stdout=coverage_summary_file,
                cwd=os.path.join(get_gd_root()))
        if result.returncode != 0:
            logging.warning("[%s] Failed to generated coverage summary, cmd result: %r" % (label, result))
            coverage_summary_path.unlink(missing_ok=True)

    def gracefully_stop_backing_process(self):
        stop_signal = signal.SIGINT
        self.backing_process.send_signal(stop_signal)
        return stop_signal


class GdAndroidDevice(GdDeviceBase):
    """Real Android device where the backing process is running on it
    """

    WAIT_FOR_DEVICE_TIMEOUT_SECONDS = 180
    WAIT_FOR_DEVICE_SIGINT_TIMEOUT_SECONDS = 1
    ADB_ABORT_EXIT_CODE = 134
    DEVICE_LIB_DIR = "/system/lib64"
    DEVICE_BIN_DIR = "/system/bin"

    def __init__(self, grpc_port: str, grpc_root_server_port: str, signal_port: str, cmd: List[str], label: str,
                 type_identifier: str, name: str, serial_number: str, verbose_mode: bool):
        super().__init__(grpc_port, grpc_root_server_port, signal_port, cmd, label, type_identifier, name, verbose_mode)
        asserts.assert_true(serial_number, "serial_number must not be None nor empty")
        self.serial_number = serial_number
        self.adb = BlueberryAdbProxy(serial_number)

    def setup(self):
        logging.info("Setting up device %s %s" % (self.label, self.serial_number))
        asserts.assert_true(self.adb.ensure_root(), "device %s cannot run as root", self.serial_number)
        self.ensure_verity_disabled()
        logging.info("Confirmed that verity is disabled on device %s %s" % (self.label, self.serial_number))

        # Try freeing ports and ignore results
        asserts.assert_true(
            make_ports_available((self.grpc_port, self.grpc_root_server_port, self.signal_port)),
            "[%s] Failed to make backing process ports available" % self.label)
        self.cleanup_port_forwarding()
        self.sync_device_time()
        logging.info("Ports cleaned up and clock is set for device %s %s" % (self.label, self.serial_number))

        # Set up port forwarding or reverse or die
        self.tcp_forward_or_die(self.grpc_port, self.grpc_port)
        if self.grpc_root_server_port != -1:
            self.tcp_forward_or_die(self.grpc_root_server_port, self.grpc_root_server_port)
        self.tcp_reverse_or_die(self.signal_port, self.signal_port)
        logging.info("Port forwarding done on device %s %s" % (self.label, self.serial_number))

        # Push test binaries
        local_dir = os.path.join(get_gd_root(), "target")

        def generate_dir_pair(local_dir, device_dir, filename):
            return os.path.join(local_dir, filename), os.path.join(device_dir, filename)

        # Do not override exist libraries shared by other binaries on the Android device to avoid corrupting the Android device
        self.push_or_die(*generate_dir_pair(local_dir, self.DEVICE_BIN_DIR, "bluetooth_stack_with_facade"))
        self.push_or_die(
            *generate_dir_pair(local_dir, self.DEVICE_LIB_DIR, "android.hardware.bluetooth@1.0.so"),
            overwrite_existing=False)
        self.push_or_die(
            *generate_dir_pair(local_dir, self.DEVICE_LIB_DIR, "android.hardware.bluetooth@1.1.so"),
            overwrite_existing=False)
        self.push_or_die(
            *generate_dir_pair(local_dir, self.DEVICE_LIB_DIR, "libandroid_runtime_lazy.so"), overwrite_existing=False)
        self.push_or_die(*generate_dir_pair(local_dir, self.DEVICE_LIB_DIR, "libbase.so"), overwrite_existing=False)
        self.push_or_die(
            *generate_dir_pair(local_dir, self.DEVICE_LIB_DIR, "libbinder_ndk.so"), overwrite_existing=False)
        self.push_or_die(*generate_dir_pair(local_dir, self.DEVICE_LIB_DIR, "libbinder.so"), overwrite_existing=False)
        self.push_or_die(*generate_dir_pair(local_dir, self.DEVICE_LIB_DIR, "libc++.so"), overwrite_existing=False)
        # libclang_rt.asan-aarch64-android.so is only needed for ASAN build and is automatically included on device
        #self.push_or_die(
        #    *generate_dir_pair(local_dir, self.DEVICE_LIB_DIR, "libclang_rt.asan-aarch64-android.so"),
        #    overwrite_existing=False)
        self.push_or_die(*generate_dir_pair(local_dir, self.DEVICE_LIB_DIR, "libcrypto.so"), overwrite_existing=False)
        self.push_or_die(*generate_dir_pair(local_dir, self.DEVICE_LIB_DIR, "libcutils.so"), overwrite_existing=False)
        self.push_or_die(
            *generate_dir_pair(local_dir, self.DEVICE_LIB_DIR, "libgrpc_wrap.so"), overwrite_existing=False)
        self.push_or_die(*generate_dir_pair(local_dir, self.DEVICE_LIB_DIR, "libgrpc++.so"))
        self.push_or_die(*generate_dir_pair(local_dir, self.DEVICE_LIB_DIR, "libhidlbase.so"), overwrite_existing=False)
        self.push_or_die(*generate_dir_pair(local_dir, self.DEVICE_LIB_DIR, "liblog.so"), overwrite_existing=False)
        self.push_or_die(*generate_dir_pair(local_dir, self.DEVICE_LIB_DIR, "liblzma.so"), overwrite_existing=False)
        self.push_or_die(*generate_dir_pair(local_dir, self.DEVICE_LIB_DIR, "libprotobuf-cpp-full.so"))
        self.push_or_die(*generate_dir_pair(local_dir, self.DEVICE_LIB_DIR, "libssl.so"), overwrite_existing=False)
        self.push_or_die(*generate_dir_pair(local_dir, self.DEVICE_LIB_DIR, "libgrpc++.so"))
        self.push_or_die(*generate_dir_pair(local_dir, self.DEVICE_LIB_DIR, "libstatslog_bt.so"))
        self.push_or_die(
            *generate_dir_pair(local_dir, self.DEVICE_LIB_DIR, "libunwindstack.so"), overwrite_existing=False)
        self.push_or_die(*generate_dir_pair(local_dir, self.DEVICE_LIB_DIR, "libutils.so"), overwrite_existing=False)
        self.push_or_die(*generate_dir_pair(local_dir, self.DEVICE_LIB_DIR, "libgrpc++.so"))
        self.push_or_die(*generate_dir_pair(local_dir, self.DEVICE_LIB_DIR, "libz.so"), overwrite_existing=False)

        logging.info("Binaries pushed to device %s %s" % (self.label, self.serial_number))

        try:
            self.adb.shell("rm /data/misc/bluetooth/logs/btsnoop_hci.log")
        except AdbError as error:
            if ADB_FILE_NOT_EXIST_ERROR not in str(error):
                logging.error("Error during setup: " + str(error))

        try:
            self.adb.shell("rm /data/misc/bluetooth/logs/btsnooz_hci.log")
        except AdbError as error:
            if ADB_FILE_NOT_EXIST_ERROR not in str(error):
                logging.error("Error during setup: " + str(error))

        try:
            self.adb.shell("rm /data/misc/bluedroid/bt_config.conf")
        except AdbError as error:
            if ADB_FILE_NOT_EXIST_ERROR not in str(error):
                logging.error("Error during setup: " + str(error))

        try:
            self.adb.shell("rm /data/misc/bluedroid/bt_config.bak")
        except AdbError as error:
            if ADB_FILE_NOT_EXIST_ERROR not in str(error):
                logging.error("Error during setup: " + str(error))
        logging.info("Old logs removed from device %s %s" % (self.label, self.serial_number))

        # Ensure Bluetooth is disabled
        self.ensure_no_output(self.adb.shell("settings put global ble_scan_always_enabled 0"))
        self.adb.shell("cmd bluetooth_manager disable")
        device_bt_state = int(self.adb.shell("settings get global bluetooth_on"))
        asserts.assert_equal(device_bt_state, 0,
                             "Failed to disable Bluetooth on device %s %s" % (self.label, self.serial_number))
        logging.info("Bluetooth disabled on device %s %s" % (self.label, self.serial_number))

        # Start logcat logging
        self.logcat_output_path = os.path.join(
            self.log_path_base, '%s_%s_%s_logcat_logs.txt' % (self.type_identifier, self.label, self.serial_number))
        self.logcat_cmd = ["adb", "-s", self.serial_number, "logcat", "-T", "1", "-v", "year", "-v", "uid"]
        logging.debug("Running %s", " ".join(self.logcat_cmd))
        self.logcat_process = subprocess.Popen(
            self.logcat_cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, universal_newlines=True)
        asserts.assert_true(self.logcat_process, msg="Cannot start logcat_process at " + " ".join(self.logcat_cmd))
        asserts.assert_true(
            is_subprocess_alive(self.logcat_process),
            msg="logcat_process stopped immediately after running " + " ".join(self.logcat_cmd))
        self.logcat_logger = AsyncSubprocessLogger(
            self.logcat_process, [self.logcat_output_path],
            log_to_stdout=self.verbose_mode,
            tag="%s_%s" % (self.label, self.serial_number),
            color=self.terminal_color)

        # Done run parent setup
        logging.info("Done preparation for %s, starting backing process" % self.serial_number)
        super().setup()

    def teardown(self):
        super().teardown()
        stop_signal = signal.SIGINT
        self.logcat_process.send_signal(stop_signal)
        try:
            return_code = self.logcat_process.wait(timeout=self.WAIT_FOR_DEVICE_SIGINT_TIMEOUT_SECONDS)
        except subprocess.TimeoutExpired:
            logging.error("[%s_%s] Failed to interrupt logcat process via SIGINT, sending SIGKILL" %
                          (self.label, self.serial_number))
            stop_signal = signal.SIGKILL
            self.logcat_process.kill()
            try:
                return_code = self.logcat_process.wait(timeout=self.WAIT_SIGKILL_TIMEOUT_SECONDS)
            except subprocess.TimeoutExpired:
                logging.error("Failed to kill logcat_process %s %s" % (self.label, self.serial_number))
                return_code = -65536
        if return_code not in [-stop_signal, 0]:
            logging.error("logcat_process %s_%s stopped with code: %d" % (self.label, self.serial_number, return_code))
        self.logcat_logger.stop()
        self.cleanup_port_forwarding()
        self.pull_logs(self.log_path_base)

    def pull_logs(self, base_dir):
        try:
            self.adb.pull([
                "/data/misc/bluetooth/logs/btsnoop_hci.log",
                str(os.path.join(base_dir, "%s_btsnoop_hci.log" % self.label))
            ])
        except AdbError as error:
            # Some tests have no snoop logs, and that's OK
            if ADB_FILE_NOT_EXIST_ERROR not in str(error):
                logging.error(PULL_LOG_FILE_ERROR_MSG_PREFIX + str(error))
        try:
            self.adb.pull(
                ["/data/misc/bluedroid/bt_config.conf",
                 str(os.path.join(base_dir, "%s_bt_config.conf" % self.label))])
        except AdbError as error:
            # Some tests have no config file, and that's OK
            if ADB_FILE_NOT_EXIST_ERROR not in str(error):
                logging.error(PULL_LOG_FILE_ERROR_MSG_PREFIX + str(error))
        try:
            self.adb.pull(
                ["/data/misc/bluedroid/bt_config.bak",
                 str(os.path.join(base_dir, "%s_bt_config.bak" % self.label))])
        except AdbError as error:
            # Some tests have no config.bak file, and that's OK
            if ADB_FILE_NOT_EXIST_ERROR not in str(error):
                logging.error(PULL_LOG_FILE_ERROR_MSG_PREFIX + str(error))

    def cleanup_port_forwarding(self):
        try:
            self.adb.remove_tcp_forward(self.grpc_port)
        except AdbError as error:
            msg = PORT_FORWARDING_ERROR_MSG_PREFIX + str(error)
            if "not found" in msg:
                logging.debug(msg)
            else:
                logging.error(msg)

        try:
            if self.grpc_root_server_port != -1:
                self.adb.remove_tcp_forward(self.grpc_root_server_port)
        except AdbError as error:
            msg = PORT_FORWARDING_ERROR_MSG_PREFIX + str(error)
            if "not found" in msg:
                logging.debug(msg)
            else:
                logging.error(msg)

        try:
            self.adb.reverse(["--remove", "tcp:%d" % self.signal_port])
        except AdbError as error:
            msg = PORT_FORWARDING_ERROR_MSG_PREFIX + str(error)
            if "not found" in msg:
                logging.debug(msg)
            else:
                logging.error(msg)

    @staticmethod
    def ensure_no_output(result):
        """
        Ensure a command has not output
        """
        asserts.assert_true(
            result is None or len(result) == 0, msg="command returned something when it shouldn't: %s" % result)

    def sync_device_time(self):
        self.adb.shell("settings put global auto_time 0")
        self.adb.shell("settings put global auto_time_zone 0")
        device_tz = self.adb.shell("date +%z").decode(UTF_8).rstrip()
        asserts.assert_true(device_tz, "date +%z must return device timezone, "
                            "but returned {} instead".format(device_tz))
        host_tz = time.strftime("%z")
        if device_tz != host_tz:
            target_timezone = utils.get_timezone_olson_id()
            logging.debug("Device timezone %s does not match host timezone %s, "
                          "syncing them by setting timezone to %s" % (device_tz, host_tz, target_timezone))
            self.adb.shell("setprop persist.sys.timezone %s" % target_timezone)
            self.reboot()
            self.adb.remount()
            device_tz = self.adb.shell("date +%z").decode(UTF_8).rstrip()
            asserts.assert_equal(
                host_tz, device_tz, "Device timezone %s still does not match host "
                "timezone %s after reset" % (device_tz, host_tz))
        self.adb.shell("date %s" % time.strftime("%m%d%H%M%Y.%S"))
        datetime_format = "%Y-%m-%dT%H:%M:%S%z"
        try:
            device_time = datetime.strptime(
                self.adb.shell("date +'%s'" % datetime_format).decode(UTF_8).rstrip(), datetime_format)
        except ValueError:
            asserts.fail("Failed to get time after sync")
            return
        # Include ADB delay that might be longer in SSH environment
        max_delta_seconds = 3
        host_time = datetime.now(tz=device_time.tzinfo)
        asserts.assert_almost_equal(
            (device_time - host_time).total_seconds(),
            0,
            msg="Device time %s and host time %s off by >%dms after sync" %
            (device_time.isoformat(), host_time.isoformat(), int(max_delta_seconds * 1000)),
            delta=max_delta_seconds)

    def push_or_die(self, src_file_path, dst_file_path, push_timeout=300, overwrite_existing=True):
        """Pushes a file to the Android device

        Args:
            src_file_path: The path to the file to install.
            dst_file_path: The destination of the file.
            push_timeout: How long to wait for the push to finish in seconds
        """
        if not overwrite_existing and self.adb.path_exists(dst_file_path):
            logging.debug("Skip pushing {} to {} as it already exists on device".format(src_file_path, dst_file_path))
            return
        out = self.adb.push([src_file_path, dst_file_path], timeout=push_timeout).decode(UTF_8).rstrip()
        if 'error' in out:
            asserts.fail('Unable to push file %s to %s due to %s' % (src_file_path, dst_file_path, out))

    def tcp_forward_or_die(self, host_port, device_port, num_retry=1):
        """
        Forward a TCP port from host to device or fail
        :param host_port: host port, int, 0 for adb to assign one
        :param device_port: device port, int
        :param num_retry: number of times to reboot and retry this before dying
        :return: host port int
        """
        error_or_port = self.adb.tcp_forward(host_port, device_port)
        if not error_or_port:
            logging.debug("host port %d was already forwarded" % host_port)
            return host_port
        if not isinstance(error_or_port, int):
            if num_retry > 0:
                # If requested, reboot an retry
                num_retry -= 1
                logging.warning(
                    "[%s] Failed to TCP forward host port %d to "
                    "device port %d, num_retries left is %d" % (self.label, host_port, device_port, num_retry))
                self.reboot()
                self.adb.remount()
                return self.tcp_forward_or_die(host_port, device_port, num_retry=num_retry)
            asserts.fail(
                'Unable to forward host port %d to device port %d, error %s' % (host_port, device_port, error_or_port))
        return error_or_port

    def tcp_reverse_or_die(self, device_port, host_port, num_retry=1):
        """
        Forward a TCP port from device to host or fail
        :param device_port: device port, int, 0 for adb to assign one
        :param host_port: host port, int
        :param num_retry: number of times to reboot and retry this before dying
        :return: device port int
        """
        error_or_port = self.adb.reverse(["tcp:%d" % device_port, "tcp:%d" % host_port])
        if not error_or_port:
            logging.debug("device port %d was already reversed" % device_port)
            return device_port
        try:
            error_or_port = int(error_or_port)
        except ValueError:
            if num_retry > 0:
                # If requested, reboot an retry
                num_retry -= 1
                logging.warning(
                    "[%s] Failed to TCP reverse device port %d to "
                    "host port %d, num_retries left is %d" % (self.label, device_port, host_port, num_retry))
                self.reboot()
                self.adb.remount()
                return self.tcp_reverse_or_die(device_port, host_port, num_retry=num_retry)
            asserts.fail(
                'Unable to reverse device port %d to host port %d, error %s' % (device_port, host_port, error_or_port))
        return error_or_port

    def ensure_verity_disabled(self):
        """Ensures that verity is enabled.

        If verity is not enabled, this call will reboot the phone. Note that
        this only works on debuggable builds.
        """
        logging.debug("Disabling verity and remount for %s", self.serial_number)
        # The below properties will only exist if verity has been enabled.
        system_verity = self.adb.getprop('partition.system.verified')
        vendor_verity = self.adb.getprop('partition.vendor.verified')
        if system_verity or vendor_verity:
            self.adb.disable_verity()
            self.reboot()
        self.adb.remount()
        self.adb.wait_for_device(timeout=self.WAIT_FOR_DEVICE_TIMEOUT_SECONDS)

    def reboot(self, timeout_minutes=15.0):
        """Reboots the device.

        Reboot the device, wait for device to complete booting.
        """
        logging.debug("Rebooting %s", self.serial_number)
        self.adb.reboot()

        timeout_start = time.time()
        timeout = timeout_minutes * 60
        # Android sometimes return early after `adb reboot` is called. This
        # means subsequent calls may make it to the device before the reboot
        # goes through, return false positives for getprops such as
        # sys.boot_completed.
        while time.time() < timeout_start + timeout:
            try:
                logging.debug("waiting for device %s to turn off", self.serial_number)
                self.adb.get_state()
                logging.debug("device %s not turned off yet", self.serial_number)
                time.sleep(.1)
            except AdbError:
                # get_state will raise an error if the device is not found. We
                # want the device to be missing to prove the device has kicked
                # off the reboot.
                logging.debug("device %s is turned off, waiting for it to boot", self.serial_number)
                break
        minutes_left = timeout_minutes - (time.time() - timeout_start) / 60.0
        self.wait_for_boot_completion(timeout_minutes=minutes_left)
        asserts.assert_true(self.adb.ensure_root(), "device %s cannot run as root after reboot" % self.serial_number)

    def wait_for_boot_completion(self, timeout_minutes=15.0):
        """
        Waits for Android framework to broadcast ACTION_BOOT_COMPLETED.
        :param timeout_minutes: number of minutes to wait
        """
        timeout_start = time.time()
        timeout = timeout_minutes * 60

        self.adb.wait_for_device(timeout=self.WAIT_FOR_DEVICE_TIMEOUT_SECONDS)
        while time.time() < timeout_start + timeout:
            try:
                completed = self.adb.getprop("sys.boot_completed")
                if completed == '1':
                    return
            except AdbError:
                # adb shell calls may fail during certain period of booting
                # process, which is normal. Ignoring these errors.
                pass
            time.sleep(5)
        asserts.fail(msg='Device %s booting process timed out.' % self.serial_number)

    def gracefully_stop_backing_process(self):
        """
        Gracefully stops backing process
        :return: expected backing process exit code on success, 0 on error
        """
        backing_process_pid = None
        # Since we do not know which segment of self.cmd is the command running
        # on the Android device. We have to iterate with trial and error.
        cmd = self.cmd
        if len(self.cmd) >= 5:
            # skip adb -s serial shell to speed up the search
            # we don't know if any environment variables are set up before the
            # actual command and hence has to try from the 4th argument
            cmd = self.cmd[4:] + self.cmd[:4]
        for segment in cmd:
            try:
                # pgrep only takes 16 bytes including null terminator
                # -f cannot be used because that include the full command args
                current_cmd = pathlib.Path(segment).stem[:15]
                # -x matches whole command, cannot avoid as short segment may partially match
                # -n returnes the newest command matched
                backing_process_pid = int(self.adb.shell("pgrep -n -x {}".format(current_cmd)))
                logging.debug("Found backing process name on Android as {}, pid is {}".format(
                    segment, backing_process_pid))
            except (AdbError, ValueError) as e:
                logging.debug("Failed to run pgrep {}".format(e))
            if backing_process_pid is not None:
                break
        if backing_process_pid is None:
            logging.warning("Failed to get pid for cmd {}".format(self.cmd))
            try:
                logging.debug(self.adb.shell("ps -A | grep bluetooth"))
            except AdbError:
                pass
            return 0
        stop_signal = signal.SIGINT
        self.adb.shell("kill -{} {}".format(stop_signal, backing_process_pid))
        logging.debug("Sent SIGINT to backing process at pid {}".format(backing_process_pid))
        stop_signal = -self.ADB_ABORT_EXIT_CODE
        return stop_signal
