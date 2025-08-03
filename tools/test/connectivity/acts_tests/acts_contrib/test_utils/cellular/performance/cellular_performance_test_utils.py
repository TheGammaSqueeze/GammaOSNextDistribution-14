#!/usr/bin/env python3.4
#
#   Copyright 2022 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the 'License');
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an 'AS IS' BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

import collections
import logging
import os
import re
import time
from queue import Empty
from acts.controllers.android_lib.tel import tel_utils

PCC_PRESET_MAPPING = {
    'N257': {
        'low': 2054999,
        'mid': 2079165,
        'high': 2090832
    },
    'N258': {
        'low': 2017499,
        'mid': 2043749,
        'high': 2057499
    },
    'N260': {
        'low': 2229999,
        'mid': 2254165,
        'high': 2265832
    },
    'N261': {
        'low': 2071667
    }
}

DUPLEX_MODE_TO_BAND_MAPPING = {
    'LTE': {
        'FDD': [
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 17, 18, 19, 20, 21,
            22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 65, 66, 67, 68, 69, 70,
            71, 72, 73, 74, 75, 76, 85, 252, 255
        ],
        'TDD': [
            33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 45, 46, 47, 48,
            50, 51, 53
        ]
    },
    'NR5G': {
        'FDD': [
            'N1', 'N2', 'N3', 'N5', 'N7', 'N8', 'N12', 'N13', 'N14', 'N18',
            'N20', 'N25', 'N26', 'N28', 'N30', 'N65', 'N66', 'N70', 'N71',
            'N74'
        ],
        'TDD': [
            'N34', 'N38', 'N39', 'N40', 'N41', 'N48', 'N50', 'N51', 'N53',
            'N77', 'N78', 'N79', 'N90', 'N257', 'N258', 'N259', 'N260', 'N261'
        ]
    },
}

LONG_SLEEP = 10

def extract_test_id(testcase_params, id_fields):
    test_id = collections.OrderedDict(
        (param, testcase_params[param]) for param in id_fields)
    return test_id


def start_pixel_logger(ad):
    """Function to start pixel logger with default log mask.

    Args:
        ad: android device on which to start logger
    """

    try:
        ad.adb.shell(
            'rm -R /storage/emulated/0/Android/data/com.android.pixellogger/files/logs/logs/'
        )
    except:
        pass
    ad.adb.shell(
        'am startservice -a com.android.pixellogger.service.logging.LoggingService.ACTION_START_LOGGING'
    )


def stop_pixel_logger(ad, log_path, tag=None):
    """Function to stop pixel logger and retrieve logs

    Args:
        ad: android device on which to start logger
        log_path: location of saved logs
    """
    ad.adb.shell(
        'am startservice -a com.android.pixellogger.service.logging.LoggingService.ACTION_STOP_LOGGING'
    )
    logging.info('Waiting for Pixel log file')
    file_name = None
    file_size = 0
    previous_file_size = 0
    for idx in range(600):
        try:
            file = ad.adb.shell(
                'ls -l /storage/emulated/0/Android/data/com.android.pixellogger/files/logs/logs/'
            ).split(' ')
            file_name = file[-1]
            file_size = file[-4]
        except:
            file_name = None
            file_size = 0
        if file_name and file_size == previous_file_size:
            logging.info('Log file found after {}s.'.format(idx))
            break
        else:
            previous_file_size = file_size
            time.sleep(1)
    try:
        local_file_name = '{}_{}'.format(file_name, tag) if tag else file_name
        local_path = os.path.join(log_path, local_file_name)
        ad.pull_files(
            '/storage/emulated/0/Android/data/com.android.pixellogger/files/logs/logs/{}'
            .format(file_name), log_path)
        return local_path
    except:
        logging.error('Could not pull pixel logs.')


def log_system_power_metrics(ad, verbose=1):
    # Log temperature sensors
    if verbose:
        temp_sensors = ad.adb.shell(
            'ls -1 /dev/thermal/tz-by-name/').splitlines()
    else:
        temp_sensors = ['BIG', 'battery', 'quiet_therm', 'usb_pwr_therm']
    temp_measurements = collections.OrderedDict()
    for sensor in temp_sensors:
        try:
            temp_measurements[sensor] = ad.adb.shell(
                'cat /dev/thermal/tz-by-name/{}/temp'.format(sensor))
        except:
            temp_measurements[sensor] = float('nan')
    logging.debug('Temperature sensor readings: {}'.format(temp_measurements))

    # Log mitigation items
    if verbose:
        mitigation_points = [
            "batoilo",
            "ocp_cpu1",
            "ocp_cpu2",
            "ocp_gpu",
            "ocp_tpu",
            "smpl_warn",
            "soft_ocp_cpu1",
            "soft_ocp_cpu2",
            "soft_ocp_gpu",
            "soft_ocp_tpu",
            "vdroop1",
            "vdroop2",
        ]
    else:
        mitigation_points = [
            "batoilo",
            "smpl_warn",
            "vdroop1",
            "vdroop2",
        ]

    parameters_f = ['count', 'capacity', 'timestamp', 'voltage']
    parameters_v = ['count', 'cap', 'time', 'volt']
    mitigation_measurements = collections.OrderedDict()
    for mp in mitigation_points:
        mitigation_measurements[mp] = collections.OrderedDict()
        for par_f, par_v in zip(parameters_f, parameters_v):
            mitigation_measurements[mp][par_v] = ad.adb.shell(
                'cat /sys/devices/virtual/pmic/mitigation/last_triggered_{}/{}_{}'
                .format(par_f, mp, par_v))
    logging.debug('Mitigation readings: {}'.format(mitigation_measurements))

    # Log power meter items
    power_meter_measurements = collections.OrderedDict()
    for device in ['device0', 'device1']:
        power_str = ad.adb.shell(
            'cat /sys/bus/iio/devices/iio:{}/lpf_power'.format(
                device)).splitlines()
        power_meter_measurements[device] = collections.OrderedDict()
        for line in power_str:
            if line.startswith('CH'):
                try:
                    line_split = line.split(', ')
                    power_meter_measurements[device][line_split[0]] = int(
                        line_split[1])
                except (IndexError, ValueError):
                    continue
            elif line.startswith('t='):
                try:
                    power_meter_measurements[device]['t_pmeter'] = int(
                        line[2:])
                except (IndexError, ValueError):
                    continue
            else:
                continue
        logging.debug(
            'Power Meter readings: {}'.format(power_meter_measurements))

        # Log battery items
        if verbose:
            battery_parameters = [
                "act_impedance", "capacity", "charge_counter", "charge_full",
                "charge_full_design", "current_avg", "current_now",
                "cycle_count", "health", "offmode_charger", "present",
                "rc_switch_enable", "resistance", "status", "temp",
                "voltage_avg", "voltage_now", "voltage_ocv"
            ]
        else:
            battery_parameters = [
                "capacity", "current_avg", "current_now", "voltage_avg",
                "voltage_now", "voltage_ocv"
            ]

        battery_meaurements = collections.OrderedDict()
        for par in battery_parameters:
            battery_meaurements['bat_{}'.format(par)] = ad.adb.shell(
                'cat /sys/class/power_supply/maxfg/{}'.format(par))
        logging.debug('Battery readings: {}'.format(battery_meaurements))


def send_at_command(ad, at_command):
    at_cmd_output = ad.adb.shell('am instrument -w -e request {} -e response wait '
                                 '"com.google.mdstest/com.google.mdstest.instrument.ModemATCommandInstrumentation"'.format(at_command))
    return at_cmd_output

def get_rx_measurements(ad, cell_type):
    cell_type_int = 7 if cell_type == 'LTE' else 8
    rx_meas = send_at_command(ad, 'AT+GOOGGETRXMEAS\={}?'.format(cell_type_int))
    rsrp_regex = r"RSRP\[\d+\]\s+(-?\d+)"
    rsrp_values = [float(x) for x in re.findall(rsrp_regex, rx_meas)]
    rsrq_regex = r"RSRQ\[\d+\]\s+(-?\d+)"
    rsrq_values = [float(x) for x in re.findall(rsrq_regex, rx_meas)]
    rssi_regex = r"RSSI\[\d+\]\s+(-?\d+)"
    rssi_values = [float(x) for x in re.findall(rssi_regex, rx_meas)]
    sinr_regex = r"SINR\[\d+\]\s+(-?\d+)"
    sinr_values = [float(x) for x in re.findall(sinr_regex, rx_meas)]
    return {'rsrp': rsrp_values, 'rsrq': rsrq_values, 'rssi': rssi_values, 'sinr': sinr_values}

def toggle_airplane_mode(log, ad, new_state=None, strict_checking=True, try_index=0):
    """ Toggle the state of airplane mode.

    Args:
        log: log handler.
        ad: android_device object.
        new_state: Airplane mode state to set to.
            If None, opposite of the current state.
        strict_checking: Whether to turn on strict checking that checks all features.
        try_index: index of apm toggle

    Returns:
        result: True if operation succeed. False if error happens.
    """
    if try_index % 2 == 0:
        log.info('Toggling airplane mode {} by adb.'.format(new_state))
        return tel_utils.toggle_airplane_mode_by_adb(log, ad, new_state)
    else:
        log.info('Toggling airplane mode {} by msim.'.format(new_state))
        return toggle_airplane_mode_msim(
            log, ad, new_state, strict_checking=strict_checking)

def toggle_airplane_mode_msim(log, ad, new_state=None, strict_checking=True):
    """ Toggle the state of airplane mode.

    Args:
        log: log handler.
        ad: android_device object.
        new_state: Airplane mode state to set to.
            If None, opposite of the current state.
        strict_checking: Whether to turn on strict checking that checks all features.

    Returns:
        result: True if operation succeed. False if error happens.
    """

    cur_state = ad.droid.connectivityCheckAirplaneMode()
    if cur_state == new_state:
        ad.log.info("Airplane mode already in %s", new_state)
        return True
    elif new_state is None:
        new_state = not cur_state
        ad.log.info("Toggle APM mode, from current tate %s to %s", cur_state,
                    new_state)
    sub_id_list = []
    active_sub_info = ad.droid.subscriptionGetAllSubInfoList()
    if active_sub_info:
        for info in active_sub_info:
            sub_id_list.append(info['subscriptionId'])

    ad.ed.clear_all_events()
    time.sleep(0.1)
    service_state_list = []
    if new_state:
        service_state_list.append(tel_utils.SERVICE_STATE_POWER_OFF)
        ad.log.info("Turn on airplane mode")

    else:
        # If either one of these 3 events show up, it should be OK.
        # Normal SIM, phone in service
        service_state_list.append(tel_utils.SERVICE_STATE_IN_SERVICE)
        # NO SIM, or Dead SIM, or no Roaming coverage.
        service_state_list.append(tel_utils.SERVICE_STATE_OUT_OF_SERVICE)
        service_state_list.append(tel_utils.SERVICE_STATE_EMERGENCY_ONLY)
        ad.log.info("Turn off airplane mode")

    for sub_id in sub_id_list:
        ad.droid.telephonyStartTrackingServiceStateChangeForSubscription(
            sub_id)

    timeout_time = time.time() + LONG_SLEEP
    ad.droid.connectivityToggleAirplaneMode(new_state)

    try:
        try:
            event = ad.ed.wait_for_event(
                tel_utils.EVENT_SERVICE_STATE_CHANGED,
                tel_utils.is_event_match_for_list,
                timeout= LONG_SLEEP,
                field=tel_utils.ServiceStateContainer.SERVICE_STATE,
                value_list=service_state_list)
            ad.log.info("Got event %s", event)
        except Empty:
            ad.log.warning("Did not get expected service state change to %s",
                           service_state_list)
        finally:
            for sub_id in sub_id_list:
                ad.droid.telephonyStopTrackingServiceStateChangeForSubscription(
                    sub_id)
    except Exception as e:
        ad.log.error(e)

    # APM on (new_state=True) will turn off bluetooth but may not turn it on
    try:
        if new_state and not tel_utils._wait_for_bluetooth_in_state(
                log, ad, False, timeout_time - time.time()):
            ad.log.error(
                "Failed waiting for bluetooth during airplane mode toggle")
            if strict_checking: return False
    except Exception as e:
        ad.log.error("Failed to check bluetooth state due to %s", e)
        if strict_checking:
            raise

    # APM on (new_state=True) will turn off wifi but may not turn it on
    if new_state and not tel_utils._wait_for_wifi_in_state(log, ad, False,
                                                 timeout_time - time.time()):
        ad.log.error("Failed waiting for wifi during airplane mode toggle on")
        if strict_checking: return False

    if ad.droid.connectivityCheckAirplaneMode() != new_state:
        ad.log.error("Set airplane mode to %s failed", new_state)
        return False
    return True

def generate_endc_combo_config_from_string(endc_combo_str):
    """Function to generate ENDC combo config from combo string

    Args:
        endc_combo_str: ENDC combo descriptor (e.g. B48A[4];A[1]+N5A[2];A[1])
    Returns:
        endc_combo_config: dictionary with all ENDC combo settings
    """
    endc_combo_config = collections.OrderedDict()
    endc_combo_config['endc_combo_name']=endc_combo_str
    endc_combo_str = endc_combo_str.replace(' ', '')
    endc_combo_list = endc_combo_str.split('+')
    cell_config_list = list()
    lte_cell_count = 0
    nr_cell_count = 0
    lte_scc_list = []
    nr_dl_carriers = []
    nr_ul_carriers = []
    lte_dl_carriers = []
    lte_ul_carriers = []

    cell_config_regex = re.compile(
        r'(?P<cell_type>[B,N])(?P<band>[0-9]+)(?P<bandwidth_class>[A-Z])\[bw=(?P<dl_bandwidth>[0-9]+)\]'
        r'(\[ch=)?(?P<channel>[0-9]+)?\]?\[ant=(?P<dl_mimo_config>[0-9]+),?(?P<transmission_mode>[TM0-9]+)?\];?'
        r'(?P<ul_bandwidth_class>[A-Z])?(\[ant=)?(?P<ul_mimo_config>[0-9])?(\])?'
    )
    for cell_string in endc_combo_list:
        cell_config = re.match(cell_config_regex, cell_string).groupdict()
        if cell_config['cell_type'] == 'B':
            # Configure LTE specific parameters
            cell_config['cell_type'] = 'LTE'
            lte_cell_count = lte_cell_count + 1
            cell_config['cell_number'] = lte_cell_count
            if cell_config['cell_number'] == 1:
                cell_config['pcc'] = 1
                endc_combo_config['lte_pcc'] = cell_config['cell_number']
            else:
                cell_config['pcc'] = 0
                lte_scc_list.append(cell_config['cell_number'])
            cell_config['duplex_mode'] = 'FDD' if int(
                cell_config['band']
            ) in DUPLEX_MODE_TO_BAND_MAPPING['LTE'][
                'FDD'] else 'TDD'
            cell_config['dl_mimo_config'] = 'D{nss}U{nss}'.format(
                nss=cell_config['dl_mimo_config'])
            lte_dl_carriers.append(cell_config['cell_number'])
        else:
            # Configure NR specific parameters
            cell_config['cell_type'] = 'NR5G'
            nr_cell_count = nr_cell_count + 1
            cell_config['cell_number'] = nr_cell_count
            nr_dl_carriers.append(cell_config['cell_number'])
            #TODO: fix NSA/SA indicator
            cell_config['nr_cell_type'] = 'NSA'
            cell_config['band'] = 'N' + cell_config['band']
            cell_config['duplex_mode'] = 'FDD' if cell_config[
                'band'] in DUPLEX_MODE_TO_BAND_MAPPING['NR5G'][
                    'FDD'] else 'TDD'
            cell_config['subcarrier_spacing'] = 'MU0' if cell_config[
                'duplex_mode'] == 'FDD' else 'MU1'
            cell_config['dl_mimo_config'] = 'N{nss}X{nss}'.format(
                nss=cell_config['dl_mimo_config'])

        cell_config['dl_bandwidth_class'] = cell_config['bandwidth_class']
        cell_config['dl_bandwidth'] = 'BW'+ cell_config['dl_bandwidth']
        cell_config['ul_enabled'] = 1 if cell_config['ul_bandwidth_class'] else 0
        if cell_config['ul_enabled']:
            cell_config['ul_mimo_config'] = 'N{nss}X{nss}'.format(
                nss=cell_config['ul_mimo_config'])
            if cell_config['cell_type'] == 'LTE':
                lte_ul_carriers.append(cell_config['cell_number'])
            elif cell_config['cell_type'] == 'NR5G':
                nr_ul_carriers.append(cell_config['cell_number'])
        cell_config_list.append(cell_config)
    endc_combo_config['lte_cell_count'] = lte_cell_count
    endc_combo_config['nr_cell_count'] = nr_cell_count
    endc_combo_config['nr_dl_carriers'] = nr_dl_carriers
    endc_combo_config['nr_ul_carriers'] = nr_ul_carriers
    endc_combo_config['cell_list'] = cell_config_list
    endc_combo_config['lte_scc_list'] = lte_scc_list
    endc_combo_config['lte_dl_carriers'] = lte_dl_carriers
    endc_combo_config['lte_ul_carriers'] = lte_ul_carriers
    return endc_combo_config

def generate_endc_combo_config_from_csv_row(test_config):
    """Function to generate ENDC combo config from CSV test config

    Args:
        test_config: dict containing ENDC combo config from CSV
    Returns:
        endc_combo_config: dictionary with all ENDC combo settings
    """
    endc_combo_config = collections.OrderedDict()
    lte_cell_count = 0
    nr_cell_count = 0
    lte_scc_list = []
    nr_dl_carriers = []
    nr_ul_carriers = []
    lte_dl_carriers = []
    lte_ul_carriers = []

    cell_config_list = []
    if test_config['lte_band']:
        lte_cell = {
            'cell_type':
            'LTE',
            'cell_number':
            1,
            'pcc':
            1,
            'band':
            test_config['lte_band'],
            'dl_bandwidth':
            test_config['lte_bandwidth'],
            'ul_enabled':
            1,
            'duplex_mode':
            test_config['lte_duplex_mode'],
            'dl_mimo_config':
            'D{nss}U{nss}'.format(nss=test_config['lte_dl_mimo_config']),
            'ul_mimo_config':
            'D{nss}U{nss}'.format(nss=test_config['lte_ul_mimo_config'])
        }
        if int(test_config['lte_dl_mimo_config']) == 1:
            lte_cell['transmission_mode'] = 'TM1'
        elif int(test_config['lte_dl_mimo_config']) == 2:
            lte_cell['transmission_mode'] = 'TM2'
        else:
            lte_cell['transmission_mode'] = 'TM3'
        cell_config_list.append(lte_cell)
        endc_combo_config['lte_pcc'] = 1
        lte_cell_count = 1
        lte_dl_carriers = [1]
        lte_ul_carriers = [1]

    if test_config['nr_band']:
        nr_cell = {
            'cell_type':
            'NR5G',
            'cell_number':
            1,
            'band':
            test_config['nr_band'],
            'nr_cell_type': test_config['nr_cell_type'],
            'duplex_mode':
            test_config['nr_duplex_mode'],
            'dl_mimo_config':
            'N{nss}X{nss}'.format(nss=test_config['nr_dl_mimo_config']),
            'dl_bandwidth_class':
            'A',
            'dl_bandwidth':
            test_config['nr_bandwidth'],
            'ul_enabled':
            1,
            'ul_bandwidth_class':
            'A',
            'ul_mimo_config':
            'N{nss}X{nss}'.format(nss=test_config['nr_ul_mimo_config']),
            'subcarrier_spacing':
            'MU0' if test_config['nr_scs'] == '15' else 'MU1'
        }
        cell_config_list.append(nr_cell)
        nr_cell_count = 1
        nr_dl_carriers = [1]
        nr_ul_carriers = [1]

    endc_combo_config['lte_cell_count'] = lte_cell_count
    endc_combo_config['nr_cell_count'] = nr_cell_count
    endc_combo_config['nr_dl_carriers'] = nr_dl_carriers
    endc_combo_config['nr_ul_carriers'] = nr_ul_carriers
    endc_combo_config['cell_list'] = cell_config_list
    endc_combo_config['lte_scc_list'] = lte_scc_list
    endc_combo_config['lte_dl_carriers'] = lte_dl_carriers
    endc_combo_config['lte_ul_carriers'] = lte_ul_carriers
    return endc_combo_config
