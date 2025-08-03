# /usr/bin/env python3
#
# Copyright (C) 2019 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License. You may obtain a copy of
# the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations under
# the License.

import os
import acts_contrib.test_utils.power.loggers.protos.power_metric_pb2 as power_protos

from acts.metrics.core import ProtoMetric
from acts.metrics.logger import MetricLogger

# Initializes the path to the protobuf
PROTO_PATH = os.path.join(os.path.dirname(__file__), 'protos',
                          'power_metric.proto')


class PowerMetricLogger(MetricLogger):
    """A logger for gathering Power test metrics

    Attributes:
        proto: Module used to store Power metrics in a proto
    """
    def __init__(self, event):
        super().__init__(event=event)
        self.proto = power_protos.PowerMetric()

    def set_dl_tput(self, avg_dl_tput):
        self.proto.cellular_metric.avg_dl_tput = avg_dl_tput

    def set_ul_tput(self, avg_ul_tput):
        self.proto.cellular_metric.avg_ul_tput = avg_ul_tput

    def set_dl_tput_threshold(self, avg_dl_tput_threshold):
        self.proto.cellular_metric.avg_dl_tput_threshold = avg_dl_tput_threshold

    def set_ul_tput_threshold(self, avg_ul_tput_threshold):
        self.proto.cellular_metric.avg_ul_tput_threshold = avg_ul_tput_threshold

    def set_avg_power(self, avg_power):
        self.proto.avg_power = avg_power

    def set_avg_current(self, avg_current):
        self.proto.avg_current = avg_current

    def set_voltage(self, voltage):
        self.proto.voltage = voltage

    def set_testbed(self, testbed):
        self.proto.testbed = testbed

    def set_branch(self, branch):
        self.proto.branch = branch

    def set_build_id(self, build_id):
        self.proto.build_id = build_id

    def set_incremental_build_id(self, incremental_build_id):
        self.proto.incremental_build_id = incremental_build_id

    def set_target(self, target):
        self.proto.target = target

    def set_test_suite_display_name(self, test_suite_display_name):
        self.proto.test_suite_display_name = test_suite_display_name

    def set_test_case_display_name(self, test_case_display_name):
        self.proto.test_case_display_name = test_case_display_name

    def set_avg_current_threshold(self, avg_current_threshold):
        self.proto.avg_current_threshold = avg_current_threshold

    def set_pass_fail_status(self, status):
        self.proto.pass_fail_status = status

    def end(self, event):
        metric = ProtoMetric(name='spanner_power_metric', data=self.proto)
        return self.publisher.publish(metric)
