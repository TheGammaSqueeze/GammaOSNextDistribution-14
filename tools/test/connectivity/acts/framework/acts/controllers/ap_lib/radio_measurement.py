#!/usr/bin/env python3
#
#   Copyright 2022 - The Android Open Source Project
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


@unique
class ApReachability(IntEnum):
    """Neighbor Report AP Reachability values.

    See IEEE 802.11-2020 Figure 9-172.
    """
    NOT_REACHABLE = 1
    UNKNOWN = 2
    REACHABLE = 3


class BssidInformationCapabilities:
    """Representation of Neighbor Report BSSID Information Capabilities.

    See IEEE 802.11-2020 Figure 9-338 and 9.4.1.4.
    """

    def __init__(self,
                 spectrum_management: bool = False,
                 qos: bool = False,
                 apsd: bool = False,
                 radio_measurement: bool = False):
        """Create a capabilities object.

        Args:
            spectrum_management: whether spectrum management is required.
            qos: whether QoS is implemented.
            apsd: whether APSD is implemented.
            radio_measurement: whether radio measurement is activated.
        """
        self._spectrum_management = spectrum_management
        self._qos = qos
        self._apsd = apsd
        self._radio_measurement = radio_measurement

    def __index__(self) -> int:
        """Convert to numeric representation of the field's bits."""
        return self.spectrum_management << 5 \
            | self.qos << 4 \
            | self.apsd << 3 \
            | self.radio_measurement << 2

    @property
    def spectrum_management(self) -> bool:
        return self._spectrum_management

    @property
    def qos(self) -> bool:
        return self._qos

    @property
    def apsd(self) -> bool:
        return self._apsd

    @property
    def radio_measurement(self) -> bool:
        return self._radio_measurement


class BssidInformation:
    """Representation of Neighbor Report BSSID Information field.

    BssidInformation contains info about a neighboring AP, to be included in a
    neighbor report element. See IEEE 802.11-2020 Figure 9-337.
    """

    def __init__(self,
                 ap_reachability: ApReachability = ApReachability.UNKNOWN,
                 security: bool = False,
                 key_scope: bool = False,
                 capabilities:
                 BssidInformationCapabilities = BssidInformationCapabilities(),
                 mobility_domain: bool = False,
                 high_throughput: bool = False,
                 very_high_throughput: bool = False,
                 ftm: bool = False):
        """Create a BSSID Information object for a neighboring AP.

        Args:
            ap_reachability: whether this AP is reachable by the STA that
                requested the neighbor report.
            security: whether this AP is known to support the same security
                provisioning as used by the STA in its current association.
            key_scope: whether this AP is known to have the same
                authenticator as the AP sending the report.
            capabilities: selected capabilities of this AP.
            mobility_domain: whether the AP is including an MDE in its beacon
                frames and the contents of that MDE are identical to the MDE
                advertised by the AP sending the report.
            high_throughput: whether the AP is an HT AP including the HT
                Capabilities element in its Beacons, and that the contents of
                that HT capabilities element are identical to the HT
                capabilities element advertised by the AP sending the report.
            very_high_throughput: whether the AP is a VHT AP and the VHT
                capabilities element, if included as a subelement, is
                identical in content to the VHT capabilities element included
                in the AP’s beacon.
            ftm: whether the AP is known to have the Fine Timing Measurement
                Responder extended capability.
        """
        self._ap_reachability = ap_reachability
        self._security = security
        self._key_scope = key_scope
        self._capabilities = capabilities
        self._mobility_domain = mobility_domain
        self._high_throughput = high_throughput
        self._very_high_throughput = very_high_throughput
        self._ftm = ftm

    def __index__(self) -> int:
        """Convert to numeric representation of the field's bits."""
        return self._ap_reachability << 30 \
            | self.security << 29 \
            | self.key_scope << 28 \
            | int(self.capabilities) << 22 \
            | self.mobility_domain << 21 \
            | self.high_throughput << 20 \
            | self.very_high_throughput << 19 \
            | self.ftm << 18

    @property
    def security(self) -> bool:
        return self._security

    @property
    def key_scope(self) -> bool:
        return self._key_scope

    @property
    def capabilities(self) -> BssidInformationCapabilities:
        return self._capabilities

    @property
    def mobility_domain(self) -> bool:
        return self._mobility_domain

    @property
    def high_throughput(self) -> bool:
        return self._high_throughput

    @property
    def very_high_throughput(self) -> bool:
        return self._very_high_throughput

    @property
    def ftm(self) -> bool:
        return self._ftm


@unique
class PhyType(IntEnum):
    """PHY type values, see dot11PhyType in 802.11-2020 Annex C."""
    DSSS = 2
    OFDM = 4
    HRDSS = 5
    ERP = 6
    HT = 7
    DMG = 8
    VHT = 9
    TVHT = 10
    S1G = 11
    CDMG = 12
    CMMG = 13


class NeighborReportElement:
    """Representation of Neighbor Report element.

    See IEEE 802.11-2020 9.4.2.36.
    """

    def __init__(self, bssid: str, bssid_information: BssidInformation,
                 operating_class: int, channel_number: int, phy_type: PhyType):
        """Create a neighbor report element.

        Args:
            bssid: MAC address of the neighbor.
            bssid_information: BSSID Information of the neigbor.
            operating_class: operating class of the neighbor.
            channel_number: channel number of the neighbor.
            phy_type: dot11PhyType of the neighbor.
        """
        self._bssid = bssid
        self._bssid_information = bssid_information

        # Operating Class, IEEE 802.11-2020 Annex E.
        self._operating_class = operating_class

        self._channel_number = channel_number

        # PHY Type, IEEE 802.11-2020 Annex C.
        self._phy_type = phy_type

    @property
    def bssid(self) -> str:
        return self._bssid

    @property
    def bssid_information(self) -> BssidInformation:
        return self._bssid_information

    @property
    def operating_class(self) -> int:
        return self._operating_class

    @property
    def channel_number(self) -> int:
        return self._channel_number

    @property
    def phy_type(self) -> PhyType:
        return self._phy_type
