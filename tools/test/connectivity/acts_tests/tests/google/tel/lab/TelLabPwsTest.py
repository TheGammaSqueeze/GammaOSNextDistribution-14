#!/usr/bin/env python3
#
#   Copyright 2022 - Google
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

import datetime
import enum
import logging
import time
from typing import Callable

from acts import asserts
from acts.controllers.amarisoft_lib import amarisoft_client
from acts.controllers.amarisoft_lib import config_utils
from acts.controllers.amarisoft_lib import ssh_utils
from acts.controllers.amarisoft_lib import ims
from acts.controllers.amarisoft_lib import mme
from acts.test_decorators import test_tracker_info
from acts_contrib.test_utils.tel import tel_defines
from acts_contrib.test_utils.tel.tel_phone_setup_utils import phone_setup_volte
from acts.libs.proc import job
from acts_contrib.test_utils.tel.TelephonyBaseTest import TelephonyBaseTest

PWS_ALERT_4370 = 4370
PWS_ALERT_4371 = 4371
PWS_ALERT_4380 = 4380
PWS_ALERT_911 = 911
PWS_ALERT_4383 = 4383
PWS_ALERT_4384 = 4384
PWS_ALERT_4393 = 4393
PWS_ALERT_919 = 919

PREFERENCES_XML_FILENAME = '/data/user_de/0/com.google.android.cellbroadcastreceiver/shared_prefs/com.google.android.cellbroadcastreceiver_preferences.xml'
ENABLE_TEST_ALERT_CMD = (
    "sed -i 's/"
    "enable_test_alerts\\\" value=\\\"false/"
    "enable_test_alerts\\\" value=\\\"true/"
    f"' {PREFERENCES_XML_FILENAME}")
PWS_DUPLICATE_DETECTION_OFF = (
    'am broadcast -a '
    'com.android.cellbroadcastservice.action.DUPLICATE_DETECTION '
    '--ez enable false')

IN_CALL_DURATION = datetime.timedelta(seconds=10)
CHECK_INTERVAL = datetime.timedelta(seconds=1)
SERVICE_RESTART_TIME_OUT = datetime.timedelta(seconds=10)
REGISTRATION_TIMEOUT = datetime.timedelta(seconds=120)
WAIT_CALL_STATE_TIMEOUT = datetime.timedelta(seconds=30)
PWS_START_END_INTERVAL = datetime.timedelta(seconds=15)


class TestScenario(enum.Enum):
  """Test scenario for PWS test."""
  PS = 0
  CS = 1
  IDLE = 2


class CallState(enum.Enum):
  """Telephony call state."""
  IDLE = 0
  RINGING = 1
  OFFHOOK = 2


def wait_until(condition: Callable[..., bool], interval: datetime.timedelta,
               timeout: datetime.timedelta, ret: bool, *argv) -> bool:
  """Waits for the condition to occur.

  Args:
    condition: Function to check specific event occur or not.
    interval: Time period during each check.
    timeout: A timer which wait for event occur.
    ret: Expected result of condition.
    *argv: Parameters used by condition.

  Returns:
    True if condition match ret, False otherwise.
  """
  start_time = datetime.datetime.now()
  while datetime.datetime.now() - start_time < timeout:
    if condition(*argv) == ret:
      return True
    time.sleep(interval.total_seconds())
  return False


def is_in_service(ad) -> bool:
  """Checks radio service state of android device .

  Args:
    ad: Mobly's Android controller objects.

  Returns:
    True if device is in service, False otherwise.
  """
  service_state = ad.droid.telephonyGetServiceState()
  if service_state is None:
    return False
  return service_state.get('serviceState') == 'IN_SERVICE'


class TelLabPwsTest(TelephonyBaseTest):

  def setup_class(self):
    super().setup_class()
    self.ad = self.android_devices[0]
    self.ad.info = self.user_params.get('AndroidDevice')[0]
    self.amarisoft_ip_address = self.user_params.get('amarisoft_ip_address')
    self.amarisoft_username = self.user_params.get('amarisoft_username')
    self.amarisoft_pw = self.user_params.get('amarisoft_pw')
    self.amarisoft_call_num = self.user_params.get('amarisoft_call_num')
    self.remote = amarisoft_client.AmariSoftClient(self.amarisoft_ip_address,
                                                   self.amarisoft_username,
                                                   self.amarisoft_pw)
    self.remote.connect()
    self.config = config_utils.ConfigUtils(self.remote)
    self.mme = mme.MmeFunctions(self.remote)
    self.ims = ims.ImsFunctions(self.remote)
    self._amarisoft_preset()
    self._android_device_preset()

  def _amarisoft_preset(self) -> None:
    """Sets Amarisoft test network."""

    if not self.remote.ssh_is_connected():
      raise ssh_utils.NotConnectedError(
          'amarisoft_preset: amarisoft is not connected.')
    self.remote.lte_service_start()

    asserts.skip_if(
        not self.config.upload_enb_template(config_utils.EnbCfg.ENB_GENERIC),
        'amarisoft_preset: Failed to upload enb configuration.')
    asserts.skip_if(
        not self.config.upload_mme_template(config_utils.MmeCfg.MME_GENERIC),
        'amarisoft_preset: Failed to upload mme configuration.')
    asserts.skip_if(
        not self.config.enb_set_plmn('46697'),
        'amarisoft_preset: Failed to set ENB PLMN.')
    asserts.skip_if(
        not self.config.mme_set_plmn('46697'),
        'amarisoft_preset: Failed to set MME PLMN.')
    asserts.skip_if(
        not self.config.enb_set_spectrum_tech(config_utils.SpecTech.FDD.value),
        'amarisoft_preset: Failed to set ENB spectrum technique.')
    asserts.skip_if(
        not self.config.enb_set_fdd_arfcn(275),
        'amarisoft_preset: Failed to set ENB FDD ARFCN.')

    self.remote.lte_service_restart()
    start_time = datetime.datetime.now()
    while not self.remote.lte_service_is_active():
      if datetime.datetime.now() - start_time > SERVICE_RESTART_TIME_OUT:
        asserts.fail('amarisoft_preset: Amarisoft service restart failed.')
      else:
        time.sleep(CHECK_INTERVAL)
    self.log.info('Amarisoft preset completed.')

  def _android_device_preset(self)->None:
    """Presets the device before the test starts."""

    self.log.info('Android device preset start.')
    self.ad.droid.connectivityToggleAirplaneMode(False)
    asserts.skip_if(
        not wait_until(is_in_service, CHECK_INTERVAL, REGISTRATION_TIMEOUT,
                       True, self.ad), 'android_device_preset: '
        f'{self.ad.serial} is still out of service after airplane mode off.')
    self.ad.droid.toggleRingerSilentMode(False)
    self.ad.adb.shell(ENABLE_TEST_ALERT_CMD)
    self.ad.reboot()
    self.ad.droid.setMediaVolume(3)
    self.ad.droid.setRingerVolume(3)
    self.ad.droid.setVoiceCallVolume(3)
    self.ad.droid.setAlarmVolume(3)
    asserts.assert_true(
        phone_setup_volte(self.log, self.ad),
        'android_device_preset: Failed to set up VoLTE.')
    self.log.info('Android device preset completed.')

  def mo_call_to_amarisoft(self) -> None:
    """Executes a MO call process including checking the call status during the MO call.

      The method focus on if any issue found on MO side with below steps:
      (1) Make a voice call from MO side to MT side(Amarisoft).
      (2) MT side accepts the call.
      (3) Check if the call is connect.
      (4) Monitor the in-call status for MO side during in-call duration.
      (5) End the call on MO side.
    """
    if not self.ad.droid.telephonyIsImsRegistered():
      asserts.skip(
          'mo_call_process: No IMS registered, cannot perform VoLTE call test.')
    self.ad.log.info('Dial a Call to callbox.')
    self.ad.droid.telecomCallNumber(self.amarisoft_call_num, False)
    asserts.assert_true(
        wait_until(self.ad.droid.telecomGetCallState, CHECK_INTERVAL,
                   WAIT_CALL_STATE_TIMEOUT, CallState.OFFHOOK.name),
        'mo_call_process: The call is not connected.')
    asserts.assert_false(
        wait_until(self.ad.droid.telecomIsInCall, CHECK_INTERVAL,
                   IN_CALL_DURATION, False),
        'mo_call_process: UE drop call before end call.')
    self.ad.droid.telecomEndCall()
    asserts.assert_true(
        wait_until(self.ad.droid.telecomGetCallState, CHECK_INTERVAL,
                   WAIT_CALL_STATE_TIMEOUT, CallState.IDLE.name),
        'mo_call_process: UE is still in-call after hanging up the call.')

  def pws_action(self, msg: str, test_scenario: int) -> None:
    """Performs a PWS broadcast and check android device receives PWS message.

    (1) Device idle or perform mo call/ping test according to test scenario.
    (2) Broadcast a specific PWS message.
    (3) Wait 15 seconds for device receive PWS message.
    (4) Stop broadcast PWS message.
    (5) Verify android device receive PWS message by check keywords in logcat.
    (6) Perform mo call/ping test according to test scenario.

    Args:
      msg: The PWS parameter to be broadcast.
      test_scenario: The parameters of the test scenario to be executed.
    """
    if test_scenario == TestScenario.PS:
      job.run(f'adb -s {self.ad.serial} shell ping -c 5 8.8.8.8')
    elif test_scenario == TestScenario.CS:
      self.mo_call_to_amarisoft()

    logging.info('Broadcast PWS: %s', msg)
    # Advance the start time by one second to avoid loss of logs
    # due to time differences between test device and mobileharness.
    start_time = datetime.datetime.now() - datetime.timedelta(seconds=1)
    self.mme.pws_write(msg)
    time.sleep(PWS_START_END_INTERVAL.seconds)
    self.mme.pws_kill(msg)

    asserts.assert_true(
        self.ad.search_logcat(
            f'CBChannelManager: isEmergencyMessage: true, message id = {msg}',
            start_time), f'{msg} not received.')
    asserts.assert_false(
        self.ad.search_logcat('Failed to play alert sound', start_time),
        f'{msg} failed to play alert sound.')

    if msg in [PWS_ALERT_911, PWS_ALERT_919]:
      asserts.assert_true(
          self.ad.search_logcat('playAlertTone: alertType=INFO', start_time),
          f'{msg} alertType not match expected (alertType=INFO).')
    else:
      asserts.assert_true(
          self.ad.search_logcat('playAlertTone: alertType=DEFAULT', start_time),
          f'{msg} alertType not match expected (alertType=DEFAULT).')

    if test_scenario == TestScenario.PS:
      job.run(f'adb -s {self.ad.serial} shell ping -c 5 8.8.8.8')
    elif test_scenario == TestScenario.CS:
      self.mo_call_to_amarisoft()

  def teardown_test(self):
    self.ad.adb.shell(PWS_DUPLICATE_DETECTION_OFF)
    super().teardown_test()

  def teardown_class(self):
    self.ad.droid.connectivityToggleAirplaneMode(True)
    super().teardown_class()

  @test_tracker_info(uuid="f8971b34-fcaa-4915-ba05-36c754378987")
  def test_pws_idle_4370(self):
    self.pws_action(PWS_ALERT_4370, TestScenario.IDLE)

  @test_tracker_info(uuid="ed925410-646f-475a-8765-44ea1631cc6a")
  def test_pws_idle_4371(self):
    self.pws_action(PWS_ALERT_4371, TestScenario.IDLE)

  @test_tracker_info(uuid="253f2e2e-8262-43b5-a66e-65b2bc73df58")
  def test_pws_idle_4380(self):
    self.pws_action(PWS_ALERT_4380, TestScenario.IDLE)

  @test_tracker_info(uuid="95ed6407-3c5b-4f58-9fd9-e5021972f03c")
  def test_pws_idle_911(self):
    self.pws_action(PWS_ALERT_911, TestScenario.IDLE)

  @test_tracker_info(uuid="a6f76e03-b808-4194-b286-54a2ca02cb7f")
  def test_pws_idle_4383(self):
    self.pws_action(PWS_ALERT_4383, TestScenario.IDLE)

  @test_tracker_info(uuid="8db4be15-2e2c-4616-8f7f-a6b8062d7265")
  def test_pws_idle_4384(self):
    self.pws_action(PWS_ALERT_4384, TestScenario.IDLE)

  @test_tracker_info(uuid="79ba63d7-8ffb-48d3-b27e-a8b152ee5a25")
  def test_pws_idle_4393(self):
    self.pws_action(PWS_ALERT_4393, TestScenario.IDLE)

  @test_tracker_info(uuid="a07b1c14-dd3f-4818-bc8d-120d006dcea5")
  def test_pws_idle_919(self):
    self.pws_action(PWS_ALERT_919, TestScenario.IDLE)

  @test_tracker_info(uuid="00b607a9-e75c-4342-9c7f-9528704ae3bd")
  def test_pws_ps_4370(self):
    self.pws_action(PWS_ALERT_4370, TestScenario.PS)

  @test_tracker_info(uuid="feff8d7a-52fe-46f0-abe5-0da698fc985c")
  def test_pws_ps_4371(self):
    self.pws_action(PWS_ALERT_4371, TestScenario.PS)

  @test_tracker_info(uuid="22afaaa1-7738-4499-a378-eabb9ae19fa6")
  def test_pws_ps_4380(self):
    self.pws_action(PWS_ALERT_4380, TestScenario.PS)

  @test_tracker_info(uuid="d6fb35fa-9058-4c90-ac8d-bc49d6be1070")
  def test_pws_ps_911(self):
    self.pws_action(PWS_ALERT_911, TestScenario.PS)

  @test_tracker_info(uuid="9937c39f-4b47-47f4-904a-108123919716")
  def test_pws_ps_4383(self):
    self.pws_action(PWS_ALERT_4383, TestScenario.PS)

  @test_tracker_info(uuid="01faa5bb-e02a-42a3-bf08-30e422c684f4")
  def test_pws_ps_4384(self):
    self.pws_action(PWS_ALERT_4384, TestScenario.PS)

  @test_tracker_info(uuid="71d02b4a-a1a3-44e1-a28a-aea3a62f758f")
  def test_pws_ps_4393(self):
    self.pws_action(PWS_ALERT_4393, TestScenario.PS)

  @test_tracker_info(uuid="f5e7801c-80e0-4cbe-b4b1-133fa88fa4a3")
  def test_pws_ps_919(self):
    self.pws_action(PWS_ALERT_919, TestScenario.PS)

  @test_tracker_info(uuid="b68e5593-1748-434c-be2a-e684791f2ca8")
  def test_pws_cs_4370(self):
    self.pws_action(PWS_ALERT_4370, TestScenario.CS)

  @test_tracker_info(uuid="a04f433d-bbf0-4a09-b958-719ec8df9991")
  def test_pws_cs_4371(self):
    self.pws_action(PWS_ALERT_4371, TestScenario.CS)

  @test_tracker_info(uuid="48432d8d-847a-44e3-aa24-32ae704e15de")
  def test_pws_cs_4380(self):
    self.pws_action(PWS_ALERT_4380, TestScenario.CS)

  @test_tracker_info(uuid="9fde76b2-e568-4aa5-a627-9d682ba9e1fb")
  def test_pws_cs_911(self):
    self.pws_action(PWS_ALERT_911, TestScenario.CS)

  @test_tracker_info(uuid="fa1f0c6a-22af-4daf-ab32-a508b06de165")
  def test_pws_cs_4383(self):
    self.pws_action(PWS_ALERT_4383, TestScenario.CS)

  @test_tracker_info(uuid="45d924be-e204-497d-b598-e18a8c668492")
  def test_pws_cs_4384(self):
    self.pws_action(PWS_ALERT_4384, TestScenario.CS)

  @test_tracker_info(uuid="ff4f0e6e-2bda-4047-a69c-7b103868e2d5")
  def test_pws_cs_4393(self):
    self.pws_action(PWS_ALERT_4393, TestScenario.CS)

  @test_tracker_info(uuid="ab2bd166-c5e0-4505-ba37-6192bf53226f")
  def test_pws_cs_919(self):
    self.pws_action(PWS_ALERT_919, TestScenario.CS)


