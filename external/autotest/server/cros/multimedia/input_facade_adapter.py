# Lint as: python2, python3
# Copyright 2017 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""An adapter to remotely access the input facade on DUT."""

import json


class InputFacadeRemoteAdapter(object):
    """This is an adapter to remotely capture the DUT's input events.

    The Autotest host object representing the remote DUT, passed to this
    class on initialization, can be accessed from its _client property.

    """

    def __init__(self, remote_facade_proxy):
        """Constructs an InputFacadeRemoteAdapter.

        @param remote_facade_proxy: RemoteFacadeProxy object.

        """
        self._proxy = remote_facade_proxy

    @property
    def _input_proxy(self):
        """Gets the proxy to DUT input facade.

        @return XML RPC proxy to DUT input facade.

        """
        return self._proxy.input

    def initialize_input_playback(self, input_type, property_file=None):
        """Initialize for input events simulation.

        @param input_type: input device either 'keyboard' or 'touchpad' etc.
        @param property_file: Property file of device to be emulated.
        """
        self._input_proxy.initialize_input_playback(input_type, property_file)

    def blocking_playback_of_default_file(self, input_type, filename):
        """Simulate event

        @param input_type: input device either 'keyboard' or 'touchpad' etc.
        @param filename: input events.
        """
        self._input_proxy.blocking_playback_of_default_file(input_type,
                                                            filename)

    def initialize_input_recorder(self, device_name, uniq=None):
        """Initialize an input event recorder object.

        @param device_name: the name of the input device to record.

        """
        self._input_proxy.initialize_input_recorder(device_name, uniq)

    def clear_input_events(self, device_name):
        """Clear the event list.

        @param device_name: the name of the input device to record.

        """
        self._input_proxy.clear_input_events(device_name)


    def start_input_recorder(self, device_name):
        """Start the recording thread.

        @param device_name: the name of the input device to record.

        """
        self._input_proxy.start_input_recorder(device_name)


    def stop_input_recorder(self, device_name):
        """Stop the recording thread.

        @param device_name: the name of the input device to record.

        """
        self._input_proxy.stop_input_recorder(device_name)


    def get_input_events(self, device_name):
        """Get the bluetooth device events.

        @param device_name: the name of the input device to record.

        @returns: the recorded input events.

        """
        return json.loads(self._input_proxy.get_input_events(device_name))


    def press_keys(self, key_list):
        """ Simulating key press

        @param key_list: A list of key strings, e.g. ['LEFTCTRL', 'F4']
        """
        self._input_proxy.press_keys(key_list)
