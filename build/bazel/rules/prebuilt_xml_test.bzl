# Copyright (C) 2022 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

load("@bazel_skylib//lib:paths.bzl", "paths")
load("@bazel_skylib//lib:unittest.bzl", "analysistest", "asserts")
load("//build/bazel/rules:prebuilt_file.bzl", "PrebuiltFileInfo")
load("//build/bazel/rules:prebuilt_xml.bzl", "prebuilt_xml")
load("//build/bazel/rules/test_common:args.bzl", "get_arg_value")

SRC = "fooSrc.xml"
DIR = "etc/xml"
DTD_SCHEMA = "bar.dtd"
XSD_SCHEMA = "baz.xsd"
FILENAME = "fooFilename"

def _test_prebuilt_xml_commands_impl(ctx):
    env = analysistest.begin(ctx)
    actions = analysistest.target_actions(env)
    asserts.equals(env, 1, len(actions), "expected  1 action got {}".format(actions))
    args = actions[0].argv

    asserts.true(env, len(args) >= 8, "expected at least 8 arguments but got {} {}".format(len(args), args))

    offset = 0
    schema = ctx.attr.schema

    if schema != "":
        offset = 2
        if schema == "--schema":
            asserts.equals(env, paths.basename(get_arg_value(args, schema)), XSD_SCHEMA)
        elif schema == "--dtdvalid":
            asserts.equals(env, paths.basename(get_arg_value(args, schema)), DTD_SCHEMA)
        else:
            analysistest.fail(
                env,
                "Expected schema attr to be --schema or --dtdvalid but got {}".format(schema),
            )

    asserts.equals(env, SRC, paths.basename(args[1 + offset]))
    asserts.equals(env, ">", args[2 + offset])
    asserts.equals(env, "/dev/null", args[3 + offset])
    asserts.equals(env, "&&", args[4 + offset])
    asserts.equals(env, "touch", args[5 + offset])
    asserts.equals(env, "-a", args[6 + offset])

    return analysistest.end(env)

prebuilt_xml_commands_test = analysistest.make(
    _test_prebuilt_xml_commands_impl,
    attrs = {
        "schema": attr.string(),
    },
)

def _test_prebuilt_xml_commands():
    name = "prebuilt_xml_commands"
    test_name = name + "_test"

    prebuilt_xml(
        name = name,
        src = SRC,
        filename = FILENAME,
        tags = ["manual"],
    )
    prebuilt_xml_commands_test(
        name = test_name,
        target_under_test = name,
    )

    return test_name

def _test_prebuilt_xml_commands_dtd():
    name = "prebuilt_xml_commands_dtd"
    test_name = name + "_test"

    prebuilt_xml(
        name = name,
        src = SRC,
        schema = DTD_SCHEMA,
        filename = FILENAME,
        tags = ["manual"],
    )
    prebuilt_xml_commands_test(
        name = test_name,
        schema = "--dtdvalid",
        target_under_test = name,
    )

    return test_name

def _test_prebuilt_xml_commands_xsd():
    name = "prebuilt_xml_commands_xsd"
    test_name = name + "_test"
    prebuilt_xml(
        name = name,
        schema = XSD_SCHEMA,
        filename = FILENAME,
        src = SRC,
        tags = ["manual"],
    )
    prebuilt_xml_commands_test(
        name = test_name,
        schema = "--schema",
        target_under_test = name,
    )

    return test_name

def _test_prebuilt_xml_PrebuiltFileInfo_impl(ctx):
    env = analysistest.begin(ctx)
    target_under_test = analysistest.target_under_test(env)
    prebuilt_file_info = target_under_test[PrebuiltFileInfo]

    asserts.equals(
        env,
        FILENAME,
        prebuilt_file_info.filename,
        "expected PrebuiltFileInfo filename to be {} but got {}".format(FILENAME, prebuilt_file_info.filename),
    )

    asserts.equals(
        env,
        SRC,
        prebuilt_file_info.src.basename,
        "expected PrebuiltFileInfo src to be {} but got {}".format(SRC, prebuilt_file_info.src),
    )

    asserts.equals(
        env,
        DIR,
        prebuilt_file_info.dir,
        "expected PrebuiltFileInfo dir to be {} but got {}".format(DIR, prebuilt_file_info.dir),
    )

    return analysistest.end(env)

prebuilt_xml_PrebuiltFileInfo_test = analysistest.make(_test_prebuilt_xml_PrebuiltFileInfo_impl)

def _test_prebuilt_xml_PrebuiltFileInfo():
    name = "prebuilt_xml_PrebuiltFileInfo"
    test_name = name + "_test"
    prebuilt_xml(
        name = name,
        src = SRC,
        filename = FILENAME,
        tags = ["manual"],
    )
    prebuilt_xml_PrebuiltFileInfo_test(
        name = test_name,
        target_under_test = name,
    )

    return test_name

def _test_prebuilt_xml_schema_validation_impl(ctx):
    env = analysistest.begin(ctx)

    actions = analysistest.target_actions(env)
    target = analysistest.target_under_test(env)
    validation_outputs = target.output_groups._validation.to_list()
    for action in actions:
        for validation_output in validation_outputs:
            if validation_output in action.inputs.to_list():
                analysistest.fail(
                    env,
                    "%s is a validation action output, but is an input to action %s" % (
                        validation_output,
                        action,
                    ),
                )

    return analysistest.end(env)

prebuilt_xml_schema_validation_test = analysistest.make(_test_prebuilt_xml_schema_validation_impl)

def _test_prebuilt_xml_dtd_schema_validation():
    name = "prebuilt_xml_dtd_schema_validation"
    test_name = name + "_test"
    prebuilt_xml(
        name = name,
        src = SRC,
        schema = DTD_SCHEMA,
        filename = FILENAME,
        tags = ["manual"],
    )
    prebuilt_xml_schema_validation_test(
        name = test_name,
        target_under_test = name,
    )

    return test_name

def _test_prebuilt_xml_xsd_schema_validation():
    name = "prebuilt_xml_xsd_schema_validation"
    test_name = name + "_test"
    prebuilt_xml(
        name = name,
        schema = XSD_SCHEMA,
        filename = FILENAME,
        src = SRC,
        tags = ["manual"],
    )
    prebuilt_xml_schema_validation_test(
        name = test_name,
        target_under_test = name,
    )

    return test_name

def _test_prebuilt_xml_minimal_schema_validation():
    name = "prebuilt_xml_minimal_schema_validation"
    test_name = name + "_test"
    prebuilt_xml(
        name = name,
        src = SRC,
        filename = FILENAME,
        tags = ["manual"],
    )
    prebuilt_xml_schema_validation_test(
        name = test_name,
        target_under_test = name,
    )

    return test_name

def prebuilt_xml_test_suite(name):
    native.test_suite(
        name = name,
        tests = [
            _test_prebuilt_xml_commands(),
            _test_prebuilt_xml_commands_dtd(),
            _test_prebuilt_xml_commands_xsd(),
            _test_prebuilt_xml_minimal_schema_validation(),
            _test_prebuilt_xml_dtd_schema_validation(),
            _test_prebuilt_xml_xsd_schema_validation(),
            _test_prebuilt_xml_PrebuiltFileInfo(),
        ],
    )
