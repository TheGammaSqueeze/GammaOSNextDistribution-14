#!/usr/bin/env python
#
# Copyright 2016 - The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
r"""
Welcome to
   ___  _______   ____  __  _____
  / _ |/ ___/ /  / __ \/ / / / _ \
 / __ / /__/ /__/ /_/ / /_/ / // /
/_/ |_\___/____/\____/\____/____/


This a tool to create Android Virtual Devices locally/remotely.

- Prerequisites:
 The manual will be available at
 https://android.googlesource.com/platform/tools/acloud/+/master/README.md

- To get started:
 - Create instances:
    1) To create a remote cuttlefish instance with the local built image.
       Example:
       $ acloud create --local-image
       Or specify built image dir:
       $ acloud create --local-image /tmp/image_dir
    2) To create a local cuttlefish instance using the image which has been
       built out in your workspace.
       Example:
       $ acloud create --local-instance --local-image

 - Delete instances:
   $ acloud delete

 - Reconnect:
   To reconnect adb/vnc to an existing instance that's been disconnected:
   $ acloud reconnect
   Or to specify a specific instance:
   $ acloud reconnect --instance-names <instance_name like ins-123-cf-x86-phone>

 - List:
   List will retrieve all the remote instances you've created in addition to any
   local instances created as well.
   To show device IP address, adb port and instance name:
   $ acloud list
   To show more detail info on the list.
   $ acloud list -vv

-  Pull:
   Pull will download log files or show the log file in screen from one remote
   cuttlefish instance:
   $ acloud pull
   Pull from a specified instance:
   $ acloud pull --instance-name "your_instance_name"

Try $acloud [cmd] --help for further details.

"""

from __future__ import print_function
import argparse
import logging
import os
import sys
import traceback

if sys.version_info.major == 2:
    print("Acloud only supports python3 (currently @ %d.%d.%d)."
          " Please run Acloud with python3." % (sys.version_info.major,
                                                sys.version_info.minor,
                                                sys.version_info.micro))
    sys.exit(1)

# By Default silence root logger's stream handler since 3p lib may initial
# root logger no matter what level we're using. The acloud logger behavior will
# be defined in _SetupLogging(). This also could workaround to get rid of below
# oauth2client warning:
# 'No handlers could be found for logger "oauth2client.contrib.multistore_file'
DEFAULT_STREAM_HANDLER = logging.StreamHandler()
DEFAULT_STREAM_HANDLER.setLevel(logging.CRITICAL)
logging.getLogger().addHandler(DEFAULT_STREAM_HANDLER)

# pylint: disable=wrong-import-position
from acloud import errors
from acloud.create import create
from acloud.create import create_args
from acloud.delete import delete
from acloud.delete import delete_args
from acloud.internal import constants
from acloud.reconnect import reconnect
from acloud.reconnect import reconnect_args
from acloud.list import list as list_instances
from acloud.list import list_args
from acloud.metrics import metrics
from acloud.powerwash import powerwash
from acloud.powerwash import powerwash_args
from acloud.public import acloud_common
from acloud.public import config
from acloud.public import report
from acloud.public.actions import create_goldfish_action
from acloud.pull import pull
from acloud.pull import pull_args
from acloud.restart import restart
from acloud.restart import restart_args
from acloud.setup import setup
from acloud.setup import setup_args
from acloud.hostcleanup import hostcleanup
from acloud.hostcleanup import hostcleanup_args


LOGGING_FMT = "%(asctime)s |%(levelname)s| %(module)s:%(lineno)s| %(message)s"
ACLOUD_LOGGER = "acloud"
_LOGGER = logging.getLogger(ACLOUD_LOGGER)
NO_ERROR_MESSAGE = ""
PROG = "acloud"
DEFAULT_SUPPORT_ARGS = ["--version", "-h", "--help"]

# Commands
CMD_CREATE_GOLDFISH = "create_gf"

# Config requires fields.
_CREATE_REQUIRE_FIELDS = ["project", "zone", "machine_type"]
# show contact info to user.
_CONTACT_INFO = ("If you have any question or need acloud team support, "
                 "please feel free to contact us by email at "
                 "buganizer-system+419709@google.com")
_LOG_INFO = " and attach those log files from %s"


# pylint: disable=too-many-statements
def _ParseArgs(args):
    """Parse args.

    Args:
        args: Argument list passed from main.

    Returns:
        Parsed args and a list of unknown argument strings.
    """
    acloud_cmds = [
        setup_args.CMD_SETUP,
        create_args.CMD_CREATE,
        list_args.CMD_LIST,
        delete_args.CMD_DELETE,
        reconnect_args.CMD_RECONNECT,
        powerwash_args.CMD_POWERWASH,
        pull_args.CMD_PULL,
        restart_args.CMD_RESTART,
        hostcleanup_args.CMD_HOSTCLEANUP,
        CMD_CREATE_GOLDFISH]
    usage = ",".join(acloud_cmds)
    parser = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
        usage="acloud {" + usage + "} ...")
    parser = argparse.ArgumentParser(prog=PROG)
    parser.add_argument('--version', action='version', version=(
        '%(prog)s ' + config.GetVersion()))
    subparsers = parser.add_subparsers(metavar="{" + usage + "}")
    subparser_list = []

    # Command "create_gf", create goldfish instances
    # In order to create a goldfish device we need the following parameters:
    # 1. The emulator build we wish to use, this is the binary that emulates
    #    an android device. See go/emu-dev for more
    # 2. A system-image. This is the android release we wish to run on the
    #    emulated hardware.
    create_gf_parser = subparsers.add_parser(CMD_CREATE_GOLDFISH)
    create_gf_parser.required = False
    create_gf_parser.set_defaults(which=CMD_CREATE_GOLDFISH)
    create_gf_parser.add_argument(
        "--emulator_build_id",
        type=str,
        dest="emulator_build_id",
        required=False,
        help="Emulator build used to run the images. e.g. 4669466.")
    create_gf_parser.add_argument(
        "--emulator_branch",
        type=str,
        dest="emulator_branch",
        required=False,
        help="Emulator build branch name, e.g. aosp-emu-master-dev. If specified"
        " without emulator_build_id, the last green build will be used.")
    create_gf_parser.add_argument(
        "--emulator-build-target",
        dest="emulator_build_target",
        required=False,
        help="Emulator build target used to run the images. e.g. "
        "emulator-linux_x64_nolocationui.")
    create_gf_parser.add_argument(
        "--base_image",
        type=str,
        dest="base_image",
        required=False,
        help="Name of the goldfish base image to be used to create the instance. "
        "This will override stable_goldfish_host_image_name from config. "
        "e.g. emu-dev-cts-061118")
    create_gf_parser.add_argument(
        "--tags",
        dest="tags",
        nargs="*",
        required=False,
        default=None,
        help="Tags to be set on to the created instance. e.g. https-server.")

    create_args.AddCommonCreateArgs(create_gf_parser)
    subparser_list.append(create_gf_parser)

    # Command "create"
    subparser_list.append(create_args.GetCreateArgParser(subparsers))

    # Command "setup"
    subparser_list.append(setup_args.GetSetupArgParser(subparsers))

    # Command "delete"
    subparser_list.append(delete_args.GetDeleteArgParser(subparsers))

    # Command "list"
    subparser_list.append(list_args.GetListArgParser(subparsers))

    # Command "reconnect"
    subparser_list.append(reconnect_args.GetReconnectArgParser(subparsers))

    # Command "restart"
    subparser_list.append(restart_args.GetRestartArgParser(subparsers))

    # Command "powerwash"
    subparser_list.append(powerwash_args.GetPowerwashArgParser(subparsers))

    # Command "pull"
    subparser_list.append(pull_args.GetPullArgParser(subparsers))

    # Command "hostcleanup"
    subparser_list.append(hostcleanup_args.GetHostcleanupArgParser(subparsers))

    # Add common arguments.
    for subparser in subparser_list:
        acloud_common.AddCommonArguments(subparser)

    support_args = acloud_cmds + DEFAULT_SUPPORT_ARGS
    if not args or args[0] not in support_args:
        parser.print_help()
        sys.exit(constants.EXIT_BY_WRONG_CMD)

    return parser.parse_known_args(args)


# pylint: disable=too-many-branches
def _VerifyArgs(parsed_args):
    """Verify args.

    Args:
        parsed_args: Parsed args.

    Raises:
        errors.CommandArgError: If args are invalid.
        errors.UnsupportedCreateArgs: When a create arg is specified but
                                      unsupported for a particular avd type.
                                      (e.g. --system-build-id for gf)
    """
    if parsed_args.which == create_args.CMD_CREATE:
        create_args.VerifyArgs(parsed_args)
    if parsed_args.which == setup_args.CMD_SETUP:
        setup_args.VerifyArgs(parsed_args)
    if parsed_args.which == CMD_CREATE_GOLDFISH:
        if not parsed_args.emulator_build_id and not parsed_args.build_id and (
                not parsed_args.emulator_branch and not parsed_args.branch):
            raise errors.CommandArgError(
                "Must specify either --build_id or --branch or "
                "--emulator_branch or --emulator_build_id")
        if not parsed_args.build_target:
            raise errors.CommandArgError("Must specify --build_target")
        if (parsed_args.system_branch
                or parsed_args.system_build_id
                or parsed_args.system_build_target):
            raise errors.UnsupportedCreateArgs(
                "--system-* args are not supported for AVD type: %s"
                % constants.TYPE_GF)

    if parsed_args.which in [create_args.CMD_CREATE, CMD_CREATE_GOLDFISH]:
        if (parsed_args.serial_log_file
                and not parsed_args.serial_log_file.endswith(".tar.gz")):
            raise errors.CommandArgError(
                "--serial_log_file must ends with .tar.gz")


def _ValidateAuthFile(cfg):
    """Check if the authentication file exist.

    Args:
        cfg: AcloudConfig object.
    """
    auth_file = os.path.join(os.path.expanduser("~"), cfg.creds_cache_file)
    if not os.path.exists(auth_file):
        print("Notice: Acloud will bring up browser to proceed authentication. "
              "For cloudtop, please run in remote desktop.")


def _ParsingConfig(args, cfg):
    """Parse config to check if missing any field.

    Args:
        args: Namespace object from argparse.parse_args.
        cfg: AcloudConfig object.

    Returns:
        error message about list of missing config fields.
    """
    missing_fields = []
    if (args.which == create_args.CMD_CREATE and
            args.local_instance is None and not args.remote_host):
        missing_fields = cfg.GetMissingFields(_CREATE_REQUIRE_FIELDS)
    if missing_fields:
        return (f"Config file ({config.GetUserConfigPath(args.config_file)}) "
                f"missing required fields: {missing_fields}, please add these "
                "fields or reset config file. For reset config information: "
                "go/acloud-googler-setup#reset-configuration")
    return None


def _SetupLogging(log_file, verbose):
    """Setup logging.

    This function define the logging policy in below manners.
    - without -v , -vv ,--log_file:
    Only display critical log and print() message on screen.

    - with -v:
    Display INFO log and set StreamHandler to acloud parent logger to turn on
    ONLY acloud modules logging.(silence all 3p libraries)

    - with -vv:
    Display INFO/DEBUG log and set StreamHandler to root logger to turn on all
    acloud modules and 3p libraries logging.

    - with --log_file.
    Dump logs to FileHandler with DEBUG level.

    Args:
        log_file: String, if not None, dump the log to log file.
        verbose: Int, if verbose = 1(-v), log at INFO level and turn on
                 logging on libraries to a StreamHandler.
                 If verbose = 2(-vv), log at DEBUG level and turn on logging on
                 all libraries and 3rd party libraries to a StreamHandler.
    """
    # Define logging level and hierarchy by verbosity.
    shandler_level = None
    logger = None
    if verbose == 0:
        shandler_level = logging.CRITICAL
        logger = logging.getLogger(ACLOUD_LOGGER)
    elif verbose == 1:
        shandler_level = logging.INFO
        logger = logging.getLogger(ACLOUD_LOGGER)
    elif verbose > 1:
        shandler_level = logging.DEBUG
        logger = logging.getLogger()

    # Add StreamHandler by default.
    shandler = logging.StreamHandler()
    shandler.setFormatter(logging.Formatter(LOGGING_FMT))
    shandler.setLevel(shandler_level)
    logger.addHandler(shandler)
    # Set the default level to DEBUG, the other handlers will handle
    # their own levels via the args supplied (-v and --log_file).
    logger.setLevel(logging.DEBUG)

    # Add FileHandler if log_file is provided.
    if log_file:
        fhandler = logging.FileHandler(filename=log_file)
        fhandler.setFormatter(logging.Formatter(LOGGING_FMT))
        fhandler.setLevel(logging.DEBUG)
        logger.addHandler(fhandler)


def main(argv=None):
    """Main entry.

    Args:
        argv: A list of system arguments.

    Returns:
        Job status: Integer, 0 if success. None-zero if fails.
        Stack trace: String of errors.
    """
    args, unknown_args = _ParseArgs(argv)
    _SetupLogging(args.log_file, args.verbose)
    _VerifyArgs(args)
    _LOGGER.info("Acloud version: %s", config.GetVersion())

    cfg = config.GetAcloudConfig(args)
    parsing_config_error = _ParsingConfig(args, cfg)
    _ValidateAuthFile(cfg)
    # TODO: Move this check into the functions it is actually needed.
    # Check access.
    # device_driver.CheckAccess(cfg)

    reporter = None
    if parsing_config_error:
        reporter = report.Report(command=args.which)
        reporter.UpdateFailure(parsing_config_error,
                               constants.ACLOUD_CONFIG_ERROR)
    elif unknown_args:
        reporter = report.Report(command=args.which)
        reporter.UpdateFailure(
            "unrecognized arguments: %s" % ",".join(unknown_args),
            constants.ACLOUD_UNKNOWN_ARGS_ERROR)
    elif args.which == create_args.CMD_CREATE:
        reporter = create.Run(args)
    elif args.which == CMD_CREATE_GOLDFISH:
        reporter = create_goldfish_action.CreateDevices(
            cfg=cfg,
            build_target=args.build_target,
            branch=args.branch,
            build_id=args.build_id,
            emulator_build_id=args.emulator_build_id,
            emulator_branch=args.emulator_branch,
            emulator_build_target=args.emulator_build_target,
            kernel_build_id=args.kernel_build_id,
            kernel_branch=args.kernel_branch,
            kernel_build_target=args.kernel_build_target,
            gpu=args.gpu,
            num=args.num,
            serial_log_file=args.serial_log_file,
            autoconnect=args.autoconnect,
            tags=args.tags,
            report_internal_ip=args.report_internal_ip,
            boot_timeout_secs=args.boot_timeout_secs)
    elif args.which == delete_args.CMD_DELETE:
        reporter = delete.Run(args)
    elif args.which == list_args.CMD_LIST:
        list_instances.Run(args)
    elif args.which == reconnect_args.CMD_RECONNECT:
        reconnect.Run(args)
    elif args.which == restart_args.CMD_RESTART:
        reporter = restart.Run(args)
    elif args.which == powerwash_args.CMD_POWERWASH:
        reporter = powerwash.Run(args)
    elif args.which == pull_args.CMD_PULL:
        reporter = pull.Run(args)
    elif args.which == setup_args.CMD_SETUP:
        setup.Run(args)
    elif args.which == hostcleanup_args.CMD_HOSTCLEANUP:
        hostcleanup.Run(args)
    else:
        error_msg = "Invalid command %s" % args.which
        sys.stderr.write(error_msg)
        return constants.EXIT_BY_WRONG_CMD, error_msg

    if reporter and args.report_file:
        reporter.Dump(args.report_file)
    if reporter and reporter.errors:
        error_msg = "\n".join(reporter.errors)
        help_msg = _CONTACT_INFO
        if reporter.data.get(constants.ERROR_LOG_FOLDER):
            help_msg += _LOG_INFO % reporter.data.get(constants.ERROR_LOG_FOLDER)
        sys.stderr.write("Encountered the following errors:\n%s\n\n%s.\n" %
                         (error_msg, help_msg))
        return constants.EXIT_BY_FAIL_REPORT, error_msg
    return constants.EXIT_SUCCESS, NO_ERROR_MESSAGE


if __name__ == "__main__":
    EXIT_CODE = None
    EXCEPTION_STACKTRACE = None
    EXCEPTION_LOG = None
    LOG_METRICS = metrics.LogUsage(sys.argv[1:])
    try:
        EXIT_CODE, EXCEPTION_STACKTRACE = main(sys.argv[1:])
    except Exception as e:
        EXIT_CODE = constants.EXIT_BY_ERROR
        EXCEPTION_STACKTRACE = traceback.format_exc()
        EXCEPTION_LOG = str(e)
        sys.stderr.write("Exception: %s" % (EXCEPTION_STACKTRACE))

    # Log Exit event here to calculate the consuming time.
    if LOG_METRICS:
        metrics.LogExitEvent(EXIT_CODE,
                             stacktrace=EXCEPTION_STACKTRACE,
                             logs=EXCEPTION_LOG)
    sys.exit(EXIT_CODE)
