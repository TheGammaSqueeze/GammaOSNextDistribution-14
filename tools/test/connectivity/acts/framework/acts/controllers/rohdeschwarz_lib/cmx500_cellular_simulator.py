#!/usr/bin/env python3
#
#   Copyright 2021 - The Android Open Source Project
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

import time
from enum import Enum

from acts.controllers.rohdeschwarz_lib import cmx500
from acts.controllers.rohdeschwarz_lib.cmx500 import LteBandwidth
from acts.controllers.rohdeschwarz_lib.cmx500 import LteState
from acts.controllers import cellular_simulator as cc
from acts.controllers.cellular_lib import LteSimulation

CMX_TM_MAPPING = {
    LteSimulation.TransmissionMode.TM1: cmx500.TransmissionModes.TM1,
    LteSimulation.TransmissionMode.TM2: cmx500.TransmissionModes.TM2,
    LteSimulation.TransmissionMode.TM3: cmx500.TransmissionModes.TM3,
    LteSimulation.TransmissionMode.TM4: cmx500.TransmissionModes.TM4,
    LteSimulation.TransmissionMode.TM7: cmx500.TransmissionModes.TM7,
    LteSimulation.TransmissionMode.TM8: cmx500.TransmissionModes.TM8,
    LteSimulation.TransmissionMode.TM9: cmx500.TransmissionModes.TM9,
}

CMX_SCH_MAPPING = {
    LteSimulation.SchedulingMode.STATIC: cmx500.SchedulingMode.USERDEFINEDCH
}

CMX_MIMO_MAPPING = {
    LteSimulation.MimoMode.MIMO_1x1: cmx500.MimoModes.MIMO1x1,
    LteSimulation.MimoMode.MIMO_2x2: cmx500.MimoModes.MIMO2x2,
    LteSimulation.MimoMode.MIMO_4x4: cmx500.MimoModes.MIMO4x4,
}


class ConfigurationMode(Enum):
    Power = "Power"


class CMX500CellularSimulator(cc.AbstractCellularSimulator):
    """ A cellular simulator for telephony simulations based on the CMX 500
    controller. """

    # The maximum power that the equipment is able to transmit
    MAX_DL_POWER = -25

    def __init__(self,
                 ip_address,
                 port='5025',
                 config_mode=None):
        """ Initializes the cellular simulator.

        Args:
            ip_address: the ip address of the CMX500
            port: the port number for the CMX500 controller
            config_mode: A pre-defined configuration mode to use.
        """
        super().__init__()
        try:
            self.cmx = cmx500.Cmx500(ip_address, port)
        except:
            raise cc.CellularSimulatorError('Error when Initializes CMX500.')

        self._config_mode = config_mode
        self.bts = self.cmx.bts

    def destroy(self):
        """ Sends finalization commands to the cellular equipment and closes
        the connection. """
        self.log.info('destroy the cmx500 simulator')
        self.cmx.disconnect()

    def set_config_mode(self, config_mode=None):
        """Sets config mode for the cmx 500 simulator."""
        self._config_mode = config_mode

    def configure_lte_bts(self, config, bts_index):
        """ Commands the equipment to setup an LTE base station with the
        required configuration except drx.

        Args:
            config: an LteSimulation.BtsConfig object.
            bts_index: the base station number.
        """
        self.configure_lte_bts_base(config, bts_index)

    def configure_lte_bts_after_started(self, config, bts_index):

        if config.drx_connected_mode:
            self.set_cdrx_config(bts_index, config)
        if config.disable_all_ul_subframes:
            self.bts[bts_index].disable_all_ul_subframes()
        self.log.info(
            'The radio connectivity after lte started config is {}'.format(
                self.cmx.dut.state.radio_connectivity))

    def configure_nr_bts_after_started(self, config, bts_index):
        if config.drx_connected_mode:
            self.set_cdrx_config(bts_index, config)
        if config.config_flexible_slots:
            self.bts[bts_index].config_flexible_slots()
        if config.disable_all_ul_slots:
            self.bts[bts_index].disable_all_ul_slots()
        self.log.info(
            'The radio connectivity after nr started config is {}'.format(
                self.cmx.dut.state.radio_connectivity))

    def setup_lte_scenario(self):
        """ Configures the equipment for an LTE simulation. """
        self.log.info('setup lte scenario')
        self.cmx.switch_lte_signalling(cmx500.LteState.LTE_ON)

    def setup_nr_sa_scenario(self):
        """ Configures the equipment for an NR stand alone simulation. """
        raise NotImplementedError()

    def setup_nr_nsa_scenario(self):
        """ Configures the equipment for an NR non stand alone simulation. """
        self.log.info('setup nsa scenario (start lte cell and nr cell')
        self.cmx.switch_on_nsa_signalling()

    def set_band_combination(self, bands, mimo_modes):
        """ Prepares the test equipment for the indicated band/mimo combination.

        Args:
            bands: a list of bands represented as ints or strings
            mimo_modes: a list of LteSimulation.MimoMode to use for each carrier
        """
        self.num_carriers = len(bands)

        # Don't configure secondary cells if we're using the built-in power
        # configuration.
        if self._config_mode != ConfigurationMode.Power:
            self.cmx.set_band_combination(bands)

    def set_lte_rrc_state_change_timer(self, enabled, time=10):
        """ Configures the LTE RRC state change timer.

        Args:
            enabled: a boolean indicating if the timer should be on or off.
            time: time in seconds for the timer to expire
        """
        self.log.info('set timer enabled to {} and the time to {}'.format(
            enabled, time))
        self.cmx.rrc_state_change_time_enable = enabled
        self.cmx.lte_rrc_state_change_timer = time

    def set_band(self, bts_index, band):
        """ Sets the band for the indicated base station.

        Args:
            bts_index: the base station number
            band: the new band
        """
        self.log.info('set band to {}'.format(band))
        self.bts[bts_index].set_band(int(band))

    def set_cdrx_config(self, bts_index, config):
        """ Sets the tdd configuration number for the indicated base station.

        Args:
            bts_index: the base station number
            config: the config including cdrx parameters
        """
        self.log.info('set cdrx config for bts {} to {}'.format(
            bts_index, config)
        )
        self.bts[bts_index].set_cdrx_config(config)

    def get_duplex_mode(self, band):
        """ Determines if the band uses FDD or TDD duplex mode

        Args:
            band: a band number

        Returns:
            an variable of class DuplexMode indicating if band is FDD or TDD
        """
        if 33 <= int(band) <= 46:
            return cmx500.DuplexMode.TDD
        else:
            return cmx500.DuplexMode.FDD

    def set_input_power(self, bts_index, input_power):
        """ Sets the input power for the indicated base station.

        Args:
            bts_index: the base station number
            input_power: the new input power
        """
        if input_power > 23:
            self.log.warning('Open loop supports -50dBm to 23 dBm. '
                             'Setting it to max power 23 dBm')
            input_power = 23
        self.log.info('set input power to {}'.format(input_power))
        self.bts[bts_index].set_ul_power(input_power)

    def set_output_power(self, bts_index, output_power):
        """ Sets the output power for the indicated base station.

        Args:
            bts_index: the base station number
            output_power: the new output power
        """
        self.log.info('set output power to {}'.format(output_power))
        self.bts[bts_index].set_dl_power(output_power)

    def set_tdd_config(self, bts_index, tdd_config):
        """ Sets the tdd configuration number for the indicated base station.

        Args:
            bts_index: the base station number
            tdd_config: the new tdd configuration number (from 0 to 6)
        """
        self.log.info('set tdd config to {}'.format(tdd_config))
        self.bts[bts_index].set_tdd_config(tdd_config)

    def set_ssf_config(self, bts_index, ssf_config):
        """ Sets the Special Sub-Frame config number for the indicated
        base station.

        Args:
            bts_index: the base station number
            ssf_config: the new ssf config number (from 0 to 9)
        """
        self.log.info('set ssf config to {}'.format(ssf_config))
        self.bts[bts_index].set_ssf_config(ssf_config)

    def set_bandwidth(self, bts_index, bandwidth):
        """ Sets the bandwidth for the indicated base station.

        Args:
            bts_index: the base station number
            bandwidth: the new bandwidth in MHz
        """
        self.log.info('set bandwidth of bts {} to {}'.format(
            bts_index, bandwidth))
        self.bts[bts_index].set_bandwidth(int(bandwidth))

    def set_downlink_channel_number(self, bts_index, channel_number):
        """ Sets the downlink channel number for the indicated base station.

        Args:
            bts_index: the base station number
            channel_number: the new channel number (earfcn)
        """
        self.log.info(
            'Sets the downlink channel number to {}'.format(channel_number))
        self.bts[bts_index].set_dl_channel(channel_number)

    def set_mimo_mode(self, bts_index, mimo_mode):
        """ Sets the mimo mode for the indicated base station.

        Args:
            bts_index: the base station number
            mimo_mode: the new mimo mode
        """
        self.log.info('set mimo mode to {}'.format(mimo_mode))
        mimo_mode = CMX_MIMO_MAPPING[mimo_mode]
        self.bts[bts_index].set_mimo_mode(mimo_mode)

    def set_transmission_mode(self, bts_index, tmode):
        """ Sets the transmission mode for the indicated base station.

        Args:
            bts_index: the base station number
            tmode: the new transmission mode
        """
        self.log.info('set TransmissionMode to {}'.format(tmode))
        tmode = CMX_TM_MAPPING[tmode]
        self.bts[bts_index].set_transmission_mode(tmode)

    def set_scheduling_mode(self,
                            bts_index,
                            scheduling,
                            mcs_dl=None,
                            mcs_ul=None,
                            nrb_dl=None,
                            nrb_ul=None):
        """ Sets the scheduling mode for the indicated base station.

        Args:
            bts_index: the base station number.
            scheduling: the new scheduling mode.
            mcs_dl: Downlink MCS.
            mcs_ul: Uplink MCS.
            nrb_dl: Number of RBs for downlink.
            nrb_ul: Number of RBs for uplink.
        """
        if scheduling not in CMX_SCH_MAPPING:
            raise cc.CellularSimulatorError(
                "This scheduling mode is not supported")
        log_list = []
        if mcs_dl:
            log_list.append('mcs_dl: {}'.format(mcs_dl))
        if mcs_ul:
            log_list.append('mcs_ul: {}'.format(mcs_ul))
        if nrb_dl:
            log_list.append('nrb_dl: {}'.format(nrb_dl))
        if nrb_ul:
            log_list.append('nrb_ul: {}'.format(nrb_ul))

        self.log.info('set scheduling mode to {}'.format(','.join(log_list)))
        self.bts[bts_index].set_scheduling_mode(mcs_dl=mcs_dl,
                                                mcs_ul=mcs_ul,
                                                nrb_dl=nrb_dl,
                                                nrb_ul=nrb_ul)

    def set_dl_256_qam_enabled(self, bts_index, enabled):
        """ Determines what MCS table should be used for the downlink.

        Args:
            bts_index: the base station number
            enabled: whether 256 QAM should be used
        """
        self.log.info('Set 256 QAM DL MCS enabled: ' + str(enabled))
        self.bts[bts_index].set_dl_modulation_table(
            cmx500.ModulationType.Q256 if enabled else cmx500.ModulationType.
            Q64)

    def set_ul_64_qam_enabled(self, bts_index, enabled):
        """ Determines what MCS table should be used for the uplink.

        Args:
            bts_index: the base station number
            enabled: whether 64 QAM should be used
        """
        self.log.info('Set 64 QAM UL MCS enabled: ' + str(enabled))
        self.bts[bts_index].set_ul_modulation_table(
            cmx500.ModulationType.Q64 if enabled else cmx500.ModulationType.Q16
        )

    def set_mac_padding(self, bts_index, mac_padding):
        """ Enables or disables MAC padding in the indicated base station.

        Args:
            bts_index: the base station number
            mac_padding: the new MAC padding setting
        """
        self.log.info('set mac pad on {}'.format(mac_padding))
        self.bts[bts_index].set_dl_mac_padding(mac_padding)

    def set_cfi(self, bts_index, cfi):
        """ Sets the Channel Format Indicator for the indicated base station.

        Args:
            bts_index: the base station number
            cfi: the new CFI setting
        """
        if cfi == 'BESTEFFORT':
            self.log.info('The cfi is BESTEFFORT, use default value')
            return
        try:
            index = int(cfi) + 1
        except Exception as e:
            index = 1
        finally:
            self.log.info('set the cfi and the cfi index is {}'.format(index))
            self.bts[bts_index].set_cfi(index)

    def set_paging_cycle(self, bts_index, cycle_duration):
        """ Sets the paging cycle duration for the indicated base station.

        Args:
            bts_index: the base station number
            cycle_duration: the new paging cycle duration in milliseconds
        """
        self.log.warning('The set_paging_cycle method is not implememted, '
                         'use default value')

    def set_phich_resource(self, bts_index, phich):
        """ Sets the PHICH Resource setting for the indicated base station.

        Args:
            bts_index: the base station number
            phich: the new PHICH resource setting
        """
        self.log.warning('The set_phich_resource method is not implememted, '
                         'use default value')

    def set_tracking_area(self, bts_index, tac):
        """ Assigns the cell to a specific tracking area.

        Args:
            tac: the unique tac to assign the cell to.
        """
        self.bts[bts_index].set_tracking_area(tac)

    def lte_attach_secondary_carriers(self, ue_capability_enquiry):
        """ Activates the secondary carriers for CA. Requires the DUT to be
        attached to the primary carrier first.

        Args:
            ue_capability_enquiry: UE capability enquiry message to be sent to
        the UE before starting carrier aggregation.
        """
        self.wait_until_communication_state()

        # primary cell is attached, now turn on all secondary cells
        if self.cmx.secondary_cells:
            self.cmx.turn_on_secondary_cells()

        # if a primary lte and primary nr cell exist, then activate endc on
        # primary nr cell
        is_nsa = self.cmx.primary_lte_cell and self.cmx.primary_nr_cell

        if is_nsa:
            self.cmx.primary_nr_cell.activate_endc()

        # attach secondary lte and nr cells
        # if nsa then nr cells should be added to secondary cell group
        for bts in self.cmx.secondary_lte_cells:
            bts.attach_as_secondary_cell()

        for bts in self.cmx.secondary_nr_cells:
            bts.attach_as_secondary_cell(scg=is_nsa)

        if self._config_mode and self._config_mode == ConfigurationMode.Power:
            self.configure_for_power_measurement()

        self.log.info('The radio connectivity is {}'.format(
            self.cmx.dut.state.radio_connectivity))

    def configure_for_power_measurement(self):
        """ Applies a pre-defined configuration for PDCCH power testing."""
        self.log.info('set lte cdrx for nr nsa scenario')
        for bts in self.cmx.lte_cells:
            bts.set_default_cdrx_config()
        time.sleep(5)

        self.log.info('Disables mac padding')
        for bts in self.bts:
            bts.set_dl_mac_padding(False)
        time.sleep(5)

        self.log.info('configure flexible slots and wait for 5 seconds')
        for bts in self.cmx.nr_cells:
            bts.config_flexible_slots()
        time.sleep(5)

        self.log.info('disable all ul subframes of the lte cell')
        for bts in self.cmx.lte_cells:
            bts.disable_all_ul_subframes()
        time.sleep(30)

        self.log.info('Disables Nr UL slots')
        for bts in self.cmx.nr_cells:
            bts.disable_all_ul_slots()
        time.sleep(5)

    def wait_until_attached(self, timeout=120):
        """ Waits until the DUT is attached to the primary carrier.

        Args:
            timeout: after this amount of time the method will raise a
                CellularSimulatorError exception. Default is 120 seconds.
        """
        self.log.info('wait until attached')
        if len(self.cmx.tracking_areas) > 1:
            self.log.info('turning off neighbor cells')
            self.cmx.turn_on_primary_cells()
            self.cmx.turn_off_neighbor_cells()

        self.cmx.wait_until_attached(timeout)

    def wait_until_communication_state(self, timeout=120):
        """ Waits until the DUT is in Communication state.

        Args:
            timeout: after this amount of time the method will raise a
                CellularSimulatorError exception. Default is 120 seconds.
        Return:
            True if cmx reach rrc state within timeout
        Raise:
            CmxError if tiemout
        """
        self.log.info('wait for rrc on state')
        return self.cmx.wait_for_rrc_state(cmx500.RrcState.RRC_ON, timeout)

    def wait_until_idle_state(self, timeout=120):
        """ Waits until the DUT is in Idle state.

        Args:
            timeout: after this amount of time the method will raise a
                CellularSimulatorError exception. Default is 120 seconds.
        Return:
            True if cmx reach rrc state within timeout
        Raise:
            CmxError if tiemout
        """
        self.log.info('wait for rrc off state')
        return self.cmx.wait_for_rrc_state(cmx500.RrcState.RRC_OFF, timeout)

    def wait_until_quiet(self, timeout=120):
        """Waits for all pending operations to finish on the simulator.

        Args:
            timeout: after this amount of time the method will raise a
                CellularSimulatorError exception. Default is 120 seconds.
        """
        self.cmx.network_apply_changes()

    def detach(self):
        """ Turns off all the base stations so the DUT loose connection."""
        self.log.info('Bypass simulator detach step for now')

    def stop(self):
        """ Stops current simulation. After calling this method, the simulator
        will need to be set up again. """
        self.log.info('Stops current simulation and disconnect cmx500')
        self.cmx.disconnect()

    def start_data_traffic(self):
        """ Starts transmitting data from the instrument to the DUT. """
        self.log.warning('The start_data_traffic is not implemented yet')

    def stop_data_traffic(self):
        """ Stops transmitting data from the instrument to the DUT. """
        self.log.warning('The stop_data_traffic is not implemented yet')

    def send_sms(self, message):
        """ Sends an SMS message to the DUT.

        Args:
            message: the SMS message to send.
        """
        self.cmx.send_sms(message)
