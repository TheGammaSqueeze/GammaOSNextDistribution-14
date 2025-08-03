# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""
Test utilities for the Apollo kit Chromebox and IP peripherals
"""

import logging

from autotest_lib.client.bin import utils


def ectool_pse_status(port):
    result = utils.run('ectool pse {0}'.format(port)).stdout.strip()
    logging.debug("ectool pse output for port {0}: {1}".format(port, result))

    # Strip stdout to return only the status
    prefix = 'Port {0}: '.format(port)
    if not result.startswith(prefix):
        return error

    return result[len(prefix):]


def ectool_pse_enable(port):
    utils.run('ectool pse {port} enable'.format(port=port))


def ectool_pse_disable(port):
    utils.run('ectool pse {port} disable'.format(port=port))
