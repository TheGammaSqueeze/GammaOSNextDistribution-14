# -*- coding: utf-8 -*-
# Copyright 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""A class that sets up the environment for telemetry testing."""

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

from autotest_lib.client.common_lib.cros import dev_server

import contextlib
import errno
import fcntl
import logging
import os
import shutil
import subprocess
import tempfile

import requests

_READ_BUFFER_SIZE_BYTES = 1024 * 1024  # 1 MB


@contextlib.contextmanager
def lock_dir(dir_name):
    """Lock a directory exclusively by placing a file lock in it.

    Args:
      dir_name: the directory name to be locked.
    """
    lock_file = os.path.join(dir_name, '.lock')
    with open(lock_file, 'w+') as f:
        fcntl.flock(f, fcntl.LOCK_EX)
        try:
            yield
        finally:
            fcntl.flock(f, fcntl.LOCK_UN)


class TelemetrySetupError(Exception):
    """Exception class used by this module."""
    pass


class TelemetrySetup(object):
    """Class that sets up the environment for telemetry testing."""

    # Relevant directory paths.
    _BASE_DIR_PATH = '/tmp/telemetry-workdir'
    _PARTIAL_DEPENDENCY_DIR_PATH = 'autotest/packages'

    # Relevant directory names.
    _TELEMETRY_SRC_DIR_NAME = 'telemetry_src'
    _TEST_SRC_DIR_NAME = 'test_src'
    _SRC_DIR_NAME = 'src'

    # Names of the telemetry dependency tarballs.
    _DEPENDENCIES = [
            'dep-telemetry_dep.tar.bz2',
            'dep-page_cycler_dep.tar.bz2',
            'dep-chrome_test.tar.bz2',
            'dep-perf_data_dep.tar.bz2',
    ]

    # Partial devserver URLs.
    _STATIC_URL_TEMPLATE = '%s/static/%s/autotest/packages/%s'

    def __init__(self, hostname, build):
        """Initializes the TelemetrySetup class.

        Args:
        hostname: The host for which telemetry environment should be setup. This
            is important for devserver resolution.
        build: The build for which telemetry environment should be setup. It is
            typically in the format <board>/<version>.
        """
        self._build = build
        self._ds = dev_server.ImageServer.resolve(self._build,
                                                  hostname=hostname)
        self._setup_dir_path = tempfile.mkdtemp(prefix='telemetry-setupdir_')
        self._tmp_build_dir = os.path.join(self._BASE_DIR_PATH, self._build)
        self._tlm_src_dir_path = os.path.join(self._tmp_build_dir,
                                              self._TELEMETRY_SRC_DIR_NAME)

    def Setup(self):
        """Sets up the environment for telemetry testing.

        This method downloads the telemetry dependency tarballs and extracts
        them into a 'src' directory.

        Returns:
        Path to the src directory where the telemetry dependencies have been
            downloaded and extracted.
        """
        src_folder = os.path.join(self._tlm_src_dir_path, self._SRC_DIR_NAME)
        test_src = os.path.join(self._tlm_src_dir_path,
                                self._TEST_SRC_DIR_NAME)
        self._MkDirP(self._tlm_src_dir_path)
        with lock_dir(self._tlm_src_dir_path):
            if not os.path.exists(src_folder):
                # Download the required dependency tarballs.
                for dep in self._DEPENDENCIES:
                    dep_path = self._DownloadFilesFromDevserver(
                            dep, self._setup_dir_path)
                    if os.path.exists(dep_path):
                        self._ExtractTarball(dep_path, self._tlm_src_dir_path)

                # By default all the tarballs extract to test_src but some parts
                # of the telemetry code specifically hardcoded to exist inside
                # of 'src'.
                try:
                    shutil.move(test_src, src_folder)
                except shutil.Error:
                    raise TelemetrySetupError(
                            'Failure in telemetry setup for build %s. Appears '
                            'that the test_src to src move failed.' %
                            self._build)
        return src_folder

    def _DownloadFilesFromDevserver(self, filename, dest_path):
        """Downloads the given tar.bz2 file from the devserver.

        Args:
          filename: Name of the tar.bz2 file to be downloaded.
          dest_path: Full path to the directory where it should be downloaded.

        Returns:
            Full path to the downloaded file.

        Raises:
          TelemetrySetupError when the download cannot be completed for any
              reason.
        """
        dep_path = os.path.join(dest_path, filename)
        url = (self._STATIC_URL_TEMPLATE %
               (self._ds.url(), self._build, filename))
        try:
            resp = requests.get(url)
            resp.raise_for_status()
            with open(dep_path, 'wb') as f:
                for content in resp.iter_content(_READ_BUFFER_SIZE_BYTES):
                    f.write(content)
        except Exception as e:
            if (isinstance(e, requests.exceptions.HTTPError)
                        and resp.status_code == 404):
                logging.error(
                        'The request %s returned a 404 Not Found status.'
                        'This dependency could be new and therefore does not '
                        'exist. Hence, squashing the exception and proceeding.',
                        url)
            elif isinstance(e, requests.exceptions.ConnectionError):
                logging.warning(
                        'The request failed because a connection to the devserver '
                        '%s could not be established. Attempting to execute the '
                        'request %s once by SSH-ing into the devserver.',
                        self._ds.url(), url)
                return self._DownloadFilesFromDevserverViaSSH(url, dep_path)
            else:
                raise TelemetrySetupError(
                        'An error occurred while trying to complete  %s: %s' %
                        (url, e))
        return dep_path

    def _DownloadFilesFromDevserverViaSSH(self, url, dep_path):
        """Downloads the file at the URL from the devserver by SSH-ing into it.

        Args:
          url: URL of the location of the tar.bz2 file on the devserver.
          dep_path: Full path to the file where it will be downloaded.

        Returns:
            Full path to the downloaded file.

        Raises:
          TelemetrySetupError when the download cannot be completed for any
              reason.
        """
        cmd = ['ssh', self._ds.hostname, 'curl', url]
        with open(dep_path, 'w') as f:
            proc = subprocess.Popen(cmd, stdout=f, stderr=subprocess.PIPE)
            _, err = proc.communicate()
            if proc.returncode != 0:
                raise TelemetrySetupError(
                        'The command: %s finished with returncode %s and '
                        'errors as following: %s. The telemetry dependency '
                        'could not be downloaded.' %
                        (' '.join(cmd), proc.returncode, err))
        return dep_path

    def _ExtractTarball(self, tarball_path, dest_path):
        """Extracts the given tarball into the destination directory.

        Args:
          tarball_path: Full path to the tarball to be extracted.
          dest_path: Full path to the directory where the tarball should be
              extracted.

        Raises:
          TelemetrySetupError if the method is unable to extract the tarball for
              any reason.
        """
        cmd = ['tar', 'xf', tarball_path, '--directory', dest_path]
        try:
            proc = subprocess.Popen(cmd,
                                    stdout=subprocess.PIPE,
                                    stderr=subprocess.PIPE)
            proc.communicate()
        except Exception as e:
            shutil.rmtree(dest_path)
            raise TelemetrySetupError(
                    'An exception occurred while trying to untar %s into %s: %s'
                    % (tarball_path, dest_path, str(e)))

    def _MkDirP(self, path):
        """Recursively creates the given directory.

        Args:
          path: Full path to the directory that needs to the created.

        Raises:
          TelemetrySetupError is the method is unable to create directories for
              any reason except OSError EEXIST which indicates that the
              directory already exists.
        """
        try:
            os.makedirs(path)
        except Exception as e:
            if not isinstance(e, OSError) or e.errno != errno.EEXIST:
                raise TelemetrySetupError(
                        'Could not create directory %s due to %s.' %
                        (path, str(e)))

    def Cleanup(self):
        """Cleans up telemetry setup and work environment."""
        try:
            shutil.rmtree(self._setup_dir_path)
        except Exception as e:
            logging.error('Something went wrong. Could not delete %s: %s',
                          self._setup_dir_path, e)
        try:
            shutil.rmtree(self._tlm_src_dir_path)
        except Exception as e:
            logging.error('Something went wrong. Could not delete %s: %s',
                          self._tlm_src_dir_path, e)
