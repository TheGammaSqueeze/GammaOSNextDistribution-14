#!/usr/bin/env python3
#
#   Copyright 2020 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the 'License');
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an 'AS IS' BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

from acts_contrib.test_utils.gnss import LabTtffTestBase as lttb


class LabTtffTest(lttb.LabTtffTestBase):
    """ LAB Stand Alone TTFF Tests"""

    def test_gnss_cold_ttff_ffpe(self):
        """
        Cold start TTFF and FFPE Testing
        """
        mode = 'cs'
        self.gnss_ttff_ffpe(mode)

    def test_gnss_warm_ttff_ffpe(self):
        """
        Warm start TTFF and FFPE Testing
        """
        mode = 'ws'
        self.gnss_ttff_ffpe(mode)

    def test_gnss_hot_ttff_ffpe(self):
        """
        Hot start TTFF and FFPE Testing
        """
        mode = 'hs'
        self.gnss_ttff_ffpe(mode)
