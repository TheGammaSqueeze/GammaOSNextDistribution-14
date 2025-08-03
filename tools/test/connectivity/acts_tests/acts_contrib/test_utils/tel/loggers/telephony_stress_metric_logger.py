# /usr/bin/env python3
#
# Copyright (C) 2021 The Android Open Source Project
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

from acts.metrics.core import ProtoMetric
from acts.metrics.logger import MetricLogger
from acts_contrib.test_utils.tel.loggers.protos.telephony_stress_metric_pb2 import TelephonyStressTestResult

# Initializes the path to the protobuf
PROTO_PATH = os.path.join(os.path.dirname(__file__), 'protos',
                          'telephony_stress_metric.proto')


class TelephonyStressMetricLogger(MetricLogger):
    """A logger for gathering Telephony Stress test metrics

    Attributes:
        proto: Module used to store Telephony metrics in a proto
    """

    def __init__(self, event):
        super().__init__(event=event)
        self.proto = TelephonyStressTestResult()

    def set_result(self, result_dict):
        self.proto.results_dict = result_dict

    def end(self, event):
        metric = ProtoMetric(name='telephony_stress_test_result',
                             data=self.proto)
        return self.publisher.publish(metric)
