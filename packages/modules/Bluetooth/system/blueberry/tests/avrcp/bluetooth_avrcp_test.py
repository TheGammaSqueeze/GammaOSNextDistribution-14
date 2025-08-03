# Lint as: python3
"""Tests for AVRCP basic functionality."""

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import time

from mobly import test_runner
from mobly import signals
from mobly.controllers.android_device_lib import adb
from blueberry.controllers import android_bt_target_device
from blueberry.utils import blueberry_base_test
from blueberry.utils import bt_constants

# The audio source path of BluetoothMediaPlayback in the SL4A app.
ANDROID_MEDIA_PATH = '/sdcard/Music/test'

# Timeout for track change and playback state update in second.
MEDIA_UPDATE_TIMEOUT_SEC = 3


class BluetoothAvrcpTest(blueberry_base_test.BlueberryBaseTest):
  """Test Class for Bluetooth AVRCP.

  This test requires two or more audio files exist in path "/sdcard/Music/test"
  on the primary device, and device controllers need to have the following APIs:
    1. play()
    2. pause()
    3. track_previous()
    4. track_next()
  """

  def __init__(self, configs):
    super().__init__(configs)
    self.derived_bt_device = None
    self.pri_device = None
    self.is_android_bt_target_device = None
    self.tracks = None

  def setup_class(self):
    """Standard Mobly setup class."""
    super(BluetoothAvrcpTest, self).setup_class()

    for device in self.android_devices:
      device.init_setup()
      device.sl4a_setup()

    # The device which role is AVRCP Target (TG).
    self.pri_device = self.android_devices[0]

    if len(self.android_devices) > 1 and not self.derived_bt_devices:
      self.derived_bt_device = self.android_devices[1]
      self.derived_bt_devices.append(self.derived_bt_device)
    else:
      self.derived_bt_device = self.derived_bt_devices[0]

    # Check if the derived bt device is android bt target device.
    self.is_android_bt_target_device = isinstance(
        self.derived_bt_device, android_bt_target_device.AndroidBtTargetDevice)

    # Check if the audio files exist on the primary device.
    try:
      self.audio_files = self.pri_device.adb.shell(
          'ls %s' % ANDROID_MEDIA_PATH).decode().split('\n')[:-1]
      if len(self.audio_files) < 2:
        raise signals.TestError(
            'Please push two or more audio files to %s on the primary device '
            '"%s".' % (ANDROID_MEDIA_PATH, self.pri_device.serial))
    except adb.AdbError as error:
      if 'No such file or directory' in str(error):
        raise signals.TestError(
            'No directory "%s" found on the primary device "%s".' %
            (ANDROID_MEDIA_PATH, self.pri_device.serial))
      raise error

    self.mac_address = self.derived_bt_device.get_bluetooth_mac_address()
    self.derived_bt_device.activate_pairing_mode()
    self.pri_device.set_target(self.derived_bt_device)
    self.pri_device.pair_and_connect_bluetooth(self.mac_address)
    self.pri_device.allow_extra_permissions()
    # Gives more time for the pairing between two devices.
    time.sleep(3)

    if self.is_android_bt_target_device:
      self.derived_bt_device.add_sec_ad_device(self.pri_device)

    # Starts BluetoothSL4AAudioSrcMBS on the phone.
    if self.is_android_bt_target_device:
      self.derived_bt_device.init_ambs_for_avrcp()
    else:
      self.pri_device.sl4a.bluetoothMediaPhoneSL4AMBSStart()
      # Waits for BluetoothSL4AAudioSrcMBS to be active.
      time.sleep(1)
    # Changes the playback state to Playing in order to other Media passthrough
    # commands can work.
    self.pri_device.sl4a.bluetoothMediaHandleMediaCommandOnPhone(
        bt_constants.CMD_MEDIA_PLAY)

    # Collects media metadata of all tracks.
    self.tracks = []
    for _ in range(len(self.audio_files)):
      self.tracks.append(self.pri_device.get_current_track_info())
      self.pri_device.sl4a.bluetoothMediaHandleMediaCommandOnPhone(
          bt_constants.CMD_MEDIA_SKIP_NEXT)
    self.pri_device.log.info('Tracks: %s' % self.tracks)

    # Sets Playback state to Paused as default.
    self.pri_device.sl4a.bluetoothMediaHandleMediaCommandOnPhone(
        bt_constants.CMD_MEDIA_PAUSE)

  def teardown_class(self):
    """Teardown class for bluetooth avrcp media play test."""
    super(BluetoothAvrcpTest, self).teardown_class()
    # Stops BluetoothSL4AAudioSrcMBS after all test methods finish.
    if self.is_android_bt_target_device:
      self.derived_bt_device.stop_ambs_for_avrcp()
    else:
      self.pri_device.sl4a.bluetoothMediaPhoneSL4AMBSStop()

  def teardown_test(self):
    """Teardown test for bluetooth avrcp media play test."""
    super(BluetoothAvrcpTest, self).teardown_test()
    # Adds 1 second waiting time to fix the NullPointerException when executing
    # the following sl4a.bluetoothMediaHandleMediaCommandOnPhone method.
    time.sleep(1)
    # Sets Playback state to Paused after a test method finishes.
    self.pri_device.sl4a.bluetoothMediaHandleMediaCommandOnPhone(
        bt_constants.CMD_MEDIA_PAUSE)
    # Buffer between tests.
    time.sleep(1)

  def wait_for_media_info_sync(self):
    """Waits for sync Media information between two sides.

    Waits for sync the current playback state and Now playing track info from
    the android bt target device to the phone.
    """
    # Check if Playback state is sync.
    expected_state = self.pri_device.get_current_playback_state()
    self.derived_bt_device.verify_playback_state_changed(
        expected_state=expected_state,
        exception=signals.TestError(
            'Playback state is not equivalent between two sides. '
            '"%s" != "%s"' %
            (self.derived_bt_device.get_current_playback_state(),
             expected_state)))

    # Check if Now Playing track is sync.
    expected_track = self.pri_device.get_current_track_info()
    self.derived_bt_device.verify_current_track_changed(
        expected_track=expected_track,
        exception=signals.TestError(
            'Now Playing track is not equivalent between two sides. '
            '"%s" != "%s"' %
            (self.derived_bt_device.get_current_track_info(), expected_track)))

  def execute_media_play_pause_test_logic(self, command_sender, test_command):
    """Executes the test logic of the media command "play" or "pause".

    Steps:
      1. Correct the playback state if needed.
      2. Send a media passthrough command.
      3. Verify that the playback state is changed from AVRCP TG and CT.

    Args:
      command_sender: a device controller sending the command.
      test_command: string, the media passthrough command for testing, either
          "play" or "pause".

    Raises:
      signals.TestError: raised if the test command is invalid.
    """
    # Checks if the test command is valid.
    if test_command not in [bt_constants.CMD_MEDIA_PLAY,
                            bt_constants.CMD_MEDIA_PAUSE]:
      raise signals.TestError(
          'Command "%s" is invalid. The test command should be "%s" or "%s".' %
          (test_command, bt_constants.CMD_MEDIA_PLAY,
           bt_constants.CMD_MEDIA_PAUSE))

    # Make sure the playback state is playing if testing the command "pause".
    if (self.pri_device.get_current_playback_state() !=
        bt_constants.STATE_PLAYING and
        test_command == bt_constants.CMD_MEDIA_PAUSE):
      self.pri_device.sl4a.bluetoothMediaHandleMediaCommandOnPhone(
          bt_constants.CMD_MEDIA_PLAY)

    # Makes sure Media info is the same between two sides.
    if self.is_android_bt_target_device:
      self.wait_for_media_info_sync()
    self.pri_device.log.info(
        'Current playback state: %s' %
        self.pri_device.get_current_playback_state())

    expected_state = None
    if test_command == bt_constants.CMD_MEDIA_PLAY:
      command_sender.play()
      expected_state = bt_constants.STATE_PLAYING
    elif test_command == bt_constants.CMD_MEDIA_PAUSE:
      command_sender.pause()
      expected_state = bt_constants.STATE_PAUSED

    # Verify that the playback state is changed.
    self.pri_device.log.info('Expected playback state: %s' % expected_state)
    device_check_list = [self.pri_device]
    # Check the playback state from the android bt target device.
    if self.is_android_bt_target_device:
      device_check_list.append(self.derived_bt_device)
    for device in device_check_list:
      device.verify_playback_state_changed(
          expected_state=expected_state,
          exception=signals.TestFailure(
              'Playback state is not changed to "%s" from the device "%s". '
              'Current state: %s' %
              (expected_state, device.serial,
               device.get_current_playback_state())))

  def execute_skip_next_prev_test_logic(self, command_sender, test_command):
    """Executes the test logic of the media command "skipNext" or "skipPrev".

    Steps:
      1. Correct the Now Playing track if needed.
      2. Send a media passthrough command.
      3. Verify that the Now Playing track is changed from AVRCP TG and CT.

    Args:
      command_sender: a device controller sending the command.
      test_command: string, the media passthrough command for testing, either
          "skipNext" or "skipPrev".

    Raises:
      signals.TestError: raised if the test command is invalid.
    """
    # Checks if the test command is valid.
    if test_command not in [bt_constants.CMD_MEDIA_SKIP_NEXT,
                            bt_constants.CMD_MEDIA_SKIP_PREV]:
      raise signals.TestError(
          'Command "%s" is invalid. The test command should be "%s" or "%s".' %
          (test_command, bt_constants.CMD_MEDIA_SKIP_NEXT,
           bt_constants.CMD_MEDIA_SKIP_PREV))

    # Make sure the track index is not 0 if testing the command "skipPrev".
    if (self.tracks.index(self.pri_device.get_current_track_info()) == 0
        and test_command == bt_constants.CMD_MEDIA_SKIP_PREV):
      self.pri_device.sl4a.bluetoothMediaHandleMediaCommandOnPhone(
          bt_constants.CMD_MEDIA_SKIP_NEXT)

    # Makes sure Media info is the same between two sides.
    if self.is_android_bt_target_device:
      self.wait_for_media_info_sync()
    current_track = self.pri_device.get_current_track_info()
    current_index = self.tracks.index(current_track)
    self.pri_device.log.info('Current track: %s' % current_track)

    expected_track = None
    if test_command == bt_constants.CMD_MEDIA_SKIP_NEXT:
      command_sender.track_next()
      # It will return to the first track by skipNext if now playing is the last
      # track.
      if current_index + 1 == len(self.tracks):
        expected_track = self.tracks[0]
      else:
        expected_track = self.tracks[current_index + 1]
    elif test_command == bt_constants.CMD_MEDIA_SKIP_PREV:
      command_sender.track_previous()
      expected_track = self.tracks[current_index - 1]

    # Verify that the now playing track is changed.
    self.pri_device.log.info('Expected track: %s' % expected_track)
    device_check_list = [self.pri_device]
    # Check the playback state from the android bt target device.
    if self.is_android_bt_target_device:
      device_check_list.append(self.derived_bt_device)
    for device in device_check_list:
      device.verify_current_track_changed(
          expected_track=expected_track,
          exception=signals.TestFailure(
              'Now Playing track is not changed to "%s" from the device "%s". '
              'Current track: %s' %
              (expected_track, device.serial, device.get_current_track_info())))

  def test_media_pause(self):
    """Tests the media pause from AVRCP Controller."""
    self.execute_media_play_pause_test_logic(
        command_sender=self.derived_bt_device,
        test_command=bt_constants.CMD_MEDIA_PAUSE)

  def test_media_play(self):
    """Tests the media play from AVRCP Controller."""
    self.execute_media_play_pause_test_logic(
        command_sender=self.derived_bt_device,
        test_command=bt_constants.CMD_MEDIA_PLAY)

  def test_media_skip_prev(self):
    """Tests the media skip prev from AVRCP Controller."""
    self.execute_skip_next_prev_test_logic(
        command_sender=self.derived_bt_device,
        test_command=bt_constants.CMD_MEDIA_SKIP_PREV)

  def test_media_skip_next(self):
    """Tests the media skip next from AVRCP Controller."""
    self.execute_skip_next_prev_test_logic(
        command_sender=self.derived_bt_device,
        test_command=bt_constants.CMD_MEDIA_SKIP_NEXT)

  def test_media_pause_from_phone(self):
    """Tests the media pause from AVRCP Target.

    Tests that Playback state of AVRCP Controller will be changed to paused when
    AVRCP Target sends the command "pause".
    """
    if not self.is_android_bt_target_device:
      signals.TestError('The test requires an android bt target device.')

    self.execute_media_play_pause_test_logic(
        command_sender=self.pri_device,
        test_command=bt_constants.CMD_MEDIA_PAUSE)

  def test_media_play_from_phone(self):
    """Tests the media play from AVRCP Target.

    Tests that Playback state of AVRCP Controller will be changed to playing
    when AVRCP Target sends the command "play".
    """
    if not self.is_android_bt_target_device:
      signals.TestError('The test requires an android bt target device.')

    self.execute_media_play_pause_test_logic(
        command_sender=self.pri_device,
        test_command=bt_constants.CMD_MEDIA_PLAY)

  def test_media_skip_prev_from_phone(self):
    """Tests the media skip prev from AVRCP Target.

    Tests that Now Playing track of AVRCP Controller will be changed to the
    previous track when AVRCP Target sends the command "skipPrev".
    """
    if not self.is_android_bt_target_device:
      signals.TestError('The test requires an android bt target device.')

    self.execute_skip_next_prev_test_logic(
        command_sender=self.pri_device,
        test_command=bt_constants.CMD_MEDIA_SKIP_PREV)

  def test_media_skip_next_from_phone(self):
    """Tests the media skip next from AVRCP Target.

    Tests that Now Playing track of AVRCP Controller will be changed to the next
    track when AVRCP Target sends the command "skipNext".
    """
    if not self.is_android_bt_target_device:
      signals.TestError('The test requires an android bt target device.')

    self.execute_skip_next_prev_test_logic(
        command_sender=self.pri_device,
        test_command=bt_constants.CMD_MEDIA_SKIP_NEXT)


if __name__ == '__main__':
  test_runner.main()
