#!/usr/bin/env python3
#
#   Copyright 2020 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.
import os
from acts_contrib.test_utils.gnss.GnssBlankingBase import GnssBlankingBase
from collections import namedtuple
from acts_contrib.test_utils.gnss.LabTtffTestBase import LabTtffTestBase
from acts_contrib.test_utils.gnss.gnss_test_utils import detect_crash_during_tracking, gnss_tracking_via_gtw_gpstool, \
                                    start_gnss_by_gtw_gpstool, process_ttff_by_gtw_gpstool, calculate_position_error
from acts.context import get_current_context
from acts.utils import get_current_epoch_time
from time import sleep
import csv
import matplotlib.pyplot as plt
from mpl_toolkits.mplot3d.axes3d import Axes3D
import statistics


class LabGnssPowerSweepTest(GnssBlankingBase):

    def gnss_plot_2D_result(self, position_error):
        """Plot 2D position error result
        """
        x_axis = []
        y_axis = []
        z_axis = []
        for key in position_error:
            tmp = key.split('_')
            l1_pwr = float(tmp[1])
            l5_pwr = float(tmp[3])
            position_error_value = position_error[key]
            x_axis.append(l1_pwr)
            y_axis.append(l5_pwr)
            z_axis.append(position_error_value)

        fig = plt.figure(figsize=(12, 7))
        ax = plt.axes(projection='3d')
        ax.scatter(x_axis, y_axis, z_axis)
        plt.title("Z axis Position Error", fontsize=12)
        plt.xlabel("L1 PWR (dBm)", fontsize=12)
        plt.ylabel("L5 PWR (dBm)", fontsize=12)
        plt.show()
        path_name = os.path.join(self.gnss_log_path, 'result.png')
        plt.savefig(path_name)

    def gnss_wait_for_ephemeris_download(self):
        """Launch GTW GPSTool and Clear all GNSS aiding data
           Start GNSS tracking on GTW_GPSTool.
           Wait for "first_wait" at simulator power = "power_level" to download Ephemeris
        """
        first_wait = self.user_params.get('first_wait', 300)
        LabTtffTestBase.start_set_gnss_power(self)
        self.start_gnss_and_wait(first_wait)

    def gnss_check_fix(self, json_tag):
        """Launch GTW GPSTool and check position fix or not
          Returns:
            True : Can fix within 120 sec
            False
        """
        # Check Latitude for fix
        self.dut.log.info("Restart GTW GPSTool in gnss_check_fix")
        start_gnss_by_gtw_gpstool(self.dut, state=True)
        begin_time = get_current_epoch_time()
        if not self.dut.is_adb_logcat_on:
            self.dut.start_adb_logcat()
        while True:
            if get_current_epoch_time() - begin_time >= 120000:
                self.dut.log.info("Location fix timeout in gnss_check_fix")
                start_gnss_by_gtw_gpstool(self.dut, state=False)
                json_tag = json_tag + '_gnss_check_fix_timeout'
                self.dut.cat_adb_log(tag=json_tag,
                                     begin_time=begin_time,
                                     end_time=None,
                                     dest_path=self.gnss_log_path)
                return False
            sleep(1)
            logcat_results = self.dut.search_logcat("Latitude", begin_time)
            if logcat_results:
                self.dut.log.info("Location fix successfully in gnss_check_fix")
                json_tag = json_tag + '_gnss_check_fix_success'
                self.dut.cat_adb_log(tag=json_tag,
                                     begin_time=begin_time,
                                     end_time=None,
                                     dest_path=self.gnss_log_path)
                return True

    def gnss_check_l5_engaging(self, json_tag):
        """check L5 engaging
          Returns:
            True : L5 engaged
            False
        """
        # Check L5 engaging rate
        begin_time = get_current_epoch_time()
        if not self.dut.is_adb_logcat_on:
            self.dut.start_adb_logcat()
        while True:
            if get_current_epoch_time() - begin_time >= 120000:
                self.dut.log.info(
                    "L5 engaging timeout in gnss_check_l5_engaging")
                start_gnss_by_gtw_gpstool(self.dut, state=False)
                json_tag = json_tag + '_gnss_check_l5_engaging_timeout'
                self.dut.cat_adb_log(tag=json_tag,
                                     begin_time=begin_time,
                                     end_time=None,
                                     dest_path=self.gnss_log_path)
                return False
            sleep(1)
            logcat_results = self.dut.search_logcat("L5 engaging rate:",
                                                    begin_time)
            if logcat_results:
                start_idx = logcat_results[-1]['log_message'].find(
                    "L5 engaging rate:")
                tmp = logcat_results[-1]['log_message'][(start_idx + 18):]
                l5_engaging_rate = float(tmp.strip('%'))

                if l5_engaging_rate != 0:
                    self.dut.log.info("L5 engaged")
                    json_tag = json_tag + '_gnss_check_l5_engaging_success'
                    self.dut.cat_adb_log(tag=json_tag,
                                         begin_time=begin_time,
                                         end_time=None,
                                         dest_path=self.gnss_log_path)
                    return True

    def gnss_check_position_error(self, json_tag):
        """check position error
          Returns:
            position error average value
        """
        average_position_error_count = 60
        position_error_all = []
        hacc_all = []
        default_position_error_mean = 6666
        default_position_error_std = 6666
        default_hacc_mean = 6666
        default_hacc_std = 6666
        idx = 0
        begin_time = get_current_epoch_time()
        if not self.dut.is_adb_logcat_on:
            self.dut.start_adb_logcat()
        while True:
            if get_current_epoch_time() - begin_time >= 120000:
                self.dut.log.info(
                    "Position error calculation timeout in gnss_check_position_error"
                )
                start_gnss_by_gtw_gpstool(self.dut, state=False)
                json_tag = json_tag + '_gnss_check_position_error_timeout'
                self.dut.cat_adb_log(tag=json_tag,
                                     begin_time=begin_time,
                                     end_time=None,
                                     dest_path=self.gnss_log_path)
                return default_position_error_mean, default_position_error_std, default_hacc_mean, default_hacc_std
            sleep(1)
            gnss_results = self.dut.search_logcat("GPSService: Check item",
                                                  begin_time)
            if gnss_results:
                self.dut.log.info(gnss_results[-1]["log_message"])
                gnss_location_log = \
                    gnss_results[-1]["log_message"].split()
                ttff_lat = float(gnss_location_log[8].split("=")[-1].strip(","))
                ttff_lon = float(gnss_location_log[9].split("=")[-1].strip(","))
                loc_time = int(gnss_location_log[10].split("=")[-1].strip(","))
                ttff_haccu = float(
                    gnss_location_log[11].split("=")[-1].strip(","))
                hacc_all.append(ttff_haccu)
                position_error = calculate_position_error(
                    ttff_lat, ttff_lon, self.simulator_location)
                position_error_all.append(abs(position_error))
                idx = idx + 1
                if idx >= average_position_error_count:
                    position_error_mean = statistics.mean(position_error_all)
                    position_error_std = statistics.stdev(position_error_all)
                    hacc_mean = statistics.mean(hacc_all)
                    hacc_std = statistics.stdev(hacc_all)
                    json_tag = json_tag + '_gnss_check_position_error_success'
                    self.dut.cat_adb_log(tag=json_tag,
                                         begin_time=begin_time,
                                         end_time=None,
                                         dest_path=self.gnss_log_path)
                    return position_error_mean, position_error_std, hacc_mean, hacc_std

    def gnss_tracking_L5_position_error_capture(self, json_tag):
        """Capture position error after L5 engaged
        Args:
        Returns:
            Position error with L5
        """
        self.dut.log.info('Start gnss_tracking_L5_position_error_capture')
        fixed = self.gnss_check_fix(json_tag)
        if fixed:
            l5_engaged = self.gnss_check_l5_engaging(json_tag)
            if l5_engaged:
                position_error_mean, position_error_std, hacc_mean, hacc_std = self.gnss_check_position_error(
                    json_tag)
                start_gnss_by_gtw_gpstool(self.dut, state=False)
            else:
                position_error_mean = 8888
                position_error_std = 8888
                hacc_mean = 8888
                hacc_std = 8888
        else:
            position_error_mean = 9999
            position_error_std = 9999
            hacc_mean = 9999
            hacc_std = 9999
            self.position_fix_timeout_cnt = self.position_fix_timeout_cnt + 1

            if self.position_fix_timeout_cnt > (self.l1_sweep_cnt / 2):
                self.l1_sensitivity_point = self.current_l1_pwr

        return position_error_mean, position_error_std, hacc_mean, hacc_std

    def gnss_power_tracking_loop(self):
        """Launch GTW GPSTool and Clear all GNSS aiding data
           Start GNSS tracking on GTW_GPSTool.

        Args:

        Returns:
            True: First fix TTFF are within criteria.
            False: First fix TTFF exceed criteria.
        """
        test_period = 60
        type = 'gnss'
        start_time = get_current_epoch_time()
        start_gnss_by_gtw_gpstool(self.dut, state=True, type=type)
        while get_current_epoch_time() - start_time < test_period * 1000:
            detect_crash_during_tracking(self.dut, start_time, type)
        stop_time = get_current_epoch_time()

        return start_time, stop_time

    def parse_tracking_log_cat(self, log_dir):
        self.log.warning(f'Parsing log cat {log_dir} results into dataframe!')

    def check_l5_points(self, gnss_pwr_swp):
        cnt = 0
        for kk in range(len(gnss_pwr_swp[1])):
            if gnss_pwr_swp[1][kk][0] == gnss_pwr_swp[1][kk + 1][0]:
                cnt = cnt + 1
            else:
                return cnt

    def test_tracking_power_sweep(self):
        # Create log file path
        full_output_path = get_current_context().get_full_output_path()
        self.gnss_log_path = os.path.join(full_output_path, '')
        os.makedirs(self.gnss_log_path, exist_ok=True)
        self.log.debug(f'Create log path: {self.gnss_log_path}')
        csv_path = self.gnss_log_path + 'L1_L5_2D_search_result.csv'
        csvfile = open(csv_path, 'w')
        writer = csv.writer(csvfile)
        writer.writerow([
            "csv_result_tag", "position_error_mean", "position_error_std",
            "hacc_mean", "hacc_std"
        ])
        # for L1 position fix early termination
        self.l1_sensitivity_point = -999
        self.enable_early_terminate = 1
        self.position_fix_timeout_cnt = 0
        self.current_l1_pwr = 0
        self.l1_sweep_cnt = 0

        self.gnss_wait_for_ephemeris_download()
        l1_cable_loss = self.gnss_sim_params.get('L1_cable_loss')
        l5_cable_loss = self.gnss_sim_params.get('L5_cable_loss')

        for i, gnss_pwr_swp in enumerate(self.gnss_pwr_sweep_fine_sweep_ls):
            self.log.info(f'Start fine GNSS power level sweep part {i + 1}')
            self.l1_sweep_cnt = self.check_l5_points(gnss_pwr_swp)
            for gnss_pwr_params in gnss_pwr_swp[1]:
                json_tag = f'test_'
                csv_result_tag = ''
                for ii, pwr in enumerate(
                        gnss_pwr_params):  # Setup L1 and L5 power
                    sat_sys = gnss_pwr_swp[0][ii].get('sat').upper()
                    band = gnss_pwr_swp[0][ii].get('band').upper()
                    if band == "L1":
                        pwr_biased = pwr + l1_cable_loss
                        if pwr != self.current_l1_pwr:
                            self.position_fix_timeout_cnt = 0
                            self.current_l1_pwr = pwr
                    elif band == "L5":
                        pwr_biased = pwr + l5_cable_loss
                    else:
                        pwr_biased = pwr
                    # Set GNSS Simulator power level
                    self.gnss_simulator.ping_inst()
                    self.gnss_simulator.set_scenario_power(
                        power_level=pwr_biased,
                        sat_system=sat_sys,
                        freq_band=band)
                    self.log.info(f'Set {sat_sys} {band} with power {pwr}')
                    json_tag = json_tag + f'{sat_sys}_{band}_{pwr}'
                    csv_result_tag = csv_result_tag + f'{band}_{pwr}_'

                if self.current_l1_pwr < self.l1_sensitivity_point and self.enable_early_terminate == 1:
                    position_error_mean = -1
                    position_error_std = -1
                    hacc_mean = -1
                    hacc_std = -1
                else:
                    position_error_mean, position_error_std, hacc_mean, hacc_std = self.gnss_tracking_L5_position_error_capture(
                        json_tag)
                writer = csv.writer(csvfile)
                writer.writerow([
                    csv_result_tag, position_error_mean, position_error_std,
                    hacc_mean, hacc_std
                ])
        csvfile.close()
