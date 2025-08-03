# Copyright (C) 2016 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License. You may obtain a copy of
# the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations under
# the License.

from google.protobuf import descriptor_pb2
from google.protobuf import text_format
import hashlib


def parse_proto_to_ascii(binary_proto_msg):
    """ Parses binary protobuf message to human readable ascii string.

    Args:
        binary_proto_msg: The binary format of the proto message.
    Returns:
        The ascii format of the proto message.
    """
    return text_format.MessageToString(binary_proto_msg)


def to_descriptor_proto(proto):
    """Retrieves the descriptor proto for the given protobuf message.

    Args:
        proto: the original message.
    Returns:
        The descriptor proto for the input message.
    """
    descriptor_proto = descriptor_pb2.DescriptorProto()
    proto.DESCRIPTOR.CopyToProto(descriptor_proto)
    return descriptor_proto


def md5_proto(proto):
    """ Obtains an md5 checksum of a proto's content."""
    encoded = parse_proto_to_ascii(proto).encode()
    return hashlib.md5(encoded).hexdigest()
