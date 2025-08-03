#!/usr/bin/env python3
#
# Copyright (C) 2022 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License. You may obtain a copy of
# the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations under
# the License.

import enum


class BleScanSettingsMatchNums(enum.IntEnum):
    """Bluetooth Low Energy scan settings match nums"""
    ONE = 1
    FEW = 2
    MAX = 3


class BleAdvertiseSettingsTxPower(enum.IntEnum):
    """Enum class for BLE advertise settings tx power."""
    ULTRA_LOW = 0
    LOW = 1
    MEDIUM = 2
    HIGH = 3


class BleAdvertiseSettingsMode(enum.IntEnum):
    """Enum class for BLE advertise settings mode."""
    LOW_POWER = 0
    BALANCED = 1
    LOW_LATENCY = 2


class BleScanSettingsModes(enum.IntEnum):
    """Bluetooth Low Energy scan settings mode"""
    OPPORTUNISTIC = -1
    LOW_POWER = 0,
    BALANCED = 1,
    LOW_LATENCY = 2
    AMBIENT_DISCOVERY = 3
