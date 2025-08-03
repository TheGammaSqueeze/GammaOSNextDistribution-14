#!/usr/bin/env python3
#
#   Copyright 2021 - The Android Open Source Project
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

import dataclasses
import re
import time
from typing import List

from acts import asserts
from acts_contrib.test_utils.tel.tel_defines import SIM_STATE_PIN_REQUIRED
from acts_contrib.test_utils.tel.tel_defines import SIM_STATE_PUK_REQUIRED
from acts_contrib.test_utils.tel.tel_test_utils import is_sim_ready
from acts_contrib.test_utils.tel.tel_test_utils import power_off_sim
from acts_contrib.test_utils.tel.tel_test_utils import power_on_sim

AT_COMMAND_INSTRUMENTATION = 'com.google.mdstest/com.google.mdstest.instrument.ModemATCommandInstrumentation'
AT_COMMAND_FAILURE = 'INSTRUMENTATION_RESULT: result=FAILURE'
LAB_PSIM_ADM_PW = '3131313131313131'
LAB_ESIM_ADM_PW = '35363738FFFFFFFF'
LAB_SIM_DEFAULT_PIN1 = '1234'
LAB_SIM_DEFAULT_PUK1 = '12345678'
UI_ELEMENT_TEXT_SECURITY_SIM_CARD_LOCK = 'SIM card lock'
UI_ELEMENT_TEXT_LOCK_SIM_SET = 'Lock SIM card'
UI_ELEMENT_TEXT_OK = 'OK'
SHORT_MNC_LENGTH = 2


@dataclasses.dataclass
class SimInfo:
  sub_id: int
  slot_index: int
  imsi: str
  mcc_mnc: str
  msin: str
  display_name: str


def get_sim_info(ad) -> List[SimInfo]:
  """Get Lab SIM subscription information.

  Args:
    ad: Android device obj.

  Returns:
    List[SimInfo]: A list of sim information dataclass
  """
  sim_info = []
  sub_info_list = ad.droid.subscriptionGetActiveSubInfoList()
  if not sub_info_list:
    asserts.skip('No Valid SIM in device')
  for sub_info in sub_info_list:
    sub_id = sub_info['subscriptionId']
    imsi = get_sim_imsi(ad, sub_id)
    mcc_mnc = get_sim_mcc_mnc(ad, sub_id)
    msin = get_sim_msin(imsi, mcc_mnc)
    sim_info.append(
        SimInfo(
            sub_id=sub_id,
            slot_index=sub_info['simSlotIndex'],
            imsi=imsi,
            mcc_mnc=mcc_mnc,
            msin=msin,
            display_name=sub_info['displayName']))
  ad.log.info(sim_info)
  return sim_info


def get_sim_msin(imsi, mcc_mnc):
  """Split IMSI to get msin value."""
  msin = imsi.split(mcc_mnc)[1]
  return msin


def get_sim_mcc_mnc(ad, sub_id):
  """Get SIM MCC+MNC value by sub id."""
  return ad.droid.telephonyGetSimOperatorForSubscription(sub_id)


def get_sim_imsi(ad, sub_id):
  """Get SIM IMSI value by sub id."""
  return ad.droid.telephonyGetSubscriberIdForSubscription(sub_id)


def unlock_sim_dsds(ad,
                    dsds=False,
                    pin=LAB_SIM_DEFAULT_PIN1,
                    puk=LAB_SIM_DEFAULT_PUK1) -> bool:
  """Unlock SIM pin1/puk1 on single or dual sim mode.

  Args:
    ad: Android device obj.
    dsds: True is dual sim mode, use adb command to unlock.
    pin: pin1 code, use LAB_DEFAULT_PIN1 for default value.
    puk: puk1 code, use LAB_DEFAULT_PUK1 for default value.

  Returns:
    True if unlock sim success. False otherwise.
  """
  ad.unlock_screen()
  ad.log.info('[Dual_sim=%s] Unlock SIM', dsds)
  if not dsds:
    if is_sim_pin_locked(ad):
      ad.log.info('Unlock SIM pin')
      ad.droid.telephonySupplyPin(pin)
    elif is_sim_puk_locked(ad):
      ad.log.info('Unlock SIM puk')
      ad.droid.telephonySupplyPuk(puk, pin)
    time.sleep(1)
    return is_sim_ready(ad.log, ad)
  else:
    # Checks both pSIM and eSIM states.
    for slot_index in range(2):
      if is_sim_pin_locked(ad, slot_index):
        ad.log.info('[Slot index=%s] Unlock SIM PIN', slot_index)
        if not unlock_pin_by_mds(ad, slot_index, pin):
          ad.log.info('[Slot index=%s] AT+CPIN unlock error', slot_index)
      elif is_sim_puk_locked(ad, slot_index):
        ad.log.info('[Slot index=%s] Unlock SIM PUK', slot_index)
        unlock_puk_by_adb(ad, pin, puk)
      time.sleep(1)
      if not is_sim_ready(ad.log, ad, slot_index):
        return False
    return True


def unlock_puk_by_mds(ad, slot_index, pin, puk) -> bool:
  """Runs AT command to disable SIM PUK1 locked.

  Args:
      ad: Android device obj.
      slot_index: sim slot id.
      pin: pin1 code.
      puk: puk1 code.

  Returns:
      True if response 'OK'. False otherwise.
  """
  set_at_command_channel(ad, slot_index)
  command = r'AT+CPIN=\"' + puk + r'\",\"' + pin + r'\"'
  cmd = (f'am instrument -w -e request "{command}" '
         f'-e response wait {AT_COMMAND_INSTRUMENTATION}')
  ad.log.info('Unlock sim pin by AT command')
  output = ad.adb.shell(cmd)
  if grep(AT_COMMAND_FAILURE, output):
    asserts.skip('Failed to run MDS test command')
  if grep('OK', output):
    return True
  else:
    return False


def unlock_pin_by_mds(ad, slot_index, pin) -> bool:
  """Runs AT command to disable SIM PIN1 locked.

  Args:
      ad: Android device obj.
      slot_index: sim slot id.
      pin: pin1 code, use LAB_DEFAULT_PIN1 for default value.

  Returns:
      True if response 'OK'. False otherwise.
  """
  set_at_command_channel(ad, slot_index)
  command = r'AT+CPIN=\"' + pin + r'\"'
  cmd = (f'am instrument -w -e request "{command}" '
         f'-e response wait {AT_COMMAND_INSTRUMENTATION}')
  ad.log.info('Unlock sim pin by AT command')
  output = ad.adb.shell(cmd)
  if grep(AT_COMMAND_FAILURE, output):
    asserts.skip('Failed to run MDS test command')
  if grep('OK', output):
    return True
  else:
    return False


def unlock_puk_by_adb(ad, pin, puk) -> None:
  """Unlock puk1 by adb keycode.

  Args:
    ad: Android device obj.
    pin: pin1 code.
    puk: puk1 code.

  """
  for key_code in puk:
    ad.send_keycode(key_code)
    time.sleep(1)
  ad.send_keycode('ENTER')
  time.sleep(1)
  # PIN required 2 times
  for _ in range(2):
    for key_code in pin:
      ad.send_keycode(key_code)
      time.sleep(1)
      ad.send_keycode('ENTER')
      time.sleep(1)


def lock_puk_by_mds(ad, slot_index) -> bool:
  """Inputs wrong PIN1 code 3 times to make PUK1 locked.

  Args:
      ad: Android device obj.
      slot_index: Sim slot id.

  Returns:
      True if SIM puk1 locked. False otherwise.
  """
  ad.unlock_screen()
  wrong_pin = '1111'
  for count in range(3):
    if not unlock_pin_by_mds(ad, slot_index, wrong_pin):
      ad.log.info('Error input pin:%d', count+1)
    time.sleep(1)
  ad.reboot()
  return is_sim_puk_locked(ad, slot_index)


def is_sim_puk_locked(ad, slot_index=None) -> bool:
  """Checks whether SIM puk1 is locked on single or dual sim mode.

  Args:
      ad: Android device obj.
      slot_index: Check the SIM status for slot_index.
                  This is optional. If this is None, check default SIM.

  Returns:
      True if SIM puk1 locked. False otherwise.
  """
  if slot_index is None:
    status = ad.droid.telephonyGetSimState()
  else:
    status = ad.droid.telephonyGetSimStateForSlotId(slot_index)
  if status != SIM_STATE_PUK_REQUIRED:
    ad.log.info('Sim state is %s', status)
    return False
  return True


def is_sim_pin_locked(ad, slot_index=None) -> bool:
  """Checks whether SIM pin is locked on single or dual sim mode.

  Args:
      ad: Android device obj.
      slot_index: Check the SIM status for slot_index. This is optional. If this
        is None, check default SIM.

  Returns:
      True if SIM pin1 locked. False otherwise.
  """
  if slot_index is None:
    status = ad.droid.telephonyGetSimState()
  else:
    status = ad.droid.telephonyGetSimStateForSlotId(slot_index)
  if status != SIM_STATE_PIN_REQUIRED:
    ad.log.info('Sim state is %s', status)
    return False
  return True


def set_at_command_channel(ad, slot_index: int) -> bool:
  """Runs AT command to set AT command channel by MDS tool(pSIM=1,eSIM=2).

  Args:
      ad: Android device obj.
      slot_index: Sim slot id.

  Returns:
      True if response 'OK'. False otherwise.
  """
  channel = slot_index + 1
  command = f'AT+CSUS={channel}'
  cmd = (f'am instrument -w -e request "{command}" '
         f'-e response wait {AT_COMMAND_INSTRUMENTATION}')
  ad.log.info('Set AT command channel')
  output = ad.adb.shell(cmd)
  if grep(AT_COMMAND_FAILURE, output):
    asserts.skip('Failed to run MDS test command')
  if grep('OK', output):
    return True
  else:
    return False


def sim_enable_pin_by_mds(ad, pin) -> bool:
  """Runs AT command to enable SIM PIN1 locked by MDS tool.

  Args:
     ad: Android device obj.
     pin: PIN1 code.

  Returns:
      True if response 'OK'. False otherwise.
  """
  command = r'AT+CLCK=\"SC\",1,\"' + pin + r'\"'
  cmd = (f'am instrument -w -e request "{command}" '
         f'-e response wait {AT_COMMAND_INSTRUMENTATION}')
  ad.log.info('Enable sim pin by AT command')
  output = ad.adb.shell(cmd)
  if grep(AT_COMMAND_FAILURE, output):
    asserts.skip('Failed to run MDS test command')
  if grep('OK', output):
    return True
  else:
    return False


def sim_disable_pin_by_mds(ad, pin) -> bool:
  """Runs AT command to disable SIM PIN1 locked by MDS tool.

  Args:
     ad: Android device obj.
     pin: PIN1 code.

  Returns:
      True if response 'OK'. False otherwise.
  """
  command = r'AT+CLCK=\"SC\",0,\"' + pin + r'\"'
  cmd = (f'am instrument -w -e request "{command}" '
         f'-e response wait {AT_COMMAND_INSTRUMENTATION}')
  ad.log.info('Disable sim pin by AT command')
  output = ad.adb.shell(cmd)
  if grep(AT_COMMAND_FAILURE, output):
    asserts.skip('Failed to run MDS test command')
  if grep('OK', output):
    return True
  else:
    return False


def set_sim_lock(ad, enable, slot_index, pin=LAB_SIM_DEFAULT_PIN1) -> bool:
  """Enable/disable SIM card lock.

  Args:
      ad: Android device obj.
      enable: True is to enable sim lock. False is to disable.
      slot_index: Sim slot id.
      pin: Pin1 code.

  Returns:
      True if enable/disable SIM lock successfully.False otherwise.
  """
  if enable:
    ad.log.info('[Slot:%d]Enable SIM pin1 locked by mds', slot_index)
    if not set_at_command_channel(ad, slot_index):
      ad.log.info('[Slot:%d] set AT command on MDS tool not OK', slot_index)
    if sim_enable_pin_by_mds(ad, pin):
      ad.reboot()
    return is_sim_pin_locked(ad, slot_index)
  else:
    ad.log.info('[Slot:%d]Disable SIM pin1 locked by mds', slot_index)
    if not set_at_command_channel(ad, slot_index):
      ad.log.info('[Slot:%d] set AT command on MDS tool not OK', slot_index)
    if sim_disable_pin_by_mds(ad, pin):
      ad.reboot()
    return is_sim_ready(ad.log, ad, slot_index)


def activate_sim(ad,
                 slot_index=None,
                 dsds=False,
                 pin=LAB_SIM_DEFAULT_PIN1,
                 puk=LAB_SIM_DEFAULT_PUK1) -> bool:
  """Activate sim state with slot id. Check sim lock state after activating.

  Args:
      ad: Android_device obj.
      slot_index: Sim slot id.
      dsds: True is dual sim mode, False is single mode.
      pin: pin1 code, use LAB_DEFAULT_PIN1 for default value.
      puk: puk1 code, use LAB_DEFAULT_PUK1 for default value.
  Returns:
     True if activate SIM lock successfully.False otherwise.
  """
  ad.log.info('Disable SIM slot')
  if not power_off_sim(ad, slot_index):
    return False
  time.sleep(2)
  ad.log.info('Enable SIM slot')
  if not power_on_sim(ad, slot_index):
    return False
  unlock_sim_dsds(ad, dsds, pin, puk)
  return True


def grep(regex, output):
  """Returns the line in an output stream that matches a given regex pattern."""
  lines = output.strip().splitlines()
  results = []
  for line in lines:
    if re.search(regex, line):
      results.append(line.strip())
  return results


def modify_sim_imsi(ad,
                    new_imsi,
                    sim_info,
                    sim_adm=LAB_PSIM_ADM_PW):
  """Uses ADB Content Provider Command to Read/Update EF (go/pmw-see-adb).

  Args:
      ad: Android_device obj.
      new_imsi: New IMSI string to be set.
      sim_info: SimInfo dataclass log.
      sim_adm: SIM slot adm password.
  """
  cmd = (f"content update --uri content://com.google.android.wsdsimeditor.EF/EFIMSI "
          f"--bind data:s:'{new_imsi}' --bind format:s:'raw' "
          f"--bind adm:s:'{sim_adm}' --where slot={sim_info.slot_index}")
  ad.log.info('Update IMSI cmd = %s', cmd)
  ad.adb.shell(cmd)
  time.sleep(5)
  modified_imsi = get_sim_imsi(ad, sim_info.sub_id)
  asserts.assert_equal(new_imsi, modified_imsi)
