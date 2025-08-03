# Copyright 2023 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
"""Simple observer base class."""

import logging


class ObserverBase:
    """Simple observer base class that provides the observer pattern."""

    def __init__(self):
        self.observers = {}

    def add_observer(self, name, observer):
        """Add named observer if it doesn't already exist.

        Args:
            name:
                Unique name for the observer.
            observer:
                Object that implements the observer callbacks.

        Returns:
            True if observer was added.
        """
        if name not in self.observers:
            self.observers[name] = observer
            return True

        logging.warning('Observer {} already exists, not adding'.format(name))
        return False

    def remove_observer(self, name, observer):
        """Remove named observer."""
        if name in self.observers:
            del self.observers[name]
