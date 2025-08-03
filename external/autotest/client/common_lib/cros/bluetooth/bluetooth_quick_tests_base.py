# Lint as: python2, python3
# Copyright 2022 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""
This class provides base wrapper functions for Bluetooth quick test
"""

import functools
import logging

from autotest_lib.client.common_lib import error


class BluetoothQuickTestsBase(object):
    """Provides base helper functions for Bluetooth quick test batches/packages.

    The Bluetooth quick test infrastructure provides a way to quickly run a set
    of tests. As for today, auto-test ramp up time per test is about 90-120
    seconds, where a typical Bluetooth test may take ~30-60 seconds to run.

    The quick test infra, implemented in this class, saves this huge overhead
    by running only the minimal reset and cleanup operations required between
    each set of tests (takes a few seconds).

    This class provides wrapper functions to start and end a test, a batch or a
    package. A batch is defined as a set of tests, preferably with a common
    subject. A package is a set of batches.
    This class takes care of tests, batches, and packages test results, and
    prints out summaries to results. The class also resets and cleans up
    required states between tests, batches and packages.

    A batch can also run as a separate auto-test. There is a place holder to
    add a way to run a specific test of a batch autonomously.

    A batch can be implemented by inheriting from this class, and using its
    wrapper functions. A package can be implemented by inheriting from a set of
    batches.

    Adding a test to one of the batches is as easy as adding a method to the
    class of the batch.
    """

    # Some delay is needed between tests. TODO(yshavit): investigate and remove
    TEST_SLEEP_SECS = 3

    def _print_delimiter(self):
        logging.info('=======================================================')

    def quick_test_init(self, flag='Quick Health'):
        """Inits the quick test."""

        self.flag = flag
        self.test_iter = None

        self.bat_tests_results = []
        self.bat_pass_count = 0
        self.bat_fail_count = 0
        self.bat_testna_count = 0
        self.bat_warn_count = 0
        self.bat_name = None
        self.bat_iter = None

        self.pkg_tests_results = []
        self.pkg_pass_count = 0
        self.pkg_fail_count = 0
        self.pkg_testna_count = 0
        self.pkg_warn_count = 0
        self.pkg_name = None
        self.pkg_iter = None
        self.pkg_is_running = False

    def quick_test_get_model_name(self):
        """This method should be implemented by children classes.

        The ways to get the model names are different between server and client
        sides. The derived class should provide the method to get the info.
        """
        raise NotImplementedError

    def quick_test_get_chipset_name(self):
        """This method should be implemented by children classes.

        The ways to get the chipset names are different between server and
        client sides. The derived class should provide the method to get the
        info.
        """
        raise NotImplementedError

    @staticmethod
    def quick_test_test_decorator(test_name,
                                  flags=None,
                                  check_runnable_func=None,
                                  pretest_func=None,
                                  posttest_func=None,
                                  model_testNA=None,
                                  model_testWarn=None,
                                  skip_models=None,
                                  skip_chipsets=None,
                                  skip_common_errors=False):
        """A decorator providing a wrapper to a quick test.

        Using the decorator a test method can implement only the core
        test and let the decorator handle the quick test wrapper methods
        (reset/cleanup/logging).

        @param test_name: The name of the test to log.
        @param flags: List of string to describe who should run the test. The
                      string could be one of the following:
                          ['AVL', 'Quick Health', 'All'].
        @check_runnable_func: A function that accepts a bluetooth quick test
                              instance as argument. If not None and returns
                              False, the test exits early without failure.
        @pretest_func: A function that accepts a bluetooth quick test instance
                       as argument. If not None, the function is run right
                       before the test method.
        @posttest_func: A function that accepts a bluetooth quick test instance
                        as argument. If not None, the function is run after the
                        test summary is logged.
                        Note that the exception raised from this function is NOT
                        caught by the decorator.
        @param model_testNA: If the current platform is in this list, failures
                             are emitted as TestNAError.
        @param model_testWarn: If the current platform is in this list, failures
                               are emitted as TestWarn.
        @param skip_models: Raises TestNA on these models and doesn't attempt to
                            run the tests.
        @param skip_chipsets: Raises TestNA on these chipset and doesn't attempt
                              to run the tests.
        @param skip_common_errors: If the test encounters a common error (such
                                   as USB disconnect or daemon crash), mark the
                                   test as TESTNA instead. USE THIS SPARINGLY,
                                   it may mask bugs. This is available for tests
                                   that require state to be properly retained
                                   throughout the whole test (i.e. advertising)
                                   and any outside failure will cause the test
                                   to fail.
        """

        if flags is None:
            flags = ['All']
        if model_testNA is None:
            model_testNA = []
        if model_testWarn is None:
            model_testWarn = []
        if skip_models is None:
            skip_models = []
        if skip_chipsets is None:
            skip_chipsets = []

        def decorator(test_method):
            """A decorator wrapper of the decorated test_method.

            @param test_method: The test method being decorated.

            @return: The wrapper of the test method.
            """

            @functools.wraps(test_method)
            def wrapper(self):
                """A wrapper of the decorated method."""

                # Set test name before exiting so batches correctly identify
                # failing tests
                self.test_name = test_name

                # Reset failure info before running any check, so
                # quick_test_test_log_results() can judge the result correctly.
                self.fails = []
                self.had_known_common_failure = False

                # Check that the test is runnable in current setting
                if not (self.flag in flags or 'All' in flags):
                    logging.info('SKIPPING TEST %s', test_name)
                    logging.info('flag %s not in %s', self.flag, flags)
                    self._print_delimiter()
                    return

                if check_runnable_func and not check_runnable_func(self):
                    return

                try:
                    model = self.quick_test_get_model_name()
                    if model in skip_models:
                        logging.info('SKIPPING TEST %s', test_name)
                        raise error.TestNAError(
                                'Test not supported on this model')

                    chipset = self.quick_test_get_chipset_name()
                    logging.debug('Bluetooth module name is %s', chipset)
                    if chipset in skip_chipsets:
                        logging.info('SKIPPING TEST %s on chipset %s',
                                     test_name, chipset)
                        raise error.TestNAError(
                                'Test not supported on this chipset')

                    if pretest_func:
                        pretest_func(self)

                    self._print_delimiter()
                    logging.info('Starting test: %s', test_name)

                    test_method(self)
                except error.TestError as e:
                    fail_msg = '[--- error {} ({})]'.format(
                            test_method.__name__, str(e))
                    logging.error(fail_msg)
                    self.fails.append(fail_msg)
                except error.TestFail as e:
                    fail_msg = '[--- failed {} ({})]'.format(
                            test_method.__name__, str(e))
                    logging.error(fail_msg)
                    self.fails.append(fail_msg)
                except error.TestNAError as e:
                    fail_msg = '[--- SKIPPED {} ({})]'.format(
                            test_method.__name__, str(e))
                    logging.error(fail_msg)
                    self.fails.append(fail_msg)
                except Exception as e:
                    fail_msg = '[--- unknown error {} ({})]'.format(
                            test_method.__name__, str(e))
                    logging.exception(fail_msg)
                    self.fails.append(fail_msg)

                self.quick_test_test_log_results(
                        model_testNA=model_testNA,
                        model_testWarn=model_testWarn,
                        skip_common_errors=skip_common_errors)

                if posttest_func:
                    posttest_func(self)

            return wrapper

        return decorator

    def quick_test_test_log_results(self,
                                    model_testNA=None,
                                    model_testWarn=None,
                                    skip_common_errors=False):
        """Logs and tracks the test results."""

        if model_testNA is None:
            model_testNA = []
        if model_testWarn is None:
            model_testWarn = []

        result_msgs = []
        model = self.quick_test_get_model_name()

        if self.test_iter is not None:
            result_msgs += ['Test Iter: ' + str(self.test_iter)]

        if self.bat_iter is not None:
            result_msgs += ['Batch Iter: ' + str(self.bat_iter)]

        if self.pkg_is_running is True:
            result_msgs += ['Package iter: ' + str(self.pkg_iter)]

        if self.bat_name is not None:
            result_msgs += ['Batch Name: ' + self.bat_name]

        if self.test_name is not None:
            result_msgs += ['Test Name: ' + self.test_name]

        result_msg = ", ".join(result_msgs)

        if not bool(self.fails):
            result_msg = 'PASSED | ' + result_msg
            self.bat_pass_count += 1
            self.pkg_pass_count += 1
        # The test should be marked as TESTNA if any of the test expressions
        # were SKIPPED (they threw their own TESTNA error) or the model is in
        # the list of NA models (so any failure is considered NA instead)
        elif model in model_testNA or any(['SKIPPED' in x
                                           for x in self.fails]):
            result_msg = 'TESTNA | ' + result_msg
            self.bat_testna_count += 1
            self.pkg_testna_count += 1
        elif model in model_testWarn:
            result_msg = 'WARN   | ' + result_msg
            self.bat_warn_count += 1
            self.pkg_warn_count += 1
        # Some tests may fail due to known common failure reasons (like usb
        # disconnect during suspend, bluetoothd crashes, etc). Skip those tests
        # with TESTNA when that happens.
        #
        # This should be used sparingly because it may hide legitimate errors.
        elif bool(self.had_known_common_failure) and skip_common_errors:
            result_msg = 'TESTNA | ' + result_msg
            self.bat_testna_count += 1
            self.pkg_testna_count += 1
        else:
            result_msg = 'FAIL   | ' + result_msg
            self.bat_fail_count += 1
            self.pkg_fail_count += 1

        logging.info(result_msg)
        self._print_delimiter()
        self.bat_tests_results.append(result_msg)
        self.pkg_tests_results.append(result_msg)

    @staticmethod
    def quick_test_batch_decorator(batch_name):
        """A decorator providing a wrapper to a batch.

        Using the decorator a test batch method can implement only its core
        tests invocations and let the decorator handle the wrapper, which is
        taking care for whether to run a specific test or the batch as a whole
        and and running the batch in iterations

        @param batch_name: The name of the batch to log.
        """

        def decorator(batch_method):
            """A decorator wrapper of the decorated test_method.

            @param test_method: The test method being decorated.
            @return: The wrapper of the test method.
            """

            @functools.wraps(batch_method)
            def wrapper(self, num_iterations=1, test_name=None):
                """A wrapper of the decorated method.

                @param num_iterations: How many iterations to run.
                @param test_name: Specific test to run otherwise None to run the
                                  whole batch.
                """

                if test_name is not None:
                    single_test_method = getattr(self, test_name)
                    for iter in range(1, num_iterations + 1):
                        self.test_iter = iter
                        single_test_method()

                    if self.fails:
                        # If failure is marked as TESTNA, prioritize that over
                        # a failure. Same with WARN.
                        if self.bat_testna_count > 0:
                            raise error.TestNAError(self.fails)
                        elif self.bat_warn_count > 0:
                            raise error.TestWarn(self.fails)
                        else:
                            raise error.TestFail(self.fails)
                else:
                    for iter in range(1, num_iterations + 1):
                        self.quick_test_batch_start(batch_name, iter)
                        batch_method(self, num_iterations, test_name)
                        self.quick_test_batch_end()

            return wrapper

        return decorator

    def quick_test_batch_start(self, bat_name, iteration=1):
        """Clears and sets test batch variables."""

        self.bat_tests_results = []
        self.bat_pass_count = 0
        self.bat_fail_count = 0
        self.bat_testna_count = 0
        self.bat_warn_count = 0
        self.bat_name = bat_name
        self.bat_iter = iteration

    def quick_test_batch_end(self):
        """Prints results summary of a test batch."""

        logging.info(
                '%s Test Batch Summary: total pass %d, total fail %d, '
                'warn %d, NA %d', self.bat_name, self.bat_pass_count,
                self.bat_fail_count, self.bat_warn_count,
                self.bat_testna_count)
        for result in self.bat_tests_results:
            logging.info(result)
        self._print_delimiter()
        if self.bat_fail_count > 0:
            logging.error('===> Test Batch Failed! More than one failure')
            self._print_delimiter()
            if self.pkg_is_running is False:
                raise error.TestFail(self.bat_tests_results)
        elif self.bat_testna_count > 0:
            logging.error('===> Test Batch Passed! Some TestNA results')
            self._print_delimiter()
            if self.pkg_is_running is False:
                raise error.TestNAError(self.bat_tests_results)
        elif self.bat_warn_count > 0:
            logging.error('===> Test Batch Passed! Some WARN results')
            self._print_delimiter()
            if self.pkg_is_running is False:
                raise error.TestWarn(self.bat_tests_results)
        else:
            logging.info('===> Test Batch Passed! zero failures')
            self._print_delimiter()

    def quick_test_package_start(self, pkg_name):
        """Clears and sets test package variables."""

        self.pkg_tests_results = []
        self.pkg_pass_count = 0
        self.pkg_fail_count = 0
        self.pkg_name = pkg_name
        self.pkg_is_running = True

    def quick_test_print_summary(self):
        """Prints results summary of a batch."""

        logging.info(
                '%s Test Package Summary: total pass %d, total fail %d, '
                'Warn %d, NA %d', self.pkg_name, self.pkg_pass_count,
                self.pkg_fail_count, self.pkg_warn_count,
                self.pkg_testna_count)
        for result in self.pkg_tests_results:
            logging.info(result)
        self._print_delimiter()

    def quick_test_package_update_iteration(self, iteration):
        """Updates state and prints log per package iteration.

        Must be called to have a proper package test result tracking.
        """

        self.pkg_iter = iteration
        if self.pkg_name is None:
            logging.error('Error: no quick package is running')
            raise error.TestFail('Error: no quick package is running')
        logging.info('Starting %s Test Package iteration %d', self.pkg_name,
                     iteration)

    def quick_test_package_end(self):
        """Prints final result of a test package."""

        if self.pkg_fail_count > 0:
            logging.error('===> Test Package Failed! More than one failure')
            self._print_delimiter()
            raise error.TestFail(self.bat_tests_results)
        elif self.pkg_testna_count > 0:
            logging.error('===> Test Package Passed! Some TestNA results')
            self._print_delimiter()
            raise error.TestNAError(self.bat_tests_results)
        elif self.pkg_warn_count > 0:
            logging.error('===> Test Package Passed! Some WARN results')
            self._print_delimiter()
            raise error.TestWarn(self.bat_tests_results)
        else:
            logging.info('===> Test Package Passed! zero failures')
            self._print_delimiter()
        self.pkg_is_running = False
