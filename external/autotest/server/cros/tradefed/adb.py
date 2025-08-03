# Lint as: python2, python3
# Copyright 2022 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# TODO(rkuroiwa): Rename this file to adb_utils.py to align with other utility
# modules. Also when class Adb is instantiated, the user is likely to call the
# instance "adb" which would collide with this file name (unless they always
# use "import adb as someothername".

import logging
import re

from autotest_lib.server import utils


class Adb:
    """Class for running adb commands."""

    def __init__(self):
        self._install_paths = set()

    def add_path(self, path):
        """Adds path for executing commands.

        Path to ADB and AAPT may have to be added it if is not in the path.
        Use this method to add it to the path before using run().
        """
        self._install_paths.add(path)

    def get_paths(self):
        return self._install_paths

    def run(self, host, *args, **kwargs):
        """Runs an ADB command on the host.

        @param host: DUT to issue the adb command.
        @param args: Extra args passed to the adb command.
        @param kwargs: Extra arguments passed to utils.run().
        """
        additional_option = _tradefed_options(host)
        kwargs['args'] = additional_option + kwargs.get('args', ())

        # _install_paths should include the directory with adb.
        # utils.run() will append these to paths.
        kwargs['extra_paths'] = (kwargs.get('extra_paths', []) +
                                 list(self._install_paths))
        result = utils.run('adb', **kwargs)
        logging.info('adb %s:\n%s', ' '.join(kwargs.get('args')),
                     result.stdout + result.stderr)
        return result


def get_adb_target(host):
    """Get the adb target format.

    This method is slightly different from host.host_port as we need to
    explicitly specify the port so the serial name of adb target would
    match.

    @param host: a DUT accessible via adb.
    @return a string for specifying the host using adb command.
    """
    port = 22 if host.port is None else host.port
    if re.search(r':.*:', host.hostname):
        # Add [] for raw IPv6 addresses, stripped for ssh.
        # In the Python >= 3.3 future, 'import ipaddress' will parse
        # addresses.
        return '[{}]:{}'.format(host.hostname, port)
    return '{}:{}'.format(host.hostname, port)


def get_adb_targets(hosts):
    """Get a list of adb targets."""
    return [get_adb_target(host) for host in hosts]


def _tradefed_options(host):
    """ADB arguments for tradefed.

    These arguments are specific to using adb with tradefed.

    @param host: DUT that want to connect to. (None if the adb command is
                 intended to run in the server. eg. keygen)
    @return a tuple of arguments for adb command.
    """
    if host:
        host_port = get_adb_target(host)
        ret = ('-s', host_port)
        return ret
    # As of N, tradefed could not specify which adb socket to use, which use
    # tcp:localhost:5037 by default.
    return ('-H', 'localhost', '-P', '5037')
