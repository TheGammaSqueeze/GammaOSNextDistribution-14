# Copyright 2022 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import os
import re
import logging
import json

import common
from autotest_lib.server import autotest, test
from autotest_lib.client.common_lib import error

from google.protobuf.text_format import Parse

# run protoc --proto_path=./ pass_criteria.proto --python_out ./
# with caution for version/upgrade compatibility
from . import pass_criteria_pb2


class test_with_pass_criteria(test.test):
    """
    test_with_pass_criteria extends the base test implementation to allow for
    test result comparison between the performance keyvalues output from a
    target test, and the input pass_criteria dictionary.

    It can be used to create a domain specific test wrapper such as
    power_QualTestWrapper.
    """

    def initialize(self, test_to_wrap):
        """
        initialize implements the initialize call in test.test, is called before
        execution of the test
        """
        self._test_prefix = []
        self._perf_dict = {}
        self._attr_dict = {}
        self._results_path = self.job._server_offload_dir_path()
        self._wrapper_results = self._results_path + self.tagged_testname + '/'
        logging.debug('...results going to %s', str(self._results_path))
        self._wrapped_test_results_keyval_path = (self._wrapper_results +
                                                  test_to_wrap +
                                                  '/results/keyval')
        self._wrapped_test_keyval_path = self._wrapper_results + test_to_wrap + '/keyval'
        self._wrapper_test_keyval_path = self._wrapper_results + 'keyval'

    def _check_wrapped_test_passed(self, test_name):
        results_path = self._wrapper_results + test_name + ""

    def _load_proto_to_pass_criteria(self):
        """
        _load_proto_to_pass_criteria optionally inputs a textproto file
        or a ':' separated string which represents the pass criteria for
        the test, and adds it to the pass criteria dictionary.
        """
        for textproto in self._textproto_path.split(':'):
            if not os.path.exists(textproto):
                raise error.TestFail('provided textproto path ' + textproto +
                                     ' does not exist')

            logging.info('loading criteria from textproto %s', textproto)
            with open(textproto) as textpb:
                textproto_criteria = Parse(textpb.read(),
                                           pass_criteria_pb2.PassCriteria())
            for criteria in textproto_criteria.criteria:
                lower_bound = criteria.lower_bound.bound if (
                        criteria.HasField('lower_bound')) else None
                upper_bound = criteria.upper_bound.bound if (
                        criteria.HasField('upper_bound')) else None
                if criteria.test_name != self._test_to_wrap and criteria.test_name != '':
                    logging.info('criteria %s does not apply',
                                 criteria.name_regex)
                    continue
                try:
                    self._pass_criteria[criteria.name_regex] = (lower_bound,
                                                                upper_bound)
                    logging.info('adding criteria %s', criteria.name_regex)
                except:
                    raise error.TestFail('invalid pass criteria provided')

    def add_prefix_test(self, test='', prefix_args_dict=None):
        """
        add_prefix_test takes a test_name and args_dict for that test.
        This function allows a user creating a domain specific test wrapper
        to add any prefix tests that must run prior to execution of the
        target test.

        @param test: the name of the test to add as a prefix test operation
        @param prefix_args_dict: the dictionary of args to pass to the test
        when it is run
        """
        if prefix_args_dict is None:
            prefix_args_dict = {}
        self._test_prefix.append((test, prefix_args_dict))

    def _print_bounds_error(self, criteria, failed_criteria, value):
        """
        _print_bounds_error will indicate missing pass criteria, printing the
        error string with failing criteria and target range

        @param criteria: the name of the pass criteria to log a failure on
        @param failed_criteria: the name of the criteria that regex matched
        @param value: the actual value of the failing pass criteria
        """
        logging.info('criteria %s: %s out of range %s', failed_criteria,
                     str(value), str(self._pass_criteria[criteria]))

    def _parse_wrapped_results_keyvals(self):
        """
        _parse_wrapped_results_keyvals first loads all of the performance and
        and attribute keyvals from the wrapped test, and then copies all of
        the test_attribute keyvals from that wrapped test into the wrapper.
        Without these keyvals being copied over, none of the metadata from
        the client job are captured in the job summary.

        @raises: error.TestFail: If any of the respective keyvals are missing
        """
        if os.path.exists(self._wrapped_test_results_keyval_path):
            with open(self._wrapped_test_results_keyval_path
                      ) as results_keyval_file:
                keyval_result = results_keyval_file.readline()
                while keyval_result:
                    regmatch = re.search(r'(.*){(.*)}=(.*)', keyval_result)
                    if regmatch is None:
                        break
                    key = regmatch.group(1)
                    which_dict = regmatch.group(2)
                    value = regmatch.group(3)
                    if which_dict != 'perf':
                        continue

                    self._perf_dict[key] = value
                    keyval_result = results_keyval_file.readline()

        with open(self._wrapped_test_keyval_path,
                  'r') as wrapped_test_keyval_file, open(
                          self._wrapper_test_keyval_path,
                          'a') as test_keyval_file:
            for keyval in wrapped_test_keyval_file:
                test_keyval_file.write(keyval)

    def _find_matching_keyvals(self):
        for c in self._pass_criteria:
            self._criteria_to_keyvals[c] = []
            for key in self._perf_dict.keys():
                if re.fullmatch(c, key):
                    logging.info('adding %s as matched key', key)
                    self._criteria_to_keyvals[c].append(key)

    def _verify_criteria(self):
        failing_criteria = 0
        for criteria in self._pass_criteria:
            logging.info('Checking %s now', criteria)
            if type(criteria) is not str:
                criteria = criteria.decode('utf-8')
            range_spec = self._pass_criteria[criteria]

            for perf_val in self._criteria_to_keyvals[criteria]:
                logging.info('Checking: %s against %s', str(criteria),
                             perf_val)
                actual_value = self._perf_dict[perf_val]
                logging.info('%s value is %s, spec is %s', perf_val,
                             float(actual_value), range_spec)

                # range_spec is passed into the dictionary as a tuple of upper and lower
                lower_bound, upper_bound = range_spec

                if lower_bound is not None and not (float(actual_value) >=
                                                    float(lower_bound)):
                    failing_criteria = failing_criteria + 1
                    self._print_bounds_error(criteria, perf_val, actual_value)

                if upper_bound is not None and not (float(actual_value) <
                                                    float(upper_bound)):
                    failing_criteria = failing_criteria + 1
                    self._print_bounds_error(criteria, perf_val, actual_value)

        if failing_criteria > 0:
            raise error.TestFail(
                    str(failing_criteria) +
                    ' criteria failed, see log for detail')

    def run_once(self,
                 host=None,
                 test_to_wrap=None,
                 pdash_note='',
                 wrap_args={},
                 pass_criteria={}):
        """
        run_once implements the run_once call in test.test, is called to begin
        execution of the test

        @param host: host from control file with which to run the test
        @param test_to_wrap: test name to execute in the wrapper
        @param pdash_note: note to annotate results on the dashboard
        @param wrap_args: args to pass to the wrapped test execution
        @param pass_criteria: dictionary of criteria to compare results against

        @raises error.TestFail: on failure of the wrapped tests
        """
        logging.debug('running test_with_pass_criteria run_once')
        logging.debug('with test name %s', str(self.tagged_testname))
        self._wrap_args = wrap_args
        self._test_to_wrap = test_to_wrap
        if self._test_to_wrap == None:
            raise error.TestFail('No test_to_wrap given')

        if isinstance(pass_criteria, dict):
            self._pass_criteria = pass_criteria
        else:
            logging.info('loading from string dict %s', pass_criteria)
            self._pass_criteria = json.loads(pass_criteria)

        self._textproto_path = self._pass_criteria.get('textproto_path', None)
        if self._textproto_path is None:
            logging.info('not using textproto criteria definitions')
        else:
            self._pass_criteria.pop('textproto_path')
            self._load_proto_to_pass_criteria()

        logging.debug('wrapping test %s', self._test_to_wrap)
        logging.debug('with wrap args %s', str(self._wrap_args))
        logging.debug('and pass criteria %s', str(self._pass_criteria))
        client_at = autotest.Autotest(host)

        for test, argv in self._test_prefix:
            argv['pdash_note'] = pdash_note
            try:
                client_at.run_test(test, check_client_result=True, **argv)
            except:
                raise error.TestFail('Prefix test failed, see log for details')

        try:
            client_at.run_test(self._test_to_wrap,
                               check_client_result=True,
                               **self._wrap_args)
        except:
            self.postprocess()
            raise error.TestFail('Wrapped test failed, see log for details')

    def postprocess(self):
        """
        postprocess is called after the completion of run_once by the test framework

        @raises error.TestFail: on any pass criteria failure
        """
        self._parse_wrapped_results_keyvals()
        if self._pass_criteria == {}:
            return
        self._criteria_to_keyvals = {}
        self._find_matching_keyvals()
        self._verify_criteria()
