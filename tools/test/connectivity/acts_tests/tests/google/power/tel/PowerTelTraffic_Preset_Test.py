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
import os
import time

from acts import asserts
import acts_contrib.test_utils.power.cellular.cellular_power_preset_base_test as PB

class PowerTelTrafficPresetTest(PB.PowerCellularPresetLabBaseTest):
    # command to enable mobile data
    ADB_CMD_ENABLE_MOBILE_DATA = 'svc data enable'

    # command to start iperf server on UE
    START_IPERF_SV_UE_CMD = 'nohup > /dev/null 2>&1 sh -c "iperf3 -s -i1 -p5201 > /dev/null  &"'

    # command to start iperf server on UE
    # (require: 1.path to iperf exe 2.hostname/hostIP)
    START_IPERF_CLIENT_UE_CMD = 'nohup > /dev/null 2>&1 sh -c "iperf3 -c {iperf_host_ip} -i1 -p5202 -w8m -t2000 -O{second} > /dev/null &"'

    # command to start iperf server on host()
    START_IPERF_SV_HOST_CMD = '{exe_path}\\iperf3 -s -p5202'

    # command to start iperf client on host
    # (require: 1.path to iperf exe 2.UE IP)
    START_IPERF_CLIENT_HOST_CMD = (
        '{exe_path}\\iperf3 -c {ue_ip} -w16M -t1000 -p5201 -O{second}')

    START_IPERF_CLIENT_HOST_CMD_FR2 = (
        '{exe_path}\\iperf3 -c {ue_ip} -w16M -t1000 -p5201 -O{second}')

    def __init__(self, controllers):
        super().__init__(controllers)
        self.ssh_iperf_client = None
        self.ssh_iperf_server = None
        self.iperf_out_err = {}

    def setup_class(self):
        super().setup_class()

        # Unpack test parameters used in this class
        self.unpack_userparams(iperf_exe_path=None,
                               ue_ip=None,
                               iperf_host_ip=None)

        # Verify required config
        for param in ('iperf_exe_path', 'ue_ip', 'iperf_host_ip'):
            if getattr(self, param) is None:
                raise RuntimeError(
                    f'Parameter "{param}" is required to run this type of test')

    def setup_test(self):
        # Call parent method first to setup simulation
        super().setup_test()

        # get tput configs
        self.unpack_userparams(abnormal_bandwidth_tolerance=0.1,
                               bandwidth_tolerance=0.1,
                               n_second_to_omitted=45)
        self.expected_downlink_bandwidth = float(self.test_configs[self.test_name]['tput']['downlink'].split()[0])
        self.expected_uplink_bandwidth = float(self.test_configs[self.test_name]['tput']['uplink'].split()[0])

        # setup ssh client
        self.ssh_iperf_client = self.cellular_simulator.create_ssh_client()
        self.ssh_iperf_server = self.cellular_simulator.create_ssh_client()

        self.turn_on_mobile_data()

    def power_tel_traffic_test(self):
        """Measure power while data is transferring."""
        # Start data traffic
        self.start_uplink_process()
        self.start_downlink_process()

        # Measure power and check against threshold
        self.collect_power_data_and_validate()

    def _end_iperfs(self):
        err_message = []
        # Write iperf log
        self.ssh_iperf_server.close()
        uplink_log_name = self.test_name + '_uplink.txt'
        out, err = self.iperf_out_err[self.ssh_iperf_server]
        output_content = ''.join(out.readlines())
        err_content = ''.join(err.readlines())
        self._write_iperf_log(uplink_log_name, output_content + err_content)
        if err_content.strip():
            err_message.append(f'Uplink process fail due to error: {err_content}\n')
        else:
            if not self._iperf_log_check(output_content, self.expected_uplink_bandwidth):
                err_message.append('Bandwidth of uplink process is unstable.')

        self.ssh_iperf_client.close()
        downlink_log_name = self.test_name + '_downlink.txt'
        out, err = self.iperf_out_err[self.ssh_iperf_client]
        output_content = ''.join(out.readlines())
        err_content = ''.join(err.readlines())
        self._write_iperf_log(downlink_log_name, output_content + err_content)
        if err_content.strip():
            err_message.append(f'Downlink process fail due to error: {err_content}\n')
        else:
            if not self._iperf_log_check(output_content, self.expected_downlink_bandwidth):
                err_message.append('Bandwidth of downlink process is unstable.')

        if err_message:
            raise RuntimeError('\n'.join(err_message))

    def teardown_test(self):
        try:
            self._end_iperfs()
        except RuntimeError as re:
            raise re
        finally:
            super().teardown_test()

    def _iperf_log_check(self, file, expected_bandwidth):
        """Check iperf log and abnormal bandwidth instances.

        Args:
            file: file object of iperf log to be checked.
            expected_bandwidth: integer value for expected bandwidth.
        Returns:
            True if log is normal, False otherwise.
        """
        # example of record line
        #[  4]   0.00-1.00   sec  20.2 MBytes   169 Mbits/sec
        total_abnormal_entries = 0
        total_record_entries = 0
        bandwidth_val_idx = 6
        record_entry_total_cols = 8
        lines = file.split('\n')
        acceptable_difference = self.bandwidth_tolerance * expected_bandwidth
        self.log.debug('Expected bandwidth: %f', expected_bandwidth)
        self.log.debug('Acceptance difference: %f', acceptable_difference)
        for line in lines:
            cols = line.split()
            self.log.debug(cols)
            if len(cols) == record_entry_total_cols:
                total_record_entries += 1
                bandwidth = float(cols[bandwidth_val_idx])
                self.log.debug('bandwidth: %f', bandwidth)
                if abs(bandwidth - expected_bandwidth) > acceptable_difference:
                    total_abnormal_entries += 1
        if not total_record_entries:
            raise RuntimeError('No tput data record found.')
        self.log.debug('Total abnormal entries: %d - Total record: %d', total_abnormal_entries, total_record_entries)
        return (total_abnormal_entries/total_record_entries) <= self.abnormal_bandwidth_tolerance

    def _exec_ssh_cmd(self, ssh_client, cmd):
        """Execute command on given ssh client.

        Args:
            ssh_client: parmiko ssh client object.
            cmd: command to execute via ssh.
        """
        self.log.info('Sending cmd to ssh host: ' + cmd)
        stdin, stdout, stderr = ssh_client.exec_command(cmd, get_pty=True)
        stdin.close()
        self.iperf_out_err[ssh_client] = (stdout, stderr)

    def start_downlink_process(self):
        """UE transfer data to host."""
        self.log.info('Start downlink process')
        # start UE iperf server
        self.cellular_dut.ad.adb.shell(self.START_IPERF_SV_UE_CMD)
        self.log.info('cmd sent to UE: ' + self.START_IPERF_SV_UE_CMD)
        self.log.info('UE iperf server started')
        time.sleep(5)
        # start host iperf client
        cmd = None
        if 'fr2' in self.test_name:
            cmd = self.START_IPERF_CLIENT_HOST_CMD_FR2.format(
                exe_path=self.iperf_exe_path,
                ue_ip=self.ue_ip,
                second=self.n_second_to_omitted)
        else:
            cmd = self.START_IPERF_CLIENT_HOST_CMD.format(
                exe_path=self.iperf_exe_path,
                ue_ip=self.ue_ip,
                second=self.n_second_to_omitted)

        if not cmd:
            raise RuntimeError('Cannot format command to start iperf client.')
        self._exec_ssh_cmd(self.ssh_iperf_client, cmd)
        self.log.info('Host iperf client started')
        time.sleep(5)

    def start_uplink_process(self):
        """Host transfer data to UE."""
        self.log.info('Start uplink process')
        # start host iperf server
        cmd = self.START_IPERF_SV_HOST_CMD.format(exe_path=self.iperf_exe_path)
        self._exec_ssh_cmd(self.ssh_iperf_server, cmd)
        self.log.info('Host iperf server started')
        time.sleep(5)
        # start UE iperf
        adb_cmd = self.START_IPERF_CLIENT_UE_CMD.format(
            iperf_host_ip=self.iperf_host_ip,
            second=self.n_second_to_omitted)
        self.cellular_dut.ad.adb.shell(adb_cmd)
        self.log.info('cmd sent to UE: ' + adb_cmd)
        self.log.info('UE iperf client started')
        time.sleep(5)

    def _write_iperf_log(self, file_name, content):
        """ Writing ssh stdout and stdin to log file.

        Args:
            file_name: Log file name to write log to.
            content: Content to write to file.
        """
        iperf_log_dir = os.path.join(self.root_output_path, 'iperf')
        os.makedirs(iperf_log_dir, exist_ok=True)
        iperf_log_file_path = os.path.join(iperf_log_dir, file_name)
        with open(iperf_log_file_path, 'w') as f:
            f.write(content)

    def turn_on_mobile_data(self):
        self.dut.adb.shell(self.ADB_CMD_ENABLE_MOBILE_DATA)


class PowerTelTraffic_Preset_Test(PowerTelTrafficPresetTest):
    def test_preset_LTE_traffic(self):
        self.power_tel_traffic_test()

    def test_preset_nsa_traffic_fr1(self):
        self.power_tel_traffic_test()

    def test_preset_sa_traffic_fr1(self):
        self.power_tel_traffic_test()


class PowerTelTrafficFr2_Preset_Test(PowerTelTrafficPresetTest):
    def test_preset_nsa_traffic_fr2(self):
        self.power_tel_traffic_test()
