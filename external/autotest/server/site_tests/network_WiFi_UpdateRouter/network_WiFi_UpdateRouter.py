# Lint as: python2, python3
# Copyright (c) 2014 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import collections
import logging

from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib import global_config
from autotest_lib.client.common_lib import utils
from autotest_lib.client.common_lib.cros import dev_server
from autotest_lib.client.common_lib.cros.network import ping_runner
from autotest_lib.server import hosts
from autotest_lib.server import site_linux_router
from autotest_lib.server import test
from autotest_lib.server.cros import dnsname_mangler
from autotest_lib.server.cros import provisioner
from autotest_lib.server.cros.network import wifi_test_context_manager


# Stable versions come from the builders.
# The builder version is used to build the URL of the corresponding image
# in Google Storage.
# The image version is a line from /etc/lsb-release in the corresponding image.
StableVersion = collections.namedtuple('StableVersion',
                                       ['builder_version', 'release_version'])

class network_WiFi_UpdateRouter(test.test):
    """Updates a router to the most recent stable version.

    This is not a test per se, since it does not test client behavior.  However
    it is advantageous to write this as a test so that we can schedule it to
    run periodically via the same infrastructure we use to run tests.

    Note that this test is very much patterned on provision_QuickProvision.

    """
    version = 1

    STABLE_VERSIONS = {
            ## crbug.com/1098024: these are left here as documentation of what the
            # last stable version is, but the current updater code does not support
            # them.
            'whirlwind':
            StableVersion('whirlwind-test-ap-tryjob/R85-13310.60.0-b4641849',
                          '13310.60.2020_08_25_0212'),
            'gale':
            StableVersion('gale-test-ap-tryjob/R92-13982.81.0-b4959409',
                          '13982.81.2021_08_11_1044'),
    }

    # List of files to remove.
    FILES_TO_REMOVE = ['/var/spool/crash/*', '/tmp/*',
                       '/var/lib/metrics/uma-events']


    def get_release_version(self, host):
        result = host.run('cat /etc/lsb-release')
        for line in result.stdout.splitlines():
            if line.startswith('CHROMEOS_RELEASE_VERSION='):
                return line.split('=', 1)[1]


    def get_update_url(self, ds_url, image):
        CONFIG = global_config.global_config
        IMAGE_URL_PATTERN = CONFIG.get_config_value(
                'CROS', 'image_url_pattern', type=str)
        return IMAGE_URL_PATTERN % (ds_url, image)


    def warmup(self, raw_cmdline_args):
        """Possibly parse the router hostname from the commandline.

        @param raw_cmdline_args raw input from autotest.

        """
        cmdline_args = utils.args_to_dict(raw_cmdline_args)
        logging.info('Running wifi test with commandline arguments: %r',
                     cmdline_args)
        self._router_hostname_from_cmdline = cmdline_args.get(
                wifi_test_context_manager.WiFiTestContextManager. \
                        CMDLINE_ROUTER_ADDR)


    def freeup_disk_space(self, device_host):
        """Remove files to free up disk space.

        @param device_host: router / pcap host object

        """
        for path in self.FILES_TO_REMOVE:
            device_host.run('rm -rf %s' % path, ignore_status=True)

    def stop_recover_duts(self, device_host):
        """Stop running recover_duts on the host.

        b/177380545: recover_duts is currently providing negative value on
        routers. TBD: decided whether we should re-enable this when router
        images are updated to fix hang issues?

        @param device_host: router / pcap host object
        """
        device_host.run('rm -f %s' % provisioner.LAB_MACHINE_FILE,
                        ignore_status=True)
        device_host.run('stop recover_duts', ignore_status=True)

    def run_once(self, host, is_pcap=False):
        """Update router / packet capture associated with host.

        @param host DUT connected to AP/Pcap that needs update

        """
        if is_pcap:
            device_hostname = dnsname_mangler.get_pcap_addr(
                    client_hostname=host.hostname)
        else:
            device_hostname = site_linux_router.build_router_hostname(
                client_hostname=host.hostname,
                router_hostname=self._router_hostname_from_cmdline)

        ping_helper = ping_runner.PingRunner()
        if not ping_helper.simple_ping(device_hostname):
            # Pcap devices aren't always present. Just claim Not Applicable if
            # we couldn't find it.
            e = error.TestNAError if is_pcap else error.TestError
            raise e('%s not found / is down.' % device_hostname)

        # Use CrosHost for all router/pcap hosts and avoid host detection.
        # Host detection would use JetstreamHost for Whirlwind routers.
        # JetstreamHost assumes ap-daemons are running.
        # Testbed routers run the testbed-ap profile with no ap-daemons.
        # TODO(ecgh): crbug.com/757075 Fix testbed-ap JetstreamHost detection.
        device_host = hosts.create_host(device_hostname,
                                        host_class=hosts.CrosHost,
                                        allow_failure=True)

        # Stop recover_duts now, for cases where we don't go through a full
        # update below.
        self.stop_recover_duts(device_host)

        # Remove un-wanted files to freeup diskspace before starting update.
        self.freeup_disk_space(device_host)
        self.update_device(device_host)

        # Stop recover_duts again, in case provisioning re-enabled it.
        self.stop_recover_duts(device_host)

    def update_device(self, device_host):
        """Update router and pcap associated with host.

        @param device_host: router / pcap host object
        @param device_board: router / pcap board name

        """
        device_board = device_host.get_board().split(':', 1)[1]
        desired = self.STABLE_VERSIONS.get(device_board, None)
        if desired is None:
            raise error.TestFail('No stable version found for %s with board=%s.'
                                 % (device_host.hostname, device_board))

        logging.info('Checking whether %s is at the latest stable version: %s',
                     device_host.hostname, desired.release_version)
        current_release_version = self.get_release_version(device_host)
        if desired.release_version == current_release_version:
            raise error.TestNAError('%s is already at latest version %s.' %
                                    (device_host.hostname,
                                     desired.release_version))

        logging.info('Updating %s to image %s from %s',
                     device_host.hostname, desired.release_version,
                     current_release_version)
        try:
            ds = dev_server.ImageServer.resolve(desired.builder_version,
                                                device_host.hostname)
        except dev_server.DevServerException as e:
            logging.error(e)
            raise error.TestFail(str(e))

        url = self.get_update_url(ds.url(), desired.builder_version)
        provisioner.ChromiumOSProvisioner(url,
                                          host=device_host).run_provision()
