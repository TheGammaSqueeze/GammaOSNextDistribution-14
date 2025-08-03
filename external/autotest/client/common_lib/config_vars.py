# Lint as: python2, python3
# Copyright (c) 2021 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""
Functions to load config variables from JSON with transformation.

* The config is a key-value dictionary.
* If the value is a list, then the list constitutes a list of conditions
  to check.
* A condition is a key-value dictionary where the key is an external variable
  name and the value is a case-insensitive regexp to match. If multiple
  variables used, they all must match for the condition to succeed.
* A special key "value" is the value to assign if condition succeeds.
* The first matching condition wins.
* Condition with zero external vars always succeeds - it should be the last in
  the list as a last resort case.
* If none of conditions match, it's an error.
* The value, in turn, can be a nested list of conditions.
* If the value is a boolean, the condition checks for the presence or absence
  of an external variable.

Example:
    Python source:
        config = TransformJsonFile(
                                    "config.json",
                                    extvars={
                                        "board": "board1",
                                        "model": "model1",
                                    })
        # config -> {
        #               "cuj_username": "user",
        #               "private_key": "SECRET",
        #               "some_var": "val for board1",
        #               "some_var2": "default val2",
        #           }

        config = TransformJsonFile(
                                    "config.json",
                                    extvars={
                                        "board": "board2",
                                        "model": "model2",
                                    })
        # config -> {
        #               "cuj_username": "user",
        #               "private_key": "SECRET",
        #               "some_var": "val for board2",
        #               "some_var2": "val2 for board2 model2",
        #           }

    config.json:
        {
            "cuj_username": "user",
            "private_key": "SECRET",
            "some_var": [
                {
                    "board": "board1.*",
                    "value": "val for board1",
                },
                {
                    "board": "board2.*",
                    "value": "val for board2",
                },
                {
                    "value": "default val",
                }
            ],
            "some_var2": [
                {
                    "board": "board2.*",
                    "model": "model2.*",
                    "value": "val2 for board2 model2",
                },
                {
                    "value": "default val2",
                }
            ],
        }

See more examples in config_vars_unittest.py

"""

# Lint as: python2, python3
# pylint: disable=missing-docstring,bad-indentation
from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import json
import logging
import re

try:
    unicode
except NameError:
    unicode = str

VERBOSE = False


class ConfigTransformError(ValueError):
    pass


def TransformConfig(data, extvars):
    """Transforms data loaded from JSON to config variables.

    Args:
        data (dict): input data dictionary from JSON parser
        extvars (dict): external variables dictionary

    Returns:
        dict: config variables

    Raises:
        ConfigTransformError: transformation error
        're' errors
    """
    if not isinstance(data, dict):
        _Error('Top level configuration object must be a dictionary but got ' +
               data.__class__.__name__)

    return {key: _GetVal(key, val, extvars) for key, val in data.items()}


def TransformJsonText(text, extvars):
    """Transforms JSON text to config variables.

    Args:
        text (str): JSON input
        extvars (dict): external variables dictionary

    Returns:
        dict: config variables

    Raises:
        ConfigTransformError: transformation error
        're' errors
        'json' errors
    """
    data = json.loads(text)
    return TransformConfig(data, extvars)


def TransformJsonFile(file_name, extvars):
    """Transforms JSON file to config variables.

    Args:
        file_name (str): JSON file name
        extvars (dict): external variables dictionary

    Returns:
        dict: config variables

    Raises:
        ConfigTransformError: transformation error
        're' errors
        'json' errors
        IO errors
    """
    with open(file_name, 'r') as f:
        data = json.load(f)
    return TransformConfig(data, extvars)


def _GetVal(key, val, extvars):
    """Calculates and returns the config variable value.

    Args:
        key (str): key for error reporting
        val (str | list): variable value or conditions list
        extvars (dict): external variables dictionary

    Returns:
        str: resolved variable value

    Raises:
        ConfigTransformError: transformation error
    """
    if (isinstance(val, str) or isinstance(val, unicode)
                or isinstance(val, int) or isinstance(val, float)):
        return val

    if not isinstance(val, list):
        _Error('Conditions must be an array but got ' + val.__class__.__name__,
               json.dumps(val), key)

    for cond in val:
        if not isinstance(cond, dict):
            _Error(
                    'Condition must be a dictionary but got ' +
                    cond.__class__.__name__, json.dumps(cond), key)
        if 'value' not in cond:
            _Error('Missing mandatory "value" key from condition',
                   json.dumps(cond), key)

        for cond_key, cond_val in cond.items():
            if cond_key == 'value':
                continue

            if isinstance(cond_val, bool):
                # Boolean value -> check if variable exists
                if (cond_key in extvars) == cond_val:
                    continue
                else:
                    break

            if cond_key not in extvars:
                logging.warning('Unknown external var: %s', cond_key)
                break
            if re.search(cond_val, extvars[cond_key], re.I) is None:
                break
        else:
            return _GetVal(key, cond['value'], extvars)

    _Error('Condition did not match any external vars',
           json.dumps(val, indent=4) + '\nvars: ' + extvars.__str__(), key)


def _Error(text, extra='', key=''):
    """Reports and raises an error.

    Args:
        text (str): Error text
        extra (str, optional): potentially sensitive error text for verbose output
        key (str): key for error reporting or empty string if none

    Raises:
        ConfigTransformError: error
    """
    if key:
        text = key + ': ' + text
    if VERBOSE and extra:
        text += ':\n' + extra
    logging.error('%s', text)
    raise ConfigTransformError(text)
