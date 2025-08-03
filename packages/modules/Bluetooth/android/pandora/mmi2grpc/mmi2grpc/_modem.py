# Copyright (C) 2024 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import os
import socket


class Modem:

    def __init__(self, port):
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.connect(("127.0.0.1", port))
        self.active_calls = []
        self.socket = s

    def close(self):
        for phone_number in self.active_calls:
            self.socket.sendall(b'REM0\r\nAT+REMOTECALL=6,0,0,"' + str(phone_number).encode("utf-8") + b'",0\r\n')
        self.socket.close()

    def call(self, phone_number):
        self.active_calls.append(phone_number)
        self.socket.sendall(b'REM0\r\nAT+REMOTECALL=4,0,0,"' + str(phone_number).encode("utf-8") + b'",129\r\n')

    def answer_outgoing_call(self, phone_number):
        self.active_calls.append(phone_number)
        self.socket.sendall(b'REM0\r\nAT+REMOTECALL=0,0,0,"' + str(phone_number).encode("utf-8") + b'",129\r\n')
