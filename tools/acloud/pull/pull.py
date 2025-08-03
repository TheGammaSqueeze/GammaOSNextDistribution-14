# Copyright 2019 - The Android Open Source Project
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
r"""Pull entry point.

This command will pull the log files from a remote instance for AVD troubleshooting.
"""

from __future__ import print_function
import logging
import os
import tempfile

from acloud import errors
from acloud.internal import constants
from acloud.internal.lib import utils
from acloud.internal.lib.ssh import Ssh
from acloud.internal.lib.ssh import IP
from acloud.list import list as list_instances
from acloud.public import config
from acloud.public import report


logger = logging.getLogger(__name__)

# Black list for log files.
_KERNEL = "kernel"
_IMG_FILE_EXTENSION = ".img"


def PullFileFromInstance(cfg, instance, file_name=None, no_prompts=False):
    """Pull file from remote CF instance.

    1. Download log files to temp folder.
    2. If only one file selected, display it on screen.
    3. Show the download folder for users.

    Args:
        cfg: AcloudConfig object.
        instance: list.Instance() object.
        file_name: String of file name.
        no_prompts: Boolean, True to skip the prompt about file streaming.

    Returns:
        A Report instance.
    """
    ssh = Ssh(ip=IP(ip=instance.ip),
              user=constants.GCE_USER,
              ssh_private_key_path=cfg.ssh_private_key_path,
              extra_args_ssh_tunnel=cfg.extra_args_ssh_tunnel)
    log_files = SelectLogFileToPull(ssh, file_name)
    PullLogs(ssh, log_files, instance.name)
    if len(log_files) == 1:
        DisplayLog(ssh, log_files[0], no_prompts)
    return report.Report(command="pull")


def PullLogs(ssh, log_files, instance_name):
    """Pull log files from remote instance.

    Args:
        ssh: Ssh object.
        log_files: List of file path in the remote instance.
        instance_name: The instance name that is used to create the download
                       folder.

    Returns:
        The download folder path.
    """
    download_folder = _GetDownloadLogFolder(instance_name)
    for log_file in log_files:
        target_file = os.path.join(download_folder, os.path.basename(log_file))
        ssh.ScpPullFile(log_file, target_file)
    _DisplayPullResult(download_folder)
    return download_folder


def DisplayLog(ssh, log_file, no_prompts=False):
    """Display the content of log file in the screen.

    Args:
        ssh: Ssh object.
        log_file: String of the log file path.
        no_prompts: Boolean, True to skip all prompts.
    """
    warning_msg = ("It will stream log to show on screen. If you want to stop "
                   "streaming, please press CTRL-C to exit.\nPress 'y' to show "
                   "log or read log by myself[y/N]:")
    if no_prompts or utils.GetUserAnswerYes(warning_msg):
        ssh.Run("tail -f -n +1 %s" % log_file, show_output=True)


def _DisplayPullResult(download_folder):
    """Display messages to user after pulling log files.

    Args:
        download_folder: String of download folder path.
    """
    utils.PrintColorString(
        "Download logs to folder: %s \nYou can look into log files to check "
        "AVD issues." % download_folder)


def _GetDownloadLogFolder(instance):
    """Get the download log folder accroding to instance name.

    Args:
        instance: String, the name of instance.

    Returns:
        String of the download folder path.
    """
    log_folder = os.path.join(tempfile.gettempdir(), instance)
    if not os.path.exists(log_folder):
        os.makedirs(log_folder)
    logger.info("Download logs to folder: %s", log_folder)
    return log_folder


def SelectLogFileToPull(ssh, file_name=None):
    """Select one log file or all log files to downalod.

    1. Get all log file paths as selection list
    2. Get user selected file path or user provided file name.

    Args:
        ssh: Ssh object.
        file_name: String of file name.

    Returns:
        List of selected file paths.

    Raises:
        errors.CheckPathError: Can't find log files.
    """
    log_files = GetAllLogFilePaths(ssh, constants.REMOTE_LOG_FOLDER)
    if file_name:
        file_path = os.path.join(constants.REMOTE_LOG_FOLDER, file_name)
        if file_path in log_files:
            return [file_path]
        raise errors.CheckPathError("Can't find this log file(%s) from remote "
                                    "instance." % file_path)

    if len(log_files) == 1:
        return log_files

    if len(log_files) > 1:
        print("Multiple log files detected, choose any one to proceed:")
        return utils.GetAnswerFromList(log_files, enable_choose_all=True)

    raise errors.CheckPathError("Can't find any log file in folder(%s) from "
                                "remote instance." % constants.REMOTE_LOG_FOLDER)


def GetAllLogFilePaths(ssh, remote_log_folder):
    """Get all file paths under the log folder.

    Two rules to filter out files.
    1. File name is "kernel".
    2. File type is image "*.img".

    Args:
        ssh: Ssh object.
        remote_log_folder: The path to the remote log folder.

    Returns:
        List of strings, the log file paths.
    """
    files = utils.FindRemoteFiles(ssh, [remote_log_folder])
    log_files = list(files)
    for file_path in files:
        file_name = os.path.basename(file_path)
        if file_name == _KERNEL or file_name.endswith(_IMG_FILE_EXTENSION):
            log_files.remove(file_path)
    return log_files


def Run(args):
    """Run pull.

    After pull command executed, tool will return one Report instance.
    If there is no instance to pull, just return empty Report.

    Args:
        args: Namespace object from argparse.parse_args.

    Returns:
        A Report instance.
    """
    cfg = config.GetAcloudConfig(args)
    if args.instance_name:
        instance = list_instances.GetInstancesFromInstanceNames(
            cfg, [args.instance_name])
        return PullFileFromInstance(cfg, instance[0], args.file_name, args.no_prompt)
    return PullFileFromInstance(cfg,
                                list_instances.ChooseOneRemoteInstance(cfg),
                                args.file_name,
                                args.no_prompt)
