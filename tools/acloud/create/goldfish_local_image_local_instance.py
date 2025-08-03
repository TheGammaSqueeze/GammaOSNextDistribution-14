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
r"""GoldfishLocalImageLocalInstance class.

Create class that is responsible for creating a local goldfish instance with
local images.

The emulator binary supports two types of environments, Android build system
and SDK. This class runs the emulator in build environment.
- This class uses the prebuilt emulator in ANDROID_EMULATOR_PREBUILTS.
- If the instance requires mixing system or boot image, this class uses the
  OTA tools in ANDROID_HOST_OUT.

To run this program outside of a build environment, the following setup is
required.
- One of the local tool directories is an unzipped SDK emulator repository,
  i.e., sdk-repo-<os>-emulator-<build>.zip.
- If the instance doesn't require mixing system image, the local image
  directory should be an unzipped SDK image repository, i.e.,
  sdk-repo-<os>-system-images-<build>.zip.
- If the instance requires mixing system image, the local image directory
  should be an unzipped extra image package, i.e.,
  emu-extra-<os>-system-images-<build>.zip.
- If the instance requires mixing system or boot image, one of the local tool
  directories should be an unzipped OTA tools package, i.e., otatools.zip.
"""

import logging
import os
import shutil
import subprocess
import sys

from acloud import errors
from acloud.create import base_avd_create
from acloud.create import create_common
from acloud.internal import constants
from acloud.internal.lib import goldfish_utils
from acloud.internal.lib import ota_tools
from acloud.internal.lib import utils
from acloud.list import instance
from acloud.public import report


logger = logging.getLogger(__name__)

# Input and output file names
_EMULATOR_BIN_NAME = "emulator"
_EMULATOR_BIN_DIR_NAMES = ("bin64", "qemu")
_SDK_REPO_EMULATOR_DIR_NAME = "emulator"
_NON_MIXED_BACKUP_IMAGE_EXT = ".bak-non-mixed"
_BUILD_PROP_FILE_NAME = "build.prop"
# Additional data written to VerifiedBootParams.textproto.
_UNLOCKING_PARAM = 'param: "androidboot.verifiedbootstate=orange"'
# Timeout
_DEFAULT_EMULATOR_TIMEOUT_SECS = 150
_EMULATOR_TIMEOUT_ERROR = "Emulator did not boot within %(timeout)d secs."
_EMU_KILL_TIMEOUT_SECS = 20
_EMU_KILL_TIMEOUT_ERROR = "Emulator did not stop within %(timeout)d secs."

_CONFIRM_RELAUNCH = ("\nGoldfish AVD is already running. \n"
                     "Enter 'y' to terminate current instance and launch a "
                     "new instance, enter anything else to exit out[y/N]: ")

_MISSING_EMULATOR_MSG = ("Emulator binary is not found. Check "
                         "ANDROID_EMULATOR_PREBUILTS in build environment, "
                         "or set --local-tool to an unzipped SDK emulator "
                         "repository.")

_INSTANCES_IN_USE_MSG = ("All instances are in use. Try resetting an instance "
                         "by specifying --local-instance and an id between 1 "
                         "and %(max_id)d.")


class GoldfishLocalImageLocalInstance(base_avd_create.BaseAVDCreate):
    """Create class for a local image local instance emulator."""

    def _CreateAVD(self, avd_spec, no_prompts):
        """Create the AVD.

        Args:
            avd_spec: AVDSpec object that provides the local image directory.
            no_prompts: Boolean, True to skip all prompts.

        Returns:
            A Report instance.
        """
        if not utils.IsSupportedPlatform(print_warning=True):
            result_report = report.Report(command="create")
            result_report.SetStatus(report.Status.FAIL)
            return result_report

        try:
            ins_id, ins_lock = self._LockInstance(avd_spec)
        except errors.CreateError as e:
            result_report = report.Report(command="create")
            result_report.AddError(str(e))
            result_report.SetStatus(report.Status.FAIL)
            return result_report

        try:
            ins = instance.LocalGoldfishInstance(ins_id,
                                                 avd_flavor=avd_spec.flavor)
            if not self._CheckRunningEmulator(ins.adb, no_prompts):
                # Mark as in-use so that it won't be auto-selected again.
                ins_lock.SetInUse(True)
                sys.exit(constants.EXIT_BY_USER)

            result_report = self._CreateAVDForInstance(ins, avd_spec)
            # The infrastructure is able to delete the instance only if the
            # instance name is reported. This method changes the state to
            # in-use after creating the report.
            ins_lock.SetInUse(True)
            return result_report
        finally:
            ins_lock.Unlock()

    @staticmethod
    def _LockInstance(avd_spec):
        """Select an id and lock the instance.

        Args:
            avd_spec: AVDSpec for the device.

        Returns:
            The instance id and the LocalInstanceLock that is locked by this
            process.

        Raises:
            errors.CreateError if fails to select or lock the instance.
        """
        if avd_spec.local_instance_id:
            ins_id = avd_spec.local_instance_id
            ins_lock = instance.LocalGoldfishInstance.GetLockById(ins_id)
            if ins_lock.Lock():
                return ins_id, ins_lock
            raise errors.CreateError("Instance %d is locked by another "
                                     "process." % ins_id)

        max_id = instance.LocalGoldfishInstance.GetMaxNumberOfInstances()
        for ins_id in range(1, max_id + 1):
            ins_lock = instance.LocalGoldfishInstance.GetLockById(ins_id)
            if ins_lock.LockIfNotInUse(timeout_secs=0):
                logger.info("Selected instance id: %d", ins_id)
                return ins_id, ins_lock
        raise errors.CreateError(_INSTANCES_IN_USE_MSG % {"max_id": max_id})

    def _CreateAVDForInstance(self, ins, avd_spec):
        """Create an emulator process for the goldfish instance.

        Args:
            ins: LocalGoldfishInstance to be initialized.
            avd_spec: AVDSpec for the device.

        Returns:
            A Report instance.

        Raises:
            errors.GetSdkRepoPackageError if emulator binary is not found.
            errors.GetLocalImageError if the local image directory does not
            contain required files.
            errors.CheckPathError if OTA tools are not found.
        """
        emulator_path = self._FindEmulatorBinary(
            avd_spec.local_tool_dirs +
            create_common.GetNonEmptyEnvVars(
                constants.ENV_ANDROID_EMULATOR_PREBUILTS))

        image_dir = self._FindImageDir(avd_spec.local_image_dir)
        # Validate the input dir.
        goldfish_utils.FindDiskImage(image_dir)

        # TODO(b/141898893): In Android build environment, emulator gets build
        # information from $ANDROID_PRODUCT_OUT/system/build.prop.
        # If image_dir is an extacted SDK repository, the file is at
        # image_dir/build.prop. Acloud copies it to
        # image_dir/system/build.prop.
        self._CopyBuildProp(image_dir)

        instance_dir = ins.instance_dir
        create_common.PrepareLocalInstanceDir(instance_dir, avd_spec)

        logcat_path = os.path.join(instance_dir, "logcat.txt")
        stdouterr_path = os.path.join(instance_dir, "kernel.log")
        logs = [report.LogFile(logcat_path, constants.LOG_TYPE_LOGCAT),
                report.LogFile(stdouterr_path, constants.LOG_TYPE_KERNEL_LOG)]
        extra_args = self._ConvertAvdSpecToArgs(avd_spec, instance_dir)

        logger.info("Instance directory: %s", instance_dir)
        proc = self._StartEmulatorProcess(emulator_path, instance_dir,
                                          image_dir, ins.console_port,
                                          ins.adb_port, logcat_path,
                                          stdouterr_path, extra_args)

        boot_timeout_secs = (avd_spec.boot_timeout_secs or
                             _DEFAULT_EMULATOR_TIMEOUT_SECS)
        result_report = report.Report(command="create")
        try:
            self._WaitForEmulatorToStart(ins.adb, proc, boot_timeout_secs)
        except (errors.DeviceBootTimeoutError, errors.SubprocessFail) as e:
            result_report.SetStatus(report.Status.BOOT_FAIL)
            result_report.AddDeviceBootFailure(ins.name, ins.ip,
                                               ins.adb_port, vnc_port=None,
                                               error=str(e),
                                               device_serial=ins.device_serial,
                                               logs=logs)
        else:
            result_report.SetStatus(report.Status.SUCCESS)
            result_report.AddDevice(ins.name, ins.ip, ins.adb_port,
                                    vnc_port=None,
                                    device_serial=ins.device_serial,
                                    logs=logs)

        return result_report

    @staticmethod
    def _FindEmulatorBinary(search_paths):
        """Find emulator binary in the directories.

        The directories may be extracted from zip archives without preserving
        file permissions. When this method finds the emulator binary and its
        dependencies, it sets the files to be executable.

        Args:
            search_paths: Collection of strings, the directories to search for
                          emulator binary.

        Returns:
            The path to the emulator binary.

        Raises:
            errors.GetSdkRepoPackageError if emulator binary is not found.
        """
        emulator_dir = None
        # Find in unzipped sdk-repo-*.zip.
        for search_path in search_paths:
            if os.path.isfile(os.path.join(search_path, _EMULATOR_BIN_NAME)):
                emulator_dir = search_path
                break

            sdk_repo_dir = os.path.join(search_path,
                                        _SDK_REPO_EMULATOR_DIR_NAME)
            if os.path.isfile(os.path.join(sdk_repo_dir, _EMULATOR_BIN_NAME)):
                emulator_dir = sdk_repo_dir
                break

        if not emulator_dir:
            raise errors.GetSdkRepoPackageError(_MISSING_EMULATOR_MSG)

        emulator_dir = os.path.abspath(emulator_dir)
        # Set the binaries to be executable.
        for subdir_name in _EMULATOR_BIN_DIR_NAMES:
            subdir_path = os.path.join(emulator_dir, subdir_name)
            if os.path.isdir(subdir_path):
                utils.SetDirectoryTreeExecutable(subdir_path)

        emulator_path = os.path.join(emulator_dir, _EMULATOR_BIN_NAME)
        utils.SetExecutable(emulator_path)
        return emulator_path

    @staticmethod
    def _FindImageDir(image_dir):
        """Find emulator images in the directory.

        In build environment, the images are in $ANDROID_PRODUCT_OUT.
        In an extracted SDK repository, the images are in the subdirectory
        named after the CPU architecture.

        Args:
            image_dir: The output directory in build environment or an
                       extracted SDK repository.

        Returns:
            The subdirectory if image_dir contains only one subdirectory;
            image_dir otherwise.
        """
        image_dir = os.path.abspath(image_dir)
        entries = os.listdir(image_dir)
        if len(entries) == 1:
            first_entry = os.path.join(image_dir, entries[0])
            if os.path.isdir(first_entry):
                return first_entry
        return image_dir

    @staticmethod
    def _IsEmulatorRunning(adb):
        """Check existence of an emulator by sending an empty command.

        Args:
            adb: adb_tools.AdbTools initialized with the emulator's serial.

        Returns:
            Boolean, whether the emulator is running.
        """
        return adb.EmuCommand() == 0

    def _CheckRunningEmulator(self, adb, no_prompts):
        """Attempt to delete a running emulator.

        Args:
            adb: adb_tools.AdbTools initialized with the emulator's serial.
            no_prompts: Boolean, True to skip all prompts.

        Returns:
            Whether the user wants to continue.

        Raises:
            errors.CreateError if the emulator isn't deleted.
        """
        if not self._IsEmulatorRunning(adb):
            return True
        logger.info("Goldfish AVD is already running.")
        if no_prompts or utils.GetUserAnswerYes(_CONFIRM_RELAUNCH):
            if adb.EmuCommand("kill") != 0:
                raise errors.CreateError("Cannot kill emulator.")
            self._WaitForEmulatorToStop(adb)
            return True
        return False

    @staticmethod
    def _CopyBuildProp(image_dir):
        """Copy build.prop to system/build.prop if it doesn't exist.

        Args:
            image_dir: The directory to find build.prop in.

        Raises:
            errors.GetLocalImageError if build.prop does not exist.
        """
        build_prop_path = os.path.join(image_dir, "system",
                                       _BUILD_PROP_FILE_NAME)
        if os.path.exists(build_prop_path):
            return
        build_prop_src_path = os.path.join(image_dir, _BUILD_PROP_FILE_NAME)
        if not os.path.isfile(build_prop_src_path):
            raise errors.GetLocalImageError("No %s in %s." %
                                            (_BUILD_PROP_FILE_NAME, image_dir))
        build_prop_dir = os.path.dirname(build_prop_path)
        logger.info("Copy %s to %s", _BUILD_PROP_FILE_NAME, build_prop_path)
        if not os.path.exists(build_prop_dir):
            os.makedirs(build_prop_dir)
        shutil.copyfile(build_prop_src_path, build_prop_path)

    @staticmethod
    def _ReplaceSystemQemuImg(new_image, image_dir):
        """Replace system-qemu.img in the directory.

        Args:
            new_image: The path to the new image.
            image_dir: The directory containing system-qemu.img.
        """
        system_qemu_img = os.path.join(image_dir,
                                       goldfish_utils.SYSTEM_QEMU_IMAGE_NAME)
        if os.path.exists(system_qemu_img):
            system_qemu_img_bak = system_qemu_img + _NON_MIXED_BACKUP_IMAGE_EXT
            if not os.path.exists(system_qemu_img_bak):
                # If system-qemu.img.bak-non-mixed does not exist, the
                # system-qemu.img was not created by acloud and should be
                # preserved. The user can restore it by renaming the backup to
                # system-qemu.img.
                logger.info("Rename %s to %s%s.",
                            system_qemu_img,
                            goldfish_utils.SYSTEM_QEMU_IMAGE_NAME,
                            _NON_MIXED_BACKUP_IMAGE_EXT)
                os.rename(system_qemu_img, system_qemu_img_bak)
            else:
                # The existing system-qemu.img.bak-non-mixed was renamed by
                # the previous invocation on acloud. The existing
                # system-qemu.img is a mixed image. Acloud removes the mixed
                # image because it is large and not reused.
                os.remove(system_qemu_img)
        try:
            logger.info("Link %s to %s.", system_qemu_img, new_image)
            os.link(new_image, system_qemu_img)
        except OSError:
            logger.info("Fail to link. Copy %s to %s",
                        system_qemu_img, new_image)
            shutil.copyfile(new_image, system_qemu_img)

    @staticmethod
    def _RewriteVerifiedBootParams(image_dir):
        """Rewrite VerifiedBootParams.textproto.

        This method appends the parameter that unlocks the device so that the
        disabled vbmeta takes effect. An alternative is to append to the kernel
        command line by `emulator -qemu -append`, but that does not pass the
        compliance test.

        Args:
            image_dir: The directory containing VerifiedBootParams.textproto.
        """
        params_path = os.path.join(
            image_dir, goldfish_utils.VERIFIED_BOOT_PARAMS_FILE_NAME)
        with open(params_path, "r", encoding="utf-8") as params_file:
            if _UNLOCKING_PARAM in params_file.read():
                # The file was possibly rewritten in the previous invocation.
                logging.info("%s conatins the parameter that unlocks the "
                             "device already.", params_path)
                return

        bak_params_path = params_path + _NON_MIXED_BACKUP_IMAGE_EXT
        # If the backup file exists, it was possibly created in the previous
        # invocation and contains the original contents. The user can restore
        # the original file by renaming it.
        if not os.path.exists(bak_params_path):
            logging.info("Copy %s to %s.", params_path, bak_params_path)
            shutil.copyfile(params_path, bak_params_path)

        logging.info("Write the unlocking parameter to %s.", params_path)
        with open(params_path, "a", encoding="utf-8") as params_file:
            params_file.writelines(["\n", _UNLOCKING_PARAM, "\n"])

    def _FindAndMixKernelImages(self, kernel_search_path, image_dir, tool_dirs,
                                instance_dir):
        """Find kernel images and mix them with emulator images.

        Args:
            kernel_search_path: The path to the boot image or the directory
                                containing kernel and ramdisk.
            image_dir: The directory containing the emulator images.
            tool_dirs: A list of directories to look for OTA tools.
            instance_dir: The instance directory for mixed images.

        Returns:
            A pair of strings, the paths to kernel image and ramdisk image.
        """
        # Find generic boot image.
        boot_image_path = create_common.FindBootImage(kernel_search_path,
                                                      raise_error=False)
        if boot_image_path:
            logger.info("Found boot image: %s", boot_image_path)
            return goldfish_utils.MixWithBootImage(
                os.path.join(instance_dir, "mix_kernel"),
                self._FindImageDir(image_dir),
                boot_image_path, ota_tools.FindOtaTools(tool_dirs))

        # Find kernel and ramdisk images built for emulator.
        kernel_dir = self._FindImageDir(kernel_search_path)
        kernel_path, ramdisk_path = goldfish_utils.FindKernelImages(kernel_dir)
        logger.info("Found kernel and ramdisk: %s %s",
                    kernel_path, ramdisk_path)
        return kernel_path, ramdisk_path

    def _ConvertAvdSpecToArgs(self, avd_spec, instance_dir):
        """Convert AVD spec to emulator arguments.

        Args:
            avd_spec: AVDSpec object.
            instance_dir: The instance directory for mixed images.

        Returns:
            List of strings, the arguments for emulator command.
        """
        args = goldfish_utils.ConvertAvdSpecToArgs(avd_spec)

        if not avd_spec.autoconnect:
            args.append("-no-window")

        ota_tools_search_paths = (
            avd_spec.local_tool_dirs +
            create_common.GetNonEmptyEnvVars(
                constants.ENV_ANDROID_SOONG_HOST_OUT,
                constants.ENV_ANDROID_HOST_OUT))

        if avd_spec.local_kernel_image:
            kernel_path, ramdisk_path = self._FindAndMixKernelImages(
                avd_spec.local_kernel_image, avd_spec.local_image_dir,
                ota_tools_search_paths, instance_dir)
            args.extend(("-kernel", kernel_path, "-ramdisk", ramdisk_path))

        if avd_spec.local_system_image:
            image_dir = self._FindImageDir(avd_spec.local_image_dir)
            # No known use case requires replacing system_ext and product.
            system_image_path = create_common.FindSystemImages(
                avd_spec.local_system_image).system
            mixed_image = goldfish_utils.MixDiskImage(
                os.path.join(instance_dir, "mix_disk"), image_dir,
                system_image_path, None,  # system_dlkm is not implemented.
                ota_tools.FindOtaTools(ota_tools_search_paths))

            # TODO(b/142228085): Use -system instead of modifying image_dir.
            self._ReplaceSystemQemuImg(mixed_image, image_dir)
            self._RewriteVerifiedBootParams(image_dir)

        return args

    @staticmethod
    def _StartEmulatorProcess(emulator_path, working_dir, image_dir,
                              console_port, adb_port, logcat_path,
                              stdouterr_path, extra_args):
        """Start an emulator process.

        Args:
            emulator_path: The path to emulator binary.
            working_dir: The working directory for the emulator process.
                         The emulator command creates files in the directory.
            image_dir: The directory containing the required images.
                       e.g., composite system.img or system-qemu.img.
            console_port: The console port of the emulator.
            adb_port: The ADB port of the emulator.
            logcat_path: The path where logcat is redirected.
            stdouterr_path: The path where stdout and stderr are redirected.
            extra_args: List of strings, the extra arguments.

        Returns:
            A Popen object, the emulator process.
        """
        emulator_env = os.environ.copy()
        emulator_env[constants.ENV_ANDROID_PRODUCT_OUT] = image_dir
        # Set ANDROID_TMP for emulator to create AVD info files in.
        emulator_env[constants.ENV_ANDROID_TMP] = working_dir
        # Set ANDROID_BUILD_TOP so that the emulator considers itself to be in
        # build environment.
        if constants.ENV_ANDROID_BUILD_TOP not in emulator_env:
            emulator_env[constants.ENV_ANDROID_BUILD_TOP] = image_dir

        # The command doesn't create -stdouterr-file automatically.
        with open(stdouterr_path, "w") as _:
            pass

        emulator_cmd = [
            os.path.abspath(emulator_path),
            "-verbose", "-show-kernel", "-read-only",
            "-ports", str(console_port) + "," + str(adb_port),
            "-logcat-output", logcat_path,
            "-stdouterr-file", stdouterr_path
        ]
        emulator_cmd.extend(extra_args)
        logger.debug("Execute %s", emulator_cmd)

        with open(os.devnull, "rb+") as devnull:
            return subprocess.Popen(
                emulator_cmd, shell=False, cwd=working_dir, env=emulator_env,
                stdin=devnull, stdout=devnull, stderr=devnull)

    def _WaitForEmulatorToStop(self, adb):
        """Wait for an emulator to be unavailable on the console port.

        Args:
            adb: adb_tools.AdbTools initialized with the emulator's serial.

        Raises:
            errors.CreateError if the emulator does not stop within timeout.
        """
        create_error = errors.CreateError(_EMU_KILL_TIMEOUT_ERROR %
                                          {"timeout": _EMU_KILL_TIMEOUT_SECS})
        utils.PollAndWait(func=lambda: self._IsEmulatorRunning(adb),
                          expected_return=False,
                          timeout_exception=create_error,
                          timeout_secs=_EMU_KILL_TIMEOUT_SECS,
                          sleep_interval_secs=1)

    def _WaitForEmulatorToStart(self, adb, proc, timeout):
        """Wait for an emulator to be available on the console port.

        Args:
            adb: adb_tools.AdbTools initialized with the emulator's serial.
            proc: Popen object, the running emulator process.
            timeout: Integer, timeout in seconds.

        Raises:
            errors.DeviceBootTimeoutError if the emulator does not boot within
            timeout.
            errors.SubprocessFail if the process terminates.
        """
        timeout_error = errors.DeviceBootTimeoutError(_EMULATOR_TIMEOUT_ERROR %
                                                      {"timeout": timeout})
        utils.PollAndWait(func=lambda: (proc.poll() is None and
                                        self._IsEmulatorRunning(adb)),
                          expected_return=True,
                          timeout_exception=timeout_error,
                          timeout_secs=timeout,
                          sleep_interval_secs=5)
        if proc.poll() is not None:
            raise errors.SubprocessFail("Emulator process returned %d." %
                                        proc.returncode)
