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
    Represents an Out of Band data set in a readible object
    """

    def __init__(self, is_valid, transport, byte_string_address, byte_string_c, byte_string_r):
        """
        @param is_valid indicates whether the data was able to be parsed
        @param transport LE or Classic
        @param 7 octet byte string.  Little Endian 6 byte address + 1 byte transport
        @param byte_string_c 16 octet confirmation
        @param byte_string_r 16 octet randomizer
        """
        self.__is_valid = True if is_valid == "1" else False
        self.__transport = int(transport)
        self.__byte_string_address = byte_string_address
        self.__byte_string_c = byte_string_c
        self.__byte_string_r = byte_string_r

    def is_valid(self):
        return self.__is_valid

    def transport(self):
        return self.__transport

    def address(self):
        return self.__byte_string_address

    def confirmation(self):
        return self.__byte_string_c

    def randomizer(self):
        return self.__byte_string_r
