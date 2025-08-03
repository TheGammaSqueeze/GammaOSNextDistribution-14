# Lint as: python2, python3
# Copyright 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""Autotest communcations to the Hosts (DUTs) via TLS ExecDutCommand."""

import common
import grpc
import logging
import six
import time

from autotest_lib.server.hosts.tls_client import autotest_common_pb2

from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib import utils


class TLSExecDutCommandClient():
    """Object for sending commands to a host, and getting the response."""

    def __init__(self, tlsconnection, hostname):
        """Configure the grpc channel."""
        if tlsconnection.alive:
            self.stub = tlsconnection.stub
        else:
            raise error.TLSConnectionError(
                "TLS connection is not alive when try to creating"
                " exec_dut_command client.")

        self.hostname = hostname
        self.tlsconnection = tlsconnection

    def run_cmd(self,
                cmd,
                timeout=120,
                stdout_tee=None,
                stderr_tee=None,
                ignore_timeout=False):
        """
        Run a command on the host configured during init.

        @param cmd: shell cmd to execute on the DUT
        @param: stdout_tee/stderr_tee: objects to write the data from the
            respective streams to
        @param timeout int(seconds): how long to allow the command to run
            before forcefully killing it.
        @param ignore_timeout: if True, do not raise err on timeouts.
        """
        if not self.tlsconnection.alive:
            error.TLSConnectionError(
                "TLS connection is not up when try to run exec_dut_command.")
        result = utils.CmdResult(command=cmd)
        try:
            self._run(cmd, stdout_tee, stderr_tee, result, timeout)
        except grpc.RpcError as e:
            if e.code().name == "DEADLINE_EXCEEDED":
                if ignore_timeout:
                    return None
                raise error.CmdTimeoutError(
                        cmd, result,
                        "Command(s) did not complete within %d seconds" %
                        timeout)
            raise e
        except Exception as e:
            raise e
        return result

    def _run(self, cmd, stdout_tee, stderr_tee, result, timeout):
        """Run the provided cmd, populate the result in place."""
        start_time = time.time()
        response = self._send_cmd(cmd, timeout)

        stdout_buf = six.StringIO()
        stderr_buf = six.StringIO()
        last_status = 0

        if response:
            for item in response:
                last_status = item.exit_info.status
                _log_item(item.stdout, stdout_buf, stdout_tee)
                _log_item(item.stderr, stderr_buf, stderr_tee)

        result.stdout = stdout_buf.getvalue()
        result.stderr = stderr_buf.getvalue()
        result.exit_status = last_status
        result.duration = time.time() - start_time

    def _send_cmd(self, cmd, timeout):
        """Serialize and send the cmd to the TLS service."""
        formatted_cmd = autotest_common_pb2.ExecDutCommandRequest(
                name=self.hostname, command=cmd)
        return self.stub.ExecDutCommand(formatted_cmd, timeout=timeout)


def _log_item(item, buf, tee):
    """
    Parse the provided item.

    If the item exists, append the provided arr with the item & write to
        the provided tee if provided.

    """
    if not item:
        return
    # TODO dbeckett@ (crbug.com/990593), adjust this to be PY3 compatible.
    buf.write(item)
    if tee is not None and tee is not utils.TEE_TO_LOGS:
        tee.write(item)
