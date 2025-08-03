#!/usr/bin/env python3
#
#   Copyright 2020 - The Android Open Source Project
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
from acts.base_test import BaseTestClass


class SampleTest(BaseTestClass):
    def setup_class(self):
        self.log.info('Called when the test class is started.')

    def teardown_class(self):
        self.log.info('Called when the test class has finished.')

    def setup_test(self):
        self.log.info('Called before each test case starts.')

    def teardown_test(self):
        self.log.info('Called after each test case completes.')

    """Tests"""

    def test_make_toast(self):
        for ad in self.android_devices:
            ad.droid.makeToast("Hello World.")
        return True
