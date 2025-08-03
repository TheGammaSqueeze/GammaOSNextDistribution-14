#!/usr/bin/env python3
#
#   Copyright 2020 - The Android Open Source Project
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


class Capture(object):
    """
    Wrap a match function and use in its place, to capture the value
    that matched. Specify an optional |capture_fn| to transform the
    captured value.
    """

    def __init__(self, match_fn, capture_fn=None):
        self._match_fn = match_fn
        self._capture_fn = capture_fn
        self._value = None

    def __call__(self, obj):
        if self._match_fn(obj) != True:
            return False

        if self._capture_fn is not None:
            self._value = self._capture_fn(obj)
        else:
            self._value = obj
        return True

    def get(self):
        return self._value
