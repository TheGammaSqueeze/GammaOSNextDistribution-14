#!/usr/bin/env python3
#
#   Copyright 2021 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

import logging

from blueberry.tests.gd.cert.closable import Closable
from blueberry.tests.gd.cert.py_le_iso import PyLeIso


class CertLeIso(Closable):

    def __init__(self, device):
        self._device = device
        self._le_iso = PyLeIso(device)

    def close(self):
        logging.info("DUT: close")
        self._le_iso.close()

    def le_set_cig_parameters(self, cig_id, sdu_interval_m_to_s, sdu_interval_s_to_m, peripherals_clock_accuracy,
                              packing, framing, max_transport_latency_m_to_s, max_transport_latency_s_to_m, cis_id,
                              max_sdu_m_to_s, max_sdu_s_to_m, phy_m_to_s, phy_s_to_m, rtn_m_to_s, rtn_s_to_m):
        return self._le_iso.le_set_cig_parameters(
            cig_id, sdu_interval_m_to_s, sdu_interval_s_to_m, peripherals_clock_accuracy, packing, framing,
            max_transport_latency_m_to_s, max_transport_latency_s_to_m, cis_id, max_sdu_m_to_s, max_sdu_s_to_m,
            phy_m_to_s, phy_s_to_m, rtn_m_to_s, rtn_s_to_m)

    def le_set_cig_parameters_test(self, cig_id, sdu_interval_m_to_s, sdu_interval_s_to_m, ft_m_to_s, ft_s_to_m,
                                   iso_interval, peripherals_clock_accuracy, packing, framing,
                                   max_transport_latency_m_to_s, max_transport_latency_s_to_m, cis_configs):
        return self._le_iso.le_set_cig_parameters_test(cig_id, sdu_interval_m_to_s, sdu_interval_s_to_m, ft_m_to_s,
                                                       ft_s_to_m, iso_interval, peripherals_clock_accuracy, packing,
                                                       framing, max_transport_latency_m_to_s,
                                                       max_transport_latency_s_to_m, cis_configs)

    def wait_le_set_cig_parameters_complete(self):
        return self._le_iso.wait_le_set_cig_parameters_complete()

    def le_cretate_cis(self, cis_and_acl_handle_array):
        self._le_iso.le_create_cis(cis_and_acl_handle_array)

    def wait_le_cis_established(self):
        return self._le_iso.wait_le_cis_established()
