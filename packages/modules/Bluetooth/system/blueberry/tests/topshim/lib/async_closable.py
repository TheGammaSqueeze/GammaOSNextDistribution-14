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

import asyncio
import time
from abc import ABC, abstractmethod
import logging


class AsyncClosable(ABC):

    async def __async_exit(self, type=None, value=None, traceback=None):
        try:
            return await self.close()
        except Exception:
            logging.warning("Failed to close or already closed")

    def __enter__(self):
        return self

    def __exit__(self, type, value, traceback):
        asyncio.run_until_complete(self.__async_exit(type, value, traceback))
        return traceback is None

    def __del__(self):
        asyncio.get_event_loop().run_until_complete(self.__async_exit())

    @abstractmethod
    async def close(self):
        pass


async def asyncSafeClose(closable):
    if closable is None:
        logging.warn("Tried to close an object that is None")
        return
    await closable.close()
