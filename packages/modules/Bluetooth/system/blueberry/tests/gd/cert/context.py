#!/usr/bin/env python3
#
#   Copyright 2018 - The Android Open Source Project
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

import enum
import logging
import os


class ContextLevel(enum.IntEnum):
    ROOT = 0
    TESTCLASS = 1
    TESTCASE = 2


def get_current_context(depth=None):
    """Get the current test context at the specified depth.
    Pulls the most recently created context, with a level at or below the given
    depth, from the _contexts stack.

    Args:
        depth: The desired context level. For example, the TESTCLASS level would
            yield the current test class context, even if the test is currently
            within a test case.

    Returns: An instance of TestContext.
    """
    if depth is None:
        return _contexts[-1]
    return _contexts[min(depth, len(_contexts) - 1)]


def append_test_context(test_class_name, test_name):
    """Add test-specific context to the _contexts stack.
    A test should should call append_test_context() at test start and
    pop_test_context() upon test end.

    Args:
        test_class_name: name of the test class.
        test_name: name of the test.
    """
    if _contexts:
        _contexts.append(TestCaseContext(test_class_name, test_name))


def pop_test_context():
    """Remove the latest test-specific context from the _contexts stack.
    A test should should call append_test_context() at test start and
    pop_test_context() upon test end.
    """
    if _contexts:
        _contexts.pop()


class TestContext(object):
    """An object representing the current context in which a test is executing.

    The context encodes the current state of the test runner with respect to a
    particular scenario in which code is being executed. For example, if some
    code is being executed as part of a test case, then the context should
    encode information about that test case such as its name or enclosing
    class.

    The subcontext specifies a relative path in which certain outputs,
    e.g. logcat, should be kept for the given context.

    The full output path is given by
    <base_output_path>/<context_dir>/<subcontext>.

    Attributes:
        _base_output_paths: a dictionary mapping a logger's name to its base
                            output path
        _subcontexts: a dictionary mapping a logger's name to its
                      subcontext-level output directory
    """

    _base_output_paths = {}
    _subcontexts = {}

    def get_base_output_path(self, log_name=None):
        """Gets the base output path for this logger.

        The base output path is interpreted as the reporting root for the
        entire test runner.

        If a path has been added with add_base_output_path, it is returned.
        Otherwise, a default is determined by _get_default_base_output_path().

        Args:
            log_name: The name of the logger.

        Returns:
            The output path.
        """
        if log_name in self._base_output_paths:
            return self._base_output_paths[log_name]
        return self._get_default_base_output_path()

    def get_subcontext(self, log_name=None):
        """Gets the subcontext for this logger.

        The subcontext is interpreted as the directory, relative to the
        context-level path, where all outputs of the given logger are stored.

        If a path has been added with add_subcontext, it is returned.
        Otherwise, the empty string is returned.

        Args:
            log_name: The name of the logger.

        Returns:
            The output path.
        """
        return self._subcontexts.get(log_name, '')

    def get_full_output_path(self, log_name=None):
        """Gets the full output path for this context.

        The full path represents the absolute path to the output directory,
        as given by <base_output_path>/<context_dir>/<subcontext>

        Args:
            log_name: The name of the logger. Used to specify the base output
                      path and the subcontext.

        Returns:
            The output path.
        """

        path = os.path.join(
            self.get_base_output_path(log_name), self._get_default_context_dir(), self.get_subcontext(log_name))
        os.makedirs(path, exist_ok=True)
        return path

    def _get_default_base_output_path(self):
        """Gets the default base output path.

        This will attempt to use logging path set up in the global
        logger.

        Returns:
            The logging path.

        Raises:
            EnvironmentError: If logger has not been initialized.
        """
        try:
            return logging.log_path
        except AttributeError as e:
            raise EnvironmentError('The Mobly logger has not been set up and'
                                   ' "base_output_path" has not been set.') from e

    def _get_default_context_dir(self):
        """Gets the default output directory for this context."""
        raise NotImplementedError()


class RootContext(TestContext):
    """A TestContext that represents a test run."""

    @property
    def identifier(self):
        return 'root'

    def _get_default_context_dir(self):
        """Gets the default output directory for this context.

        Logs at the root level context are placed directly in the base level
        directory, so no context-level path exists."""
        return ''


class TestCaseContext(TestContext):
    """A TestContext that represents a test case.

    Attributes:
        test_case: the name of the test case.
        test_class: the name of the test class.
    """

    def __init__(self, test_class, test_case):
        """Initializes a TestCaseContext for the given test case.

        Args:
            test_class: test-class name.
            test_case: test name.
        """
        self.test_class = test_class
        self.test_case = test_case

    @property
    def test_case_name(self):
        return self.test_case

    @property
    def test_class_name(self):
        return self.test_class

    @property
    def identifier(self):
        return '%s.%s' % (self.test_class_name, self.test_case_name)

    def _get_default_context_dir(self):
        """Gets the default output directory for this context.

        For TestCaseContexts, this will be the name of the test itself.
        """
        return self.test_case_name


# stack for keeping track of the current test context
_contexts = [RootContext()]
