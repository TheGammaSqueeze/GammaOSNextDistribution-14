# Lint as: python2, python3
# Copyright (c) 2015 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import six.moves.http_client
import logging
import socket
import tempfile
import time
import six.moves.xmlrpc_client

import common
from autotest_lib.client.bin import utils
from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib.cros import retry


class RpcServerTracker(object):
    """
    This class keeps track of all the RPC server connections started on a
    remote host. The caller can use either |xmlrpc_connect| to start the
    required type of rpc server on the remote host.
    The host will cleanup all the open RPC server connections on disconnect.
    """

    _RPC_PROXY_URL_FORMAT = 'http://localhost:%d'
    _RPC_HOST_ADDRESS_FORMAT = 'localhost:%d'
    _RPC_SHUTDOWN_POLLING_PERIOD_SECONDS = 2
    _RPC_SHUTDOWN_TIMEOUT_SECONDS = 10

    def __init__(self, host):
        """
        @param port: The host object associated with this instance of
                     RpcServerTracker.
        """
        self._host = host
        self._rpc_proxy_map = {}


    def _setup_port(self, port, command_name, remote_pid=None):
        """Sets up a tunnel process and register it to rpc_server_tracker.

        ChromeOS on the target closes down most external ports for security.
        We could open the port, but doing that would conflict with security
        tests that check that only expected ports are open.  So, to get to
        the port on the target we use an ssh tunnel.

        This method assumes that xmlrpc and jsonrpc never conflict, since
        we can only either have an xmlrpc or a jsonrpc server listening on
        a remote port. As such, it enforces a single proxy->remote port
        policy, i.e if one starts a jsonrpc proxy/server from port A->B,
        and then tries to start an xmlrpc proxy forwarded to the same port,
        the xmlrpc proxy will override the jsonrpc tunnel process, however:

        1. None of the methods on the xmlrpc proxy will work because
        the server listening on B is jsonrpc.

        2. The xmlrpc client cannot initiate a termination of the JsonRPC
        server, as the only use case currently is goofy, which is tied to
        the factory image. It is much easier to handle a failed xmlrpc
        call on the client than it is to terminate goofy in this scenario,
        as doing the latter might leave the DUT in a hard to recover state.

        With the current implementation newer rpc proxy connections will
        terminate the tunnel processes of older rpc connections tunneling
        to the same remote port. If methods are invoked on the client
        after this has happened they will fail with connection closed errors.

        @param port: The remote forwarding port.
        @param command_name: The name of the remote process, to terminate
                                using pkill.
        @param remote_pid: The PID of the remote background process
                            as a string.

        @return the local port which is used for port forwarding on the ssh
                    client.
        """
        self.disconnect(port)
        local_port = utils.get_unused_port()
        tunnel_proc = self._host.create_ssh_tunnel(port, local_port)
        self._rpc_proxy_map[port] = (command_name, tunnel_proc, remote_pid)
        return local_port


    def _setup_rpc(self, port, command_name, remote_pid=None):
        """Construct a URL for an rpc connection using ssh tunnel.

        @param port: The remote forwarding port.
        @param command_name: The name of the remote process, to terminate
                              using pkill.
        @param remote_pid: The PID of the remote background process
                            as a string.

        @return a url that we can use to initiate the rpc connection.
        """
        return self._RPC_PROXY_URL_FORMAT % self._setup_port(
                port, command_name, remote_pid=remote_pid)


    def tunnel_connect(self, port):
        """Construct a host address using ssh tunnel.

        @param port: The remote forwarding port.

        @return a host address using ssh tunnel.
        """
        return self._RPC_HOST_ADDRESS_FORMAT % self._setup_port(port, None)


    def xmlrpc_connect(self, command, port, command_name=None,
                       ready_test_name=None, timeout_seconds=10,
                       logfile=None, request_timeout_seconds=None,
                       server_desc=None):
        """Connect to an XMLRPC server on the host.

        The `command` argument should be a simple shell command that
        starts an XMLRPC server on the given `port`.  The command
        must not daemonize, and must terminate cleanly on SIGTERM.
        The command is started in the background on the host, and a
        local XMLRPC client for the server is created and returned
        to the caller.

        Note that the process of creating an XMLRPC client makes no
        attempt to connect to the remote server; the caller is
        responsible for determining whether the server is running
        correctly, and is ready to serve requests.

        Optionally, the caller can pass ready_test_name, a string
        containing the name of a method to call on the proxy.  This
        method should take no parameters and return successfully only
        when the server is ready to process client requests.  When
        ready_test_name is set, xmlrpc_connect will block until the
        proxy is ready, and throw a TestError if the server isn't
        ready by timeout_seconds.

        If a server is already running on the remote port, this
        method will kill it and disconnect the tunnel process
        associated with the connection before establishing a new one,
        by consulting the rpc_proxy_map in disconnect.

        @param command Shell command to start the server.
        @param port Port number on which the server is expected to
                    be serving.
        @param command_name String to use as input to `pkill` to
            terminate the XMLRPC server on the host.
        @param ready_test_name String containing the name of a
            method defined on the XMLRPC server.
        @param timeout_seconds Number of seconds to wait
            for the server to become 'ready.'  Will throw a
            TestFail error if server is not ready in time.
        @param logfile Logfile to send output when running
            'command' argument.
        @param request_timeout_seconds Timeout in seconds for an XMLRPC request.
        @param server_desc: Extra text to report in socket.error descriptions.

        """
        # Clean up any existing state.  If the caller is willing
        # to believe their server is down, we ought to clean up
        # any tunnels we might have sitting around.
        self.disconnect(port)
        remote_pid = None
        if command is not None:
            if logfile:
                remote_cmd = '%s > %s 2>&1' % (command, logfile)
            else:
                remote_cmd = command
            remote_pid = self._host.run_background(remote_cmd)
            logging.debug('Started XMLRPC server on host %s, pid = %s',
                          self._host.hostname, remote_pid)

        # Tunnel through SSH to be able to reach that remote port.
        rpc_url = self._setup_rpc(port, command_name, remote_pid=remote_pid)
        if not server_desc:
            server_desc = "<%s '%s:%s'>" % (command_name or 'XMLRPC',
                                            self._host.hostname, port)
        server_desc = '%s (%s)' % (server_desc, rpc_url.replace('http://', ''))
        if request_timeout_seconds is not None:
            proxy = TimeoutXMLRPCServerProxy(
                    rpc_url, timeout=request_timeout_seconds, allow_none=True)
        else:
            proxy = six.moves.xmlrpc_client.ServerProxy(rpc_url, allow_none=True)

        if ready_test_name is not None:
            # retry.retry logs each attempt; calculate delay_sec to
            # keep log spam to a dull roar.
            @retry.retry((socket.error,
                          six.moves.xmlrpc_client.ProtocolError,
                          six.moves.http_client.BadStatusLine),
                         timeout_min=timeout_seconds / 60.0,
                         delay_sec=min(max(timeout_seconds / 20.0, 0.1), 1))
            def ready_test():
                """ Call proxy.ready_test_name(). """
                try:
                    getattr(proxy, ready_test_name)()
                except socket.error as e:
                    e.filename = server_desc
                    raise

            try:
                logging.info('Waiting %d seconds for XMLRPC server '
                             'to start.', timeout_seconds)
                ready_test()
            except Exception as exc:
                log_lines = []
                if logfile:
                    logging.warning('Failed to start XMLRPC server; getting log.')
                    with tempfile.NamedTemporaryFile() as temp:
                        self._host.get_file(logfile, temp.name)
                        with open(temp.name) as f:
                            log_lines = f.read().rstrip().splitlines()
                else:
                    logging.warning('Failed to start XMLRPC server; no log.')

                logging.error(
                        'Failed to start XMLRPC server:  %s.%s: %s.',
                        type(exc).__module__, type(exc).__name__,
                        str(exc).rstrip('.'))

                if isinstance(exc, six.moves.http_client.BadStatusLine):
                    # BadStatusLine: inject the last log line into the message,
                    # using the 'line' and 'args' attributes.
                    if log_lines:
                        if exc.line:
                            exc.line = '%s -- Log tail: %r' % (
                                    exc.line, log_lines[-1])
                        else:
                            exc.line = 'Log tail: %r' % (
                                    log_lines[-1])
                        exc.args = (exc.line,)
                elif isinstance(exc, socket.error):
                    # socket.error: inject the last log line into the message,
                    # using the 'filename' attribute.
                    if log_lines:
                        if exc.filename:
                            exc.filename = '%s -- Log tail: %r' % (
                                    exc.filename, log_lines[-1])
                        else:
                            exc.filename = 'Log tail: %r' % log_lines[-1]
                elif log_lines:
                    # Unusual failure: can't inject the last log line,
                    # so report it via logging.
                    logging.error('Log tail: %r', log_lines[-1])

                if len(log_lines) > 1:
                    # The failure messages include only the last line,
                    # so report the whole thing if it had more lines.
                    logging.error('Full XMLRPC server log:\n%s',
                                  '\n'.join(log_lines))

                self.disconnect(port)
                raise
        logging.info('XMLRPC server started successfully.')
        return proxy

    def disconnect(self, port, pkill=True):
        """Disconnect from an RPC server on the host.

        Terminates the remote RPC server previously started for
        the given `port`.  Also closes the local ssh tunnel created
        for the connection to the host.  This function does not
        directly alter the state of a previously returned RPC
        client object; however disconnection will cause all
        subsequent calls to methods on the object to fail.

        This function does nothing if requested to disconnect a port
        that was not previously connected via _setup_rpc.

        @param port Port number passed to a previous call to `_setup_rpc()`.
        @param pkill: if True, ssh in to the server and pkill the process.
        """
        if port not in self._rpc_proxy_map:
            return
        remote_name, tunnel_proc, remote_pid = self._rpc_proxy_map[port]
        if pkill and remote_name:
            # We use 'pkill' to find our target process rather than
            # a PID, because the host may have rebooted since
            # connecting, and we don't want to kill an innocent
            # process with the same PID.
            #
            # 'pkill' helpfully exits with status 1 if no target
            # process  is found, for which run() will throw an
            # exception.  We don't want that, so we the ignore
            # status.
            self._host.run("pkill -f '%s'" % remote_name, ignore_status=True)
            if remote_pid:
                logging.info('Waiting for RPC server "%s" shutdown (%s)',
                             remote_name, remote_pid)
                start_time = time.time()
                while (time.time() - start_time <
                       self._RPC_SHUTDOWN_TIMEOUT_SECONDS):
                    running_processes = self._host.run(
                            "pgrep -f '%s'" % remote_name,
                            ignore_status=True).stdout.split()
                    if not remote_pid in running_processes:
                        logging.info('Shut down RPC server %s.', remote_pid)
                        break
                    time.sleep(self._RPC_SHUTDOWN_POLLING_PERIOD_SECONDS)
                    self._host.run("pkill -9 -f '%s'" % remote_name,
                                   ignore_status=True)
                else:
                    raise error.TestError('Failed to shutdown RPC server %s' %
                                          remote_name)

        self._host.disconnect_ssh_tunnel(tunnel_proc)
        del self._rpc_proxy_map[port]


    def disconnect_all(self):
        """Disconnect all known RPC proxy ports."""
        for port in list(self._rpc_proxy_map.keys()):
            self.disconnect(port)


class TimeoutXMLRPCServerProxy(six.moves.xmlrpc_client.ServerProxy):
    """XMLRPC ServerProxy supporting timeout."""
    def __init__(self, uri, timeout=20, *args, **kwargs):
        """Initializes a TimeoutXMLRPCServerProxy.

        @param uri: URI to a XMLRPC server.
        @param timeout: Timeout in seconds for a XMLRPC request.
        @param *args: args to xmlrpclib.ServerProxy.
        @param **kwargs: kwargs to xmlrpclib.ServerProxy.

        """
        if timeout:
            kwargs['transport'] = TimeoutXMLRPCTransport(timeout=timeout)
        six.moves.xmlrpc_client.ServerProxy.__init__(self, uri, *args, **kwargs)


class TimeoutXMLRPCTransport(six.moves.xmlrpc_client.Transport):
    """A Transport subclass supporting timeout."""
    def __init__(self, timeout=20, *args, **kwargs):
        """Initializes a TimeoutXMLRPCTransport.

        @param timeout: Timeout in seconds for a HTTP request through this transport layer.
        @param *args: args to xmlrpclib.Transport.
        @param **kwargs: kwargs to xmlrpclib.Transport.

        """
        six.moves.xmlrpc_client.Transport.__init__(self, *args, **kwargs)
        self.timeout = timeout


    def make_connection(self, host):
        """Overwrites make_connection in xmlrpclib.Transport with timeout.

        @param host: Host address to connect.

        @return: A httplib.HTTPConnection connecting to host with timeout.

        """
        conn = six.moves.http_client.HTTPConnection(host, timeout=self.timeout)
        return conn
