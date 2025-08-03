# Lint as: python2, python3
# Copyright 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""Comparators that be used inplace of mox.<comparator>."""


class IsA():
    """Helper class to check whether a class is an instance of another class.

    Class to help replace mox.IsA. Defines the __eq__ and equals.
    Use to compare to str to see if the other string contains this substr.
    Example:
        foo = IsA(host)
        print(host == foo)
        >>> True
    """

    def __init__(self, arg):
        self.arg = arg

    def __eq__(self, other):
        return self.arg == other

    def equals(self, other):
        """Wrapper for __eq__."""
        return self.__eq__(other)


class Substrings:
    """Class for to simplify multiple substring checks."""

    def __init__(self, substrings):
        self._substrings = substrings

    def __eq__(self, rhs):
        """Return true iff all of _substrings are in the other string."""
        if not isinstance(rhs, str):
            return False
        return all(substr in rhs for substr in self._substrings)


class Substring:
    """Helper class to check whether a substring exists in a string parameter.

    Class to help replace mox.StrContains. Defines the __eq__ and equals.
    Use to compare to str to see if the other string contains this substr.
    Example:
        foobar = Substring("foobar")
        print(foo == "foobarfizzbuzz")
        >>> True
        print(foo == "fizzfoobarbuzz")
        >>> True
        print(foo == "barfoofizzbuzz")
        >>> False
    """

    def __init__(self, _substr):
        if not isinstance(_substr, str):
            raise TypeError("Substring must be of type str")

        self._substr = _substr

    def __eq__(self, rhs):
        if not isinstance(rhs, str):
            return False
        return self._substr in str(rhs)

    def equals(self, rhs):
        """Wrapper for __eq__."""
        return self.__eq__(rhs)
