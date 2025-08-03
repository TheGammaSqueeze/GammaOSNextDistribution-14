# Lint as: python2, python3
# Copyright 2022 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging

from autotest_lib.client.common_lib import error


def connect_to_wifi(host, ssid, password):
    """
    Performs steps needed to configure a CrOS device for Cross Device tests.

    @param host: Host to run the command on.
    @param ssid: SSID of the Wifi network to connect to
    @param password: password to connect to wifi network

    """
    host.run(
            'dbus-send --system --print-reply --dest=org.chromium.flimflam / org.chromium.flimflam.Manager.EnableTechnology string:wifi'
    )
    try:
        host.run('/usr/local/autotest/cros/scripts/wifi connect %s %s' %
                 (ssid, password))
    except error.AutoservRunError as e:
        if 'already connected' in str(e):
            logging.debug('Already connected to network. Ignoring error.')
        else:
            raise
