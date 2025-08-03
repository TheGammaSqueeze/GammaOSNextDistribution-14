#!/usr/bin/env python3
#
#   Copyright 2019 - The Android Open Source Project
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
import time
from abc import ABC, abstractmethod
import logging


class Closable(ABC):

    def __enter__(self):
        return self

    def __exit__(self, type, value, traceback):
        try:
            self.close()
        except Exception:
            logging.warning("Failed to close or already closed")
        return traceback is None

    def __del__(self):
        try:
            self.close()
        except Exception:
            logging.warning("Failed to close or already closed")

    @abstractmethod
    def close(self):
        pass


def safeClose(closable):
    if closable is not None:
        closable.close()
        # sleep for 100ms because GrpcEventQueue takes at most 100 ms to close
        time.sleep(0.1)
