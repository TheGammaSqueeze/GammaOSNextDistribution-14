# Copyright 2020 - The Android Open Source Project
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
r"""Powerwash entry point.

This command will powerwash the AVD from a remote instance.
"""

import logging
import subprocess

from acloud import errors
from acloud.internal import constants
from acloud.internal.lib import utils
from acloud.internal.lib.ssh import Ssh
from acloud.internal.lib.ssh import IP
from acloud.list import list as list_instances
from acloud.public import config
from acloud.public import report


logger = logging.getLogger(__name__)


def PowerwashFromInstance(cfg, instance, instance_id):
    """Powerwash AVD from remote CF instance.

    Args:
        cfg: AcloudConfig object.
        instance: list.Instance() object.
        instance_id: Integer of the instance id.

    Returns:
        A Report instance.
    """
    ssh = Ssh(ip=IP(ip=instance.ip),
              user=constants.GCE_USER,
              ssh_private_key_path=cfg.ssh_private_key_path,
              extra_args_ssh_tunnel=cfg.extra_args_ssh_tunnel)
    logger.info("Start to powerwash AVD id (%s) from the instance: %s.",
                instance_id, instance.name)
    PowerwashDevice(ssh, instance_id)
    return report.Report(command="powerwash")


@utils.TimeExecute(function_description="Waiting for AVD to powerwash")
def PowerwashDevice(ssh, instance_id):
    """Powerwash AVD with the instance id.

    Args:
        ssh: Ssh object.
        instance_id: Integer of the instance id.
    """
    ssh_command = "./bin/powerwash_cvd --instance_num=%d" % (instance_id)
    try:
        ssh.Run(ssh_command)
    except (subprocess.CalledProcessError, errors.DeviceConnectionError) as e:
        logger.debug(str(e))
        utils.PrintColorString(str(e), utils.TextColors.FAIL)


def Run(args):
    """Run powerwash.

    After powerwash command executed, tool will return one Report instance.

    Args:
        args: Namespace object from argparse.parse_args.

    Returns:
        A Report instance.
    """
    cfg = config.GetAcloudConfig(args)
    if args.instance_name:
        instance = list_instances.GetInstancesFromInstanceNames(
            cfg, [args.instance_name])
        return PowerwashFromInstance(cfg, instance[0], args.instance_id)
    return PowerwashFromInstance(cfg,
                                 list_instances.ChooseOneRemoteInstance(cfg),
                                 args.instance_id)
