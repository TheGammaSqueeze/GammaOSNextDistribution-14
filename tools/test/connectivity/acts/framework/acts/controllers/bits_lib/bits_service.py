#!/usr/bin/env python3
#
#   Copyright 2020 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the 'License');
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an 'AS IS' BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

import atexit
import json
import logging
import os
import re
import signal
import tempfile
import time

from enum import Enum

from acts import context
from acts.libs.proc import job
from acts.libs.proc import process


class BitsServiceError(Exception):
    pass


class BitsServiceStates(Enum):
    NOT_STARTED = 'not-started'
    STARTED = 'started'
    STOPPED = 'stopped'


class BitsService(object):
    """Helper class to start and stop a bits service

    Attributes:
        port: When the service starts the port it was assigned to is made
        available for external agents to reference to the background service.
        config: The BitsServiceConfig used to configure this service.
        name: A free form string.
        service_state: A BitsServiceState that represents the service state.
    """

    def __init__(self, config, binary, output_log_path,
                 name='bits_service_default',
                 timeout=None):
        """Creates a BitsService object.

        Args:
            config: A BitsServiceConfig.
            described in go/pixel-bits/user-guide/service/configuration.md
            binary: Path to a bits_service binary.
            output_log_path: Full path to where the resulting logs should be
            stored.
            name: Optional string to identify this service by. This
            is used as reference in logs to tell this service apart from others
            running in parallel.
            timeout: Maximum time in seconds the service should be allowed
            to run in the background after start. If left undefined the service
            in the background will not time out.
        """
        self.name = name
        self.port = None
        self.config = config
        self.service_state = BitsServiceStates.NOT_STARTED
        self._timeout = timeout
        self._binary = binary
        self._log = logging.getLogger()
        self._process = None
        self._output_log = open(output_log_path, 'w')
        self._collections_dir = tempfile.TemporaryDirectory(
            prefix='bits_service_collections_dir_')
        self._cleaned_up = False
        atexit.register(self._atexit_cleanup)

    def _atexit_cleanup(self):
        if not self._cleaned_up:
            self._log.error('Cleaning up bits_service %s at exit.', self.name)
            self._cleanup()

    def _write_extra_debug_logs(self):
        dmesg_log = '%s.dmesg.txt' % self._output_log.name
        dmesg = job.run(['dmesg', '-e'], ignore_status=True)
        with open(dmesg_log, 'w') as f:
            f.write(dmesg.stdout)

        free_log = '%s.free.txt' % self._output_log.name
        free = job.run(['free', '-m'], ignore_status=True)
        with open(free_log, 'w') as f:
            f.write(free.stdout)

        df_log = '%s.df.txt' % self._output_log.name
        df = job.run(['df', '-h'], ignore_status=True)
        with open(df_log, 'w') as f:
            f.write(df.stdout)

    def _cleanup(self):
        self._write_extra_debug_logs()
        self.port = None
        self._collections_dir.cleanup()
        if self._process and self._process.is_running():
            self._process.signal(signal.SIGINT)
            self._log.debug('SIGINT sent to bits_service %s.' % self.name)
            self._process.wait(kill_timeout=60.0)
            self._log.debug('bits_service %s has been stopped.' % self.name)
        self._output_log.close()
        if self.config.has_monsoon:
            job.run([self.config.monsoon_config.monsoon_binary,
                     '--serialno',
                     str(self.config.monsoon_config.serial_num),
                     '--usbpassthrough',
                     'on'],
                    timeout=10)
        self._cleaned_up = True

    def _service_started_listener(self, line):
        if self.service_state is BitsServiceStates.STARTED:
            return
        if 'Started server!' in line and self.port is not None:
            self.service_state = BitsServiceStates.STARTED

    PORT_PATTERN = re.compile(r'.*Server listening on .*:(\d+)\.$')

    def _service_port_listener(self, line):
        if self.port is not None:
            return
        match = self.PORT_PATTERN.match(line)
        if match:
            self.port = match.group(1)

    def _output_callback(self, line):
        self._output_log.write(line)
        self._output_log.write('\n')
        self._service_port_listener(line)
        self._service_started_listener(line)

    def _trigger_background_process(self, binary):
        config_path = os.path.join(
            context.get_current_context().get_full_output_path(),
            '%s.config.json' % self.name)
        with open(config_path, 'w') as f:
            f.write(json.dumps(self.config.config_dic, indent=2))

        cmd = [binary,
               '--port',
               '0',
               '--collections_folder',
               self._collections_dir.name,
               '--collector_config_file',
               config_path]

        # bits_service only works on linux systems, therefore is safe to assume
        # that 'timeout' will be available.
        if self._timeout:
            cmd = ['timeout',
                   '--signal=SIGTERM',
                   '--kill-after=60',
                   str(self._timeout)] + cmd

        self._process = process.Process(cmd)
        self._process.set_on_output_callback(self._output_callback)
        self._process.set_on_terminate_callback(self._on_terminate)
        self._process.start()

    def _on_terminate(self, *_):
        self._log.error('bits_service %s stopped unexpectedly.', self.name)
        self._cleanup()

    def start(self):
        """Starts the bits service in the background.

        This function blocks until the background service signals that it has
        successfully started. A BitsServiceError is raised if the signal is not
        received.
        """
        if self.service_state is BitsServiceStates.STOPPED:
            raise BitsServiceError(
                'bits_service %s was already stopped. A stopped'
                ' service can not be started again.' % self.name)

        if self.service_state is BitsServiceStates.STARTED:
            raise BitsServiceError(
                'bits_service %s has already been started.' % self.name)

        self._log.info('starting bits_service %s', self.name)
        self._trigger_background_process(self._binary)

        # wait 40 seconds for the service to be ready.
        max_startup_wait = time.time() + 40
        while time.time() < max_startup_wait:
            if self.service_state is BitsServiceStates.STARTED:
                self._log.info('bits_service %s started on port %s', self.name,
                               self.port)
                return
            time.sleep(0.1)

        self._log.error('bits_service %s did not start on time, starting '
                        'service teardown and raising a BitsServiceError.')
        self._cleanup()
        raise BitsServiceError(
            'bits_service %s did not start successfully' % self.name)

    def stop(self):
        """Stops the bits service."""
        if self.service_state is BitsServiceStates.STOPPED:
            raise BitsServiceError(
                'bits_service %s has already been stopped.' % self.name)
        port = self.port
        self._log.info('stopping bits_service %s on port %s', self.name, port)
        self.service_state = BitsServiceStates.STOPPED
        self._cleanup()
        self._log.info('bits_service %s on port %s was stopped', self.name,
                       port)
