# Copyright 2018 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import subprocess

# Full path of the CUPS configuration file
_CUPS_CONF_FILE = '/etc/cups/cupsd.conf'

class CommandFailedException(Exception):
    """
    An simple exception that is thrown when OS command fails.

    """
    pass

class Configurator():
    """
    An instance of this class is responsible for an initial configuration of
    the system. This is performed by the method configure(). To restore the
    system to the state before a configure() call, the method  restore() must
    be called.

    """

    def __init__(self):
        """
        Constructor.

        """
        self._cupsd_conf_loglevel_line_no = None
        self._cupsd_conf_loglevel_content = None


    def _run_as_root(self, argv):
        """
        Run given command as root.

        @param argv: an array of command-line parameters

        @returns standard output produced by the command

        @raises Exception if the command returns code different than 0

        """
        p1 = subprocess.Popen(["echo", "test0000"], stdout=subprocess.PIPE)
        p2 = subprocess.Popen(["sudo", "--stdin", "--prompt="] + argv,
                stdin=p1.stdout, stdout=subprocess.PIPE)
        p1.stdout.close()
        out,err = p2.communicate()
        if p2.returncode != 0:
            raise CommandFailedException("The command '%s' returns %d" %
                    (' '.join(argv),p2.returncode));
        return out


    def _set_cups_logging_level(self):
        """
        Modify the CUPS configuration file to set log level to 'debug'.

        @raises Exception in case of any errors

        """
        # parse content of the CUPS configuration file and find a number of
        # a line with 'LogLevel' option
        lines = self._run_as_root(["cat", _CUPS_CONF_FILE]).splitlines()

        line_no = None
        for index, line in enumerate(lines):
            if line.startswith(b'LogLevel'):
                line_no = index
                break
        if line_no is None:
            raise Exception('Cannot find a line with LogLevel in cupsd.conf')
        # save the original line and replace it with 'LogLevel debug'
        self._cupsd_conf_loglevel_content = lines[line_no]
        self._cupsd_conf_loglevel_line_no = line_no + 1
        self._run_as_root(['sed', '-i', '%ds/.*/LogLevel debug/' % (line_no+1),
                _CUPS_CONF_FILE])
        # if CUPS is started, we have to stop
        try:
            self._run_as_root(['stop', 'cupsd'])
        except CommandFailedException:
            pass


    def _restore_cups_logging_level(self):
        """
        Restore content of the CUPS configuration file to this one before
        calling _set_cups_logging_level(). Do nothing if the method
        _set_cups_logging_level() was not called earlier.

        """
        if self._cupsd_conf_loglevel_content is None:
            return
        self._run_as_root(['sed', '-i', '%ds/.*/%s/' %
                (self._cupsd_conf_loglevel_line_no,
                self._cupsd_conf_loglevel_content), _CUPS_CONF_FILE])
        self._cupsd_conf_loglevel_content = None
        self._cupsd_conf_loglevel_line_no = None


    def _set_root_partition_as_read_write(self):
        """
        Remount the root partition in read-write mode.

        """
        self._run_as_root(['mount', '-o', 'rw,remount', '/'])


    def configure(self, set_cups_logging_level):
        """
        Apply the configuration required by the test.

        @param set_cups_logging_level: True or False; if True then
                the root partition is remounted in R/W mode and the CUPS
                configuration file is updated to set "LogLevel" to "debug".
        """
        # Update CUPS logging level
        if set_cups_logging_level:
            self._set_root_partition_as_read_write()
            self._set_cups_logging_level()


    def restore(self):
        """
        Restore the system state before configure(). It is safe to run
        this method, even if configure() failed or has not been called.

        """
        # Restore CUPS logging level
        if self._cupsd_conf_loglevel_content is not None:
            self._restore_cups_logging_level()
