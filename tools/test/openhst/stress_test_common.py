# Copyright 2020 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Common functions for the stress tester."""

import logging
import os

from absl import flags
import stress_test_pb2
from google.protobuf import text_format

FLAGS = flags.FLAGS

flags.DEFINE_string("resource_path", None,
                    "Optional override path where to grab resources from. By "
                    "default, resources are grabbed from "
                    "stress_test_common.RESOURCE_DIR, specifying this flag "
                    "will instead result in first looking in this path before "
                    "the module defined resource directory.")

RESOURCE_DIR = "resources/"


def MakeDirsIfNeeded(path):
  """Helper function to create all the directories on a path."""
  if not os.path.isdir(path):
    os.makedirs(path)


def GetResourceContents(resource_name):
  """Gets a string containing the named resource."""
  # Look in the resource override folder first (just go with the basename to
  # find the file, rather than the full path).
  if FLAGS.resource_path:
    path = os.path.join(FLAGS.resource_path, os.path.basename(resource_name))
    if os.path.exists(path):
      return open(path, "rb").read()

  # If the full path exists, grab that, otherwise fall back to the basename.
  if os.path.exists(resource_name):
    return open(resource_name, "rb").read()
  return open(os.path.join(RESOURCE_DIR, os.path.basename(resource_name)),
              "rb").read()


def LoadDeviceConfig(device_type, serial_number):
  """Assembles a DeviceConfig proto following all includes, or the default."""

  config = stress_test_pb2.DeviceConfig()
  text_format.Merge(GetResourceContents(
      os.path.join(RESOURCE_DIR, "device_config.common.ascii_proto")), config)
  def RecursiveIncludeToConfig(resource_prefix, print_error):
    """Load configurations recursively."""
    try:
      new_config = stress_test_pb2.DeviceConfig()
      text_format.Merge(GetResourceContents(
          os.path.join(RESOURCE_DIR,
                       "device_config.%s.ascii_proto" % resource_prefix)),
                        new_config)
      for include_name in new_config.include:
        # If we've managed to import this level properly, then we should print
        # out any errors if we hit them on the included files.
        RecursiveIncludeToConfig(include_name, print_error=True)
      config.MergeFrom(new_config)
    except IOError as err:
      if print_error:
        logging.error(str(err))

  RecursiveIncludeToConfig(device_type, print_error=True)
  RecursiveIncludeToConfig(serial_number, print_error=False)

  def TakeOnlyLatestFromRepeatedField(message, field, key):
    """Take only the latest version."""
    old_list = list(getattr(message, field))
    message.ClearField(field)
    new_list = []
    for i in range(len(old_list) - 1, -1, -1):
      element = old_list[i]
      if not any([getattr(x, key) == getattr(element, key)
                  for x in old_list[i + 1:]]):
        new_list.append(element)
    getattr(message, field).extend(reversed(new_list))

  # We actually need to do a bit of post-processing on the proto - we only want
  # to take the latest version for each (that way people can override stuff if
  # they want)
  TakeOnlyLatestFromRepeatedField(config, "file_to_watch", "source")
  TakeOnlyLatestFromRepeatedField(config, "file_to_move", "source")
  TakeOnlyLatestFromRepeatedField(config, "event", "name")
  TakeOnlyLatestFromRepeatedField(config, "daemon_process", "name")

  return config
