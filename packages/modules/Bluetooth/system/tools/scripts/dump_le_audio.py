#!/usr/bin/env python
# Copyright 2021 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License. You may obtain a copy of
# the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations under
# the License.
#
#
# This script extracts LE Audio audio data from btsnoop.
# Generates a audio dump file where each frame consists of a two-byte frame
# length information and the coded frame
#
# Audio File Name Format:
# [Context]_sf[Sample frequency]_fd[Frame duration]_[Channel allocation]_
# frame[Octets per frame]_[Stream start timestamp]_[Direction].bin
#
#
# Usage:
# ./dump_le_audio.py BTSNOOP.cfa [-v] [--header] [--ase_handle ASE_HANDLE]
#
# -v, --verbose: to enable the verbose log
# --header: Add the header for LC3 Conformance Interoperability Test Software V.1.0.3 from LC3 test specification.
#  --ase_handle ASE_HANDLE: Set the ASE handle manually.
#
# NOTE:
# Please make sure you HCI Snoop data file includes the following frames:
# 1. GATT service discovery for "ASE Control Point" chracteristic (if you give the ase_handle via command, the flow could be skipped)
# 2. GATT config codec via ASE Control Point
# 3. HCI create CIS to point out the "Start stream", and the data frames.
# After all hci packet parse finished, would dump all remain audio data as well
#
# Correspondsing Spec.
# ASCS_1.0
# PACS_1.0
# BAP_1.0
# LC3.TS V1.0.3
#
from collections import defaultdict
from os import X_OK

import argparse
import struct
import sys
import time

BTSNOOP_FILE_NAME = ""
BTSNOOP_HEADER = b'btsnoop\x00\x00\x00\x00\x01\x00\x00\x03\xea'

COMMADN_PACKET = 1
ACL_PACKET = 2
SCO_PACKET = 3
EVENT_PACKET = 4
ISO_PACKET = 5

SENT = 0
RECEIVED = 1

L2CAP_ATT_CID = 0x0004
L2CAP_CID = 0x0005

PSM_EATT = 0x0027

# opcode for att protocol
OPCODE_ATT_READ_BY_TYPE_RSP = 0x09
OPCODE_ATT_WRITE_CMD = 0x52

UUID_ASE_CONTROL_POINT = 0x2BC6

# opcode for ase control
OPCODE_CONFIG_CODEC = 0x01
OPCODE_ENABLE = 0x03
OPCODE_UPDATE_METADATA = 0x07
OPCODE_RELEASE = 0x08

# opcode for hci command
OPCODE_HCI_CREATE_CIS = 0x2064
OPCODE_REMOVE_ISO_DATA_PATH = 0x206F
OPCODE_LE_SET_PERIODIC_ADVERTISING_DATA = 0x203F
OPCODE_LE_CREATE_BIG = 0x2068
OPCODE_LE_SETUP_ISO_DATA_PATH = 0x206E

# opcode for L2CAP channel
OPCODE_L2CAP_CREDIT_BASED_CONNECTION_REQ = 0x17
OPCODE_L2CAP_CREDIT_BASED_CONNECTION_RSP = 0x18

# HCI event
EVENT_CODE_LE_META_EVENT = 0x3E
SUBEVENT_CODE_LE_CREATE_BIG_COMPLETE = 0x1B

TYPE_STREAMING_AUDIO_CONTEXTS = 0x02

TYPE_SAMPLING_FREQUENCIES = 0x01
TYPE_FRAME_DURATION = 0x02
TYPE_CHANNEL_ALLOCATION = 0x03
TYPE_OCTETS_PER_FRAME = 0x04

CONTEXT_TYPE_UNSPECIFIED = 0x0001
CONTEXT_TYPE_CONVERSATIONAL = 0x0002
CONTEXT_TYPE_MEDIA = 0x0004
CONTEXT_TYPE_GAME = 0x0008
CONTEXT_TYPE_VOICEASSISTANTS = 0x0020
CONTEXT_TYPE_LIVE = 0x0040
CONTEXT_TYPE_RINGTONE = 0x0200

# sample frequency
SAMPLE_FREQUENCY_8000 = 0x01
SAMPLE_FREQUENCY_11025 = 0x02
SAMPLE_FREQUENCY_16000 = 0x03
SAMPLE_FREQUENCY_22050 = 0x04
SAMPLE_FREQUENCY_24000 = 0x05
SAMPLE_FREQUENCY_32000 = 0x06
SAMPLE_FREQUENCY_44100 = 0x07
SAMPLE_FREQUENCY_48000 = 0x08
SAMPLE_FREQUENCY_88200 = 0x09
SAMPLE_FREQUENCY_96000 = 0x0a
SAMPLE_FREQUENCY_176400 = 0x0b
SAMPLE_FREQUENCY_192000 = 0x0c
SAMPLE_FREQUENCY_384000 = 0x0d

FRAME_DURATION_7_5 = 0x00
FRAME_DURATION_10 = 0x01

AUDIO_LOCATION_MONO = 0x00
AUDIO_LOCATION_LEFT = 0x01
AUDIO_LOCATION_RIGHT = 0x02
AUDIO_LOCATION_CENTER = 0x04

AD_TYPE_SERVICE_DATA_16_BIT = 0x16
BASIC_AUDIO_ANNOUNCEMENT_SERVICE = 0x1851

packet_number = 0
debug_enable = False
add_header = False
ase_handle = 0xFFFF

l2cap_identifier_set = set()
source_cid = set()
destinate_cid = set()


class Connection:

    def __init__(self):
        self.ase_handle = 0xFFFF
        self.number_of_ases = 0
        self.ase = defaultdict(AseStream)
        self.context = 0xFFFF
        self.cis_handle = 0xFFFF
        self.input_dump = []
        self.output_dump = []
        self.start_time = 0xFFFFFFFF

    def dump(self):
        print("start_time: " + str(self.start_time))
        print("ase_handle: " + str(self.ase_handle))
        print("context type: " + str(self.context))
        print("number_of_ases:  " + str(self.number_of_ases))
        print("cis_handle:  " + str(self.cis_handle))
        for id, ase_stream in self.ase.items():
            print("ase id: " + str(id))
            ase_stream.dump()


class AseStream:

    def __init__(self):
        self.sampling_frequencies = 0xFF
        self.frame_duration = 0xFF
        self.channel_allocation = 0xFFFFFFFF
        self.octets_per_frame = 0xFFFF

    def dump(self):
        print("sampling_frequencies: " + str(self.sampling_frequencies))
        print("frame_duration: " + str(self.frame_duration))
        print("channel_allocation: " + str(self.channel_allocation))
        print("octets_per_frame: " + str(self.octets_per_frame))


class Broadcast:

    def __init__(self):
        self.num_of_bis = defaultdict(int)  # subgroup - num_of_bis
        self.bis = defaultdict(BisStream)  # bis_index - codec_config
        self.bis_index_handle_map = defaultdict(int)  # bis_index - bis_handle
        self.bis_index_list = []

    def dump(self):
        for bis_index, iso_stream in self.bis.items():
            print("bis_index: " + str(bis_index) + " bis handle: " + str(self.bis_index_handle_map[bis_index]))
            iso_stream.dump()


class BisStream:

    def __init__(self):
        self.sampling_frequencies = 0xFF
        self.frame_duration = 0xFF
        self.channel_allocation = 0xFFFFFFFF
        self.octets_per_frame = 0xFFFF
        self.output_dump = []
        self.start_time = 0xFFFFFFFF

    def dump(self):
        print("start_time: " + str(self.start_time))
        print("sampling_frequencies: " + str(self.sampling_frequencies))
        print("frame_duration: " + str(self.frame_duration))
        print("channel_allocation: " + str(self.channel_allocation))
        print("octets_per_frame: " + str(self.octets_per_frame))


connection_map = defaultdict(Connection)
cis_acl_map = defaultdict(int)
broadcast_map = defaultdict(Broadcast)
big_adv_map = defaultdict(int)
bis_stream_map = defaultdict(BisStream)


def generate_header(file, stream, is_cis):
    sf_case = {
        SAMPLE_FREQUENCY_8000: 80,
        SAMPLE_FREQUENCY_11025: 110,
        SAMPLE_FREQUENCY_16000: 160,
        SAMPLE_FREQUENCY_22050: 220,
        SAMPLE_FREQUENCY_24000: 240,
        SAMPLE_FREQUENCY_32000: 320,
        SAMPLE_FREQUENCY_44100: 441,
        SAMPLE_FREQUENCY_48000: 480,
        SAMPLE_FREQUENCY_88200: 882,
        SAMPLE_FREQUENCY_96000: 960,
        SAMPLE_FREQUENCY_176400: 1764,
        SAMPLE_FREQUENCY_192000: 1920,
        SAMPLE_FREQUENCY_384000: 2840,
    }
    fd_case = {FRAME_DURATION_7_5: 7.5, FRAME_DURATION_10: 10}
    al_case = {AUDIO_LOCATION_MONO: 1, AUDIO_LOCATION_LEFT: 1, AUDIO_LOCATION_RIGHT: 1, AUDIO_LOCATION_CENTER: 2}

    header = bytearray.fromhex('1ccc1200')
    if is_cis:
        for ase in stream.ase.values():
            header = header + struct.pack("<H", sf_case[ase.sampling_frequencies])
            header = header + struct.pack("<H", int(ase.octets_per_frame * 8 * 10 / fd_case[ase.frame_duration]))
            header = header + struct.pack("<HHHL", al_case[ase.channel_allocation], fd_case[ase.frame_duration] * 100,
                                          0, 48000000)
            break
    else:
        header = header + struct.pack("<H", sf_case[stream.sampling_frequencies])
        header = header + struct.pack("<H", int(stream.octets_per_frame * 8 * 10 / fd_case[stream.frame_duration]))
        header = header + struct.pack("<HHHL", al_case[stream.channel_allocation], fd_case[stream.frame_duration] * 100,
                                      0, 48000000)
    file.write(header)


def parse_codec_information(connection_handle, ase_id, packet):
    length, packet = unpack_data(packet, 1, False)
    if len(packet) < length:
        debug_print("Invalid codec configuration length")
        return packet
    ase = connection_map[connection_handle].ase[ase_id]
    while length > 0:
        config_length, packet = unpack_data(packet, 1, False)
        config_type, packet = unpack_data(packet, 1, False)
        value, packet = unpack_data(packet, config_length - 1, False)
        if config_type == TYPE_SAMPLING_FREQUENCIES:
            ase.sampling_frequencies = value
        elif config_type == TYPE_FRAME_DURATION:
            ase.frame_duration = value
        elif config_type == TYPE_CHANNEL_ALLOCATION:
            ase.channel_allocation = value
        elif config_type == TYPE_OCTETS_PER_FRAME:
            ase.octets_per_frame = value
        length -= (config_length + 1)

    return packet


def parse_att_read_by_type_rsp(packet, connection_handle):
    length, packet = unpack_data(packet, 1, False)
    if length != 7:
        #ignore the packet, we're only interested in this packet for the characteristic type UUID
        return

    if length > len(packet):
        debug_print("Invalid att packet length")
        return

    attribute_handle, packet = unpack_data(packet, 2, False)
    if debug_enable:
        debug_print("attribute_handle - " + str(attribute_handle))
    packet = unpack_data(packet, 1, True)
    value_handle, packet = unpack_data(packet, 2, False)
    characteristic_uuid, packet = unpack_data(packet, 2, False)
    if characteristic_uuid == UUID_ASE_CONTROL_POINT:
        debug_print("ASE Control point found!")
        connection_map[connection_handle].ase_handle = value_handle


def parse_att_write_cmd(packet, connection_handle, timestamp):
    attribute_handle, packet = unpack_data(packet, 2, False)
    global ase_handle
    if ase_handle != 0xFFFF:
        connection_map[connection_handle].ase_handle = ase_handle

    if connection_map[connection_handle].ase_handle == attribute_handle:
        if debug_enable:
            debug_print("Action with ASE Control point")
        opcode, packet = unpack_data(packet, 1, False)
        if opcode == OPCODE_CONFIG_CODEC:
            debug_print("config_codec")
            (connection_map[connection_handle].number_of_ases, packet) = unpack_data(packet, 1, False)
            for i in range(connection_map[connection_handle].number_of_ases):
                ase_id, packet = unpack_data(packet, 1, False)
                # ignore target_latency, target_phy, codec_id
                packet = unpack_data(packet, 7, True)
                packet = parse_codec_information(connection_handle, ase_id, packet)
        elif opcode == OPCODE_ENABLE or opcode == OPCODE_UPDATE_METADATA:
            if debug_enable:
                debug_print("enable or update metadata")
            numbers_of_ases, packet = unpack_data(packet, 1, False)
            for i in range(numbers_of_ases):
                ase_id, packet = unpack_data(packet, 1, False)
                metadata_length, packet = unpack_data(packet, 1, False)
                if metadata_length > len(packet):
                    debug_print("Invalid metadata length")
                    return
                length, packet = unpack_data(packet, 1, False)
                if length > len(packet):
                    debug_print("Invalid metadata value length")
                    return
                metadata_type, packet = unpack_data(packet, 1, False)
                if metadata_type == TYPE_STREAMING_AUDIO_CONTEXTS:
                    (connection_map[connection_handle].context, packet) = unpack_data(packet, 2, False)
                    break

            if opcode == OPCODE_ENABLE:
                debug_print("enable, set timestamp")
                connection_map[connection_handle].start_time = timestamp

            if debug_enable:
                connection_map[connection_handle].dump()


def parse_att_packet(packet, connection_handle, flags, timestamp):
    opcode, packet = unpack_data(packet, 1, False)
    packet_handle = {
        (OPCODE_ATT_READ_BY_TYPE_RSP, RECEIVED): (lambda x, y, z: parse_att_read_by_type_rsp(x, y)),
        (OPCODE_ATT_WRITE_CMD, SENT): (lambda x, y, z: parse_att_write_cmd(x, y, z))
    }
    packet_handle.get((opcode, flags), lambda x, y, z: None)(packet, connection_handle, timestamp)


def parse_big_codec_information(adv_handle, packet):
    # Ignore presentation delay
    packet = unpack_data(packet, 3, True)
    number_of_subgroup, packet = unpack_data(packet, 1, False)
    for subgroup in range(number_of_subgroup):
        num_of_bis, packet = unpack_data(packet, 1, False)
        broadcast_map[adv_handle].num_of_bis[subgroup] = num_of_bis
        # Ignore codec id
        packet = unpack_data(packet, 5, True)
        length, packet = unpack_data(packet, 1, False)
        if len(packet) < length:
            print("Invalid subgroup codec information length")
            return

        while length > 0:
            config_length, packet = unpack_data(packet, 1, False)
            config_type, packet = unpack_data(packet, 1, False)
            value, packet = unpack_data(packet, config_length - 1, False)
            if config_type == TYPE_SAMPLING_FREQUENCIES:
                sampling_frequencies = value
            elif config_type == TYPE_FRAME_DURATION:
                frame_duration = value
            elif config_type == TYPE_OCTETS_PER_FRAME:
                octets_per_frame = value
            else:
                print("Unknown config type")
            length -= (config_length + 1)

        # Ignore metadata
        metadata_length, packet = unpack_data(packet, 1, False)
        packet = unpack_data(packet, metadata_length, True)

        for count in range(num_of_bis):
            bis_index, packet = unpack_data(packet, 1, False)
            broadcast_map[adv_handle].bis_index_list.append(bis_index)
            length, packet = unpack_data(packet, 1, False)
            if len(packet) < length:
                print("Invalid level 3 codec information length")
                return

            while length > 0:
                config_length, packet = unpack_data(packet, 1, False)
                config_type, packet = unpack_data(packet, 1, False)
                value, packet = unpack_data(packet, config_length - 1, False)
                if config_type == TYPE_CHANNEL_ALLOCATION:
                    channel_allocation = value
                else:
                    print("Ignored config type")
                length -= (config_length + 1)

            broadcast_map[adv_handle].bis[bis_index].sampling_frequencies = sampling_frequencies
            broadcast_map[adv_handle].bis[bis_index].frame_duration = frame_duration
            broadcast_map[adv_handle].bis[bis_index].octets_per_frame = octets_per_frame
            broadcast_map[adv_handle].bis[bis_index].channel_allocation = channel_allocation

    return packet


def debug_print(log):
    global packet_number
    print("#" + str(packet_number) + ": " + log)


def unpack_data(data, byte, ignore):
    if ignore:
        return data[byte:]

    value = 0
    if byte == 1:
        value = struct.unpack("<B", data[:byte])[0]
    elif byte == 2:
        value = struct.unpack("<H", data[:byte])[0]
    elif byte == 4:
        value = struct.unpack("<I", data[:byte])[0]
    return value, data[byte:]


def parse_command_packet(packet, timestamp):
    opcode, packet = unpack_data(packet, 2, False)
    if opcode == OPCODE_HCI_CREATE_CIS:
        debug_print("OPCODE_HCI_CREATE_CIS")

        length, packet = unpack_data(packet, 1, False)
        if length != len(packet):
            debug_print("Invalid cmd length")
            return
        cis_count, packet = unpack_data(packet, 1, False)
        for i in range(cis_count):
            cis_handle, packet = unpack_data(packet, 2, False)
            cis_handle &= 0x0EFF
            acl_handle, packet = unpack_data(packet, 2, False)
            connection_map[acl_handle].cis_handle = cis_handle
            cis_acl_map[cis_handle] = acl_handle

        if debug_enable:
            connection_map[acl_handle].dump()
    elif opcode == OPCODE_REMOVE_ISO_DATA_PATH:
        debug_print("OPCODE_REMOVE_ISO_DATA_PATH")

        length, packet = unpack_data(packet, 1, False)
        if length != len(packet):
            debug_print("Invalid cmd length")
            return

        iso_handle, packet = unpack_data(packet, 2, False)
        # CIS stream
        if iso_handle in cis_acl_map:
            acl_handle = cis_acl_map[iso_handle]
            dump_cis_audio_data_to_file(acl_handle)
        # To Do: BIS stream
        elif iso_handle in bis_stream_map:
            dump_bis_audio_data_to_file(iso_handle)
    elif opcode == OPCODE_LE_SET_PERIODIC_ADVERTISING_DATA:
        debug_print("OPCODE_LE_SET_PERIODIC_ADVERTISING_DATA")

        length, packet = unpack_data(packet, 1, False)
        if length != len(packet):
            debug_print("Invalid cmd length")
            return

        if length < 21:
            debug_print("Ignored. Not basic audio announcement")
            return

        adv_hdl, packet = unpack_data(packet, 1, False)
        #ignore operation, advertising_data_length
        packet = unpack_data(packet, 2, True)
        length, packet = unpack_data(packet, 1, False)
        if length != len(packet):
            debug_print("Invalid AD element length")
            return

        ad_type, packet = unpack_data(packet, 1, False)
        service, packet = unpack_data(packet, 2, False)
        if ad_type != AD_TYPE_SERVICE_DATA_16_BIT or service != BASIC_AUDIO_ANNOUNCEMENT_SERVICE:
            debug_print("Ignored. Not basic audio announcement")
            return

        packet = parse_big_codec_information(adv_hdl, packet)
    elif opcode == OPCODE_LE_CREATE_BIG:
        debug_print("OPCODE_LE_CREATE_BIG")

        length, packet = unpack_data(packet, 1, False)
        if length != len(packet) and length < 31:
            debug_print("Invalid Create BIG command length")
            return

        big_handle, packet = unpack_data(packet, 1, False)
        adv_handle, packet = unpack_data(packet, 1, False)
        big_adv_map[big_handle] = adv_handle
    elif opcode == OPCODE_LE_SETUP_ISO_DATA_PATH:
        debug_print("OPCODE_LE_SETUP_ISO_DATA_PATH")
        length, packet = unpack_data(packet, 1, False)
        if len(packet) != length:
            debug_print("Invalid LE SETUP ISO DATA PATH command length")
            return

        iso_handle, packet = unpack_data(packet, 2, False)
        if iso_handle in bis_stream_map:
            bis_stream_map[iso_handle].start_time = timestamp


def parse_event_packet(packet):
    event_code, packet = unpack_data(packet, 1, False)
    if event_code != EVENT_CODE_LE_META_EVENT:
        return

    length, packet = unpack_data(packet, 1, False)
    if len(packet) != length:
        print("Invalid LE mata event length")
        return

    subevent_code, packet = unpack_data(packet, 1, False)
    if subevent_code != SUBEVENT_CODE_LE_CREATE_BIG_COMPLETE:
        return

    status, packet = unpack_data(packet, 1, False)
    if status != 0x00:
        debug_print("Create_BIG failed")
        return

    big_handle, packet = unpack_data(packet, 1, False)
    if big_handle not in big_adv_map:
        print("Invalid BIG handle")
        return
    adv_handle = big_adv_map[big_handle]
    # Ignore, we don't care these parameter
    packet = unpack_data(packet, 15, True)
    num_of_bis, packet = unpack_data(packet, 1, False)
    for count in range(num_of_bis):
        bis_handle, packet = unpack_data(packet, 2, False)
        bis_index = broadcast_map[adv_handle].bis_index_list[count]
        broadcast_map[adv_handle].bis_index_handle_map[bis_index] = bis_handle
        bis_stream_map[bis_handle] = broadcast_map[adv_handle].bis[bis_index]


def convert_time_str(timestamp):
    """This function converts time to string format."""
    timestamp_sec = float(timestamp) / 1000000
    local_timestamp = time.localtime(timestamp_sec)
    ms = timestamp_sec - int(timestamp_sec)
    ms_str = "{0:06}".format(int(round(ms * 1000000)))

    str_format = time.strftime("%m_%d__%H_%M_%S", local_timestamp)
    full_str_format = str_format + "_" + ms_str

    return full_str_format


def dump_cis_audio_data_to_file(acl_handle):
    if debug_enable:
        connection_map[acl_handle].dump()
    file_name = ""
    context_case = {
        CONTEXT_TYPE_UNSPECIFIED: "Unspecified",
        CONTEXT_TYPE_CONVERSATIONAL: "Conversational",
        CONTEXT_TYPE_MEDIA: "Media",
        CONTEXT_TYPE_GAME: "Game",
        CONTEXT_TYPE_VOICEASSISTANTS: "VoiceAssistants",
        CONTEXT_TYPE_LIVE: "Live",
        CONTEXT_TYPE_RINGTONE: "Ringtone"
    }
    file_name += context_case.get(connection_map[acl_handle].context, "Unknown")
    for ase in connection_map[acl_handle].ase.values():
        sf_case = {
            SAMPLE_FREQUENCY_8000: "8000",
            SAMPLE_FREQUENCY_11025: "11025",
            SAMPLE_FREQUENCY_16000: "16000",
            SAMPLE_FREQUENCY_22050: "22050",
            SAMPLE_FREQUENCY_24000: "24000",
            SAMPLE_FREQUENCY_32000: "32000",
            SAMPLE_FREQUENCY_44100: "44100",
            SAMPLE_FREQUENCY_48000: "48000",
            SAMPLE_FREQUENCY_88200: "88200",
            SAMPLE_FREQUENCY_96000: "96000",
            SAMPLE_FREQUENCY_176400: "176400",
            SAMPLE_FREQUENCY_192000: "192000",
            SAMPLE_FREQUENCY_384000: "284000"
        }
        file_name += ("_sf" + sf_case[ase.sampling_frequencies])
        fd_case = {FRAME_DURATION_7_5: "7_5", FRAME_DURATION_10: "10"}
        file_name += ("_fd" + fd_case[ase.frame_duration])
        al_case = {
            AUDIO_LOCATION_MONO: "mono",
            AUDIO_LOCATION_LEFT: "left",
            AUDIO_LOCATION_RIGHT: "right",
            AUDIO_LOCATION_CENTER: "center"
        }
        file_name += ("_" + al_case[ase.channel_allocation])
        file_name += ("_frame" + str(ase.octets_per_frame))
        file_name += ("_" + convert_time_str(connection_map[acl_handle].start_time))
        break

    if connection_map[acl_handle].input_dump != []:
        debug_print("Dump unicast input...")
        f = open(file_name + "_input.bin", 'wb')
        if add_header == True:
            generate_header(f, connection_map[acl_handle], True)
        arr = bytearray(connection_map[acl_handle].input_dump)
        f.write(arr)
        f.close()
        connection_map[acl_handle].input_dump = []

    if connection_map[acl_handle].output_dump != []:
        debug_print("Dump unicast output...")
        f = open(file_name + "_output.bin", 'wb')
        if add_header == True:
            generate_header(f, connection_map[acl_handle], True)
        arr = bytearray(connection_map[acl_handle].output_dump)
        f.write(arr)
        f.close()
        connection_map[acl_handle].output_dump = []

    return


def dump_bis_audio_data_to_file(iso_handle):
    if debug_enable:
        bis_stream_map[iso_handle].dump()
    file_name = "broadcast"
    sf_case = {
        SAMPLE_FREQUENCY_8000: "8000",
        SAMPLE_FREQUENCY_11025: "11025",
        SAMPLE_FREQUENCY_16000: "16000",
        SAMPLE_FREQUENCY_22050: "22050",
        SAMPLE_FREQUENCY_24000: "24000",
        SAMPLE_FREQUENCY_32000: "32000",
        SAMPLE_FREQUENCY_44100: "44100",
        SAMPLE_FREQUENCY_48000: "48000",
        SAMPLE_FREQUENCY_88200: "88200",
        SAMPLE_FREQUENCY_96000: "96000",
        SAMPLE_FREQUENCY_176400: "176400",
        SAMPLE_FREQUENCY_192000: "192000",
        SAMPLE_FREQUENCY_384000: "284000"
    }
    file_name += ("_sf" + sf_case[bis_stream_map[iso_handle].sampling_frequencies])
    fd_case = {FRAME_DURATION_7_5: "7_5", FRAME_DURATION_10: "10"}
    file_name += ("_fd" + fd_case[bis_stream_map[iso_handle].frame_duration])
    al_case = {
        AUDIO_LOCATION_MONO: "mono",
        AUDIO_LOCATION_LEFT: "left",
        AUDIO_LOCATION_RIGHT: "right",
        AUDIO_LOCATION_CENTER: "center"
    }
    file_name += ("_" + al_case[bis_stream_map[iso_handle].channel_allocation])
    file_name += ("_frame" + str(bis_stream_map[iso_handle].octets_per_frame))
    file_name += ("_" + convert_time_str(bis_stream_map[iso_handle].start_time))

    if bis_stream_map[iso_handle].output_dump != []:
        debug_print("Dump broadcast output...")
        f = open(file_name + "_output.bin", 'wb')
        if add_header == True:
            generate_header(f, bis_stream_map[iso_handle], False)
        arr = bytearray(bis_stream_map[iso_handle].output_dump)
        f.write(arr)
        f.close()
        bis_stream_map[iso_handle].output_dump = []

    return


def parse_acl_packet(packet, flags, timestamp):
    # Check the minimum acl length, HCI leader (4 bytes)
    # + L2CAP header (4 bytes)
    if len(packet) < 8:
        debug_print("Invalid acl data length.")
        return

    connection_handle, packet = unpack_data(packet, 2, False)
    connection_handle = connection_handle & 0x0FFF
    if connection_handle > 0x0EFF:
        debug_print("Invalid packet handle, skip")
        return
    total_length, packet = unpack_data(packet, 2, False)
    if total_length != len(packet):
        debug_print("Invalid total length, skip")
        return
    pdu_length, packet = unpack_data(packet, 2, False)
    channel_id, packet = unpack_data(packet, 2, False)
    if pdu_length != len(packet):
        debug_print("Invalid pdu length, skip")
        return

    if debug_enable:
        debug_print("ACL connection_handle - " + str(connection_handle) + " channel id - " + (str(channel_id)))

    # Gather EATT CID
    if channel_id == L2CAP_CID:
        global l2cap_identifier_set
        global source_cid
        global destinate_cid
        opcode, packet = unpack_data(packet, 1, False)
        identifier, packet = unpack_data(packet, 1, False)
        l2cap_length, packet = unpack_data(packet, 2, False)
        if opcode == OPCODE_L2CAP_CREDIT_BASED_CONNECTION_REQ:
            spsm, packet = unpack_data(packet, 2, False)
            if spsm == PSM_EATT:
                if opcode == OPCODE_L2CAP_CREDIT_BASED_CONNECTION_REQ:
                    l2cap_identifier_set.add(identifier)
                    packet = unpack_data(packet, 6, True)
                    for i in range(0, l2cap_length - 8, 2):
                        cid, packet = unpack_data(packet, 2, False)
                        source_cid.add(cid)

        if opcode == OPCODE_L2CAP_CREDIT_BASED_CONNECTION_RSP:
            if identifier in l2cap_identifier_set:
                l2cap_identifier_set.remove(identifier)
                packet = unpack_data(packet, 8, True)
                for i in range(0, l2cap_length - 8, 2):
                    cid, packet = unpack_data(packet, 2, False)
                    destinate_cid.add(cid)

    # Parse ATT protocol
    if channel_id == L2CAP_ATT_CID:
        if debug_enable:
            debug_print("parse_att_packet")
        parse_att_packet(packet, connection_handle, flags, timestamp)

    if channel_id in source_cid or channel_id in destinate_cid:
        if debug_enable:
            debug_print("parse_eatt_packet")
        packet = unpack_data(packet, 2, True)
        parse_att_packet(packet, connection_handle, flags, timestamp)


def parse_iso_packet(packet, flags):
    iso_handle, packet = unpack_data(packet, 2, False)
    iso_handle &= 0x0EFF
    iso_data_load_length, packet = unpack_data(packet, 2, False)
    if iso_data_load_length != len(packet):
        debug_print("Invalid iso data load length")
        return

    # Ignore timestamp, sequence number
    packet = unpack_data(packet, 6, True)
    iso_sdu_length, packet = unpack_data(packet, 2, False)
    if len(packet) == 0:
        debug_print("The iso data is empty")
    elif iso_sdu_length != len(packet):
        debug_print("Invalid iso sdu length")
        return

    # CIS stream
    if iso_handle in cis_acl_map:
        acl_handle = cis_acl_map[iso_handle]
        if flags == SENT:
            connection_map[acl_handle].output_dump.extend(struct.pack("<H", len(packet)))
            connection_map[acl_handle].output_dump.extend(list(packet))
        elif flags == RECEIVED:
            connection_map[acl_handle].input_dump.extend(struct.pack("<H", len(packet)))
            connection_map[acl_handle].input_dump.extend(list(packet))
    elif iso_handle in bis_stream_map:
        bis_stream_map[iso_handle].output_dump.extend(struct.pack("<H", len(packet)))
        bis_stream_map[iso_handle].output_dump.extend(list(packet))


def parse_next_packet(btsnoop_file):
    global packet_number
    packet_number += 1
    packet_header = btsnoop_file.read(25)
    if len(packet_header) != 25:
        return False

    (length_original, length_captured, flags, dropped_packets, timestamp,
     type) = struct.unpack(">IIIIqB", packet_header)

    if length_original != length_captured:
        debug_print("Filtered btnsoop, can not be parsed")
        return False

    packet = btsnoop_file.read(length_captured - 1)
    if len(packet) != length_original - 1:
        debug_print("Invalid packet length!")
        return False

    if dropped_packets:
        debug_print("Invalid droped value")
        return False

    packet_handle = {
        COMMADN_PACKET: (lambda x, y, z: parse_command_packet(x, z)),
        ACL_PACKET: (lambda x, y, z: parse_acl_packet(x, y, z)),
        SCO_PACKET: (lambda x, y, z: None),
        EVENT_PACKET: (lambda x, y, z: parse_event_packet(x)),
        ISO_PACKET: (lambda x, y, z: parse_iso_packet(x, y))
    }
    packet_handle.get(type, lambda x, y, z: None)(packet, flags, timestamp)
    return True


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("btsnoop_file", help="btsnoop file contains LE audio start procedure")
    parser.add_argument("-v", "--verbose", help="Enable verbose log.", action="store_true")
    parser.add_argument("--header",
                        help="Add the header for LC3 Conformance Interoperability Test Software V.1.0.3.",
                        action="store_true")
    parser.add_argument("--ase_handle", help="Set the ASE handle manually.", type=int)

    argv = parser.parse_args()
    BTSNOOP_FILE_NAME = argv.btsnoop_file

    global debug_enable
    global add_header
    global ase_handle
    if argv.verbose:
        debug_enable = True

    if argv.header:
        add_header = True

    if argv.ase_handle:
        ase_handle = int(argv.ase_handle)

    with open(BTSNOOP_FILE_NAME, "rb") as btsnoop_file:
        if btsnoop_file.read(16) != BTSNOOP_HEADER:
            print("Invalid btsnoop header")
            exit(1)

        while True:
            if not parse_next_packet(btsnoop_file):
                break

    for handle in connection_map.keys():
        dump_cis_audio_data_to_file(handle)

    for handle in bis_stream_map.keys():
        dump_bis_audio_data_to_file(handle)


if __name__ == "__main__":
    main()
