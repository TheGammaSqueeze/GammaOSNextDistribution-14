# Lint as: python2, python3
# Copyright (c) 2013 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging
from six.moves import http_client as httplib
import socket
import time
from six.moves import xmlrpc_client as xmlrpclib

from autotest_lib.client.cros.faft.config import Config as ClientConfig
from autotest_lib.server import autotest


class _Method(object):
    """Class to save the name of the RPC method instead of the real object.

    It keeps the name of the RPC method locally first such that the RPC method
    can be evalulated to a real object while it is called. Its purpose is to
    refer to the latest RPC proxy as the original previous-saved RPC proxy may
    be lost due to reboot.

    The call_method is the method which does refer to the latest RPC proxy.
    """
    def __init__(self, call_method, name):
        self.__call_method = call_method
        self.__name = name

    def __getattr__(self, name):
        # Support a nested method.
        return _Method(self.__call_method, "%s.%s" % (self.__name, name))

    def __call__(self, *args, **dargs):
        return self.__call_method(self.__name, *args, **dargs)

    def __repr__(self):
        """Return a description of the method object"""
        return "%s('%s')" % (self.__class__.__name__, self.__name)

    def __str__(self):
        """Return a description of the method object"""
        return "<%s '%s'>" % (self.__class__.__name__, self.__name)


class RPCProxy(object):
    """Proxy to the FAFTClient RPC server on DUT.

    It acts as a proxy to the FAFTClient on DUT. It is smart enough to:
     - postpone the RPC connection to the first class method call;
     - reconnect to the RPC server in case connection lost, e.g. reboot;
     - always call the latest RPC proxy object.

     @ivar _client: the ssh host object
     @type host: autotest_lib.server.hosts.abstract_ssh.AbstractSSHHost
     @ivar _faft_client: the real serverproxy to use for calls
     @type _faft_client: xmlrpclib.ServerProxy | None
    """
    _client_config = ClientConfig()

    def __init__(self, host):
        """Constructor.

        @param host: The host object, passed via the test control file.
        """
        self.host = host
        self._faft_client = None
        self.logfiles = []

    def __del__(self):
        self.disconnect()

    def __getattr__(self, name):
        """Return a _Method object only, not its real object."""
        return _Method(self.__call_faft_client, name)

    def __call_faft_client(self, name, *args, **dargs):
        """Make the call on the latest RPC proxy object.

        This method gets the internal method of the RPC proxy and calls it.

        @param name: Name of the RPC method, a nested method supported.
        @param args: The rest of arguments.
        @param dargs: The rest of dict-type arguments.
        @return: The return value of the FAFTClient RPC method.
        """
        if self._faft_client is None:
            self.connect()
        try:
            return getattr(self._faft_client, name)(*args, **dargs)
        except (socket.error,
                httplib.BadStatusLine,
                xmlrpclib.ProtocolError):
            # Reconnect the RPC server in case connection lost, e.g. reboot.
            self.connect()
            # Try again.
            return getattr(self._faft_client, name)(*args, **dargs)

    def connect(self):
        """Connect the RPC server."""
        # Make sure Autotest dependency is there.
        autotest.Autotest(self.host).install()
        logfile = "%s.%s" % (self._client_config.rpc_logfile, time.time())
        self.logfiles.append(logfile)
        self._faft_client = self.host.rpc_server_tracker.xmlrpc_connect(
                self._client_config.rpc_command,
                self._client_config.rpc_port,
                command_name=self._client_config.rpc_command_short,
                ready_test_name=self._client_config.rpc_ready_call,
                timeout_seconds=self._client_config.rpc_timeout,
                logfile=logfile,
                server_desc=str(self),
                request_timeout_seconds=self._client_config.
                rpc_request_timeout,
        )

    def disconnect(self):
        """Disconnect the RPC server."""
        # The next start of the RPC server will terminate any leftovers,
        # so no need to pkill upon disconnect.
        if self._faft_client is not None:
            logging.debug("Closing FAFT RPC server connection.")
        self.host.rpc_server_tracker.disconnect(self._client_config.rpc_port,
                                                pkill=False)
        self._faft_client = None

    def quit(self):
        """Tell the RPC server to quit, then disconnect from it."""
        if self._faft_client is None:
            return
        logging.debug("Telling FAFT RPC server to quit.")
        try:
            remote_quit = getattr(
                    self._faft_client, self._client_config.rpc_quit_call)
            remote_quit()
            need_pkill = False
        except Exception as e:
            logging.warning("Error while telling FAFT RPC server to quit: %s", e)
            # If we failed to tell the RPC server to quit for some reason,
            # fall back to SIGTERM, because it may not have exited.
            need_pkill = True

        self.host.rpc_server_tracker.disconnect(self._client_config.rpc_port,
                                                pkill=need_pkill)
        self._faft_client = None

    def collect_logfiles(self, dest):
        """Download all logfiles from the DUT, then delete them."""
        if self.logfiles:
            for logfile in self.logfiles:
                if self.host.run("test -f", args=[logfile],
                                 ignore_status=True).exit_status == 0:
                    self.host.get_file(logfile, dest)
                    self.host.run("rm -f", ignore_status=True, args=[logfile])
            self.logfiles.clear()

    def __repr__(self):
        """Return a description of the proxy object"""
        return '%s(%s)' % (self.__class__.__name__, self.host)

    def __str__(self):
        """Return a description of the proxy object"""
        return "<%s '%s:%s'>" % (self.__class__.__name__, self.host.hostname,
                                 self._client_config.rpc_port)
