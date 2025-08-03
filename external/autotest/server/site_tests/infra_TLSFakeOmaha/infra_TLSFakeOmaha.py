# Copyright 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from autotest_lib.client.common_lib import error
from autotest_lib.server import test
from autotest_lib.server.hosts.tls_client import connection
from autotest_lib.server.hosts.tls_client import fake_omaha


class infra_TLSFakeOmaha(test.test):
    """
    Start the TLS FakeOmaha service and ensure a URL is returned.

    """

    version = 1

    def run_once(self, host, case):
        """
        Run the test.

        @param host: A host object representing the DUT.
        @param case: The case to run.

        """
        tlsconn = connection.TLSConnection()
        self.fake_omaha = fake_omaha.TLSFakeOmaha(tlsconn)
        self.host = host

        # Run the case
        eval("self._%s()" % case)

    def _basic(self):
        """Run the test with the minimum number of flags."""
        fake_omaha_url = self.fake_omaha.start_omaha(
                self.host.hostname,
                target_build=
                'gs://chromeos-image-archive/eve-release/R87-13457.0.0',
                payloads=[{
                        'payload_id': 'ROOTFS',
                        'payload_type': 'FULL'
                }])
        if fake_omaha_url is None or fake_omaha_url == '':
            raise error.TestFail("No url returned from fake_omaha")
        if 'http://' not in fake_omaha_url:
            raise error.TestFail("fake_omaha returned invalid update url: %s" %
                                 fake_omaha_url)

    def _full(self):
        """Run the test with the none-default flags."""
        fake_omaha_url = self.fake_omaha.start_omaha(
                self.host.hostname,
                target_build=
                'gs://chromeos-image-archive/eve-release/R87-13457.0.0',
                payloads=[{
                        'payload_id': 'ROOTFS',
                        'payload_type': 'FULL'
                }],
                exposed_via_proxy=True,
                critical_update=True,
                return_noupdate_starting=1)

        critical_tag = 'critical_update=True'
        no_update_tag = '&no_update=True'
        none_proxy_url = 'http://127.0.0.1'
        if critical_tag not in fake_omaha_url:
            raise error.TestFail("fake_omaha returned invalid update url: %s"
                                 " Expected %s in url." %
                                 (fake_omaha_url, critical_tag))

        if no_update_tag not in fake_omaha_url:
            raise error.TestFail("fake_omaha returned invalid update url: %s"
                                 " Expected %s in url." %
                                 (fake_omaha_url, no_update_tag))

        if none_proxy_url in fake_omaha_url:
            raise error.TestFail("fake_omaha returned invalid update url: %s"
                                 " Expected %s NOT in url." %
                                 (fake_omaha_url, none_proxy_url))
