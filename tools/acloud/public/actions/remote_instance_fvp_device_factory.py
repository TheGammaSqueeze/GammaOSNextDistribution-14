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

"""RemoteInstanceDeviceFactory provides basic interface to create an FVP
device factory."""

import os

from acloud.internal import constants
from acloud.internal.lib import utils
from acloud.internal.lib import ssh
from acloud.public.actions import gce_device_factory

class RemoteInstanceDeviceFactory(gce_device_factory.GCEDeviceFactory):
    """A class that can produce a FVP device."""
    def __init__(self, avd_spec):
        super().__init__(avd_spec)

    def CreateInstance(self):
        """Start a GCE instance, copy the necessary artifacts to it and then
        start FVP.

        Returns:
            The instance.
        """
        instance = self.CreateGceInstance()
        if instance in self.GetFailures():
            return instance

        try:
            self._UploadArtifacts()
            self._StartFVP()
        except Exception as e:
            self._SetFailures(instance, e)

        return instance

    @utils.TimeExecute(function_description="Processing and uploading local images")
    def _UploadArtifacts(self):
        """Copy artifacts to the GCE instance: the local images, the model
        itself and support files.
        """
        images_dir = self._avd_spec.local_image_dir
        images_path = os.path.join(images_dir, "required_images")
        with open(images_path, "r") as images:
            artifact_files = images.read().splitlines()
        ssh_cmd = self._ssh.GetBaseCmd(constants.SSH_BIN)

        cmd = ("tar -cf - --lzop -S -C {images_dir} {artifact_files} | "
               "{ssh_cmd} -- tar -xf - --lzop -S".format(
                   images_dir=images_dir,
                   artifact_files=" ".join(artifact_files),
                   ssh_cmd=ssh_cmd))
        ssh.ShellCmdWithRetry(cmd)

        model_bin = utils.GetBuildEnvironmentVariable("MODEL_BIN")
        cmd = ("tar -cf - --lzop -S -C {model_dir} . | "
               "{ssh_cmd} -- tar -xf - --lzop -S".format(
                   model_dir=os.path.dirname(model_bin),
                   ssh_cmd=ssh_cmd))
        ssh.ShellCmdWithRetry(cmd)

        self._ssh.ScpPushFile(
            src_file="device/generic/goldfish/fvpbase/run_model_only",
            dst_file="run_model_only")

        cmd = "{ssh_cmd} -- mkdir -p lib64".format(ssh_cmd=ssh_cmd)
        ssh.ShellCmdWithRetry(cmd)
        host_out = utils.GetBuildEnvironmentVariable("ANDROID_HOST_OUT")
        self._ssh.ScpPushFile(
            src_file="%s/lib64/bind_to_localhost.so" % host_out,
            dst_file="lib64/bind_to_localhost.so")

    @utils.TimeExecute(function_description="Starting FVP")
    def _StartFVP(self):
        """Start the model on the GCE instance."""
        ssh_cmd = self._ssh.GetBaseCmd(constants.SSH_BIN)
        model_bin = utils.GetBuildEnvironmentVariable("MODEL_BIN")

        cmd = ("{ssh_cmd} -- sh -c \"'ANDROID_HOST_OUT=. "
               "ANDROID_PRODUCT_OUT=. MODEL_BIN=./{model_basename} "
               "./run_model_only > /dev/null 2> /dev/null &'\"".format(
            ssh_cmd=ssh_cmd,
            model_basename=os.path.basename(model_bin)))
        ssh.ShellCmdWithRetry(cmd)
