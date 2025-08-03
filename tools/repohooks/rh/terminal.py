# Copyright 2016 The Android Open Source Project
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

"""Terminal utilities

This module handles terminal interaction including ANSI color codes.
"""

import os
import sys
from typing import List, Optional

_path = os.path.realpath(__file__ + '/../..')
if sys.path[0] != _path:
    sys.path.insert(0, _path)
del _path

# pylint: disable=wrong-import-position
import rh.shell


# This will erase all content in the current line after the cursor.  This is
# useful for partial updates & progress messages as the terminal can display
# it better.
CSI_ERASE_LINE_AFTER = '\x1b[K'


class Color(object):
    """Conditionally wraps text in ANSI color escape sequences."""

    BLACK, RED, GREEN, YELLOW, BLUE, MAGENTA, CYAN, WHITE = range(8)
    BOLD = -1
    COLOR_START = '\033[1;%dm'
    BOLD_START = '\033[1m'
    RESET = '\033[m'

    def __init__(self, enabled=None):
        """Create a new Color object, optionally disabling color output.

        Args:
          enabled: True if color output should be enabled.  If False then this
              class will not add color codes at all.
        """
        self._enabled = enabled

    def start(self, color):
        """Returns a start color code.

        Args:
          color: Color to use, e.g. BLACK, RED, etc...

        Returns:
          If color is enabled, returns an ANSI sequence to start the given
          color, otherwise returns empty string
        """
        if self.enabled:
            return self.COLOR_START % (color + 30)
        return ''

    def stop(self):
        """Returns a stop color code.

        Returns:
          If color is enabled, returns an ANSI color reset sequence, otherwise
          returns empty string
        """
        if self.enabled:
            return self.RESET
        return ''

    def color(self, color, text):
        """Returns text with conditionally added color escape sequences.

        Args:
          color: Text color -- one of the color constants defined in this class.
          text: The text to color.

        Returns:
          If self._enabled is False, returns the original text.  If it's True,
          returns text with color escape sequences based on the value of color.
        """
        if not self.enabled:
            return text
        if color == self.BOLD:
            start = self.BOLD_START
        else:
            start = self.COLOR_START % (color + 30)
        return start + text + self.RESET

    @property
    def enabled(self):
        """See if the colorization is enabled."""
        if self._enabled is None:
            if 'NOCOLOR' in os.environ:
                self._enabled = not rh.shell.boolean_shell_value(
                    os.environ['NOCOLOR'], False)
            else:
                self._enabled = sys.stderr.isatty()
        return self._enabled


def print_status_line(line, print_newline=False):
    """Clears the current terminal line, and prints |line|.

    Args:
      line: String to print.
      print_newline: Print a newline at the end, if sys.stderr is a TTY.
    """
    if sys.stderr.isatty():
        output = '\r' + line + CSI_ERASE_LINE_AFTER
        if print_newline:
            output += '\n'
    else:
        output = line + '\n'

    sys.stderr.write(output)
    sys.stderr.flush()


def str_prompt(
    prompt: str,
    choices: List[str],
    lower: bool = True,
) -> Optional[str]:
    """Helper function for processing user input.

    Args:
        prompt: The question to present to the user.
        lower: Whether to lowercase the response.

    Returns:
        The string the user entered, or None if EOF (e.g. Ctrl+D).
    """
    prompt = f'{prompt} ({"/".join(choices)})? '
    try:
        result = input(prompt)
        return result.lower() if lower else result
    except EOFError:
        # If the user hits Ctrl+D, or stdin is disabled, use the default.
        print()
        return None
    except KeyboardInterrupt:
        # If the user hits Ctrl+C, just exit the process.
        print()
        raise


def boolean_prompt(prompt='Do you want to continue?', default=True,
                   true_value='yes', false_value='no', prolog=None):
    """Helper function for processing boolean choice prompts.

    Args:
      prompt: The question to present to the user.
      default: Boolean to return if the user just presses enter.
      true_value: The text to display that represents a True returned.
      false_value: The text to display that represents a False returned.
      prolog: The text to display before prompt.

    Returns:
      True or False.
    """
    true_value, false_value = true_value.lower(), false_value.lower()
    true_text, false_text = true_value, false_value
    if true_value == false_value:
        raise ValueError(
            f'true_value and false_value must differ: got {true_value!r}')

    if default:
        true_text = true_text[0].upper() + true_text[1:]
    else:
        false_text = false_text[0].upper() + false_text[1:]

    if prolog:
        prompt = f'\n{prolog}\n{prompt}'
    prompt = '\n' + prompt

    while True:
        response = str_prompt(prompt, choices=(true_text, false_text))
        if not response:
            return default
        if true_value.startswith(response):
            if not false_value.startswith(response):
                return True
            # common prefix between the two...
        elif false_value.startswith(response):
            return False
