# Lint as: python3
"""Tests for testing audio capture in android bt target controller.

  location of the controller:
    blueberry.controllers.android_bt_target_device
  Before the test, the music file should be copied to the location of the
  pri_phone. The a2dp sink phone should be with the android build that can
  support a2dp sink profile (for example, the git_master-bds-dev).
"""

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import logging
import time

from mobly import asserts
from mobly import test_runner
from blueberry.utils import blueberry_base_test
from blueberry.utils import bt_test_utils

MUSIC_FILE = '1khz.wav'


class BluetoothAudioCaptureTest(blueberry_base_test.BlueberryBaseTest):

  def setup_class(self):
    """Standard Mobly setup class."""
    super(BluetoothAudioCaptureTest, self).setup_class()
    self.derived_bt_device = self.derived_bt_devices[0]
    for device in self.android_devices:
      device.init_setup()
      device.sl4a_setup()
    self.pri_phone = self.android_devices[0]
    self.mac_address = self.derived_bt_device.get_bluetooth_mac_address()
    self.derived_bt_device.activate_pairing_mode()
    self.pri_phone.sl4a.bluetoothDiscoverAndBond(self.mac_address)
    self.pri_phone.wait_for_connection_success(self.mac_address)
    # Gives more time for the pairing between the pri_phone and the
    # derived_bt_device (the android bt target device)
    time.sleep(3)
    self.derived_bt_device.add_sec_ad_device(self.pri_phone)
    self.derived_bt_device.disconnect_all()
    self.duration = self.derived_bt_device.audio_params['duration']
    self.recorded_duration = 0

  def setup_test(self):
    """Setup for bluetooth latency test."""
    logging.info('Setup Test for audio capture test')
    super(BluetoothAudioCaptureTest, self).setup_test()
    asserts.assert_true(self.derived_bt_device.a2dp_sink_connect(),
                        'Failed to establish A2dp Sink connection')

  def test_audio_capture(self):
    """Tests the audio capture for the android bt target device."""

    music_file = self.derived_bt_device.audio_params.get(
        'music_file', MUSIC_FILE)
    music_file = 'file:///sdcard/Music/{}'.format(music_file)
    self.pri_phone.sl4a.mediaPlayOpen(music_file)
    self.pri_phone.sl4a.mediaPlaySetLooping()
    self.pri_phone.sl4a.mediaPlayStart()
    time.sleep(3)
    self.pri_phone.log.info(self.pri_phone.sl4a.mediaPlayGetInfo())
    self.derived_bt_device.start_audio_capture()
    time.sleep(self.duration)
    audio_captured = self.derived_bt_device.stop_audio_capture()
    self.pri_phone.sl4a.mediaPlayStop()
    self.pri_phone.sl4a.mediaPlayClose()
    self.derived_bt_device.log.info('Audio play and record stopped')
    self.recorded_duration = bt_test_utils.get_duration_seconds(audio_captured)
    self.derived_bt_device.log.info(
        'The capture duration is %s s and the recorded duration is %s s',
        self.duration, self.recorded_duration)

  def teardown_class(self):
    logging.info('Factory resetting Bluetooth on devices.')
    self.pri_phone.factory_reset_bluetooth()
    self.derived_bt_device.factory_reset_bluetooth()
    super(BluetoothAudioCaptureTest, self).teardown_class()
    self.derived_bt_device.stop_all_services()
    self.record_data({
        'Test Name': 'test_audio_capture',
        'sponge_properties': {
            'duration': self.duration,
            'recorded duration': self.recorded_duration
        }
    })


if __name__ == '__main__':
  test_runner.main()
