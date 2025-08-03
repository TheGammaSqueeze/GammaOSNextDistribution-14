# Lint as: python2, python3
# Copyright 2020 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging
import time

from autotest_lib.client.bin import test
from autotest_lib.client.bin import utils
from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib.cros import chrome
from autotest_lib.client.cros import constants as cros_constants
from autotest_lib.client.cros.audio import cras_utils
from autotest_lib.client.cros.multimedia import audio_facade

class audio_CrasGetNodes(test.test):
    """Verifies dbus GetNodes API of CRAS."""
    version = 1

    ALOOP_CRAS_NODE_TYPE = 'ALSA_LOOPBACK'
    ALOOP_MODULE_NAME = 'snd-aloop'

    def run_once(self):
        """Entry point of this test."""
        # Check CRAS server is alive. If not, restart it and wait a second to
        # get server ready.
        if utils.get_service_pid('cras') == 0:
            logging.debug('CRAS server is down. Restart it.')
            utils.start_service('cras', ignore_status=True)
            time.sleep(1)

        utils.load_module(self.ALOOP_MODULE_NAME)

        try:
            with chrome.Chrome(
                    extension_paths=[cros_constants.AUDIO_TEST_EXTENSION],
                    autotest_ext=True) as cr:
                audio_facade_local = audio_facade.AudioFacadeLocal(cr)
                audio_facade_local.set_chrome_active_node_type(
                        self.ALOOP_CRAS_NODE_TYPE, self.ALOOP_CRAS_NODE_TYPE)

            # Checks active output and input node types are correct.
            active_output_types, active_input_types = (
                cras_utils.get_selected_node_types())
            if len(active_output_types) != 1:
                raise error.TestFail(
                    'Length of active output types is not 1, got: %d',
                    len(active_output_types))
            if active_output_types[0] != self.ALOOP_CRAS_NODE_TYPE:
                raise error.TestFail(
                    'Active output device is not %s, got: %d',
                    self.ALOOP_CRAS_NODE_TYPE, active_output_types[0])
            if len(active_input_types) != 1:
                raise error.TestFail(
                    'Length of active input types is not 1, got: %d',
                    len(active_input_types))
            if active_input_types[0] != self.ALOOP_CRAS_NODE_TYPE:
                raise error.TestFail(
                    'Active input device is not %s, got: %d',
                    self.ALOOP_CRAS_NODE_TYPE, active_input_types[0])

            # Checks active node volume is correct.
            for target_volume in [25, 75]:
                cras_utils.set_selected_output_node_volume(target_volume)
                volume = cras_utils.get_active_node_volume()
                if volume != target_volume:
                    raise error.TestFail('Volume is as expected: %d, got: %d',
                                         target_volume, volume)

        finally:
            utils.stop_service('cras', ignore_status=True)
            utils.unload_module(self.ALOOP_MODULE_NAME)
            utils.start_service('cras', ignore_status=True)
