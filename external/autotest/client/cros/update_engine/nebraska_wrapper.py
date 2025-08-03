# Lint as: python2, python3
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import errno
import json
import logging
import os
import requests
import subprocess
import six
import six.moves.urllib.parse

from autotest_lib.client.bin import utils
from autotest_lib.client.common_lib import autotemp
from autotest_lib.client.common_lib import error


# JSON attributes used in payload properties. Look at nebraska.py for more
# information.
KEY_PUBLIC_KEY='public_key'
KEY_METADATA_SIZE='metadata_size'
KEY_SHA256='sha256_hex'

# Path to the startup config file.
NEBRASKA_DIR = '/usr/local/nebraska'
NEBRASKA_CONFIG = os.path.join(NEBRASKA_DIR, 'config.json')
NEBRASKA_METADATA_DIR = os.path.join(NEBRASKA_DIR, 'metadata')


class NebraskaWrapper(object):
    """
    A wrapper around nebraska.py

    This wrapper is used to start a nebraska.py service and allow the
    update_engine to interact with it.

    """

    def __init__(self,
                 log_dir=None,
                 payload_url=None,
                 persist_metadata=False,
                 **props_to_override):
        """
        Initializes the NebraskaWrapper module.

        @param log_dir: The directory to write nebraska.log into.
        @param payload_url: The payload that will be returned in responses for
                            update requests. This can be a single URL string
                            or a list of URLs to return multiple payload URLs
                            (such as a platform payload + DLC payloads) in the
                            responses.
        @param persist_metadata: True to store the update and install metadata
                                 in a location that will survive a reboot. Use
                                 this if you plan on starting nebraska at
                                 system startup using a conf file. If False,
                                 the metadata will be stored in /tmp and will
                                 not persist after rebooting the device.
        @param props_to_override: Dictionary of key/values to use in responses
                instead of the default values in payload_url's properties file.

        """
        self._nebraska_server = None
        self._port = None
        self._log_dir = log_dir

        # _update_metadata_dir is the directory for storing the json metadata
        # files associated with the payloads.
        # _update_payloads_address is the address of the update server where
        # the payloads are staged.
        # The _install variables serve the same purpose for payloads intended
        # for DLC install requests.
        self._update_metadata_dir = None
        self._update_payloads_address = None
        self._install_metadata_dir = None
        self._install_payloads_address = None

        # Download the metadata files and save them in a tempdir for general
        # use, or in a directory that will survive reboot if we want nebraska
        # to be up after a reboot. If saving to a tempdir, save a reference
        # to it to ensure its reference count does not go to zero causing the
        # directory to be deleted.
        if payload_url:
            # Normalize payload_url to be a list.
            if not isinstance(payload_url, list):
                payload_url = [payload_url]

            if persist_metadata:
                self._create_nebraska_dir(metadata=True)
                self._update_metadata_dir = NEBRASKA_METADATA_DIR
            else:
                self._tempdir = autotemp.tempdir()
                self._update_metadata_dir = self._tempdir.name

            self._update_payloads_address = ''.join(
                payload_url[0].rpartition('/')[0:2])
            # We can reuse _update_metadata_dir and _update_payloads_address
            # for the DLC-specific install values for N-N tests, since the
            # install and update versions will be the same. For the delta
            # payload case, Nebraska will always use a full payload for
            # installation and prefer a delta payload for update, so both full
            # and delta payload metadata files can occupy the same
            # metadata_dir. The payloads_address can be shared as well,
            # provided all payloads have the same base URL.
            self._install_metadata_dir = self._update_metadata_dir
            self._install_payloads_address = self._update_payloads_address

            for url in payload_url:
                self.get_payload_properties_file(url,
                                                 self._update_metadata_dir,
                                                 **props_to_override)

    def __enter__(self):
        """So that NebraskaWrapper can be used as a Context Manager."""
        self.start()
        return self

    def __exit__(self, *exception_details):
        """
        So that NebraskaWrapper can be used as a Context Manager.

        @param exception_details: Details of exceptions happened in the
                ContextManager.

        """
        self.stop()

    def start(self):
        """
        Starts the Nebraska server.

        @raise error.TestError: If fails to start the Nebraska server.

        """
        # Any previously-existing files (port, pid and log files) will be
        # overriden by Nebraska during bring up.
        runtime_root = '/tmp/nebraska'
        cmd = ['nebraska.py', '--runtime-root', runtime_root]
        if self._log_dir:
            cmd += ['--log-file', os.path.join(self._log_dir, 'nebraska.log')]
        if self._update_metadata_dir:
            cmd += ['--update-metadata', self._update_metadata_dir]
        if self._update_payloads_address:
            cmd += ['--update-payloads-address', self._update_payloads_address]
        if self._install_metadata_dir:
            cmd += ['--install-metadata', self._install_metadata_dir]
        if self._install_payloads_address:
            cmd += ['--install-payloads-address',
                    self._install_payloads_address]

        logging.info('Starting nebraska.py with command: %s', cmd)

        try:
            self._nebraska_server = subprocess.Popen(cmd,
                                                     stdout=subprocess.PIPE,
                                                     stderr=subprocess.STDOUT)

            # Wait for port file to appear.
            port_file = os.path.join(runtime_root, 'port')
            utils.poll_for_condition(lambda: os.path.exists(port_file),
                                     timeout=5)

            with open(port_file, 'r') as f:
                self._port = int(f.read())

            # Send a health_check request to it to make sure its working.
            requests.get('http://127.0.0.1:%d/health_check' % self._port)

        except Exception as e:
            raise error.TestError('Failed to start Nebraska %s' % e)

    def stop(self):
        """Stops the Nebraska server."""
        if not self._nebraska_server:
            return
        try:
            self._nebraska_server.terminate()
            stdout, _ = self._nebraska_server.communicate()
            logging.info('Stopping nebraska.py with stdout %s', stdout)
            self._nebraska_server.wait()
        except subprocess.TimeoutExpired:
            logging.error('Failed to stop Nebraska. Ignoring...')
        finally:
            self._nebraska_server = None

    def get_update_url(self, **kwargs):
        """
        Returns a URL for getting updates from this Nebraska instance.

        @param kwargs: A set of key/values to form a search query to instruct
                Nebraska to do a set of activities. See
                nebraska.py::ResponseProperties for examples key/values.
        """

        query = '&'.join('%s=%s' % (k, v) for k, v in kwargs.items())
        url = six.moves.urllib.parse.SplitResult(scheme='http',
                                                 netloc='127.0.0.1:%d' %
                                                 self._port,
                                                 path='/update',
                                                 query=query,
                                                 fragment='')
        return six.moves.urllib.parse.urlunsplit(url)

    def get_payload_properties_file(self, payload_url, target_dir, **kwargs):
        """
        Downloads the payload properties file into a directory.

        @param payload_url: The URL to the update payload file.
        @param target_dir: The directory to download the file into.
        @param kwargs: A dictionary of key/values that needs to be overridden on
                the payload properties file.

        """
        payload_props_url = payload_url + '.json'
        _, _, file_name = payload_props_url.rpartition('/')
        try:
            response = json.loads(requests.get(payload_props_url).text)
            # Override existing keys if any.
            for k, v in six.iteritems(kwargs):
                # Don't set default None values. We don't want to override good
                # values to None.
                if v is not None:
                    response[k] = v
            with open(os.path.join(target_dir, file_name), 'w') as fp:
                json.dump(response, fp)

        except (requests.exceptions.RequestException,
                IOError,
                ValueError) as err:
            raise error.TestError(
                'Failed to get update payload properties: %s with error: %s' %
                (payload_props_url, err))

    def update_config(self, **kwargs):
        """
        Updates the current running nebraska's config.

        @param kwargs: A dictionary of key/values to update the nebraska's
                       config.  See platform/dev/nebraska/nebraska.py for more
                       information.

        """
        requests.post('http://127.0.0.1:%d/update_config' % self._port,
                      json=kwargs)

    def _create_nebraska_dir(self, metadata=True):
        """
        Creates /usr/local/nebraska for storing the startup conf and
        persistent metadata files.

        @param metadata: True to create a subdir for metadata.

        """
        dir_to_make = NEBRASKA_DIR
        if metadata:
            dir_to_make = NEBRASKA_METADATA_DIR
        try:
            os.makedirs(dir_to_make)
        except OSError as e:
            if errno.EEXIST != e.errno:
                raise error.TestError('Failed to create %s with error: %s',
                                      dir_to_make, e)

    def create_startup_config(self, **kwargs):
        """
        Creates a nebraska startup config file. If this file is present, nebraska
        will start before update_engine does during system startup.

        @param kwargs: A dictionary of key/values for nebraska config options.
                       See platform/dev/nebraska/nebraska.py for more info.

        """
        conf = {}
        if self._update_metadata_dir:
            conf['update_metadata'] = self._update_metadata_dir
        if self._update_payloads_address:
            conf['update_payloads_address'] = self._update_payloads_address
        if self._install_metadata_dir:
            conf['install_metadata'] = self._install_metadata_dir
        if self._install_payloads_address:
            conf['install_payloads_address'] = self._install_payloads_address

        for k, v in six.iteritems(kwargs):
            conf[k] = v

        self._create_nebraska_dir()
        with open(NEBRASKA_CONFIG, 'w') as fp:
            json.dump(conf, fp)
