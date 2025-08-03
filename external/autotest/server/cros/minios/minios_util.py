# Lint as: python2, python3
# Copyright 2022 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import json
import logging
import os
import requests
import six

from autotest_lib.client.common_lib import autotemp
from autotest_lib.client.common_lib import error
from autotest_lib.client.cros.update_engine import nebraska_wrapper


class NebraskaService:
    """
    Remotely sets up nebraska on the DUT.

    This service is different from
    `autotest_lib.client.cros.update_engine.nebraska_wrapper.NebraskaWrapper` in
    that it is used by server-only tests to remotely launch nebraska on the DUT.

    """

    def __init__(self, test, host, payload_url=None, **props_to_override):
        """
        Initializes the NebraskaService.

        @param test: Instance of the test using the service.
        @param host: The DUT we will be running on.
        @param payload_url: The payload that will be returned in responses for
            update requests. This can be a single URL string or a list of URLs
            to return multiple payload URLs (such as a platform payload + DLC
            payloads) in the responses.
        @param props_to_override: Dictionary of key/values to use in responses
            instead of the default values in payload_url's properties file.

        """
        self._host = host
        self._test = test

        # _update_metadata_dir is the directory for storing the json metadata
        # files associated with the payloads.
        # _update_payloads_address is the address of the update server where
        # the payloads are staged.
        self._update_metadata_dir = None
        self._update_payloads_address = None

        if payload_url:
            # Normalize payload_url to be a list.
            if not isinstance(payload_url, list):
                payload_url = [payload_url]

            self._update_metadata_dir = self._host.get_tmp_dir()
            self._update_payloads_address = ''.join(
                    payload_url[0].rpartition('/')[0:2])

            # Download the metadata files and save them in a tempdir for general
            # use.
            for url in payload_url:
                self.get_payload_properties_file(url,
                                                 self._update_metadata_dir,
                                                 **props_to_override)

    def get_payload_properties_file(self, payload_url, target_dir, **kwargs):
        """
        Downloads the payload properties file into a directory on the DUT.

        @param payload_url: The URL to the update payload file.
        @param target_dir: The directory on the DUT to download the file into.
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
            self._write_remote_file(os.path.join(target_dir, file_name),
                                    json.dumps(response))

        except (requests.exceptions.RequestException, IOError,
                ValueError) as err:
            raise error.TestError(
                    'Failed to get update payload properties: %s with error: %s'
                    % (payload_props_url, err))

    def start(self, **kwargs):
        """Launch nebraska on DUT."""
        # Generate nebraska configuration.
        self._write_remote_file(
                nebraska_wrapper.NEBRASKA_CONFIG,
                json.dumps(self._create_startup_config(**kwargs)),
        )
        logging.info('Start nebraska service')
        self._host.upstart_restart('nebraska')
        self._host.wait_for_service('nebraska')

    def stop(self):
        """Stop Nebraska service."""
        logging.info('Stop nebraska service')
        self._host.upstart_stop('nebraska')
        self._host.run('rm', args=('-f', nebraska_wrapper.NEBRASKA_CONFIG))

    def _create_startup_config(self, **kwargs):
        """
        Creates a nebraska startup config file. If this file is present, nebraska
        can be started by upstart.

        @param kwargs: A dictionary of key/values for nebraska config options.
            See platform/dev/nebraska/nebraska.py for more info.

        @return: A dictionary of nebraska config options.

        """
        conf = {}
        if self._update_metadata_dir:
            conf['update_metadata'] = self._update_metadata_dir
        if self._update_payloads_address:
            conf['update_payloads_address'] = self._update_payloads_address

        for k, v in six.iteritems(kwargs):
            conf[k] = v
        return conf

    def _create_remote_dir(self, remote_dir, owner=None):
        """
        Create directory on DUT.

        @param remote_dir: The directory to create.
        @param owner: Set owner of the remote directory.

        """
        permission = '1777'
        if owner:
            permission = '1770'
        self._host.run(['mkdir', '-p', '-m', permission, remote_dir])
        if owner:
            self._host.run('chown', args=(owner, remote_dir))

    def _write_remote_file(self,
                           filepath,
                           content,
                           permission=None,
                           owner=None):
        """
        Write content to filepath on DUT.

        @param permission: set permission to 0xxx octal number of remote file.
        @param owner: set owner of remote file.

        """
        tmpdir = autotemp.tempdir(unique_id='minios')
        tmp_path = os.path.join(tmpdir.name, os.path.basename(filepath))
        with open(tmp_path, 'w') as f:
            f.write(content)
        if permission is not None:
            os.chmod(tmp_path, permission)
        self._create_remote_dir(os.path.dirname(filepath), owner)
        self._host.send_file(tmp_path, filepath, delete_dest=True)
        if owner is not None:
            self._host.run('chown', args=(owner, filepath))
        tmpdir.clean()
