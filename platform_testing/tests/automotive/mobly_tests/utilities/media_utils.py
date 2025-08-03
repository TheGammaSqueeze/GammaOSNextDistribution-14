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
import re
import time
from utilities import constants
from utilities.common_utils import CommonUtils


class MediaUtils:
    """A utility that provides Media controls for handheld device and HU."""

    def __init__(self, target, discoverer):
        self.target = target
        self.discoverer = discoverer
        self.common_utils = CommonUtils(self.target, self.discoverer)

    # Execute any shell command on phone device
    def execute_shell_on_device(self, shell_command):
        self.target.log.info(
            'Executing shell command: <%s> on phone device <%s>',
            shell_command,
            self.target.serial,
        )
        return self.target.adb.shell(shell_command)

    # Execute any shell command on HU
    def execute_shell_on_hu_device(self, shell_command):
        self.target.log.info(
            'Executing shell command: <%s> on HU device <%s>',
            shell_command,
            self.discoverer.serial,
        )
        return self.discoverer.adb.shell(shell_command)

    # Start YouTube Music app on phone device
    def open_youtube_music_app(self):
        logging.info("Open YouTube Music on phone device")
        self.execute_shell_on_device(
            constants.DEFAULT_YOUTUBE_MUSIC_PLAYLIST
        )

    # Stop YouTube Music app on phone device
    def close_youtube_music_app(self):
        logging.info("Close YouTube Music on phone device")
        self.execute_shell_on_device(
            constants.STOP_YOUTUBE_MEDIA_SHELL
        )

    # Press TAB button on phone device
    def press_tab_on_device(self):
        logging.info("Press <TAB> on phone device")
        self.execute_shell_on_device(constants.KEYCODE_TAB)

    # Press ENTER button on phone device
    def press_enter_on_device(self):
        logging.info("Press <ENTER> on phone device")
        self.execute_shell_on_device(constants.KEYCODE_ENTER)

    # Press NEXT song on phone device
    def press_next_song_button(self):
        logging.info("Press <NEXT SONG> button on phone device")
        self.execute_shell_on_device(constants.KEYCODE_MEDIA_NEXT)

    # Press PREVIOUS song on phone device
    def press_previous_song_button(self):
        logging.info("Press <PREVIOUS SONG> button on phone device")
        self.execute_shell_on_device(constants.KEYCODE_MEDIA_PREVIOUS)

    # Press PAUSE song on phone device
    def press_pause_song_button(self):
        logging.info("Press <PAUSE> button on phone device")
        self.execute_shell_on_device(constants.KEYCODE_MEDIA_PAUSE)

    # Press PLAY song on phone device
    def press_play_song_button(self):
        logging.info("Press <PLAY> button on phone device")
        self.execute_shell_on_device(constants.KEYCODE_MEDIA_PLAY)

    # Press STOP playing song on phone device
    def press_stop_playing_song_button(self):
        logging.info("Press <STOP> playing song button on phone device")
        self.execute_shell_on_device(constants.KEYCODE_MEDIA_STOP)

    # Get song metadata from phone device
    def get_song_metadata(self):
        logging.info("Getting song metadata from phone device")
        # get dumpsys
        dumpsys_metadata = self.execute_shell_on_device(constants.GET_DUMPSYS_METADATA).decode(
            'utf8')
        # compile regex
        regex_pattern = re.compile(constants.SONG_METADATA_PATTERN)
        # match regex
        actual_song_metadata = regex_pattern.findall(dumpsys_metadata)[0]
        logging.info("Actual song metadata on phone device: %s", actual_song_metadata)
        parsed_song_metadata = actual_song_metadata.split('=')[1]
        logging.info("Parsed song metadata on phone device: %s", parsed_song_metadata)
        return parsed_song_metadata

    # Get song title from phone device
    def get_song_title_from_phone(self):
        logging.info("Getting song title from phone device")
        time.sleep(constants.WAIT_FOR_LOAD)
        song_metadata_array = self.get_song_metadata().split(',')
        actual_song_title = song_metadata_array[0]
        logging.info("Actual song title on phone device: %s", actual_song_title)
        return actual_song_title

    # Get song title from HU
    def get_song_title_from_hu(self):
        logging.info("Getting song title from HU")
        actual_song_title = self.discoverer.mbs.getMediaTrackName()
        logging.info("Actual song title on HU: %s", actual_song_title)
        return actual_song_title

    # Click on NEXT track on HU
    def click_next_track_on_hu(self):
        logging.info("Click on NEXT track on HU")
        self.discoverer.mbs.clickNextTrack()
        time.sleep(constants.WAIT_FOR_LOAD)

    # Click on PREVIOUS track on HU
    def click_previous_track_on_hu(self):
        logging.info("Click on PREVIOUS track on HU")
        self.discoverer.mbs.clickPreviousTrack()
        time.sleep(constants.WAIT_FOR_LOAD)

    # Open Media app on HU
    def open_media_app_on_hu(self):
        logging.info("Open Media app on HU")
        self.discoverer.mbs.openMediaApp()

    # Pause Media app on HU
    def pause_media_on_hu(self):
        logging.info("Press PAUSE button on HU")
        self.discoverer.mbs.pauseMedia()

    # Play Media app on HU
    def play_media_on_hu(self):
        logging.info("Press PLAY button on HU")
        self.discoverer.mbs.playMedia()

    # Click on CANCEL button on Connect BT Audio page on HU
    def click_on_cancel_bt_audio_connection_button_on_hu(self):
        logging.info("Click on Cancel Bluetooth Audio connection button on HU")
        self.discoverer.mbs.cancelBluetoothAudioConncetion()

    # Reboot HU
    def reboot_hu(self):
        logging.info("Starting HU reboot...")
        self.execute_shell_on_hu_device(constants.REBOOT)

    # Is song playing on HU
    def is_song_playing_on_hu(self):
        logging.info("Checking is song playing on HU")
        actual_song_playing_status = self.discoverer.mbs.isPlaying()
        logging.info("Is song playing: %s", actual_song_playing_status)
        return actual_song_playing_status

    # Maximize playing song
    def maximize_now_playing(self):
        logging.info("Maximizing playing song on HU")
        self.discoverer.mbs.maximizeNowPlaying()

    # Minimize playing song
    def minimize_now_playing(self):
        logging.info("Maximizing playing song on HU")
        self.discoverer.mbs.minimizeNowPlaying()

    # Open playlist
    def open_media_playlist(self):
        logging.info("Open Playlist content on HU")
        self.discoverer.mbs.openMediaAppMenuItems()

    # Scroll playlist to the button
    def scroll_playlist_to_the_button(self):
        logging.info("Scroll Playlist to the button on HU")
        self.discoverer.mbs.scrollPlayListDown()

    # Select first visible song from playlist
    def select_song_from_playlist(self):
        logging.info("Select song from playlist on HU")
        self.discoverer.mbs.clickOnSongFromPlaylist()

    # Get playing Album title
    def get_album_title_on_hu(self):
        logging.info("Getting Album title on HU")
        actual_album_title = self.discoverer.mbs.getAlbumTitle()
        logging.info("Actual Album title on HU: <%s>", actual_album_title)
        return actual_album_title

    # Get Artist title on HU
    def get_artist_title_on_hu(self):
        logging.info("Getting Artist title on HU")
        actual_artist_title = self.discoverer.mbs.getArtistrTitle()
        logging.info("Actual Artist title on HU: <%s>", actual_artist_title)
        return actual_artist_title

    # Get current song playing time on HU
    def get_current_song_playing_time_on_hu(self):
        logging.info("Getting current song playing time on HU")
        actual_current_song_playing_time = self.discoverer.mbs.getSongCurrentPlayingTime()
        logging.info("Actual current song playing time on HU: <%s>",
                     actual_current_song_playing_time)
        return actual_current_song_playing_time

    def get_current_song_max_playing_time_on_hu(self):
        logging.info("Getting current song max playing time on HU")
        actual_current_song_max_playing_time = self.discoverer.mbs.getCurrentSongMaxPlayingTime()
        logging.info("Actual current song max playing time on HU: <%s>",
                     actual_current_song_max_playing_time)
        return actual_current_song_max_playing_time

    # Check is NOW PLAYING label displayed
    def is_now_playing_label_displayed(self):
        logging.info("Checking is <Now Playing> label displayed on HU")
        actual_is_now_playing_label_display_status = self.discoverer.mbs.isNowPlayingLabelVisible()
        logging.info("<Now Playing> label displayed: <%s>",
                     actual_is_now_playing_label_display_status)
        return actual_is_now_playing_label_display_status

    # Check is PLAYLIST icon visible
    def is_playlist_icon_visible(self):
        logging.info("Checking is Playlist icon displayed on HU")
        actual_is_playlist_icon_displayed = self.discoverer.mbs.isPlaylistIconVisible()
        logging.info("Playlist icon displayed: <%s>",
                     actual_is_playlist_icon_displayed)
        return actual_is_playlist_icon_displayed

    # Click on Playlist icon
    def click_on_playlist_icon(self):
        logging.info("Click on Playlist icon on HU")
        self.discoverer.mbs.clickOnPlaylistIcon()

    # Open Youtube Music app on HU
    def open_youtube_music_app_on_hu(self):
        logging.info("Open <%s> app on HU", constants.YOUTUBE_MUSIC_APP)
        self.common_utils.click_on_ui_element_with_text(constants.YOUTUBE_MUSIC_APP)

    # Open Bluetooth Audio app on HU
    def open_bluetooth_audio_app_on_hu(self):
        logging.info("Open <%s> app on HU", constants.BLUETOOTH_AUDIO_APP)
        self.common_utils.click_on_ui_element_with_text(constants.BLUETOOTH_AUDIO_APP)

    # Open Media Apps menu
    def open_media_apps_menu(self):
        logging.info("Open Media apps menu items on HU")
        self.discoverer.mbs.openMediaAppMenuItems()

    # Open Bluetooth player
    def open_bluetooth_player(self):
        logging.info("Open <%s> on HU", constants.BLUETOOTH_PLAYER)
        self.common_utils.click_on_ui_element_with_text(constants.BLUETOOTH_PLAYER)

    # Open Radio app
    def open_radio_app(self):
        logging.info("Open <%s> app on HU", constants.RADIO_APP)
        self.common_utils.click_on_ui_element_with_text(constants.RADIO_APP)

    # Tune FM Radio on HU
    def tune_fm_radio_on_hu(self, fm_frequency):
        if re.match(constants.FM_FREQUENCY_PATTERN, fm_frequency):
            logging.info("Tune FM Radio on HU for frequency <%s>", fm_frequency)
            logging.info("Select <%s> range for radio", constants.RADIO_FM_RANGE)
            self.common_utils.click_on_ui_element_with_text(constants.RADIO_FM_RANGE)
            for x in fm_frequency:
                self.common_utils.click_on_ui_element_with_text(x)
                time.sleep(constants.WAIT_ONE_SEC)
            logging.info("Confirm selected %s frequency on HU", constants.RADIO_FM_RANGE)
            self.common_utils.click_on_ui_element_with_text(constants.CONFIRM_RADIO_FREQUENCY)
        else:
            logging.error("Invalid FM Radio frequency. Expected pattern: <%s>",
                          constants.FM_FREQUENCY_PATTERN)
