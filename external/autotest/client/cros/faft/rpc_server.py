#!/usr/bin/python3 -u
# Copyright (c) 2013 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""Exposes the FAFTClient interface over XMLRPC.

It launches an XMLRPC server and exposes the functions in RPCRouter().
"""

import logging
import os
from optparse import OptionParser

import common
from autotest_lib.client.cros import xmlrpc_server
from autotest_lib.client.cros.faft.utils import os_interface
from autotest_lib.client.common_lib import logging_config


def main():
    """The Main program, to run the XMLRPC server."""
    parser = OptionParser(usage='Usage: %prog [options]')
    parser.add_option(
            '--port',
            type='int',
            dest='port',
            default=9990,
            help='port number of XMLRPC server')
    (options, _) = parser.parse_args()

    config = logging_config.LoggingConfig()
    config.configure_logging(use_console=True, verbose=True)

    logging.debug('faft.rpc_server[%s] main...', os.getpid())
    xmlrpc_server.terminate_old(__file__)

    # Import after terminate, so old process is killed even if import fails
    from autotest_lib.client.cros.faft import rpc_functions

    # Launch the XMLRPC server to provide FAFTClient commands.
    os_if = os_interface.OSInterface()
    os.chdir(os_if.state_dir)

    server = xmlrpc_server.XmlRpcServer('localhost', options.port)
    router = rpc_functions.FaftXmlRpcDelegate(os_if)
    server.register_delegate(router)
    server.run()
    logging.debug('faft.rpc_server[%s] done.\n', os.getpid())


if __name__ == '__main__':
    main()
