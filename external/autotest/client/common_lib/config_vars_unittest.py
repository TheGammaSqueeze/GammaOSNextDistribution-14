#!/usr/bin/python3
# Lint as: python2, python3
# pylint: disable=missing-docstring,bad-indentation

import common
import unittest
import logging

from autotest_lib.client.common_lib.config_vars import TransformJsonText, ConfigTransformError


class ConfigVarsTransformTestCase(unittest.TestCase):
    def testSimple(self):
        self.assertDictEqual(
                TransformJsonText(
                        """{
                            "a": "zzz"
                        }""", {"qwe": "asd"}), {'a': 'zzz'})

    def testSimpleCond(self):
        self.assertDictEqual(
                TransformJsonText(
                        """{
                            "a": "zzz",
                            "b": [
                                {
                                    "AAA": "asd",
                                    "value": "vvvvv"
                                }
                            ]
                        }""", {"AAA": "asd"}), {
                                'a': 'zzz',
                                'b': 'vvvvv'
                        })

    def testSimpleCond2(self):
        self.assertDictEqual(
                TransformJsonText(
                        """{
                            "a": "zzz",
                            "b": [
                                {
                                    "value": "vvvvv"
                                }
                            ]
                        }""", {"AAA": "asd"}), {
                                'a': 'zzz',
                                'b': 'vvvvv'
                        })

    def testSimpleCondFallback(self):
        self.assertDictEqual(
                TransformJsonText(
                        """{
                            "a": "zzz",
                            "b": [
                                {
                                    "AAA": "xxx",
                                    "value": "vvvvv1"
                                },
                                {
                                    "AAA": "yyy",
                                    "value": "vvvvv2"
                                },
                                {
                                    "value": "vvvvv3"
                                }
                            ]
                        }""", {"AAA": "asd"}), {
                                'a': 'zzz',
                                'b': 'vvvvv3'
                        })

    def testNoMatch(self):
        logging.disable(logging.CRITICAL)
        self.assertRaises(
                ConfigTransformError, TransformJsonText, """{
                    "a": "zzz",
                    "b": [
                        {
                            "XXX": "asd",
                            "value": "vvvvv"
                        }
                    ]
                }""", {"AAA": "asd"})
        logging.disable(logging.NOTSET)

    def testUnmatch(self):
        logging.disable(logging.CRITICAL)
        self.assertRaises(
                ConfigTransformError, TransformJsonText, """{
                    "a": "zzz",
                    "b": [
                        {
                            "AAA": "zzz",
                            "value": "vvvvv"
                        }
                    ]
                }""", {"AAA": "asd"})
        logging.disable(logging.NOTSET)

    def testMatchFirst(self):
        self.assertDictEqual(
                TransformJsonText(
                        """{
                            "a": "zzz",
                            "b": [
                                {
                                    "AAA": "asd",
                                    "value": "vvvvv1"
                                },
                                {
                                    "AAA": "asd",
                                    "value": "vvvvv2"
                                }
                            ]
                        }""", {"AAA": "asd"}), {
                                'a': 'zzz',
                                'b': 'vvvvv1'
                        })

    def testMatchMid(self):
        self.assertDictEqual(
                TransformJsonText(
                        """{
                            "a": "zzz",
                            "b": [
                                {
                                    "AAA": "zzz",
                                    "value": "vvvvv1"
                                },
                                {
                                    "AAA": "asd",
                                    "BBB": "jjj",
                                    "value": "vvvvv2"
                                },
                                {
                                    "AAA": "asd",
                                    "BBB": "zxc",
                                    "value": "vvvvv3"
                                },
                                {
                                    "AAA": "asd",
                                    "BBB": "zxc",
                                    "CCC": "qwe",
                                    "value": "vvvvv4"
                                }
                            ]
                        }""", {
                                "AAA": "asd",
                                "BBB": "zxc",
                                "CCC": "qwe"
                        }), {
                                'a': 'zzz',
                                'b': 'vvvvv3'
                        })

    def testMatchLast(self):
        self.assertDictEqual(
                TransformJsonText(
                        """{
                            "a": "zzz",
                            "b": [
                                {
                                    "AAA": "zzz",
                                    "value": "vvvvv1"
                                },
                                {
                                    "AAA": "asd",
                                    "BBB": "jjj",
                                    "value": "vvvvv2"
                                },
                                {
                                    "AAA": "asd",
                                    "BBB": "zxc",
                                    "CCC": "jjj",
                                    "value": "vvvvv3"
                                },
                                {
                                    "AAA": "asd",
                                    "BBB": "zxc",
                                    "CCC": "qwe",
                                    "value": "vvvvv4"
                                }
                            ]
                        }""", {
                                "AAA": "asd",
                                "BBB": "zxc",
                                "CCC": "qwe"
                        }), {
                                'a': 'zzz',
                                'b': 'vvvvv4'
                        })

    def testNested(self):
        self.assertDictEqual(
                TransformJsonText(
                        """{
                            "a": "zzz",
                            "b": [
                                {
                                    "AAA": "asd",
                                    "value": [
                                        {
                                            "BBB": "zxc",
                                            "value": [
                                                {
                                                    "CCC": "qwe",
                                                    "value": "vvvvv4"
                                                }
                                            ]
                                        }
                                    ]
                                }
                            ]
                        }""", {
                                "AAA": "asd",
                                "BBB": "zxc",
                                "CCC": "qwe"
                        }), {
                                'a': 'zzz',
                                'b': 'vvvvv4'
                        })

    def testRegex(self):
        self.assertDictEqual(
                TransformJsonText(
                        """{
                            "a": "zzz",
                            "b": [
                                {
                                    "AAA": "^a.*",
                                    "value": "vvvvv"
                                }
                            ]
                        }""", {"AAA": "asd"}), {
                                'a': 'zzz',
                                'b': 'vvvvv'
                        })

    def testRegexCase(self):
        self.assertDictEqual(
                TransformJsonText(
                        """{
                            "a": "zzz",
                            "b": [
                                {
                                    "AAA": "^A.*D$",
                                    "value": "vvvvv"
                                }
                            ]
                        }""", {"AAA": "asd"}), {
                                'a': 'zzz',
                                'b': 'vvvvv'
                        })

    def testVarExists(self):
        self.assertDictEqual(
                TransformJsonText(
                        """{
                            "a": "zzz",
                            "b": [
                                {
                                    "AAA": true,
                                    "value": "aaa"
                                },
                                {
                                    "value": "bbb"
                                }
                            ]
                        }""", {"AAA": ""}), {
                                'a': 'zzz',
                                'b': 'aaa'
                        })

    def testVarExistsNot(self):
        self.assertDictEqual(
                TransformJsonText(
                        """{
                            "a": "zzz",
                            "b": [
                                {
                                    "BBB": true,
                                    "value": "aaa"
                                },
                                {
                                    "value": "bbb"
                                }
                            ]
                        }""", {"AAA": ""}), {
                                'a': 'zzz',
                                'b': 'bbb'
                        })

    def testVarNotExists(self):
        self.assertDictEqual(
                TransformJsonText(
                        """{
                            "a": "zzz",
                            "b": [
                                {
                                    "AAA": false,
                                    "value": "aaa"
                                },
                                {
                                    "value": "bbb"
                                }
                            ]
                        }""", {"AAA": ""}), {
                                'a': 'zzz',
                                'b': 'bbb'
                        })

    def testVarNotExistsNot(self):
        self.assertDictEqual(
                TransformJsonText(
                        """{
                            "a": "zzz",
                            "b": [
                                {
                                    "BBB": false,
                                    "value": "aaa"
                                },
                                {
                                    "value": "bbb"
                                }
                            ]
                        }""", {"AAA": ""}), {
                                'a': 'zzz',
                                'b': 'aaa'
                        })

    def testEmptyInput(self):
        self.assertRaises(ValueError, TransformJsonText, '', {"qwe": "asd"})

    def testMalformedJson(self):
        self.assertRaises(ValueError, TransformJsonText, '{qwe',
                          {"qwe": "asd"})

    def testNonObjectTopLevelJson(self):
        logging.disable(logging.CRITICAL)
        self.assertRaises(ConfigTransformError, TransformJsonText, '[1, 2, 3]',
                          {"qwe": "asd"})
        logging.disable(logging.NOTSET)

    def testNonObjectTopLevelJson2(self):
        logging.disable(logging.CRITICAL)
        self.assertRaises(ConfigTransformError, TransformJsonText, '"wwwww"',
                          {"qwe": "asd"})
        logging.disable(logging.NOTSET)


if __name__ == '__main__':
    unittest.main()
