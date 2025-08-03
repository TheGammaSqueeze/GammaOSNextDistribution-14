#!/usr/bin/python3
#
# Copyright (c) 2014 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import datetime as datetime_base
import logging
from datetime import datetime

import common

from autotest_lib.client.common_lib import global_config
from autotest_lib.server import utils
from autotest_lib.server.cros.dynamic_suite import reporting_utils

CONFIG = global_config.global_config


class DUTsNotAvailableError(utils.TestLabException):
    """Raised when a DUT label combination is not available in the lab."""


class NotEnoughDutsError(utils.TestLabException):
    """Rasied when the lab doesn't have the minimum number of duts."""

    def __init__(self, labels, num_available, num_required, hosts):
        """Initialize instance.

        Please pass arguments by keyword.

        @param labels: Labels required, including board an pool labels.
        @param num_available: Number of available hosts.
        @param num_required: Number of hosts required.
        @param hosts: Sequence of Host instances for given board and pool.
        """
        self.labels = labels
        self.num_available = num_available
        self.num_required = num_required
        self.hosts = hosts
        self.bug_id = None
        self.suite_name = None
        self.build = None


    def __repr__(self):
        return (
            '<{cls} at 0x{id:x} with'
            ' labels={this.labels!r},'
            ' num_available={this.num_available!r},'
            ' num_required={this.num_required!r},'
            ' bug_id={this.bug_id!r},'
            ' suite_name={this.suite_name!r},'
            ' build={this.build!r}>'
            .format(cls=type(self).__name__, id=id(self), this=self)
        )


    def __str__(self):
        msg_parts = [
            'Not enough DUTs for requirements: {this.labels};'
            ' required: {this.num_required}, found: {this.num_available}'
        ]
        format_dict = {'this': self}
        if self.bug_id is not None:
            msg_parts.append('bug: {bug_url}')
            format_dict['bug_url'] = reporting_utils.link_crbug(self.bug_id)
        if self.suite_name is not None:
            msg_parts.append('suite: {this.suite_name}')
        if self.build is not None:
            msg_parts.append('build: {this.build}')
        return ', '.join(msg_parts).format(**format_dict)


class SimpleTimer(object):
    """A simple timer used to periodically check if a deadline has passed."""

    def _reset(self):
        """Reset the deadline."""
        if not self.interval_hours or self.interval_hours < 0:
            logging.error('Bad interval %s', self.interval_hours)
            self.deadline = None
            return
        self.deadline = datetime.now() + datetime_base.timedelta(
                hours=self.interval_hours)


    def __init__(self, interval_hours=0.5):
        """Initialize a simple periodic deadline timer.

        @param interval_hours: Interval of the deadline.
        """
        self.interval_hours = interval_hours
        self._reset()


    def poll(self):
        """Poll the timer to see if we've hit the deadline.

        This method resets the deadline if it has passed. If the deadline
        hasn't been set, or the current time is less than the deadline, the
        method returns False.

        @return: True if the deadline has passed, False otherwise.
        """
        if not self.deadline or datetime.now() < self.deadline:
            return False
        self._reset()
        return True


class JobTimer(object):
    """Utility class capable of measuring job timeouts.
    """

    # Format used in datetime - string conversion.
    time_format = '%m-%d-%Y [%H:%M:%S]'

    def __init__(self, job_created_time, timeout_mins):
        """JobTimer constructor.

        @param job_created_time: float representing the time a job was
            created. Eg: time.time()
        @param timeout_mins: float representing the timeout in minutes.
        """
        self.job_created_time = datetime.fromtimestamp(job_created_time)
        self.timeout_hours = datetime_base.timedelta(hours=timeout_mins/60.0)
        self.debug_output_timer = SimpleTimer(interval_hours=0.5)
        self.past_halftime = False


    @classmethod
    def format_time(cls, datetime_obj):
        """Get the string formatted version of the datetime object.

        @param datetime_obj: A datetime.datetime object.
            Eg: datetime.datetime.now()

        @return: A formatted string containing the date/time of the
            input datetime.
        """
        return datetime_obj.strftime(cls.time_format)


    def elapsed_time(self):
        """Get the time elapsed since this job was created.

        @return: A timedelta object representing the elapsed time.
        """
        return datetime.now() - self.job_created_time


    def is_suite_timeout(self):
        """Check if the suite timed out.

        @return: True if more than timeout_hours has elapsed since the suite job
            was created.
        """
        if self.elapsed_time() >= self.timeout_hours:
            logging.info('Suite timed out. Started on %s, timed out on %s',
                         self.format_time(self.job_created_time),
                         self.format_time(datetime.now()))
            return True
        return False


    def first_past_halftime(self):
        """Check if we just crossed half time.

        This method will only return True once, the first time it is called
        after a job's elapsed time is past half its timeout.

        @return True: If this is the first call of the method after halftime.
        """
        if (not self.past_halftime and
            self.elapsed_time() > self.timeout_hours/2):
            self.past_halftime = True
            return True
        return False


class RPCHelper(object):
    """A class to help diagnose a suite run through the rpc interface.
    """

    def __init__(self, rpc_interface):
        """Constructor for rpc helper class.

        @param rpc_interface: An rpc object, eg: A RetryingAFE instance.
        """
        self.rpc_interface = rpc_interface


    def check_dut_availability(self, labels, minimum_duts=0,
                               skip_duts_check=False):
        """Check if DUT availability for a given board and pool is less than
        minimum.

        @param labels: DUT label dependencies, including board and pool
                       labels.
        @param minimum_duts: Minimum Number of available machines required to
                             run the suite. Default is set to 0, which means do
                             not force the check of available machines before
                             running the suite.
        @param skip_duts_check: If True, skip minimum available DUTs check.
        @raise: NotEnoughDutsError if DUT availability is lower than minimum.
        @raise: DUTsNotAvailableError if no host found for requested
                board/pool.
        """
        if minimum_duts == 0:
            return

        hosts = self.rpc_interface.get_hosts(
                invalid=False, multiple_labels=labels)
        if not hosts:
            raise DUTsNotAvailableError(
                    'No hosts found for labels %r. The test lab '
                    'currently does not cover test for those DUTs.' %
                    (labels,))

        if skip_duts_check:
            # Bypass minimum avilable DUTs check
            logging.debug('skip_duts_check is on, do not enforce minimum '
                          'DUTs check.')
            return

        if len(hosts) < minimum_duts:
            logging.debug('The total number of DUTs for %r is %d, '
                          'which is less than %d, the required minimum '
                          'number of available DUTS', labels, len(hosts),
                          minimum_duts)

        available_hosts = 0
        for host in hosts:
            if host.is_available():
                available_hosts += 1
        logging.debug('%d of %d DUTs are available for %r.',
                      available_hosts, len(hosts), labels)
        if available_hosts < minimum_duts:
            raise NotEnoughDutsError(
                labels=labels,
                num_available=available_hosts,
                num_required=minimum_duts,
                hosts=hosts)


    def diagnose_job(self, job_id, instance_server):
        """Diagnose a suite job.

        Logs information about the jobs that are still to run in the suite.

        @param job_id: The id of the suite job to get information about.
            No meaningful information gets logged if the id is for a sub-job.
        @param instance_server: The instance server.
            Eg: cautotest, cautotest-cq, localhost.
        """
        incomplete_jobs = self.rpc_interface.get_jobs(
                parent_job_id=job_id, summary=True,
                hostqueueentry__complete=False)
        if incomplete_jobs:
            logging.info('\n%s printing summary of incomplete jobs (%s):\n',
                         JobTimer.format_time(datetime.now()),
                         len(incomplete_jobs))
            for job in incomplete_jobs:
                logging.info('%s: %s', job.testname[job.testname.rfind('/')+1:],
                             reporting_utils.link_job(job.id, instance_server))
        else:
            logging.info('All jobs in suite have already completed.')
