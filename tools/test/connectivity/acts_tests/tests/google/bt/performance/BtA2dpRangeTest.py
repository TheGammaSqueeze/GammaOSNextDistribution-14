#!/usr/bin/env python3
#
# Copyright (C) 2019 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License. You may obtain a copy of
# the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations under
# the License.
import acts_contrib.test_utils.bt.bt_test_utils as btutils
from acts import asserts
from acts_contrib.test_utils.bt.A2dpBaseTest import A2dpBaseTest

INIT_ATTEN = 0


class BtA2dpRangeTest(A2dpBaseTest):

    def __init__(self, configs):
        super().__init__(configs)
        req_params = ['attenuation_vector', 'codecs']
        opt_params = ['gain_mismatch', 'dual_chain']
        #'attenuation_vector' is a dict containing: start, stop and step of
        #attenuation changes
        #'codecs' is a list containing all codecs required in the tests
        self.unpack_userparams(req_params)
        self.unpack_userparams(opt_params, dual_chain=None, gain_mismatch=None)

    def setup_generated_tests(self):
        for codec_config in self.codecs:
            arg_set = [(codec_config, )]
            self.generate_tests(test_logic=self.BtA2dp_test_logic,
                                name_func=self.create_test_name,
                                arg_sets=arg_set)

    def setup_class(self):
        super().setup_class()
        # Enable BQR on all android devices
        btutils.enable_bqr(self.android_devices)
        if hasattr(self, 'dual_chain') and self.dual_chain == 1:
            self.atten_c0 = self.attenuators[0]
            self.atten_c1 = self.attenuators[1]
            self.atten_c0.set_atten(INIT_ATTEN)
            self.atten_c1.set_atten(INIT_ATTEN)

    def teardown_class(self):
        super().teardown_class()
        if hasattr(self, 'atten_c0') and hasattr(self, 'atten_c1'):
            self.atten_c0.set_atten(INIT_ATTEN)
            self.atten_c1.set_atten(INIT_ATTEN)

    def BtA2dp_test_logic(self, codec_config):
        self.run_a2dp_to_max_range(codec_config)

    def create_test_name(self, arg_set):
        if hasattr(self, 'dual_chain') and self.dual_chain == 1:
            test_case_name = 'test_dual_bt_a2dp_range_codec_{}_gainmismatch_{}dB'.format(
                arg_set['codec_type'], self.gain_mismatch)
        else:
            test_case_name = 'test_bt_a2dp_range_codec_{}'.format(
                arg_set['codec_type'])
        return test_case_name
