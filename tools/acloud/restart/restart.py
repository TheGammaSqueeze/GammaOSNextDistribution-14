# Copyright 2021 - The Android Open Source Project
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
r"""Restart entry point.

This command will restart the CF AVD from a remote instance.
"""

import logging
import subprocess
import sys

from acloud import errors
from acloud.internal import constants
from acloud.internal.lib import utils
from acloud.internal.lib.ssh import Ssh
from acloud.internal.lib.ssh import IP
from acloud.list import list as list_instances
from acloud.powerwash import powerwash
from acloud.public import config
from acloud.public import report
from acloud.reconnect import reconnect


logger = logging.getLogger(__name__)
_NOT_SUPPORT_MSG = ("Currently the restart function doesn't support local "
                    "instances. Please try to create one new instance.")


def RestartFromInstance(cfg, instance, instance_id, powerwash_data):
    """Restart AVD from remote CF instance.

    Args:
        cfg: AcloudConfig object.
        instance: list.Instance() object.
        instance_id: Integer of the instance id.
        powerwash_data: Boolean, True to powerwash AVD data.

    Returns:
        A Report instance.
    """
    ssh = Ssh(ip=IP(ip=instance.ip),
              user=constants.GCE_USER,
              ssh_private_key_path=cfg.ssh_private_key_path,
              extra_args_ssh_tunnel=cfg.extra_args_ssh_tunnel)
    logger.info("Start to restart AVD id (%s) from the instance: %s.",
                instance_id, instance.name)
    if powerwash_data:
        powerwash.PowerwashDevice(ssh, instance_id)
    else:
        RestartDevice(ssh, instance_id)
    reconnect.ReconnectInstance(cfg.ssh_private_key_path,
                                instance,
                                report.Report(command="reconnect"),
                                cfg.extra_args_ssh_tunnel)
    return report.Report(command="restart")


@utils.TimeExecute(function_description="Waiting for AVD to restart")
def RestartDevice(ssh, instance_id):
    """Restart AVD with the instance id.

    Args:
        ssh: Ssh object.
        instance_id: Integer of the instance id.
    """
    ssh_command = "./bin/restart_cvd --instance_num=%d" % (instance_id)
    try:
        ssh.Run(ssh_command)
    except (subprocess.CalledProcessError, errors.DeviceConnectionError) as e:
        logger.debug(str(e))
        utils.PrintColorString(str(e), utils.TextColors.FAIL)


def Run(args):
    """Run restart.

    After restart command executed, tool will return one Report instance.

    Args:
        args: Namespace object from argparse.parse_args.

    Returns:
        A Report instance.
    """
    cfg = config.GetAcloudConfig(args)
    if args.instance_name:
        instance = list_instances.GetInstancesFromInstanceNames(
            cfg, [args.instance_name])
        return RestartFromInstance(
            cfg, instance[0], args.instance_id, args.powerwash)
    if (not list_instances.GetCFRemoteInstances(cfg)
            and list_instances.GetLocalInstances()):
        utils.PrintColorString(_NOT_SUPPORT_MSG, utils.TextColors.FAIL)
        sys.exit()
    return RestartFromInstance(cfg,
                               list_instances.ChooseOneRemoteInstance(cfg),
                               args.instance_id,
                               args.powerwash)
