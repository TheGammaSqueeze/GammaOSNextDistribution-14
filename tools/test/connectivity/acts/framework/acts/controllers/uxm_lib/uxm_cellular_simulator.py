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
import re
import logging
import os
import paramiko
import socket
import time

from acts.controllers.cellular_simulator import AbstractCellularSimulator

class SocketWrapper():
    """A wrapper for socket communicate with test equipment.

    Attributes:
        _socket: a socket object.
        _ip: a string value for ip address
            which we want to connect.
        _port: an integer for port
            which we want to connect.
        _connecting_timeout: an integer for socket connecting timeout.
        _encode_format: a string specify encoding format.
        _cmd_terminator: a character indicates the end of command/data
            which need to be sent.
    """

    def __init__(self, ip, port,
                 connecting_timeout=120,
                 cmd_terminator='\n',
                 encode_format='utf-8',
                 buff_size=1024):
        self._socket = None
        self._ip = ip
        self._port = port
        self._connecting_timeout = connecting_timeout
        self._cmd_terminator = cmd_terminator
        self._encode_format = encode_format
        self._buff_size = buff_size
        self._logger = logging.getLogger(__name__)

    def _connect(self):
        self._socket = socket.create_connection(
            (self._ip, self._port), timeout=self._connecting_timeout
        )

    def send_command(self, cmd: str):
        if not self._socket:
            self._connect()
        if cmd and cmd[-1] != self._cmd_terminator:
            cmd = cmd + self._cmd_terminator
        self._socket.sendall(cmd.encode(self._encode_format))

    def send_command_recv(self, cmd: str) -> str:
        """Send data and wait for response

        Args:
            cmd: a string command to be sent.

        Returns:
            a string response.
        """
        self.send_command(cmd)
        response = ''
        try:
            response = self._socket.recv(self._buff_size).decode(
                self._encode_format
            )
        except socket.timeout as e:
            self._logger.info('Socket timeout while receiving response.')
            self.close()
            raise

        return response

    def close(self):
        self._socket.close()
        self._socket = None

class UXMCellularSimulator(AbstractCellularSimulator):
    """A cellular simulator for UXM callbox."""

    # Keys to obtain data from cell_info dictionary.
    _KEY_CELL_NUMBER = "cell_number"
    _KEY_CELL_TYPE = "cell_type"

    # UXM socket port
    UXM_SOCKET_PORT = 5125

    # UXM SCPI COMMAND
    SCPI_IMPORT_STATUS_QUERY_CMD = 'SYSTem:SCPI:IMPort:STATus?'
    SCPI_SYSTEM_ERROR_CHECK_CMD = 'SYST:ERR?\n'
    SCPI_CHECK_CONNECTION_CMD = '*IDN?\n'
    SCPI_DEREGISTER_UE_IMS = 'SYSTem:IMS:SERVer:UE:DERegister'
    _SCPI_CHANGE_DL_TDOMAIN = 'BSE:CONFig:NR5G:CELL1:SCHeduling:BWP0:FC0:SC0:DL:TDOMain:APOLicy ONMac'
    _SCPI_CHANGE_UL_TDOMAIN = 'BSE:CONFig:NR5G:CELL1:SCHeduling:BWP0:FC0:SC0:UL:NUL:TDOMain:APOLicy ONSRbsr'
    # require: path to SCPI file
    SCPI_IMPORT_SCPI_FILE_CMD = 'SYSTem:SCPI:IMPort "{}"\n'
    # require: 1. cell type (E.g. NR5G), 2. cell number (E.g CELL1)
    SCPI_CELL_ON_CMD = 'BSE:CONFig:{}:{}:ACTive 1'
    SCPI_CELL_OFF_CMD = 'BSE:CONFig:{}:{}:ACTive 0'
    SCPI_GET_CELL_STATUS = 'BSE:STATus:{}:{}?'
    SCPI_RRC_RELEASE_LTE_CMD = 'BSE:FUNCtion:{}:{}:RELease:SEND'
    SCPI_RRC_RELEASE_NR_CMD = 'BSE:CONFig:{}:{}:RCONtrol:RRC:STARt RRELease'
    # require cell number
    SCPI_CREATE_DEDICATED_BEARER = 'BSE:FUNCtion:LTE:{}:NAS:EBID10:DEDicated:CREate'
    SCPI_CHANGE_SIM_NR_CMD = 'BSE:CONFig:NR5G:CELL1:SECurity:AUTHenticate:KEY:TYPE {}'
    SCPI_CHANGE_SIM_LTE_CMD = 'BSE:CONFig:LTE:SECurity:AUTHenticate:KEY {}'
    SCPI_SETTINGS_PRESET_CMD = 'SYSTem:PRESet:FULL'

    # UXM's Test Application recovery
    TA_BOOT_TIME = 100

    # shh command
    SSH_START_GUI_APP_CMD_FORMAT = 'psexec -s -d -i 1 "{exe_path}"'
    SSH_CHECK_APP_RUNNING_CMD_FORMAT = 'tasklist | findstr /R {regex_app_name}'
    SSH_KILL_PROCESS_BY_NAME = 'taskkill /IM {process_name} /F'
    UXM_TEST_APP_NAME = 'TestApp.exe'

    # start process success regex
    PSEXEC_PROC_STARTED_REGEX_FORMAT = 'started on * with process ID {proc_id}'

    # HCCU default value
    HCCU_SOCKET_PORT = 4882
    # number of digit of the length of setup name
    HCCU_SCPI_CHANGE_SETUP_CMD = ':SYSTem:SETup:CONFig #{number_of_digit}{setup_name_len}{setup_name}'
    HCCU_SCPI_CHANGE_SCENARIO_CMD = ':SETup:SCENe "((NE_1, {scenario_name}))"'
    HCCU_STATUS_CHECK_CMD = ':SETup:INSTrument:STATus? 0\n'
    HCCU_FR2_SETUP_NAME = '{Name:"TSPC_1UXM5G_HF_2RRH_M1740A"}'
    HCCU_FR1_SETUP_NAME = '{Name:"TSPC_1UXM5G_LF"}'
    HCCU_GET_INSTRUMENT_COUNT_CMD = ':SETup:INSTrument:COUNt?'
    HCCU_FR2_INSTRUMENT_COUNT = 5
    HCCU_FR1_INSTRUMENT_COUNT = 2
    HCCU_FR2_SCENARIO = 'NR_4DL2x2_2UL2x2_LTE_4CC'
    HCCU_FR1_SCENARIO = 'NR_1DL4x4_1UL2x2_LTE_4CC'


    def __init__(self, ip_address, custom_files,uxm_user,
                 ssh_private_key_to_uxm, ta_exe_path, ta_exe_name):
        """Initializes the cellular simulator.

        Args:
            ip_address: the ip address of host where Keysight Test Application (TA)
                is installed.
            custom_files: a list of file path for custom files.
            uxm_user: username of host where Keysight TA resides.
            ssh_private_key_to_uxm: private key for key based ssh to
                host where Keysight TA resides.
            ta_exe_path: path to TA exe.
            ta_exe_name: name of TA exe.
        """
        super().__init__()
        self.custom_files = custom_files
        self.rockbottom_script = None
        self.cells = []
        self.uxm_ip = ip_address
        self.uxm_user = uxm_user
        self.ssh_private_key_to_uxm = os.path.expanduser(
                                        ssh_private_key_to_uxm)
        self.ta_exe_path = ta_exe_path
        self.ta_exe_name = ta_exe_name
        self.ssh_client = self.create_ssh_client()

        # get roclbottom file
        for file in self.custom_files:
            if 'rockbottom_' in file:
                self.rockbottom_script = file

        # connect to Keysight Test Application via socket
        self.recovery_ta()
        self.socket = self._socket_connect(self.uxm_ip, self.UXM_SOCKET_PORT)
        self.check_socket_connection()
        self.timeout = 120

        # hccu socket
        self.hccu_socket_port = self.HCCU_SOCKET_PORT
        self.hccu_socket = SocketWrapper(self.uxm_ip, self.hccu_socket_port)

    def socket_connect(self):
        self.socket = self._socket_connect(self.uxm_ip, self.UXM_SOCKET_PORT)

    def switch_HCCU_scenario(self, scenario_name: str):
        cmd = self.HCCU_SCPI_CHANGE_SCENARIO_CMD.format(
            scenario_name=scenario_name)
        self.hccu_socket.send_command(cmd)
        self.log.debug(f'Sent command: {cmd}')
        # this is require for the command to take effect
        # because hccu's port need to be free.
        self.hccu_socket.close()

    def switch_HCCU_setup(self, setup_name: str):
        """Change HHCU system setup.

        Args:
            setup_name: a string name
                of the system setup will be changed to.
        """
        setup_name_len = str(len(setup_name))
        number_of_digit = str(len(setup_name_len))
        cmd = self.HCCU_SCPI_CHANGE_SETUP_CMD.format(
            number_of_digit=number_of_digit,
            setup_name_len=setup_name_len,
            setup_name=setup_name
        )
        self.hccu_socket.send_command(cmd)
        self.log.debug(f'Sent command: {cmd}')
        # this is require for the command to take effect
        # because hccu's port need to be free.
        self.hccu_socket.close()

    def wait_until_hccu_operational(self, timeout=1200):
        """ Wait for hccu is ready to operate for a specified timeout.

        Args:
            timeout: time we are waiting for
                hccu in opertional status.

        Returns:
            True if HCCU status is operational within timeout.
            False otherwise.
        """
        # check status
        self.log.info('Waiting for HCCU to ready to operate.')
        cmd = self.HCCU_STATUS_CHECK_CMD
        t = 0
        interval = 10
        while t < timeout:
            response = self.hccu_socket.send_command_recv(cmd)
            if response == 'OPER\n':
                return True
            time.sleep(interval)
            t += interval
        return False

    def switch_HCCU_settings(self, is_fr2: bool):
        """Set HCCU setup configuration.

        HCCU stands for Hardware Configuration Control Utility,
        an interface allows us to control Keysight Test Equipment.

        Args:
            is_fr2: a bool value.
        """
        # change HCCU configration
        data = ''
        scenario_name = ''
        instrument_count_res = self.hccu_socket.send_command_recv(
            self.HCCU_GET_INSTRUMENT_COUNT_CMD)
        instrument_count = int(instrument_count_res)
        # if hccu setup is correct, no need to change.
        if is_fr2 and instrument_count == self.HCCU_FR2_INSTRUMENT_COUNT:
            self.log.info('UXM has correct HCCU setup.')
            return
        if not is_fr2 and instrument_count == self.HCCU_FR1_INSTRUMENT_COUNT:
            self.log.info('UXM has correct HCCU setup.')
            return

        self.log.info('UXM has incorrect HCCU setup, start changing setup.')
        # terminate TA and close socket
        self.log.info('Terminate TA before switch HCCU settings.')
        self.terminate_process(self.UXM_TEST_APP_NAME)
        self.socket.close()

        # change hccu setup
        if is_fr2:
            data = self.HCCU_FR2_SETUP_NAME
            scenario_name = self.HCCU_FR2_SCENARIO
        else:
            data = self.HCCU_FR1_SETUP_NAME
            scenario_name = self.HCCU_FR1_SCENARIO
        self.log.info('Switch HCCU setup.')
        self.switch_HCCU_setup(data)
        time.sleep(10)
        if not self.wait_until_hccu_operational():
            raise RuntimeError('Fail to switch HCCU setup.')

        # change scenario
        self.log.info('Ativate HCCU scenario.')
        self.switch_HCCU_scenario(scenario_name)
        time.sleep(40)
        if not self.wait_until_hccu_operational():
            raise RuntimeError('Fail to switch HCCU scenario.')

        # start TA and reconnect socket.
        self.recovery_ta()
        self.socket = self._socket_connect(self.uxm_ip, self.UXM_SOCKET_PORT)

    def create_ssh_client(self):
        """Create a ssh client to host."""
        ssh = paramiko.SSHClient()
        ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
        mykey = paramiko.Ed25519Key.from_private_key_file(
            self.ssh_private_key_to_uxm)
        ssh.connect(hostname=self.uxm_ip, username=self.uxm_user, pkey=mykey)
        self.log.info('SSH client to %s is connected' % self.uxm_ip)
        return ssh

    def terminate_process(self, process_name):
        cmd = self.SSH_KILL_PROCESS_BY_NAME.format(
            process_name=process_name
        )
        stdin, stdout, stderr = self.ssh_client.exec_command(cmd)
        stdin.close()
        err = ''.join(stderr.readlines())
        out = ''.join(stdout.readlines())
        final_output = str(out) + str(err)
        self.log.info(final_output)
        return out

    def is_ta_running(self):
        is_running_cmd = self.SSH_CHECK_APP_RUNNING_CMD_FORMAT.format(
            regex_app_name=self.ta_exe_name)
        stdin, stdout, stderr = self.ssh_client.exec_command(is_running_cmd)
        stdin.close()
        err = ''.join(stderr.readlines())
        out = ''.join(stdout.readlines())
        final_output = str(out) + str(err)
        self.log.info(final_output)
        return (out != '' and err == '')

    def _start_test_app(self):
        """Start Test Application on Windows."""
        # start GUI exe via ssh
        start_app_cmd = self.SSH_START_GUI_APP_CMD_FORMAT.format(
            exe_path=self.ta_exe_path)
        stdin, stdout, stderr = self.ssh_client.exec_command(start_app_cmd)
        self.log.info(f'Command sent to {self.uxm_ip}: {start_app_cmd}')
        stdin.close()
        err = ''.join(stderr.readlines())
        out = ''.join(stdout.readlines())
        # psexec return process ID as part of the exit code
        exit_status = stderr.channel.recv_exit_status()
        is_started = re.search(
            self.PSEXEC_PROC_STARTED_REGEX_FORMAT.format(proc_id=exit_status),
            err[-1])
        if is_started:
            raise RuntimeError('Fail to start TA: ' + out + err)
        # wait for ta completely boot up
        self.log.info('TA is starting')
        time.sleep(self.TA_BOOT_TIME)

    def recovery_ta(self):
        """Start TA if it is not running."""
        if not self.is_ta_running():
            self._start_test_app()
            # checking if ta booting process complete
            # by checking socket connection
            s = None
            retries = 12
            for _ in range(retries):
                try:
                    s = self._socket_connect(self.uxm_ip, self.UXM_SOCKET_PORT)
                    s.close()
                    return
                except ConnectionRefusedError as cre:
                    self.log.info(
                        'Connection refused, wait 10s for TA to boot')
                    time.sleep(10)
            raise RuntimeError('TA does not start on time')

    def set_rockbottom_script_path(self, path):
        """Set path to rockbottom script.

        Args:
            path: path to rockbottom script.
        """
        self.rockbottom_script = path

    def set_cell_info(self, cell_info):
        """Set type and number for multiple cells.

        Args:
            cell_info: list of dictionaries,
                each dictionary contain cell type
                and cell number for each cell
                that the simulator need to control.
        """
        if not cell_info:
            raise ValueError('Missing cell info from configurations file')
        self.cells = cell_info

    def deregister_ue_ims(self):
        """Remove UE IMS profile from UXM."""
        self._socket_send_SCPI_command(
                self.SCPI_DEREGISTER_UE_IMS)

    def create_dedicated_bearer(self):
        """Create a dedicated bearer setup for ims call.

        After UE connected and register on UXM IMS tab.
        It is required to create a dedicated bearer setup
        with EPS bearer ID 10.
        """
        cell_number = self.cells[0][self._KEY_CELL_NUMBER]
        self._socket_send_SCPI_command(
                self.SCPI_CREATE_DEDICATED_BEARER.format(cell_number))

    def turn_cell_on(self, cell_type, cell_number):
        """Turn UXM's cell on.

        Args:
            cell_type: type of cell (e.g NR5G, LTE).
            cell_number: ordinal number of a cell.
        """
        if cell_type and cell_number:
            self._socket_send_SCPI_command(
                self.SCPI_CELL_ON_CMD.format(cell_type, cell_number))
        else:
            raise ValueError('Invalid cell info\n' +
                             f' cell type: {cell_type}\n' +
                             f' cell number: {cell_number}\n')

    def turn_cell_off(self, cell_type, cell_number):
        """Turn UXM's cell off.

        Args:
            cell_type: type of cell (e.g NR5G, LTE).
            cell_number: ordinal number of a cell.
        """
        if cell_type and cell_number:
            self._socket_send_SCPI_command(
                self.SCPI_CELL_OFF_CMD.format(cell_type, cell_number))
        else:
            raise ValueError('Invalid cell info\n' +
                             f' cell type: {cell_type}\n' +
                             f' cell number: {cell_number}\n')

    def get_all_cell_status(self):
        """Gets status of all cells.

        Returns:
        List of tuples which has values (cell_type, cell_number, cell_status)
        """
        res = []
        for cell in self.cells:
            cell_type = cell[self._KEY_CELL_TYPE]
            cell_number = cell[self._KEY_CELL_NUMBER]
            cell_status = self.get_cell_status(cell_type, cell_number)
            res.append((cell_type, cell_number, cell_status))
        return res

    def get_cell_status(self, cell_type, cell_number):
        """Get status of cell.

        Args:
            cell_type: type of cell (e.g NR5G, LTE).
            cell_number: ordinal number of a cell.
        """
        if not cell_type or not cell_number:
            raise ValueError('Invalid cell with\n' +
                             f' cell type: {cell_type}\n' +
                             f' cell number: {cell_number}\n')

        return self._socket_send_SCPI_for_result_command(
            self.SCPI_GET_CELL_STATUS.format(cell_type, cell_number))

    def check_socket_connection(self):
        """Check if the socket connection is established.

        Query the identification of the Keysight Test Application
        we are trying to connect to. Empty response indicates
        connection fail, and vice versa.
        """
        self.socket.sendall(self.SCPI_CHECK_CONNECTION_CMD.encode())
        response = self.socket.recv(1024).decode()
        if response:
            self.log.info(f'Connected to: {response}')
        else:
            self.log.error('Fail to connect to callbox')

    def _socket_connect(self, host, port):
        """Create socket connection.

        Args:
            host: IP address of desktop where Keysight Test Application resides.
            port: port that Keysight Test Application is listening for socket
                communication.
        Returns:
            s: socket object.
        """
        self.log.info('Establishing connection to callbox via socket')
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.connect((host, port))
        return s

    def _socket_send_SCPI_command(self, command):
        """Send SCPI command without expecting response.

        Args:
            command: a string SCPI command.
        """
        # make sure there is a line break for the socket to send command
        command = command + '\n'
        # send command
        self.socket.sendall(command.encode())
        self.log.info(f'Sent {command}')

    def _socket_receive_SCPI_result(self):
        """Receive response from socket. """
        i = 1
        response = ''
        while i < self.timeout and not response:
            response = self.socket.recv(1024).decode()
            i += 1
        return response

    def _socket_send_SCPI_for_result_command(self, command):
        """Send SCPI command and expecting response.

        Args:
            command: a string SCPI command.
        """
        self._socket_send_SCPI_command(command)
        response = self._socket_receive_SCPI_result()
        return response

    def check_system_error(self):
        """Query system error from Keysight Test Application.

        Returns:
            status: a message indicate the number of errors
                and detail of errors if any.
                a string `0,"No error"` indicates no error.
        """
        status = self._socket_send_SCPI_for_result_command(
            self.SCPI_SYSTEM_ERROR_CHECK_CMD)
        self.log.info(f'System error status: {status}')
        return status

    def import_configuration(self, path):
        """Import SCPI config file.

        Args:
            path: path to SCPI file.
        """
        self._socket_send_SCPI_command(
            self.SCPI_SETTINGS_PRESET_CMD)
        time.sleep(10)
        self._socket_send_SCPI_command(
            self.SCPI_IMPORT_SCPI_FILE_CMD.format(path))
        time.sleep(45)

    def destroy(self):
        """Close socket connection with UXM. """
        self.socket.close()

    def setup_lte_scenario(self, path):
        """Configures the equipment for an LTE simulation.

        Args:
            path: path to SCPI config file.
        """
        self.import_configuration(path)

    def set_sim_type(self, is_3gpp_sim):
        sim_type = 'KEYSight'
        if is_3gpp_sim:
            sim_type = 'TEST3GPP'
        self._socket_send_SCPI_command(
            self.SCPI_CHANGE_SIM_NR_CMD.format(sim_type))
        time.sleep(2)
        self._socket_send_SCPI_command(
            self.SCPI_CHANGE_SIM_LTE_CMD.format(sim_type))
        time.sleep(2)

    def wait_until_attached_one_cell(self,
                                     cell_type,
                                     cell_number,
                                     dut,
                                     wait_for_camp_interval,
                                     attach_retries):
        """Wait until connect to given UXM cell.

        After turn off airplane mode, sleep for
        wait_for_camp_interval seconds for device to camp.
        If not device is not connected after the wait,
        either toggle airplane mode on/off or reboot device.
        Args:
            cell_type: type of cell
                which we are trying to connect to.
            cell_number: ordinal number of a cell
                which we are trying to connect to.
            dut: a AndroidCellular controller.
            wait_for_camp_interval: sleep interval,
                wait for device to camp.
            attach_retries: number of retry
                to wait for device
                to connect to 1 basestation.
        Raise:
            RuntimeError: device unable to connect to cell.
        """
        # airplane mode on
        dut.toggle_airplane_mode(True)
        time.sleep(5)

        # turn cell on
        self.turn_cell_on(cell_type, cell_number)
        time.sleep(5)

        interval = 10
        # waits for device to camp
        for index in range(1, attach_retries+1):
            count = 0
            # airplane mode off
            dut.toggle_airplane_mode(False)
            time.sleep(5)
            # check connection in small interval
            while count < wait_for_camp_interval:
                time.sleep(interval)
                cell_state = self.get_cell_status(cell_type, cell_number)
                self.log.info(f'cell state: {cell_state}')
                if cell_state == 'CONN\n':
                    # wait for connection stable
                    time.sleep(15)
                    # check connection status again
                    cell_state = self.get_cell_status(cell_type, cell_number)
                    self.log.info(f'cell state: {cell_state}')
                    if cell_state == 'CONN\n':
                        return True
                if cell_state == 'OFF\n':
                    self.turn_cell_on(cell_type, cell_number)
                    time.sleep(5)
                count += interval

            # reboot device
            if (index % 2) == 0:
                dut.ad.reboot()

            # toggle APM and cell on/off
            elif (index % 1) == 0:
                # Toggle APM on
                dut.toggle_airplane_mode(True)
                time.sleep(5)

                # Toggle simulator cell
                self.turn_cell_off(cell_type, cell_number)
                time.sleep(5)
                self.turn_cell_on(cell_type, cell_number)
                time.sleep(5)

                # Toggle APM off
                dut.toggle_airplane_mode(False)
                time.sleep(5)
            # increase length of small waiting interval
            interval += 5

        # Phone cannot connected to basestation of callbox
        raise RuntimeError(
            f'Phone was unable to connect to cell: {cell_type}-{cell_number}')

    def wait_until_attached(self, dut, timeout, attach_retries):
        """Waits until the DUT is attached to all required cells.

        Args:
            dut: a CellularAndroid controller.
            timeout: sleep interval,
                wait for device to camp in 1 try.
            attach_retries: number of retry
                to wait for device
                to connect to 1 basestation.
        """
        # get cell info
        first_cell_type = self.cells[0][self._KEY_CELL_TYPE]
        first_cell_number = self.cells[0][self._KEY_CELL_NUMBER]
        if len(self.cells) == 2:
            second_cell_type = self.cells[1][self._KEY_CELL_TYPE]
            second_cell_number = self.cells[1][self._KEY_CELL_NUMBER]

        # connect to 1st cell
        self.wait_until_attached_one_cell(first_cell_type,
                                          first_cell_number, dut, timeout,
                                          attach_retries)

        # aggregation to NR
        if len(self.cells) == 2:
            self.turn_cell_on(
                second_cell_type,
                second_cell_number,
            )

            for _ in range(1, attach_retries+1):
                self.log.info('Try to aggregate to NR.')
                self._socket_send_SCPI_command(
                    'BSE:CONFig:LTE:CELL1:CAGGregation:AGGRegate:NRCC:DL None')
                self._socket_send_SCPI_command(
                    'BSE:CONFig:LTE:CELL1:CAGGregation:AGGRegate:NRCC:UL None')
                self._socket_send_SCPI_command(
                    'BSE:CONFig:LTE:CELL1:CAGGregation:AGGRegate:NRCC:DL CELL1')
                self._socket_send_SCPI_command(
                    'BSE:CONFig:LTE:CELL1:CAGGregation:AGGRegate:NRCC:DL CELL1')
                time.sleep(1)
                self._socket_send_SCPI_command(
                    "BSE:CONFig:LTE:CELL1:CAGGregation:AGGRegate:NRCC:APPly")
                # wait for status stable
                time.sleep(10)
                cell_state = self.get_cell_status(second_cell_type, second_cell_number)
                self.log.info(f'cell state: {cell_state}')
                if cell_state == 'CONN\n':
                    return
                else:
                    self.turn_cell_off(second_cell_type, second_cell_number)
                    # wait for LTE cell to connect again
                    self.wait_until_attached_one_cell(first_cell_type,
                                            first_cell_number, dut, 120,
                                            2)

            raise RuntimeError(f'Fail to aggregate to NR from LTE.')

    def modify_dl_ul_mac_padding(self):
        """Disables dl/ul mac padding packets."""
        self.log.info('modifying dl ul mac padding')
        self._socket_send_SCPI_command(self._SCPI_CHANGE_DL_TDOMAIN)
        self._socket_send_SCPI_command(self._SCPI_CHANGE_UL_TDOMAIN)

    def set_lte_rrc_state_change_timer(self, enabled, time=10):
        """Configures the LTE RRC state change timer.

        Args:
            enabled: a boolean indicating if the timer should be on or off.
            time: time in seconds for the timer to expire.
        """
        raise NotImplementedError(
            'This UXM callbox simulator does not support this feature.')

    def set_band(self, bts_index, band):
        """Sets the band for the indicated base station.

        Args:
            bts_index: the base station number.
            band: the new band.
        """
        raise NotImplementedError(
            'This UXM callbox simulator does not support this feature.')

    def get_duplex_mode(self, band):
        """Determines if the band uses FDD or TDD duplex mode

        Args:
            band: a band number.

        Returns:
            an variable of class DuplexMode indicating if band is FDD or TDD.
        """
        raise NotImplementedError(
            'This UXM callbox simulator does not support this feature.')

    def set_input_power(self, bts_index, input_power):
        """Sets the input power for the indicated base station.

        Args:
            bts_index: the base station number.
            input_power: the new input power.
        """
        raise NotImplementedError(
            'This UXM callbox simulator does not support this feature.')

    def set_output_power(self, bts_index, output_power):
        """Sets the output power for the indicated base station.

        Args:
            bts_index: the base station number.
            output_power: the new output power.
        """
        raise NotImplementedError(
            'This UXM callbox simulator does not support this feature.')

    def set_tdd_config(self, bts_index, tdd_config):
        """Sets the tdd configuration number for the indicated base station.

        Args:
            bts_index: the base station number.
            tdd_config: the new tdd configuration number.
        """
        raise NotImplementedError(
            'This UXM callbox simulator does not support this feature.')

    def set_ssf_config(self, bts_index, ssf_config):
        """Sets the Special Sub-Frame config number for the indicated.

        base station.

        Args:
            bts_index: the base station number.
            ssf_config: the new ssf config number.
        """
        raise NotImplementedError(
            'This UXM callbox simulator does not support this feature.')

    def set_bandwidth(self, bts_index, bandwidth):
        """Sets the bandwidth for the indicated base station.

        Args:
            bts_index: the base station number
            bandwidth: the new bandwidth
        """
        raise NotImplementedError(
            'This UXM callbox simulator does not support this feature.')

    def set_downlink_channel_number(self, bts_index, channel_number):
        """Sets the downlink channel number for the indicated base station.

        Args:
            bts_index: the base station number.
            channel_number: the new channel number.
        """
        raise NotImplementedError(
            'This UXM callbox simulator does not support this feature.')

    def set_mimo_mode(self, bts_index, mimo_mode):
        """Sets the mimo mode for the indicated base station.

        Args:
            bts_index: the base station number
            mimo_mode: the new mimo mode
        """
        raise NotImplementedError(
            'This UXM callbox simulator does not support this feature.')

    def set_transmission_mode(self, bts_index, tmode):
        """Sets the transmission mode for the indicated base station.

        Args:
            bts_index: the base station number.
            tmode: the new transmission mode.
        """
        raise NotImplementedError(
            'This UXM callbox simulator does not support this feature.')

    def set_scheduling_mode(self,
                            bts_index,
                            scheduling,
                            mcs_dl=None,
                            mcs_ul=None,
                            nrb_dl=None,
                            nrb_ul=None):
        """Sets the scheduling mode for the indicated base station.

        Args:
            bts_index: the base station number.
            scheduling: the new scheduling mode.
            mcs_dl: Downlink MCS.
            mcs_ul: Uplink MCS.
            nrb_dl: Number of RBs for downlink.
            nrb_ul: Number of RBs for uplink.
        """
        raise NotImplementedError(
            'This UXM callbox simulator does not support this feature.')

    def set_dl_256_qam_enabled(self, bts_index, enabled):
        """Determines what MCS table should be used for the downlink.

        This only saves the setting that will be used when configuring MCS.

        Args:
            bts_index: the base station number.
            enabled: whether 256 QAM should be used.
        """
        raise NotImplementedError(
            'This UXM callbox simulator does not support this feature.')

    def set_ul_64_qam_enabled(self, bts_index, enabled):
        """Determines what MCS table should be used for the uplink.

        This only saves the setting that will be used when configuring MCS.

        Args:
            bts_index: the base station number.
            enabled: whether 64 QAM should be used.
        """
        raise NotImplementedError(
            'This UXM callbox simulator does not support this feature.')

    def set_mac_padding(self, bts_index, mac_padding):
        """Enables or disables MAC padding in the indicated base station.

        Args:
            bts_index: the base station number.
            mac_padding: the new MAC padding setting.
        """
        raise NotImplementedError(
            'This UXM callbox simulator does not support this feature.')

    def set_cfi(self, bts_index, cfi):
        """Sets the Channel Format Indicator for the indicated base station.

        Args:
            bts_index: the base station number.
            cfi: the new CFI setting.
        """
        raise NotImplementedError(
            'This UXM callbox simulator does not support this feature.')

    def set_paging_cycle(self, bts_index, cycle_duration):
        """Sets the paging cycle duration for the indicated base station.

        Args:
            bts_index: the base station number.
            cycle_duration: the new paging cycle duration in milliseconds.
        """
        raise NotImplementedError(
            'This UXM callbox simulator does not support this feature.')

    def set_phich_resource(self, bts_index, phich):
        """Sets the PHICH Resource setting for the indicated base station.

        Args:
            bts_index: the base station number.
            phich: the new PHICH resource setting.
        """
        raise NotImplementedError(
            'This UXM callbox simulator does not support this feature.')

    def lte_attach_secondary_carriers(self, ue_capability_enquiry):
        """Activates the secondary carriers for CA.

        Requires the DUT to be attached to the primary carrier first.

        Args:
            ue_capability_enquiry: UE capability enquiry message to be sent to
              the UE before starting carrier aggregation.
        """
        raise NotImplementedError(
            'This UXM callbox simulator does not support this feature.')

    def wait_until_communication_state(self, timeout=120):
        """Waits until the DUT is in Communication state.

        Args:
            timeout: after this amount of time the method will raise
                a CellularSimulatorError exception. Default is 120 seconds.
        """
        raise NotImplementedError(
            'This UXM callbox simulator does not support this feature.')

    def wait_until_idle_state(self, timeout=120):
        """Waits until the DUT is in Idle state.

        Args:
            timeout: after this amount of time the method will raise a
                CellularSimulatorError exception. Default is 120 seconds.
        """
        # turn on RRC release
        cell_type = self.cells[0][self._KEY_CELL_TYPE]
        cell_number = self.cells[0][self._KEY_CELL_NUMBER]

        # choose cmd base on cell type
        cmd = None
        if cell_type == 'LTE':
            cmd = self.SCPI_RRC_RELEASE_LTE_CMD
        else:
            cmd = self.SCPI_RRC_RELEASE_NR_CMD

        if not cmd:
            raise RuntimeError(f'Cell type [{cell_type}] is not supporting IDLE.')

        # checking status
        self.log.info('Wait for IDLE state.')
        for _ in range(5):
            cell_state = self.get_cell_status(cell_type, cell_number)
            self.log.info(f'cell state: {cell_state}')
            if cell_state == 'CONN\n':
                # RRC release
                self._socket_send_SCPI_command(cmd.format(cell_type, cell_number))
                # wait for status stable
                time.sleep(60)
            elif cell_state == 'IDLE\n':
                return

        raise RuntimeError('RRC release fail.')

    def detach(self):
        """ Turns off all the base stations so the DUT loose connection."""
        for cell in self.cells:
            cell_type = cell[self._KEY_CELL_TYPE]
            cell_number = cell[self._KEY_CELL_NUMBER]
            self.turn_cell_off(cell_type, cell_number)
            time.sleep(5)

    def stop(self):
        """Stops current simulation.

        After calling this method, the simulator will need to be set up again.
        """
        raise NotImplementedError(
            'This UXM callbox simulator does not support this feature.')

    def start_data_traffic(self):
        """Starts transmitting data from the instrument to the DUT. """
        raise NotImplementedError(
            'This UXM callbox simulator does not support this feature.')

    def stop_data_traffic(self):
        """Stops transmitting data from the instrument to the DUT. """
        raise NotImplementedError(
            'This UXM callbox simulator does not support this feature.')
