# Copyright 2022 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import base64
import unittest
from unittest import mock

from autotest_lib.server.cros import chrome_sideloader


class TestGetTastExpr(unittest.TestCase):
    """Test GetTastExpr in ChromeSideloader library."""

    class MockHost:
        def path_exists(self, path):
            return True

        def get_file(self, src, dst, delete_dest):
            pass

    def testTastExpr(self):
        tast_expr = "lacros.Basic"
        args_dict = {'tast_expr': tast_expr}
        self.assertEqual(chrome_sideloader.get_tast_expr(args_dict), tast_expr)

    def testEmptyArgsDict(self):
        args_dict = {}
        with self.assertRaises(Exception):
            chrome_sideloader.get_tast_expr(args_dict)

    def testTastExprB64(self):
        tast_expr = '''("group:mainline" && !informational)'''
        tast_expr_b64 = _base64_encode_str(tast_expr)
        args_dict = {'tast_expr_b64': tast_expr_b64}
        self.assertEqual(chrome_sideloader.get_tast_expr(args_dict), tast_expr)

    def testTastExprB64Corrupted(self):
        tast_expr = '''("group:mainline" && !informational)'''
        tast_expr_b64 = _base64_encode_str(tast_expr)
        # remove last character to corrupt the encoding
        tast_expr_b64 = tast_expr_b64[:-1]
        args_dict = {'tast_expr_b64': tast_expr_b64}
        with self.assertRaises(Exception):
            chrome_sideloader.get_tast_expr(args_dict)

    def testTastFileWithKey(self):
        read_data = '{"default": "(\\"group:mainline\\" && !informational)"}'
        file_mock = mock.mock_open(read_data=read_data)
        args_dict = {
                'tast_expr_file': 'mocked_file',
                'tast_expr_key': 'default'
        }
        with mock.patch('builtins.open', file_mock),\
            mock.patch('os.stat'),\
            mock.patch('os.chmod'):
            expr = chrome_sideloader.get_tast_expr_from_file(
                    TestGetTastExpr.MockHost(), args_dict, 'mock/path/')
            self.assertEqual('("group:mainline" && !informational)', expr)


def _base64_encode_str(s):
    return base64.b64encode(s.encode('utf-8')).decode('ascii')


if __name__ == '__main__':
    unittest.main()
