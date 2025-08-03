#!/usr/bin/env python3
#
#   Copyright 2020 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

from datetime import datetime


class PerformanceTestLogger(object):
    """
    A helper class to log time points and intervals
    """

    def __init__(self):
        self.base_timepoint = datetime.now()
        # We use a dictionary of a list of timepoints
        self.start_interval_points = {}
        self.end_interval_points = {}
        self.single_points = {}

    def log_single_point(self, label=""):
        if label not in self.single_points:
            self.single_points[label] = []
        self.single_points[label].append(datetime.now())

    def start_interval(self, label=""):
        if label not in self.start_interval_points:
            self.start_interval_points[label] = []
        self.start_interval_points[label].append(datetime.now())

    def end_interval(self, label=""):
        if label not in self.end_interval_points:
            self.end_interval_points[label] = []
        self.end_interval_points[label].append(datetime.now())

    def _check_interval_label(self, label):
        if label not in self.start_interval_points or label not in self.end_interval_points:
            raise KeyError("label %s doesn't exist" % label)
        if len(self.start_interval_points[label]) != len(self.end_interval_points[label]):
            raise KeyError("label %s doesn't have correct start and end log" % label)

    def get_duration_of_intervals(self, label):
        """
        Return the list of duration of the intervals with specified label.
        """
        self._check_interval_label(label)
        intervals = []
        for i in range(len(self.start_interval_points[label])):
            interval = self.end_interval_points[label][i] - self.start_interval_points[label][i]
            intervals.append(interval)
        return intervals

    def dump_intervals(self):
        """
        Gives an iterator of (iterator of label, start, end) over all labels
        """
        for label in self.start_interval_points:
            self._check_interval_label(label)
            yield ((label, self.start_interval_points[label][i], self.end_interval_points[label][i])
                   for i in range(len(self.start_interval_points[label])))
