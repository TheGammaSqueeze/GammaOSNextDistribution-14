# Copyright (c) 2014 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
#

"""This file provides core logic for connecting a Chameleon Daemon."""

import logging

from autotest_lib.client.bin import utils
from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib import global_config
from autotest_lib.client.cros.chameleon import chameleon
from autotest_lib.server.cros import dnsname_mangler
from autotest_lib.server.cros.dynamic_suite import frontend_wrappers
from autotest_lib.server.hosts import ssh_host

# Names of the host attributes in the database that represent the values for
# the chameleon_host and chameleon_port for a servo connected to the DUT.
CHAMELEON_HOST_ATTR = 'chameleon_host'
CHAMELEON_PORT_ATTR = 'chameleon_port'

_CONFIG = global_config.global_config
ENABLE_SSH_TUNNEL_FOR_CHAMELEON = _CONFIG.get_config_value(
        'CROS', 'enable_ssh_tunnel_for_chameleon', type=bool, default=False)

class ChameleonHostError(Exception):
    """Error in ChameleonHost."""
    pass


class ChameleonHost(ssh_host.SSHHost):
    """Host class for a host that controls a Chameleon."""

    # Chameleond process name.
    CHAMELEOND_PROCESS = 'chameleond'


    # TODO(waihong): Add verify and repair logic which are required while
    # deploying to Cros Lab.


    def _initialize(self, chameleon_host='localhost', chameleon_port=9992,
                    *args, **dargs):
        """Initialize a ChameleonHost instance.

        A ChameleonHost instance represents a host that controls a Chameleon.

        @param chameleon_host: Name of the host where the chameleond process
                               is running.
                               If this is passed in by IP address, it will be
                               treated as not in lab.
        @param chameleon_port: Port the chameleond process is listening on.

        """
        super(ChameleonHost, self)._initialize(hostname=chameleon_host,
                                               *args, **dargs)

        self._is_in_lab = None
        self._check_if_is_in_lab()

        self._chameleon_port = chameleon_port
        self._local_port = None
        self._tunneling_process = None

        try:
            if self._is_in_lab and not ENABLE_SSH_TUNNEL_FOR_CHAMELEON:
                self._chameleon_connection = chameleon.ChameleonConnection(
                        self.hostname, chameleon_port)
            else:
                # A proxy generator is passed as an argument so that a proxy
                # could be re-created on demand in ChameleonConnection
                # whenever needed, e.g., after a reboot.
                proxy_generator = (
                        lambda: self.rpc_server_tracker.xmlrpc_connect(
                                None, chameleon_port,
                                ready_test_name=chameleon.CHAMELEON_READY_TEST,
                                timeout_seconds=60))
                self._chameleon_connection = chameleon.ChameleonConnection(
                        None, proxy_generator=proxy_generator)

        except Exception as e:
            raise ChameleonHostError('Can not connect to Chameleon: %s(%s)',
                                     e.__class__, e)


    def _check_if_is_in_lab(self):
        """Checks if Chameleon host is in lab and set self._is_in_lab.

        If self.hostname is an IP address, we treat it as is not in lab zone.

        """
        self._is_in_lab = (False if dnsname_mangler.is_ip_address(self.hostname)
                           else utils.host_is_in_lab_zone(self.hostname))


    def is_in_lab(self):
        """Check whether the chameleon host is a lab device.

        @returns: True if the chameleon host is in Cros Lab, otherwise False.

        """
        return self._is_in_lab


    def get_wait_up_processes(self):
        """Get the list of local processes to wait for in wait_up.

        Override get_wait_up_processes in
        autotest_lib.client.common_lib.hosts.base_classes.Host.
        Wait for chameleond process to go up. Called by base class when
        rebooting the device.

        """
        processes = [self.CHAMELEOND_PROCESS]
        return processes


    def create_chameleon_board(self):
        """Create a ChameleonBoard object with error recovery.

        This function will reboot the chameleon board once and retry if we can't
        create chameleon board.

        @return A ChameleonBoard object.
        """
        # TODO(waihong): Add verify and repair logic which are required while
        # deploying to Cros Lab.
        try:
            chameleon_board = chameleon.ChameleonBoard(
                    self._chameleon_connection, self)
            return chameleon_board
        except Exception as e:
            raise ChameleonHostError('Can not create chameleon board: %s(%s)',
                                     e.__class__, e)


def create_chameleon_host(dut, chameleon_args):
    """Create a ChameleonHost object.

    There three possible cases:
    1) If the DUT is in Cros Lab and has a chameleon board, then create
       a ChameleonHost object pointing to the board. chameleon_args
       is ignored.
    2) If not case 1) and chameleon_args is neither None nor empty, then
       create a ChameleonHost object using chameleon_args.
    3) If neither case 1) or 2) applies, return None.

    @param dut: host name of the host that chameleon connects. It can be used
                to lookup the chameleon in test lab using naming convention.
                If dut is an IP address, it can not be used to lookup the
                chameleon in test lab.
    @param chameleon_args: A dictionary that contains args for creating
                           a ChameleonHost object,
                           e.g. {'chameleon_host': '172.11.11.112',
                                 'chameleon_port': 9992}.

    @returns: A ChameleonHost object or None.

    """
    if not utils.is_in_container():
        is_moblab = utils.is_moblab()
    else:
        is_moblab = _CONFIG.get_config_value(
                'SSP', 'is_moblab', type=bool, default=False)

    if not is_moblab:
        dut_is_hostname = not dnsname_mangler.is_ip_address(dut)
        if dut_is_hostname:
            chameleon_hostname = chameleon.make_chameleon_hostname(dut)
            if utils.host_is_in_lab_zone(chameleon_hostname):
                # Be more tolerant on chameleon in the lab because
                # we don't want dead chameleon blocks non-chameleon tests.
                # We use ssh ping here as BeyondCorp-only hosts cannot make ICMP
                # ping to chameleon test devices.
                try:
                    ssh_host.SSHHost(chameleon_hostname).ssh_ping()
                except (error.AutoservSSHTimeout,
                        error.AutoservSshPermissionDeniedError,
                        error.AutoservSshPingHostError) as e:
                    logging.warning(
                            'Chameleon %s is not accessible. Please file a bug'
                            ' to test lab: %s', chameleon_hostname, e)
                    return None
                return ChameleonHost(chameleon_host=chameleon_hostname)
        if chameleon_args:
            return ChameleonHost(**chameleon_args)
        else:
            return None
    else:
        afe = frontend_wrappers.RetryingAFE(timeout_min=5, delay_sec=10)
        hosts = afe.get_hosts(hostname=dut)
        if hosts and CHAMELEON_HOST_ATTR in hosts[0].attributes:
            return ChameleonHost(
                chameleon_host=hosts[0].attributes[CHAMELEON_HOST_ATTR],
                chameleon_port=hosts[0].attributes.get(
                    CHAMELEON_PORT_ATTR, 9992)
            )
        else:
            return None


def create_btpeer_host(dut, btpeer_args_list):
    """Create a ChameleonHost object for a Bluetooth peer

    This is similar to create_chameleon_host but unlike chameleon board
    there can be multiple btpeers with a single DUT

    There four possible cases:
    1) If the DUT is in Cros Lab then assume that it can have up to 4 bluetooth
       peers. Ping the url and create a Chameleon host for each Bluetooth peer
       present. btpeer_args_list is ignored.
    2) If not case 1) and btpeer_args_list is not empty, then
       create a BtpeerHost object for each host specified in btpeer_args_list.
    3) If neither case 1) or 2) applies, return None.
    4) This DUT is controlled  by moblab. This case is not implemented.


    @param dut: host name of the host that btpeer connects. It can be used
                to lookup the btpeer in test lab using naming convention.
                If dut is an IP address, it can not be used to lookup the
                btpeer in test lab. Naming convention in the lab is
                <hostname>-btpeer[1-4]
    @param btpeer_args_list: A list of dictionaries that contains args for
                            creating a BtpeerHost object,
                           e.g. {'btpeer_host': '172.11.11.112',
                                 'btpeer_port': 9992}.

    @returns: A list of BtpeerHost objects

    """
    def _convert_btpeer_args(args):
        """Convert btpeer args to format accepted by ChameleonHost."""
        ret_args = {}
        if 'btpeer_host' in args:
            ret_args['chameleon_host'] = args['btpeer_host']
        if 'btpeer_port' in args:
            ret_args['chameleon_port'] = int(args['btpeer_port'])
        if 'btpeer_ssh_port' in args:
            ret_args['port'] = int(args['btpeer_ssh_port'])
        return ret_args

    if not utils.is_in_container():
        is_moblab = utils.is_moblab()
    else:
        is_moblab = _CONFIG.get_config_value(
                'SSP', 'is_moblab', type=bool, default=False)

    btpeer_hosts = []

    if not is_moblab:
        if (not dnsname_mangler.is_ip_address(dut) and
            utils.host_is_in_lab_zone(dut)):
            # This is a device in the lab. Ignore any arguments passed and
            # derive peer hostnames from the DUT hostname
            btpeer_hostnames = chameleon.make_btpeer_hostnames(dut)
            for btpeer_hostname in btpeer_hostnames:
                # Not all test bed have 4 Bluetooth peers
                if utils.ping(btpeer_hostname, deadline=3):
                    logging.warning('Btpeer %s is not accessible. This maybe '
                                    'expected or it maybe an issue with the '
                                    'Bluetooth peer. Please Check the test bed.'
                                    , btpeer_hostname)
                    continue
                else:
                    logging.debug("Creating btpeer from %s",btpeer_hostname)
                    btpeer_hosts.append(
                        ChameleonHost(chameleon_host=btpeer_hostname))
            return btpeer_hosts
        else:
            # IP address given or DNS address is not in lab.
            # Create the Bluetooth peers from the arguments passed
            return [ ChameleonHost(**_convert_btpeer_args(btpeer_args))
                     for btpeer_args in btpeer_args_list]
    else:
        # TODO(b:149606762)
        # moblab still create Bluetooth peer from chameleon_args
        afe = frontend_wrappers.RetryingAFE(timeout_min=5, delay_sec=10)
        hosts = afe.get_hosts(hostname=dut)
        if hosts and CHAMELEON_HOST_ATTR in hosts[0].attributes:
            return [ChameleonHost(
                chameleon_host=hosts[0].attributes[CHAMELEON_HOST_ATTR],
                chameleon_port=hosts[0].attributes.get(
                    CHAMELEON_PORT_ATTR, 9992)
            )]
        else:
            return []
