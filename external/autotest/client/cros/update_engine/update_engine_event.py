# Lint as: python2, python3
# Copyright 2017 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# Update event types.
from __future__ import absolute_import
from __future__ import division
from __future__ import print_function
import six

EVENT_TYPE_DOWNLOAD_COMPLETE = 1
EVENT_TYPE_INSTALL_COMPLETE = 2
EVENT_TYPE_UPDATE_COMPLETE = 3
EVENT_TYPE_DOWNLOAD_STARTED = 13
EVENT_TYPE_DOWNLOAD_FINISHED = 14
EVENT_TYPE_REBOOTED_AFTER_UPDATE = 54

# Update event results.
EVENT_RESULT_ERROR = 0
EVENT_RESULT_SUCCESS = 1
EVENT_RESULT_UPDATE_DEFERRED = 9

EVENT_TYPE_DICT = {
    EVENT_TYPE_DOWNLOAD_COMPLETE: 'download_complete',
    EVENT_TYPE_INSTALL_COMPLETE: 'install_complete',
    EVENT_TYPE_UPDATE_COMPLETE: 'update_complete',
    EVENT_TYPE_DOWNLOAD_STARTED: 'download_started',
    EVENT_TYPE_DOWNLOAD_FINISHED: 'download_finished',
    EVENT_TYPE_REBOOTED_AFTER_UPDATE: 'rebooted_after_update',
    None: 'initial_check'
}


def get_event_type(event_type_code):
    """
    Utility to look up the different event types by ID.

    @param event_type_code: An integer event type code.
    @returns: a string representation of the event type.

    """
    return EVENT_TYPE_DICT[event_type_code]


class UpdateEngineEvent(object):
    """This class represents a single EXPECTED update engine event.

    This class's data will be compared against an ACTUAL event from a hostlog.
    """

    def __init__(self, event_type=None, event_result=None, version=None,
                 previous_version=None, timeout=None):
        """Initializes an event.

        @param event_type: Expected event type.
        @param event_result: Expected event result code.
        @param version: Expected reported image version.
        @param previous_version: Expected reported previous image version.
        @param timeout: How many seconds max should it take to reach this event
                        from the previous one.
        """
        self._expected_attrs = {
            'event_type': event_type,
            'event_result': event_result,
            'version': version,
            'previous_version': previous_version,
        }
        self._timeout = timeout


    def __str__(self):
        """Returns a comma separated list of the event data."""
        return '{%s}' % ', '.join([
                '%s:%s' % (k, v)
                for k, v in six.iteritems(self._expected_attrs)
        ])

    def equals(self, actual_event):
        """
        Compares this expected event with an actual event from the hostlog.

        We only compare values from the expected event that are not empty.
        None values in the actual event are assumed to be missing and
        non-matching.

        @param actual_event: a hostlog event.
        @return A list of mismatched attributes or None if events match.

        """
        mismatched_attrs = []
        for expected_name, expected_val in six.iteritems(self._expected_attrs):
            actual_val = actual_event.get(expected_name)
            if (expected_val and (actual_val is None or
                                  expected_val != actual_val)):
                mismatched_attrs.append(expected_name)

        return mismatched_attrs if mismatched_attrs else None
