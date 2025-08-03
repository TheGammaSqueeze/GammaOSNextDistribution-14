#!/usr/bin/env python3
#
#   Copyright 2021 - Google
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



# mini-circuit range is 0-95 dbm
NO_SERVICE_POWER_LEVEL = 95
IN_SERVICE_POWER_LEVEL = 0

# definition for wifi/cellular area
NO_SERVICE_AREA = 0
IN_SERVICE_AREA = 1
WIFI_AREA = 2
NO_WIFI_AREA = 3

# time that device will stay at no service area
NO_SERVICE_TIME = 60

# timer for wait back to in-service
WAIT_FOR_SERVICE_TIME = 30

VOICE_CALL = "voice"
VOLTE_CALL = "volte"
CSFB_CALL = "csfb"
WFC_CALL = "wfc_call"
NO_VOICE_CALL = "no voice call"

