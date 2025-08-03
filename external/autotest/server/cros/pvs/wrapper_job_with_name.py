# Copyright 2022 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

PASSCRITERIA_PREFIX = 'passcriteria_'


class wrapper_job_with_name():
    """
    wrapper_job_with_name wraps the server_job which is passed from the control
    file. This is used to alter the name of the job, by using the job.record
    function to represent the results of the internal "wrapped" job. Without
    the wrapper job, all tests will have the generic wrapper name, regardless
    of the test that is being wrapped.
    """

    def __init__(self,
                 job,
                 job_name,
                 wrapper_url,
                 args_dict,
                 default_pass_criteria={}):
        """
        wrapper_job_with_name wraps the server_job which is passed from the control
        file. This takes in the necessary parameters to execute that wrapper job.

        @param job: server_job object in the control file
        @param job_name: the name with which to overwrite the generic name
        @param wrapper_url: the name of the generic wrapper to call
        @param args_dict: passed in args_dict from the control file
        @param default_pass_criteria: the pass criteria to use if none are given
        """
        self._job = job
        self._name = job_name
        self._wrapper_url = wrapper_url
        self._pass_criteria = default_pass_criteria
        self._args_dict = args_dict
        self._parse_arg_dict_to_criteria()
        self._args_dict["pass_criteria"] = self._pass_criteria

    def run(self, host, test_to_wrap, wrap_args):
        """
        run executes the generic wrapper with the test_to_wrap and wrap_args
        necessary for that test wrapper, as well as recording the outer job
        state (which will overwrite the name of the test in results)

        @param host: host from the control file
        @param test_to_wrap: test name to pass into the generic wrapper
        @param wrap_args: test args to pass into the generic wrapper
        """
        self._job.record('START', None, self._name)
        if self._job.run_test(
                self._wrapper_url,
                host=host,
                test_to_wrap=test_to_wrap,
                wrap_args=wrap_args,
                disable_sysinfo=True,
                results_path=self._job._server_offload_dir_path(),
                **self._args_dict):
            self._job.record('INFO', None, self._name)
            self._job.record('END GOOD', None, self._name, "")
        else:
            self._job.record('INFO', None, self._name)
            self._job.record('END FAIL', None, self._name, "")

    def _parse_arg_dict_to_criteria(self):
        """
        _parse_arg_dict_to_criteria takes in the generic arg dict and looks
        for items with the prefix passcriteria_*. These are pass criteria values, and
        will be appended to the dictionary passed into the wrapper run.

        @param: arg_dict, the argv from autoserv parsed into a dict
        """
        for key in self._args_dict.keys():
            if key.startswith(PASSCRITERIA_PREFIX):
                self._pass_criteria[
                        key[len(PASSCRITERIA_PREFIX):]] = self._args_dict[key]
