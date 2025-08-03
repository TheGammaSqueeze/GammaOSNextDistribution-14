# Lint as: python2, python3
# Copyright 2019 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Wrapper test to run verification on a labstation."""

import json
import logging
import os
import re
import time

from autotest_lib.client.common_lib import error
from autotest_lib.server import test
from autotest_lib.server import utils as server_utils
from autotest_lib.server import site_utils
from autotest_lib.server.hosts import servo_host as _servo_host
from autotest_lib.server.hosts import servo_constants
from autotest_lib.server.hosts import factory
from autotest_lib.server.hosts import host_info


class servo_LabstationVerification(test.test):
    """Wrapper test to run verifications on a labstation image.

    This test verifies basic servod behavior on the host supplied to it e.g.
    that servod can start etc, before inferring the DUT attached to the servo
    device, and running more comprehensive servod tests by using a full
    cros_host and servo_host setup.
    """
    version = 1

    UL_BIT_MASK = 0x2

    # Regex to match ipv4 byte.
    IPV4_RE_BLOCK = r'(25[0-5]|2[0-4][0-9]|[01]?[0-9]?[0-9])'

    # Full regex to match an ipv4 with optional subnet mask.
    RE_IPV4 = re.compile(r'^(%(block)s\.){3}(%(block)s)(/\d+)?$' %
                         {'block':IPV4_RE_BLOCK})

    # Timeout in seconds to wait after cold_reset before attempting to ping
    # again. This includes a potential fw screen (30s), and some buffer
    # for the network.
    RESET_TIMEOUT_S = 60

    def get_servo_mac(self, servo_proxy):
        """Given a servo's serial retrieve ethernet port mac address.

        @param servo_proxy: proxy to talk to servod

        @returns: mac address of the ethernet port as a string
        @raises: error.TestError: if mac address cannot be inferred
        """
        # TODO(coconutruben): once mac address retrieval through v4 is
        # implemented remove these lines of code, and replace with
        # servo_v4_eth_mac.
        try:
            serial = servo_proxy.get('support.serialname')
            if serial == 'unknown':
                serial = servo_proxy.get('serialname')
        except error.TestFail as e:
            if 'No control named' in e:
                serial = servo_proxy.get('serialname')
            else:
                raise e
        ctrl_path = os.path.join(os.path.dirname(os.path.realpath(__file__)),
                                 'serial_to_mac_map.json')
        with open(ctrl_path, 'r') as f:
            serial_mac_map = json.load(f)
        if not serial in serial_mac_map:
            raise error.TestError('Unable to retrieve mac address for '
                                  'serial %s' % serial)
        return str(serial_mac_map[serial])

    def _flip_UL_bit(self, byte):
        """Helper to flip the Universal/Local bit in a given byte.

        For some IPv6's extended unique identifier (EUI) 64 calculation
        part of the logic is to flip the U/L bit on the first byte.

        Note: it is the callers responsibility to ensure that |byte| is
        only one byte. This function will just flip the 7th bit of whatever
        is supplied and return that.

        @param byte: the byte to flip

        @returns: |byte| with it's U/L bit flipped.
        """
        return byte ^ self.UL_BIT_MASK

    def _from_mac_to_ipv6_eui_64(self, mac):
        """Convert a MAC address (IEEE EUI48) to a IEEE EUI64 node component.

        This follows guidelines to convert a mac address to an IPv6 node
        component by
        - splitting the mac into two parts
        - inserting 0xfffe in between the two parts
        - flipping the U/L bit on the first byte

        @param mac: string containing the mac address

        @returns: string containing the IEEE EUI64 node component to |mac|
        """
        mac_bytes = [b.lower() for b in mac.split(':')]
        # First, flip the 7th bit again. This converts the string coming from
        # the mac (as it's a hex) into an int, flips it, before casting it back
        # to a hex as is expected for the mac address.
        mac_bytes[0] = hex(self._flip_UL_bit(int(mac_bytes[0],16)))[2:]
        mac_bytes = (mac_bytes[:3] + ['ff', 'fe'] + mac_bytes[-3:])
        ipv6_components = []
        while mac_bytes:
            # IPv6 has two bytes between :
            ipv6_components.append('%s%s' % (mac_bytes.pop(0),
                                             mac_bytes.pop(0)))
        # Lastly, remove the leading 0s to have a well formatted concise IPv6.
        return ':'.join([c.lstrip('0') for c in ipv6_components])

    def _mac_to_ipv6_addr(self, mac, ipv6_network_component):
        """Helper to generate an IPv6 address given network component and mac.

        @param mac: the mac address of the target network interface
        @param ipv6_network_component: prefix + subnet id portion of IPv6 [:64]

        @returns: an IPv6 address that could be used to target the network
                  interface at |mac| if it's on the same network as the network
                  component indicates
        """
        # Do not add an extra/miss a ':' when glueing both parts together.
        glue = '' if ipv6_network_component[-1] == ':' else ':'
        return '%s%s%s' % (ipv6_network_component, glue,
                           self._from_mac_to_ipv6_eui_64(mac))

    def _from_ipv6_to_mac_address(self, ipv6):
        """Given an IPv6 address retrieve the mac address.

        Assuming the address at |ipv6| followed the conversion standard layed
        out at _from_mac_to_ipv6_eui_64() above, this helper does the inverse.

        @param ipv6: full IPv6 address to extract the mac address from

        @returns: mac address extracted from node component as a string
        """
        # The node component i.e. the one holding the mac info is the 64 bits.
        components = ipv6.split(':')[-4:]
        # This is reversing the EUI 64 logic.
        mac_bytes = []
        for component in components:
            # Expand the components fully again.
            full_component = component.rjust(4,'0')
            # Mac addresses use one byte components as opposed to the two byte
            # ones for IPv6 - split them up.
            mac_bytes.extend([full_component[:2], full_component[2:]])
        # First, flip the 7th bit again.
        mac_bytes[0] = self._flip_UL_bit(mac_bytes[0])
        # Second, remove the 0xFFFE bytes inserted in the middle again.
        mac_bytes = mac_bytes[:3] + mac_bytes[-3:]
        return ':'.join([c.lower() for c in mac_bytes])

    def _build_ssh_cmd(self, hostname, cmd):
        """Build the ssh command to run |cmd| via bash on |hostname|.

        @param hostname: hostname/ip where to run the cmd on
        @param cmd: cmd on hostname to run

        @returns: ssh command to run
        """
        ssh_cmd = [r'ssh', '-q', '-o', 'StrictHostKeyChecking=no',
                   r'-o', 'UserKnownHostsFile=/dev/null',
                   r'root@%s' % hostname,
                   r'"%s"' % cmd]
        return ' '.join(ssh_cmd)

    def _ip_info_from_host(self, host, ip, info, host_name):
        """Retrieve some |info| related to |ip| from host on |ip|.

        @param host: object that implements 'run', where the command
                     will be executed form
        @param ip: ip address to run on and to filter for
        @param info: one of 'ipv4' or 'dev'
        @param host_name: executing host's name, for error message

        @returns: ipv4 associated on the same nic as |ip| if |info|== 'ipv4'
                  nic dev name associated with |ip| if |info|== 'dev'

        @raises error.TestError: if output of 'ip --brief addr' is unexpected
        @raises error.TestError: info not in ['ipv4', 'dev']
        """
        if info not in ['ipv4', 'dev']:
            raise error.TestFail('Cannot retrieve info %r', info)
        ip_stub = r"ip --brief addr | grep %s" % ip
        cmd = self._build_ssh_cmd(ip, ip_stub)
        logging.info('command to find %s on %s: %s', info, host_name, cmd)
        # The expected output here is of the form:
        # [net device] [UP/DOWN] [ipv4]/[subnet mask] [ipv6]/[subnet mask]+
        try:
            output = host.run(cmd).stdout.strip()
        except (error.AutoservRunError, error.CmdError) as e:
            logging.error(str(e))
            raise error.TestFail('Failed to retrieve %s on %s' % (info, ip))
        logging.debug('ip raw output: %s', output)
        components = output.split()
        if info == 'ipv4':
            # To be safe, get all IPs, and subsequently report the first ipv4
            # found.
            raw_ips = components[2:]
            for raw_ip in raw_ips:
                if re.match(self.RE_IPV4, raw_ip):
                    ret = raw_ip.split('/')[0]
                    logging.info('ipv4 found: %s', ret)
                    break
            else:
                raise error.TestFail('No ipv4 address found in ip command: %s' %
                                     ', '.join(raw_ips))
        if info == 'dev':
            ret = components[0]
            logging.info('dev found: %s', ret)
        return ret

    def get_dut_on_servo_ip(self, servo_host_proxy):
        """Retrieve the IPv4 IP of the DUT attached to a servo.

        Note: this will reboot the DUT if it fails initially to get the IP
        Note: for this to work, servo host and dut have to be on the same subnet

        @param servo_host_proxy: proxy to talk to the servo host

        @returns: IPv4 address of DUT attached to servo on |servo_host_proxy|

        @raises error.TestError: if the ip cannot be inferred
        """
        # Note: throughout this method, sh refers to servo host, dh to DUT host.
        # Figure out servo hosts IPv6 address that's based on its mac address.
        servo_proxy = servo_host_proxy._servo
        sh_ip = server_utils.get_ip_address(servo_host_proxy.hostname)
        sh_nic_dev = self._ip_info_from_host(servo_host_proxy, sh_ip, 'dev',
                                             'servo host')
        addr_cmd ='cat /sys/class/net/%s/address' % sh_nic_dev
        sh_dev_addr = servo_host_proxy.run(addr_cmd).stdout.strip()
        logging.debug('Inferred Labstation MAC to be: %s', sh_dev_addr)
        sh_dev_ipv6_stub = self._from_mac_to_ipv6_eui_64(sh_dev_addr)
        # This will get us the IPv6 address that uses the mac address as node id
        cmd = (r'ifconfig %s | grep -oE "([0-9a-f]{0,4}:){4}%s"' %
               (sh_nic_dev, sh_dev_ipv6_stub))
        servo_host_ipv6 = servo_host_proxy.run(cmd).stdout.strip()
        logging.debug('Inferred Labstation IPv6 to be: %s', servo_host_ipv6)
        # Figure out DUTs expected IPv6 address
        # The network component should be shared between the DUT and the servo
        # host as long as they're on the same subnet.
        network_component = ':'.join(servo_host_ipv6.split(':')[:4])
        dut_ipv6 = self._mac_to_ipv6_addr(self.get_servo_mac(servo_proxy),
                                          network_component)
        logging.info('Inferred DUT IPv6 to be: %s', dut_ipv6)
        # Dynamically generate the correct shell-script to retrieve the ipv4.
        try:
            server_utils.run('ping -6 -c 1 -w 35 %s' % dut_ipv6)
        except error.CmdError:
            # If the DUT cannot be pinged, then try to reset it and try to
            # ping again.
            logging.info('Failed to ping DUT on ipv6: %s. Cold resetting',
                         dut_ipv6)
            servo_proxy.get_power_state_controller().reset()
            time.sleep(self.RESET_TIMEOUT_S)
        dut_ipv4 = None
        try:
            # Pass |server_utils| here as it implements the same interface
            # as a host to run things locally i.e. on the autoserv runner.
            dut_ipv4 = self._ip_info_from_host(server_utils, dut_ipv6, 'ipv4',
                                               'autoserv')
            return dut_ipv4
        except error.TestFail:
            logging.info('Failed to retrieve the DUT ipv4 directly. '
                         'Going to attempt to tunnel request through '
                         'labstation and forgive the error for now.')
        # Lastly, attempt to run the command from the labstation instead
        # to guard against networking issues.
        dut_ipv4 = self._ip_info_from_host(servo_host_proxy, dut_ipv6, 'ipv4',
                                           'autoserv')
        return dut_ipv4

    def _set_dut_stable_version(self, dut_host, stable_version=None):
        """Helper method to set stable_version in DUT host.

        @param dut_host: CrosHost object representing the DUT.
        """
        if not stable_version:
            stable_version = self.cros_version
        logging.info('Setting stable_version to %s for DUT %s.',
                     stable_version, dut_host.hostname)
        info = dut_host.host_info_store.get()
        info.stable_versions['cros'] = stable_version
        dut_host.host_info_store.commit(info)

    def _get_dut_info_from_config(self):
        """Get DUT info from json config file.

        @returns a list of dicts that each dict represents a dut.
        """
        ctrl_path = os.path.join(os.path.dirname(os.path.realpath(__file__)),
                                 'labstation_to_dut_map.json')
        with open(ctrl_path, 'r') as f:
            data = json.load(f, object_hook=self._byteify)
            # create a default dut dict in case the servohost is not in config
            # map, this is normally happened in local testing.
            default_dut = {
                'hostname': None,
                'servo_port': '9999',
                'servo_serial': None
            }
            return data.get(self.labstation_host.hostname, [default_dut])

    def _byteify(self, data, ignore_dicts=False):
        """Helper method to convert unicode to string.
        """
        # In python2 we need to convert unicode into str, however in python3
        # unicode renamed to str so json.load will load data into str by
        # default so run encode with a str in python3 will actually convert
        # the data into bytes which we don't want. To make the test compatible
        # to both python2 and python3, we check data type != dict, list, str
        # for now. # TODO(xianuowang@): remove this method once we fully
        # migrated to python3.
        if type(data) not in {dict, list, str}:
            return data.encode('utf-8')
        if isinstance(data, list):
            return [self._byteify(item, ignore_dicts=True) for item in data]
        if isinstance(data, dict) and not ignore_dicts:
            return {
                self._byteify(key, ignore_dicts=True):
                    self._byteify(value, ignore_dicts=True)
                for key, value in list(data.items())
            }
        return data

    def _setup_servod(self):
        """Setup all servod instances under servohost for later testing.
        """
        for dut in self.dut_list:
            # Use board: nami as default for local testing.
            board = dut.get('board', 'nami')
            port = dut.get('servo_port')
            serial = dut.get('servo_serial')
            servo_args = {
                    servo_constants.SERVO_HOST_ATTR:
                    self.labstation_host.hostname,
                    servo_constants.SERVO_PORT_ATTR: port,
                    servo_constants.SERVO_SERIAL_ATTR: serial,
                    servo_constants.SERVO_BOARD_ATTR: board,
                    servo_constants.ADDITIONAL_SERVOD_ARGS: 'DUAL_V4=1',
                    'is_in_lab': False,
            }

            logging.info('Setting up servod for port %s', port)
            # We need try_lab_servo option here, so servo firmware will get
            # updated before run tests.
            servo_host, _ = _servo_host.create_servo_host(None,
                                                          servo_args,
                                                          try_lab_servo=True)
            try:
                validate_cmd = 'servodutil show -p %s' % port
                servo_host.run_grep(validate_cmd,
                    stdout_err_regexp='No servod scratch entry found.')
            except error.AutoservRunError:
                raise error.TestFail('Servod of port %s did not come up on'
                                     ' labstation.' % port)

            self.servo_hosts.append(servo_host)

    def setup_hosts(self):
        """Prepare all cros and servo hosts that need to run."""
        # Servod came up successfully at this point - build a ServoHost and
        # CrosHost for later testing to verfiy servo functionality.

        for dut_info, servo_host in zip(self.dut_list, self.servo_hosts):
            dut_hostname = dut_info.get('hostname')
            if not dut_hostname:
                # TODO(coconutruben@): remove this statement once the inferring
                # is the default.
                logging.info('hostname not specified for DUT, through '
                             'static config or command-line. Will attempt '
                             'to infer through hardware address.')
                dut_hostname = self.get_dut_on_servo_ip(servo_host)
            labels = []
            if dut_info.get('board'):
                labels.append('board:%s' % dut_info.get('board'))
            if dut_info.get('model'):
                labels.append('model:%s' % dut_info.get('model'))
            info = host_info.HostInfo(labels=labels)
            host_info_store = host_info.InMemoryHostInfoStore(info=info)
            machine = {
                    'hostname': dut_hostname,
                    'host_info_store': host_info_store,
                    'afe_host': site_utils.EmptyAFEHost()
            }
            dut_host = factory.create_host(machine)
            dut_host.set_servo_host(servo_host)

            # Copy labstation's stable_version to dut_host for later test
            # consume.
            # TODO(xianuowang@): remove this logic once we figured out how to
            # propagate DUT's stable_version to the test.
            stable_version_from_config = dut_info.get('stable_version')
            self._set_dut_stable_version(dut_host, stable_version_from_config)
            # Store |dut_host| in |machine_dict| so that parallel running can
            # find the host.
            self.machine_dict[dut_host.hostname] = dut_host

    def initialize(self, host, config=None, local=False):
        """Setup servod on |host| to run subsequent tests.

        @param host: LabstationHost object representing the servohost.
        @param config: the args argument from test_that in a dict.
        @param local: whether a test image is already on the usb stick.
        """
        # Cache whether this is a local run or not.
        self.local = local
        # This list hosts the servo_hosts, in the same order as the |dut_list|
        # below.
        self.servo_hosts = []
        # This dict houses a mapping of |dut| hostnames to initialized cros_host
        # objects for the tests to run.
        self.machine_dict = {}
        # Save the host.
        self.labstation_host = host
        # Make sure recovery is quick in case of failure.
        self.job.fast = True
        # Get list of duts under the servohost.
        self.dut_list = self._get_dut_info_from_config()
        # Setup servod for all duts.
        self._setup_servod()
        # We need a cros build number for testing download image to usb and
        # use servo to reimage DUT purpose. So copying labstation's
        # stable_version here since we don't really care about which build
        # to install on the DUT.
        self.cros_version = (
            self.labstation_host.host_info_store.get().cros_stable_version)

        if config:
            if 'dut_ip' in config:
                # Retrieve DUT ip from args if caller specified it.
                # |dut_ip| is special in that it can be used for (quick) setup
                # testing if the setup is not in the configuration file.
                # This has two implications:
                # - the user can only test one dut/servo pair
                # - the config has to be empty.
                # TODO(coconutruben): remove this logic for a more holistic
                # command-line overwrite solution.
                if len(self.dut_list) == 1 and not self.dut_list[0]['hostname']:
                    self.dut_list[0]['hostname'] = config['dut_ip']
                    logging.info('Setting the hostname of the only dut to %s.',
                                 self.dut_list[0]['hostname'])
                else:
                    logging.info('dut_ip %s will be ignored. The target '
                                 'labstation is to be part of static config.')
            if 'cros_version' in config:
                # We allow user to override a cros image build.
                self.cros_version = config['cros_version']
        # Lastly, setup the hosts so that testing can occur in parallel.
        self.setup_hosts()

    def _run_on_machine(self, machine):
        """Thin wrapper to run 'servo_Verification' on all machines.

        @param machine: hostname of the dut to run 'servo_Verification' against.

        @raises error.TestFail: 'servo_Verification' fails
        @raises error.TestFail: |machine| unknown (not in |self.machine_dict|)
        """
        dut_host = self.machine_dict.get(machine, None)
        if dut_host is None:
            raise error.TestFail('dut machine %r not known to suite. Known '
                                 'machines: %r', machine,
                                 ', '.join(list(self.machine_dict.keys())))
        logging.info('About to run on machine %s', machine)
        if not self.job.run_test('servo_Verification', host=dut_host,
                                 local=self.local):
            raise error.TestFail('At least one test failed.')

    def run_once(self):
        """Run through all hosts in |self.machine_dict|."""
        self.job.parallel_simple(self._run_on_machine,
                                 list(self.machine_dict.keys()))
        # TODO(coconutruben): at this point, you can print a report what kind of
        # servod setups failed and which succeeded. Build that out so that
        # debugging failures is cleaner given multiple setups.

    def cleanup(self):
        """Clean up by calling close for dut host, which will also take care
        of servo cleanup.
        """
        for _, dut in list(self.machine_dict.items()):
            dut.close()
