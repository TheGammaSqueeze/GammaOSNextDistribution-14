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

import shutil
import tempfile
import unittest
from unittest import TestCase
import warnings

from acts.base_test import BaseTestClass
from acts.metrics.loggers.blackbox import BlackboxMetricLogger
from acts.test_runner import TestRunner
from mobly.config_parser import TestRunConfig
from mock import call
from mock import Mock
from mock import patch

GET_CONTEXT_FOR_EVENT = 'acts.metrics.logger.get_context_for_event'
PROTO_METRIC_PUBLISHER = 'acts.metrics.logger.ProtoMetricPublisher'


class BlackboxMetricLoggerTest(TestCase):
    """Unit tests for BlackboxMetricLogger."""

    TEST_METRIC_NAME = "metric_name"

    def setUp(self):
        self.event = Mock()
        self.context = Mock()
        self.publisher = Mock()
        self._get_blackbox_identifier = lambda: str(id(self.context))

    def test_default_init_attributes(self):
        metric_name = Mock()

        logger = BlackboxMetricLogger(metric_name)

        self.assertEqual(logger.metric_name, metric_name)
        self.assertIsNone(logger.metric_key)

    def test_init_with_params(self):
        metric_name = Mock()
        metric_key = Mock()

        logger = BlackboxMetricLogger(metric_name, metric_key=metric_key)

        self.assertEqual(logger.metric_key, metric_key)

    @patch(PROTO_METRIC_PUBLISHER)
    @patch(GET_CONTEXT_FOR_EVENT)
    def test_init_with_event(self, _get_context, _publisher_cls):
        metric_name = Mock()

        logger = BlackboxMetricLogger(metric_name, event=self.event)

        self.assertIsNotNone(logger.context)
        self.assertIsNotNone(logger.publisher)

    @patch('acts.metrics.loggers.protos.gen.acts_blackbox_pb2'
           '.ActsBlackboxMetricResultsBundle')
    @patch('acts.metrics.loggers.protos.gen.acts_blackbox_pb2'
           '.ActsBlackboxMetricResult')
    def test_end_populates_result(self, mock_acts_blackbox,
                                  _mock_acts_blackbox_bundle):
        result = Mock()
        mock_acts_blackbox.return_value = result

        logger = BlackboxMetricLogger(self.TEST_METRIC_NAME)
        logger.context = self.context
        logger.publisher = self.publisher
        logger.context.identifier = 'Class.test'
        logger.metric_value = 'foo'

        logger.end(self.event)

        self.assertEqual(result.test_identifier, 'Class#test')
        self.assertEqual(result.metric_key,
                         '%s.%s' % ('Class#test', self.TEST_METRIC_NAME))
        self.assertEqual(result.metric_value, logger.metric_value)

    @patch('acts.metrics.loggers.protos.gen.acts_blackbox_pb2'
           '.ActsBlackboxMetricResultsBundle')
    @patch('acts.metrics.loggers.protos.gen.acts_blackbox_pb2'
           '.ActsBlackboxMetricResult')
    def test_end_uses_metric_value_on_metric_value_not_none(
        self, mock_acts_blackbox, _mock_acts_blackbox_bundle):
        result = Mock()
        expected_result = Mock()
        mock_acts_blackbox.return_value = result

        logger = BlackboxMetricLogger(self.TEST_METRIC_NAME)
        logger.context = self.context
        logger.context.identifier = 'Class.test'
        logger.publisher = self.publisher
        logger.metric_value = expected_result
        logger.end(self.event)

        self.assertEqual(result.metric_value, expected_result)

    @patch('acts.metrics.loggers.protos.gen.acts_blackbox_pb2'
           '.ActsBlackboxMetricResultsBundle')
    @patch('acts.metrics.loggers.protos.gen.acts_blackbox_pb2'
           '.ActsBlackboxMetricResult')
    def test_end_uses_custom_metric_key(self, mock_acts_blackbox,
                                        _mock_acts_blackbox_bundle):
        result = Mock()
        mock_acts_blackbox.return_value = result
        metric_key = 'metric_key'

        logger = BlackboxMetricLogger(self.TEST_METRIC_NAME,
                                      metric_key=metric_key)
        logger.context = self.context
        logger.publisher = self.publisher
        logger._get_blackbox_identifier = self._get_blackbox_identifier
        logger.metric_value = 'foo'

        logger.end(self.event)

        expected_metric_key = '%s.%s' % (metric_key, self.TEST_METRIC_NAME)
        self.assertEqual(result.metric_key, expected_metric_key)

    @patch('acts.metrics.loggers.protos.gen.acts_blackbox_pb2'
           '.ActsBlackboxMetricResultsBundle')
    @patch('acts.metrics.loggers.blackbox.ProtoMetric')
    @patch('acts.metrics.loggers.blackbox.md5_proto')
    def test_end_does_publish(self, mock_md5_proto,
                              proto_metric_cls,
                              mock_acts_blackbox_bundle):
        result_bundle = Mock()
        mock_acts_blackbox_bundle.return_value = result_bundle
        mock_md5_proto.return_value = '<123456>'

        logger = BlackboxMetricLogger(self.TEST_METRIC_NAME)
        logger.context = self.context
        logger.publisher = self.publisher
        logger._get_blackbox_identifier = self._get_blackbox_identifier
        logger.metric_value = 42

        logger.end(self.event)

        self.assertEqual(1, proto_metric_cls.call_count,
                         'expected exactly 1 call')
        proto_metric_cls.assert_has_calls([
            call(name='blackbox_metrics_bundle.<123456>',
                 data=result_bundle)])

        self.publisher.publish.assert_called_once_with(
            [proto_metric_cls.return_value])


class BlackboxMetricLoggerIntegrationTest(TestCase):
    """Integration tests for BlackboxMetricLogger."""

    def setUp(self):
        warnings.simplefilter('ignore', ResourceWarning)

    @patch('acts.test_runner.sys')
    @patch('acts.test_runner.utils')
    @patch('acts.test_runner.importlib')
    def run_acts_test(self, test_class, importlib, utils, sys):
        test_run_config = TestRunConfig()
        test_run_config.testbed_name = 'SampleTestBed'
        test_run_config.log_path = tempfile.mkdtemp()
        test_run_config.controller_configs = {'testpaths': ['./']}

        mock_module = Mock()
        setattr(mock_module, test_class.__name__, test_class)
        utils.find_files.return_value = [(None, None, None)]
        importlib.import_module.return_value = mock_module
        runner = TestRunner(test_run_config, [(
            test_class.__name__,
            None,
        )])

        runner.run()
        runner.stop()
        shutil.rmtree(test_run_config.log_path)
        return runner

    @patch('acts.metrics.logger.ProtoMetricPublisher')
    def test_test_case_metric(self, publisher_cls):
        result = 5.0

        class MyTest(BaseTestClass):
            def __init__(self, controllers):
                super().__init__(controllers)
                self.tests = ('test_case',)
                self.metric = BlackboxMetricLogger.for_test_case('my_metric')

            def test_case(self):
                self.metric.metric_value = result

        self.run_acts_test(MyTest)

        args_list = publisher_cls().publish.call_args_list
        self.assertEqual(len(args_list), 1)
        published = self.__get_only_arg(args_list[0])[0]
        bundle = published.data
        metric = bundle.acts_blackbox_metric_results[0]
        self.assertIn('blackbox_metrics_bundle', published.name)
        self.assertEqual(metric.test_identifier, 'MyTest#test_case')
        self.assertEqual(metric.metric_key, 'MyTest#test_case.my_metric')
        self.assertEqual(metric.metric_value, result)

    @patch('acts.metrics.logger.ProtoMetricPublisher')
    def test_multiple_test_case_metrics(self, publisher_cls):
        result = 5.0

        class MyTest(BaseTestClass):
            def __init__(self, controllers):
                super().__init__(controllers)
                self.tests = ('test_case',)
                self.metric_1 = (BlackboxMetricLogger.for_test_case(
                    'my_metric_1'))
                self.metric_2 = (BlackboxMetricLogger.for_test_case(
                    'my_metric_2'))

            def test_case(self):
                self.metric_1.metric_value = result
                self.metric_2.metric_value = result

        self.run_acts_test(MyTest)

        args_list = publisher_cls().publish.call_args_list
        self.assertEqual(len(args_list), 2)
        all_published = [self.__get_only_arg(args)[0] for args in args_list]
        bundles = [published_instance.data for
                   published_instance in all_published]

        flattened_metrics = [metric for bundle in bundles for metric in
                             bundle.acts_blackbox_metric_results]

        self.assertEqual({metric.test_identifier
                          for metric in
                          flattened_metrics},
                         {'MyTest#test_case'})
        self.assertEqual({metric.metric_key
                          for metric in flattened_metrics},
                         {'MyTest#test_case.my_metric_1',
                          'MyTest#test_case.my_metric_2'})
        self.assertEqual({metric.metric_value
                          for metric in
                          flattened_metrics}, {result})

    @patch('acts.metrics.logger.ProtoMetricPublisher')
    def test_test_case_metric_with_custom_key(self, publisher_cls):
        result = 5.0

        class MyTest(BaseTestClass):
            def __init__(self, controllers):
                super().__init__(controllers)
                self.tests = ('test_case',)
                self.metrics = BlackboxMetricLogger.for_test_case(
                    'my_metric', metric_key='my_metric_key')

            def test_case(self):
                self.metrics.metric_value = result

        self.run_acts_test(MyTest)

        args_list = publisher_cls().publish.call_args_list
        self.assertEqual(len(args_list), 1)
        bundle = self.__get_only_arg(args_list[0])[0].data
        self.assertEqual(bundle.acts_blackbox_metric_results[0].metric_key,
                         'my_metric_key.my_metric')

    @patch('acts.metrics.logger.ProtoMetricPublisher')
    def test_test_class_metric(self, publisher_cls):
        publisher_cls().publish = Mock()
        result_1 = 5.0
        result_2 = 8.0

        class MyTest(BaseTestClass):
            def __init__(self, controllers):
                super().__init__(controllers)
                self.tests = (
                    'test_case_1',
                    'test_case_2',
                )
                self.metric = BlackboxMetricLogger.for_test_class('my_metric')

            def setup_class(self):
                self.metric.metric_value = 0

            def test_case_1(self):
                self.metric.metric_value += result_1

            def test_case_2(self):
                self.metric.metric_value += result_2

        self.run_acts_test(MyTest)

        args_list = publisher_cls().publish.call_args_list
        self.assertEqual(len(args_list), 1)
        bundle = self.__get_only_arg(args_list[0])[0].data
        metric = bundle.acts_blackbox_metric_results[0]
        self.assertEqual(metric.metric_value, result_1 + result_2)
        self.assertEqual(metric.test_identifier, MyTest.__name__)

    def __get_only_arg(self, call_args):
        self.assertEqual(len(call_args[0]) + len(call_args[1]), 1)
        if len(call_args[0]) == 1:
            return call_args[0][0]
        return next(iter(call_args[1].values()))


if __name__ == '__main__':
    unittest.main()
