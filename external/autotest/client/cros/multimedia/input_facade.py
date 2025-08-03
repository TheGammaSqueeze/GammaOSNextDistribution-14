# Copyright 2017 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""An interface to access the local input facade."""


import json
import logging
import threading

from autotest_lib.client.bin.input import input_event_recorder
from autotest_lib.client.cros.input_playback import input_playback
from autotest_lib.client.common_lib import error
from autotest_lib.client.cros.graphics import graphics_utils


class InputFacadeLocalError(Exception):
    """Error in InputFacadeLocal."""
    pass


class InputFacadeLocal(object):
    """Facade to access the record input events."""

    def __init__(self):
        """Initializes the input facade."""
        self.recorders_lock = threading.Lock()
        self.recorders = dict()

    def initialize_input_playback(self, input_type='keyboard', property_file=None):
        """Initialize for input events simulation.

        @param input_type: the name of the input device.
        @param property_file: Property file of device to be emulated.
        """
        self._player = input_playback.InputPlayback()
        self._player.emulate(input_type=input_type, property_file=property_file)
        self._player.find_connected_inputs()

    def initialize_input_recorder(self, device_name, uniq):
        """Initialize an input event recorder object.

        @param device_name: the name of the input device to record.
        @param uniq: Unique address of input device (None if not used)

        """
        with self.recorders_lock:
            self.recorders[device_name] = \
                input_event_recorder.InputEventRecorder(device_name, uniq)
            logging.info('input event device: %s [uniq=%s] (%s)',
                         self.recorders[device_name].device_name,
                         self.recorders[device_name].uniq,
                         self.recorders[device_name].device_node)


    def clear_input_events(self, device_name):
        """Clear the event list.

        @param device_name: the name of the input device to record.

        """
        with self.recorders_lock:
            if self.recorders[device_name] is None:
                raise error.TestError(
                    'input facade: input device name not given')
            self.recorders[device_name].clear_events()


    def start_input_recorder(self, device_name):
        """Start the recording thread.

        @param device_name: the name of the input device to record.

        """
        with self.recorders_lock:
            if self.recorders[device_name] is None:
                raise error.TestError(
                    'input facade: input device name not given')
            self.recorders[device_name].start()


    def stop_input_recorder(self, device_name):
        """Stop the recording thread.

        @param device_name: the name of the input device to record.

        """
        with self.recorders_lock:
            if self.recorders[device_name] is None:
                raise error.TestError(
                    'input facade: input device name not given')
            self.recorders[device_name].stop()


    def get_input_events(self, device_name):
        """Get the bluetooth device input events.

        @param device_name: the name of the input device to record.

        @returns: the recorded input events.

        """
        with self.recorders_lock:
            if self.recorders[device_name] is None:
                raise error.TestError(
                    'input facade: input device name not given')
            events = self.recorders[device_name].get_events()
        return json.dumps(events)


    def press_keys(self, key_list):
        """ Simulating key press

        @param key_list: A list of key strings, e.g. ['LEFTCTRL', 'F4']
        """
        graphics_utils.press_keys(key_list)


    def blocking_playback_of_default_file(self, input_type, filename):
        """Simulate events

        @param input_type: input device name
        @param filename: input events
        """
        self._player.blocking_playback_of_default_file(input_type=input_type,
                                                       filename=filename)
