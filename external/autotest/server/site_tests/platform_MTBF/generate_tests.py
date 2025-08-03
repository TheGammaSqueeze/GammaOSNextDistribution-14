# Lint as: python2, python3
# Copyright 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""Script to generate Tauto test wrappers based on JSON configuration.

USAGE: python generate_tests.py <config_file.json> [suite]

This script generates control files for wrapping all Tast test files provided
in the configuration JSON file with a Tauto test cases. No Tauto suite files are
generated, these assumed to be added manually.

Configuration file may contain multiple suites, in which case, all tests of all
suites will be generated, unless [suite] argument is present, in which case
only that suite will be re/generated.

Configuration file is validated against the schema in config_schema.yaml file.
Schema file must be located in the same folder with the current script.
"""

import copy
import json
import os
import sys
from jsonschema import validate
import yaml

SCHEMA_FILE = 'config_schema.yaml'
TEST_TEMPLATE_FILE = 'template.control.performance_cuj'

# The priority of the first test. Decremented by 1 for each subsequent test.
INITIAL_PRIORITY = 5000

# Max duration of a single test.
HOUR_IN_SECS = 60 * 60
DEFAULT_TEST_DURATION = 1 * HOUR_IN_SECS


def _get_absolute_path(local_file):
    return os.path.join(os.path.dirname(os.path.realpath(__file__)),
                        local_file)


def _load_json_config(config_path):
    with open(_get_absolute_path(config_path), 'r') as config_file:
        return json.load(config_file)


def _validate_config_schema(json_config):
    # Loading the schema file
    with open(SCHEMA_FILE, 'r') as schema_file:
        schema = yaml.safe_load(schema_file)

    validate(json_config, schema)


def _parse_constants(json_config):
    consts = dict()
    if 'const' in json_config:
        for c in json_config['const']:
            consts[c['name']] = c['value']
    return consts


def _substitute_constants(val, constants):
    for const in constants:
        val = val.replace('$' + const + '$', constants[const])
    return val


def _parse_tests(json_config, constants):
    tests = []
    for test in json_config['tests']:
        new_test = copy.deepcopy(test)
        # Substitute constants in all fields of the test.
        new_test['name'] = _substitute_constants(new_test['name'], constants)
        new_test['test_expr'] = _substitute_constants(new_test['test_expr'],
                                                      constants)
        if 'args' in new_test:
            new_args = []
            for arg in new_test['args']:
                new_args.append(_substitute_constants(arg, constants))
            new_test['args'] = new_args
        if 'attributes' in new_test:
            new_attrs = []
            for attr in new_test['attributes']:
                new_attrs.append(_substitute_constants(attr, constants))
            new_test['attributes'] = new_attrs
        if 'deps' in new_test:
            new_deps = []
            for dep in new_test['deps']:
                new_deps.append(_substitute_constants(dep, constants))
            new_test['deps'] = new_deps
        tests.append(new_test)
    return tests


def _find_test(test_name, tests):
    for test in tests:
        if test['name'] == test_name:
            return test
    return None


def _parse_suites(json_config, tests, constants):
    suites = []
    for suite in json_config['suites']:
        new_suite = copy.deepcopy(suite)
        new_suite['name'] = _substitute_constants(new_suite['name'], constants)
        if 'args_file' in new_suite:
            new_suite['args_file'] = _substitute_constants(
                    new_suite['args_file'], constants)
        if 'args' in new_suite:
            new_args = []
            for arg in new_suite['args']:
                new_args.append(_substitute_constants(arg, constants))
            new_suite['args'] = new_args
        for test in new_suite['tests']:
            if not _find_test(test['test'], tests):
                raise Exception(
                        'Test %s (requested by suite %s) is not defined.' %
                        (test['test'], new_suite['name']))
            test['test'] = _substitute_constants(test['test'], constants)
        suites.append(new_suite)
    return suites


def _read_file(filename):
    with open(filename, 'r') as content_file:
        return content_file.read()


def _write_file(filename, data):
    with open(filename, 'w') as out_file:
        out_file.write(data)


def _normalize_test_name(test_name):
    return test_name.replace('.', '_').replace('*', '_')


def _calculate_suffix(current_index, repeats):
    # No suffix for single tests.
    if repeats == 1:
        return ''
    # Number of suffix digits depends on the total repeat count.
    digits = len(str(repeats))
    format_string = ('_{{index:0{digits}n}}').format(digits=digits)
    return format_string.format(index=current_index)


def _generate_test_files(version, suites, tests, suite_name=None):
    template = _read_file(_get_absolute_path(TEST_TEMPLATE_FILE))
    for suite in suites:
        priority = INITIAL_PRIORITY
        if suite_name and suite['name'] != suite_name:
            continue
        for test in suite['tests']:
            test_data = _find_test(test['test'], tests)
            repeats = test['repeats']
            deps = []
            if 'deps' in test_data:
                deps = test_data['deps']
            for i in range(repeats):
                test_name = _normalize_test_name(
                        test_data['test_expr'] +
                        _calculate_suffix(i + 1, repeats))
                control_file = template.format(
                        name=test_name,
                        priority=priority,
                        duration=DEFAULT_TEST_DURATION,
                        test_exprs=test_data['test_expr'],
                        length='long',
                        version=version,
                        attributes='suite:' + suite['name'],
                        dependencies=', '.join(deps),
                        iteration=i + 1,
                )
                control_file_name = 'control.' + '_'.join(
                        [suite['name'], test_name])
                _write_file(control_file_name, control_file)
                priority = priority - 1


def main(argv):
    """Main program that parses JSON configuration and generates test wrappers."""
    if not argv or len(argv) < 2 or len(argv) > 3:
        raise Exception(
                'Invalid command-line arguments. Usage: python generate_tests.py <config_file.json> [suite]'
        )

    suite_name = None
    if (len(argv) == 3):
        suite_name = argv[2]

    # Load and validate the config JSON file.
    json_config = _load_json_config(argv[1])
    _validate_config_schema(json_config)

    version = json_config['version']
    constants = _parse_constants(json_config)
    tests = _parse_tests(json_config, constants)
    suites = _parse_suites(json_config, tests, constants)
    _generate_test_files(version, suites, tests, suite_name)


if __name__ == '__main__':
    sys.exit(main(sys.argv))
