# Copyright 2020 The Android Open Source Project
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
import google.protobuf.text_format


def fmt_proto(msg: google.protobuf.Message) -> str:
  """Formats a `google.protobuf.Message` object as a string.

  Parameters:
      msg: A `google.protobuf.Message` object.

  Returns:
      A string representation of the `msg` object, with each line on a separate
      line.

  Raises:
      TypeError: If `msg` is not a `google.protobuf.Message` object.

  Example:

      >>> from google.protobuf import message
      >>> msg = message.Message()
      >>> msg.set_field1("value1")
      >>> msg.set_field2(123)
      >>> fmt_proto(msg)
      'field1: value1\nfield2: 123'
  """
  return google.protobuf.text_format.MessageToString(msg, as_one_line=True)
