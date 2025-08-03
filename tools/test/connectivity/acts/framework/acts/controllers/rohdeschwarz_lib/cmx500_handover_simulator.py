#!/usr/bin/env python3
#
#   Copyright 2023 - The Android Open Source Project
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
"""Provides cmx500 handover functionality."""

from acts.controllers import handover_simulator as hs
from acts import logger


class Cmx500HandoverSimulator(hs.AbstractHandoverSimulator):
    """Provides methods for performing inter/intra-RAT handovers."""
    def __init__(self, cmx):
        """Init method to setup handover controller.

        Args:
            cmx: the CMX500 instrument.
        """
        self.cmx = cmx
        self.log = logger.create_logger()

    def lte_handover(self, band, channel, bandwidth, source_technology):
        """Performs a handover to LTE.

        Args:
            band: the band of the handover destination.
            channel: the downlink channel of the handover destination.
            bandwidth: the downlink bandwidth of the handover destination.
            source_technology: the source handover technology.
        """
        new_primary = self._get_free_lte_cell(band, channel, bandwidth)
        if new_primary:
            self.cmx.handover(new_primary)

        elif source_technology == hs.CellularTechnology.LTE:
            if not self.cmx.primary_lte_cell:
                raise hs.HandoverSimulatorError(
                    'Unable to perform handover, source cell is not attached')
            self.log.warn('No new cell found, reconfiguring current cell')
            self._configure_cell(self.cmx.primary_lte_cell, band, channel,
                                 bandwidth)

        elif source_technology == hs.CellularTechnology.NR5G_NSA:
            # No free cell available, release scell and update LTE cell config.
            if not self.cmx.primary_nr_cell or not self.cmx.primary_lte_cell:
                raise hs.HandoverSimulatorError(
                    'Unable to perform handover, source cell is not attached')
            self.log.warn('No new cell found, releasing ENDC')
            self.cmx.dut.signaling.release_scg()
            self._configure_cell(self.cmx.primary_lte_cell, band, channel,
                                 bandwidth)

        else:
            raise hs.HandoverSimulatorError(
                'Unable to perform handover, invalid network configuration')

        self.cmx.turn_off_neighbor_cells()

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
        new_primary = self._get_free_lte_cell(band, channel, bandwidth)
        new_secondary = self._get_free_nr_cell(secondary_band,
                                               secondary_channel,
                                               secondary_bandwidth)
        if source_technology == hs.CellularTechnology.LTE:
            # Handover to LTE and activate ENDC since there's no secondary cell
            # mobility.
            if new_primary and new_secondary:
                self.cmx.handover(new_primary)
                new_secondary.activate_endc()
            # NR cell available in same TA so just activate endc.
            elif self.cmx.primary_nr_cell:
                self.log.warn('No new cell found, activating ENDC')
                self.cmx.primary_nr_cell.activate_endc()
            else:
                raise hs.HandoverSimulatorError(
                    'Cannot handover to 5G NSA, no available NR cell found')

        elif source_technology == hs.CellularTechnology.NR5G_NSA:
            if new_primary and new_secondary:
                self.cmx.handover(new_primary, new_secondary)
            # No new primary and secondary -> just reconfigure current cells.
            else:
                if self.cmx.primary_lte_cell and self.cmx.primary_nr_cell:
                    self.log.warn(
                        'No new cell found, reconfiguring current cells')
                    self._configure_cell(self.cmx.primary_lte_cell, band,
                                         channel, bandwidth)
                    self._configure_cell(self.cmx.primary_nr_cell,
                                         secondary_band, secondary_channel,
                                         secondary_bandwidth)
                else:
                    raise hs.HandoverSimulatorError(
                        'Unable to perform handover, source cell is not attached'
                    )
        else:
            raise hs.HandoverSimulatorError(
                'Handover from %s to %s is not supported' %
                (source_technology, hs.CellularTechnology.NR5G_NSA))

        self.cmx.turn_off_neighbor_cells()

    def nr5g_sa_handover(self, band, channel, bandwidth, source_technology):
        """Performs a handover to 5G SA.

        Args:
            band: the band of the handover destination.
            channel: the downlink channel of the handover destination.
            bandwidth: the downlink bandwidth of the handover destination.
            source_technology: the source handover technology.
        """
        new_primary = self._get_free_nr_cell(band, channel, bandwidth)
        if source_technology == hs.CellularTechnology.LTE:
            if not new_primary:
                raise hs.HandoverSimulatorError(
                    'Cannot handover to 5G SA, no available NR cell found')
            self.cmx.handover(new_primary)

        elif source_technology == hs.CellularTechnology.NR5G_SA:
            if new_primary:
                self.cmx.handover(new_primary)
            elif self.cmx.primary_nr_cell:
                self.log.warn('No new cell found, reconfiguring current cell')
                self._configure_cell(self.cmx.primary_nr_cell, band, channel,
                                     bandwidth)
            else:
                raise hs.HandoverSimulatorError(
                    'Cannot handover to 5G NSA, no available NR cell found')

        else:
            # Handover from NSA not supported.
            raise hs.HandoverSimulatorError(
                'Unable to perform handover, invalid network configuration')

        self.cmx.turn_off_neighbor_cells()

    def wcdma_handover(self, band, channel, source_technology):
        """Performs a handover to WCDMA.

        Args:
            band: the band of the handover destination.
            channel: the downlink channel of the handover destination.
            source_technology: the source handover technology.
        """
        raise NotImplementedError('WCDMA handovers not supported on CMX500')

    def _get_free_lte_cell(self, band, channel, bandwidth):
        """Gets a available LTE cell with the given configuration.

        Args:
            band: The band of cell.
            channel: The channel fo the cell.
            bandwidth: The bandwidth of the cell.
        """
        cell = next(iter(c for c in self.cmx.lte_cells if not c.is_active),
                    None)
        if not cell:
            self.log.warn('No free LTE cell found')
            return None

        self._configure_cell(cell, band, channel, bandwidth)
        self.cmx.set_bts_enabled(True, cell)
        return cell

    def _get_free_nr_cell(self, band, channel, bandwidth):
        """Gets a available NR cell with the given configuration.

        Args:
            band: The band of cell.
            channel: The channel fo the cell.
            bandwidth: The bandwidth of the cell.
        """
        cell = next(iter(c for c in self.cmx.nr_cells if not c.is_active),
                    None)
        if not cell:
            self.log.warn('No free NR cell found')
            return None

        self._configure_cell(cell, band, channel, bandwidth)
        self.cmx.set_bts_enabled(True, cell)
        return cell

    def _configure_cell(self, cell, band, channel, bandwidth):
        """Configures the cell with the requested frequency parameters.

        Note: Cells should ideally be initialized to the correct
        frequency/band combos since the CMX may not allow reconfiguration of the
        cell after starting the simulation depending on if "baseband-combining"
        is set.
        """
        cell.set_band(band)
        cell.set_dl_channel(channel)
        cell.set_bandwidth(bandwidth)
