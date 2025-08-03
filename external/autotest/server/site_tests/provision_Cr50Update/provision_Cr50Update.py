# Copyright 2017 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.


""" The autotest performing Cr50 update."""


import logging

from autotest_lib.server.cros.faft.cr50_test import Cr50Test


class provision_Cr50Update(Cr50Test):
    """A test that can provision a machine to the correct cr50 version and
    board id.
    """
    version = 1

    def initialize(self, host, cmdline_args, full_args):
        """Initialize get the cr50 update version information"""
        full_args['is_release_qual'] = 'true'
        super(provision_Cr50Update, self).initialize(host, cmdline_args,
                                                     full_args)

    def run_once(self):
        """The method called by the control file to start the update."""
        logging.info('Used initialize logic to update to the cr50 release '
                     'image')
