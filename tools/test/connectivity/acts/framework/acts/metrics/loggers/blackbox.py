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
from acts.metrics.loggers.protos.gen import acts_blackbox_pb2


class BlackboxMappedMetricLogger(MetricLogger):
    """A MetricLogger for logging and publishing Blackbox metrics from a dict.
    The dict maps the metric name to the metric value.

    The logger will publish an ActsBlackboxMetricResult message, containing
    data intended to be uploaded to Blackbox. The message itself contains only
    minimal information specific to the metric, with the intention being that
    all other metadata is extracted from the test_summary.yaml.

    This logger will extract an attribute from the test class as the metric
    result. The metric key will be either the context's identifier or a custom
    value assigned to this class.

    Attributes:
        metric_key: The metric key to use. If unset, the logger will use the
                    context's identifier.
        _metric_map: the map of metric_name -> metric_value to publish
                to blackbox. If the metric value is set to None, the
                metric will not be reported.
    """

    def __init__(self, metric_key=None, event=None):
        """Initializes a logger for Blackbox metrics.

        Args:
            metric_key: The metric key to use. If unset, the logger will use
                        the context's identifier.
            event: The event triggering the creation of this logger.
        """
        super().__init__(event=event)
        self.metric_key = metric_key
        self._metric_map = {}

    def _get_metric_key(self, metric_name):
        """Gets the metric key to use.

        If the metric_key is explicitly set, returns that value. Otherwise,
        extracts an identifier from the context.

        Args:
            metric_name: The name of the metric to report.
        """
        if self.metric_key:
            key = self.metric_key
        else:
            key = self._get_blackbox_identifier()
        key = '%s.%s' % (key, metric_name)
        return key

    def set_metric_data(self, metric_map):
        """Sets the map of metrics to be uploaded to Blackbox. Note that
        this will overwrite all existing added by this function or add_metric.

        Args:
            metric_map: the map of metric_name -> metric_value to publish
                to blackbox. If the metric value is set to None, the
                metric will not be reported.
        """
        self._metric_map = metric_map

    def add_metric(self, metric_name, metric_value):
        """Adds a metric value to be published later.

        Note that if the metric name has already been added, the metric value
        will be overwritten.

        Args:
            metric_name: the name of the metric.
            metric_value: the value of the metric.
        """
        self._metric_map[metric_name] = metric_value

    def _get_blackbox_identifier(self):
        """Returns the testcase identifier, as expected by Blackbox."""
        # b/119787228: Blackbox requires function names to look like Java
        # functions.
        identifier = self.context.identifier
        parts = identifier.rsplit('.', 1)
        return '#'.join(parts)

    def end(self, _):
        """Creates and publishes a ProtoMetric with blackbox data.

        Builds a list of ActsBlackboxMetricResult messages from the set
        metric data, and sends them to the publisher.
        """
        bundle = acts_blackbox_pb2.ActsBlackboxMetricResultsBundle()
        for metric_name, metric_value in self._metric_map.items():
            if metric_value is None:
                continue
            result = acts_blackbox_pb2.ActsBlackboxMetricResult()
            result.test_identifier = self._get_blackbox_identifier()
            result.metric_key = self._get_metric_key(metric_name)
            result.metric_value = metric_value
            bundle.acts_blackbox_metric_results.append(result)

        # Since there could technically be more than one concurrent logger
        # instance we add a hash for files to not override each other. We use a
        # static hash for repeatability.
        bundle_name = 'blackbox_metrics_bundle.' + md5_proto(bundle)[0:8]
        return self.publisher.publish(
            [ProtoMetric(name=bundle_name, data=bundle)])


class BlackboxMetricLogger(BlackboxMappedMetricLogger):
    """A MetricLogger for logging and publishing individual Blackbox metrics.

    For additional information on reporting to Blackbox, see
    BlackboxMappedMetricLogger.

    Attributes:
        metric_name: The name of the metric, used to determine output filename.
        metric_key: The metric key to use. If unset, the logger will use the
                    context's identifier.
        metric_value: The metric value.
    """

    def __init__(self, metric_name, metric_key=None, event=None):
        """Initializes a logger for Blackbox metrics.

        Args:
            metric_name: The name of the metric.
            metric_key: The metric key to use. If unset, the logger will use
                        the context's identifier.
            event: The event triggering the creation of this logger.
        """
        super().__init__(metric_key=metric_key, event=event)
        if not metric_name:
            raise ValueError("metric_name must be supplied.")
        self.metric_name = metric_name
        self.metric_value = None

    @property
    def metric_value(self):
        return self._metric_map[self.metric_name]

    @metric_value.setter
    def metric_value(self, value):
        self.add_metric(self.metric_name, value)
