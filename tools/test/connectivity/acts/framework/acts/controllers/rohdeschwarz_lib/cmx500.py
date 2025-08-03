#!/usr/bin/env python3
#
#   Copyright 2021 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#           http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

import logging
import time
import sys

from enum import Enum
from os import path
from acts.controllers import abstract_inst

DEFAULT_XLAPI_PATH = '/home/mobileharness/Rohde-Schwarz/XLAPI/latest/venv/lib/python3.7/site-packages'
DEFAULT_LTE_STATE_CHANGE_TIMER = 10
DEFAULT_CELL_SWITCH_ON_TIMER = 60
DEFAULT_ENDC_TIMER = 300

logger = logging.getLogger('Xlapi_cmx500')

LTE_CELL_PROPERTIES = [
    'band',
    'bandwidth',
    'dl_earfcn',
    'ul_earfcn',
    'total_dl_power',
    'p_b',
    'dl_epre',
    'ref_signal_power',
    'm',
    'beamforming_antenna_ports',
    'p0_nominal_pusch',
]

LTE_MHZ_UPPER_BOUND_TO_RB = [
    (1.5, 6),
    (4.0, 15),
    (7.5, 25),
    (12.5, 50),
    (17.5, 75),
]


class DciFormat(Enum):
    """Support DCI Formats for MIMOs."""
    DCI_FORMAT_0 = 1
    DCI_FORMAT_1 = 2
    DCI_FORMAT_1A = 3
    DCI_FORMAT_1B = 4
    DCI_FORMAT_1C = 5
    DCI_FORMAT_2 = 6
    DCI_FORMAT_2A = 7
    DCI_FORMAT_2B = 8
    DCI_FORMAT_2C = 9
    DCI_FORMAT_2D = 10


class DuplexMode(Enum):
    """Duplex Modes."""
    FDD = 'FDD'
    TDD = 'TDD'
    DL_ONLY = 'DL_ONLY'


class LteBandwidth(Enum):
    """Supported LTE bandwidths."""
    BANDWIDTH_1MHz = 6  # MHZ_1 is RB_6
    BANDWIDTH_3MHz = 15  # MHZ_3 is RB_15
    BANDWIDTH_5MHz = 25  # MHZ_5 is RB_25
    BANDWIDTH_10MHz = 50  # MHZ_10 is RB_50
    BANDWIDTH_15MHz = 75  # MHZ_15 is RB_75
    BANDWIDTH_20MHz = 100  # MHZ_20 is RB_100


class LteState(Enum):
    """LTE ON and OFF."""
    LTE_ON = 'ON'
    LTE_OFF = 'OFF'


class MimoModes(Enum):
    """MIMO Modes dl antennas."""
    MIMO1x1 = 1
    MIMO2x2 = 2
    MIMO4x4 = 4


class ModulationType(Enum):
    """Supported Modulation Types."""
    Q16 = 0
    Q64 = 1
    Q256 = 2


class NasState(Enum):
    """NAS state between callbox and dut."""
    DEREGISTERED = 'OFF'
    EMM_REGISTERED = 'EMM'
    MM5G_REGISTERED = 'NR'


class RrcState(Enum):
    """States to enable/disable rrc."""
    RRC_ON = 'ON'
    RRC_OFF = 'OFF'


class RrcConnectionState(Enum):
    """RRC Connection states, describes possible DUT RRC connection states."""
    IDLE = 1
    IDLE_PAGING = 2
    IDLE_CONNECTION_ESTABLISHMENT = 3
    CONNECTED = 4
    CONNECTED_CONNECTION_REESTABLISHMENT = 5
    CONNECTED_SCG_FAILURE = 6
    CONNECTED_HANDOVER = 7
    CONNECTED_CONNECTION_RELEASE = 8


class SchedulingMode(Enum):
    """Supported scheduling modes."""
    USERDEFINEDCH = 'UDCHannels'


class TransmissionModes(Enum):
    """Supported transmission modes."""
    TM1 = 1
    TM2 = 2
    TM3 = 3
    TM4 = 4
    TM7 = 7
    TM8 = 8
    TM9 = 9


# For mimo 1x1, also set_num_crs_antenna_ports to 1
MIMO_MAX_LAYER_MAPPING = {
    MimoModes.MIMO1x1: 2,
    MimoModes.MIMO2x2: 2,
    MimoModes.MIMO4x4: 4,
}


class Cmx500(abstract_inst.SocketInstrument):
    def __init__(self, ip_addr, port, xlapi_path=DEFAULT_XLAPI_PATH):
        """Init method to setup variables for the controller.

        Args:
              ip_addr: Controller's ip address.
              port: Port.
        """

        # keeps the socket connection for debug purpose for now
        super().__init__(ip_addr, port)
        if not xlapi_path in sys.path:
            sys.path.insert(0, xlapi_path)
        self._initial_xlapi()
        self._settings.system.set_instrument_address(ip_addr)
        logger.info('The instrument address is {}'.format(
            self._settings.system.get_instrument_address()))

        self.bts = []

        # Stops all active cells if there is any
        self.clear_network()

        # initialize one lte and nr cell
        self._add_lte_cell()
        self._add_nr_cell()

        self.lte_rrc_state_change_timer = DEFAULT_LTE_STATE_CHANGE_TIMER
        self.rrc_state_change_time_enable = False
        self.cell_switch_on_timer = DEFAULT_CELL_SWITCH_ON_TIMER

    def set_band_combination(self, bands):
        """ Prepares the test equipment for the indicated band/mimo combination.

        Args:
            bands: a list of bands represented as ints or strings
            mimo_modes: a list of LteSimulation.MimoMode to use for each carrier
        """
        self.clear_network()

        n_lte_cells = 1
        n_nr_cells = 1
        for band in bands:
            if isinstance(band, str) and band[0] == 'n':
                nr_bts = self._add_nr_cell()
                nr_bts.set_band(int(band[1:]))
                # set secondary cells to dl only to avoid running out of
                # resources in high CA scenarios
                if n_nr_cells > 2:
                    nr_bts.set_dl_only(True)
                n_nr_cells += 1
            else:
                lte_bts = self._add_lte_cell()
                lte_bts.set_band(int(band))
                if n_lte_cells > 2:
                    lte_bts.set_dl_only(True)
                n_lte_cells += 1

        # turn on primary lte and/or nr cells
        self._network.apply_changes()
        self.turn_on_primary_cells()

    def _add_nr_cell(self):
        """Creates a new NR cell and configures antenna ports."""
        from mrtype.counters import N310
        nr_cell = self._network.create_nr_cell()
        nr_cell_max_config = nr_cell.stub.GetMaximumConfiguration()
        nr_cell_max_config.csi_rs_antenna_ports = self.MAX_CSI_RS_PORTS
        nr_cell.stub.SetMaximumConfiguration(nr_cell_max_config)
        # Sets n310 timer to N310.N20 to make endc more stable
        nr_cell.set_n310(N310.N20)

        nr_bts = self.create_base_station(nr_cell)
        self.bts.append(nr_bts)
        return nr_bts

    def _add_lte_cell(self):
        """Creates a new LTE cell and configures antenna ports."""
        lte_cell = self._network.create_lte_cell()
        lte_cell_max_config = lte_cell.stub.GetMaximumConfiguration()
        lte_cell_max_config.csi_rs_antenna_ports = self.MAX_CSI_RS_PORTS
        lte_cell_max_config.crs_antenna_ports = self.MAX_CRS_PORTS
        lte_cell.stub.SetMaximumConfiguration(lte_cell_max_config)
        lte_bts = self.create_base_station(lte_cell)

        self.bts.append(lte_bts)
        return lte_bts

    def _initial_xlapi(self):
        import xlapi
        import mrtype
        from xlapi import network
        from xlapi import settings
        from rs_mrt.testenvironment.signaling.sri.rat.common import (
            CsiRsAntennaPorts)
        from rs_mrt.testenvironment.signaling.sri.rat.lte import CrsAntennaPorts

        self._xlapi = xlapi
        self._network = network
        self._settings = settings

        # initialize defaults
        self.MAX_CSI_RS_PORTS = (
            CsiRsAntennaPorts.NUMBER_CSI_RS_ANTENNA_PORTS_FOUR)
        self.MAX_CRS_PORTS = CrsAntennaPorts.NUMBER_CRS_ANTENNA_PORTS_FOUR

    def configure_mimo_settings(self, mimo, bts_index=0):
        """Sets the mimo scenario for the test.

        Args:
            mimo: mimo scenario to set.
        """
        self.bts[bts_index].set_mimo_mode(mimo)

    @property
    def connection_type(self):
        """Gets the connection type applied in callbox."""
        state = self.dut.state.rrc_connection_state
        return RrcConnectionState(state.value)

    def create_base_station(self, cell):
        """Creates the base station object with cell and current object.

        Args:
            cell: the XLAPI cell.

        Returns:
            base station object.
        Raise:
            CmxError if the cell is neither LTE nor NR.
        """
        from xlapi.lte_cell import LteCell
        from xlapi.nr_cell import NrCell
        if isinstance(cell, LteCell):
            return LteBaseStation(self, cell)
        elif isinstance(cell, NrCell):
            return NrBaseStation(self, cell)
        else:
            raise CmxError('The cell type is neither LTE nor NR')

    def detach(self):
        """Detach callbox and controller."""
        for bts in self.bts:
            bts.stop()

    def disable_packet_switching(self):
        """Disable packet switching in call box."""
        raise NotImplementedError()

    def clear_network(self):
        """Wipes the network configuration."""
        self.bts.clear()
        self._network.reset()
        # dut stub becomes stale after resetting
        self.dut = self._network.get_dut()

    def disconnect(self):
        """Disconnect controller from device and switch to local mode."""
        self.clear_network()
        self._close_socket()

    def enable_packet_switching(self):
        """Enable packet switching in call box."""
        raise NotImplementedError()

    def get_cell_configs(self, cell):
        """Getes cell settings.

        This method is for debugging purpose. In XLAPI, there are many get
        methods in the cell or component carrier object. When this method is
        called, the corresponding value could be recorded with logging, which is
        useful for debug.

        Args:
            cell: the lte or nr cell in xlapi
        """
        cell_getters = [attr for attr in dir(cell) if attr.startswith('get')]
        for attr in cell_getters:
            try:
                getter = getattr(cell, attr)
                logger.info('The {} is {}'.format(attr, getter()))
            except Exception as e:
                logger.warning('Error in get {}: {}'.format(attr, e))

    def get_base_station(self, bts_index=0):
        """Gets the base station object based on bts num. By default
        bts_index set to 0 (PCC).

        Args:
            bts_num: base station identifier

        Returns:
            base station object.
        """
        return self.bts[bts_index]

    def get_network(self):
        """ Gets the network object from cmx500 object."""
        return self._network

    def init_lte_measurement(self):
        """Gets the class object for lte measurement which can be used to
        initiate measurements.

        Returns:
            lte measurement object.
        """
        raise NotImplementedError()

    def network_apply_changes(self):
        """Uses self._network to apply changes"""
        self._network.apply_changes()

    def reset(self):
        """System level reset."""

        self.disconnect()

    @property
    def rrc_connection(self):
        """Gets the RRC connection state."""
        return self.dut.state.rrc.is_connected

    def set_timer(self, timeout):
        """Sets timer for the Cmx500 class."""
        self.rrc_state_change_time_enable = True
        self.lte_rrc_state_change_timer = timeout

    def switch_lte_signalling(self, state):
        """ Turns LTE signalling ON/OFF.

        Args:
            state: an instance of LteState indicating the state to which LTE
                   signal has to be set.
        """
        if not isinstance(state, LteState):
            raise ValueError('state should be the instance of LteState.')

        if self.primary_lte_cell:
            self.set_bts_enabled(state.value == 'ON', self.primary_lte_cell)
        else:
            raise CmxError(
                'Unable to set LTE signalling to {},'.format(state.value) +
                ' no LTE cell found.')

    def switch_on_nsa_signalling(self):
        logger.info('Switches on NSA signalling')

        if self.primary_lte_cell:
            self.set_bts_enabled(True, self.primary_lte_cell)
        else:
            raise CmxError(
                'Unable to turn on NSA signalling, no LTE cell found.')

        if self.primary_nr_cell:
            self.set_bts_enabled(True, self.primary_nr_cell)
        else:
            raise CmxError(
                'Unable to turn on NSA signalling, no NR cell found.')

        time.sleep(5)

    @property
    def primary_cell(self):
        """Gets the primary cell in the current scenario."""
        # If the simulation has an active cell return it as the primary
        # otherwise default to the first cell.
        for cell in self.bts:
            if cell.is_primary_cell:
                return cell

        return self.bts[0] if self.bts else None

    @property
    def primary_lte_cell(self):
        """Gets the primary LTE cell in the current scenario.

        Note: This should generally be the same as primary_cell unless we're
        connected to a NR cell in a TA with both LTE and NR cells.
        """
        cell = self.primary_cell
        if isinstance(cell, LteBaseStation):
            return cell

        # find the first cell in the same ta as the primary cell
        elif cell:
            cells = self._get_cells_in_same_ta(cell)
            return next((c for c in cells if isinstance(c, LteBaseStation)),
                        None)

        return None

    @property
    def primary_nr_cell(self):
        """Gets the primary NR cell in the current scenario.

        Note: This may be the PSCell for NSA scenarios.
        """
        cell = self.primary_cell
        if cell and isinstance(cell, NrBaseStation):
            return cell

        elif cell:
            cells = self._get_cells_in_same_ta(cell)
            return next((c for c in cells if isinstance(c, NrBaseStation)),
                        None)

        return None

    @property
    def lte_cells(self):
        """Gets all LTE cells in the current scenario."""
        return list(
            [bts for bts in self.bts if isinstance(bts, LteBaseStation)])

    @property
    def nr_cells(self):
        """Gets all NR cells in the current scenario."""
        return list(
            [bts for bts in self.bts if isinstance(bts, NrBaseStation)])

    @property
    def secondary_lte_cells(self):
        """Gets a list of all LTE cells in the same TA as the primary cell."""
        pcell = self.primary_cell
        if not pcell or not self.lte_cells:
            return []

        # If NR cell is primary then there are no secondary LTE cells.
        if not isinstance(pcell, LteBaseStation):
            return []

        return [
            c for c in self._get_cells_in_same_ta(pcell)
            if isinstance(c, LteBaseStation)
        ]

    @property
    def secondary_nr_cells(self):
        """Gets a list of all NR cells in the same TA as the primary cell."""
        pcell = self.primary_nr_cell
        if not pcell or not self.nr_cells:
            return []

        return [
            c for c in self._get_cells_in_same_ta(pcell)
            if isinstance(c, NrBaseStation)
        ]

    @property
    def secondary_cells(self):
        """Gets all secondary cells in the current scenario."""
        return self.secondary_lte_cells + self.secondary_nr_cells

    @property
    def tracking_areas(self):
        """Returns a list of LTE and 5G tracking areas in the simulation."""
        plmn = self._network.get_or_create_plmn()
        return plmn.eps_ta_list + plmn.fivegs_ta_list

    def _get_cells_in_same_ta(self, bts):
        """Returns a list of all cells in the same TA."""
        tracking_area = next(t for t in self.tracking_areas
                             if bts._cell in t.cells)
        return [
            c for c in self.bts if c != bts and c._cell in tracking_area.cells
        ]

    def set_bts_enabled(self, enabled, bts):
        """Switch bts signalling on/off.

        Args:
            enabled: True/False if the  signalling should be enabled.
            bts: The bts to configure.
        """
        if enabled and not bts.is_on():
            bts.start()
            state = bts.wait_cell_enabled(self.cell_switch_on_timer, True)
            if state:
                logger.info('The cell status is on')
            else:
                raise CmxError('The cell cannot be switched on')

        elif not enabled and bts.is_on():
            bts.stop()
            state = bts.wait_cell_enabled(self.cell_switch_on_timer, False)
            if not state:
                logger.info('The cell status is off')
            else:
                raise CmxError('The cell cannot be switched off')

    @property
    def use_carrier_specific(self):
        """Gets current status of carrier specific duplex configuration."""
        raise NotImplementedError()

    @use_carrier_specific.setter
    def use_carrier_specific(self, state):
        """Sets the carrier specific duplex configuration.

        Args:
            state: ON/OFF UCS configuration.
        """
        raise NotImplementedError()

    def set_keep_rrc(self, keep_connected):
        """Sets if the CMX should maintain RRC connection after registration.

        Args:
            keep_connected: true if cmx should stay in RRC_CONNECTED state or
                false if UE preference should be used.
        """
        state = 'ON' if keep_connected else 'OFF'
        self._send('CONFigure:SIGNaling:EPS:NBEHavior:KRRC {}'.format(state))
        self._send('CONFigure:SIGNaling:FGS:NBEHavior:KRRC {}'.format(state))

    def turn_off_neighbor_cells(self):
        """Turns off all cells in other tracking areas."""
        for cell in self.bts:
            if not cell.is_active:
                self.set_bts_enabled(False, cell)

    def turn_on_primary_cells(self):
        """Turns on all primary cells."""
        if self.primary_lte_cell:
            self.set_bts_enabled(True, self.primary_lte_cell)
        if self.primary_nr_cell:
            self.set_bts_enabled(True, self.primary_nr_cell)

    def turn_on_secondary_cells(self):
        """Turns on all secondary cells."""
        for bts in self.secondary_cells:
            self.set_bts_enabled(True, bts)

    def handover(self, primary, secondary=None):
        """Performs a Inter/Intra-RAT handover.

        Args:
            primary: the new primary bts.
            secondary: the new secondary bts.
        """
        self.dut.signaling.handover_to(primary.cell,
                                       secondary.cell if secondary else None,
                                       None)

    def wait_for_rrc_state(self, state, timeout=120):
        """ Waits until a certain RRC state is set.

        Args:
            state: the RRC state that is being waited for.
            timeout: timeout for phone to be in connected state.

        Raises:
            CmxError on time out.
        """
        is_idle = (state.value == 'OFF')
        for idx in range(timeout):
            time.sleep(1)
            if self.dut.state.rrc.is_idle == is_idle:
                logger.info('{} reached at {} s'.format(state.value, idx))
                return True
        error_message = 'Waiting for {} state timeout after {}'.format(
            state.value, timeout)
        logger.error(error_message)
        raise CmxError(error_message)

    def wait_until_attached(self, timeout=120):
        """Waits until Lte attached.

        Args:
            timeout: timeout for phone to get attached.

        Raises:
            CmxError on time out.
        """
        if not self.bts:
            raise CmxError('cannot attach device, no cells found')

        self.primary_cell.wait_for_attach(timeout)

    def send_sms(self, message):
        """ Sends an SMS message to the DUT.

        Args:
            message: the SMS message to send.
        """
        self.dut.signaling.mt_sms(message)


class BaseStation(object):
    """Class to interact with different the base stations."""
    def __init__(self, cmx, cell):
        """Init method to setup variables for base station.

        Args:
            cmx: Controller (Cmx500) object.
            cell: The cell for the base station.
        """

        self._cell = cell
        self._cmx = cmx
        self._cc = cmx.dut.cc(cell)
        self._network = cmx.get_network()

    @property
    def band(self):
        """Gets the current band of cell.

        Return:
            the band number in int.
        """
        cell_band = self._cell.get_band()
        return int(cell_band)

    @property
    def dl_power(self):
        """Gets RSPRE level.

        Return:
            the power level in dbm.
        """
        return self._cell.get_total_dl_power().in_dBm()

    @property
    def duplex_mode(self):
        """Gets current duplex of cell."""
        band = self._cell.get_band()
        if band.is_fdd():
            return DuplexMode.FDD
        if band.is_tdd():
            return DuplexMode.TDD
        if band.is_dl_only():
            return DuplexMode.DL_ONLY

    def get_cc(self):
        """Gets component carrier of the cell."""
        return self._cc

    def is_on(self):
        """Verifies if the cell is turned on.

            Return:
                boolean (if the cell is on).
        """
        return self._cell.is_on()

    def set_band(self, band):
        """Sets the Band of cell.

        Args:
            band: band of cell.
        """
        self._cell.set_band(band)
        logger.info('The band is set to {} and is {} after setting'.format(
            band, self.band))

    def set_dl_mac_padding(self, state):
        """Enables/Disables downlink padding at the mac layer.

        Args:
            state: a boolean
        """
        self._cc.set_dl_mac_padding(state)

    def set_dl_power(self, pwlevel):
        """Modifies RSPRE level.

        Args:
            pwlevel: power level in dBm.
        """
        self._cell.set_total_dl_power(pwlevel)

    def set_ul_power(self, ul_power):
        """Sets ul power

        Args:
            ul_power: the uplink power in dbm
        """
        self._cc.set_target_ul_power(ul_power)

    def set_dl_only(self, dl_only):
        """Sets if the cell should be in downlink only mode.

        Args:
            dl_only: bool indicating if the cell should be downlink only.
        """
        self._cell.dl_only = dl_only

    def start(self):
        """Starts the cell."""
        self._cell.start()

    def stop(self):
        """Stops the cell."""
        self._cell.stop()

    def wait_cell_enabled(self, timeout, enabled):
        """Waits for the requested cell state.

        Args:
            timeout: the time for waiting the cell on.
            enabled: true if the cell should be enabled.

        Raises:
            CmxError on time out.
        """
        waiting_time = 0
        while waiting_time < timeout:
            if self._cell.is_on() == enabled:
                return enabled
            waiting_time += 1
            time.sleep(1)
        return self._cell.is_on()

    def set_tracking_area(self, tac):
        """Sets the cells tracking area.

        Args:
            tac: the tracking area ID to add the cell to.
        """
        plmn = self._network.get_or_create_plmn()
        old_ta = next((t for t in (plmn.eps_ta_list + plmn.fivegs_ta_list)
                       if self._cell in t.cells))
        new_ta = next((t for t in (plmn.eps_ta_list + plmn.fivegs_ta_list)
                       if t.tac == tac), None)
        if not new_ta:
            new_ta = self.create_new_ta(tac)

        if old_ta == new_ta:
            return

        new_ta.add_cell(self._cell)
        old_ta.remove_cell(self._cell)

    @property
    def is_primary_cell(self):
        """Returns true if the cell is the current primary cell."""
        return self._cell == self._cmx.dut.state.pcell

    @property
    def is_active(self):
        """Returns true if the cell is part of the current simulation.

        A cell is considered "active" if it is the primary cell
        or is in the same TA as the primary cell, i.e. not a neighbor cell.
        """
        return (self == self._cmx.primary_lte_cell
                or self == self._cmx.primary_nr_cell
                or self in self._cmx.secondary_cells)

    @property
    def cell(self):
        """Returns the underlying XLAPI cell object."""
        return self._cell


class LteBaseStation(BaseStation):
    """ LTE base station."""
    def __init__(self, cmx, cell):
        """Init method to setup variables for the LTE base station.

        Args:
            cmx: Controller (Cmx500) object.
            cell: The cell for the LTE base station.
        """
        from xlapi.lte_cell import LteCell
        if not isinstance(cell, LteCell):
            raise CmxError(
                'The cell is not a LTE cell, LTE base station  fails'
                ' to create.')
        super().__init__(cmx, cell)

    def _config_scheduler(self,
                          dl_mcs=None,
                          dl_rb_alloc=None,
                          dl_dci_ncce=None,
                          dl_dci_format=None,
                          dl_tm=None,
                          dl_num_layers=None,
                          dl_mcs_table=None,
                          ul_mcs=None,
                          ul_rb_alloc=None,
                          ul_dci_ncce=None):

        from rs_mrt.testenvironment.signaling.sri.rat.lte import DciFormat
        from rs_mrt.testenvironment.signaling.sri.rat.lte import DlTransmissionMode
        from rs_mrt.testenvironment.signaling.sri.rat.lte import MaxLayersMIMO
        from rs_mrt.testenvironment.signaling.sri.rat.lte import McsTable
        from rs_mrt.testenvironment.signaling.sri.rat.lte import PdcchFormat

        log_list = []
        if dl_mcs:
            log_list.append('dl_mcs: {}'.format(dl_mcs))
        if ul_mcs:
            log_list.append('ul_mcs: {}'.format(ul_mcs))
        if dl_rb_alloc:
            if not isinstance(dl_rb_alloc, tuple):
                dl_rb_alloc = (0, dl_rb_alloc)
            log_list.append('dl_rb_alloc: {}'.format(dl_rb_alloc))
        if ul_rb_alloc:
            if not isinstance(ul_rb_alloc, tuple):
                ul_rb_alloc = (0, ul_rb_alloc)
            log_list.append('ul_rb_alloc: {}'.format(ul_rb_alloc))
        if dl_dci_ncce:
            dl_dci_ncce = PdcchFormat(dl_dci_ncce)
            log_list.append('dl_dci_ncce: {}'.format(dl_dci_ncce))
        if ul_dci_ncce:
            ul_dci_ncce = PdcchFormat(ul_dci_ncce)
            log_list.append('ul_dci_ncce: {}'.format(ul_dci_ncce))
        if dl_dci_format:
            dl_dci_format = DciFormat(dl_dci_format)
            log_list.append('dl_dci_format: {}'.format(dl_dci_format))
        if dl_tm:
            dl_tm = DlTransmissionMode(dl_tm.value)
            log_list.append('dl_tm: {}'.format(dl_tm))
        if dl_num_layers:
            dl_num_layers = MaxLayersMIMO(dl_num_layers)
            log_list.append('dl_num_layers: {}'.format(dl_num_layers))
        if dl_mcs_table:
            dl_mcs_table = McsTable(dl_mcs_table)
            log_list.append('dl_mcs_table: {}'.format(dl_mcs_table))

        num_crs_antenna_ports = self._cell.get_num_crs_antenna_ports()

        # Sets num of crs antenna ports to 4 for configuring
        self._cell.set_num_crs_antenna_ports(4)
        scheduler = self._cmx.dut.get_scheduler(self._cell)
        logger.info('configure scheduler for {}'.format(','.join(log_list)))
        scheduler.configure_scheduler(dl_mcs=dl_mcs,
                                      dl_rb_alloc=dl_rb_alloc,
                                      dl_dci_ncce=dl_dci_ncce,
                                      dl_dci_format=dl_dci_format,
                                      dl_tm=dl_tm,
                                      dl_num_layers=dl_num_layers,
                                      dl_mcs_table=dl_mcs_table,
                                      ul_mcs=ul_mcs,
                                      ul_rb_alloc=ul_rb_alloc,
                                      ul_dci_ncce=ul_dci_ncce)
        logger.info('Configure scheduler succeeds')

        # Sets num of crs antenna ports back to previous value
        self._cell.set_num_crs_antenna_ports(num_crs_antenna_ports)
        self._network.apply_changes()

    @property
    def bandwidth(self):
        """Get the channel bandwidth of the cell.

        Return:
            the number rb of the bandwidth.
        """
        return self._cell.get_bandwidth().num_rb

    @property
    def dl_channel(self):
        """Gets the downlink channel of cell.

        Return:
            the downlink channel (earfcn) in int.
        """
        return int(self._cell.get_dl_earfcn())

    @property
    def dl_frequency(self):
        """Get the downlink frequency of the cell."""
        from mrtype.frequency import Frequency
        return self._cell.get_dl_earfcn().to_freq().in_units(
            Frequency.Units.GHz)

    def _to_rb_bandwidth(self, bandwidth):
        for idx in range(5):
            if bandwidth < LTE_MHZ_UPPER_BOUND_TO_RB[idx][0]:
                return LTE_MHZ_UPPER_BOUND_TO_RB[idx][1]
        return 100

    def disable_all_ul_subframes(self):
        """Disables all ul subframes for LTE cell."""
        self._cc.disable_all_ul_subframes()
        self._network.apply_changes()
        logger.info('lte cell disable all ul subframes completed')

    def set_bandwidth(self, bandwidth):
        """Sets the channel bandwidth of the cell.

        Args:
            bandwidth: channel bandwidth of cell in MHz.
        """
        self._cell.set_bandwidth(self._to_rb_bandwidth(bandwidth))
        self._network.apply_changes()

    def set_cdrx_config(self, config):
        """Configures LTE cdrx with lte config parameters.

        config: The LteCellConfig for current base station.
        """

        logger.info(
            f'Configure Lte drx with\n'
            f'drx_on_duration_timer: {config.drx_on_duration_timer}\n'
            f'drx_inactivity_timer: {config.drx_inactivity_timer}\n'
            f'drx_retransmission_timer: {config.drx_retransmission_timer}\n'
            f'drx_long_cycle: {config.drx_long_cycle}\n'
            f'drx_long_cycle_offset: {config.drx_long_cycle_offset}'
        )

        from mrtype.lte.drx import (
            LteDrxConfig,
            LteDrxInactivityTimer,
            LteDrxOnDurationTimer,
            LteDrxRetransmissionTimer,
        )

        from mrtype.lte.drx import LteDrxLongCycleStartOffset as longCycle

        long_cycle_mapping = {
            10: longCycle.ms10, 20: longCycle.ms20, 32: longCycle.ms32,
            40: longCycle.ms40, 60: longCycle.ms60, 64: longCycle.ms64,
            70: longCycle.ms70, 80: longCycle.ms80, 128: longCycle.ms128,
            160: longCycle.ms160, 256: longCycle.ms256, 320: longCycle.ms320,
            512: longCycle.ms512, 640: longCycle.ms640, 1280: longCycle.ms1280,
            2048: longCycle.ms2048, 2560: longCycle.ms2560
        }

        drx_on_duration_timer = LteDrxOnDurationTimer(
            int(config.drx_on_duration_timer)
        )
        drx_inactivity_timer = LteDrxInactivityTimer(
            int(config.drx_inactivity_timer)
        )
        drx_retransmission_timer = LteDrxRetransmissionTimer(
            int(config.drx_retransmission_timer)
        )
        drx_long_cycle = long_cycle_mapping[int(config.drx_long_cycle)]
        drx_long_cycle_offset = drx_long_cycle(config.drx_long_cycle_offset)

        lte_drx_config = LteDrxConfig(
            on_duration_timer=drx_on_duration_timer,
            inactivity_timer=drx_inactivity_timer,
            retransmission_timer=drx_retransmission_timer,
            long_cycle_start_offset=drx_long_cycle_offset,
            short_drx=None
        )

        self._cmx.dut.lte_cell_group().set_drx_and_adjust_scheduler(
            drx_config=lte_drx_config
        )
        self._network.apply_changes()

    def set_default_cdrx_config(self):
        """Sets default LTE cdrx config for endc (for legacy code)."""
        from mrtype.lte.drx import (
            LteDrxConfig,
            LteDrxInactivityTimer,
            LteDrxLongCycleStartOffset,
            LteDrxOnDurationTimer,
            LteDrxRetransmissionTimer,
        )

        logger.info('Config Lte drx config')
        lte_drx_config = LteDrxConfig(
            on_duration_timer=LteDrxOnDurationTimer.PSF_10,
            inactivity_timer=LteDrxInactivityTimer.PSF_200,
            retransmission_timer=LteDrxRetransmissionTimer.PSF_33,
            long_cycle_start_offset=LteDrxLongCycleStartOffset.ms160(83),
            short_drx=None)
        self._cmx.dut.lte_cell_group().set_drx_and_adjust_scheduler(
            drx_config=lte_drx_config)
        self._network.apply_changes()

    def set_cell_frequency_band(self, tdd_cfg=None, ssf_cfg=None):
        """Sets cell frequency band with tdd and ssf config.

        Args:
            tdd_cfg: the tdd subframe assignment config in number (from 0-6).
            ssf_cfg: the special subframe pattern config in number (from 1-9).
        """
        from rs_mrt.testenvironment.signaling.sri.rat.lte import SpecialSubframePattern
        from rs_mrt.testenvironment.signaling.sri.rat.lte import SubFrameAssignment
        from rs_mrt.testenvironment.signaling.sri.rat.lte.config import CellFrequencyBand
        from rs_mrt.testenvironment.signaling.sri.rat.lte.config import Tdd
        tdd_subframe = None
        ssf_pattern = None
        if tdd_cfg:
            tdd_subframe = SubFrameAssignment(tdd_cfg + 1)
        if ssf_cfg:
            ssf_pattern = SpecialSubframePattern(ssf_cfg)
        tdd = Tdd(tdd_config=Tdd.TddConfigSignaling(
            subframe_assignment=tdd_subframe,
            special_subframe_pattern=ssf_pattern))
        self._cell.stub.SetCellFrequencyBand(CellFrequencyBand(tdd=tdd))
        self._network.apply_changes()

    def set_cfi(self, cfi):
        """Sets number of pdcch symbols (cfi).

        Args:
            cfi: the value of NumberOfPdcchSymbols
        """
        from rs_mrt.testenvironment.signaling.sri.rat.lte import NumberOfPdcchSymbols
        from rs_mrt.testenvironment.signaling.sri.rat.lte.config import PdcchRegionReq

        logger.info('The cfi enum to set is {}'.format(
            NumberOfPdcchSymbols(cfi)))
        req = PdcchRegionReq()
        req.num_pdcch_symbols = NumberOfPdcchSymbols(cfi)
        self._cell.stub.SetPdcchControlRegion(req)

    def set_dci_format(self, dci_format):
        """Selects the downlink control information (DCI) format.

        Args:
            dci_format: supported dci.
        """
        if not isinstance(dci_format, DciFormat):
            raise CmxError('Wrong type for dci_format')
        self._config_scheduler(dl_dci_format=dci_format.value)

    def set_dl_channel(self, channel):
        """Sets the downlink channel number of cell.

        Args:
            channel: downlink channel number of cell.
        """
        if self.dl_channel == channel:
            logger.info('The dl_channel was at {}'.format(self.dl_channel))
            return
        self._cell.set_earfcn(channel)
        logger.info('The dl_channel was set to {}'.format(self.dl_channel))

    def set_dl_modulation_table(self, modulation):
        """Sets down link modulation table.

        Args:
            modulation: modulation table setting (ModulationType).
        """
        if not isinstance(modulation, ModulationType):
            raise CmxError('The modulation is not the type of Modulation')
        self._config_scheduler(dl_mcs_table=modulation.value)

    def set_mimo_mode(self, mimo):
        """Sets mimo mode for Lte scenario.

        Args:
            mimo: the mimo mode.
        """
        if not isinstance(mimo, MimoModes):
            raise CmxError("Wrong type of mimo mode")

        self._cell.set_num_crs_antenna_ports(mimo.value)

    def set_scheduling_mode(self,
                            mcs_dl=None,
                            mcs_ul=None,
                            nrb_dl=None,
                            nrb_ul=None):
        """Sets scheduling mode.

        Args:
            scheduling: the new scheduling mode.
            mcs_dl: Downlink MCS.
            mcs_ul: Uplink MCS.
            nrb_dl: Number of RBs for downlink.
            nrb_ul: Number of RBs for uplink.
        """
        self._config_scheduler(dl_mcs=mcs_dl,
                               ul_mcs=mcs_ul,
                               dl_rb_alloc=nrb_dl,
                               ul_rb_alloc=nrb_ul)

    def set_ssf_config(self, ssf_config):
        """Sets ssf subframe assignment with tdd_config.

        Args:
            ssf_config: the special subframe pattern config (from 1-9).
        """
        self.set_cell_frequency_band(ssf_cfg=ssf_config)

    def set_tdd_config(self, tdd_config):
        """Sets tdd subframe assignment with tdd_config.

        Args:
            tdd_config: the subframe assignemnt config (from 0-6).
        """
        self.set_cell_frequency_band(tdd_cfg=tdd_config)

    def set_transmission_mode(self, transmission_mode):
        """Sets transmission mode with schedular.

        Args:
            transmission_mode: the download link transmission mode.
        """
        from rs_mrt.testenvironment.signaling.sri.rat.lte import DlTransmissionMode
        if not isinstance(transmission_mode, TransmissionModes):
            raise CmxError('Wrong type of the trasmission mode')
        dl_tm = DlTransmissionMode(transmission_mode.value)
        logger.info('set dl tm to {}'.format(dl_tm))
        self._cc.set_dl_tm(dl_tm)
        self._network.apply_changes()

    def set_ul_channel(self, channel):
        """Sets the up link channel number of cell.

        Args:
            channel: up link channel number of cell.
        """
        if self.ul_channel == channel:
            logger.info('The ul_channel is at {}'.format(self.ul_channel))
            return
        self._cell.set_earfcn(channel)
        logger.info('The dl_channel was set to {}'.format(self.ul_channel))

    @property
    def ul_channel(self):
        """Gets the uplink channel of cell.

        Return:
            the uplink channel (earfcn) in int
        """
        return int(self._cell.get_ul_earfcn())

    @property
    def ul_frequency(self):
        """Get the uplink frequency of the cell.

        Return:
            The uplink frequency in GHz.
        """
        from mrtype.frequency import Frequency
        return self._cell.get_ul_earfcn().to_freq().in_units(
            Frequency.Units.GHz)

    def set_ul_modulation_table(self, modulation):
        """Sets up link modulation table.

        Args:
            modulation: modulation table setting (ModulationType).
        """
        if not isinstance(modulation, ModulationType):
            raise CmxError('The modulation is not the type of Modulation')
        if modulation == ModulationType.Q16:
            self._cell.stub.SetPuschCommonConfig(False)
        else:
            self._cell.stub.SetPuschCommonConfig(True)

    def create_new_ta(self, tac):
        """Creates and initializes a new tracking area for this cell.

        Args:
            tac: the tracking area ID of the new cell.
        Returns:
            ta: the new tracking area.
        """
        plmn = self._network.get_or_create_plmn()
        return plmn.create_eps_ta(tac)

    def wait_for_attach(self, timeout):
        self._cmx.dut.signaling.wait_for_lte_attach(self._cell, timeout)

    def attach_as_secondary_cell(self):
        """Attach this cell as a secondary cell."""
        self._cmx.dut.signaling.ca_add_scell(self._cell)


class NrBaseStation(BaseStation):
    """ NR base station."""
    def __init__(self, cmx, cell):
        """Init method to setup variables for the NR base station.

        Args:
            cmx: Controller (Cmx500) object.
            cell: The cell for the NR base station.
        """
        from xlapi.nr_cell import NrCell
        if not isinstance(cell, NrCell):
            raise CmxError('the cell is not a NR cell, NR base station  fails'
                           ' to creat.')

        super().__init__(cmx, cell)

    def _config_scheduler(self,
                          dl_mcs=None,
                          dl_mcs_table=None,
                          dl_rb_alloc=None,
                          dl_mimo_mode=None,
                          ul_mcs=None,
                          ul_mcs_table=None,
                          ul_rb_alloc=None,
                          ul_mimo_mode=None):

        from rs_mrt.testenvironment.signaling.sri.rat.nr import McsTable

        log_list = []
        if dl_mcs:
            log_list.append('dl_mcs: {}'.format(dl_mcs))
        if ul_mcs:
            log_list.append('ul_mcs: {}'.format(ul_mcs))

        # If rb alloc is not a tuple, add 0 as start RBs for XLAPI NR scheduler
        if dl_rb_alloc:
            if not isinstance(dl_rb_alloc, tuple):
                dl_rb_alloc = (0, dl_rb_alloc)
            log_list.append('dl_rb_alloc: {}'.format(dl_rb_alloc))
        if ul_rb_alloc:
            if not isinstance(ul_rb_alloc, tuple):
                ul_rb_alloc = (0, ul_rb_alloc)
            log_list.append('ul_rb_alloc: {}'.format(ul_rb_alloc))
        if dl_mcs_table:
            dl_mcs_table = McsTable(dl_mcs_table)
            log_list.append('dl_mcs_table: {}'.format(dl_mcs_table))
        if ul_mcs_table:
            ul_mcs_table = McsTable(ul_mcs_table)
            log_list.append('ul_mcs_table: {}'.format(ul_mcs_table))
        if dl_mimo_mode:
            log_list.append('dl_mimo_mode: {}'.format(dl_mimo_mode))
        if ul_mimo_mode:
            log_list.append('ul_mimo_mode: {}'.format(ul_mimo_mode))

        scheduler = self._cmx.dut.get_scheduler(self._cell)
        logger.info('configure scheduler for {}'.format(','.join(log_list)))

        scheduler.configure_ue_scheduler(dl_mcs=dl_mcs,
                                         dl_mcs_table=dl_mcs_table,
                                         dl_rb_alloc=dl_rb_alloc,
                                         dl_mimo_mode=dl_mimo_mode,
                                         ul_mcs=ul_mcs,
                                         ul_mcs_table=ul_mcs_table,
                                         ul_rb_alloc=ul_rb_alloc,
                                         ul_mimo_mode=ul_mimo_mode)
        logger.info('Configure scheduler succeeds')
        self._network.apply_changes()

    def wait_for_attach(self, timeout):
        self._cmx.dut.signaling.wait_for_nr_registration(self._cell)

    def attach_as_secondary_cell(self, scg=False):
        """Attach this cell as a secondary cell.

        Args:
            scg: bool specifing if the cell should be added as part of the
                secondary cell group.
        """
        if scg:
            self._cmx.dut.signaling.ca_add_scg_scell(self._cell)
        else:
            self._cmx.dut.signaling.ca_add_scell(self._cell)

    def activate_endc(self, endc_timer=DEFAULT_ENDC_TIMER):
        """Enable endc mode for NR cell.

        Args:
            endc_timer: timeout for endc state
        """
        logger.info('enable endc mode for nsa dual connection')
        self._cmx.dut.signaling.nsa_dual_connect(self._cell)
        time_count = 0
        while time_count < endc_timer:
            if str(self._cmx.dut.state.radio_connectivity) == \
                    'RadioConnectivityMode.EPS_LTE_NR':
                logger.info('enter endc mode')
                return
            time.sleep(1)
            time_count += 1
            if time_count % 30 == 0:
                logger.info('did not reach endc at {} s'.format(time_count))
        raise CmxError('Cannot reach endc after {} s'.format(endc_timer))

    def config_flexible_slots(self):
        """Configs flexible slots for NR cell."""

        from rs_mrt.testenvironment.signaling.sri.rat.nr.config import CellFrequencyBandReq
        from rs_mrt.testenvironment.signaling.sri.rat.common import SetupRelease

        req = CellFrequencyBandReq()
        req.band.frequency_range.fr1.tdd.tdd_config_common.setup_release = SetupRelease.RELEASE
        self._cell.stub.SetCellFrequencyBand(req)
        self._network.apply_changes()
        logger.info('Config flexible slots completed')

    def disable_all_ul_slots(self):
        """Disable all uplink scheduling slots for NR cell"""
        self._cc.disable_all_ul_slots()
        self._network.apply_changes()
        logger.info('Disabled all uplink scheduling slots for the nr cell')

    @property
    def dl_channel(self):
        """Gets the downlink channel of cell.

        Return:
            the downlink channel (nr_arfcn) in int.
        """
        return int(self._cell.get_dl_ref_a())

    def _bandwidth_to_carrier_bandwidth(self, bandwidth):
        """Converts bandwidth in MHz to CarrierBandwidth.
            CarrierBandwidth Enum in XLAPI:
                MHZ_5 = 0
                MHZ_10 = 1
                MHZ_15 = 2
                MHZ_20 = 3
                MHZ_25 = 4
                MHZ_30 = 5
                MHZ_40 = 6
                MHZ_50 = 7
                MHZ_60 = 8
                MHZ_70 = 9
                MHZ_80 = 10
                MHZ_90 = 11
                MHZ_100 = 12
                MHZ_200 = 13
                MHZ_400 = 14
        Args:
            bandwidth: channel bandwidth in MHz.

        Return:
            the corresponding NR Carrier Bandwidth.
        """
        from mrtype.nr.frequency import CarrierBandwidth
        if bandwidth > 100:
            return CarrierBandwidth(12 + bandwidth // 200)
        elif bandwidth > 30:
            return CarrierBandwidth(2 + bandwidth // 10)
        else:
            return CarrierBandwidth(bandwidth // 5 - 1)

    def set_bandwidth(self, bandwidth, scs=None):
        """Sets the channel bandwidth of the cell.

        Args:
            bandwidth: channel bandwidth of cell.
            scs: subcarrier spacing (SCS) of resource grid 0
        """
        if not scs:
            scs = self._cell.get_scs()
        self._cell.set_carrier_bandwidth_and_scs(
            self._bandwidth_to_carrier_bandwidth(bandwidth), scs)
        logger.info(
            'The bandwidth in MHz is {}. After setting, the value is {}'.
            format(bandwidth, str(self._cell.get_carrier_bandwidth())))

    def set_cdrx_config(self, config):
        """Configures NR cdrx with nr config parameters.

        config: The NrCellConfig for current base station.
        """
        logger.info(
            f'Configure Nr drx with\n'
            f'drx_on_duration_timer: {config.drx_on_duration_timer}\n'
            f'drx_inactivity_timer: {config.drx_inactivity_timer}\n'
            f'drx_retransmission_timer_dl: {config.drx_retransmission_timer_dl}\n'
            f'drx_retransmission_timer_ul: {config.drx_retransmission_timer_ul}\n'
            f'drx_long_cycle: {config.drx_long_cycle}\n'
            f'drx_long_cycle_offset: {config.drx_long_cycle_offset}\n'
            f'harq_rtt_timer_dl: {config.harq_rtt_timer_dl}\n'
            f'harq_rtt_timer_ul: {config.harq_rtt_timer_ul}\n'
            f'slot_offset: {config.slot_offset}\n'
        )

        from mrtype.nr.drx import (
            NrDrxConfig,
            NrDrxInactivityTimer,
            NrDrxOnDurationTimer,
            NrDrxRetransmissionTimer,
            NrDrxHarqRttTimer,
            NrDrxSlotOffset,
        )

        from mrtype.nr.drx import NrDrxLongCycleStartOffset as longCycle

        long_cycle_mapping = {
            10: longCycle.ms10, 20: longCycle.ms20, 32: longCycle.ms32,
            40: longCycle.ms40, 60: longCycle.ms60, 64: longCycle.ms64,
            70: longCycle.ms70, 80: longCycle.ms80, 128: longCycle.ms128,
            160: longCycle.ms160, 256: longCycle.ms256, 320: longCycle.ms320,
            512: longCycle.ms512, 640: longCycle.ms640, 1024: longCycle.ms1024,
            1280: longCycle.ms1280, 2048: longCycle.ms2048,
            2560: longCycle.ms2560,
        }

        drx_on_duration_timer = NrDrxOnDurationTimer(
            int(config.drx_on_duration_timer)
        )
        drx_inactivity_timer = NrDrxInactivityTimer(
            int(config.drx_inactivity_timer)
        )
        drx_retransmission_timer_dl = NrDrxRetransmissionTimer(
            int(config.drx_retransmission_timer_dl)
        )
        drx_retransmission_timer_ul = NrDrxRetransmissionTimer(
            int(config.drx_retransmission_timer_ul)
        )
        drx_long_cycle = long_cycle_mapping[int(config.drx_long_cycle)]
        drx_long_cycle_offset = drx_long_cycle(
            int(config.drx_long_cycle_offset)
        )
        harq_rtt_timer_dl = NrDrxHarqRttTimer(config.harq_rtt_timer_dl)
        harq_rtt_timer_ul = NrDrxHarqRttTimer(config.harq_rtt_timer_ul)
        slot_offset=NrDrxSlotOffset(config.slot_offset)

        nr_drx_config = NrDrxConfig(
            on_duration_timer=drx_on_duration_timer,
            inactivity_timer=drx_inactivity_timer,
            retransmission_timer_dl=drx_retransmission_timer_dl,
            retransmission_timer_ul=drx_retransmission_timer_ul,
            long_cycle_start_offset=drx_long_cycle_offset,
            harq_rtt_timer_dl=harq_rtt_timer_dl,
            harq_rtt_timer_ul=harq_rtt_timer_ul,
            slot_offset=slot_offset,
        )

        self._cmx.dut.nr_cell_group().set_drx_and_adjust_scheduler(
            nr_drx_config
        )
        self._network.apply_changes()

    def set_dl_channel(self, channel):
        """Sets the downlink channel number of cell.

        Args:
            channel: downlink channel number of cell or Frequency Range ('LOW',
                     'MID' or 'HIGH').
        """
        from mrtype.nr.frequency import NrArfcn
        from mrtype.frequency import FrequencyRange

        # When the channel is string, set it use Frenquency Range
        if isinstance(channel, str):
            logger.info('Sets dl channel Frequency Range {}'.format(channel))
            frequency_range = FrequencyRange.LOW
            if channel.upper() == 'MID':
                frequency_range = FrequencyRange.MID
            elif channel.upper() == 'HIGH':
                frequency_range = FrequencyRange.HIGH
            self._cell.set_dl_ref_a_offset(self.band, frequency_range)
            logger.info('The dl_channel was set to {}'.format(self.dl_channel))
            return
        if self.dl_channel == channel:
            logger.info('The dl_channel was at {}'.format(self.dl_channel))
            return
        self._cell.set_dl_ref_a_offset(self.band, NrArfcn(channel))
        logger.info('The dl_channel was set to {}'.format(self.dl_channel))

    def set_dl_modulation_table(self, modulation):
        """Sets down link modulation table.

        Args:
            modulation: modulation table setting (ModulationType).
        """
        if not isinstance(modulation, ModulationType):
            raise CmxError('The modulation is not the type of Modulation')
        self._config_scheduler(dl_mcs_table=modulation.value)

    def set_mimo_mode(self, mimo):
        """Sets mimo mode for NR nsa scenario.

        Args:
            mimo: the mimo mode.
        """
        from rs_mrt.testenvironment.signaling.sri.rat.nr import DownlinkMimoMode
        if not isinstance(mimo, MimoModes):
            raise CmxError("Wrong type of mimo mode")

        self._config_scheduler(dl_mimo_mode=DownlinkMimoMode.Enum(mimo.value))

    def set_scheduling_mode(self,
                            mcs_dl=None,
                            mcs_ul=None,
                            nrb_dl=None,
                            nrb_ul=None):
        """Sets scheduling mode.

        Args:
            mcs_dl: Downlink MCS.
            mcs_ul: Uplink MCS.
            nrb_dl: Number of RBs for downlink.
            nrb_ul: Number of RBs for uplink.
        """
        self._config_scheduler(dl_mcs=mcs_dl,
                               ul_mcs=mcs_ul,
                               dl_rb_alloc=nrb_dl,
                               ul_rb_alloc=nrb_ul)

    def set_ssf_config(self, ssf_config):
        """Sets ssf subframe assignment with tdd_config.

        Args:
            ssf_config: the special subframe pattern config (from 1-9).
        """
        raise CmxError('the set ssf config for nr did not implemente yet')

    def set_tdd_config(self, tdd_config):
        """Sets tdd subframe assignment with tdd_config.

        Args:
            tdd_config: the subframe assignemnt config (from 0-6).
        """
        raise CmxError('the set tdd config for nr did not implemente yet')

    def set_transmission_mode(self, transmission_mode):
        """Sets transmission mode with schedular.

        Args:
            transmission_mode: the download link transmission mode.
        """
        logger.info('The set transmission mode for nr is set by mimo mode')

    def set_ul_modulation_table(self, modulation):
        """Sets down link modulation table.

        Args:
            modulation: modulation table setting (ModulationType).
        """
        if not isinstance(modulation, ModulationType):
            raise CmxError('The modulation is not the type of Modulation')
        self._config_scheduler(ul_mcs_table=modulation.value)

    def create_new_ta(self, tac):
        """Creates and initializes a new tracking area for this cell.

        Args:
            tac: the tracking area ID of the new cell.
        Returns:
            ta: the new tracking area.
        """
        plmn = self._network.get_or_create_plmn()
        return plmn.create_fivegs_ta(tac)


class CmxError(Exception):
    """Class to raise exceptions related to cmx."""
