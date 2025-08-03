#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# Copyright 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# pylint: disable=module-missing-docstring,class-missing-docstring

import grpc
import unittest

import callbox_server

from chromiumos.test.api import callbox_service_pb2 as cbp
from chromiumos.test.api import callbox_service_pb2_grpc as cbs


class CallboxServerTest(unittest.TestCase):
    def test_check_health(self):
        server = callbox_server.serve()
        with grpc.insecure_channel('localhost:50051') as channel:
            client = cbs.CallboxServiceStub(channel)
            client.CheckHealth(cbp.CheckHealthRequest())
        server.stop(grace=1).wait()


if __name__ == '__main__':
    unittest.main()
