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
import re

from bluetooth_test import bluetooth_base_test
from mobly import asserts
from utilities.media_utils import MediaUtils
from utilities.common_utils import CommonUtils
from utilities.main_utils import common_main

TIMESTAMP_MATCHER = "^([0-5]?[0-9]):([0-5][0-9])$"


class IsMediaMetadataForNextAndPrevSongOnHuValid(bluetooth_base_test.BluetoothBaseTest):

    def setup_class(self):
        super().setup_class()
        self.media_utils = MediaUtils(self.target, self.discoverer)
        self.common_utils = CommonUtils(self.target, self.discoverer)

    def setup_test(self):
        self.common_utils.grant_local_mac_address_permission()
        self.common_utils.enable_wifi_on_phone_device()
        self.bt_utils.pair_primary_to_secondary()

    def test_is_media_metadata_valid_on_hu(self):
        """Tests is media metadata on HU valid"""
        self.media_utils.open_youtube_music_app()
        self.media_utils.open_media_app_on_hu()
        self.call_utils.wait_with_log(5)
        self.media_utils.pause_media_on_hu()

        # Current song metadata validation
        current_phone_song_title = self.media_utils.get_song_title_from_phone()
        self.media_utils.maximize_now_playing()
        current_hu_song_title = self.media_utils.get_song_title_from_hu()
        asserts.assert_true(current_phone_song_title == current_hu_song_title,
                            'Invalid song titles. '
                            'Song title on phone device and HU should be the same')
        phone_current_song_metadata = self.media_utils.get_song_metadata().split(',')
        asserts.assert_true(len(phone_current_song_metadata) > 0,
                            'Phone Media metadata should not be empty')
        actual_hu_artist_title = self.media_utils.get_artist_title_on_hu()
        actual_phone_artist_title = phone_current_song_metadata[1].strip()
        actual_hu_album_title = self.media_utils.get_album_title_on_hu()
        actual_phone_album_title = phone_current_song_metadata[2].strip()
        actual_current_song_playing_time = self.media_utils.get_current_song_playing_time_on_hu()
        actual_current_song_max_playing_time = self.media_utils.get_current_song_max_playing_time_on_hu()
        asserts.assert_true(
            actual_hu_artist_title == actual_phone_artist_title,
            'Artist title should be the same on HU and phone device.\nHU Artist title: '
            '<' + actual_hu_artist_title + '>\nPhone Artist title: '
                                           '<' + actual_phone_artist_title + '>')
        asserts.assert_true(
            actual_hu_album_title == actual_phone_album_title,
            'Album title should be the same on HU and phone device.\nHU Album title: '
            '<' + actual_hu_album_title + '>\nPhone Album title: '
                                          '<' + actual_phone_album_title + '>')
        asserts.assert_true(
            re.match(TIMESTAMP_MATCHER, actual_current_song_playing_time),
            'Invalid song playing time. Timestamp should match with RegEx: '
            '<' + TIMESTAMP_MATCHER + '>\nActual current playing timestamp on HU: '
                                      '<' + actual_current_song_playing_time + '>')
        asserts.assert_true(
            re.match(TIMESTAMP_MATCHER, actual_current_song_max_playing_time),
            'Invalid song max playing time. Timestamp should match with RegEx: '
            '<' + TIMESTAMP_MATCHER + '>\nActual current max playing timestamp on HU: '
                                      '<' + actual_current_song_max_playing_time + '>')
        # Next song metadata validation
        self.media_utils.click_next_track_on_hu()
        self.media_utils.pause_media_on_hu()
        # current_phone_next_song_title = self.media_utils.get_song_title_from_phone()
        current_hu_next_song_title = self.media_utils.get_song_title_from_hu()
        asserts.assert_true(current_phone_song_title != current_hu_next_song_title,
                            'Song title on phone device and HU should be different,'
                            ' but they are the same')

        # Previous song metadata validation
        self.media_utils.click_previous_track_on_hu()
        self.media_utils.click_previous_track_on_hu()
        self.media_utils.pause_media_on_hu()
        current_hu_previous_song_title = self.media_utils.get_song_title_from_hu()
        asserts.assert_true(current_phone_song_title == current_hu_previous_song_title,
                            'Invalid song titles. '
                            'Song title on phone device and HU should be the same')
        actual_hu_previous_artist_title = self.media_utils.get_artist_title_on_hu()
        actual_hu_previous_album_title = self.media_utils.get_album_title_on_hu()
        actual_previous_current_song_playing_time = self.media_utils.get_current_song_playing_time_on_hu()
        actual_previous_current_song_max_playing_time = self.media_utils.get_current_song_max_playing_time_on_hu()
        asserts.assert_true(
            actual_hu_previous_artist_title == actual_phone_artist_title,
            'Artist title should be the same on HU and phone device.\nHU Artist title: '
            '<' + actual_hu_previous_artist_title + '>\nPhone Artist title: '
                                                    '<' + actual_phone_artist_title + '>')
        asserts.assert_true(
            actual_hu_previous_album_title == actual_phone_album_title,
            'Album title should be the same on HU and phone device.\nHU Album title: '
            '<' + actual_hu_previous_album_title + '>\nPhone Album title: '
                                                   '<' + actual_phone_album_title + '>')
        asserts.assert_true(
            re.match(TIMESTAMP_MATCHER, actual_previous_current_song_playing_time),
            'Invalid song playing time. Timestamp should match with RegEx: '
            '<' + TIMESTAMP_MATCHER + '>\nActual current playing timestamp on HU: '
                                      '<' + actual_previous_current_song_playing_time + '>')
        asserts.assert_true(
            re.match(TIMESTAMP_MATCHER, actual_previous_current_song_max_playing_time),
            'Invalid song max playing time. Timestamp should match with RegEx: '
            '<' + TIMESTAMP_MATCHER + '>\nActual current max playing timestamp on HU: '
                                      '<' + actual_previous_current_song_max_playing_time + '>')

    def teardown_test(self):
        # Close YouTube Music app
        self.media_utils.close_youtube_music_app()
        super().teardown_test()


if __name__ == '__main__':
    common_main()
