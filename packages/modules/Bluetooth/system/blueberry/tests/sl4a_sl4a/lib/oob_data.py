#!/usr/bin/env python3
#
#   Copyright 2022 - The Android Open Source Project
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


class OobData:
    """
    This represents the data generated from the device
    """

    address = None
    confirmation = None
    randomizer = None

    ADDRESS_WITH_TYPE_LENGTH = 14

    def __init__(self, address, confirmation, randomizer):
        self.address = address
        self.confirmation = confirmation
        self.randomizer = randomizer

    def to_sl4a_address(self):
        oob_address = self.address.upper()
        address_str_octets = []
        i = 1
        buf = ""
        for c in oob_address:
            buf += c
            if i % 2 == 0:
                address_str_octets.append(buf)
                buf = ""
            i += 1
        address_str_octets = address_str_octets[:6]
        address_str_octets.reverse()
        return ":".join(address_str_octets)

    def to_sl4a_address_type(self):
        if len(self.address) != self.ADDRESS_WITH_TYPE_LENGTH:
            return -1
        return self.address.upper()[-1]
