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

"""Android Nearby device setup."""

import datetime
import time
from typing import Mapping

from mobly.controllers import android_device

from performance_test import gms_auto_updates_util

WIFI_COUNTRYCODE_CONFIG_TIME_SEC = 3
TOGGLE_AIRPLANE_MODE_WAIT_TIME_SEC = 2
PH_FLAG_WRITE_WAIT_TIME_SEC = 3

_DISABLE_ENABLE_GMS_UPDATE_WAIT_TIME_SEC = 2

LOG_TAGS = [
    'Nearby',
    'NearbyMessages',
    'NearbyDiscovery',
    'NearbyConnections',
    'NearbyMediums',
    'NearbySetup',
]


def set_country_code(
    ad: android_device.AndroidDevice, country_code: str
) -> None:
  """Sets Wi-Fi and Telephony country code.

  When you set the phone to EU or JP, the available 5GHz channels shrinks.
  Some phones, like Pixel 2, can't use Wi-Fi Direct or Hotspot on 5GHz
  in these countries. Pixel 3+ can, but only on some channels.
  Not all of them. So, test Nearby Share or Nearby Connections without
  Wi-Fi LAN to catch any bugs and make sure we don't break it later.

  Args:
    ad: AndroidDevice, Mobly Android Device.
    country_code: WiFi and Telephony Country Code.
  """
  if (not ad.is_adb_root):
    ad.log.info(f'Skipped setting wifi country code on device "{ad.serial}" '
                'because we do not set country code on unrooted phone.')
    return

  ad.log.info(f'Set Wi-Fi and Telephony country code to {country_code}.')
  ad.adb.shell('cmd wifi set-wifi-enabled disabled')
  time.sleep(WIFI_COUNTRYCODE_CONFIG_TIME_SEC)
  ad.adb.shell(
      'am broadcast -a com.android.internal.telephony.action.COUNTRY_OVERRIDE'
      f' --es country {country_code}'
  )
  ad.adb.shell(f'cmd wifi force-country-code enabled {country_code}')
  enable_airplane_mode(ad)
  time.sleep(WIFI_COUNTRYCODE_CONFIG_TIME_SEC)
  disable_airplane_mode(ad)
  ad.adb.shell('cmd wifi set-wifi-enabled enabled')
  telephony_country_code = (
      ad.adb.shell('dumpsys wifi | grep mTelephonyCountryCode')
      .decode('utf-8')
      .strip()
  )
  ad.log.info(f'Telephony country code: {telephony_country_code}')


def enable_logs(ad: android_device.AndroidDevice) -> None:
  """Enables Nearby related logs."""
  ad.log.info('Enable Nearby loggings.')
  for tag in LOG_TAGS:
    ad.adb.shell(f'setprop log.tag.{tag} VERBOSE')


def grant_manage_external_storage_permission(
    ad: android_device.AndroidDevice, package_name: str
) -> None:
  """Grants MANAGE_EXTERNAL_STORAGE permission to Nearby snippet."""
  build_version_sdk = int(ad.build_info['build_version_sdk'])
  if (build_version_sdk < 30):
    return
  ad.log.info(
      f'Grant MANAGE_EXTERNAL_STORAGE permission on device "{ad.serial}".'
  )
  _grant_manage_external_storage_permission(ad, package_name)


def dump_gms_version(ad: android_device.AndroidDevice) -> Mapping[str, str]:
  """Dumps GMS version from dumpsys to sponge properties."""
  out = (
      ad.adb.shell(
          'dumpsys package com.google.android.gms | grep "versionCode="'
      )
      .decode('utf-8')
      .strip()
  )
  return {f'GMS core version on {ad.serial}': out}


def toggle_airplane_mode(ad: android_device.AndroidDevice) -> None:
  """Toggles airplane mode on the given device."""
  ad.log.info('turn on airplane mode')
  enable_airplane_mode(ad)
  ad.log.info('turn off airplane mode')
  disable_airplane_mode(ad)


def connect_to_wifi_wlan_till_success(
    ad: android_device.AndroidDevice, wifi_ssid: str, wifi_password: str
) -> datetime.timedelta:
  """Connecting to the specified wifi WLAN."""
  ad.log.info('Start connecting to wifi WLAN')
  wifi_connect_start = datetime.datetime.now()
  if not wifi_password:
    wifi_password = None
  connect_to_wifi(ad, wifi_ssid, wifi_password)
  return datetime.datetime.now() - wifi_connect_start


def connect_to_wifi(
    ad: android_device.AndroidDevice,
    ssid: str,
    password: str | None = None,
) -> None:
  if not ad.nearby.wifiIsEnabled():
    ad.nearby.wifiEnable()
  # return until the wifi is connected.
  ad.nearby.wifiConnectSimple(ssid, password)


def _grant_manage_external_storage_permission(
    ad: android_device.AndroidDevice, package_name: str
) -> None:
  """Grants MANAGE_EXTERNAL_STORAGE permission to Nearby snippet.

  This permission will not grant automatically by '-g' option of adb install,
  you can check the all permission granted by:
  am start -a android.settings.APPLICATION_DETAILS_SETTINGS
           -d package:{YOUR_PACKAGE}

  Reference for MANAGE_EXTERNAL_STORAGE:
  https://developer.android.com/training/data-storage/manage-all-files

  This permission will reset to default "Allow access to media only" after
  reboot if you never grant "Allow management of all files" through system UI.
  The appops command and MANAGE_EXTERNAL_STORAGE only available on API 30+.

  Args:
    ad: AndroidDevice, Mobly Android Device.
    package_name: The nearbu snippet package name.
  """
  try:
    ad.adb.shell(
        f'appops set --uid {package_name} MANAGE_EXTERNAL_STORAGE allow'
    )
  except Exception:
    ad.log.info('Failed to grant MANAGE_EXTERNAL_STORAGE permission.')


def enable_airplane_mode(ad: android_device.AndroidDevice) -> None:
  """Enables airplane mode on the given device."""
  if (ad.is_adb_root):
    ad.adb.shell(['settings', 'put', 'global', 'airplane_mode_on', '1'])
    ad.adb.shell([
        'am', 'broadcast', '-a', 'android.intent.action.AIRPLANE_MODE', '--ez',
        'state', 'true'
    ])
  ad.adb.shell(['svc', 'wifi', 'disable'])
  ad.adb.shell(['svc', 'bluetooth', 'disable'])
  time.sleep(TOGGLE_AIRPLANE_MODE_WAIT_TIME_SEC)


def disable_airplane_mode(ad: android_device.AndroidDevice) -> None:
  """Disables airplane mode on the given device."""
  if (ad.is_adb_root):
    ad.adb.shell(['settings', 'put', 'global', 'airplane_mode_on', '0'])
    ad.adb.shell([
        'am', 'broadcast', '-a', 'android.intent.action.AIRPLANE_MODE', '--ez',
        'state', 'false'
    ])
  ad.adb.shell(['svc', 'wifi', 'enable'])
  ad.adb.shell(['svc', 'bluetooth', 'enable'])
  time.sleep(TOGGLE_AIRPLANE_MODE_WAIT_TIME_SEC)


def check_if_ph_flag_committed(
    ad: android_device.AndroidDevice,
    pname: str,
    flag_name: str,
) -> bool:
  """Check if P/H flag is committed."""
  sql_str = (
      'sqlite3 /data/data/com.google.android.gms/databases/phenotype.db'
      ' "select name, quote(coalesce(intVal, boolVal, floatVal, stringVal,'
      ' extensionVal)) from FlagOverrides where committed=1 AND'
      f' packageName=\'{pname}\';"'
  )
  flag_result = ad.adb.shell(sql_str).decode('utf-8').strip()
  return flag_name in flag_result


def write_ph_flag(
    ad: android_device.AndroidDevice,
    pname: str,
    flag_name: str,
    flag_type: str,
    flag_value: str,
) -> None:
  """Write P/H flag."""
  ad.adb.shell(
      'am broadcast -a "com.google.android.gms.phenotype.FLAG_OVERRIDE" '
      f'--es package "{pname}" --es user "*" '
      f'--esa flags "{flag_name}" '
      f'--esa types "{flag_type}" --esa values "{flag_value}" '
      'com.google.android.gms'
  )
  time.sleep(PH_FLAG_WRITE_WAIT_TIME_SEC)


def check_and_try_to_write_ph_flag(
    ad: android_device.AndroidDevice,
    pname: str,
    flag_name: str,
    flag_type: str,
    flag_value: str,
) -> None:
  """Check and try to enable the given flag on the given device."""
  if(not ad.is_adb_root):
    ad.log.info(
        "Can't read or write P/H flag value in non-rooted device. Use Mobile"
        ' Utility app to config instead.'
    )
    return

  if check_if_ph_flag_committed(ad, pname, flag_name):
    ad.log.info(f'{flag_name} is already committed.')
    return
  ad.log.info(f'write {flag_name}.')
  write_ph_flag(ad, pname, flag_name, flag_type, flag_value)

  if check_if_ph_flag_committed(ad, pname, flag_name):
    ad.log.info(f'{flag_name} is configured successfully.')
  else:
    ad.log.info(f'failed to configure {flag_name}.')


def enable_bluetooth_multiplex(ad: android_device.AndroidDevice) -> None:
  """Enable bluetooth multiplex on the given device."""
  pname = 'com.google.android.gms.nearby'
  flag_name = 'mediums_supports_bluetooth_multiplex_socket'
  flag_type = 'boolean'
  flag_value = 'true'
  check_and_try_to_write_ph_flag(ad, pname, flag_name, flag_type, flag_value)


def enable_wifi_aware(ad: android_device.AndroidDevice) -> None:
  """Enable wifi aware on the given device."""
  pname = 'com.google.android.gms.nearby'
  flag_name = 'mediums_supports_wifi_aware'
  flag_type = 'boolean'
  flag_value = 'true'

  check_and_try_to_write_ph_flag(ad, pname, flag_name, flag_type, flag_value)


def enable_auto_reconnect(ad: android_device.AndroidDevice) -> None:
  """Enable auto reconnect on the given device."""
  pname = 'com.google.android.gms.nearby'
  flag_name = 'connection_safe_to_disconnect_auto_reconnect_enabled'
  flag_type = 'boolean'
  flag_value = 'true'
  check_and_try_to_write_ph_flag(ad, pname, flag_name, flag_type, flag_value)

  flag_name = 'connection_safe_to_disconnect_auto_resume_enabled'
  flag_type = 'boolean'
  flag_value = 'true'
  check_and_try_to_write_ph_flag(ad, pname, flag_name, flag_type, flag_value)

  flag_name = 'connection_safe_to_disconnect_version'
  flag_type = 'long'
  flag_value = '4'
  check_and_try_to_write_ph_flag(ad, pname, flag_name, flag_type, flag_value)


def disable_redaction(ad: android_device.AndroidDevice) -> None:
  """Disable info log redaction on the given device."""
  pname = 'com.google.android.gms'
  flag_name = 'ClientLogging__enable_info_log_redaction'
  flag_type = 'boolean'
  flag_value = 'false'

  check_and_try_to_write_ph_flag(ad, pname, flag_name, flag_type, flag_value)


def install_apk(ad: android_device.AndroidDevice, apk_path: str) -> None:
  """Installs the apk on the given device."""
  ad.adb.install(['-r', '-g', '-t', apk_path])


def disable_gms_auto_updates(ad: android_device.AndroidDevice) -> None:
  """Disable GMS auto updates on the given device."""
  if not ad.is_adb_root:
    ad.log.warning(
        'You should disable the play store auto updates manually on a'
        'unrooted device, otherwise the test may be broken unexpected')
  ad.log.info('try to disable GMS Auto Updates.')
  gms_auto_updates_util.GmsAutoUpdatesUtil(ad).disable_gms_auto_updates()
  time.sleep(_DISABLE_ENABLE_GMS_UPDATE_WAIT_TIME_SEC)


def enable_gms_auto_updates(ad: android_device.AndroidDevice) -> None:
  """Enable GMS auto updates on the given device."""
  if not ad.is_adb_root:
    ad.log.warning(
        'You may enable the play store auto updates manually on a'
        'unrooted device after test.')
  ad.log.info('try to enable GMS Auto Updates.')
  gms_auto_updates_util.GmsAutoUpdatesUtil(ad).enable_gms_auto_updates()
  time.sleep(_DISABLE_ENABLE_GMS_UPDATE_WAIT_TIME_SEC)
