#!/usr/bin/env python3
#  Copyright (C) 2021 The Android Open Source Project
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

# This script helps fix the apns-full-conf.xml
import re

# As in TelephonyProvider.java#rilRadioTechnologyToNetworkTypeBitmask(int rat)
def rilRadioTechnologyToNetworkType(rat):
    match rat:
        case "1":
            return 1
        case "2":
            return 2
        case "3":
            return 3
        case "9":
            return 8
        case "10":
            return 9
        case "11":
            return 10
        case "4":
            return 4
        case "5":
            return 4
        case "6":
            return 7
        case "7":
            return 5
        case "8":
            return 6
        case "12":
            return 12
        case "13":
            return 14
        case "14":
            return 13
        case "15":
            return 15
        case "16":
            return 16
        case "17":
            return 17
        case "18":
            return 18
        case "19":
            return 19
        case "20":
            return 20
        case _:
            return 0

with open('apns-full-conf.xml', 'r') as ifile, open('new-apns-full-conf.xml', 'w') as ofile:
    RE_TYPE = re.compile(r"^\s*type")
    RE_IA_DEFAULT = re.compile(r"(?!.*ia)default")

    RE_BEAR_BITMASK = re.compile(r"bearer_bitmask=\"[\d|]+\"")
    for line in ifile:
        if re.match(RE_TYPE, line):
        # add the missing "IA" APN type to the APN entry that support "default" APN type
            ofile.write(re.sub(RE_IA_DEFAULT, "default,ia", line))
        elif re.search(RE_BEAR_BITMASK, line):
        # convert bearer_bitmask -> network_type_bitmask
            rats = line.split("\"")[1].strip().split("|")
            networktypes = map(rilRadioTechnologyToNetworkType, rats)
            networktypes = sorted(set(networktypes))
            networktypes = map(str, networktypes)
            networktypes = "|".join(networktypes)
            res = "network_type_bitmask=\"" + networktypes + "\""
            ofile.write(re.sub(RE_BEAR_BITMASK, res, line))
        else:
            ofile.write(line)
