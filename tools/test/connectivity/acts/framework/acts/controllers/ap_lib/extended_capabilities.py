#!/usr/bin/env python3
#
#   Copyright 2021 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#         http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

from enum import IntEnum, unique
from typing import Tuple


@unique
class ExtendedCapability(IntEnum):
    """All extended capabilities present in IEEE 802.11-2020 Table 9-153.

    Each name has a value corresponding to that extended capability's bit offset
    in the specification's extended capabilities field.

    Note that most extended capabilities are represented by a single bit, which
    indicates whether the extended capability is advertised by the STA; but
    some are represented by multiple bits. In the enum, each extended capability
    has the value of its offset; comments indicate capabilities that use
    multiple bits.
    """
    TWENTY_FORTY_BSS_COEXISTENCE_MANAGEMENT_SUPPORT = 0
    GLK = 1
    EXTENDED_CHANNEL_SWITCHING = 2
    GLK_GCR = 3
    PSMP_CAPABILITY = 4
    # 5 reserved
    S_PSMP_SUPPORT = 6
    EVENT = 7
    DIAGNOSTICS = 8
    MULTICAST_DIAGNOSTICS = 9
    LOCATION_TRACKING = 10
    FMS = 11
    PROXY_ARP_SERVICE = 12
    COLLOCATED_INTERFERENCE_REPORTING = 13
    CIVIC_LOCATION = 14
    GEOSPATIAL_LOCATION = 15
    TFS = 16
    WNM_SLEEP_MODE = 17
    TIM_BROADCAST = 18
    BSS_TRANSITION = 19
    QOS_TRAFFIC_CAPABILITY = 20
    AC_STATION_COUNT = 21
    MULTIPLE_BSSID = 22
    TIMING_MEASUREMENT = 23
    CHANNEL_USAGE = 24
    SSID_LIST = 25
    DMS = 26
    UTC_TSF_OFFSET = 27
    TPU_BUFFER_STA_SUPPORT = 28
    TDLS_PEER_PSM_SUPPORT = 29
    TDLS_CHANNEL_SWITCHING = 30
    INTERWORKING = 31
    QOS_MAP = 32
    EBR = 33
    SSPN_INTERFACE = 34
    # 35 reserved
    MSGCF_CAPABILITY = 36
    TDLS_SUPPORT = 37
    TDLS_PROHIBITED = 38
    TDLS_CHANNEL_SWITCHING_PROHIBITED = 39
    REJECT_UNADMITTED_FRAME = 40
    SERVICE_INTERVAL_GRANULARITY = 41
    # Bits 41-43 contain SERVICE_INTERVAL_GRANULARITY value
    IDENTIFIER_LOCATION = 44
    U_APSD_COEXISTENCE = 45
    WNM_NOTIFICATION = 46
    QAB_CAPABILITY = 47
    UTF_8_SSID = 48
    QMF_ACTIVATED = 49
    QMF_RECONFIGURATION_ACTIVATED = 50
    ROBUST_AV_STREAMING = 51
    ADVANCED_GCR = 52
    MESH_GCR = 53
    SCS = 54
    QLOAD_REPORT = 55
    ALTERNATE_EDCA = 56
    UNPROTECTED_TXOP_NEGOTIATION = 57
    PROTECTED_TXOP_NEGOTIATION = 58
    # 59 reserved
    PROTECTED_QLOAD_REPORT = 60
    TDLS_WIDER_BANDWIDTH = 61
    OPERATING_MODE_NOTIFICATION = 62
    MAX_NUMBER_OF_MSDUS_IN_A_MSDU = 63
    # 63-64 contain MAX_NUMBER_OF_MSDUS_IN_A_MSDU value
    CHANNEL_SCHEDULE_MANAGEMENT = 65
    GEODATABASE_INBAND_ENABLING_SIGNAL = 66
    NETWORK_CHANNEL_CONTROL = 67
    WHITE_SPACE_MAP = 68
    CHANNEL_AVAILABILITY_QUERY = 69
    FINE_TIMING_MEASUREMENT_RESPONDER = 70
    FINE_TIMING_MEASUREMENT_INITIATOR = 71
    FILS_CAPABILITY = 72
    EXTENDED_SPECTRUM_MANAGEMENT_CAPABLE = 73
    FUTURE_CHANNEL_GUIDANCE = 74
    PAD = 75
    # 76-79 reserved
    COMPLETE_LIST_OF_NON_TX_BSSID_PROFILES = 80
    SAE_PASSWORD_IDENTIFIERS_IN_USE = 81
    SAE_PASSWORD_IDENTIFIERS_USED_EXCLUSIVELY = 82
    # 83 reserved
    BEACON_PROTECTION_ENABLED = 84
    MIRRORED_SCS = 85
    # 86 reserved
    LOCAL_MAC_ADDRESS_POLICY = 87
    # 88-n reserved


def _offsets(ext_cap_offset: ExtendedCapability) -> Tuple[int, int]:
    """For given capability, return the byte and bit offsets within the field.

    802.11 divides the extended capability field into bytes, as does the
    ExtendedCapabilities class below. This function returns the index of the
    byte that contains the given extended capability, as well as the bit offset
    inside that byte (all offsets zero-indexed). For example,
    MULTICAST_DIAGNOSTICS is bit 9, which is within byte 1 at bit offset 1.
    """
    byte_offset = ext_cap_offset // 8
    bit_offset = ext_cap_offset % 8
    return byte_offset, bit_offset


class ExtendedCapabilities:
    """Extended capability parsing and representation.

    See IEEE 802.11-2020 9.4.2.26.
    """

    def __init__(self, ext_cap: bytearray = bytearray()):
        """Represent the given extended capabilities field.

        Args:
            ext_cap: IEEE 802.11-2020 9.4.2.26 extended capabilities field.
            Default is an empty field, meaning no extended capabilities are
            advertised.
        """
        self._ext_cap = ext_cap

    def _capability_advertised(self, ext_cap: ExtendedCapability) -> bool:
        """Whether an extended capability is advertised.

        Args:
            ext_cap: an extended capability.
        Returns:
            True if the bit is present and its value is 1, otherwise False.
        Raises:
            NotImplementedError: for extended capabilities that span more than
            a single bit. These could be supported, but no callers need them
            at this time.
        """
        if ext_cap in [
                ExtendedCapability.SERVICE_INTERVAL_GRANULARITY,
                ExtendedCapability.MAX_NUMBER_OF_MSDUS_IN_A_MSDU
        ]:
            raise NotImplementedError(
                f'{ext_cap.name} not implemented yet by {__class__}')
        byte_offset, bit_offset = _offsets(ext_cap)
        if len(self._ext_cap) > byte_offset:
            # Use bit_offset to derive a mask that will check the correct bit.
            if self._ext_cap[byte_offset] & 2**bit_offset > 0:
                return True
        return False

    @property
    def bss_transition(self) -> bool:
        return self._capability_advertised(ExtendedCapability.BSS_TRANSITION)

    @property
    def proxy_arp_service(self) -> bool:
        return self._capability_advertised(
            ExtendedCapability.PROXY_ARP_SERVICE)

    @property
    def utc_tsf_offset(self) -> bool:
        return self._capability_advertised(ExtendedCapability.UTC_TSF_OFFSET)

    @property
    def wnm_sleep_mode(self) -> bool:
        return self._capability_advertised(ExtendedCapability.WNM_SLEEP_MODE)

    # Other extended capability property methods can be added as needed by callers.
