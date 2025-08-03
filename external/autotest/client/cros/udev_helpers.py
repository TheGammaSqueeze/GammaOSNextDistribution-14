# Lint as: python2, python3
# Copyright (c) 2013 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
# Lint as: python3
""" Udevadm helper classes and functions.
"""

import subprocess

class UdevadmInfo():
    """ Use udevadm info on a specific path.
    """

    @classmethod
    def GetProperties(cls, syspath):
        """ Get all properties of given syspath as a dict.

        Args:
            syspath: System path to get properties for.

        Returns:
            Dict with attribute/property as key and it's value. All keys are
            converted to lowercase. Example: {'subsystem': 'input'}
        """
        props = {}
        rawprops = subprocess.check_output(' '.join(
                ['udevadm', 'info', '-q', 'property', '-p', syspath]),
                                           shell=True)

        for line in rawprops.splitlines():
            upper_key, value = line.split(b'=', 1)
            props[upper_key.lower()] = value.strip(b'"')

        return props

class UdevadmTrigger():
    """ Use udevadm trigger with specific rules.
    """

    def __init__(self,
                 verbose=True,
                 event_type=None,
                 attr_match=[],
                 attr_nomatch=[],
                 subsystem_match=[],
                 subsystem_nomatch=[]):
        """ Constructor

        Args:
            verbose: Whether to output triggered syspaths
            event_type: What type of events to trigger (device or subsystem)
            attr_match: What attributes to match
            attr_nomatch: What attributes not to match
            subsystem_match: What subsystems to match
            subsystem_nomatch: What subsystems not to match
        """
        cmd = ['udevadm', 'trigger']

        if verbose:
            cmd.append('-v')

        if event_type:
            cmd.append('-t')
            cmd.append('"{}"'.format(event_type))

        for attr in attr_match:
            cmd.append('-a')
            cmd.append('"{}"'.format(attr))

        for attr in attr_nomatch:
            cmd.append('-A')
            cmd.append('"{}"'.format(attr))

        for subsystem in subsystem_match:
            cmd.append('-s')
            cmd.append('"{}"'.format(subsystem))

        for subsystem in subsystem_nomatch:
            cmd.append('-S')
            cmd.append('"{}"'.format(subsystem))

        self.cmd = cmd

    def DryRun(self):
        """ Do a dry run using initialized trigger rules.

        Returns:
            List of syspaths that would be triggered.
        """
        cmd = self.cmd + ['-n']
        lines = subprocess.check_output(' '.join(cmd), shell=True)
        return lines.splitlines() if lines else []
