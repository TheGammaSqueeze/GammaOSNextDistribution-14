"""Module manager the required definitions for tigertail"""

import logging
import time

from enum import Enum

from acts.libs.proc import job

MOBLY_CONTROLLER_CONFIG_NAME = "Tigertail"
ACTS_CONTROLLER_REFERENCE_NAME = "tigertails"

TIGERTAIL_SLEEP_TIME = 5


def create(configs):
    """Takes a list of Tigertail serial numbers and returns Tigertail Controllers.

    Args:
        configs: A list of serial numbers

    Returns:
        a list of Tigertail controllers

    Raises:
        ValueError if the configuration is not a list of serial number
    """
    tigertails = []
    if isinstance(configs, list):
        for serial_no in configs:
            tigertail = Tigertail(serial_no)
            tigertails.append(tigertail)
    else:
        raise ValueError(
            'Invalid config for tigertail, should be a list of serial number')

    return tigertails


def destroy(tigertails):
    pass


def get_info(tigertails):
    return [tigertail.get_info() for tigertail in tigertails]


class TigertailError(Exception):
    pass


class TigertailState(Enum):
    def __str__(self):
        return str(self.value)

    A = 'A'
    B = 'B'
    Off = 'off'


class Tigertail(object):
    def __init__(self, serial_number):
        self.serial_number = serial_number
        self.tigertool_bin = None

    def setup(self, user_params):
        """Links tigertool binary

        This function needs to be:
        Args:
            user_params: User defined parameters. Expected field is:
            {
                // required, string or list of strings
                tigertool: ['/path/to/tigertool.par']
            }
        """
        tigertool_path = user_params['tigertool']
        if tigertool_path is None:
            self.tigertool_bin = None
        elif isinstance(tigertool_path, str):
            self.tigertool_bin = tigertool_path
        elif isinstance(tigertool_path, list):
            if len(tigertool_path) == 0:
                self.tigertool_bin = None
            else:
                self.tigertool_bin = tigertool_path[0]

        if self.tigertool_bin is None:
            raise TigertailError('Tigertail binary not found')

        logging.getLogger().debug(
            f'Setup {self.serial_number} with binary at {self.tigertool_bin}')

    def turn_on_mux_A(self):
        self._set_tigertail_state(TigertailState.A)

    def turn_on_mux_B(self):
        self._set_tigertail_state(TigertailState.B)

    def turn_off(self):
        self._set_tigertail_state(TigertailState.Off)

    def get_info(self):
        return {'tigertail_serial_no': self.serial_number}

    def _set_tigertail_state(self, state: TigertailState):
        """Sets state for tigertail, there are 3 possible states:
            A  : enable port A
            B  : enable port B
            Off: turn off both ports
        """
        result = job.run([
            self.tigertool_bin, '--serialno',
            str(self.serial_number), '--mux',
            str(state)
        ],
                         timeout=10)

        if result.stderr != '':
            raise TigertailError(result.stderr)

        # Sleep time to let the device connected/disconnect to tigertail
        time.sleep(TIGERTAIL_SLEEP_TIME)
