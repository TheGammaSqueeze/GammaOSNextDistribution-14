#!/usr/bin/env python
#
# Copyright 2021 - The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the',  help='License');
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an',  help='AS IS' BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.


from distutils.spawn import find_executable
import itertools
import logging
import os
from pathlib import Path
import platform
import socket
from environment import get_default_environment
from utils import AOSP_ROOT, run


# A class that is responsible for configuring the server when running the build.
class ServerConfig(object):
  REDIS_SCCACHE_IP = "34.145.83.254"
  REDIS_PORT = 443

  def __in_gce(self):
    """Queries the magic url to determine if we are in GCE"""
    try:
      # TODO(jansene): Remove once windows buildbots are using PY3
      import urllib.request

      with urllib.request.urlopen("http://metadata.google.internal") as r:
        return r.getheader("Metadata-Flavor") == "Google"
    except:
      logging.info("Unable to query magic url, we are not in gce.")
      return False

  def __can_use_redis(self):
    """Tries to connect to the redis cache."""
    try:
      s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
      s.settimeout(2.5)
      s.connect((ServerConfig.REDIS_SCCACHE_IP, ServerConfig.REDIS_PORT))
      return True
    except:
      logging.exception("Unable to connect to redis, we are not in corp.")
    return False

  def __init__(self, presubmit, args):
    self.args = args
    self.presubmit = presubmit
    self.env = get_default_environment(AOSP_ROOT)
    self.target = platform.system().lower()
    search_dir = Path(
        AOSP_ROOT,
        "prebuilts",
        "android-emulator-build",
        "common",
        "sccache",
        f"{self.target}-x86_64",
    ).absolute()
    self.sccache = find_executable("sccache", str(search_dir))

  def get_env(self):
    return self.env

  def __enter__(self):
    """Configure cache, report statistics and setup vscode"""
    # Let's make sure we have ninja on the path.

    if self.__in_gce():
      # Use a bucket in gce. Make sure the default service account has R/W
      # access to gs://emu-dev-sccache
      self.env["SCCACHE_GCS_BUCKET"] = "emu-dev-sccache"
      self.env["SCCACHE_GCS_OAUTH_URL"] = (
          "http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token"
      )
      self.env["SCCACHE_GCS_RW_MODE"] = "READ_WRITE"
    elif self.__can_use_redis():
      # Lets try our redis cache.
      self.env["SCCACHE_REDIS"] = "redis//{}:{}/1".format(
          ServerConfig.REDIS_SCCACHE_IP, ServerConfig.REDIS_PORT
      )

    # Configure logging, (debug logging is very verbose, and only needed if
    # you wish to figure out why you have a lot of cache-misses)
    self.env["SCCACHE_LOG"] = "info"
    self.env["SCCACHE_ERROR_LOG"] = os.path.join(
        self.args.dist_dir, "sccache.log"
    )

    # We will terminate sccache upon completion
    self.env["SCCACHE_IDLE_TIMEOUT"] = "0"
    if self.sccache:
      # This will print stats, and launch the server if needed
      run([self.sccache, "--stop-server"], self.env, "scc", AOSP_ROOT, False)
      run([self.sccache, "--start-server"], self.env, "scc", AOSP_ROOT, False)

    return self

  def __exit__(self, exc_type, exc_value, tb):
    """Report cache statistics and stop the server."""
    if self.sccache:
      run([self.sccache, "--stop-server"], self.env, "scc")
