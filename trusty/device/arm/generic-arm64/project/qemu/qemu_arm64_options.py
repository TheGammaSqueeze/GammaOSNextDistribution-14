"""Generate QEMU options for Trusty test framework"""

import subprocess
import tempfile

from qemu_error import RunnerGenericError


class QemuArm64Options(object):

    MACHINE = "virt,secure=on,virtualization=on"

    BASIC_ARGS = [
        "-nographic", "-cpu", "cortex-a57", "-smp", "4", "-m", "1024", "-d",
        "unimp", "-semihosting-config", "enable,target=native", "-no-acpi",
    ]

    LINUX_ARGS = (
        "earlyprintk console=ttyAMA0,38400 keep_bootcon "
        "root=/dev/vda ro init=/init androidboot.hardware=qemu_trusty "
        "trusty-log.log_ratelimit_interval=0 trusty-log.log_to_dmesg=always")

    def __init__(self, config):
        self.args = []
        self.config = config

    def rpmb_data_path(self):
        return f"{self.config.atf}/RPMB_DATA"

    def rpmb_options(self, sock):
        return [
            "-device", "virtio-serial",
            "-device", "virtserialport,chardev=rpmb0,name=rpmb0",
            "-chardev", f"socket,id=rpmb0,path={sock}"]

    def gen_dtb(self, args, dtb_tmp_file):
        """Computes a trusty device tree, returning a file for it"""
        with tempfile.NamedTemporaryFile() as dtb_gen:
            dump_dtb_cmd = [
                self.config.qemu, "-machine",
                f"{self.MACHINE},dumpdtb={dtb_gen.name}"
            ] + [arg for arg in args if arg != "-S"]
            returncode = subprocess.call(dump_dtb_cmd)
            if returncode != 0:
                raise RunnerGenericError(
                    f"dumping dtb failed with {returncode}")
            dtc = f"{self.config.linux}/scripts/dtc/dtc"
            dtb_to_dts_cmd = [dtc, "-q", "-O", "dts", dtb_gen.name]
            # pylint: disable=consider-using-with
            with subprocess.Popen(dtb_to_dts_cmd,
                                  stdout=subprocess.PIPE,
                                  universal_newlines=True) as dtb_to_dts:
                dts = dtb_to_dts.communicate()[0]
                if dtb_to_dts.returncode != 0:
                    raise RunnerGenericError(
                        f"dtb_to_dts failed with {dtb_to_dts.returncode}")

        firmware = f"{self.config.atf}/firmware.android.dts"
        with open(firmware, "r", encoding="utf-8") as firmware_file:
            dts += firmware_file.read()

        # Subprocess closes dtb, so we can't allow it to autodelete
        dtb = dtb_tmp_file
        dts_to_dtb_cmd = [dtc, "-q", "-O", "dtb"]
        with subprocess.Popen(dts_to_dtb_cmd,
                              stdin=subprocess.PIPE,
                              stdout=dtb,
                              universal_newlines=True) as dts_to_dtb:
            dts_to_dtb.communicate(dts)
            dts_to_dtb_ret = dts_to_dtb.wait()

        if dts_to_dtb_ret:
            raise RunnerGenericError(f"dts_to_dtb failed with {dts_to_dtb_ret}")
        return ["-dtb", dtb.name]

    def drive_args(self, image, index):
        """Generates arguments for mapping a drive"""
        index_letter = chr(ord('a') + index)
        image_dir = f"{self.config.android}/target/product/trusty"
        return [
            "-drive",
            # pylint: disable=line-too-long
            f"file={image_dir}/{image}.img,index={index},if=none,id=hd{index_letter},format=raw,snapshot=on",
            "-device",
            f"virtio-blk-device,drive=hd{index_letter}"
        ]

    def android_drives_args(self):
        """Generates arguments for mapping all default drives"""
        args = []
        # This is order sensitive due to using e.g. root=/dev/vda
        args += self.drive_args("userdata", 2)
        args += self.drive_args("vendor", 1)
        args += self.drive_args("system", 0)
        return args

    def machine_options(self):
        return ["-machine", self.MACHINE]

    def basic_options(self):
        return list(self.BASIC_ARGS)

    def bios_options(self):
        return ["-bios", f"{self.config.atf}/bl1.bin"]

    def linux_options(self):
        return [
            "-kernel",
            f"{self.config.linux}/arch/{self.config.linux_arch}/boot/Image",
            "-append", self.LINUX_ARGS
        ]

    def android_trusty_user_data(self):
        return f"{self.config.android}/target/product/trusty/data"
