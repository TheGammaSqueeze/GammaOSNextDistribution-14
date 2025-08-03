"""Make sure the user build configuration is working as expected.

Although we can assume the features should be the same between user and user_debug build,
the configuration difference between this two build are not tested.

In this test suite, we modify the gps configuration to be the same as user build
and check if the setting is working.
For more details, please refer to : go/p22_user_build_verification
"""
import os
import re
import shutil
import tempfile
import time

from acts import asserts
from acts import signals
from acts.base_test import BaseTestClass
from acts_contrib.test_utils.gnss.testtracker_util import log_testtracker_uuid
from acts.controllers.adb_lib.error import AdbCommandError
from acts.libs.proc.job import TimeoutError
from acts_contrib.test_utils.wifi import wifi_test_utils as wutils
from acts_contrib.test_utils.gnss import gnss_test_utils as gutils


class GpsConfig:
    def __init__(self, ad, name) -> None:
        self.ad = ad
        self.name = name
        self.folder = "/vendor/etc/gnss"
        self.full_path = os.path.join(self.folder, self.name)
        self.logenabled = "LogEnabled"
        self._log_enable = "true"
        self._log_disable = "false"

    def _change_file_content(self, pattern, target):
        """Modify file via sed command

        command will be sed -i 's/<pattern>/<target>/g' <file_path>
        Args:
            pattern: a string will be used as search pattern
            target: string that will overwrite the matched result
        """
        self.ad.adb.remount()
        command = f"sed -i s/{pattern}/{target}/g {self.full_path}"
        self.ad.adb.shell(command)

    def _get_setting_value(self, key):
        """Get setting value from config file

        command is grep <key> self.full_path
        Args:
            key: a string will be used as search pattern
        Returns:
            string: grep result ("" for no grep result)
        """
        command = f"grep {key} {self.full_path}"
        result = self.ad.adb.shell(command)
        return result

    def _adjust_log_enable_setting(self, key, enable):
        """Enable / Disable in self.full_path by setting key = true / false
        Args:
            key: The target will be changed
            enable: True to enable / False to disable
        """
        src = self._log_disable if enable else self._log_enable
        target = self._log_enable if enable else self._log_disable
        pattern = f"{key}={src}"
        target = f"{key}={target}"
        self._change_file_content(pattern, target)
        result = self._get_setting_value(key)
        self.ad.log.debug("%s setting: %s", self.name, result)

    def _check_file_exist(self, file_pattern):
        """use command ls to check if file/dir exists
        command ls <file_pattern>
        Args:
            file_pattern: A string represents the file or dir
        Returns:
            bool: True -> file exists / False -> file doesn't exist
        """
        command = f"ls {file_pattern}"
        try:
            self.ad.adb.shell(command)
            result = True
        except AdbCommandError as e:
            result = False
        return result

    def enable_diagnostic_log(self):
        """Set LogEnabled=true in config file
        In gps.xml it will be LogEnabled=\"true\"
        """
        self.ad.log.info("Enable diagnostic log in %s", self.name)
        self._adjust_log_enable_setting(key=self.logenabled, enable=True)

    def disable_diagnostic_log(self):
        """Set LogEnabled=false in config file
        In gps.xml it will be LogEnabled=\"false\"
        """
        self.ad.log.info("Disable diagnostic log in %s", self.name)
        self._adjust_log_enable_setting(key=self.logenabled, enable=False)


class ScdConf(GpsConfig):
    def __init__(self, ad) -> None:
        super().__init__(ad, "scd.conf")


class GpsXml(GpsConfig):
    def __init__(self, ad) -> None:
        super().__init__(ad, "gps.xml")
        self.supllogenable = "SuplLogEnable"
        self.supl_log = "/data/vendor/gps/suplflow.txt"
        self._log_enable = "\\\"true\\\""
        self._log_disable = "\\\"false\\\""

    def enable_supl_log(self):
        """Set SuplLogEnable=\"true\" in gps.xml"""
        self.ad.log.info("Enable SUPL logs")
        self._adjust_log_enable_setting(key=self.supllogenable, enable=True)

    def disable_supl_log(self):
        """Set SuplLogEnable=\"false\" in gps.xml"""
        self.ad.log.info("Disable SUPL log")
        self._adjust_log_enable_setting(key=self.supllogenable, enable=False)

    def remove_supl_logs(self):
        """Remove /data/vendor/gps/suplflow.txt"""
        self.ad.log.info("Remove SUPL logs")
        command = f"rm -f {self.supl_log}"
        self.ad.adb.shell(command)

    def is_supl_log_file_exist(self):
        """Check if /data/vendor/gps/suplflow.txt exist
        Returns:
            bool: True -> supl log exists / False -> supl log doesn't exist
        """
        result = self._check_file_exist(self.supl_log)
        self.ad.log.debug("Supl file exists?: %s", result)
        return result


class LhdConf(GpsConfig):
    def __init__(self, ad) -> None:
        super().__init__(ad, "lhd.conf")
        self.lhefailsafe = "LheFailSafe"
        self.lheconsole = "LheConsole"
        self.lheconsole_hub = self.get_lheconsole_value()
        self.esw_crash_dump_pattern = self.get_esw_crash_dump_pattern()

    def _adjust_lhe_setting(self, key, enable):
        """Set lhe setting.
        Enable - uncomment out the setting
        Dissable - comment out the setting
        Args:
            key: A string will be used as search pattern
            enable: bool True to enable / False to disable
        """
        pattern = f"#\ {key}" if enable else key
        target = key if enable else f"#\ {key}"
        self._change_file_content(pattern, target)

    def enable_lhefailsafe(self):
        """Uncomment out LheFailSafe"""
        self.ad.log.info("Enable %s", self.lhefailsafe)
        self._adjust_lhe_setting(key=self.lhefailsafe, enable=True)

    def disable_lhefailsafe(self):
        """Comment out LheFailSafe"""
        self.ad.log.info("Disable %s", self.lhefailsafe)
        self._adjust_lhe_setting(key=self.lhefailsafe, enable=False)

    def enable_lheconsole(self):
        """Uncomment out LheConsole"""
        self.ad.log.info("Enable %s", self.lheconsole)
        self._adjust_lhe_setting(key=self.lheconsole, enable=True)

    def disable_lheconsole(self):
        """Comment out LheConsole"""
        self.ad.log.info("Disable %s", self.lheconsole)
        self._adjust_lhe_setting(key=self.lheconsole, enable=False)

    def get_lhefailsafe_value(self):
        """Get the LheFailSafe value

        Returns:
            string: the LheFailSafe value in config
        Raises:
            ValueError: No LheFailSafe value
        """
        result = self._get_setting_value(self.lhefailsafe)
        if not result:
            raise ValueError(("%s should exists in %s", self.lhefailsafe, self.name))
        result = result.split("=")[1]
        self.ad.log.debug("%s is %s", self.lhefailsafe, result)
        return result

    def get_lheconsole_value(self):
        """Get the LheConsole value

        Returns:
            string: the LheConsole value in config
        Raises:
            ValueError: No LheConsole value
        """
        result = self._get_setting_value(self.lheconsole)
        if not result:
            raise ValueError(("%s should exists in %s", self.lheconsole, self.name))
        result = result.split("=")[1]
        self.ad.log.debug("%s is %s", self.lheconsole, result)
        return result

    def get_esw_crash_dump_pattern(self):
        """Get the esw crash dump file pattern
        The value is set in LheFailSafe, but we need to add wildcard.
        Returns:
            string: esw crash dump pattern
        Raises:
            ValueError: No LheFailSafe value
        """
        value = self.get_lhefailsafe_value()
        value = value.replace(".txt", "*.txt")
        self.ad.log.debug("Dump file pattern is %s", value)
        return value

    def remove_esw_crash_dump_file(self):
        """Remove crash dump file"""
        self.ad.log.info("Remove esw crash file")
        command = f"rm -f {self.esw_crash_dump_pattern}"
        self.ad.adb.shell(command)

    def trigger_firmware_crash(self):
        """Send command to LheConsole to trigger firmware crash"""
        self.ad.log.info("Trigger firmware crash")
        command = f"echo Lhe:write=0xFFFFFFFF,4 > {self.lheconsole_hub}.toAsic"
        self.ad.adb.shell(command, timeout=10)

    def is_esw_crash_dump_file_exist(self):
        """Check if esw_crash_dump_pattern exists
        Will try 3 times, 1 second interval for each attempt
        Returns:
            bool: True -> file exists / False -> file doesn't exist
        """
        for attempt in range(1, 4):
            result = self._check_file_exist(self.esw_crash_dump_pattern)
            self.ad.log.debug("(Attempt %s)esw dump file exists?: %s", attempt, result)
            if result:
                return result
            time.sleep(1)
        return False


class GnssBroadcomConfigurationTest(BaseTestClass):
    """ GNSS configuration Tests on Broadcom device."""
    def setup_class(self):
        super().setup_class()
        self.ad = self.android_devices[0]
        req_params = ["standalone_cs_criteria"]
        self.unpack_userparams(req_param_names=req_params)

        if not gutils.check_chipset_vendor_by_qualcomm(self.ad):
            self.init_device()
            self.gps_config_path = tempfile.mkdtemp()
            self.gps_xml = GpsXml(self.ad)
            self.lhd_conf = LhdConf(self.ad)
            self.scd_conf = ScdConf(self.ad)
            self.enable_testing_setting()
            self.backup_gps_config()

    def init_device(self):
        gutils._init_device(self.ad)
        gutils.enable_supl_mode(self.ad)
        gutils.enable_vendor_orbit_assistance_data(self.ad)
        wutils.wifi_toggle_state(self.ad, True)
        gutils.set_mobile_data(self.ad, state=True)


    def teardown_class(self):
        if hasattr(self, "gps_config_path") and os.path.isdir(self.gps_config_path):
            shutil.rmtree(self.gps_config_path)

    def setup_test(self):
        gutils.log_current_epoch_time(self.ad, "test_start_time")
        log_testtracker_uuid(self.ad, self.current_test_name)
        if gutils.check_chipset_vendor_by_qualcomm(self.ad):
            raise signals.TestSkip("Device is Qualcomm, skip the test")
        gutils.get_baseband_and_gms_version(self.ad)
        gutils.clear_logd_gnss_qxdm_log(self.ad)

    def teardown_test(self):
        if not gutils.check_chipset_vendor_by_qualcomm(self.ad):
            self.revert_gps_config()
            self.ad.reboot()
        gutils.log_current_epoch_time(self.ad, "test_end_time")

    def on_fail(self, test_name, begin_time):
        self.ad.take_bug_report(test_name, begin_time)
        gutils.get_gnss_qxdm_log(self.ad)

    def enable_testing_setting(self):
        """Enable setting to the testing target
        Before backing up config, enable all the testing target
        To ensure the teardown_test can bring the device back to the desired state
        """
        self.set_gps_logenabled(enable=True)
        self.gps_xml.enable_supl_log()
        self.lhd_conf.enable_lheconsole()
        self.lhd_conf.enable_lhefailsafe()

    def backup_gps_config(self):
        """Copy the gps config

        config file will be copied: gps.xml / lhd.conf / scd.conf
        """
        for conf in [self.gps_xml, self.scd_conf, self.lhd_conf]:
            self.ad.log.debug("Backup %s", conf.full_path)
            self.ad.adb.pull(conf.full_path, self.gps_config_path)

    def revert_gps_config(self):
        """Revert the gps config from the one we backup in the setup_class

        config file will be reverted: gps.xml / lhd.conf / scd.conf
        """
        self.ad.adb.remount()
        for conf in [self.gps_xml, self.scd_conf, self.lhd_conf]:
            file_path = os.path.join(self.gps_config_path, conf.name)
            self.ad.log.debug("Revert %s", conf.full_path)
            self.ad.adb.push(file_path, conf.full_path)

    def run_gps_and_capture_log(self):
        """Enable GPS via gps tool for 15s and capture pixel log"""
        gutils.start_pixel_logger(self.ad)
        gutils.gnss_tracking_via_gtw_gpstool(self.ad, self.standalone_cs_criteria, testtime=1)

    def set_gps_logenabled(self, enable):
        """Set LogEnabled in gps.xml / lhd.conf / scd.conf

        Args:
            enable: True to enable / False to disable
        """
        if enable:
            self.gps_xml.enable_diagnostic_log()
            self.scd_conf.enable_diagnostic_log()
            self.lhd_conf.enable_diagnostic_log()
        else:
            self.gps_xml.disable_diagnostic_log()
            self.scd_conf.disable_diagnostic_log()
            self.lhd_conf.disable_diagnostic_log()

    def test_gps_logenabled_setting(self):
        """Verify the LogEnabled setting in gps.xml / scd.conf / lhd.conf
        Steps:
            1. default setting is on in user_debug build
            2. run gps tracking for 1 min
            3. should find slog in pixel logger log files
            4. disable LogEnabled in all the gps conf
            5. run gps tracking for 1 min
            6. should not find slog in pixel logger log files
        """
        self.run_gps_and_capture_log()
        pattern = re.compile(f".*slog\s+:.*")
        result, _ = gutils.parse_brcm_nmea_log(self.ad, pattern, [])
        asserts.assert_true(bool(result), "LogEnabled is set to true, but no gps log was found")

        self.set_gps_logenabled(enable=False)
        gutils.clear_logd_gnss_qxdm_log(self.ad)
        # Removes pixel logger path again in case pixel logger still writes log unexpectedly.
        gutils.remove_pixel_logger_folder(self.ad)

        self.run_gps_and_capture_log()
        try:
            result, _ = gutils.parse_brcm_nmea_log(self.ad, pattern, [])
            asserts.assert_false(
                bool(result),
                ("LogEnabled is set to False but still found %d slog" % len(result)))
        except FileNotFoundError:
            self.ad.log.info("Test pass because no BRCM log files/folders was found")

    def test_gps_supllogenable_setting(self):
        """Verify SuplLogEnable in gps.xml
        Steps:
            1. default setting is on in user_debug build
            2. remove existing supl log
            3. run gps tracking for 1 min
            4. supl log should exist
            5. disable SuplLogEnable in gps.xml
            6. remove existing supl log
            7. run gps tracking for 1 min
            8. supl log should not exist
        """
        def is_supl_log_exist_after_supl_request():
            self.gps_xml.remove_supl_logs()
            self.ad.reboot()
            self.run_gps_and_capture_log()
            return self.gps_xml.is_supl_log_file_exist()

        result = is_supl_log_exist_after_supl_request()
        asserts.assert_true(result, "SuplLogEnable is enable, should find supl log file")

        self.gps_xml.disable_supl_log()

        result = is_supl_log_exist_after_supl_request()
        asserts.assert_false(result, "SuplLogEnable is disable, should not find supl log file")

    def test_lhe_setting(self):
        """Verify lhefailsafe / lheconsole setting in lhd.conf
        Steps:
            1. both setting is enabled
            2. trigger firmware crash and check if dump file exist
            3. disable lhefailsafe
            4. trigger firmware crash and check if dump file exist
            5. disable lheconsle
            6. trigger firmware crash and check if command timeout
        """
        def is_dump_file_exist_after_firmware_crash():
            self.lhd_conf.remove_esw_crash_dump_file()
            self.lhd_conf.trigger_firmware_crash()
            return self.lhd_conf.is_esw_crash_dump_file_exist()

        result = is_dump_file_exist_after_firmware_crash()
        asserts.assert_true(result, "LheFailSafe is enabled, but no crash file was found")

        self.lhd_conf.disable_lhefailsafe()
        self.ad.reboot()

        result = is_dump_file_exist_after_firmware_crash()
        asserts.assert_false(result, "LheFailSafe is disabled, but still found crash file")

        self.lhd_conf.disable_lheconsole()
        self.ad.reboot()

        with asserts.assert_raises(TimeoutError):
            self.lhd_conf.trigger_firmware_crash()
