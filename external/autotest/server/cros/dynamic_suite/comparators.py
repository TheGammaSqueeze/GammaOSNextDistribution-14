# Copyright (c) 2012 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Comparators for use in dynamic_suite module unit tests."""

from unittest.mock import ANY


class StatusContains(object):
    @staticmethod
    def CreateFromStrings(status=None, test_name=None, reason=None):
        status_comp = AnyStringWith(status) if status else ANY
        name_comp = AnyStringWith(test_name) if test_name else ANY
        reason_comp = AnyStringWith(reason) if reason else ANY
        return StatusContains(status_comp, name_comp, reason_comp)


    def __init__(self, status=ANY, test_name=ANY, reason=ANY):
        """Initialize.

        Takes mox.Comparator objects to apply to job_status.Status
        member variables.

        @param status: status code, e.g. 'INFO', 'START', etc.
        @param test_name: expected test name.
        @param reason: expected reason
        """
        self._status = status
        self._test_name = test_name
        self._reason = reason


    def equals(self, rhs):
        """Check to see if fields match base_job.status_log_entry obj in rhs.

        @param rhs: base_job.status_log_entry object to match.
        @return boolean
        """
        return (self._status.equals(rhs.status_code) and
                self._test_name.equals(rhs.operation) and
                self._reason.equals(rhs.message))


    def __repr__(self):
        return '<Status containing \'%s\t%s\t%s\'>' % (self._status,
                                                       self._test_name,
                                                       self._reason)


class AnyStringWith(str):
    def __eq__(self, other):
        return self in str(other)
