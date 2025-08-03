# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.


# Set root for generated files.
lucicfg.config(
    config_dir = "metadata/generated",
)

# Load and register protos.
load("@stdlib//internal/descpb.star", "wellknown_descpb")
protos = proto.new_descriptor_set(
    name = "chromiumos",
    blob = io.read_file("//metadata/proto/descpb.bin"),
    deps = [wellknown_descpb],
)
protos.register()


load('@proto//chromiumos/config/api/test/metadata/v1/metadata.proto',
    metadata_pb = 'chromiumos.config.api.test.metadata.v1'
)
load('//metadata/tests.star', 'define_tests')


def _specification():
    return metadata_pb.Specification(
        remote_test_drivers = [metadata_pb.RemoteTestDriver(
            name = 'remoteTestDrivers/tauto',
            tests = define_tests(),
        )]
    )


# Generate metadata proto output.
def _generate(config):
    """
    Serializes a proto message to files.

    A text proto and binary proto are written.
    """
    def _generate_impl(ctx):
        ctx.output["config.cfg"] = proto.to_jsonpb(config)
        ctx.output["config.binaryproto"] = proto.to_wirepb(config)
    lucicfg.generator(impl = _generate_impl)


def generate():
    _generate(_specification())
