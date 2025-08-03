#!/usr/bin/env python3
"""Run Trusty under QEMU in different configurations"""
import enum
import errno
import fcntl
import json
import os
from textwrap import dedent
import re
import select
import socket
import subprocess
import shutil
import sys
import tempfile
import time
import threading

from typing import Optional, List

import qemu_options
from qemu_error import AdbFailure, ConfigError, RunnerGenericError, Timeout


# ADB expects its first console on 5554, and control on 5555
ADB_BASE_PORT = 5554


def find_android_build_dir(android):
    if os.path.exists(f"{android}/target/product/trusty"):
        return android
    if os.path.exists(f"{android}/out/target/product/trusty"):
        return f"{android}/out"

    print(f"{android} not an Android source or build directory")
    sys.exit(1)


class Config(object):
    """Stores a QEMU configuration for use with the runner

    Attributes:
        android:          Path to a built Android tree or prebuilt.
        linux:            Path to a built Linux kernel tree or prebuilt.
        linux_arch:       Architecture of Linux kernel.
        atf:              Path to the ATF build to use.
        qemu:             Path to the emulator to use.
        arch:             Architecture definition.
        rpmbd:            Path to the rpmb daemon to use.
        extra_qemu_flags: Extra flags to pass to QEMU.
    Setting android or linux to None will result in a QEMU which starts
    without those components.
    """

    def __init__(self, config=None):
        """Qemu Configuration

        If config is passed in, it should be a file containing a json
        specification fields described in the docs.
        Unspecified fields will be defaulted.

        If you do not pass in a config, you will almost always need to
        override these values; the default is not especially useful.
        """
        config_dict = {}
        if config:
            config_dict = json.load(config)

        self.script_dir = os.path.dirname(os.path.realpath(__file__))

        def abspath(config_key, default_value=None):
            if config_value := config_dict.get(config_key, default_value):
                return os.path.join(self.script_dir, config_value)
            return None

        if android_path := abspath("android"):
            self.android = find_android_build_dir(android_path)
        else:
            self.android = None
        self.linux = abspath("linux")
        self.linux_arch = config_dict.get("linux_arch")
        self.atf = abspath("atf")
        self.qemu = abspath("qemu", "qemu-system-aarch64")
        self.rpmbd = abspath("rpmbd")
        self.arch = config_dict.get("arch")
        self.extra_qemu_flags = config_dict.get("extra_qemu_flags", [])

    def check_config(self, interactive: bool, boot_tests=(),
                     android_tests=()):
        """Checks the runner/qemu config to make sure they are compatible"""
        # If we have any android tests, we need a linux dir and android dir
        if android_tests:
            if not self.linux:
                raise ConfigError("Need Linux to run android tests")
            if not self.android:
                raise ConfigError("Need Android to run android tests")

        # For now, we can't run boot tests and android tests at the same time,
        # because test-runner reports its exit code by terminating the
        # emulator.
        if android_tests:
            if boot_tests:
                raise ConfigError("Cannot run Android tests and boot"
                                  " tests from same runner")

        # Since boot test utilizes virtio serial console port for communication
        # between QEMU guest and current process, it is not compatible with
        # interactive mode.
        if boot_tests:
            if interactive:
                raise ConfigError("Cannot run boot tests interactively")

        if self.android:
            if not self.linux:
                raise ConfigError("Cannot run Android without Linux")


def alloc_ports():
    """Allocates 2 sequential ports above 5554 for adb"""
    # adb uses ports in pairs
    port_width = 2

    # We can't actually reserve ports atomically for QEMU, but we can at
    # least scan and find two that are not currently in use.
    min_port = ADB_BASE_PORT
    while True:
        alloced_ports = []
        for port in range(min_port, min_port + port_width):
            # If the port is already in use, don't hand it out
            try:
                sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                sock.connect(("localhost", port))
                break
            except IOError:
                alloced_ports += [port]
        if len(alloced_ports) == port_width:
            return alloced_ports

        # We could increment by only 1, but if we are competing with other
        # adb sessions for ports, this will be more polite
        min_port += port_width


def forward_ports(ports):
    """Generates arguments to forward ports in QEMU on a virtio network"""
    forwards = ""
    remap_port = ADB_BASE_PORT
    for port in ports:
        forwards += f",hostfwd=tcp::{port}-:{remap_port}"
        remap_port = remap_port + 1
    return [
        "-device", "virtio-net,netdev=adbnet0", "-netdev",
        "user,id=adbnet0" + forwards
    ]


class QEMUCommandPipe(object):
    """Communicate with QEMU."""

    def __init__(self):
        """Produces pipes for talking to QEMU and args to enable them."""
        self.command_dir = tempfile.mkdtemp()
        os.mkfifo(f"{self.command_dir}/com.in")
        os.mkfifo(f"{self.command_dir}/com.out")
        self.command_args = [
            "-chardev",
            f"pipe,id=command0,path={self.command_dir}/com", "-mon",
            "chardev=command0,mode=control"
        ]
        self.com_pipe_in = None
        self.com_pipe_out = None

    def open(self):
        # pylint: disable=consider-using-with
        self.com_pipe_in = open(f"{self.command_dir}/com.in", "w",
                                encoding="utf-8")
        self.com_pipe_out = open(f"{self.command_dir}/com.out", "r",
                                 encoding="utf-8")
        self.qmp_command({"execute": "qmp_capabilities"})

    def close(self):
        """Close and clean up command pipes."""

        def try_close(pipe):
            try:
                pipe.close()
            except IOError as e:
                print("close error ignored", e)

        try_close(self.com_pipe_in)
        try_close(self.com_pipe_out)

        # Onerror callback function to handle errors when we try to remove
        # command pipe directory, since we sleep one second if QEMU doesn't
        # die immediately, command pipe directory might has been removed
        # already during sleep period.
        def cb_handle_error(func, path, exc_info):
            if not os.access(path, os.F_OK):
                # Command pipe directory already removed, this case is
                # expected, pass this case.
                pass
            else:
                raise RunnerGenericError("Failed to clean up command pipe.")

        # Clean up our command pipe
        shutil.rmtree(self.command_dir, onerror=cb_handle_error)

    def qmp_command(self, qmp_command):
        """Send a qmp command and return result."""

        try:
            json.dump(qmp_command, self.com_pipe_in)
            self.com_pipe_in.flush()
            for line in iter(self.com_pipe_out.readline, ""):
                res = json.loads(line)

                if err := res.get("error"):
                    sys.stderr.write(f"Command {qmp_command} failed: {err}\n")
                    return res

                if "return" in res:
                    return res

                if "QMP" not in res and "event" not in res:
                    # Print unexpected extra lines
                    sys.stderr.write("ignored:" + line)
        except IOError as e:
            print("qmp_command error ignored", e)

        return None

    def qmp_execute(self, execute, arguments=None):
        """Send a qmp execute command and return result."""
        cmp_command = {"execute": execute}
        if arguments:
            cmp_command["arguments"] = arguments
        return self.qmp_command(cmp_command)

    def monitor_command(self, monitor_command):
        """Send a monitor command and write result to stderr."""

        res = self.qmp_execute("human-monitor-command",
                               {"command-line": monitor_command})
        if res and "return" in res:
            sys.stderr.write(res["return"])


def qemu_handle_error(command_pipe, debug_on_error):
    """Dump registers and/or wait for debugger."""

    sys.stdout.flush()

    sys.stderr.write("QEMU register dump:\n")
    command_pipe.monitor_command("info registers -a")
    sys.stderr.write("\n")

    if debug_on_error:
        command_pipe.monitor_command("gdbserver")
        print("Connect gdb, press enter when done ")
        select.select([sys.stdin], [], [])
        input("\n")  # pylint: disable=bad-builtin


def qemu_exit(command_pipe, qemu_proc, has_error, debug_on_error):
    """Ensures QEMU is terminated"""
    unclean_exit = False

    if command_pipe:
        # Ask QEMU to quit
        if qemu_proc and (qemu_proc.poll() is None):
            try:
                if has_error:
                    qemu_handle_error(command_pipe=command_pipe,
                                      debug_on_error=debug_on_error)
                command_pipe.qmp_execute("quit")
            except OSError:
                pass

            # If it doesn't die immediately, wait a second
            if qemu_proc.poll() is None:
                time.sleep(1)
                # If it's still not dead, take it out
                if qemu_proc.poll() is None:
                    qemu_proc.kill()
                    print("QEMU refused quit")
                    unclean_exit = True
            qemu_proc.wait()

        command_pipe.close()

    elif qemu_proc and (qemu_proc.poll() is None):
        # This was an interactive run or a boot test
        # QEMU should not be running at this point
        print("QEMU still running with no command channel")
        qemu_proc.kill()
        qemu_proc.wait()
        unclean_exit = True
    return unclean_exit

class RunnerSession:
    """Hold shared state between runner launch and shutdown."""

    def __init__(self):
        self.has_error = False
        self.command_pipe = None
        self.qemu_proc = None
        self.ports = None
        # stores the arguments used to start qemu iff performing a boot test
        self.args = []
        self.temp_files = []

    def get_qemu_arg_temp_file(self):
        """Returns a temp file that will be deleted after qemu exits."""
        tmp = tempfile.NamedTemporaryFile(delete=False)  # pylint: disable=consider-using-with
        self.temp_files.append(tmp.name)
        return tmp


class RunnerState(enum.Enum):
    OFF = 0
    BOOTLOADER = 1
    ANDROID = 2


class Runner(object):
    """Executes tests in QEMU"""

    def __init__(self,
                 config,
                 interactive=False,
                 verbose=False,
                 rpmb=True,
                 debug=False,
                 debug_on_error=False):
        """Initializes the runner with provided settings.

        See .run() for the meanings of these.
        """
        self.config = config
        self.interactive = interactive
        self.debug = debug
        self.verbose = verbose
        self.adb_transport = None
        self.use_rpmb = rpmb
        self.rpmb_proc = None
        self.rpmb_sock_dir = None
        self.msg_sock = None
        self.msg_sock_conn = None
        self.msg_sock_dir = None
        self.debug_on_error = debug_on_error
        self.dump_stdout_on_error = False
        self.qemu_arch_options = None
        self.default_timeout = 60 * 10  # 10 Minutes
        self.session: Optional[RunnerSession] = None
        self.state = RunnerState.OFF

        # If we're not verbose or interactive, squelch command output
        if verbose or self.interactive:
            self.stdout = None
            self.stderr = None
        else:
            self.stdout = tempfile.TemporaryFile()  # pylint: disable=consider-using-with
            self.stderr = subprocess.STDOUT
            self.dump_stdout_on_error = True

        # If we're interactive connect stdin to the user
        if self.interactive:
            self.stdin = None
        else:
            self.stdin = subprocess.DEVNULL

        if self.config.arch in ("arm64", "arm"):
            self.qemu_arch_options = qemu_options.QemuArm64Options(self.config)
        elif self.config.arch == "x86_64":
            # pylint: disable=no-member
            self.qemu_arch_options = qemu_options.QemuX86_64Options(self.config)
        else:
            raise ConfigError("Architecture unspecified or unsupported!")

    def error_dump_output(self):
        if self.dump_stdout_on_error:
            sys.stdout.flush()
            sys.stderr.write("System log:\n")
            self.stdout.seek(0)
            sys.stderr.buffer.write(self.stdout.read())

    def get_qemu_arg_temp_file(self):
        """Returns a temp file that will be deleted after qemu exits."""
        # pylint: disable=consider-using-with
        tmp = tempfile.NamedTemporaryFile(delete=False)
        self.session.temp_files.append(tmp.name)
        return tmp

    def rpmb_up(self):
        """Brings up the rpmb daemon, returning QEMU args to connect"""
        rpmb_data = self.qemu_arch_options.rpmb_data_path()

        self.rpmb_sock_dir = tempfile.mkdtemp()
        rpmb_sock = f"{self.rpmb_sock_dir}/rpmb"
        # pylint: disable=consider-using-with
        rpmb_proc = subprocess.Popen([self.config.rpmbd,
                                      "-d", rpmb_data,
                                      "--sock", rpmb_sock])
        self.rpmb_proc = rpmb_proc

        # Wait for RPMB socket to appear to avoid a race with QEMU
        test_sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        tries = 0
        max_tries = 10
        while True:
            tries += 1
            try:
                test_sock.connect(rpmb_sock)
                break
            except socket.error as exn:
                if tries >= max_tries:
                    raise exn
                time.sleep(1)

        return self.qemu_arch_options.rpmb_options(rpmb_sock)

    def rpmb_down(self):
        """Kills the running rpmb daemon, cleaning up its socket directory"""
        if self.rpmb_proc:
            self.rpmb_proc.kill()
            self.rpmb_proc = None
        if self.rpmb_sock_dir:
            shutil.rmtree(self.rpmb_sock_dir)
            self.rpmb_sock_dir = None

    def msg_channel_up(self):
        """Create message channel between host and QEMU guest

        Virtual serial console port 'testrunner0' is introduced as socket
        communication channel for QEMU guest and current process. Testrunner
        enumerates this port, reads test case which to be executed from
        testrunner0 port, sends output log message and test result to
        testrunner0 port.
        """

        self.msg_sock_dir = tempfile.mkdtemp()
        msg_sock_file = f"{self.msg_sock_dir}/msg"
        self.msg_sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        self.msg_sock.bind(msg_sock_file)

        # Listen on message socket
        self.msg_sock.listen(1)

        return ["-device",
                "virtserialport,chardev=testrunner0,name=testrunner0",
                "-chardev", f"socket,id=testrunner0,path={msg_sock_file}"]

    def msg_channel_down(self):
        if self.msg_sock_conn:
            self.msg_sock_conn.close()
            self.msg_sock_conn = None
        if self.msg_sock_dir:
            shutil.rmtree(self.msg_sock_dir)
            self.msg_sock_dir = None

    def msg_channel_wait_for_connection(self):
        """wait for testrunner to connect."""

        # Accept testrunner's connection request
        self.msg_sock_conn, _ = self.msg_sock.accept()

    def msg_channel_send_msg(self, msg):
        """Send message to testrunner via testrunner0 port

        Testrunner tries to connect port while message with following format
        "boottest your.port.here". Currently, we utilize this format to execute
        cases in boot test.
        If message does not comply above format, testrunner starts to launch
        secondary OS.

        """
        if self.msg_sock_conn:
            self.msg_sock_conn.send(msg.encode())
        else:
            sys.stderr.write("Connection has not been established yet!")

    def msg_channel_recv(self):
        if self.msg_sock_conn:
            return self.msg_sock_conn.recv(64)

        # error cases: channel not yet initialized or channel torn down
        return bytes()

    def msg_channel_close(self):
        if self.msg_sock_conn:
            self.msg_sock_conn.close()

    def boottest_run(self, boot_tests, timeout=(60 * 2)):
        """Run boot test cases"""
        args = self.session.args
        has_error = False
        result = 2

        if self.debug:
            warning = """\
                      Warning: Test selection does not work when --debug is set.
                      To run a test in test runner, run in GDB:

                      target remote :1234
                      break host_get_cmdline
                      c
                      next 6
                      set cmdline="boottest your.port.here"
                      set cmdline_len=sizeof("boottest your.port.here")-1
                      c
                      """
            print(dedent(warning))

        if self.interactive:
            args = ["-serial", "mon:stdio"] + args
        elif self.verbose:
            # This still leaves stdin connected, but doesn't connect a monitor
            args = ["-serial", "stdio", "-monitor", "none"] + args
        else:
            # Silence debugging output
            args = ["-serial", "null", "-monitor", "none"] + args

        # Create command channel which used to quit QEMU after case execution
        command_pipe = QEMUCommandPipe()
        args += command_pipe.command_args
        cmd = [self.config.qemu] + args

        # pylint: disable=consider-using-with
        qemu_proc = subprocess.Popen(cmd, cwd=self.config.atf)

        command_pipe.open()
        self.msg_channel_wait_for_connection()

        def kill_testrunner():
            self.msg_channel_down()
            qemu_exit(command_pipe, qemu_proc, has_error=True,
                      debug_on_error=self.debug_on_error)
            raise Timeout("Wait for boottest to complete", timeout)

        kill_timer = threading.Timer(timeout, kill_testrunner)
        if not self.debug:
            kill_timer.start()

        testcase = "boottest " + "".join(boot_tests)
        try:
            self.msg_channel_send_msg(testcase)

            while True:
                ret = self.msg_channel_recv()

                # If connection is disconnected accidently by peer, for
                # instance child QEMU process crashed, a message with length
                # 0 would be received. We should drop this message, and
                # indicate test framework that something abnormal happened.
                if len(ret) == 0:
                    has_error = True
                    break

                # Print message to STDOUT. Since we might meet EAGAIN IOError
                # when writting to STDOUT, use try except loop to catch EAGAIN
                # and waiting STDOUT to be available, then try to write again.
                def print_msg(msg):
                    while True:
                        try:
                            sys.stdout.write(msg)
                            break
                        except IOError as e:
                            if e.errno != errno.EAGAIN:
                                RunnerGenericError("Failed to print message")
                            select.select([], [sys.stdout], [])

                # Please align message structure definition in testrunner.
                if ret[0] == 0:
                    msg_len = ret[1]
                    msg = ret[2 : 2 + msg_len].decode()
                    print_msg(msg)
                elif ret[0] == 1:
                    result = ret[1]
                    break
                else:
                    # Unexpected type, return test result:TEST_FAILED
                    has_error = True
                    result = 1
                    break
        finally:
            kill_timer.cancel()
            self.msg_channel_down()
            unclean_exit = qemu_exit(command_pipe, qemu_proc,
                                     has_error=has_error,
                                     debug_on_error=self.debug_on_error)

        if unclean_exit:
            raise RunnerGenericError("QEMU did not exit cleanly")

        return result

    def androidtest_run(self, cmd, test_timeout=None):
        """Run android test cases"""
        session: RunnerSession = self.session
        assert session, "No session; must call launch before running any tests."

        try:
            if not test_timeout:
                test_timeout = self.default_timeout

            def on_adb_timeout():
                print(f"adb Timed out ({test_timeout} s)")
                qemu_handle_error(command_pipe=session.command_pipe,
                                  debug_on_error=self.debug_on_error)

            test_result = self.adb(["shell"] + cmd, timeout=test_timeout,
                                   on_timeout=on_adb_timeout, force_output=True)
            if test_result:
                session.has_error = True

            return test_result
        except:
            session.has_error = True
            raise

    def adb_bin(self):
        """Returns location of adb"""
        return f"{self.config.android}/host/linux-x86/bin/adb"

    def adb(self,
            args,
            timeout=60,
            on_timeout=lambda timeout: print(f"Timed out ({timeout} s)"),
            force_output=False):
        """Runs an adb command

        If self.adb_transport is set, specializes the command to that
        transport to allow for multiple simultaneous tests.

        Timeout specifies a timeout for the command in seconds.

        If force_output is set true, will send results to stdout and
        stderr regardless of the runner's preferences.
        """
        if self.adb_transport:
            args = ["-t", str(self.adb_transport)] + args

        if force_output:
            stdout = None
            stderr = None
        else:
            stdout = self.stdout
            stderr = self.stderr

        adb_proc = subprocess.Popen(  # pylint: disable=consider-using-with
            [self.adb_bin()] + args, stdin=self.stdin, stdout=stdout,
            stderr=stderr)

        status = 1
        try:
            status = adb_proc.wait(timeout)
        except subprocess.TimeoutExpired:
            if on_timeout:
                on_timeout()

            try:
                adb_proc.kill()
            except OSError:
                pass

        return status


    def check_adb(self, args, **kwargs):
        """As .adb(), but throws an exception if the command fails"""
        code = self.adb(args, **kwargs)
        if code != 0:
            raise AdbFailure(args, code)

    def adb_root(self):
        """Restarts adbd with root permissions and waits until it's back up"""
        max_tries = 10
        num_tries = 0

        # Ensure device is up else adb root can fail
        self.adb(["wait-for-device"])
        self.check_adb(["root"])

        while True:
            # adbd might not be down by this point yet
            self.adb(["wait-for-device"])

            # Check that adbd is up and running with root permissions
            code = self.adb(["shell",
                             "if [[ $(id -u) -ne 0 ]] ; then exit 1; fi"])
            if code == 0:
                return

            num_tries += 1
            if num_tries >= max_tries:
                raise AdbFailure(["root"], code)
            time.sleep(1)

    def scan_transport(self, port, expect_none=False):
        """Given a port and `adb devices -l`, find the transport id"""
        output = subprocess.check_output([self.adb_bin(), "devices", "-l"],
                                         universal_newlines=True)
        match = re.search(fr"localhost:{port}.*transport_id:(\d+)", output)
        if not match:
            if expect_none:
                self.adb_transport = None
                return
            raise RunnerGenericError(
                f"Failed to find transport for port {port} in \n{output}")
        self.adb_transport = int(match.group(1))

    def adb_up(self, port):
        """Ensures adb is connected to adbd on the selected port"""
        # Wait until we can connect to the target port
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        connect_max_tries = 15
        connect_tries = 0
        while True:
            try:
                sock.connect(("localhost", port))
                break
            except IOError as ioe:
                connect_tries += 1
                if connect_tries >= connect_max_tries:
                    raise Timeout("Wait for adbd socket",
                                  connect_max_tries) from ioe
                time.sleep(1)
        sock.close()
        self.check_adb(["connect", f"localhost:{port}"])
        self.scan_transport(port)

        # Sometimes adb can get stuck and will never connect. Using multiple
        # shorter timeouts works better than one longer timeout in such cases.
        adb_exception = None
        for _ in range(10):
            try:
                self.check_adb(["wait-for-device"], timeout=30, on_timeout=None)
                break
            except AdbFailure as e:
                adb_exception = e
                continue
        else:
            print("'adb wait-for-device' Timed out")
            raise adb_exception

        self.adb_root()

        # Files put onto the data partition in the Android build will not
        # actually be populated into userdata.img when make dist is used.
        # To work around this, we manually update /data once the device is
        # booted by pushing it the files that would have been there.
        userdata = self.qemu_arch_options.android_trusty_user_data()
        self.check_adb(["push", userdata, "/"])

    def adb_down(self, port):
        """Cleans up after adb connection to adbd on selected port"""
        self.check_adb(["disconnect", f"localhost:{port}"])

        # Wait until QEMU's forward has expired
        connect_max_tries = 300
        connect_tries = 0
        while True:
            try:
                self.scan_transport(port, expect_none=True)
                if not self.adb_transport:
                    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                    sock.connect(("localhost", port))
                    sock.close()
                connect_tries += 1
                if connect_tries >= connect_max_tries:
                    raise Timeout("Wait for port forward to go away",
                                  connect_max_tries)
                time.sleep(1)
            except IOError:
                break

    def universal_args(self):
        """Generates arguments used in all qemu invocations"""
        args = self.qemu_arch_options.basic_options()
        args += self.qemu_arch_options.bios_options()

        if self.config.linux:
            args += self.qemu_arch_options.linux_options()

        if self.config.android:
            args += self.qemu_arch_options.android_drives_args()

        # Append configured extra flags
        args += self.config.extra_qemu_flags

        return args

    def launch(self, target_state):
        """Launches the QEMU execution.

        If interactive is specified, it will leave the user connected
        to the serial console/monitor, and they are responsible for
        terminating execution.

        If debug is on, the main QEMU instance will be launched with -S and
        -s, which pause the CPU rather than booting, and starts a gdb server
        on port 1234 respectively.

        It is the responsibility of callers to ensure that shutdown gets called
        after launch - regardless of whether the launch succeeded or not.

        Limitations:
          If the adb port range is already in use, port forwarding may fail.

        TODO: For boot tests, the emulator isn't actually launched here but in
              boottest_run. Eventually, we want to unify the way boot tests and
              android tests are launched. Specifically, we might stop execution
              in the bootloader and have it wait for a command to boot android.
        """
        assert self.state == RunnerState.OFF
        assert target_state in [RunnerState.BOOTLOADER,
                                RunnerState.ANDROID], target_state

        self.session = RunnerSession()
        args = self.universal_args()

        try:
            if self.use_rpmb:
                args += self.rpmb_up()

            if self.config.linux:
                args += self.qemu_arch_options.gen_dtb(
                    args,
                    self.session.get_qemu_arg_temp_file())

            # Prepend the machine since we don't need to edit it as in gen_dtb
            args = self.qemu_arch_options.machine_options() + args

            if self.debug:
                args += ["-s", "-S"]

            # Create socket for communication channel
            args += self.msg_channel_up()

            if target_state == RunnerState.BOOTLOADER:
                self.session.args = args
                self.state = target_state
                return

            # Logging and terminal monitor
            # Prepend so that it is the *first* serial port and avoid
            # conflicting with rpmb0.
            args = ["-serial", "mon:stdio"] + args

            # If we're noninteractive (e.g. testing) we need a command channel
            # to tell the guest to exit
            if not self.interactive:
                self.session.command_pipe = QEMUCommandPipe()
                args += self.session.command_pipe.command_args

            # Reserve ADB ports
            self.session.ports = alloc_ports()

            # Write expected serial number (as given in adb) to stdout.
            sys.stdout.write(
                f"DEVICE_SERIAL: localhost:{self.session.ports[1]}\n")
            sys.stdout.flush()

            # Forward ADB ports in qemu
            args += forward_ports(self.session.ports)

            qemu_cmd = [self.config.qemu] + args
            self.session.qemu_proc = subprocess.Popen(  # pylint: disable=consider-using-with
                qemu_cmd,
                cwd=self.config.atf,
                stdin=self.stdin,
                stdout=self.stdout,
                stderr=self.stderr)

            if self.session.command_pipe:
                self.session.command_pipe.open()
            self.msg_channel_wait_for_connection()

            if self.debug:
                script_dir = self.config.script_dir
                if script_dir.endswith("/build-qemu-generic-arm64-test-debug"):
                    print(f"Debug with: lldb --source {script_dir}/lldbinit")
                else:
                    print("Debug with: lldb --one-line 'gdb-remote 1234'")

            # Send request to boot secondary OS
            self.msg_channel_send_msg("Boot Secondary OS")

            # Bring ADB up talking to the command port
            self.adb_up(self.session.ports[1])

            self.state = target_state
        except:
            self.session.has_error = True
            raise

    def shutdown(self):
        """Shut down emulator after test cases have run

        The launch and shutdown methods store shared state in a session object.
        Calls to launch and shutdown must be correctly paired no matter whether
        the launch steps and calls to adb succeed or fail.
        """
        if self.state == RunnerState.OFF:
            return

        assert self.session is not None

        # Clean up generated device tree
        for temp_file in self.session.temp_files:
            os.remove(temp_file)

        if self.session.has_error:
            self.error_dump_output()

        unclean_exit = qemu_exit(self.session.command_pipe,
                                 self.session.qemu_proc,
                                 has_error=self.session.has_error,
                                 debug_on_error=self.debug_on_error)

        fcntl.fcntl(0, fcntl.F_SETFL,
                    fcntl.fcntl(0, fcntl.F_GETFL) & ~os.O_NONBLOCK)

        self.rpmb_down()

        self.msg_channel_down()

        if self.adb_transport:
            # Disconnect ADB and wait for our port to be released by qemu
            self.adb_down(self.session.ports[1])

        self.session = None
        self.state = RunnerState.OFF

        if unclean_exit:
            raise RunnerGenericError("QEMU did not exit cleanly")

    def reboot(self, target_state):
        self.shutdown()

        try:
            self.launch(target_state)
        except:
            self.shutdown()
            raise

    def run(self, boot_tests: Optional[List] = None,
            android_tests: Optional[List] = None,
            timeout: Optional[int] = None) -> List[int]:
        """Run boot or android tests.

        Runs boot_tests through test_runner, android_tests through ADB,
        returning aggregated test return codes in a list.

        Returns:
          A list of return codes for the provided tests.
          A negative return code indicates an internal tool failure.

        Limitations:
          Until test_runner is updated, only one of android_tests or boot_tests
          may be provided.
          Similarly, while boot_tests is a list, test_runner only knows how to
          correctly run a single test at a time.
          Again due to test_runner's current state, if boot_tests are
          specified, interactive will be ignored since the machine will
          terminate itself.

          If android_tests is provided, a Linux and Android dir must be
          provided in the config.
        """
        assert self.state == RunnerState.OFF
        self.config.check_config(self.interactive, boot_tests, android_tests)

        if boot_tests and android_tests:
            raise RunnerGenericError(
                "Cannot run boot tests and android tests in the same "
                "QEMU instance")

        if boot_tests and len(boot_tests) > 1:
            raise RunnerGenericError(
                "Can only run a single boot test at a time")

        timeout = timeout if timeout else self.default_timeout
        try:
            self.launch(RunnerState.BOOTLOADER if boot_tests else
                        RunnerState.ANDROID)
            test_results = []

            if boot_tests:
                test_results.append(self.boottest_run(boot_tests, timeout))

            if android_tests:
                for android_test in android_tests:
                    test_result = self.androidtest_run([android_test], timeout)
                    test_results.append(test_result)
                    if test_result:
                        break

            return test_results
        finally:
            # The wait on QEMU is done here to ensure that ADB failures do not
            # take away the user's serial console in interactive mode.
            if self.interactive and self.session:
                # The user is responsible for quitting QEMU
                self.session.qemu_proc.wait()
            self.shutdown()
