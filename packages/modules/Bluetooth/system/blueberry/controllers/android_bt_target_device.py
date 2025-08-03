"""Controller class for an android bt device with git_master-bds-dev build.

The config for this derived_bt_target_device in mobileharness is:
- name: android_bt_target_device
  devices:
  - type: MiscTestbedSubDevice
    dimensions:
      mobly_type: DerivedBtDevice
    properties:
      ModuleName: android_bt_target_device
      ClassName: AndroidBtTargetDevice
      Params:
        config:
          device_id: phone_serial_number
          audio_params:
            channel: 2
            duration: 50
            music_file: "music.wav"
            sample_rate: 44100
"""

import logging
import os
import time
from typing import Any, Dict, Optional

from mobly import asserts
from mobly import signals
from mobly.controllers import android_device

# Internal import
from blueberry.utils import android_bluetooth_decorator
from blueberry.utils import bt_constants
from blueberry.utils import bt_test_utils as btutils

_CONNECTION_STATE = bt_constants.BluetoothConnectionStatus

ADB_FILE = 'rec.pcm'
ADB_PATH = '/sdcard/Music/'
WAVE_FILE_TEMPLATE = 'recorded_audio_%s.wav'
DEFAULT_WAIT_TIME = 3.0

# A MediaBrowserService implemented in the SL4A app to intercept Media keys and
# commands.
BLUETOOTH_SL4A_AUDIO_SRC_MBS = 'BluetoothSL4AAudioSrcMBS'

A2DP_HFP_PROFILES = [
    bt_constants.BluetoothProfile.A2DP_SINK,
    bt_constants.BluetoothProfile.HEADSET_CLIENT
]


class AndroidBtTargetDevice(object):
  """Implements an android device as a hfp and a2dp sink device.

  With git_master-bds-dev build, the android device can act as a bluetooth
  hfp and a2dp sink device.
  """

  def __init__(self, config: Dict[str, Any]) -> None:
    """Initializes an android hfp device."""
    logging.info('Initializes the android hfp device')
    self.config = config
    self.pri_ad = None
    self.sec_ad = None
    self.serial = config.get('device_id', None)
    self.audio_params = config.get('audio_params', None)

    if self.serial:
      # self._ad for accessing the device at the end of the test
      self._ad = android_device.AndroidDevice(self.serial)
      self.aud = adb_ui_device.AdbUiDevice(self._ad)
      self.pri_ad = android_bluetooth_decorator.AndroidBluetoothDecorator(
          self._ad)
      self.pri_ad.init_setup()
      self.pri_ad.sl4a_setup()
      self.sl4a = self._ad.services.sl4a
      self.mac_address = self.sl4a.bluetoothGetLocalAddress()

      if self.audio_params:
        self._initialize_audio_params()
    self.avrcp_ready = False

  def __getattr__(self, name: str) -> Any:
    return getattr(self.pri_ad, name)

  def _disable_profiles(self) -> None:
    if self.sec_ad is None:
      raise MissingBtClientDeviceError('Please provide sec_ad forsetting'
                                       'profiles')
    self.set_profiles_policy_off(self.sec_ad, A2DP_HFP_PROFILES)

  def _initialize_audio_params(self) -> None:
    self.audio_capture_path = os.path.join(self._ad.log_path, 'audio_capture')
    os.makedirs(self.audio_capture_path)
    self.adb_path = os.path.join(ADB_PATH, ADB_FILE)
    self.wave_file_template = os.path.join(self.audio_capture_path,
                                           WAVE_FILE_TEMPLATE)
    self.wave_file_number = 0

  def _verify_pri_ad(self) -> None:
    if not self.pri_ad:
      raise signals.ControllerError('No be target device')

  def clean_up(self) -> None:
    """Resets Bluetooth and stops all services when the device is destroyed."""
    self.deactivate_ble_pairing_mode()
    self.factory_reset_bluetooth()
    self._ad.services.stop_all()

  def a2dp_sink_connect(self) -> bool:
    """Establishes the hft connection between self.pri_ad and self.sec_ad."""
    self._verify_pri_ad()
    connected = self.pri_ad.a2dp_sink_connect(self.sec_ad)
    asserts.assert_true(
        connected, 'The a2dp sink connection between {} and {} failed'.format(
            self.serial, self.sec_ad.serial))
    self.log.info('The a2dp sink connection between %s and %s succeeded',
                  self.serial, self.sec_ad.serial)
    return True

  def activate_pairing_mode(self) -> None:
    """Makes the android hfp device discoverable over Bluetooth."""
    self.log.info('Activating the pairing mode of the android target device')
    self.pri_ad.activate_pairing_mode()

  def activate_ble_pairing_mode(self) -> None:
    """Activates BLE pairing mode on an AndroidBtTargetDevice."""
    self.pri_ad.activate_ble_pairing_mode()

  def deactivate_ble_pairing_mode(self) -> None:
    """Deactivates BLE pairing mode on an AndroidBtTargetDevice."""
    self.pri_ad.deactivate_ble_pairing_mode()

  def add_pri_ad_device(self, pri_ad: android_device.AndroidDevice) -> None:
    """Adds primary android device as bt target device.

    The primary android device should have been initialized with
    android_bluetooth_decorator.

    Args:
      pri_ad: the primary android device as bt target device.
    """
    self._ad = pri_ad
    self.pri_ad = pri_ad
    self.sl4a = self._ad.services.sl4a
    self.mac_address = self.sl4a.bluetoothGetLocalAddress()
    self.log = self.pri_ad.log
    self.serial = self.pri_ad.serial
    self.log.info(
        'Adds primary android device with id %s for the bluetooth'
        'connection', pri_ad.serial)
    if self.audio_params:
      self._initialize_audio_params()

  def add_sec_ad_device(self, sec_ad: android_device.AndroidDevice) -> None:
    """Adds second android device for bluetooth connection.

    The second android device should have sl4a service acitvated.

    Args:
      sec_ad: the second android device for bluetooth connection.
    """
    self.log.info(
        'Adds second android device with id %s for the bluetooth'
        'connection', sec_ad.serial)
    self.sec_ad = sec_ad
    self.sec_ad_mac_address = self.sec_ad.sl4a.bluetoothGetLocalAddress()

  def answer_phone_call(self) -> bool:
    """Answers an incoming phone call."""
    if not self.is_hfp_connected():
      self.hfp_connect()
    # Make sure the device is in ringing state.
    if not self.wait_for_call_state(
        bt_constants.CALL_STATE_RINGING, bt_constants.CALL_STATE_TIMEOUT_SEC):
      raise signals.ControllerError(
          'Timed out after %ds waiting for the device %s to be ringing state '
          'before anwsering the incoming phone call.' %
          (bt_constants.CALL_STATE_TIMEOUT_SEC, self.serial))
    self.log.info('Answers the incoming phone call from hf phone %s for %s',
                  self.mac_address, self.sec_ad_mac_address)
    return self.sl4a.bluetoothHfpClientAcceptCall(self.sec_ad_mac_address)

  def call_volume_down(self) -> None:
    """Lowers the volume."""
    current_volume = self.mbs.getVoiceCallVolume()
    if current_volume > 0:
      change_volume = current_volume - 1
      self.log.debug('Set voice call volume from %d to %d.' %
                     (current_volume, change_volume))
      self.mbs.setVoiceCallVolume(change_volume)

  def call_volume_up(self) -> None:
    """Raises the volume."""
    current_volume = self.mbs.getVoiceCallVolume()
    if current_volume < self.mbs.getVoiceCallMaxVolume():
      change_volume = current_volume + 1
      self.log.debug('Set voice call volume from %d to %d.' %
                     (current_volume, change_volume))
      self.mbs.setVoiceCallVolume(change_volume)

  def disconnect_all(self) -> None:
    self._disable_profiles()

  def factory_reset_bluetooth(self) -> None:
    """Factory resets Bluetooth on the android hfp device."""
    self.log.info('Factory resets Bluetooth on the android target device')
    self.pri_ad.factory_reset_bluetooth()

  def get_bluetooth_mac_address(self) -> str:
    """Gets Bluetooth mac address of this android_bt_device."""
    self.log.info('Getting Bluetooth mac address for AndroidBtTargetDevice.')
    mac_address = self.sl4a.bluetoothGetLocalAddress()
    self.log.info('Bluetooth mac address of AndroidBtTargetDevice: %s',
                  mac_address)
    return mac_address

  def get_audio_params(self) -> Optional[Dict[str, str]]:
    """Gets audio params from the android_bt_target_device."""
    return self.audio_params

  def get_new_wave_file_path(self) -> str:
    """Gets a new wave file path for the audio capture."""
    wave_file_path = self.wave_file_template % self.wave_file_number
    while os.path.exists(wave_file_path):
      self.wave_file_number += 1
      wave_file_path = self.wave_file_template % self.wave_file_number
    return wave_file_path

  def get_unread_messages(self) -> None:
    """Gets unread messages from the connected device (MSE)."""
    self.sl4a.mapGetUnreadMessages(self.sec_ad_mac_address)

  def hangup_phone_call(self) -> bool:
    """Hangs up an ongoing phone call."""
    if not self.is_hfp_connected():
      self.hfp_connect()
    self.log.info('Hangs up the phone call from hf phone %s for %s',
                  self.mac_address, self.sec_ad_mac_address)
    return self.sl4a.bluetoothHfpClientTerminateAllCalls(
        self.sec_ad_mac_address)

  def hfp_connect(self) -> bool:
    """Establishes the hft connection between self.pri_ad and self.sec_ad."""
    self._verify_pri_ad()
    connected = self.pri_ad.hfp_connect(self.sec_ad)
    asserts.assert_true(
        connected, 'The hfp connection between {} and {} failed'.format(
            self.serial, self.sec_ad.serial))
    self.log.info('The hfp connection between %s and %s succeed', self.serial,
                  self.sec_ad.serial)
    return connected

  def init_ambs_for_avrcp(self) -> bool:
    """Initializes media browser service for avrcp.

    This is required to be done before running any of the passthrough
    commands.

    Steps:
      1. Starts up the AvrcpMediaBrowserService on the A2dp source phone. This
           MediaBrowserService is part of the SL4A app.
      2. Switch the playback state to be paused.
      3. Connects a MediaBrowser to the A2dp sink's A2dpMediaBrowserService.

    Returns:
      True: if it is avrcp ready after the initialization.
      False: if it is still not avrcp ready after the initialization.

    Raises:
      Signals.ControllerError: raise if AvrcpMediaBrowserService on the A2dp
          source phone fails to be started.
    """
    if self.is_avrcp_ready():
      return True
    if not self.is_a2dp_sink_connected():
      self.a2dp_sink_connect()

    self.sec_ad.log.info('Starting AvrcpMediaBrowserService')
    self.sec_ad.sl4a.bluetoothMediaPhoneSL4AMBSStart()

    time.sleep(DEFAULT_WAIT_TIME)

    # Check if the media session "BluetoothSL4AAudioSrcMBS" is active on sec_ad.
    active_sessions = self.sec_ad.sl4a.bluetoothMediaGetActiveMediaSessions()
    if BLUETOOTH_SL4A_AUDIO_SRC_MBS not in active_sessions:
      raise signals.ControllerError('Failed to start AvrcpMediaBrowserService.')

    self.log.info('Connecting to A2dp media browser service')
    self.sl4a.bluetoothMediaConnectToCarMBS()

    # TODO(user) Wait for an event back instead of sleep
    time.sleep(DEFAULT_WAIT_TIME)
    self.avrcp_ready = True
    return self.avrcp_ready

  def is_avrcp_ready(self) -> bool:
    """Checks if the pri_ad and sec_ad are ready for avrcp."""
    self._verify_pri_ad()
    if self.avrcp_ready:
      return True
    active_sessions = self.sl4a.bluetoothMediaGetActiveMediaSessions()
    if not active_sessions:
      self.log.info('The device is not avrcp ready')
      self.avrcp_ready = False
    else:
      self.log.info('The device is avrcp ready')
      self.avrcp_ready = True
    return self.avrcp_ready

  def is_hfp_connected(self) -> _CONNECTION_STATE:
    """Checks if the pri_ad and sec_ad are hfp connected."""
    self._verify_pri_ad()
    if self.sec_ad is None:
      raise MissingBtClientDeviceError('The sec_ad was not added')
    return self.sl4a.bluetoothHfpClientGetConnectionStatus(
        self.sec_ad_mac_address)

  def is_a2dp_sink_connected(self) -> _CONNECTION_STATE:
    """Checks if the pri_ad and sec_ad are hfp connected."""
    self._verify_pri_ad()
    if self.sec_ad is None:
      raise MissingBtClientDeviceError('The sec_ad was not added')
    return self.sl4a.bluetoothA2dpSinkGetConnectionStatus(
        self.sec_ad_mac_address)

  def last_number_dial(self) -> None:
    """Redials last outgoing phone number."""
    if not self.is_hfp_connected():
      self.hfp_connect()
    self.log.info('Redials last number from hf phone %s for %s',
                  self.mac_address, self.sec_ad_mac_address)
    self.sl4a.bluetoothHfpClientDial(self.sec_ad_mac_address, None)

  def map_connect(self) -> None:
    """Establishes the map connection between self.pri_ad and self.sec_ad."""
    self._verify_pri_ad()
    connected = self.pri_ad.map_connect(self.sec_ad)
    asserts.assert_true(
        connected, 'The map connection between {} and {} failed'.format(
            self.serial, self.sec_ad.serial))
    self.log.info('The map connection between %s and %s succeed', self.serial,
                  self.sec_ad.serial)

  def map_disconnect(self) -> None:
    """Initiates a map disconnection to the connected device.

    Raises:
      BluetoothProfileConnectionError: raised if failed to disconnect.
    """
    self._verify_pri_ad()
    if not self.pri_ad.map_disconnect(self.sec_ad_mac_address):
      raise BluetoothProfileConnectionError(
          'Failed to terminate the MAP connection with the device "%s".' %
          self.sec_ad_mac_address)

  def pbap_connect(self) -> None:
    """Establishes the pbap connection between self.pri_ad and self.sec_ad."""
    connected = self.pri_ad.pbap_connect(self.sec_ad)
    asserts.assert_true(
        connected, 'The pbap connection between {} and {} failed'.format(
            self.serial, self.sec_ad.serial))
    self.log.info('The pbap connection between %s and %s succeed', self.serial,
                  self.sec_ad.serial)

  def pause(self) -> None:
    """Sends Avrcp pause command."""
    self.send_media_passthrough_cmd(bt_constants.CMD_MEDIA_PAUSE, self.sec_ad)

  def play(self) -> None:
    """Sends Avrcp play command."""
    self.send_media_passthrough_cmd(bt_constants.CMD_MEDIA_PLAY, self.sec_ad)

  def power_on(self) -> bool:
    """Turns the Bluetooth on the android bt garget device."""
    self.log.info('Turns on the bluetooth')
    return self.sl4a.bluetoothToggleState(True)

  def power_off(self) -> bool:
    """Turns the Bluetooth off the android bt garget device."""
    self.log.info('Turns off the bluetooth')
    return self.sl4a.bluetoothToggleState(False)

  def route_call_audio(self, connect: bool = False) -> None:
    """Routes call audio during a call."""
    if not self.is_hfp_connected():
      self.hfp_connect()
    self.log.info(
        'Routes call audio during a call from hf phone %s for %s '
        'audio connection %s after routing', self.mac_address,
        self.sec_ad_mac_address, connect)
    if connect:
      self.sl4a.bluetoothHfpClientConnectAudio(self.sec_ad_mac_address)
    else:
      self.sl4a.bluetoothHfpClientDisconnectAudio(self.sec_ad_mac_address)

  def reject_phone_call(self) -> bool:
    """Rejects an incoming phone call."""
    if not self.is_hfp_connected():
      self.hfp_connect()
    # Make sure the device is in ringing state.
    if not self.wait_for_call_state(
        bt_constants.CALL_STATE_RINGING, bt_constants.CALL_STATE_TIMEOUT_SEC):
      raise signals.ControllerError(
          'Timed out after %ds waiting for the device %s to be ringing state '
          'before rejecting the incoming phone call.' %
          (bt_constants.CALL_STATE_TIMEOUT_SEC, self.serial))
    self.log.info('Rejects the incoming phone call from hf phone %s for %s',
                  self.mac_address, self.sec_ad_mac_address)
    return self.sl4a.bluetoothHfpClientRejectCall(self.sec_ad_mac_address)

  def set_audio_params(self, audio_params: Optional[Dict[str, str]]) -> None:
    """Sets audio params to the android_bt_target_device."""
    self.audio_params = audio_params

  def track_previous(self) -> None:
    """Sends Avrcp skip prev command."""
    self.send_media_passthrough_cmd(
        bt_constants.CMD_MEDIA_SKIP_PREV, self.sec_ad)

  def track_next(self) -> None:
    """Sends Avrcp skip next command."""
    self.send_media_passthrough_cmd(
        bt_constants.CMD_MEDIA_SKIP_NEXT, self.sec_ad)

  def start_audio_capture(self, duration_sec: int = 20) -> None:
    """Starts the audio capture over adb.

    Args:
      duration_sec: int, Number of seconds to record audio, 20 secs as default.
    """
    if 'duration' in self.audio_params.keys():
      duration_sec = self.audio_params['duration']
    if not self.is_a2dp_sink_connected():
      self.a2dp_sink_connect()
    cmd = 'ap2f --usage 1 --start --duration {} --target {}'.format(
        duration_sec, self.adb_path)
    self.log.info('Starts capturing audio with adb shell command %s', cmd)
    self.adb.shell(cmd)

  def stop_audio_capture(self) -> str:
    """Stops the audio capture and stores it in wave file.

    Returns:
      File name of the recorded file.

    Raises:
      MissingAudioParamsError: when self.audio_params is None
    """
    if self.audio_params is None:
      raise MissingAudioParamsError('Missing audio params for capturing audio')
    if not self.is_a2dp_sink_connected():
      self.a2dp_sink_connect()
    adb_pull_args = [self.adb_path, self.audio_capture_path]
    self.log.info('start adb -s %s pull %s', self.serial, adb_pull_args)
    self._ad.adb.pull(adb_pull_args)
    pcm_file_path = os.path.join(self.audio_capture_path, ADB_FILE)
    self.log.info('delete the recored file %s', self.adb_path)
    self._ad.adb.shell('rm {}'.format(self.adb_path))
    wave_file_path = self.get_new_wave_file_path()
    self.log.info('convert pcm file %s to wav file %s', pcm_file_path,
                  wave_file_path)
    btutils.convert_pcm_to_wav(pcm_file_path, wave_file_path, self.audio_params)
    return wave_file_path

  def stop_all_services(self) -> None:
    """Stops all services for the pri_ad device."""
    self.log.info('Stops all services on the android bt target device')
    self._ad.services.stop_all()

  def stop_ambs_for_avrcp(self) -> None:
    """Stops media browser service for avrcp."""
    if self.is_avrcp_ready():
      self.log.info('Stops avrcp connection')
      self.sec_ad.sl4a.bluetoothMediaPhoneSL4AMBSStop()
      self.avrcp_ready = False

  def stop_voice_dial(self) -> None:
    """Stops voice dial."""
    if not self.is_hfp_connected():
      self.hfp_connect()
    self.log.info('Stops voice dial from hf phone %s for %s', self.mac_address,
                  self.sec_ad_mac_address)
    if self.is_hfp_connected():
      self.sl4a.bluetoothHfpClientStopVoiceRecognition(
          self.sec_ad_mac_address)

  def take_bug_report(self,
                      test_name: Optional[str] = None,
                      begin_time: Optional[int] = None,
                      timeout: float = 300,
                      destination: Optional[str] = None) -> None:
    """Wrapper method to capture bugreport on the android bt target device."""
    self._ad.take_bug_report(test_name, begin_time, timeout, destination)

  def voice_dial(self) -> None:
    """Triggers voice dial."""
    if not self.is_hfp_connected():
      self.hfp_connect()
    self.log.info('Triggers voice dial from hf phone %s for %s',
                  self.mac_address, self.sec_ad_mac_address)
    if self.is_hfp_connected():
      self.sl4a.bluetoothHfpClientStartVoiceRecognition(
          self.sec_ad_mac_address)

  def log_type(self) -> str:
    """Gets the log type of Android bt target device.

    Returns:
      A string, the log type of Android bt target device.
    """
    return bt_constants.LogType.BLUETOOTH_DEVICE_SIMULATOR.value


class BluetoothProfileConnectionError(Exception):
  """Error for Bluetooth Profile connection problems."""


class MissingBtClientDeviceError(Exception):
  """Error for missing required bluetooth client device."""


class MissingAudioParamsError(Exception):
  """Error for missing the audio params."""
