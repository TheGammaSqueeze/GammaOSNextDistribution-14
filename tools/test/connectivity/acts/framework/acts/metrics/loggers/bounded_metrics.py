#!/usr/bin/env python3
#
#   Copyright 2016 - The Android Open Source Project
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
from acts.libs.proto.proto_utils import md5_proto
from acts.metrics.core import ProtoMetric
from acts.metrics.logger import MetricLogger

from acts.metrics.loggers.protos.gen import metrics_pb2


class BoundedMetricsLogger(MetricLogger):
    """A MetricLogger for logging and publishing bounded metrics (metrics that
    are expected to fall within a given range).

    Attributes:
        _metric_map: the map of metric_name -> bounded_pb2.Metric to publish.
    """

    def __init__(self, event=None):
        """Initializes a logger for Blackbox metrics.

        Args:
            event: The event triggering the creation of this logger.
        """
        super().__init__(event=event)
        self._metric_map = {}

    def add(self, metric, value, lower_limit=None, upper_limit=None, unit=None):
        """Adds a metric value to be published later.

        Note that if the metric name has already been added, the metric value
        will be overwritten.

        Args:
            metric: the name of the metric.
            value: the value of the metric.
            lower_limit: lower bound for the metric.
            upper_limit: upper bound for the metric.
            unit: optional unit to identify this metric with.
        """
        bounded_metric = metrics_pb2.BoundedMetric()
        if self.get_test():
            bounded_metric.test_method = self.get_test()
        bounded_metric.test_class = self.get_class()
        bounded_metric.metric = metric
        bounded_metric.value = value
        if lower_limit is not None:
            bounded_metric.lower_limit.value = lower_limit
        if upper_limit is not None:
            bounded_metric.upper_limit.value = upper_limit
        if unit:
            bounded_metric.unit = unit
        self._metric_map[metric] = bounded_metric

    def get_test(self):
        identifier = self.context.identifier
        parts = identifier.rsplit('.', 1)
        return None if len(parts) < 2 else parts[1]

    def get_class(self):
        identifier = self.context.identifier
        parts = identifier.rsplit('.', 1)
        return parts[0]

    def end(self, _):
        """Creates and publishes a ProtoMetric with bounded_pb2.Metric data.

        Builds a list of bounded_pb2.Metric messages from the set
        metric data, and sends them to the publisher.
        """
        bundle = metrics_pb2.BoundedMetricsBundle()
        for metric_name, bounded_metric in self._metric_map.items():
            if bounded_metric is None:
                continue
            bundle.bounded_metrics.append(bounded_metric)

        # Since there could technically be more than one concurrent logger
        # instance we add a hash for files to not override each other. We use a
        # static hash for repeatability.
        bundle_name = 'bounded_metrics_bundle.' + md5_proto(bundle)[0:8]
        return self.publisher.publish(
            [ProtoMetric(name=bundle_name, data=bundle)])
