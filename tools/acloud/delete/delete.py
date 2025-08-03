# Copyright 2018 - The Android Open Source Project
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
r"""Delete entry point.

Delete will handle all the logic related to deleting a local/remote instance
of an Android Virtual Device.
"""

from __future__ import print_function

import logging
import re
import subprocess

from acloud import errors
from acloud.internal import constants
from acloud.internal.lib import cvd_utils
from acloud.internal.lib import emulator_console
from acloud.internal.lib import goldfish_utils
from acloud.internal.lib import oxygen_client
from acloud.internal.lib import ssh
from acloud.internal.lib import utils
from acloud.list import list as list_instances
from acloud.public import config
from acloud.public import device_driver
from acloud.public import report


logger = logging.getLogger(__name__)

_COMMAND_GET_PROCESS_ID = ["pgrep", "run_cvd"]
_COMMAND_GET_PROCESS_COMMAND = ["ps", "-o", "command", "-p"]
_RE_RUN_CVD = re.compile(r"^(?P<run_cvd>.+run_cvd)")
_LOCAL_INSTANCE_PREFIX = "local-"
_RE_OXYGEN_RELEASE_ERROR = re.compile(
    r".*Error received while trying to release device: (?P<error>.*)$", re.DOTALL)


def DeleteInstances(cfg, instances_to_delete):
    """Delete instances according to instances_to_delete.

    Args:
        cfg: AcloudConfig object.
        instances_to_delete: List of list.Instance() object.

    Returns:
        Report object.
    """
    delete_report = report.Report(command="delete")
    remote_instance_list = []
    for instance in instances_to_delete:
        if instance.islocal:
            if instance.avd_type == constants.TYPE_GF:
                DeleteLocalGoldfishInstance(instance, delete_report)
            elif instance.avd_type == constants.TYPE_CF:
                DeleteLocalCuttlefishInstance(instance, delete_report)
            else:
                delete_report.AddError("Deleting %s is not supported." %
                                       instance.avd_type)
                delete_report.SetStatus(report.Status.FAIL)
        else:
            remote_instance_list.append(instance.name)
        # Delete ssvnc viewer
        if instance.vnc_port:
            utils.CleanupSSVncviewer(instance.vnc_port)

    if remote_instance_list:
        # TODO(119283708): We should move DeleteAndroidVirtualDevices into
        # delete.py after gce is deprecated.
        # Stop remote instances.
        return DeleteRemoteInstances(cfg, remote_instance_list, delete_report)

    return delete_report


@utils.TimeExecute(function_description="Deleting remote instances",
                   result_evaluator=utils.ReportEvaluator,
                   display_waiting_dots=False)
def DeleteRemoteInstances(cfg, instances_to_delete, delete_report=None):
    """Delete remote instances.

    Args:
        cfg: AcloudConfig object.
        instances_to_delete: List of instance names(string).
        delete_report: Report object.

    Returns:
        Report instance if there are instances to delete, None otherwise.

    Raises:
        error.ConfigError: when config doesn't support remote instances.
    """
    if not cfg.SupportRemoteInstance():
        raise errors.ConfigError("No gcp project info found in config! "
                                 "The execution of deleting remote instances "
                                 "has been aborted.")
    utils.PrintColorString("")
    for instance in instances_to_delete:
        utils.PrintColorString(" - %s" % instance, utils.TextColors.WARNING)
    utils.PrintColorString("")
    utils.PrintColorString("status: waiting...", end="")

    # TODO(119283708): We should move DeleteAndroidVirtualDevices into
    # delete.py after gce is deprecated.
    # Stop remote instances.
    delete_report = device_driver.DeleteAndroidVirtualDevices(
        cfg, instances_to_delete, delete_report)

    return delete_report


@utils.TimeExecute(function_description="Deleting local cuttlefish instances",
                   result_evaluator=utils.ReportEvaluator)
def DeleteLocalCuttlefishInstance(instance, delete_report):
    """Delete a local cuttlefish instance.

    Delete local instance and write delete instance
    information to report.

    Args:
        instance: instance.LocalInstance object.
        delete_report: Report object.

    Returns:
        delete_report.
    """
    ins_lock = instance.GetLock()
    if not ins_lock.Lock():
        delete_report.AddError("%s is locked by another process." %
                               instance.name)
        delete_report.SetStatus(report.Status.FAIL)
        return delete_report

    try:
        ins_lock.SetInUse(False)
        instance.Delete()
        delete_report.SetStatus(report.Status.SUCCESS)
        device_driver.AddDeletionResultToReport(
            delete_report, [instance.name], failed=[],
            error_msgs=[],
            resource_name="instance")
    except subprocess.CalledProcessError as e:
        delete_report.AddError(str(e))
        delete_report.SetStatus(report.Status.FAIL)
    finally:
        ins_lock.Unlock()

    return delete_report


@utils.TimeExecute(function_description="Deleting local goldfish instances",
                   result_evaluator=utils.ReportEvaluator)
def DeleteLocalGoldfishInstance(instance, delete_report):
    """Delete a local goldfish instance.

    Args:
        instance: LocalGoldfishInstance object.
        delete_report: Report object.

    Returns:
        delete_report.
    """
    lock = instance.GetLock()
    if not lock.Lock():
        delete_report.AddError("%s is locked by another process." %
                               instance.name)
        delete_report.SetStatus(report.Status.FAIL)
        return delete_report

    try:
        lock.SetInUse(False)
        if instance.adb.EmuCommand("kill") == 0:
            delete_report.SetStatus(report.Status.SUCCESS)
            device_driver.AddDeletionResultToReport(
                delete_report, [instance.name], failed=[],
                error_msgs=[],
                resource_name="instance")
        else:
            delete_report.AddError("Cannot kill %s." % instance.device_serial)
            delete_report.SetStatus(report.Status.FAIL)
    finally:
        lock.Unlock()

    return delete_report


def ResetLocalInstanceLockByName(name, delete_report):
    """Set the lock state of a local instance to be not in use.

    Args:
        name: The instance name.
        delete_report: Report object.
    """
    ins_lock = list_instances.GetLocalInstanceLockByName(name)
    if not ins_lock:
        delete_report.AddError("%s is not a valid local instance name." % name)
        delete_report.SetStatus(report.Status.FAIL)
        return

    if not ins_lock.Lock():
        delete_report.AddError("%s is locked by another process." % name)
        delete_report.SetStatus(report.Status.FAIL)
        return

    try:
        ins_lock.SetInUse(False)
        delete_report.SetStatus(report.Status.SUCCESS)
        device_driver.AddDeletionResultToReport(
            delete_report, [name], failed=[], error_msgs=[],
            resource_name="instance")
    finally:
        ins_lock.Unlock()


@utils.TimeExecute(function_description=("Deleting remote host goldfish "
                                         "instance"),
                   result_evaluator=utils.ReportEvaluator)
def DeleteHostGoldfishInstance(cfg, name, ssh_user,
                               ssh_private_key_path, delete_report):
    """Delete a goldfish instance on a remote host by console command.

    Args:
        cfg: An AcloudConfig object.
        name: String, the instance name.
        remote_host : String, the IP address of the host.
        ssh_user: String or None, the ssh user for the host.
        ssh_private_key_path: String or None, the ssh private key for the host.
        delete_report: A Report object.

    Returns:
        delete_report.
    """
    ip_addr, port = goldfish_utils.ParseRemoteHostConsoleAddress(name)
    try:
        with emulator_console.RemoteEmulatorConsole(
                ip_addr, port,
                (ssh_user or constants.GCE_USER),
                (ssh_private_key_path or cfg.ssh_private_key_path),
                cfg.extra_args_ssh_tunnel) as console:
            console.Kill()
        delete_report.SetStatus(report.Status.SUCCESS)
        device_driver.AddDeletionResultToReport(
            delete_report, [name], failed=[], error_msgs=[],
            resource_name="instance")
    except errors.DeviceConnectionError as e:
        delete_report.AddError("%s is not deleted: %s" % (name, str(e)))
        delete_report.SetStatus(report.Status.FAIL)
    return delete_report


@utils.TimeExecute(function_description=("Deleting remote host cuttlefish "
                                         "instance"),
                   result_evaluator=utils.ReportEvaluator)
def CleanUpRemoteHost(cfg, remote_host, host_user, host_ssh_private_key_path,
                      base_dir, delete_report):
    """Clean up the remote host.

    Args:
        cfg: An AcloudConfig instance.
        remote_host : String, ip address or host name of the remote host.
        host_user: String of user login into the instance.
        host_ssh_private_key_path: String of host key for logging in to the
                                   host.
        base_dir: String, the base directory on the remote host.
        delete_report: A Report object.

    Returns:
        delete_report.
    """
    ssh_obj = ssh.Ssh(
        ip=ssh.IP(ip=remote_host),
        user=host_user,
        ssh_private_key_path=(
            host_ssh_private_key_path or cfg.ssh_private_key_path))
    try:
        cvd_utils.CleanUpRemoteCvd(ssh_obj, base_dir, raise_error=True)
        delete_report.SetStatus(report.Status.SUCCESS)
        device_driver.AddDeletionResultToReport(
            delete_report, [remote_host], failed=[],
            error_msgs=[],
            resource_name="remote host")
    except subprocess.CalledProcessError as e:
        delete_report.AddError(str(e))
        delete_report.SetStatus(report.Status.FAIL)
    return delete_report


def DeleteInstanceByNames(cfg, instances, host_user,
                          host_ssh_private_key_path):
    """Delete instances by the given instance names.

    This method can identify the following types of instance names:
    local cuttlefish instance: local-instance-<id>
    local goldfish instance: local-goldfish-instance-<id>
    remote host cuttlefish instance: host-<ip_addr>-<build_info>
    remote host goldfish instance: host-goldfish-<ip_addr>-<port>-<build_info>
    remote instance: ins-<uuid>-<build_info>

    Args:
        cfg: AcloudConfig object.
        instances: List of instance name.
        host_user: String or None, the ssh user for remote hosts.
        host_ssh_private_key_path: String or None, the ssh private key for
                                   remote hosts.

    Returns:
        A Report instance.
    """
    delete_report = report.Report(command="delete")
    local_names = set(name for name in instances if
                      name.startswith(_LOCAL_INSTANCE_PREFIX))
    remote_host_cf_names = set(
        name for name in instances if cvd_utils.ParseRemoteHostAddress(name))
    remote_host_gf_names = set(
        name for name in instances if
        goldfish_utils.ParseRemoteHostConsoleAddress(name))
    remote_names = list(set(instances) - local_names - remote_host_cf_names -
                        remote_host_gf_names)

    if local_names:
        active_instances = list_instances.GetLocalInstancesByNames(local_names)
        inactive_names = local_names.difference(ins.name for ins in
                                                active_instances)
        if active_instances:
            utils.PrintColorString("Deleting local instances")
            delete_report = DeleteInstances(cfg, active_instances)
        if inactive_names:
            utils.PrintColorString("Unlocking local instances")
            for name in inactive_names:
                ResetLocalInstanceLockByName(name, delete_report)

    if remote_host_cf_names:
        for name in remote_host_cf_names:
            ip_addr, base_dir = cvd_utils.ParseRemoteHostAddress(name)
            CleanUpRemoteHost(cfg, ip_addr, host_user,
                              host_ssh_private_key_path, base_dir,
                              delete_report)

    if remote_host_gf_names:
        for name in remote_host_gf_names:
            DeleteHostGoldfishInstance(
                cfg, name, host_user, host_ssh_private_key_path, delete_report)

    if remote_names:
        delete_report = DeleteRemoteInstances(cfg, remote_names, delete_report)
    return delete_report


def _ReleaseOxygenDevice(cfg, instances, ip):
    """ Release one Oxygen device.

    Args:
        cfg: AcloudConfig object.
        instances: List of instance name.
        ip: String of device ip.

    Returns:
        A Report instance.
    """
    if len(instances) != 1:
        raise errors.CommandArgError(
            "The release device function doesn't support multiple instances. "
            "Please check the specified instance names: %s" % instances)
    instance_name = instances[0]
    delete_report = report.Report(command="delete")
    try:
        oxygen_client.OxygenClient.ReleaseDevice(instance_name, ip,
                                                 cfg.oxygen_client)
        delete_report.SetStatus(report.Status.SUCCESS)
        device_driver.AddDeletionResultToReport(
            delete_report, [instance_name], failed=[],
            error_msgs=[],
            resource_name="instance")
    except subprocess.CalledProcessError as e:
        logger.error("Failed to release device from Oxygen, error: %s",
            e.output)
        error = str(e)
        match = _RE_OXYGEN_RELEASE_ERROR.match(e.output)
        if match:
            error = match.group("error").strip()
        delete_report.AddError(error)
        delete_report.SetErrorType(constants.ACLOUD_OXYGEN_RELEASE_ERROR)
        delete_report.SetStatus(report.Status.FAIL)
    return delete_report


def Run(args):
    """Run delete.

    After delete command executed, tool will return one Report instance.
    If there is no instance to delete, just reutrn empty Report.

    Args:
        args: Namespace object from argparse.parse_args.

    Returns:
        A Report instance.
    """
    # Prioritize delete instances by names without query all instance info from
    # GCP project.
    cfg = config.GetAcloudConfig(args)
    if args.oxygen:
        return _ReleaseOxygenDevice(cfg, args.instance_names, args.ip)
    if args.instance_names:
        return DeleteInstanceByNames(cfg,
                                     args.instance_names,
                                     args.host_user,
                                     args.host_ssh_private_key_path)
    if args.remote_host:
        delete_report = report.Report(command="delete")
        CleanUpRemoteHost(cfg, args.remote_host, args.host_user,
                          args.host_ssh_private_key_path,
                          cvd_utils.GetRemoteHostBaseDir(1),
                          delete_report)
        return delete_report

    instances = list_instances.GetLocalInstances()
    if not args.local_only and cfg.SupportRemoteInstance():
        instances.extend(list_instances.GetRemoteInstances(cfg))

    if args.adb_port:
        instances = list_instances.FilterInstancesByAdbPort(instances,
                                                            args.adb_port)
    elif not args.all:
        # Provide instances list to user and let user choose what to delete if
        # user didn't specify instances in args.
        instances = list_instances.ChooseInstancesFromList(instances)

    if not instances:
        utils.PrintColorString("No instances to delete")
    return DeleteInstances(cfg, instances)
