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
import unittest
from unittest import TestCase

from acts.context import TestCaseContext, TestClassContext, RootContext
from acts.libs.proto.proto_utils import parse_proto_to_ascii
from acts.metrics.loggers import usage_metadata_logger
from acts.metrics.loggers.protos.gen import acts_usage_metadata_pb2
from acts.metrics.loggers.usage_metadata_logger import UsageMetadataKey
from acts.metrics.loggers.usage_metadata_logger import UsageMetadataPublisher
from acts.metrics.loggers.usage_metadata_logger import _usage_map
from acts.metrics.loggers.usage_metadata_logger import log_usage
from acts.metrics.core import ProtoMetric
from mock import Mock
from mock import patch

CURRENT_CONTEXT = 'acts.context.get_current_context'

class UsageMetadataLoggerTest(TestCase):
    def setUp(self):
        usage_metadata_logger._reset()

    @patch(CURRENT_CONTEXT)
    def test_log_usage_context_change(self, current_context):

        class HurloWorld:
            """Just for testing"""
        class YerAHarryWizard:
            """Just for testing"""

        test_case = 'make toast'

        current_context.side_effect = [
            TestCaseContext(HurloWorld(), test_case),
            TestClassContext(YerAHarryWizard()),
            TestClassContext(YerAHarryWizard())
        ]

        module_name = 'com.android.google'
        func_name = 'test_func'
        joined_name = '.'.join([module_name, func_name])

        log_usage(module_name, func_name) # TestCaseContext
        log_usage(module_name, func_name) # TestClassContext
        log_usage(module_name, func_name) # TestClassContext

        case_key = UsageMetadataKey(joined_name,
                                    '%s.%s' % (HurloWorld.__name__, test_case))
        class_key = UsageMetadataKey(joined_name, YerAHarryWizard.__name__)

        self.assertIn(case_key, _usage_map)
        self.assertIn(class_key, _usage_map)

        case_value = _usage_map[case_key]
        class_value = _usage_map[class_key]

        self.assertEqual(case_value, 1)
        self.assertEqual(class_value, 2)

    @patch(CURRENT_CONTEXT)
    def test_log_usage_test_case_context(self, current_context):
        class HurlowWorld:
            """Just for testing"""

        test_case = 'made toast'

        current_context.return_value = TestCaseContext(HurlowWorld(),
                                                       test_case)

        module_name = 'com.android.google'
        func_name = 'test_func'
        joined_name = '.'.join([module_name, func_name])

        log_usage(module_name, func_name)

        key = UsageMetadataKey(joined_name,
                               '%s.%s' %(HurlowWorld.__name__, test_case))

        self.assertIn(key, _usage_map)

        value = _usage_map[key]

        self.assertEqual(value, 1)

    @patch(CURRENT_CONTEXT)
    def test_log_usage_test_class_context(self, current_context):
        class YerAHarryWizard:
            """Just for testing"""
        current_context.return_value = TestClassContext(YerAHarryWizard())

        module_name = 'com.android.google'
        func_name = 'test_func'
        joined_name = '.'.join([module_name, func_name])

        log_usage(module_name, func_name)

        key = UsageMetadataKey(joined_name, YerAHarryWizard.__name__)

        self.assertIn(key, _usage_map)

        value = usage_metadata_logger._usage_map[key]

        self.assertEqual(value, 1)

class UsageMetadataPublisherTest(TestCase):
    def setUp(self):
        usage_metadata_logger._reset()

    @patch(CURRENT_CONTEXT)
    def test_init(self, current_context):
        publisher = UsageMetadataPublisher()

        self.assertEqual(publisher.context.__class__, RootContext)

    @patch(CURRENT_CONTEXT)
    def test_usage_map_to_proto_metric(self, current_context):
        context = TestCaseContext('a', 'b')
        current_context.return_value = context
        publisher = UsageMetadataPublisher()

        umk1 = UsageMetadataKey('a.b', 'c.d')
        umk2 = UsageMetadataKey('e.f', 'h.i')
        _usage_map[umk1] = 51
        _usage_map[umk2] = 5

        usage_metadata_proto = acts_usage_metadata_pb2.ActsTestRunMetadata()
        method_invocation = usage_metadata_proto.usage.add()
        method_invocation.method_identifier = 'a.b'
        method_invocation.test_context = 'c.d'
        method_invocation.count = 51
        method_invocation = usage_metadata_proto.usage.add()
        method_invocation.method_identifier = 'e.f'
        method_invocation.test_context = 'h.i'
        method_invocation.count = 5

        expected = ProtoMetric(name='acts_usage_metadata',
                               data=usage_metadata_proto)

        actual = publisher._usage_map_to_proto_metric()
        self.assertEqual(expected.name, actual.name)
        self.assertEqual(parse_proto_to_ascii(expected.data),
                         parse_proto_to_ascii(actual.data))

    @patch(CURRENT_CONTEXT)
    def test_publish(self, current_context):
        context = Mock()
        current_context.return_value = context
        publisher = UsageMetadataPublisher()
        publisher._publish_single = Mock()

        publisher.publish()

        publisher._publish_single.assert_called_once()


if __name__ == '__main__':
    unittest.main()