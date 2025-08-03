# Lint as: python2, python3
# Copyright 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""Communication with the TLS FakeOmaha Service."""

import logging

import common

from autotest_lib.server.hosts.tls_client import autotest_common_pb2
from autotest_lib.client.common_lib import error

PAYLOAD_TYPE = {
        'TYPE_UNSPECIFIED':
        autotest_common_pb2.FakeOmaha.Payload.TYPE_UNSPECIFIED,
        'FULL': autotest_common_pb2.FakeOmaha.Payload.FULL,
        'DELTA': autotest_common_pb2.FakeOmaha.Payload.DELTA
}


class TLSFakeOmaha():
    """Object for sending commands to a host, and getting the response."""

    def __init__(self, tlsconnection):
        """Configure the grpc channel."""
        if tlsconnection.alive:
            self.stub = tlsconnection.stub
        else:
            raise error.TLSConnectionError(
                    "TLS connection is not alive when try to creating"
                    " FakeOmaha client.")

        self.tlsconnection = tlsconnection
        self.fo_name = None

    def _make_payloads(self, payloads):
        """Serialize and return the list of payloads."""
        serialized_payloads = []
        for payload in payloads:
            serialized_payloads.append(
                    autotest_common_pb2.FakeOmaha.Payload(
                            id=payload['payload_id'],
                            type=PAYLOAD_TYPE[payload['payload_type']]))

        return serialized_payloads

    def start_omaha(self,
                    hostname,
                    target_build,
                    payloads,
                    exposed_via_proxy=False,
                    critical_update=False,
                    return_noupdate_starting=0):
        """Serialize and send the cmd to the TLS service.

        @param hostname: hostname of dut. Normally 'hostname' or 'self.hostname'
        @param target_build: full target build for the update. Example:

        @param payloads: list of the payloads in the format:
            [{'payload_id': <id>, 'payload_type': <type>}]
            example:
                [{'payload_id': 'ROOTFS', 'payload_type': 'FULL'},]
        @param exposed_via_proxy: bool indicates that the fake Omaha service is
            exposed to a DUT via a proxy server, instead of exposing to the DUT
                directly.
        @param critical_update:bool instructs the fake Omaha created that the
            update is critical.
        @param return_noupdate_starting: int indicates from which update check
            to start returning noupdate.

        @returns: the omaha_url
        """
        payload = self._make_payloads(payloads)

        target_build = autotest_common_pb2.ChromeOsImage(
                gs_path_prefix=target_build)
        fake_omaha = autotest_common_pb2.FakeOmaha(
                dut=hostname,
                target_build=target_build,
                payloads=payload,
                exposed_via_proxy=exposed_via_proxy,
                critical_update=critical_update,
                return_noupdate_starting=return_noupdate_starting)

        req = autotest_common_pb2.CreateFakeOmahaRequest(fake_omaha=fake_omaha)

        try:
            result = self.stub.CreateFakeOmaha(req)
            self.fo_name = result.name
            return result.omaha_url
        except Exception as e:
            logging.error("TLS FakeOmaha Debug String: %s",
                          e.debug_error_string())
            raise error.TestError(
                    "Could not start FakeOmaha Server because %s", e.details())

    def stop_omaha(self):
        """Delete the running Omaha Service."""
        if not self.fo_name:
            raise error.TestWarn(
                    "No FakeOmaha name specified, has it been started?")
        req = autotest_common_pb2.DeleteFakeOmahaRequest(name=self.fo_name)
        try:
            self.stub.DeleteFakeOmaha(req)
        except Exception as e:
            raise error.TestWarn("Could not stop FakeOmaha Server because %s",
                                 e.details())
