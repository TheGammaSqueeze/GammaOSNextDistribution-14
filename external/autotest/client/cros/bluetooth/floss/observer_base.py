# Lint as: python2, python3
# Copyright 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""Simple observer base class."""

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import logging

class ObserverBase:
    """Simple observer base class that provides the observer pattern."""
    def __init__(self):
        self.observers = {}

    def add_observer(self, name, observer):
        """Add named observer if it doesn't already exist.

        @param name: Unique name for the observer.
        @param observer: Object that implements the observer callbacks.

        @return True if observer was added.
        """
        if name not in self.observers:
            self.observers[name] = observer
            return True

        logging.warn('Observer {} already exists, not adding'.format(name))
        return False

    def remove_observer(self, name, observer):
        """Remove named observer."""
        if name in self.observers:
            del self.observers[name]
