#!/usr/bin/env python3
#
# Copyright (C) 2022 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License."""

from abc import ABC, abstractmethod
import pathlib
from typing import Callable

# given a file, give a list of "information" about it
ExtractInfo = Callable[[pathlib.Path], list[str]]

class Diff(ABC):
  @abstractmethod
  def diff(left_path: pathlib.Path, right_path: pathlib.Path) -> list[str]:
    """Returns a list of strings describing differences in `.o` files.
    Returns the empty list if these files are deemed "similar enough".
    """
    pass
