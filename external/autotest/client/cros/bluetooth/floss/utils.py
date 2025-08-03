# Lint as: python2, python3
# Copyright 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""Simple observer base class."""

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import functools
from gi.repository import GLib
import logging
import threading

# All GLIB method calls should wait this many seconds by default
GLIB_METHOD_CALL_TIMEOUT = 2

# GLib thread name that will run the mainloop.
GLIB_THREAD_NAME = 'glib'


class GlibDeadlockException(Exception):
    """Detected a situation that will cause a deadlock in GLib.

    This exception should be emitted when we detect that a deadlock is likely to
    occur. For example, a method call running in the mainloop context is making
    a function call that is wrapped with @glib_call.
    """
    pass


def glib_call(default_result=None,
              timeout=GLIB_METHOD_CALL_TIMEOUT,
              thread_name=GLIB_THREAD_NAME):
    """Threads method call to glib thread and waits for result.

    The dbus-python package does not support multi-threaded access. As a result,
    we pipe all dbus function to the mainloop using GLib.idle_add which runs the
    method as part of the mainloop.

    @param default_result: The default return value from the function call if it
                           fails or times out.
    @param timeout: How long to wait for the method call to complete.
    @param thread_name: Name of the thread that should be running GLib.Mainloop.
    """

    def decorator(method):
        """Internal wrapper."""

        def call_and_signal(data):
            """Calls a function and signals completion.

            This method is called by GLib and added via GLib.idle_add. It will
            be run in the same thread as the GLib mainloop.

            @param data: Dict containing data to be passed. Must have keys:
                         event, method, args, kwargs and result. The value for
                         result should be the default value and will be set
                         before return.

            @return False so that glib doesn't reschedule this to run again.
            """
            (event, method, args, kwargs) = (data['event'], data['method'],
                                             data['args'], data['kwargs'])
            logging.info('%s: Running %s',
                         threading.current_thread().name, str(method))
            err = None
            try:
                data['result'] = method(*args, **kwargs)
            except Exception as e:
                logging.error('Exception during %s: %s', str(method), str(e))
                err = e

            event.set()

            # If method callback is set, this will call that method with results
            # of this method call and any error that may have resulted.
            if 'method_callback' in data:
                data['method_callback'](err, data['result'])

            return False

        @functools.wraps(method)
        def wrapper(*args, **kwargs):
            """Sends method call to GLib and waits for its completion.

            @param args: Positional arguments to method.
            @param kwargs: Keyword arguments to method. Some special keywords:
                |method_callback|: Returns result via callback without blocking.
            """
            method_callback = None
            # If a method callback is given, we will not block on the completion
            # of the call but expect the response in the callback instead. The
            # callback has the signature: def callback(err, result)
            if 'method_callback' in kwargs:
                method_callback = kwargs['method_callback']
                del kwargs['method_callback']

            # Make sure we're not scheduling in the GLib thread since that'll
            # cause a deadlock. An exception is if we have a method callback
            # which is async.
            current_thread_name = threading.current_thread().name
            if current_thread_name is thread_name and not method_callback:
                raise GlibDeadlockException(
                        '{} called in GLib thread'.format(method))

            done_event = threading.Event()
            data = {
                    'event': done_event,
                    'method': method,
                    'args': args,
                    'kwargs': kwargs,
                    'result': default_result,
            }
            if method_callback:
                data['method_callback'] = method_callback

            logging.info('%s: Adding %s to GLib.idle_add',
                         threading.current_thread().name, str(method))
            GLib.idle_add(call_and_signal, data)

            if not method_callback:
                # Wait for the result from the GLib call
                if not done_event.wait(timeout=timeout):
                    logging.warn('%s timed out after %d s', str(method),
                                 timeout)

            return data['result']

        return wrapper

    return decorator


def glib_callback(thread_name=GLIB_THREAD_NAME):
    """Marks callbacks that are called by GLib and checks for errors.
    """

    def _decorator(method):
        @functools.wraps(method)
        def _wrapper(*args, **kwargs):
            current_thread_name = threading.current_thread().name
            if current_thread_name is not thread_name:
                raise GlibDeadlockException(
                        '{} should be called by GLib'.format(method))

            return method(*args, **kwargs)

        return _wrapper

    return _decorator


class PropertySet:
    """Helper class with getters and setters for properties. """

    class MissingProperty(Exception):
        """Raised when property is missing in PropertySet."""
        pass

    class PropertyGetterMissing(Exception):
        """Raised when get is called on a property that doesn't support it."""
        pass

    class PropertySetterMissing(Exception):
        """Raised when set is called on a property that doesn't support it."""
        pass

    def __init__(self, property_set):
        """Constructor.

        @param property_set: Dictionary with proxy methods for get/set of named
                             properties. These are NOT normal DBus properties
                             that are implemented via
                             org.freedesktop.DBus.Properties.
        """
        self.pset = property_set

    def get(self, prop_name, *args):
        """Calls the getter function for a property if it exists.

        @param prop_name: The property name to call the getter function on.
        @param args: Any positional arguments to pass to getter function.

        @return Result from calling the getter function with given args.
        """
        if prop_name not in self.pset:
            raise self.MissingProperty('{} is unknown.'.format(prop_name))

        (getter, _) = self.pset[prop_name]

        if not getter:
            raise self.PropertyGetterMissing(
                    '{} has no getter.'.format(prop_name))

        return getter(*args)

    def set(self, prop_name, *args):
        """Calls the setter function for a property if it exists.

        @param prop_name: The property name to call the setter function on.
        @param args: Any positional arguments to pass to the setter function.

        @return Result from calling the setter function with given args.
        """
        if prop_name not in self.pset:
            raise self.MissingProperty('{} is unknown.'.format(prop_name))

        (_, setter) = self.pset[prop_name]

        if not setter:
            raise self.PropertySetterMissing(
                    '{} has no getter.'.format(prop_name))

        return setter(*args)
