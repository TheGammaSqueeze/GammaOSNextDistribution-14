#!/usr/bin/env python3
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
"""Abstract simulator for providing cellular handover functionality."""

from enum import Enum


class CellularTechnology(Enum):
    """A supported cellular technology to handover to/from."""
    LTE = 'LTE'
    WCDMA = 'WCDMA'
    NR5G_NSA = "NR5G_NSA"
    NR5G_SA = "NR5G_SA"


class AbstractHandoverSimulator:
    """Simulator for facilitating inter/intra-RAT handovers."""
    def nr5g_sa_handover(self, band, channel, bandwidth, source_technology):
        """Performs a handover to 5G SA.

        Args:
            band: the band of the handover destination
            channel: the downlink channel of the handover destination
            bandwidth: the downlink bandwidth of the handover destination
            source_technology: the source handover technology.
        """
        raise NotImplementedError()

    def nr5g_nsa_handover(self, band, channel, bandwidth, secondary_band,
                          secondary_channel, secondary_bandwidth,
                          source_technology):
        """Performs a handover to 5G NSA.

        Args:
            band: the band of the handover destination primary cell.
            channel: the downlink channel of the handover destination primary
                cell.
            bandwidth: the downlink bandwidth of the handover destination
                primary cell.
            secondary_band: the band of the handover destination secondary cell.
            secondary_channel: the downlink channel of the handover destination
                secondary cell.
            secondary_bandwidth: the downlink bandwidth of the handover
                destination secondary cell.
            source_technology: the source handover technology.
        """
        raise NotImplementedError()

    def lte_handover(self, band, channel, bandwidth, source_technology):
        """Performs a handover to LTE.

        Args:
            band: the band of the handover destination
            channel: the downlink channel of the handover destination
            bandwidth: the downlink bandwidth of the handover destination
            source_technology: the source handover technology.
        """
        raise NotImplementedError()

    def wcdma_handover(self, band, channel, source_technology):
        """Performs a handover to WCDMA.

        Args:
            band: the band of the handover destination
            channel: the downlink channel of the handover destination
            source_technology: the source handover technology.
        """
        raise NotImplementedError()


class HandoverSimulatorError(Exception):
    """Exceptions thrown when the cellular equipment is unable to successfully perform a handover operation."""
