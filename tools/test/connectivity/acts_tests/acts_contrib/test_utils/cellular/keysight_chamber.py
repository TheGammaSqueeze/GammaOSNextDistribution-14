# Copyright 2020 Google Inc. All Rights Reserved.
# Author: oelayach@google.com

import pyvisa
import time
from acts import logger

class KeysightChamber(object):
    """Base class implementation for signal generators.

    Base class provides functions whose implementation is shared by all
    chambers.
    """
    CHAMBER_SLEEP = 10

    VISA_LOCATION = '/opt/keysight/iolibs/libktvisa32.so'

    def __init__(self, config):
        self.config = config
        self.log = logger.create_tagged_trace_logger("{}{}".format(
            self.config['brand'], self.config['model']))
        self.chamber_resource = pyvisa.ResourceManager(self.VISA_LOCATION)
        self.chamber_inst = self.chamber_resource.open_resource(
            'TCPIP0::{}::{}::INSTR'.format(self.config['ip_address'],
                                       self.config['hislip_interface']))
        self.chamber_inst.timeout = 200000
        self.chamber_inst.write_termination = '\n'
        self.chamber_inst.read_termination = '\n'

        self.id_check(self.config)
        self.current_azim = 0
        self.current_roll = 0
        if self.config.get('reset_home', True):
            self.find_chamber_home()
            self.move_theta_phi_abs(self.config['chamber_home']['theta'],
                                    self.config['chamber_home']['phi'])
            self.set_new_home_position()
        else:
            self.config['chamber_home'] = {'phi': 0, 'theta': 0}
            self.log.warning(
                'Reset home set to false. Assumed [0,0]. Chamber angles may not be as expected.'
            )
        self.preset_orientations = self.config['preset_orientations']

    def id_check(self, config):
        """ Checks Chamber ID."""
        self.log.info("ID Check Successful.")
        self.log.info(self.chamber_inst.query("*IDN?"))

    def reset(self):
        self.reset_phi_theta()

    def disconnect(self):
        if self.config.get('reset_home', True):
            self.reset_phi_theta()
        self.chamber_inst.close()
        self.chamber_resource.close()

    def find_chamber_home(self):
        self.chamber_inst.write(f"POS:BOR:INIT")
        self.set_new_home_position()
        self.wait_for_move_end()

    def set_new_home_position(self):
        self.chamber_inst.write(f"POS:ZERO:RES")

    def get_phi(self):
        return self.current_azim

    def get_theta(self):
        return self.current_roll

    def get_pattern_sweep_limits(self):
        return {
            "pattern_phi_start": -self.config['chamber_home']['phi'],
            "pattern_phi_stop": 165 - self.config['chamber_home']['phi'],
            "pattern_theta_start": -self.config['chamber_home']['theta'],
            "pattern_theta_stop": 360 - self.config['chamber_home']['theta'],
        }

    def move_phi_abs(self, phi):
        self.log.info("Moving to Phi={}".format(phi))
        self.move_to_azim_roll(phi, self.current_roll)

    def move_theta_abs(self, theta):
        self.log.info("Moving to Theta={}".format(theta))
        self.move_to_azim_roll(self.current_azim, theta)

    def move_theta_phi_abs(self, theta, phi):
        self.log.info("Moving to Theta={}, Phi={}".format(theta, phi))
        self.move_to_azim_roll(phi, theta)

    def move_theta_phi_abs(self, theta, phi):
        self.log.info("Moving chamber to [{}, {}]".format(theta, phi))
        self.move_to_azim_roll(phi, theta)

    def move_phi_rel(self, phi):
        self.log.info("Moving Phi by {} degrees".format(phi))
        self.move_to_azim_roll(self.current_azim + phi, self.current_roll)

    def move_theta_rel(self, theta):
        self.log.info("Moving Theta by {} degrees".format(theta))
        self.move_to_azim_roll(self.current_azim, self.current_roll + theta)

    def move_feed_roll(self, roll):
        self.log.info("Moving feed roll to {} degrees".format(roll))
        self.chamber_inst.write(f"POS:MOVE:ROLL:FEED {roll}")
        self.chamber_inst.write("POS:MOVE:INIT")
        self.wait_for_move_end()
        self.current_feed_roll = self.chamber_inst.query("POS:MOVE:ROLL:FEED?")

    def reset_phi(self):
        self.log.info("Resetting Phi.")
        self.move_to_azim_roll(0, self.current_roll)
        self.phi = 0

    def reset_theta(self):
        self.log.info("Resetting Theta.")
        self.move_to_azim_roll(self.current_azim, 0)
        self.theta = 0

    def reset_phi_theta(self):
        """ Resets signal generator."""
        self.log.info("Resetting to home.")
        self.chamber_inst.write(f"POS:ZERO:GOTO")
        self.wait_for_move_end()

    # Keysight-provided functions
    def wait_for_move_end(self):
        moving_bitmask = 4
        while True:
            stat = int(self.chamber_inst.query("STAT:OPER:COND?"))
            if (stat & moving_bitmask) == 0:
                return
            time.sleep(0.25)

    def wait_for_sweep_end(self):
        sweeping_bitmask = 16
        while True:
            stat = int(self.chamber_inst.query("STAT:OPER:COND?"))
            if (stat & sweeping_bitmask) == 0:
                return
            time.sleep(0.25)

    def move_to_azim_roll(self, azim, roll):
        self.chamber_inst.write(f"POS:MOVE:AZIM {azim};ROLL {roll}")
        self.chamber_inst.write("POS:MOVE:INIT")
        self.wait_for_move_end()
        curr_motor = self.chamber_inst.query("POS:CURR:MOT?")
        curr_azim, curr_roll = map(float, (curr_motor.split(',')))
        self.current_azim = curr_azim
        self.current_roll = curr_roll
        return curr_azim, curr_roll

    def sweep_setup(self, azim_sss: tuple, roll_sss: tuple, sweep_type: str):
        self.chamber_inst.write(
            f"POS:SWE:AZIM:STAR {azim_sss[0]};STOP {azim_sss[1]};STEP {azim_sss[2]}"
        )
        self.chamber_inst.write(
            f"POS:SWE:ROLL:STAR {roll_sss[0]};STOP {roll_sss[1]};STEP {roll_sss[2]}"
        )
        self.chamber_inst.write(f"POS:SWE:TYPE {sweep_type}")
        self.chamber_inst.write("POS:SWE:CONT 1")

    def sweep_init(self):
        def query_float_list(inst, scpi):
            resp = inst.query(scpi)
            return list(map(float, resp.split(',')))

        self.chamber_inst.write("POS:SWE:INIT")
        self.wait_for_sweep_end()
        azims = query_float_list(self.chamber_inst, "FETC:AZIM?")
        rolls = query_float_list(self.chamber_inst, "FETC:ROLL?")
        phis = query_float_list(self.chamber_inst, "FETC:DUT:PHI?")
        thetas = query_float_list(self.chamber_inst, "FETC:DUT:THET?")
        return zip(azims, rolls, phis, thetas)