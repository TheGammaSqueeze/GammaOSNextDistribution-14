# Lint as: python2, python3
# Copyright 2019 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""
This class provides functions to initialize variable attentuator used for
Bluetooth range vs rate tests
"""

import logging

from autotest_lib.client.bin import utils
from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib import global_config
from autotest_lib.client.common_lib.cros.network import ping_runner
from autotest_lib.server.cros import dnsname_mangler
from autotest_lib.server.cros.network import attenuator_controller


def init_btattenuator(host, args_dict):
    """
    Function to initialize bluetooth attenuator and zero the attenuator

    Attenuator address can be passed as argument to test_that or have to
    be derived from the host name (hostname-btattenuator). For devices in lab,
    attenuator is assumed to be absent unless added to attenuator_hosts.py file
    If attenuator is present but not accessible, an exception is raised.

    @param host: cros host object representing the DUT
           args_dict : arguments passed to test_that
    @return: AttenuatorController object if attenutator is present else None
    @raises: TestError if attenautor init fails or if attenutator cannot be
             accessed
    """
    try:
        if not utils.is_in_container():
            is_moblab = utils.is_moblab()
        else:
            is_moblab = global_config.global_config.get_config_value(
                    'SSP', 'is_moblab', type=bool, default=False)
        if is_moblab:
            # TODO(b:183231262) Implement for moblab
            logging.debug('bt attenuator not implemented for moblab')
            return None

        # If attenuator address is provided in args, then it is used
        # else try to derive attenuator hostname from DUT hostname
        btattenuator_args = host.get_btattenuator_arguments(
                args_dict) if args_dict is not None else {}
        btatten_addr = btattenuator_args.get('btatten_addr')
        btatten_addr = dnsname_mangler.get_btattenuator_addr(
                host.hostname, btatten_addr, True)
        logging.debug('Bluetooth attentuator address is %s', btatten_addr)

        if not btatten_addr:
            logging.debug('Bluetooth attenuator not present')
            return None
        # Attenuator retains previous attenuation set even if it powered down
        # Do not proceed if attenutator is not accessible
        if not ping_runner.PingRunner().simple_ping(btatten_addr):
            logging.debug('Bluetooth attenuator not accessible')
            return None

        # Init also sets attenutation to zero
        logging.debug('Initializing bluetooth attenuator')
        return attenuator_controller.AttenuatorController(btatten_addr)
    except error.TestError:
        raise
    except Exception as e:
        logging.error('Exception %s while initializing bt attenuator', str(e))
        return None
