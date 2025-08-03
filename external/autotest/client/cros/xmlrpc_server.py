# Lint as: python2, python3
# Copyright (c) 2013 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# contextlib.nested is deprecated and removed in python3
# Since the apis are quite different, keep track of whether to use nested or not
# based on the availability of contextlib.nested and take different code paths.
try:
    from contextlib import nested
    use_nested = True
except ImportError:
    import contextlib
    use_nested = False

import dbus
import errno
import functools
import logging
import os
import select
import signal
import six.moves.xmlrpc_server
import threading


def terminate_old(script_name, sigterm_timeout=5, sigkill_timeout=3):
    """
    Avoid "address already in use" errors by killing any leftover RPC server
    processes, possibly from previous runs.

    A process is a match if it's Python and has the given script in the command
    line.  This should avoid including processes such as editors and 'tail' of
    logs, which might match a simple pkill.

    exe=/usr/local/bin/python3
    cmdline=['/usr/bin/python3', '-u', '/usr/local/autotest/.../rpc_server.py']

    @param script_name: The filename of the main script, used to match processes
    @param sigterm_timeout: Wait N seconds after SIGTERM before trying SIGKILL.
    @param sigkill_timeout: Wait N seconds after SIGKILL before complaining.
    """
    # import late, to avoid affecting servers that don't call the method
    import psutil

    script_name_abs = os.path.abspath(script_name)
    script_name_base = os.path.basename(script_name)
    me = psutil.Process()

    logging.debug('This process:  %s: %s, %s', me, me.exe(), me.cmdline())
    logging.debug('Checking for leftover processes...')

    running = []
    for proc in psutil.process_iter(attrs=['name', 'exe', 'cmdline']):
        if proc == me:
            continue
        try:
            name = proc.name()
            if not name or 'py' not in name:
                continue
            exe = proc.exe()
            args = proc.cmdline()
            # Note: If we ever need multiple instances on different ports,
            # add a check for listener ports, likely via proc.connections()
            if '/python' in exe and (script_name in args
                                     or script_name_abs in args
                                     or script_name_base in args):
                logging.debug('Found process: %s: %s', proc, args)
                running.append(proc)
        except psutil.Error as e:
            logging.debug('%s: %s', e, proc)
            continue

    if not running:
        return

    logging.info('Trying SIGTERM: pids=%s', [p.pid for p in running])
    for proc in running:
        try:
            proc.send_signal(0)
            proc.terminate()
        except psutil.NoSuchProcess as e:
            logging.debug('%s: %s', e, proc)
        except psutil.Error as e:
            logging.warning('%s: %s', e, proc)

    (terminated, running) = psutil.wait_procs(running, sigterm_timeout)
    if not running:
        return

    running.sort(key=lambda p: p.pid)
    logging.info('Trying SIGKILL: pids=%s', [p.pid for p in running])
    for proc in running:
        try:
            proc.kill()
        except psutil.NoSuchProcess as e:
            logging.debug('%s: %s', e, proc)
        except psutil.Error as e:
            logging.warning('%s: %s', e, proc)

    (sigkilled, running) = psutil.wait_procs(running, sigkill_timeout)
    if running:
        running.sort(key=lambda p: p.pid)
        logging.warning('Found leftover processes %s; address may be in use!',
                     [p.pid for p in running])
    else:
        logging.debug('Leftover processes have exited.')


class XmlRpcServer(threading.Thread):
    """Simple XMLRPC server implementation.

    In theory, Python should provide a valid XMLRPC server implementation as
    part of its standard library.  In practice the provided implementation
    doesn't handle signals, not even EINTR.  As a result, we have this class.

    Usage:

    server = XmlRpcServer(('localhost', 43212))
    server.register_delegate(my_delegate_instance)
    server.run()

    """

    def __init__(self, host, port):
        """Construct an XmlRpcServer.

        @param host string hostname to bind to.
        @param port int port number to bind to.

        """
        super(XmlRpcServer, self).__init__()
        logging.info('Binding server to %s:%d', host, port)
        self._server = six.moves.xmlrpc_server.SimpleXMLRPCServer(
                (host, port), allow_none=True)
        self._server.register_introspection_functions()
        # After python 2.7.10, BaseServer.handle_request automatically retries
        # on EINTR, so handle_request will be blocked at select.select forever
        # if timeout is None. Set a timeout so server can be shut down
        # gracefully. Check issue crbug.com/571737 and
        # https://bugs.python.org/issue7978 for the explanation.
        self._server.timeout = 0.5
        self._keep_running = True
        self._delegates = []
        # Gracefully shut down on signals.  This is how we expect to be shut
        # down by autotest.
        signal.signal(signal.SIGTERM, self._handle_signal)
        signal.signal(signal.SIGINT, self._handle_signal)


    def register_delegate(self, delegate):
        """Register delegate objects with the server.

        The server will automagically look up all methods not prefixed with an
        underscore and treat them as potential RPC calls.  These methods may
        only take basic Python objects as parameters, as noted by the
        SimpleXMLRPCServer documentation.  The state of the delegate is
        persisted across calls.

        @param delegate object Python object to be exposed via RPC.

        """
        self._server.register_instance(delegate)
        self._delegates.append(delegate)

    def run(self):
        """Block and handle many XmlRpc requests."""
        logging.info('XmlRpcServer starting...')

        def stack_inner():
            """Handle requests to server until asked to stop running."""
            while self._keep_running:
                try:
                    self._server.handle_request()
                except select.error as v:
                    # In a cruel twist of fate, the python library doesn't
                    # handle this kind of error.
                    if v[0] != errno.EINTR:
                        raise

        if use_nested:
            with nested(*self._delegates):
                stack_inner()
        else:
            with contextlib.ExitStack() as stack:
                delegates = [stack.enter_context(d) for d in self._delegates]
                stack_inner()

        for delegate in self._delegates:
            if hasattr(delegate, 'cleanup'):
                delegate.cleanup()

        logging.info('XmlRpcServer exited.')


    def _handle_signal(self, _signum, _frame):
        """Handle a process signal by gracefully quitting.

        SimpleXMLRPCServer helpfully exposes a method called shutdown() which
        clears a flag similar to _keep_running, and then blocks until it sees
        the server shut down.  Unfortunately, if you call that function from
        a signal handler, the server will just hang, since the process is
        paused for the signal, causing a deadlock.  Thus we are reinventing the
        wheel with our own event loop.

        """
        self._keep_running = False


def dbus_safe(default_return_value):
    """Catch all DBus exceptions and return a default value instead.

    Wrap a function with a try block that catches DBus exceptions and
    returns default values instead.  This is convenient for simple error
    handling since XMLRPC doesn't understand DBus exceptions.

    @param wrapped_function function to wrap.
    @param default_return_value value to return on exception (usually False).

    """
    def decorator(wrapped_function):
        """Call a function and catch DBus errors.

        @param wrapped_function function to call in dbus safe context.
        @return function return value or default_return_value on failure.

        """
        @functools.wraps(wrapped_function)
        def wrapper(*args, **kwargs):
            """Pass args and kwargs to a dbus safe function.

            @param args formal python arguments.
            @param kwargs keyword python arguments.
            @return function return value or default_return_value on failure.

            """
            logging.debug('%s()', wrapped_function.__name__)
            try:
                return wrapped_function(*args, **kwargs)

            except dbus.exceptions.DBusException as e:
                logging.error('Exception while performing operation %s: %s: %s',
                              wrapped_function.__name__,
                              e.get_dbus_name(),
                              e.get_dbus_message())
                return default_return_value

        return wrapper

    return decorator


class XmlRpcDelegate(object):
    """A super class for XmlRPC delegates used with XmlRpcServer.

    This doesn't add much helpful functionality except to implement the trivial
    status check method expected by autotest's host.xmlrpc_connect() method.
    Subclass this class to add more functionality.

    """


    def __enter__(self):
        logging.debug('Bringing up XmlRpcDelegate: %r.', self)
        pass


    def __exit__(self, exception, value, traceback):
        logging.debug('Tearing down XmlRpcDelegate: %r.', self)
        pass


    def ready(self):
        """Confirm that the XMLRPC server is up and ready to serve.

        @return True (always).

        """
        logging.debug('ready()')
        return True
