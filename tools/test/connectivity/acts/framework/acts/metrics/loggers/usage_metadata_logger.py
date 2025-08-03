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

from functools import wraps

from acts import context
from acts.metrics.core import ProtoMetric
from acts.metrics.core import ProtoMetricPublisher
from acts.metrics.loggers.protos.gen import acts_usage_metadata_pb2


_usage_map = {}


def record_api_usage(f):
    """Decorator method that wraps api functions to log their usage.
    Reads __module__ and __name__ attributes of the function to pass
    as args to log_usage

    Args:
        f: The function to record.

    Returns:
        A wrapper function with the same name as the original
        function that records function calls.
    """
    @wraps(f)
    def wrapper(*args, **kwargs):
        log_usage(f.__module__, f.__name__)
        return f(*args, **kwargs)

    return wrapper


def log_usage(module_name, func_name):
    """Creates a dict key from the params and the current context, and
    uses them to update a counter for that key.
    Key is an instance of UsageMetadataKey, where
    method_name: module_name.func_name
    test_context: current_context.identifier

    Args:
        module_name: function module
        func_name: function name
    """
    current_context = context.get_current_context()

    name = '.'.join([module_name, func_name])

    usage_key = UsageMetadataKey(name, current_context.identifier)

    _usage_map[usage_key] = _usage_map.get(usage_key, 0) + 1


def _reset():
    """Clear the _usage_map.
    """
    _usage_map.clear()


class UsageMetadataPublisher(ProtoMetricPublisher):
    """Publisher with the added ability to convert the _usage_map into a
    ProtoMetric object for publishing.
    """

    def __init__(self):
        """Initializes the publisher, passing RootContext to the parent
        implementation.
        """
        super().__init__(context.RootContext())

    def publish(self):
        """Create a ProtoMetric object and call _publish_single implementation,
         typically to write ProtoMetric to a file.
        """
        metric = self._usage_map_to_proto_metric()
        self._publish_single(metric)

    def _usage_map_to_proto_metric(self):
        """Iterate over _usage_map, creating an ActsMethodUsageMetadata for
        each entry.

        Returns:
            ProtoMetric wrapper object with name='acts_usage_metadata' and
            data=ActsTestRunMetadata()
        """
        data = acts_usage_metadata_pb2.ActsTestRunMetadata()

        for usage in _usage_map:
            method_count = data.usage.add()
            method_count.test_context = usage.test_context
            method_count.method_identifier = usage.method_name
            method_count.count = _usage_map[usage]

        return ProtoMetric(name='acts_usage_metadata', data=data)


class UsageMetadataKey:
    """Dict key for counting method invocations. Used as keys in _usage_map.
    Simple tuple object with hash and eq.

    Attributes:
        test_context: Identifier for calling test
        method_name: Identifier for api method
    """

    def __init__(self, method_name, test_context):
        """Initialize a UsageMetadataKey.

        Args:
            method_name: Fully qualified name of the method to record,
                typically an ACTS api method.
            test_context: Contextual identifier
        """
        self.test_context = test_context
        self.method_name = method_name

    def __hash__(self):
        return hash(self.test_context) ^ hash(self.method_name)

    def __eq__(self, other):
        return (self.test_context == other.test_context and
            self.method_name == other.method_name)

    def __repr__(self):
        return ('UsageMetadataKey{' + self.method_name + ', ' +
               self.test_context + '}')
