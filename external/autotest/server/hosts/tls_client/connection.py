# Lint as: python2, python3
# Copyright 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""Abstract Client for Autotest side communications to the TLS Server."""

import grpc

import common

from autotest_lib.server.hosts.tls_client import autotest_common_pb2_grpc

TLS_PORT = 7152
TLS_IP = '10.254.254.254'


class TLSConnection(object):
    """The client side connection to Common-TLS service running in a drone."""

    def __init__(self):
        """Configure the grpc channel."""
        self.channel = grpc.insecure_channel('{}:{}'.format(TLS_IP, TLS_PORT))
        self.stub = autotest_common_pb2_grpc.CommonStub(self.channel)
        self.alive = True

    def __enter__(self):
        return self

    def __exit__(self, *exc):
        self.close()

    def close(self):
        """Close the grpc channel."""
        self.channel.close()
        self.alive = False
