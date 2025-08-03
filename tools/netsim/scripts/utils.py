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
import json
import logging
import os
from pathlib import Path
import platform
import socket
import subprocess
import sys
from threading import currentThread

from time_formatter import TimeFormatter

if sys.version_info[0] == 3:
  from queue import Queue
else:
  from Queue import Queue

from threading import Thread, currentThread

AOSP_ROOT = Path(__file__).absolute().parents[3]
TOOLS = Path(AOSP_ROOT, "tools")
PYTHON_EXE = sys.executable or "python3"
TARGET_MAP = {
    "windows": "windows_msvc-x86_64",
    "windows_x64": "windows_msvc-x86_64",
    "windows_x86_64": "windows_msvc-x86_64",
    "linux": "linux-x86_64",
    "linux_x64": "linux-x86_64",
    "linux_x86_64": "linux-x86_64",
    "linux_aarch64": "linux-aarch64",
    "darwin": "darwin-x86_64",
    "darwin_x64": "darwin-x86_64",
    "darwin_x86_64": "darwin-x86_64",
    "darwin_aarch64": "darwin-aarch64",
}

AVAILABLE = {
    "windows_msvc-x86_64": "toolchain-windows_msvc-x86_64.cmake",
    "linux-x86_64": "toolchain-linux-x86_64.cmake",
    "darwin-x86_64": "toolchain-darwin-x86_64.cmake",
    "linux-aarch64": "toolchain-linux-aarch64.cmake",
    "darwin-aarch64": "toolchain-darwin-aarch64.cmake",
}


def platform_to_cmake_target(target):
  """Translates platform to cmake target"""
  return TARGET_MAP[target.replace("-", "_")]


def cmake_toolchain(target) -> str:
  """Returns the path to the cmake toolchain file."""
  return (
      AOSP_ROOT
      / "external"
      / "qemu"
      / "android"
      / "build"
      / "cmake"
      / AVAILABLE[TARGET_MAP[target.replace("-", "_")]]
  )


def is_presubmit(build_id):
  return build_id.startswith("P")


def get_host_and_ip():
  """Try to get my hostname and ip address."""
  st = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
  try:
    st.connect(("10.255.255.255", 1))
    my_ip = st.getsockname()[0]
  except Exception:
    my_ip = "127.0.0.1"
  finally:
    st.close()

  try:
    hostname = socket.gethostname()
  except Exception:
    hostname = "Unkwown"

  return hostname, my_ip


class LogBelowLevel(logging.Filter):

  def __init__(self, exclusive_maximum, name=""):
    super(LogBelowLevel, self).__init__(name)
    self.max_level = exclusive_maximum

  def filter(self, record):
    return True if record.levelno < self.max_level else False


def config_logging():
  logging_handler_out = logging.StreamHandler(sys.stdout)
  logging_handler_out.setFormatter(
      TimeFormatter("%(asctime)s %(threadName)s | %(message)s")
  )
  logging_handler_out.setLevel(logging.DEBUG)
  logging_handler_out.addFilter(LogBelowLevel(logging.WARNING))

  logging_handler_err = logging.StreamHandler(sys.stderr)
  logging_handler_err.setFormatter(
      TimeFormatter("%(asctime)s %(threadName)s | %(message)s")
  )
  logging_handler_err.setLevel(logging.WARNING)

  logging.root = logging.getLogger("build")
  logging.root.setLevel(logging.INFO)
  logging.root.addHandler(logging_handler_out)
  logging.root.addHandler(logging_handler_err)

  currentThread().setName("inf")


def log_system_info():
  """Log some useful system information."""
  version = "{0[0]}.{0[1]}.{0[2]}".format(sys.version_info)
  hostname, my_ip = get_host_and_ip()

  logging.info(
      "Hello from %s (%s). I'm a %s build bot",
      hostname,
      my_ip,
      platform.system(),
  )
  logging.info("My uname is: %s", platform.uname())
  logging.info(
      "I'm happy to build the emulator using Python %s (%s)",
      PYTHON_EXE,
      version,
  )


def run(cmd, env, log_prefix, cwd=AOSP_ROOT, throw_on_failure=True):
  currentThread().setName(log_prefix)
  cmd_env = os.environ.copy()
  cmd_env.update(env)
  is_windows = platform.system() == "Windows"

  cmd = [str(x) for x in cmd]
  # logging.info("=" * 140)
  # logging.info(json.dumps(cmd_env, sort_keys=True))
  logging.info("%s $> %s", cwd, " ".join(cmd))
  # logging.info("=" * 140)

  proc = subprocess.Popen(
      cmd,
      stdout=subprocess.PIPE,
      stderr=subprocess.PIPE,
      shell=is_windows,  # Make sure windows propagates ENV vars properly.
      cwd=cwd,
      env=cmd_env,
  )

  _log_proc(proc, log_prefix)
  proc.wait()
  if proc.returncode != 0 and throw_on_failure:
    raise Exception("Failed to run %s - %s" % (" ".join(cmd), proc.returncode))


def log_to_queue(q, line):
  """Logs the output of the given process."""
  if q.full():
    q.get()

  strip = line.strip()
  logging.info(strip)
  q.put(strip)


def _reader(pipe, logfn):
  try:
    with pipe:
      for line in iter(pipe.readline, b""):
        lg = line[:-1]
        try:
          lg = lg.decode("utf-8")
        except Exception as e:
          logfn("Failed to utf-8 decode line, {}".format(e))
          lg = str(lg)
        logfn(lg.strip())
  finally:
    pass


def _log_proc(proc, log_prefix):
  """Logs the output of the given process."""
  q = Queue()
  for args in [[proc.stdout, logging.info], [proc.stderr, logging.error]]:
    t = Thread(target=_reader, args=args)
    t.setName(log_prefix)
    t.start()

  return q
