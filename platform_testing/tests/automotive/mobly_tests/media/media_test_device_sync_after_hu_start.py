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


from bluetooth_test import bluetooth_base_test
from mobly import asserts
from utilities.media_utils import MediaUtils
from utilities.main_utils import common_main
from utilities.common_utils import CommonUtils
from mobly.controllers import android_device


class IsDeviceSyncedAfterHuStart(bluetooth_base_test.BluetoothBaseTest):

    def setup_class(self):
        super().setup_class()
        self.media_utils = MediaUtils(self.target, self.discoverer)
        self.common_utils = CommonUtils(self.target, self.discoverer)

    def setup_test(self):
        self.common_utils.grant_local_mac_address_permission()
        self.common_utils.enable_wifi_on_phone_device()
        self.bt_utils.pair_primary_to_secondary()

    def test_is_hu_synced_after_hu_start(self):
        """Tests validating is media synced after HU start"""
        # Reboot HU
        self.discoverer.unload_snippet('mbs')
        self.media_utils.reboot_hu()
        self.call_utils.wait_with_log(10)
        self.media_utils.open_youtube_music_app()
        self.call_utils.wait_with_log(30)
        self.discoverer.load_snippet('mbs', android_device.MBS_PACKAGE)
        current_phone_song_title = self.media_utils.get_song_title_from_phone()
        self.media_utils.open_media_app_on_hu()
        current_hu_song_title = self.media_utils.get_song_title_from_hu()
        asserts.assert_true(self.media_utils.is_song_playing_on_hu(),
                            'Song should be playing after HU reboot')
        asserts.assert_true(current_phone_song_title == current_hu_song_title,
                            'Invalid song titles. '
                            'Song title on phone device and HU should be the same')

    def teardown_test(self):
        # Close YouTube Music app
        self.media_utils.close_youtube_music_app()
        super().teardown_test()


if __name__ == '__main__':
    common_main()
