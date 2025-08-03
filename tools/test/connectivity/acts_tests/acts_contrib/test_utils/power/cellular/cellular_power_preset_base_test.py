import json
import os
from typing import Optional, List
import time

from acts import asserts
from acts import signals
import acts_contrib.test_utils.power.cellular.cellular_power_base_test as PWCEL
from acts_contrib.test_utils.tel import tel_test_utils as telutils
from acts_contrib.test_utils.power.cellular import modem_logs

# TODO: b/261639867
class AtUtil():
    """Util class for sending at command.

    Attributes:
        dut: AndroidDevice controller object.
    """
    ADB_CMD_DISABLE_TXAS = 'am instrument -w -e request at+googtxas=2 -e response wait "com.google.mdstest/com.google.mdstest.instrument.ModemATCommandInstrumentation"'
    ADB_CMD_GET_TXAS = 'am instrument -w -e request at+googtxas? -e response wait "com.google.mdstest/com.google.mdstest.instrument.ModemATCommandInstrumentation"'
    ADB_MODEM_STATUS = 'cat /sys/bus/platform/devices/cpif/modem_state'
    ADB_CMD_SET_NV = ('am instrument -w '
                      '-e request \'at+googsetnv=\"{nv_name}\",{nv_index},\"{nv_value}\"\' '
                      '-e response wait "com.google.mdstest/com.google.mdstest.instrument.ModemATCommandInstrumentation"')

    def __init__(self, dut, log) -> None:
        self.dut = dut
        self.log = log

    # TODO: to be remove when b/261639867 complete,
    # and we are using parent method.
    def send(self, cmd: str, retries: int=5) -> Optional[str]:
        for _ in range(30):
            modem_status = self.dut.adb.shell(self.ADB_MODEM_STATUS)
            self.log.debug(f'Modem status: {modem_status}')
            if modem_status == 'ONLINE':
                break
            time.sleep(1)

        wait_for_device_ready_time = 2
        for i in range(retries):
            res = self.dut.adb.shell(cmd)
            self.log.info(f'cmd sent: {cmd}')
            self.log.debug(f'response: {res}')
            if 'SUCCESS' in res and 'OK' in res:
                return res
            else:
                self.log.warning('Fail to execute cmd, retry to send again.')
                time.sleep(wait_for_device_ready_time)
        self.log.error(f'Fail to execute cmd: {cmd}')
        return res

    def lock_band(self):
        """Lock lte and nr bands.

        LTE bands to be locked include B1, B2, B4.
        NR bands to belocked include n71, n78, n260.
        """
        adb_enable_band_lock_lte = r'am instrument -w -e request at+GOOGSETNV=\"!SAEL3.Manual.Band.Select\ Enb\/\ Dis\",00,\"01\" -e response wait "com.google.mdstest/com.google.mdstest.instrument.ModemATCommandInstrumentation"'
        adb_set_band_lock_bitmap_0 = r'am instrument -w -e request at+GOOGSETNV=\"!SAEL3.Manual.Enabled.RFBands.BitMap\",0,\"0B,00,00,00,00,00,00,00\" -e response wait "com.google.mdstest/com.google.mdstest.instrument.ModemATCommandInstrumentation"'
        adb_set_band_lock_bitmap_1 = r'am instrument -w -e request at+GOOGSETNV=\"!SAEL3.Manual.Enabled.RFBands.BitMap\",1,\"00,00,00,00,00,00,00,00\" -e response wait "com.google.mdstest/com.google.mdstest.instrument.ModemATCommandInstrumentation"'
        adb_set_band_lock_bitmap_2 = r'am instrument -w -e request at+GOOGSETNV=\"!SAEL3.Manual.Enabled.RFBands.BitMap\",2,\"00,00,00,00,00,00,00,00\" -e response wait "com.google.mdstest/com.google.mdstest.instrument.ModemATCommandInstrumentation"'
        adb_set_band_lock_bitmap_3 = r'am instrument -w -e request at+GOOGSETNV=\"!SAEL3.Manual.Enabled.RFBands.BitMap\",3,\"00,00,00,00,00,00,00,00\" -e response wait "com.google.mdstest/com.google.mdstest.instrument.ModemATCommandInstrumentation"'
        # enable lte
        self.send(adb_enable_band_lock_lte)
        time.sleep(2)

        # lock to B1, B2 and B4
        self.send(adb_set_band_lock_bitmap_0)
        time.sleep(2)
        self.send(adb_set_band_lock_bitmap_1)
        time.sleep(2)
        self.send(adb_set_band_lock_bitmap_2)
        time.sleep(2)
        self.send(adb_set_band_lock_bitmap_3)
        time.sleep(2)

        adb_enable_band_lock_nr = r'am instrument -w -e request at+GOOGSETNV=\"!NRRRC.SIM_BASED_BAND_LIST_SUPPORT\",00,\"01\" -e response wait "com.google.mdstest/com.google.mdstest.instrument.ModemATCommandInstrumentation"'
        self.send(adb_enable_band_lock_nr)
        time.sleep(2)
        adb_add_band_list_n71 = r'am instrument -w -e request at+GOOGSETNV=\"!NRRRC.SIM_OPERATOR_BAND_LIST\",00,\"47,00\" -e response wait "com.google.mdstest/com.google.mdstest.instrument.ModemATCommandInstrumentation"'
        self.send(adb_add_band_list_n71)
        time.sleep(2)
        adb_add_band_list_n78 = r'am instrument -w -e request at+GOOGSETNV=\"!NRRRC.SIM_OPERATOR_BAND_LIST\",01,\"4E,00\" -e response wait "com.google.mdstest/com.google.mdstest.instrument.ModemATCommandInstrumentation"'
        self.send(adb_add_band_list_n78)
        time.sleep(2)
        adb_add_band_list_n260 = r'am instrument -w -e request at+GOOGSETNV=\"!NRRRC.SIM_OPERATOR_BAND_LIST\",02,\"04,01\" -e response wait "com.google.mdstest/com.google.mdstest.instrument.ModemATCommandInstrumentation"'
        self.send(adb_add_band_list_n260)
        time.sleep(2)

    def disable_lock_band_lte(self):
        adb_disable_band_lock_lte = r'am instrument -w -e request at+GOOGSETNV=\"!SAEL3.Manual.Band.Select\ Enb\/\ Dis\",0,\"01\" -e response wait "com.google.mdstest/com.google.mdstest.instrument.ModemATCommandInstrumentation"'

        # disable band lock lte
        self.send(adb_disable_band_lock_lte)
        time.sleep(2)

    def disable_txas(self):
        res = self.send(self.ADB_CMD_GET_TXAS)
        if '+GOOGGETTXAS:2' in res:
            self.log.info('TXAS is in default.')
            return res
        cmd = self.ADB_CMD_DISABLE_TXAS
        response = self.send(cmd)
        return 'OK' in response

    def get_band_lock_info(self):
        cmd = r'am instrument -w -e request at+GOOGGETNV=\"!SAEL3.Manual.Enabled.RFBands.BitMap\" -e response wait "com.google.mdstest/com.google.mdstest.instrument.ModemATCommandInstrumentation"'
        res = self.send(cmd)
        cmd = r'am instrument -w -e request at+GOOGGETNV=\"!SAEL3.Manual.Band.Select\ Enb\/\ Dis\" -e response wait "com.google.mdstest/com.google.mdstest.instrument.ModemATCommandInstrumentation"'
        res += self.send(cmd)
        cmd = r'am instrument -w -e request at+GOOGGETNV=\"!NRRRC.SIM_BASED_BAND_LIST_SUPPORT\" -e response wait "com.google.mdstest/com.google.mdstest.instrument.ModemATCommandInstrumentation"'
        res += self.send(cmd)
        cmd = r'am instrument -w -e request at+GOOGGETNV=\"!NRRRC.SIM_OPERATOR_BAND_LIST\" -e response wait "com.google.mdstest/com.google.mdstest.instrument.ModemATCommandInstrumentation"'
        res += self.send(cmd)
        return res

    def set_nv(self, nv_name, index, value):
        cmd = self.ADB_CMD_SET_NV.format(
            nv_name=nv_name,
            nv_index=index,
            nv_value=value
        )
        res = self.send(cmd)
        return res

    def get_sim_slot_mapping(self):
        cmd = r'am instrument -w -e request at+slotmap? -e response wait "com.google.mdstest/com.google.mdstest.instrument.ModemATCommandInstrumentation"'
        return self.send(cmd)

    def set_single_psim(self):
        cmd = r'am instrument -w -e request at+slotmap=1 -e response wait "com.google.mdstest/com.google.mdstest.instrument.ModemATCommandInstrumentation"'
        return self.send(cmd)

    def disable_dsp(self):
        cmd = r'am instrument -w -e request at+googsetnv=\"NASU\.LCPU\.LOG\.SWITCH\",0,\"00\" -e response wait "com.google.mdstest/com.google.mdstest.instrument.ModemATCommandInstrumentation"'
        return self.send(cmd)

    def get_dsp_status(self):
        cmd = r'am instrument -w -e request at+googgetnv=\"NASU\.LCPU\.LOG\.SWITCH\" -e response wait "com.google.mdstest/com.google.mdstest.instrument.ModemATCommandInstrumentation"'
        return self.send(cmd)

    def enable_ims_nr(self):
        # set !NRCAPA.Gen.VoiceOverNr
        self.set_nv(
            nv_name = '!NRCAPA.Gen.VoiceOverNr',
            index = '0',
            value = '01'
        )
        # set PSS.AIMS.Enable.NRSACONTROL
        self.set_nv(
            nv_name = 'PSS.AIMS.Enable.NRSACONTROL',
            index = '0',
            value = '00'
        )
        # set DS.PSS.AIMS.Enable.NRSACONTROL
        self.set_nv(
            nv_name = 'DS.PSS.AIMS.Enable.NRSACONTROL',
            index = '0',
            value = '00'
        )
        if self.dut.model == 'oriole':
            # For P21, NR.CONFIG.MODE/DS.NR.CONFIG.MODE
            self.set_nv(
                nv_name = 'NR.CONFIG.MODE',
                index = '0',
                value = '11'
            )
            # set DS.NR.CONFIG.MODE
            self.set_nv(
                nv_name = 'DS.NR.CONFIG.MODE',
                index = '0',
                value = '11'
            )
        else:
            # For P22, NASU.NR.CONFIG.MODE to 11
            self.set_nv(
                nv_name = 'NASU.NR.CONFIG.MODE',
                index = '0',
                value = '11'
            )

class PowerCellularPresetLabBaseTest(PWCEL.PowerCellularLabBaseTest):
    # Key for ODPM report
    ODPM_ENERGY_TABLE_NAME = 'PowerStats HAL 2.0 energy meter'
    ODPM_MODEM_CHANNEL_NAME = '[VSYS_PWR_MODEM]:Modem'

    # Pass fail threshold lower bound
    THRESHOLD_TOLERANCE_LOWER_BOUND_DEFAULT = 0.3

    # Key for custom_property in Sponge
    CUSTOM_PROP_KEY_BUILD_ID = 'build_id'
    CUSTOM_PROP_KEY_INCR_BUILD_ID = 'incremental_build_id'
    CUSTOM_PROP_KEY_BUILD_TYPE = 'build_type'
    CUSTOM_PROP_KEY_SYSTEM_POWER = 'system_power'
    CUSTOM_PROP_KEY_MODEM_BASEBAND = 'baseband'
    CUSTOM_PROP_KEY_MODEM_ODPM_POWER= 'modem_odpm_power'
    CUSTOM_PROP_KEY_DEVICE_NAME = 'device'
    CUSTOM_PROP_KEY_DEVICE_BUILD_PHASE = 'device_build_phase'
    CUSTOM_PROP_KEY_MODEM_KIBBLE_POWER = 'modem_kibble_power'
    CUSTOM_PROP_KEY_TEST_NAME = 'test_name'
    CUSTOM_PROP_KEY_MODEM_KIBBLE_WO_PCIE_POWER = 'modem_kibble_power_wo_pcie'
    CUSTOM_PROP_KEY_MODEM_KIBBLE_PCIE_POWER = 'modem_kibble_pcie_power'
    CUSTOM_PROP_KEY_RFFE_POWER = 'rffe_power'
    CUSTOM_PROP_KEY_MMWAVE_POWER = 'mmwave_power'
    CUSTOM_PROP_KEY_CURRENT_REFERENCE_TARGET = 'reference_target'
    # kibble report
    KIBBLE_SYSTEM_RECORD_NAME = '- name: default_device.C10_EVT_1_1.Monsoon:mA'
    MODEM_PCIE_RAIL_NAME_LIST = [
        'PP1800_L2C_PCIEG3',
        'PP1200_L9C_PCIE',
        'PP0850_L8C_PCIE'
    ]

    MODEM_RFFE_RAIL_NAME = 'VSYS_PWR_RFFE'

    MODEM_POWER_RAIL_NAME = 'VSYS_PWR_MODEM'

    MODEM_POWER_RAIL_WO_PCIE_NAME = 'VSYS_PWR_MODEM_W_O_PCIE'

    WEARABLE_POWER_RAIL = 'LTE_DC'

    WEARABLE_SOC_MODEM_RAIL = 'SOC_MODEM_USBHS'

    MODEM_MMWAVE_RAIL_NAME = 'VSYS_PWR_MMWAVE'

    MONSOON_RAIL_NAME = 'Monsoon:mW'

    # params key
    MONSOON_VOLTAGE_KEY = 'mon_voltage'

    MDSTEST_APP_APK_NAME = 'mdstest.apk'

    ADB_CMD_ENABLE_ALWAYS_ON_LOGGING = (
        'am broadcast -n com.android.pixellogger/.receiver.AlwaysOnLoggingReceiver '
        '-a com.android.pixellogger.service.logging.LoggingService.ACTION_CONFIGURE_ALWAYS_ON_LOGGING '
        '-e intent_key_enable "true" '
        '-e intent_key_config "Lassen\ default" '
        '--ei intent_key_max_log_size_mb 100 '
        '--ei intent_key_max_number_of_files 20'
    )
    ADB_CMD_DISABLE_ALWAYS_ON_LOGGING = (
        'am start-foreground-service -a '
        'com.android.pixellogger.service.logging.LoggingService.ACTION_STOP_LOGGING')

    ADB_CMD_TOGGLE_MODEM_LOG = 'setprop persist.vendor.sys.modem.logging.enable {state}'

    _ADB_GET_ACTIVE_NETWORK = ('dumpsys connectivity | '
                             'grep \'Active default network\'')

    def __init__(self, controllers):
        super().__init__(controllers)
        self.retryable_exceptions = signals.TestFailure
        self.power_rails = {}
        self.pcie_power = 0
        self.rffe_power = 0
        self.mmwave_power = 0
        self.modem_power = 0
        self.monsoon_power = 0
        self.kibble_error_range = 2
        self.system_power = 0
        self.odpm_power = 0

    def setup_class(self):
        super().setup_class()

        # preset callbox
        is_fr2 = 'Fr2' in self.TAG
        self.cellular_simulator.switch_HCCU_settings(is_fr2=is_fr2)

        self.at_util = AtUtil(self.cellular_dut.ad, self.log)

        # preset UE.
        self.log.info(f'Bug report mode: {self.bug_report}')
        self.toggle_modem_log(False)
        self.log.info('Installing mdstest app.')
        self.install_apk()

        self.unpack_userparams(is_mdstest_supported=True)
        self.log.info(f'Supports mdstest: {self.is_mdstest_supported}')
        if self.is_mdstest_supported:
            # UE preset
            self.log.info('Disable antenna switch.')
            self.at_util.disable_txas()
            time.sleep(10)

            # set device to be data centric
            nv_result = self.at_util.set_nv(
                nv_name = '!SAEL3.SAE_UE_OPERATION_MODE',
                index = '0',
                value = '03'
            )
            self.log.info(nv_result)

            self.at_util.lock_band()
            self.log.info('Band lock info: \n%s',self.at_util.get_band_lock_info())

            self.at_util.set_single_psim()

        self.unpack_userparams(is_wifi_only_device=False)

        # extract log only flag
        self.unpack_userparams(collect_log_only=False)
        # get sdim type
        self.unpack_userparams(has_3gpp_sim=True)
        # extract time to take log after test
        self.unpack_userparams(post_test_log_duration=30)

        # toggle on/off APM for all devices
        self.log.info('Toggle APM on/off for all devices.')
        for ad in self.android_devices:
            telutils.toggle_airplane_mode_by_adb(self.log, ad, False)
            time.sleep(2)
            telutils.toggle_airplane_mode_by_adb(self.log, ad, True)
            time.sleep(2)

        # clear modem logs
        modem_logs.clear_modem_logging(self.cellular_dut.ad)

    def collect_power_data_and_validate(self):
        cells_status_before = sorted(self.cellular_simulator.get_all_cell_status())
        self.log.info('UXM cell status before collect power: %s', cells_status_before)

        super().collect_power_data()
        cells_status_after = sorted(self.cellular_simulator.get_all_cell_status())
        self.log.info('UXM cell status after collect power: %s', cells_status_after)

        # power measurement results
        odpm_power_results = self.get_odpm_values()
        self.odpm_power = odpm_power_results.get(
            self.ODPM_MODEM_CHANNEL_NAME.lower(), 0)
        if hasattr(self, 'bitses'):
            self.parse_power_rails_csv()

        asserts.assert_true(cells_status_before == cells_status_after,
            'Cell status before {} and after {} the test run are not the same.'.format(
                cells_status_before, cells_status_after
            ))
        self.threshold_check()

    def setup_test(self):
        try:
            if self.collect_log_only:
                self.log.info('Collect log only mode on.')
                # set log mask
                modem_logs.set_modem_log_profle(self.cellular_dut.ad, modem_logs.ModemLogProfile.LASSEN_TCP_DSP)
                # start log
                modem_logs.start_modem_logging(self.cellular_dut.ad)
            modem_log_dir = os.path.join(self.root_output_path, 'modem_log')
            os.makedirs(modem_log_dir, exist_ok=True)
            self.modem_log_path = os.path.join(modem_log_dir, self.test_name)
            os.makedirs(self.modem_log_path, exist_ok=True)
            super().setup_test()
        except BrokenPipeError:
            self.log.info('TA crashed test need retry.')
            self.need_retry = True
            self.cellular_simulator.recovery_ta()
            self.cellular_simulator.socket_connect()
            raise signals.TestFailure('TA crashed mid test, retry needed.')

    def toggle_modem_log(self, new_state: bool, timeout: int=30):
        new_state = str(new_state).lower()
        current_state = self.cellular_dut.ad.adb.shell('getprop persist.vendor.sys.modem.logging.enable')
        cmd = self.ADB_CMD_TOGGLE_MODEM_LOG.format(state=new_state)
        if new_state != current_state:
            self.cellular_dut.ad.adb.shell(cmd)
            for _ in range(timeout):
                self.log.debug(f'Wait for modem logging status to be {new_state}.')
                time.sleep(1)
                current_state = self.cellular_dut.ad.adb.shell('getprop persist.vendor.sys.modem.logging.enable')
                if new_state == current_state:
                    self.log.info(f'Always-on modem logging status is {new_state}.')
                    return
            raise RuntimeError(f'Fail to set modem logging to {new_state}.')

    def collect_modem_log(self, out_path, duration: int=30):
        # set log mask
        modem_logs.set_modem_log_profle(self.cellular_dut.ad, modem_logs.ModemLogProfile.LASSEN_TCP_DSP)

        # start log
        modem_logs.start_modem_logging(self.cellular_dut.ad)
        time.sleep(duration)
        # stop log
        modem_logs.stop_modem_logging(self.cellular_dut.ad)
        try:
            # pull log
            modem_logs.pull_logs(self.cellular_dut.ad, out_path)
        finally:
            # clear log
            modem_logs.clear_modem_logging(self.cellular_dut.ad)

    def install_apk(self):
        sleep_time = 3
        for file in self.custom_files:
            if self.MDSTEST_APP_APK_NAME in file:
                if not self.cellular_dut.ad.is_apk_installed("com.google.mdstest"):
                    self.cellular_dut.ad.adb.install("-r -g %s" % file, timeout=300, ignore_status=True)
        time.sleep(sleep_time)
        if self.cellular_dut.ad.is_apk_installed("com.google.mdstest"):
            self.log.info('mdstest installed.')
        else:
            self.log.warning('fail to install mdstest.')

    def get_odpm_values(self):
        """Get power measure from ODPM.

        Parsing energy table in ODPM file
        and convert to.
        Returns:
            odpm_power_results: a dictionary
                has key as channel name,
                and value as power measurement of that channel.
        """
        self.log.info('Start calculating power by channel from ODPM report.')
        odpm_power_results = {}

        # device before P21 don't have ODPM reading
        if not self.odpm_folder:
            return odpm_power_results

        # getting ODPM modem power value
        odpm_file_name = '{}.{}.dumpsys_odpm_{}.txt'.format(
            self.__class__.__name__,
            self.current_test_name,
            'after')
        odpm_file_path = os.path.join(self.odpm_folder, odpm_file_name)
        if os.path.exists(odpm_file_path):
            elapsed_time = None
            with open(odpm_file_path, 'r') as f:
                # find energy table in ODPM report
                for line in f:
                    if self.ODPM_ENERGY_TABLE_NAME in line:
                        break

                # get elapse time 2 adb ODPM cmd (mS)
                elapsed_time_str = f.readline()
                elapsed_time = float(elapsed_time_str
                                        .split(':')[1]
                                        .strip()
                                        .split(' ')[0])
                self.log.info(elapsed_time_str)

                # skip column name row
                next(f)

                # get power of different channel from odpm report
                for line in f:
                    if 'End' in line:
                        break
                    else:
                        # parse columns
                        # example result of line.strip().split()
                        # ['[VSYS_PWR_DISPLAY]:Display', '1039108.42', 'mWs', '(', '344.69)']
                        channel, _, _, _, delta_str = line.strip().split()
                        channel = channel.lower()
                        delta = float(delta_str[:-2].strip())

                        # calculate OPDM power
                        # delta is a different in cumulative energy
                        # between 2 adb ODPM cmd
                        elapsed_time_s = elapsed_time / 1000
                        power = delta / elapsed_time_s
                        odpm_power_results[channel] = power
                        self.log.info(
                            channel + ' ' + str(power) + ' mW'
                        )
        return odpm_power_results

    def _is_any_substring(self, longer_word: str, word_list: List[str]) -> bool:
        """Check if any word in word list a substring of a longer word."""
        return any(w in longer_word for w in word_list)

    def parse_power_rails_csv(self):
        kibble_dir = os.path.join(self.root_output_path, 'Kibble')
        kibble_json_path = None
        if os.path.exists(kibble_dir):
            for f in os.listdir(kibble_dir):
                if self.test_name in f and '.json' in f:
                    kibble_json_path = os.path.join(kibble_dir, f)
                    self.log.info('Kibble json file path: ' + kibble_json_path)
                    break

        self.log.info('Parsing power rails from csv.')
        if kibble_json_path:
            with open(kibble_json_path, 'r') as f:
                rails_data_json = json.load(f)
            if rails_data_json:
                for record in rails_data_json:
                    unit = record['unit']
                    if unit != 'mW':
                        continue
                    railname = record['name']
                    power = record['avg']
                    # parse pcie power
                    if self._is_any_substring(railname, self.MODEM_PCIE_RAIL_NAME_LIST):
                        self.log.info('%s: %f',railname, power)
                        self.pcie_power += power
                    elif self.MODEM_POWER_RAIL_NAME in railname:
                        self.log.info('%s: %f',railname, power)
                        self.modem_power = power
                    elif self.MODEM_RFFE_RAIL_NAME in railname:
                        self.log.info('%s: %f',railname, power)
                        self.rffe_power = power
                    elif self.MODEM_MMWAVE_RAIL_NAME in railname:
                        self.log.info('%s: %f',railname, power)
                        self.mmwave_power = power
                    elif self.MONSOON_RAIL_NAME in railname:
                        self.log.info('%s: %f',railname, power)
                        self.monsoon_power = power
                    elif self.WEARABLE_POWER_RAIL in railname or self.WEARABLE_SOC_MODEM_RAIL in railname:
                        self.log.info('%s: %f',railname, power)
                        self.modem_power += power
        if self.modem_power:
            self.power_results[self.test_name] = self.modem_power

    def sponge_upload(self):
        """Upload result to sponge as custom field."""
        # test name
        test_name_arr = self.current_test_name.split('_')
        test_name_for_sponge = ''.join(
            word[0].upper() + word[1:].lower()
                for word in test_name_arr
                    if word not in ('preset', 'test')
        )

        # build info
        build_info = self.cellular_dut.ad.build_info
        build_id = build_info.get('build_id', 'Unknown')
        incr_build_id = build_info.get('incremental_build_id', 'Unknown')
        modem_base_band = self.cellular_dut.ad.adb.getprop(
            'gsm.version.baseband')
        build_type = build_info.get('build_type', 'Unknown')

        # device info
        device_info = self.cellular_dut.ad.device_info
        device_name = device_info.get('model', 'Unknown')
        device_build_phase = self.cellular_dut.ad.adb.getprop(
            'ro.boot.hardware.revision'
        )

        # if kibbles are using, get power from kibble
        modem_kibble_power_wo_pcie = 0
        if hasattr(self, 'bitses'):
            modem_kibble_power_wo_pcie = self.modem_power - self.pcie_power
            self.system_power = self.monsoon_power
        else:
            self.system_power = self.power_results.get(self.test_name, 0)

        # record reference target, if it exists
        self.reference_target = ''
        if self.threshold and self.test_name in self.threshold:
            self.reference_target = self.threshold[self.test_name]

        self.record_data({
            'Test Name': self.test_name,
            'sponge_properties': {
                self.CUSTOM_PROP_KEY_SYSTEM_POWER: self.system_power,
                self.CUSTOM_PROP_KEY_BUILD_ID: build_id,
                self.CUSTOM_PROP_KEY_INCR_BUILD_ID: incr_build_id,
                self.CUSTOM_PROP_KEY_MODEM_BASEBAND: modem_base_band,
                self.CUSTOM_PROP_KEY_BUILD_TYPE: build_type,
                self.CUSTOM_PROP_KEY_MODEM_ODPM_POWER: self.odpm_power,
                self.CUSTOM_PROP_KEY_DEVICE_NAME: device_name,
                self.CUSTOM_PROP_KEY_DEVICE_BUILD_PHASE: device_build_phase,
                self.CUSTOM_PROP_KEY_MODEM_KIBBLE_POWER: self.modem_power,
                self.CUSTOM_PROP_KEY_TEST_NAME: test_name_for_sponge,
                self.CUSTOM_PROP_KEY_MODEM_KIBBLE_WO_PCIE_POWER: modem_kibble_power_wo_pcie,
                self.CUSTOM_PROP_KEY_MODEM_KIBBLE_PCIE_POWER: self.pcie_power,
                self.CUSTOM_PROP_KEY_RFFE_POWER: self.rffe_power,
                self.CUSTOM_PROP_KEY_MMWAVE_POWER: self.mmwave_power,
                self.CUSTOM_PROP_KEY_CURRENT_REFERENCE_TARGET: self.reference_target
            },
        })

    def threshold_check(self):
        """Check the test result and decide if it passed or failed.

        The threshold is provided in the config file. In this class, result is
        current in mA.
        """

        if not self.threshold or self.test_name not in self.threshold:
            self.log.error("No threshold is provided for the test '{}' in "
                           "the configuration file.".format(self.test_name))
            return

        if not hasattr(self, 'bitses'):
            self.log.error("No bitses attribute found, threshold cannot be"
                           "checked against system power.")
            return

        average_current = self.modem_power
        if ('modem_rail' in self.threshold.keys() and self.threshold['modem_rail'] == self.MODEM_POWER_RAIL_WO_PCIE_NAME):
            average_current = average_current - self.pcie_power
        current_threshold = self.threshold[self.test_name]

        acceptable_upper_difference = max(
            self.threshold[self.test_name] * self.pass_fail_tolerance,
            self.kibble_error_range
        )
        self.log.info('acceptable upper difference' + str(acceptable_upper_difference))

        self.unpack_userparams(pass_fail_tolerance_lower_bound=self.THRESHOLD_TOLERANCE_LOWER_BOUND_DEFAULT)
        acceptable_lower_difference = max(
            self.threshold[self.test_name] * self.pass_fail_tolerance_lower_bound,
            self.kibble_error_range)
        self.log.info('acceptable lower diff ' + str(acceptable_lower_difference))

        if average_current:
            asserts.assert_true(
                average_current < current_threshold + acceptable_upper_difference,
                'Measured average current in [{}]: {:.2f}mW, which is '
                'out of the acceptable upper range {:.2f}+{:.2f}mW'.format(
                    self.test_name, average_current, current_threshold,
                    acceptable_upper_difference))

            asserts.assert_true(
                average_current > current_threshold - acceptable_lower_difference,
                'Measured average current in [{}]: {:.2f}mW, which is '
                'out of the acceptable lower range {:.2f}-{:.2f}mW'.format(
                    self.test_name, average_current, current_threshold,
                    acceptable_lower_difference))

            asserts.explicit_pass(
                'Measured average current in [{}]: {:.2f}mW, which is '
                'within the acceptable range of {:.2f}-{:.2f} and {:.2f}+{:.2f}'.format(
                    self.test_name, average_current, current_threshold,
                    acceptable_lower_difference, current_threshold, acceptable_upper_difference))
        else:
            asserts.fail(
                'Something happened, measurement is not complete, test failed')

    def _get_device_network(self) -> str:
        """Get active network on device.

        Returns:
        Information of active network in string.
        """
        return self.dut.adb.shell(
            self._ADB_GET_ACTIVE_NETWORK)

    def teardown_test(self):
        if self.collect_log_only:
            try:
                # stop log
                modem_logs.stop_modem_logging(self.cellular_dut.ad)
                # pull log
                modem_logs.pull_logs(self.cellular_dut.ad, self.modem_log_path)
            finally:
                # clear log
                modem_logs.clear_modem_logging(self.cellular_dut.ad)
        else:
            if self.is_mdstest_supported:
                try:
                    self.collect_modem_log(self.modem_log_path, self.post_test_log_duration)
                except RuntimeError:
                    self.log.warning('Fail to collect log before test end.')
        self.log.info('===>Before test end info.<====')
        cells_status = self.cellular_simulator.get_all_cell_status()
        self.log.info('UXM cell status: %s', cells_status)
        active_network = self._get_device_network()
        self.log.info('Device network: %s', active_network)
        super().teardown_test()
        # restore device to ready state for next test
        if not self.is_wifi_only_device:
            self.log.info('Enable mobile data.')
            self.cellular_dut.ad.adb.shell('svc data enable')
        self.cellular_simulator.detach()
        self.cellular_dut.toggle_airplane_mode(True)

        if self.is_mdstest_supported:
            self.at_util.disable_dsp()
            self.log.info('Band lock info: \n%s', self.at_util.get_band_lock_info())
            self.log.info('Sim slot map: \n%s', self.at_util.get_sim_slot_mapping())
            self.log.info('DSP status: \n%s', self.at_util.get_dsp_status)

        # processing result
        self.sponge_upload()
