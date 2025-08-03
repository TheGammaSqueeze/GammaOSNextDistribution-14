#  Copyright (C) 2023 The Android Open Source Project
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

import logging
import time

from bluetooth_test import bluetooth_base_test
from utilities.main_utils import common_main
from utilities.crystalball_metrics_utils import export_to_crystalball

ITERATIONS_PARAM_NAME = 'iterations'
DEFAULT_ITERATIONS = 10
DEFAULT_ITERATION_DELAY_S = 2

class BTPerformancePairingTest(bluetooth_base_test.BluetoothBaseTest):
    """Test Class for Bluetooth Pairing Test."""

    def setup_class(self):
        super().setup_class()
        self.iterations = DEFAULT_ITERATIONS
        self.iteration_delay = DEFAULT_ITERATION_DELAY_S
        if ITERATIONS_PARAM_NAME in self.user_params:
            self.iterations = self.user_params[ITERATIONS_PARAM_NAME]
        else:
            logging.info(f'{ITERATIONS_PARAM_NAME} is not in testbed config. Using default value')
        logging.info(f'Setup {self.__class__.__name__} with {ITERATIONS_PARAM_NAME} = {self.iterations} and iteration delay = {self.iteration_delay}')

    def test_pairing(self):
        """Test for pairing/unpairing a HU with a bluetooth device"""
        pairing_success_count = 0
        for i in range(1, self.iterations + 1):
            logging.info(f'Pairing iteration {i}')
            try:
                self.bt_utils.pair_primary_to_secondary()
                pairing_success_count += 1
            except:
                logging.error(f'Failed to pair devices on iteration {i}')
            self.bt_utils.unpair()
            time.sleep(self.iteration_delay)
        success_rate = pairing_success_count / self.iterations
        metrics = {'success_rate': success_rate}
        export_to_crystalball(metrics, self.log_path, self.current_test_info.name)

if __name__ == '__main__':
    common_main()
