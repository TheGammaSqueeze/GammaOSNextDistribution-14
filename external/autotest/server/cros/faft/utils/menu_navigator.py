# Lint as: python2, python3
# Copyright 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import abc
import logging
import six


@six.add_metaclass(abc.ABCMeta)
class _BaseMenuNavigator:
    """Abstract base class for menu navigator."""

    def __init__(self, test):
        self.test = test
        self.faft_config = self.test.faft_config
        self.servo = self.test.servo

    @abc.abstractmethod
    def up(self):
        """Navigate up in the menu."""
        raise NotImplementedError

    @abc.abstractmethod
    def down(self):
        """Navigate down in the menu."""
        raise NotImplementedError

    @abc.abstractmethod
    def select(self, msg=None):
        """Select a menu item."""
        raise NotImplementedError

    def move_to(self, from_idx, to_idx):
        """Move from 'from_idx' to 'to_idx' by menu up/down."""
        if from_idx > to_idx:
            for _ in range(from_idx, to_idx, -1):
                self.up()
                self.test.wait_for('keypress_delay')
        elif from_idx < to_idx:
            for _ in range(from_idx, to_idx, 1):
                self.down()
                self.test.wait_for('keypress_delay')


class _KeyboardMenuNavigator(_BaseMenuNavigator):
    """Navigate with arrow and function keys."""

    def up(self):
        """Navigate up in the menu."""
        self.servo.arrow_up()

    def down(self):
        """Navigate down in the menu."""
        self.servo.arrow_down()

    def select(self, msg=None):
        """Select a menu item."""
        if msg:
            logging.info(msg)
        self.servo.enter_key()


class _DetachableMenuNavigator(_BaseMenuNavigator):
    """Navigate with physical buttons for tablet or detachable devices."""

    def up(self):
        """Navigate up in the menu."""
        self.servo.set_nocheck('volume_up_hold', 100)

    def down(self):
        """Navigate down in the menu."""
        self.servo.set_nocheck('volume_down_hold', 100)

    def select(self, msg=None):
        """Select a menu item."""
        if msg:
            logging.info(msg)
        self.servo.power_short_press()


def create_menu_navigator(faft_framework):
    """Create a proper navigator based on whether or not it is detachable"""
    if faft_framework.faft_config.is_detachable:
        return _DetachableMenuNavigator(faft_framework)
    else:
        return _KeyboardMenuNavigator(faft_framework)
