#!/usr/bin/env python3
#
# Copyright 2017 - The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

from mobly import signals


def test_info(predicate=None, **keyed_info):
    """Adds info about test.

    Extra info to include about the test. This info will be available in the
    test output. Note that if a key is given multiple times it will be added
    as a list of all values. If multiples of these are stacked their results
    will be merged.

    Example:
        # This test will have a variable my_var
        @test_info(my_var='THIS IS MY TEST')
        def my_test(self):
            return False

    Args:
        predicate: A func to call that if false will skip adding this test
                   info. Function signature is bool(test_obj, args, kwargs)
        **keyed_info: The key, value info to include in the extras for this
                      test.
    """

    def test_info_decorator(func):
        return _TestInfoDecoratorFunc(func, predicate, keyed_info)

    return test_info_decorator


def __select_last(test_signals, _):
    return test_signals[-1]


def repeated_test(num_passes, acceptable_failures=0, result_selector=__select_last):
    """A decorator that runs a test case multiple times.

    This decorator can be used to run a test multiple times and aggregate the
    data into a single test result. By setting `result_selector`, the user can
    access the returned result of each run, allowing them to average results,
    return the median, or gather and return standard deviation values.

    This decorator should be used on test cases, and should not be used on
    static or class methods. The test case must take in an additional argument,
    `attempt_number`, which returns the current attempt number, starting from
    1.

    Note that any TestSignal intended to abort or skip the test will take
    abort or skip immediately.

    Args:
        num_passes: The number of times the test needs to pass to report the
            test case as passing.
        acceptable_failures: The number of failures accepted. If the failures
            exceeds this number, the test will stop repeating. The maximum
            number of runs is `num_passes + acceptable_failures`. If the test
            does fail, result_selector will still be called.
        result_selector: A lambda that takes in the list of TestSignals and
            returns the test signal to report the test case as. Note that the
            list also contains any uncaught exceptions from the test execution.
    """

    def decorator(func):
        if not func.__name__.startswith('test_'):
            raise ValueError('Tests must start with "test_".')

        def test_wrapper(self):
            num_failures = 0
            num_seen_passes = 0
            test_signals_received = []
            for i in range(num_passes + acceptable_failures):
                try:
                    func(self, i + 1)
                except (signals.TestFailure, signals.TestError, AssertionError) as signal:
                    test_signals_received.append(signal)
                    num_failures += 1
                except signals.TestPass as signal:
                    test_signals_received.append(signal)
                    num_seen_passes += 1
                except (signals.TestSignal, KeyboardInterrupt):
                    raise
                except Exception as signal:
                    test_signals_received.append(signal)
                    num_failures += 1
                else:
                    num_seen_passes += 1
                    test_signals_received.append(
                        signals.TestPass('Test iteration %s of %s passed without details.' % (i, func.__name__)))

                if num_failures > acceptable_failures:
                    break
                elif num_seen_passes == num_passes:
                    break
                else:
                    self.teardown_test()
                    self.setup_test()

            raise result_selector(test_signals_received, self)

        return test_wrapper

    return decorator


def test_tracker_info(uuid, extra_environment_info=None, predicate=None):
    """Decorator for adding test tracker info to tests results.

    Will add test tracker info inside of Extras/test_tracker_info.

    Example:
        # This test will be linked to test tracker uuid abcd
        @test_tracker_info(uuid='abcd')
        def my_test(self):
            return False

    Args:
        uuid: The uuid of the test case in test tracker.
        extra_environment_info: Extra info about the test tracker environment.
        predicate: A func that if false when called will ignore this info.
    """
    return test_info(test_tracker_uuid=uuid, test_tracker_environment_info=extra_environment_info, predicate=predicate)


class _TestInfoDecoratorFunc(object):
    """Object that acts as a function decorator test info."""

    def __init__(self, func, predicate, keyed_info):
        self.func = func
        self.predicate = predicate
        self.keyed_info = keyed_info
        self.__name__ = func.__name__
        self.__doc__ = func.__doc__
        self.__module__ = func.__module__

    def __get__(self, instance, owner):
        """Called by Python to create a binding for an instance closure.

        When called by Python this object will create a special binding for
        that instance. That binding will know how to interact with this
        specific decorator.
        """
        return _TestInfoBinding(self, instance)

    def __call__(self, *args, **kwargs):
        """
        When called runs the underlying func and then attaches test info
        to a signal.
        """
        cause = None
        try:
            result = self.func(*args, **kwargs)

            if result or result is None:
                new_signal = signals.TestPass('')
            else:
                new_signal = signals.TestFailure('')
        except signals.TestSignal as signal:
            new_signal = signal
        except Exception as ex:
            cause = ex
            new_signal = signals.TestError(cause)

        if new_signal.extras is None:
            new_signal.extras = {}
        if not isinstance(new_signal.extras, dict):
            raise ValueError('test_info can only append to signal data that has a dict as the extra value.')

        gathered_extras = self._gather_local_info(None, *args, **kwargs)
        for k, v in gathered_extras.items():
            if k not in new_signal.extras:
                new_signal.extras[k] = v
            else:
                if not isinstance(new_signal.extras[k], list):
                    new_signal.extras[k] = [new_signal.extras[k]]

                new_signal.extras[k].insert(0, v)

        raise new_signal from cause

    def gather(self, *args, **kwargs):
        """
        Gathers the info from this decorator without invoking the underlying
        function. This will also gather all child info if the underlying func
        has that ability.

        Returns: A dictionary of info.
        """
        if hasattr(self.func, 'gather'):
            extras = self.func.gather(*args, **kwargs)
        else:
            extras = {}

        self._gather_local_info(extras, *args, **kwargs)

        return extras

    def _gather_local_info(self, gather_into, *args, **kwargs):
        """Gathers info from this decorator and ignores children.

        Args:
            gather_into: Gathers into a dictionary that already exists.

        Returns: The dictionary with gathered info in it.
        """
        if gather_into is None:
            extras = {}
        else:
            extras = gather_into
        if not self.predicate or self.predicate(args, kwargs):
            for k, v in self.keyed_info.items():
                if v and k not in extras:
                    extras[k] = v
                elif v and k in extras:
                    if not isinstance(extras[k], list):
                        extras[k] = [extras[k]]
                    extras[k].insert(0, v)

        return extras


class _TestInfoBinding(object):
    """
    When Python creates an instance of an object it creates a binding object
    for each closure that contains what the instance variable should be when
    called. This object is a similar binding for _TestInfoDecoratorFunc.
    When Python tries to create a binding of a _TestInfoDecoratorFunc it
    will return one of these objects to hold the instance for that closure.
    """

    def __init__(self, target, instance):
        """
        Args:
            target: The target for creating a binding to.
            instance: The instance to bind the target with.
        """
        self.target = target
        self.instance = instance
        self.__name__ = target.__name__

    def __call__(self, *args, **kwargs):
        """
        When this object is called it will call the target with the bound
        instance.
        """
        return self.target(self.instance, *args, **kwargs)

    def gather(self, *args, **kwargs):
        """
        Will gather the target with the bound instance.
        """
        return self.target.gather(self.instance, *args, **kwargs)
