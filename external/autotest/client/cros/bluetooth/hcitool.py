# Lint as: python3
# Copyright 2022 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""Module to execute hcitool commands according to Bluetooth Core Spec v5.2."""

import btsocket
import logging
import struct
from autotest_lib.client.bin import utils
from autotest_lib.client.common_lib import error


class Hcitool(object):
    """Executes hcitool commands according to Bluetooth Core Spec v5.2."""
    CONTROLLER_PASS_CODE_VALUE = 0
    HCI_COMMAND_COMPLETE_EVENT = '0x0e'

    def _execute_hcitool_cmd(self, ogf, ocf, *parameter):
        """Executes hcitool commands using 'hcitool cmd ... '

        NOTE: return list depend on the Bluetooth Core Spec documentation.

        @param ogf: btsocket.OGF_... (int value).
        @param ocf: btsocket.OCF_... (int value).
        @param *parameter: parameter as hex string, e.g., ...,'1A','FA'.

        @return: list of the hcitool output. In case
                of failure, returns [hcitool status].
        """
        params = ['hcitool', 'cmd', hex(ogf), hex(ocf)]
        params.extend(parameter)
        cmd = ' '.join(params)
        logging.debug('Running "%s"', cmd)
        # Output format of hcitool command:
        # < HCI Command: ogf 0xXX, ocf 0xXXXX, plen X
        # > HCI Event: 0xXX plen XX
        #   XX XX XX XX XX XX XX XX XX XX ...
        output = utils.system_output(cmd, retain_output=True)
        output_parse_value = HciToolParser.parse_output(output)
        event_type, plen_value, status, event_bytearray = output_parse_value
        if event_type != self.HCI_COMMAND_COMPLETE_EVENT:
            raise error.TestError(
                    'Expect Command complete event with value: ' +
                    self.HCI_COMMAND_COMPLETE_EVENT + ' but got ' + event_type)

        if len(event_bytearray) != plen_value:
            raise error.TestError('Expect plen value of ' + str(plen_value) +
                                  'but got ' + str(len(event_bytearray)))

        if status != self.CONTROLLER_PASS_CODE_VALUE:
            return [status]

        return HciToolParser.parse_payload(event_bytearray, ogf, ocf)

    @staticmethod
    def filter_with_mask(names, mask):
        """Picks the supported names base on the given mask.

        @param names: List of names like feature,commands,...
        @param mask: A bitmask (8 bit little-endian) or a list of bitmasks.

        @return: List of supported names (features/commands/...).
        """

        if isinstance(mask, list):
            # Convert masks to bitstring in little-endian.
            mask = ''.join('{0:08b}'.format(m)[::-1] for m in mask)
        else:
            mask = '{:b}'.format(mask)
            mask = mask[::-1]
        return [names[i] for i, m in enumerate(mask) if m == '1']

    def _execute_hcitool_cmd_or_raise(self, ogf, ocf, *parameter):
        result = self._execute_hcitool_cmd(ogf, ocf, *parameter)
        status = result[0]
        if status != self.CONTROLLER_PASS_CODE_VALUE:
            raise error.TestError(
                    'Unexpected command output, the status code is ' +
                    str(status))
        return result

    def read_buffer_size(self):
        """Reads the buffer size of the BT controller.

        @returns: (status, acl_data_packet_length,
                synchronous_data_packet_length, total_num_acl_data_packets,
                total_num_synchronous_data_packets).
        """
        return self._execute_hcitool_cmd_or_raise(
                btsocket.OGF_INFO_PARAM, btsocket.OCF_READ_BUFFER_SIZE)

    def read_local_supported_features(self):
        """Reads local supported features for BR/EDR.

        @returns: (status, [features_name_list]).
        """
        execute_command_result = self._execute_hcitool_cmd_or_raise(
                btsocket.OGF_INFO_PARAM, btsocket.OCF_READ_LOCAL_FEATURES)
        status = execute_command_result[0]
        lmp_features_mask = execute_command_result[1]
        supported_features = SupportedFeatures.SUPPORTED_FEATURES_PAGE_ZERO
        final_result = self.filter_with_mask(supported_features,
                                             lmp_features_mask)
        return status, final_result

    def read_local_extended_features(self, page_number):
        """Reads local supported extended features for BR/EDR.

        @param: page number (0,1,2).

        @returns: (status, return_page_number,
                maximum_page_number, [features_name_list]).
        """
        if page_number not in (0, 1, 2):
            raise error.TestError(
                    'Invalid page_number: want (0, 1, 2), actual: ' +
                    str(page_number))
        execute_command_result = self._execute_hcitool_cmd_or_raise(
                btsocket.OGF_INFO_PARAM, btsocket.OCF_READ_LOCAL_EXT_FEATURES,
                str(page_number))

        status = execute_command_result[0]
        return_page_number = execute_command_result[1]
        maximum_page_number = execute_command_result[2]
        extended_mask = execute_command_result[3]
        supported_features = []
        if page_number == 0:
            supported_features = SupportedFeatures.SUPPORTED_FEATURES_PAGE_ZERO
        elif page_number == 1:
            supported_features = SupportedFeatures.SUPPORTED_FEATURES_PAGE_ONE
        elif page_number == 2:
            supported_features = SupportedFeatures.SUPPORTED_FEATURES_PAGE_TWO

        final_result = self.filter_with_mask(supported_features, extended_mask)

        return status, return_page_number, maximum_page_number, final_result

    def read_le_local_supported_features(self):
        """Reads LE (Low Energy) supported features.

        @return: (status, [LE_features_name_list]).
        """

        execute_command_result = self._execute_hcitool_cmd_or_raise(
                btsocket.OGF_LE_CTL,
                btsocket.OCF_LE_READ_LOCAL_SUPPORTED_FEATURES)

        status = execute_command_result[0]
        le_features_mask = execute_command_result[1]
        le_supported_features = SupportedFeatures.LE_SUPPORTED_FEATURE
        final_result = self.filter_with_mask(le_supported_features,
                                             le_features_mask)

        return status, final_result

    def set_event_filter(self, filter_type, filter_condition_type, condition):
        """Sets event filter.

        @param filter_type: filter type.
        @param filter_condition_type: filter condition type.
        @param condition: condition.

        @return: [status].
        """
        execute_command_result = self._execute_hcitool_cmd(
                btsocket.OGF_HOST_CTL, btsocket.OCF_SET_EVENT_FLT, filter_type,
                filter_condition_type, condition)

        return execute_command_result

    def read_local_supported_commands(self):
        """Reads local supported commands.

        @return: (status, [supported_commands_name_list]).
        """
        execute_command_result = self._execute_hcitool_cmd_or_raise(
                btsocket.OGF_INFO_PARAM, btsocket.OCF_READ_LOCAL_COMMANDS)
        status = execute_command_result[0]
        commands_mask = list(execute_command_result[1:])
        commands = SupportedCommands.SUPPORTED_COMMANDS
        final_result = self.filter_with_mask(commands, commands_mask)

        return status, final_result

    def check_command_supported(self, command_name):
        """Check if the given command name is supported.

        @param: command_name as string, e.g., HCI_Inquiry.

        @return: True if the command is supported, False otherwise.
        """
        supported_commands = self.read_local_supported_commands()[1]

        return command_name in supported_commands

    def le_read_accept_list_size(self):
        """Reads accept list size of the BT LE controller.

        @returns: (status, accept_list_size).
        """
        return self._execute_hcitool_cmd_or_raise(
                btsocket.OGF_LE_CTL, btsocket.OCF_LE_READ_ACCEPT_LIST_SIZE)

    def le_read_maximum_data_length(self):
        """Reads packet data length of the BT LE controller.

        @returns: (status, supported_max_tx_octets, supported_max_tx_time,
                supported_max_rx_octets, supported_max_rx_time).
        """
        return self._execute_hcitool_cmd_or_raise(
                btsocket.OGF_LE_CTL,
                HciToolParser.OCF_LE_READ_MAXIMUM_DATA_LENGTH)

    def le_read_resolving_list_size(self):
        """Reads resolving list size of the BT LE controller.
        @returns: (status, resolving_list_size).
        """
        return self._execute_hcitool_cmd_or_raise(
                btsocket.OGF_LE_CTL,
                HciToolParser.OCF_LE_READ_RESOLVING_LIST_SIZE)

    def le_read_number_of_supported_advertising_sets(self):
        """Reads number of supported advertisement sets.

        @returns: (status, num_supported_advertising_sets).
        """
        return self._execute_hcitool_cmd_or_raise(
                btsocket.OGF_LE_CTL,
                HciToolParser.OCF_LE_READ_NUMBER_OF_SUPPORTED_ADVERTISING_SETS)

    def vs_msft_read_supported_features(self, msft_ocf):
        """Reads VS MSFT supported features.

        @param msft_ocf: The msft_ocf for different chipset.

        @returns: (status, subcommand_opcode, [vs_msft_features_name_list],
                microsoft_event_prefix_length, microsoft_event_prefix)
        """
        VS_MSFT_READ_SUPPORTED_FEATURES_SUBCOMMAND_OPCODE = '00'
        execute_command_result = self._execute_hcitool_cmd_or_raise(
                btsocket.OGF_VENDOR_CMD, msft_ocf,
                VS_MSFT_READ_SUPPORTED_FEATURES_SUBCOMMAND_OPCODE)
        status = execute_command_result[0]
        vs_msft_features_mask = execute_command_result[2]
        vs_msft_supported_features = (
                SupportedFeatures.VS_MSFT_SUPPORTED_FEATURES)
        final_result = self.filter_with_mask(vs_msft_supported_features,
                                             vs_msft_features_mask)
        (_, subcommand_opcode, _, microsoft_event_prefix_length,
         microsoft_event_prefix) = execute_command_result
        return (status, subcommand_opcode, final_result,
                microsoft_event_prefix_length, microsoft_event_prefix)

    def le_get_vendor_capabilities_command(self):
        """Gets AOSP LE vendor capabilities.

        @returns: (status, max_advt_instances(deprecated),
                offloaded_resolution_of_private-address(deprecated),
                total_scan_results_storage, max_irk_list_sz, filtering_support,
                max_filter, activity_energy_info_support, version_supported,
                total_num_of_advt_tracked, extended_scan_support,
                debug_logging_supported,
                LE_address_generation_offloading_support(deprecated),
                A2DP_source_offload_capability_mask,
                bluetooth_quality_report_support, dynamic_audio_buffer_support).
        """
        execute_command_result = self._execute_hcitool_cmd_or_raise(
                btsocket.OGF_VENDOR_CMD,
                HciToolParser.OCF_LE_GET_VENDOR_CAPABILITIES_COMMAND)
        pack_format = '<{}B'.format(len(execute_command_result))
        execute_command_result = struct.pack(pack_format,
                                             execute_command_result)
        aosp_formats = [
                '<BBBHBBBBHHBB',  # v0.95
                '<BBBHBBBBHHBBB',  # v0.96
                '<BBBHBBBBHHBBBIB',  # v0.98
                '<BBBHBBBBHHBBBIBI',  # v1.00
        ]

        for f in aosp_formats:
            if struct.calcsize(f) == len(execute_command_result):
                return struct.unpack(f, execute_command_result)
        raise error.TestError(
                'Invalid output of AOSP capability command, length = ' +
                str(len(execute_command_result)))


class HciToolParser:
    """Parser of hcitool command output based on the hcitool parameters."""
    OCF_LE_READ_MAXIMUM_DATA_LENGTH = 0x002F
    OCF_LE_READ_RESOLVING_LIST_SIZE = 0x002A
    OCF_LE_READ_NUMBER_OF_SUPPORTED_ADVERTISING_SETS = 0x003B
    OCF_MSFT_INTEL_CHIPSET = 0X001e
    OCF_MSFT_MEDIATEK_CHIPSET = 0x0130
    OCF_MSFT_QCA_CHIPSET = 0x0170
    OCF_LE_GET_VENDOR_CAPABILITIES_COMMAND = 0x0153

    FORMATS = {
            ################## OGF=0X03 (OGF_HOST_CTL) ##################
            # Set Event Filter command
            (btsocket.OGF_HOST_CTL, btsocket.OCF_SET_EVENT_FLT):
            '<B',

            ################## OGF=0X04 (OGF_INFO_PARAM) ##################
            # Read Local Supported Commands command
            (btsocket.OGF_INFO_PARAM, btsocket.OCF_READ_LOCAL_COMMANDS):
            '<B64B',
            # Read Local Supported Features command
            (btsocket.OGF_INFO_PARAM, btsocket.OCF_READ_LOCAL_FEATURES):
            '<BQ',
            # Read Local Extended Features command
            (btsocket.OGF_INFO_PARAM, btsocket.OCF_READ_LOCAL_EXT_FEATURES):
            '<BBBQ',
            # Read Buffer Size command
            (btsocket.OGF_INFO_PARAM, btsocket.OCF_READ_BUFFER_SIZE):
            '<BHBHH',

            ################## OGF=0X08 (OGF_LE_CTL) ##################
            # LE Read Local Supported Features command
            (btsocket.OGF_LE_CTL, btsocket.OCF_LE_READ_LOCAL_SUPPORTED_FEATURES):
            '<BQ',
            # LE Set Advertising Data command
            (btsocket.OGF_LE_CTL, btsocket.OCF_LE_SET_ADVERTISING_DATA):
            '<B',
            # Read Data Packet Size
            (btsocket.OGF_LE_CTL, OCF_LE_READ_MAXIMUM_DATA_LENGTH):
            '<BHHHH',
            # LE Read Number of Supported Advertising Sets command
            (btsocket.OGF_LE_CTL, OCF_LE_READ_NUMBER_OF_SUPPORTED_ADVERTISING_SETS):
            '<BB',
            # LE Read Resolving List Size
            (btsocket.OGF_LE_CTL, OCF_LE_READ_RESOLVING_LIST_SIZE):
            '<BB',
            # LE Read Accept List Size command
            (btsocket.OGF_LE_CTL, btsocket.OCF_LE_READ_ACCEPT_LIST_SIZE):
            '<BB',

            ################## OGF=0X3f (OGF_VENDOR_CMD) ##################
            # LE_Get_Vendor_Capabilities_Command
            (btsocket.OGF_VENDOR_CMD, OCF_LE_GET_VENDOR_CAPABILITIES_COMMAND):
            None,
            # HCI_VS_MSFT_Intel_Read_Supported_Features
            (btsocket.OGF_VENDOR_CMD, OCF_MSFT_INTEL_CHIPSET):
            '<BBQBB',
            # HCI_VS_MSFT_QCA_Read_Supported_Features
            (btsocket.OGF_VENDOR_CMD, OCF_MSFT_QCA_CHIPSET):
            '<BBQBB',
            # HCI_VS_MSFT_Mediatek_Read_Supported_Features
            (btsocket.OGF_VENDOR_CMD, OCF_MSFT_MEDIATEK_CHIPSET):
            '<BBQBB'
    }

    @staticmethod
    def get_parsing_format(ogf, ocf):
        """Gets the format string to unpack the hcitool command output.

        @param ogf: Opcode Group Field.
        @param ocf: Opcode Command Field.

        @return: opcode output format according to Bluetooth Core Spec v5.2.
        """
        return HciToolParser.FORMATS[(ogf, ocf)]

    @staticmethod
    def parse_output(output):
        """Parse hcitool output.
        @param output: hcitool command output.

        @return: event_type, plen_value, status, event_bytearray.
        """
        hci_event = output.split('HCI Event:')[1].strip()
        event_type, *_, plen_value = hci_event.split('\n')[0].split(' ')

        # for example hci_event_values =XX XX XX XX XX XX XX XX XX XX ...
        # Sometimes hci_event_values may consist of multiple lines
        hci_event_values = hci_event.split('\n')[1:]
        hci_event_values_as_string = ''.join([
                v for v in hci_event_values
        ]).strip().replace("'", '').replace(' ', '')
        status = int(hci_event_values_as_string[6:8])
        event_bytearray = bytearray.fromhex(hci_event_values_as_string[6:])
        # Remove first 3 octet from count, not in 'event_bytearray'
        plen_value = int(plen_value) - 3
        return event_type, plen_value, status, event_bytearray

    @staticmethod
    def parse_payload(payload, ogf, ocf):
        """Parse hcitool payload.

        @param payload: hcitool event payload (as bytearray).
        @param ogf: btsocket.OGF_... (int value).
        @param ocf: btsocket.OCF_... (int value).

        @return: parsed result of the hcitool payload based on (ogf, ocf).
        If it cannot be parsed, returns the payload as bytes.
        """
        cmd_output_format = HciToolParser.get_parsing_format(ogf, ocf)
        if cmd_output_format is None:
            cmd_output_format = '<{}B'.format(len(payload))
        return struct.unpack(cmd_output_format, payload)


class SupportedFeatures:
    """List supported features names from BT core spec 5.2."""
    VS_MSFT_SUPPORTED_FEATURES = [
            'RSSI Monitoring feature for BR/EDR',
            'RSSI Monitoring feature for LE connections',
            'RSSI Monitoring of LE advertisements',
            'Advertising Monitoring of LE advertisements',
            'Verifying the validity of P-192 and P-256 keys',
            'Continuous Advertising Monitoring'
    ]
    SUPPORTED_FEATURES_PAGE_ZERO = [
            '3 slot packets', '5 slot packets', 'Encryption', 'Slot offset',
            'Timing accuracy', 'Role switch', 'Hold mode', 'Sniff mode',
            'Previously used', 'Power control requests',
            'Channel quality driven data rate (CQDDR)', 'SCO link',
            'HV2 packets', 'HV3 packets', 'u-law log synchronous data',
            'A-law log synchronous data', 'CVSD synchronous data',
            'Paging parameter negotiation', 'Power control',
            'Transparent synchronous data',
            'Flow control lag (least significant bit)',
            'Flow control lag (middle bit)',
            'Flow control lag (most significant bit)', 'Broadcast Encryption',
            'Reserved for future use', 'Enhanced Data Rate ACL 2 Mb/s mode',
            'Enhanced Data Rate ACL 3 Mb/s mode', 'Enhanced inquiry scan',
            'Interlaced inquiry scan', 'Interlaced page scan',
            'RSSI with inquiry results', 'Extended SCO link (EV3 packets)',
            'EV4 packets', 'EV5 packets', 'Reserved for future use',
            'AFH capable slave', 'AFH classification slave',
            'BR/EDR Not Supported', 'LE Supported (Controller)',
            '3-slot Enhanced Data Rate ACL packets',
            '5-slot Enhanced Data Rate ACL packets', 'Sniff subrating',
            'Pause encryption', 'AFH capable master',
            'AFH classification master', 'Enhanced Data Rate eSCO 2 Mb/s mode',
            'Enhanced Data Rate eSCO 3 Mb/s mode',
            '3-slot Enhanced Data Rate eSCO packets',
            'Extended Inquiry Response',
            'Simultaneous LE and BR/EDR to Same Device Capable (Controller)',
            'Reserved for future use',
            'Secure Simple Pairing (Controller Support)', 'Encapsulated PDU',
            'Erroneous Data Reporting', 'Non-flushable Packet Boundary Flag',
            'Reserved for future use',
            'HCI_Link_Supervision_Timeout_Changed event',
            'Variable Inquiry TX Power Level', 'Enhanced Power Control',
            'Reserved for future use', 'Reserved for future use',
            'Reserved for future use', 'Reserved for future use',
            'Extended features'
    ]

    SUPPORTED_FEATURES_PAGE_ONE = [
            'Secure Simple Pairing (Host Support)', 'LE Supported (Host)',
            'Simultaneous LE and BR/EDR to Same Device Capable (Host)',
            'Secure Connections (Host Support)'
    ]

    SUPPORTED_FEATURES_PAGE_TWO = [
            'Connectionless Slave Broadcast – Master Operation',
            'Connectionless Slave Broadcast – Slave Operation',
            'Synchronization Train', 'Synchronization Scan',
            'HCI_Inquiry_Response_Notification event',
            'Generalized interlaced scan', 'Coarse Clock Adjustment',
            'Reserved for future use',
            'Secure Connections (Controller Support)', 'Ping',
            'Slot Availability Mask', 'Train nudging'
    ]

    LE_SUPPORTED_FEATURE = [
            'LE Encryption', 'Connection Parameters Request Procedure',
            'Extended Reject Indication', 'Slave-initiated Features Exchange',
            'LE Ping', 'LE Data Packet Length Extension', 'LL Privacy',
            'Extended Scanner Filter Policies', 'LE 2M PHY',
            'Stable Modulation Index - Transmitter',
            'Stable Modulation Index Receiver', 'LE Coded PHY',
            'LE Extended Advertising', 'LE Periodic Advertising',
            'Channel Selection Algorithm #2', 'LE Power Class 1',
            'Minimum Number of Used Channels Procedur',
            'Connection CTE Request', 'Connection CTE Response',
            'Connectionless CTE Transmitter', 'Connectionless CTE Receiver',
            'Antenna Switching During CTE Transmission (AoD)',
            'Antenna Switching During CTE Reception (AoA)',
            'Receiving Constant Tone Extensions',
            'Periodic Advertising Sync Transfer Sender',
            'Periodic Advertising Sync Transfer Recipient',
            'Sleep Clock Accuracy Updates', 'Remote Public Key Validation',
            'Connected Isochronous Stream Master',
            'Connected Isochronous Stream Slave', 'Isochronous Broadcaster',
            'Synchronized Receiver', 'Isochronous Channels (Host Support)',
            'LE Power Control Request', 'LE Power Change Indication',
            'LE Path Loss Monitoring'
    ]


class SupportedCommands:
    """List supported command from BT core spec 5.2."""
    SUPPORTED_COMMANDS = [
            "HCI_Inquiry", "HCI_Inquiry_Cancel", "HCI_Periodic_Inquiry_Mode",
            "HCI_Exit_Periodic_Inquiry_Mode", "HCI_Create_Connection",
            "HCI_Disconnect", "HCI_Add_SCO_Connection",
            "HCI_Create_Connection_Cancel", "HCI_Accept_Connection_Request",
            "HCI_Reject_Connection_Request", "HCI_Link_Key_Request_Reply",
            "HCI_Link_Key_Request_Negative_Reply",
            "HCI_PIN_Code_Request_Reply",
            "HCI_PIN_Code_Request_Negative_Reply",
            "HCI_Change_Connection_Packet_Type",
            "HCI_Authentication_Requested", "HCI_Set_Connection_Encryption",
            "HCI_Change_Connection_Link_Key", "HCI_Master_Link_Key",
            "HCI_Remote_Name_Request", "HCI_Remote_Name_Request_Cancel",
            "HCI_Read_Remote_Supported_Features",
            "HCI_Read_Remote_Extended_Features",
            "HCI_Read_Remote_Version_Information", "HCI_Read_Clock_Offset",
            "HCI_Read_LMP_Handle", "Reserved for future use",
            "Reserved for future use", "Reserved for future use",
            "Reserved for future use", "Reserved for future use",
            "Reserved for future use", "Reserved for future use",
            "HCI_Hold_Mode", "HCI_Sniff_Mode", "HCI_Exit_Sniff_Mode",
            "Previously used", "Previously used", "HCI_QoS_Setup",
            "HCI_Role_Discovery", "HCI_Switch_Role",
            "HCI_Read_Link_Policy_Settings", "HCI_Write_Link_Policy_Settings",
            "HCI_Read_Default_Link_Policy_Settings",
            "HCI_Write_Default_Link_Policy_Settings", "HCI_Flow_Specification",
            "HCI_Set_Event_Mask", "HCI_Reset", "HCI_Set_Event_Filter",
            "HCI_Flush", "HCI_Read_PIN_Type", "HCI_Write_PIN_Type",
            "Previously used", "HCI_Read_Stored_Link_Key",
            "HCI_Write_Stored_Link_Key", "HCI_Delete_Stored_Link_Key",
            "HCI_Write_Local_Name", "HCI_Read_Local_Name",
            "HCI_Read_Connection_Accept_Timeout",
            "HCI_Write_Connection_Accept_Timeout", "HCI_Read_Page_Timeout",
            "HCI_Write_Page_Timeout", "HCI_Read_Scan_Enable",
            "HCI_Write_Scan_Enable", "HCI_Read_Page_Scan_Activity",
            "HCI_Write_Page_Scan_Activity", "HCI_Read_Inquiry_Scan_Activity",
            "HCI_Write_Inquiry_Scan_Activity",
            "HCI_Read_Authentication_Enable",
            "HCI_Write_Authentication_Enable", "HCI_Read_Encryption_Mode",
            "HCI_Write_Encryption_Mode", "HCI_Read_Class_Of_Device",
            "HCI_Write_Class_Of_Device", "HCI_Read_Voice_Setting",
            "HCI_Write_Voice_Setting", "HCI_Read_Automatic_Flush_Timeout",
            "HCI_Write_Automatic_Flush_Timeout",
            "HCI_Read_Num_Broadcast_Retransmissions",
            "HCI_Write_Num_Broadcast_Retransmissions",
            "HCI_Read_Hold_Mode_Activity", "HCI_Write_Hold_Mode_Activity",
            "HCI_Read_Transmit_Power_Level",
            "HCI_Read_Synchronous_Flow_Control_Enable",
            "HCI_Write_Synchronous_Flow_Control_Enable",
            "HCI_Set_Controller_To_Host_Flow_Control", "HCI_Host_Buffer_Size",
            "HCI_Host_Number_Of_Completed_Packets",
            "HCI_Read_Link_Supervision_Timeout",
            "HCI_Write_Link_Supervision_Timeout",
            "HCI_Read_Number_Of_Supported_IAC", "HCI_Read_Current_IAC_LAP",
            "HCI_Write_Current_IAC_LAP", "HCI_Read_Page_Scan_Mode_Period",
            "HCI_Write_Page_Scan_Mode_Period", "HCI_Read_Page_Scan_Mode",
            "HCI_Write_Page_Scan_Mode",
            "HCI_Set_AFH_Host_Channel_Classification",
            "Reserved for future use", "Reserved for future use",
            "HCI_Read_Inquiry_Scan_Type", "HCI_Write_Inquiry_Scan_Type",
            "HCI_Read_Inquiry_Mode", "HCI_Write_Inquiry_Mode",
            "HCI_Read_Page_Scan_Type", "HCI_Write_Page_Scan_Type",
            "HCI_Read_AFH_Channel_Assessment_Mode",
            "HCI_Write_AFH_Channel_Assessment_Mode", "Reserved for future use",
            "Reserved for future use", "Reserved for future use",
            "Reserved for future use", "Reserved for future use",
            "Reserved for future use", "Reserved for future use",
            "HCI_Read_Local_Version_Information", "Reserved for future use",
            "HCI_Read_Local_Supported_Features",
            "HCI_Read_Local_Extended_Features", "HCI_Read_Buffer_Size",
            "HCI_Read_Country_Code", "HCI_Read_BD_ADDR",
            "HCI_Read_Failed_Contact_Counter",
            "HCI_Reset_Failed_Contact_Counter", "HCI_Read_Link_Quality",
            "HCI_Read_RSSI", "HCI_Read_AFH_Channel_Map", "HCI_Read_Clock",
            "HCI_Read_Loopback_Mode", "HCI_Write_Loopback_Mode",
            "HCI_Enable_Device_Under_Test_Mode",
            "HCI_Setup_Synchronous_Connection_Request",
            "HCI_Accept_Synchronous_Connection_Request",
            "HCI_Reject_Synchronous_Connection_Request",
            "Reserved for future use", "Reserved for future use",
            "HCI_Read_Extended_Inquiry_Response",
            "HCI_Write_Extended_Inquiry_Response",
            "HCI_Refresh_Encryption_Key", "Reserved for future use",
            "HCI_Sniff_Subrating", "HCI_Read_Simple_Pairing_Mode",
            "HCI_Write_Simple_Pairing_Mode", "HCI_Read_Local_OOB_Data",
            "HCI_Read_Inquiry_Response_Transmit_Power_Level",
            "HCI_Write_Inquiry_Transmit_Power_Level",
            "HCI_Read_Default_Erroneous_Data_Reporting",
            "HCI_Write_Default_Erroneous_Data_Reporting",
            "Reserved for future use", "Reserved for future use",
            "Reserved for future use", "HCI_IO_Capability_Request_Reply",
            "HCI_User_Confirmation_Request_Reply",
            "HCI_User_Confirmation_Request_Negative_Reply",
            "HCI_User_Passkey_Request_Reply",
            "HCI_User_Passkey_Request_Negative_Reply",
            "HCI_Remote_OOB_Data_Request_Reply",
            "HCI_Write_Simple_Pairing_Debug_Mode", "HCI_Enhanced_Flush",
            "HCI_Remote_OOB_Data_Request_Negative_Reply",
            "Reserved for future use", "Reserved for future use",
            "HCI_Send_Keypress_Notification",
            "HCI_IO_Capability_Request_Negative_Reply",
            "HCI_Read_Encryption_Key_Size", "Reserved for future use",
            "Reserved for future use", "Reserved for future use",
            "HCI_Create_Physical_Link", "HCI_Accept_Physical_Link",
            "HCI_Disconnect_Physical_Link", "HCI_Create_Logical_Link",
            "HCI_Accept_Logical_Link", "HCI_Disconnect_Logical_Link",
            "HCI_Logical_Link_Cancel", "HCI_Flow_Spec_Modify",
            "HCI_Read_Logical_Link_Accept_Timeout",
            "HCI_Write_Logical_Link_Accept_Timeout",
            "HCI_Set_Event_Mask_Page_2", "HCI_Read_Location_Data",
            "HCI_Write_Location_Data", "HCI_Read_Local_AMP_Info",
            "HCI_Read_Local_AMP_ASSOC", "HCI_Write_Remote_AMP_ASSOC",
            "HCI_Read_Flow_Control_Mode", "HCI_Write_Flow_Control_Mode",
            "HCI_Read_Data_Block_Size", "Reserved for future use",
            "Reserved for future use", "HCI_Enable_AMP_Receiver_Reports",
            "HCI_AMP_Test_End", "HCI_AMP_Test",
            "HCI_Read_Enhanced_Transmit_Power_Level",
            "Reserved for future use", "HCI_Read_Best_Effort_Flush_Timeout",
            "HCI_Write_Best_Effort_Flush_Timeout", "HCI_Short_Range_Mode",
            "HCI_Read_LE_Host_Support", "HCI_Write_LE_Host_Support",
            "Reserved for future use", "HCI_LE_Set_Event_Mask",
            "HCI_LE_Read_Buffer_Size [v1]",
            "HCI_LE_Read_Local_Supported_Features", "Reserved for future use",
            "HCI_LE_Set_Random_Address", "HCI_LE_Set_Advertising_Parameters",
            "HCI_LE_Read_Advertising_Physical_Channel_Tx_Power",
            "HCI_LE_Set_Advertising_Data", "HCI_LE_Set_Scan_Response_Data",
            "HCI_LE_Set_Advertising_Enable", "HCI_LE_Set_Scan_Parameters",
            "HCI_LE_Set_Scan_Enable", "HCI_LE_Create_Connection",
            "HCI_LE_Create_Connection_Cancel", "HCI_LE_Read_White_List_Size",
            "HCI_LE_Clear_White_List", "HCI_LE_Add_Device_To_White_List",
            "HCI_LE_Remove_Device_From_White_List", "HCI_LE_Connection_Update",
            "HCI_LE_Set_Host_Channel_Classification",
            "HCI_LE_Read_Channel_Map", "HCI_LE_Read_Remote_Features",
            "HCI_LE_Encrypt", "HCI_LE_Rand", "HCI_LE_Enable_Encryption",
            "HCI_LE_Long_Term_Key_Request_Reply",
            "HCI_LE_Long_Term_Key_Request_Negative_Reply",
            "HCI_LE_Read_Supported_States", "HCI_LE_Receiver_Test [v1]",
            "HCI_LE_Transmitter_Test [v1]", "HCI_LE_Test_End",
            "Reserved for future use", "Reserved for future use",
            "Reserved for future use", "Reserved for future use",
            "HCI_Enhanced_Setup_Synchronous_Connection",
            "HCI_Enhanced_Accept_Synchronous_Connection",
            "HCI_Read_Local_Supported_Codecs",
            "HCI_Set_MWS_Channel_Parameters",
            "HCI_Set_External_Frame_Configuration", "HCI_Set_MWS_Signaling",
            "HCI_Set_MWS_Transport_Layer", "HCI_Set_MWS_Scan_Frequency_Table",
            "HCI_Get_MWS_Transport_Layer_Configuration",
            "HCI_Set_MWS_PATTERN_Configuration",
            "HCI_Set_Triggered_Clock_Capture", "HCI_Truncated_Page",
            "HCI_Truncated_Page_Cancel",
            "HCI_Set_Connectionless_Slave_Broadcast",
            "HCI_Set_Connectionless_Slave_Broadcast_Receive",
            "HCI_Start_Synchronization_Train",
            "HCI_Receive_Synchronization_Train", "HCI_Set_Reserved_LT_ADDR",
            "HCI_Delete_Reserved_LT_ADDR",
            "HCI_Set_Connectionless_Slave_Broadcast_Data",
            "HCI_Read_Synchronization_Train_Parameters",
            "HCI_Write_Synchronization_Train_Parameters",
            "HCI_Remote_OOB_Extended_Data_Request_Reply",
            "HCI_Read_Secure_Connections_Host_Support",
            "HCI_Write_Secure_Connections_Host_Support",
            "HCI_Read_Authenticated_Payload_Timeout",
            "HCI_Write_Authenticated_Payload_Timeout",
            "HCI_Read_Local_OOB_Extended_Data",
            "HCI_Write_Secure_Connections_Test_Mode",
            "HCI_Read_Extended_Page_Timeout",
            "HCI_Write_Extended_Page_Timeout",
            "HCI_Read_Extended_Inquiry_Length",
            "HCI_Write_Extended_Inquiry_Length",
            "HCI_LE_Remote_Connection_Parameter_Request_Reply",
            "HCI_LE_Remote_Connection_Parameter_Request_Negative_Reply",
            "HCI_LE_Set_Data_Length",
            "HCI_LE_Read_Suggested_Default_Data_Length",
            "HCI_LE_Write_Suggested_Default_Data_Length",
            "HCI_LE_Read_Local_P-256_Public_Key", "HCI_LE_Generate_DHKey [v1]",
            "HCI_LE_Add_Device_To_Resolving_List",
            "HCI_LE_Remove_Device_From_Resolving_List",
            "HCI_LE_Clear_Resolving_List", "HCI_LE_Read_Resolving_List_Size",
            "HCI_LE_Read_Peer_Resolvable_Address",
            "HCI_LE_Read_Local_Resolvable_Address",
            "HCI_LE_Set_Address_Resolution_Enable",
            "HCI_LE_Set_Resolvable_Private_Address_Timeout",
            "HCI_LE_Read_Maximum_Data_Length", "HCI_LE_Read_PHY",
            "HCI_LE_Set_Default_PHY", "HCI_LE_Set_PHY",
            "HCI_LE_Receiver_Test [v2]", "HCI_LE_Transmitter_Test [v2]",
            "HCI_LE_Set_Advertising_Set_Random_Address",
            "HCI_LE_Set_Extended_Advertising_Parameters",
            "HCI_LE_Set_Extended_Advertising_Data",
            "HCI_LE_Set_Extended_Scan_Response_Data",
            "HCI_LE_Set_Extended_Advertising_Enable",
            "HCI_LE_Read_Maximum_Advertising_Data_Length",
            "HCI_LE_Read_Number_of_Supported_Advertising_Sets",
            "HCI_LE_Remove_Advertising_Set", "HCI_LE_Clear_Advertising_Sets",
            "HCI_LE_Set_Periodic_Advertising_Parameters",
            "HCI_LE_Set_Periodic_Advertising_Data",
            "HCI_LE_Set_Periodic_Advertising_Enable",
            "HCI_LE_Set_Extended_Scan_Parameters",
            "HCI_LE_Set_Extended_Scan_Enable",
            "HCI_LE_Extended_Create_Connection",
            "HCI_LE_Periodic_Advertising_Create_Sync",
            "HCI_LE_Periodic_Advertising_Create_Sync_Cancel",
            "HCI_LE_Periodic_Advertising_Terminate_Sync",
            "HCI_LE_Add_Device_To_Periodic_Advertiser_List",
            "HCI_LE_Remove_Device_From_Periodic_Advertiser_List",
            "HCI_LE_Clear_Periodic_Advertiser_List",
            "HCI_LE_Read_Periodic_Advertiser_List_Size",
            "HCI_LE_Read_Transmit_Power", "HCI_LE_Read_RF_Path_Compensation",
            "HCI_LE_Write_RF_Path_Compensation", "HCI_LE_Set_Privacy_Mode",
            "HCI_LE_Receiver_Test [v3]", "HCI_LE_Transmitter_Test [v3]",
            "HCI_LE_Set_Connectionless_CTE_Transmit_Parameters",
            "HCI_LE_Set_Connectionless_CTE_Transmit_Enable",
            "HCI_LE_Set_Connectionless_IQ_Sampling_Enable",
            "HCI_LE_Set_Connection_CTE_Receive_Parameters",
            "HCI_LE_Set_Connection_CTE_Transmit_Parameters",
            "HCI_LE_Connection_CTE_Request_Enable",
            "HCI_LE_Connection_CTE_Response_Enable",
            "HCI_LE_Read_Antenna_Information",
            "HCI_LE_Set_Periodic_Advertising_Receive_Enable",
            "HCI_LE_Periodic_Advertising_Sync_Transfer",
            "HCI_LE_Periodic_Advertising_Set_Info_Transfer",
            "HCI_LE_Set_Periodic_Advertising_Sync_Transfer_Parameters",
            "HCI_LE_Set_Default_Periodic_Advertising_Sync_Transfer_Parameters",
            "HCI_LE_Generate_DHKey [v2]",
            "HCI_Read_Local_Simple_Pairing_Options",
            "HCI_LE_Modify_Sleep_Clock_Accuracy",
            "HCI_LE_Read_Buffer_Size [v2]", "HCI_LE_Read_ISO_TX_Sync",
            "HCI_LE_Set_CIG_Parameters", "HCI_LE_Set_CIG_Parameters_Test",
            "HCI_LE_Create_CIS", "HCI_LE_Remove_CIG",
            "HCI_LE_Accept_CIS_Request", "HCI_LE_Reject_CIS_Request",
            "HCI_LE_Create_BIG", "HCI_LE_Create_BIG_Test",
            "HCI_LE_Terminate_BIG", "HCI_LE_BIG_Create_Sync",
            "HCI_LE_BIG_Terminate_Sync", "HCI_LE_Request_Peer_SCA",
            "HCI_LE_Setup_ISO_Data_Path", "HCI_LE_Remove_ISO_Data_Path",
            "HCI_LE_ISO_Transmit_Test", "HCI_LE_ISO_Receive_Test",
            "HCI_LE_ISO_Read_Test_Counters", "HCI_LE_ISO_Test_End",
            "HCI_LE_Set_Host_Feature", "HCI_LE_Read_ISO_Link_Quality",
            "HCI_LE_Enhanced_Read_Transmit_Power_Level",
            "HCI_LE_Read_Remote_Transmit_Power_Level",
            "HCI_LE_Set_Path_Loss_Reporting_Parameters",
            "HCI_LE_Set_Path_Loss_Reporting_Enable",
            "HCI_LE_Set_Transmit_Power_Reporting_Enable",
            "HCI_LE_Transmitter_Test [v4]", "HCI_Set_Ecosystem_Base_Interval",
            "HCI_Read_Local_Supported_Codecs [v2]",
            "HCI_Read_Local_Supported_Codec_Capabilities",
            "HCI_Read_Local_Supported_Controller_Delay",
            "HCI_Configure_Data_Path", "Reserved for future use",
            "Reserved for future use"
    ]
    DEPRECATED_COMMANDS = [
            "HCI_Add_SCO_Connection", "HCI_Read_Encryption_Mode",
            "HCI_Write_Encryption_Mode", "HCI_Read_Page_Scan_Mode_Period",
            "HCI_Write_Page_Scan_Mode_Period", "HCI_Read_Page_Scan_Mode",
            "HCI_Write_Page_Scan_Mode", "HCI_Read_Country_Code"
    ]
