# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

load("@proto//chromiumos/config/api/test/metadata/v1/metadata.proto",
    metadata_pb = "chromiumos.config.api.test.metadata.v1"
)
load("@proto//google/protobuf/struct.proto", google_pb = "google.protobuf")



_TEST_NAME_PREFIX = "remoteTestDrivers/tauto/tests/"

_COMMON_DEPS = {
    "bluetooth": "scope.hardware_topology.bluetooth == scope.hardware_features.PRESENT",
}



def _define_test(
    name,
    *,
    suites = [],
    main_package = 'NOT SET',
    main_args = [],
):
    """Define a single Tauto test.

    Args:
        name: The globally unique name of the test, as known to the scheduling
                infrastructure.
        suites: A list of test suites this test belongs to, without the 'suite:'
                prefix.
        main_package: Python package that contains the entry function.
                e.g. autotest_lib.client.site_tests.stub_Pass.stub_Pass
        main_args: A list of arguments to the entry function.
    """
    test_args = google_pb.ListValue(values = [
        google_pb.Value(string_value = a) for a in main_args
    ])
    informational = metadata_pb.Informational(
        details = google_pb.Struct(fields = {
            "main": google_pb.Value(struct_value = google_pb.Struct(fields = {
                "python_package": google_pb.Value(string_value = main_package),
                "test_args": google_pb.Value(list_value = test_args),
            })),
        }),
    )
    return metadata_pb.Test(
        name = _TEST_NAME_PREFIX + name,
        attributes = [metadata_pb.Attribute(name = "suite:" + s)
                      for s in suites],
        informational = informational,
    )


def _define_client_test(
    test_name,
    purpose,
    doc,
    owner_emails = [],
    owner_groups = [],
    suites = [],
    common_deps = [],
    dep_expressions = [],
    named_args = {},
):

    attrs = [metadata_pb.Attribute(name = "suite:" + s) for s in suites]

    contacts = ([metadata_pb.Contact(email = e) for e in owner_emails]
                + [metadata_pb.Contact(mdb_group = g) for g in owner_groups])

    details = google_pb.Struct(fields = {
        "purpose": google_pb.Value(string_value = purpose),
        "doc": google_pb.Value(string_value = doc),
        "named_args": google_pb.Value(string_value = to_json(named_args))
    })

    info = metadata_pb.Informational(
        authors = contacts,
        details = details,
    )

    missing = [dep for dep in common_deps if dep not in _COMMON_DEPS]
    if missing:
        fail(str(missing) + " are not known common dependencies! " +
             "Please add to test_common.star or check spelling.")

    dep_strs = dep_expressions + [_COMMON_DEPS[dep] for dep in common_deps]
    expression = " && ".join(dep_strs)
    deps = [metadata_pb.DUTCondition(expression = expression)]

    return metadata_pb.Test(
                name = _TEST_NAME_PREFIX + test_name,
                attributes = attrs,
                informational = info,
                dut_constraint = metadata_pb.DUTConstraint(
                    config = metadata_pb.DUTConfigConstraint(
                        expression = expression,
                    )
                ),
    )


test_common = struct(
    define_client_test = _define_client_test,
    define_test = _define_test,
)
