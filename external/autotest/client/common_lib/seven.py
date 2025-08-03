# Copyright (c) 2020 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""Miscellaneous Python 2-3 compatibility functions.

Seven is an extension to the compatibility layer six.
It contains utilities that ease migration from Python 2
to Python 3, but aren't present in the six library.
"""

import six
import six.moves.configparser
import socket
import sys
import types

if six.PY3:
    import builtins
    SOCKET_ERRORS = (builtins.ConnectionError, socket.timeout, socket.gaierror,
                     socket.herror)
    string_types = (str,)
    integer_types = (int,)
    class_types = (type,)
    text_type = str
    binary_type = bytes

    MAXSIZE = sys.maxsize
else:
    SOCKET_ERRORS = (socket.error, )
    string_types = (basestring,)
    integer_types = (int, long)
    class_types = (type, types.ClassType)
    text_type = unicode
    binary_type = str

    MAXSIZE = float("inf")


def exec_file(filename, globals_, locals_):
    """exec_file compiles and runs a file with globals and locals.

    exec_file does not exactly mimic all the edge cases in Python 2's
    execfile function. Rather, it does only what is necessary to execute
    control files in autotest and prevent compiler-wide settings like
    'from __future__ import ...' from spilling into control files that
    have not yet been made Python 3-compatible.

    Arguments:
        filename:   path to a file
        globals_:    dictionary of globals
        locals_:     dictionary of locals

    Returns:
        None (output of six.exec_)
    """
    with open(filename, "rb") as fh:
        code_obj = compile(
                fh.read(),
                filename,
                mode="exec",
                flags=0,
                dont_inherit=1,
        )
    return six.exec_(code_obj, globals_, locals_)


def config_parser(args=None):
    """config_parser returns a non-strict config parser.

    Unfortunately, in six configparser is not same between 2/3. For our .ini's
    we do not want it to be strict (ie, error upon duplicates).
    """
    if six.PY3:
        return six.moves.configparser.ConfigParser(args, strict=False)
    return six.moves.configparser.ConfigParser(args)


def ensure_text(s, encoding='utf-8', errors='strict'):
    """Coerce *s* to six.text_type. Copied from six lib.

    For Python 2:
      - `unicode` -> `unicode`
      - `str` -> `unicode`
    For Python 3:
      - `str` -> `str`
      - `bytes` -> decoded to `str`
    """
    if isinstance(s, binary_type):
        return s.decode(encoding, errors)
    elif isinstance(s, text_type):
        return s
    else:
        raise TypeError("not expecting type '%s'" % type(s))


def ensure_long(n):
    """ensure_long returns a long if py2, or int if py3."""
    if six.PY2:
        return long(n)
    return int(n)
