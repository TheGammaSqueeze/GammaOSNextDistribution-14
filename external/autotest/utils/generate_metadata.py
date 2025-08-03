#!/usr/bin/python3
# Copyright (c) 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""Generate metadata for build from Autotest ctrl files."""

import argparse
import os
import six
import sys

# If running in Autotest dir, keep this.
os.environ["PY_VERSION"] = '3'

import common

# NOTE: this MUST be run in Python3, if we get configured back to PY2, exit.
if six.PY2:
    exit(1)

from autotest_lib.server.cros.dynamic_suite import control_file_getter
from autotest_lib.client.common_lib import control_data

from chromiumos.test.api import test_case_metadata_pb2 as tc_metadata_pb
from chromiumos.test.api import test_harness_pb2 as th_pb
from chromiumos.test.api import test_case_pb2 as tc_pb

HARNESS = th_pb.TestHarness.Tauto()


def parse_local_arguments(args):
    """Parse the CLI."""
    parser = argparse.ArgumentParser(
            description="Prep Autotest, Tast, & Services for DockerBuild.")
    parser.add_argument('-autotest_path',
                        dest='autotest_path',
                        default='../../../../third_party/autotest/files/',
                        help='path to autotest/files relative to this script.')
    parser.add_argument('-output_file',
                        dest='output_file',
                        default=None,
                        help='Where to write the serialized pb.')
    return parser.parse_args(args)


def read_file(filename):
    """Read the given file."""
    with open(filename, 'r') as f:
        return f.read()


def all_control_files(args):
    """Return all control files as control file objs."""
    subpaths = ['server/site_tests', 'client/site_tests']
    start_cwd = os.getcwd()
    try:
        os.chdir(args.autotest_path)

        # Might not be needed, but this resolves out the ../
        autotest_path = os.getcwd()

        directories = [os.path.join(autotest_path, p) for p in subpaths]
        f = control_file_getter.FileSystemGetter(directories)
    except Exception as e:
        raise Exception("Failed to find control files at path %s",
                        args.autotest_path)

    finally:
        os.chdir(start_cwd)
    return f._get_control_file_list()


def serialize_test_case_info(data):
    """Return a serialized TestCaseInfo obj."""
    serialized_contacts = tc_metadata_pb.Contact(email=data.author)
    return tc_metadata_pb.TestCaseInfo(owners=[serialized_contacts])


def serialize_tags(data):
    """Return a serialized tags obj (list)."""
    serialized_tags = []
    for value in data.dependencies:
        serialized_tags.append(tc_pb.TestCase.Tag(value=value))
    for value in data.attributes:
        serialized_tags.append(tc_pb.TestCase.Tag(value=value))
    if data.test_class:
        serialized_tags.append(
                tc_pb.TestCase.Tag(
                        value="test_class:{}".format(data.test_class)))
    return serialized_tags


def serialize_test_case(data):
    """Return a serialized api.TestCase obj."""
    serialized_testcase_id = tc_pb.TestCase.Id(value="tauto." + data.name)
    tags = serialize_tags(data)
    return tc_pb.TestCase(id=serialized_testcase_id, name=data.name, tags=tags)


def serialized_test_case_exec(data):
    """Return a serialized TestCaseExec obj."""
    serialized_test_harness = th_pb.TestHarness(tauto=HARNESS)
    return tc_metadata_pb.TestCaseExec(test_harness=serialized_test_harness)


def serialized_test_case_metadata(data):
    """Return a TestCaseMetadata obj from a given control file."""
    serialized_meta_data = tc_metadata_pb.TestCaseMetadata(
            test_case_exec=serialized_test_case_exec(data),
            test_case=serialize_test_case(data),
            test_case_info=serialize_test_case_info(data))
    return serialized_meta_data


def serialized_test_case_metadata_list(data):
    """Return a TestCaseMetadataList obj from a list of TestCaseMetadata pb."""
    serialized_meta_data_list = tc_metadata_pb.TestCaseMetadataList(
            values=data)
    return serialized_meta_data_list


def main():
    """Generate the metadata, and if an output path is given, save it."""
    args = parse_local_arguments(sys.argv[1:])
    ctrlfiles = all_control_files(args)
    serialized_metadata = []
    for file_path in ctrlfiles:
        text = read_file(file_path)
        path = ctrlfiles[1]

        test = control_data.parse_control_string(text,
                                                 raise_warnings=True,
                                                 path=path)
        serialized_metadata.append(serialized_test_case_metadata(test))

    serialized = serialized_test_case_metadata_list(serialized_metadata)
    if args.output_file:
        with open(args.output_file, 'wb') as wf:
            wf.write(serialized.SerializeToString())


if __name__ == '__main__':
    main()
