#!/usr/bin/python3

# Copyright (c) 2020 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from __future__ import print_function

import os
import sys
import tempfile
import unittest

from autotest_lib.client.common_lib import seven


class TestExecCompileFile(unittest.TestCase):
    """Unittests for Seven helpers."""

    def _remove_tempfile(self):
        if hasattr(self, "tempfile"):
            try:
                os.remove(self.tempfile)
            except OSError:
                pass
        return None

    def setUp(self):
        try:
            with tempfile.NamedTemporaryFile(delete=False) as fh:
                self.tempfile = fh.name
        except Exception:  # pylint: disable=broad-except
            self._remove_tempfile()
        super(TestExecCompileFile, self).setUp()

    def tearDown(self):
        super(TestExecCompileFile, self).tearDown()
        self._remove_tempfile()

    def testExecSyntaxError(self):
        with open(self.tempfile, "wb") as fh:
            fh.write(br"(")

        try:
            seven.exec_file(
                    filename=self.tempfile,
                    globals_={},
                    locals_={},
            )
            exn = None
        except Exception as e:  # pylint: disable=broad-except
            exn = e

        self.assertIsInstance(exn, SyntaxError)

    def testExecPrint(self):
        with open(self.tempfile, "wb") as fh:
            fh.write(br"print 'hi'")

        try:
            seven.exec_file(
                    filename=self.tempfile,
                    globals_={},
                    locals_={},
            )
            exn = None
        except Exception as e:  # pylint: disable=broad-except
            exn = e

        if sys.version_info[0] <= 2:
            self.assertIsNone(exn)
        else:
            self.assertIsInstance(exn, SyntaxError)

    def testExecPrintWithFutureImport(self):
        with open(self.tempfile, "wb") as fh:
            fh.write(br"from __future__ import print_function; print 'hi'")

        try:
            seven.exec_file(
                    filename=self.tempfile,
                    globals_={},
                    locals_={},
            )
            exn = None
        except Exception as e:  # pylint: disable=broad-except
            exn = e

        self.assertIsInstance(exn, SyntaxError)


if __name__ == "__main__":
    unittest.main()
