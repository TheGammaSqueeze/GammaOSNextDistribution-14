# Copyright 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import grpc

from chromiumos.test.api import callbox_service_pb2 as cbp
from chromiumos.test.api import callbox_service_pb2_grpc as cbs

from concurrent import futures


class CallBoxServer(cbs.CallboxServiceServicer):
    """Implements the callbox_service.proto API"""

    def CheckHealth(self, request, context):
        """ Basic endpoint to check the service is up """
        return cbp.CheckHealthResponse()


def serve():
    """Start/run the server with a single worker thread"""
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=1))
    cbs.add_CallboxServiceServicer_to_server(CallBoxServer(), server)
    server.add_insecure_port('[::]:50051')
    server.start()
    return server


if __name__ == '__main__':
    server = serve()
    server.wait_for_termination()
