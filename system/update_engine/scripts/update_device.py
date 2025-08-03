#!/usr/bin/env python3
#
# Copyright (C) 2017 The Android Open Source Project
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
#

"""Send an A/B update to an Android device over adb."""

from __future__ import print_function
from __future__ import absolute_import

import argparse
import binascii
import logging
import os
import re
import socket
import subprocess
import sys
import struct
import tempfile
import time
import threading
import zipfile
import shutil

from six.moves import BaseHTTPServer


# The path used to store the OTA package when applying the package from a file.
OTA_PACKAGE_PATH = '/data/ota_package'

# The path to the payload public key on the device.
PAYLOAD_KEY_PATH = '/etc/update_engine/update-payload-key.pub.pem'

# The port on the device that update_engine should connect to.
DEVICE_PORT = 1234


def CopyFileObjLength(fsrc, fdst, buffer_size=128 * 1024, copy_length=None, speed_limit=None):
  """Copy from a file object to another.

  This function is similar to shutil.copyfileobj except that it allows to copy
  less than the full source file.

  Args:
    fsrc: source file object where to read from.
    fdst: destination file object where to write to.
    buffer_size: size of the copy buffer in memory.
    copy_length: maximum number of bytes to copy, or None to copy everything.
    speed_limit: upper limit for copying speed, in bytes per second.

  Returns:
    the number of bytes copied.
  """
  # If buffer size significantly bigger than speed limit
  # traffic would seem extremely spiky to the client.
  if speed_limit:
    print(f"Applying speed limit: {speed_limit}")
    buffer_size = min(speed_limit//32, buffer_size)

  start_time = time.time()
  copied = 0
  while True:
    chunk_size = buffer_size
    if copy_length is not None:
      chunk_size = min(chunk_size, copy_length - copied)
      if not chunk_size:
        break
    buf = fsrc.read(chunk_size)
    if not buf:
      break
    if speed_limit:
      expected_duration = copied/speed_limit
      actual_duration = time.time() - start_time
      if actual_duration < expected_duration:
        time.sleep(expected_duration-actual_duration)
    fdst.write(buf)
    copied += len(buf)
  return copied


class AndroidOTAPackage(object):
  """Android update payload using the .zip format.

  Android OTA packages traditionally used a .zip file to store the payload. When
  applying A/B updates over the network, a payload binary is stored RAW inside
  this .zip file which is used by update_engine to apply the payload. To do
  this, an offset and size inside the .zip file are provided.
  """

  # Android OTA package file paths.
  OTA_PAYLOAD_BIN = 'payload.bin'
  OTA_PAYLOAD_PROPERTIES_TXT = 'payload_properties.txt'
  SECONDARY_OTA_PAYLOAD_BIN = 'secondary/payload.bin'
  SECONDARY_OTA_PAYLOAD_PROPERTIES_TXT = 'secondary/payload_properties.txt'
  PAYLOAD_MAGIC_HEADER = b'CrAU'

  def __init__(self, otafilename, secondary_payload=False):
    self.otafilename = otafilename

    otazip = zipfile.ZipFile(otafilename, 'r')
    payload_entry = (self.SECONDARY_OTA_PAYLOAD_BIN if secondary_payload else
                     self.OTA_PAYLOAD_BIN)
    payload_info = otazip.getinfo(payload_entry)

    if payload_info.compress_type != 0:
      logging.error(
          "Expected payload to be uncompressed, got compression method %d",
          payload_info.compress_type)
    # Don't use len(payload_info.extra). Because that returns size of extra
    # fields in central directory. We need to look at local file directory,
    # as these two might have different sizes.
    with open(otafilename, "rb") as fp:
      fp.seek(payload_info.header_offset)
      data = fp.read(zipfile.sizeFileHeader)
      fheader = struct.unpack(zipfile.structFileHeader, data)
      # Last two fields of local file header are filename length and
      # extra length
      filename_len = fheader[-2]
      extra_len = fheader[-1]
      self.offset = payload_info.header_offset
      self.offset += zipfile.sizeFileHeader
      self.offset += filename_len + extra_len
      self.size = payload_info.file_size
      fp.seek(self.offset)
      payload_header = fp.read(4)
      if payload_header != self.PAYLOAD_MAGIC_HEADER:
        logging.warning(
            "Invalid header, expected %s, got %s."
            "Either the offset is not correct, or payload is corrupted",
            binascii.hexlify(self.PAYLOAD_MAGIC_HEADER),
            binascii.hexlify(payload_header))

    property_entry = (self.SECONDARY_OTA_PAYLOAD_PROPERTIES_TXT if
                      secondary_payload else self.OTA_PAYLOAD_PROPERTIES_TXT)
    self.properties = otazip.read(property_entry)


class UpdateHandler(BaseHTTPServer.BaseHTTPRequestHandler):
  """A HTTPServer that supports single-range requests.

  Attributes:
    serving_payload: path to the only payload file we are serving.
    serving_range: the start offset and size tuple of the payload.
  """

  @staticmethod
  def _parse_range(range_str, file_size):
    """Parse an HTTP range string.

    Args:
      range_str: HTTP Range header in the request, not including "Header:".
      file_size: total size of the serving file.

    Returns:
      A tuple (start_range, end_range) with the range of bytes requested.
    """
    start_range = 0
    end_range = file_size

    if range_str:
      range_str = range_str.split('=', 1)[1]
      s, e = range_str.split('-', 1)
      if s:
        start_range = int(s)
        if e:
          end_range = int(e) + 1
      elif e:
        if int(e) < file_size:
          start_range = file_size - int(e)
    return start_range, end_range

  def do_GET(self):  # pylint: disable=invalid-name
    """Reply with the requested payload file."""
    if self.path != '/payload':
      self.send_error(404, 'Unknown request')
      return

    if not self.serving_payload:
      self.send_error(500, 'No serving payload set')
      return

    try:
      f = open(self.serving_payload, 'rb')
    except IOError:
      self.send_error(404, 'File not found')
      return
    # Handle the range request.
    if 'Range' in self.headers:
      self.send_response(206)
    else:
      self.send_response(200)

    serving_start, serving_size = self.serving_range
    start_range, end_range = self._parse_range(self.headers.get('range'),
                                               serving_size)
    logging.info('Serving request for %s from %s [%d, %d) length: %d',
                 self.path, self.serving_payload, serving_start + start_range,
                 serving_start + end_range, end_range - start_range)

    self.send_header('Accept-Ranges', 'bytes')
    self.send_header('Content-Range',
                     'bytes ' + str(start_range) + '-' + str(end_range - 1) +
                     '/' + str(end_range - start_range))
    self.send_header('Content-Length', end_range - start_range)

    stat = os.fstat(f.fileno())
    self.send_header('Last-Modified', self.date_time_string(stat.st_mtime))
    self.send_header('Content-type', 'application/octet-stream')
    self.end_headers()

    f.seek(serving_start + start_range)
    CopyFileObjLength(f, self.wfile, copy_length=end_range -
                      start_range, speed_limit=self.speed_limit)


class ServerThread(threading.Thread):
  """A thread for serving HTTP requests."""

  def __init__(self, ota_filename, serving_range, speed_limit):
    threading.Thread.__init__(self)
    # serving_payload and serving_range are class attributes and the
    # UpdateHandler class is instantiated with every request.
    UpdateHandler.serving_payload = ota_filename
    UpdateHandler.serving_range = serving_range
    UpdateHandler.speed_limit = speed_limit
    self._httpd = BaseHTTPServer.HTTPServer(('127.0.0.1', 0), UpdateHandler)
    self.port = self._httpd.server_port

  def run(self):
    try:
      self._httpd.serve_forever()
    except (KeyboardInterrupt, socket.error):
      pass
    logging.info('Server Terminated')

  def StopServer(self):
    self._httpd.shutdown()
    self._httpd.socket.close()


def StartServer(ota_filename, serving_range, speed_limit):
  t = ServerThread(ota_filename, serving_range, speed_limit)
  t.start()
  return t


def AndroidUpdateCommand(ota_filename, secondary, payload_url, extra_headers):
  """Return the command to run to start the update in the Android device."""
  ota = AndroidOTAPackage(ota_filename, secondary)
  headers = ota.properties
  headers += b'USER_AGENT=Dalvik (something, something)\n'
  headers += b'NETWORK_ID=0\n'
  headers += extra_headers.encode()

  return ['update_engine_client', '--update', '--follow',
          '--payload=%s' % payload_url, '--offset=%d' % ota.offset,
          '--size=%d' % ota.size, '--headers="%s"' % headers.decode()]


class AdbHost(object):
  """Represents a device connected via ADB."""

  def __init__(self, device_serial=None):
    """Construct an instance.

    Args:
        device_serial: options string serial number of attached device.
    """
    self._device_serial = device_serial
    self._command_prefix = ['adb']
    if self._device_serial:
      self._command_prefix += ['-s', self._device_serial]

  def adb(self, command, timeout_seconds: float = None):
    """Run an ADB command like "adb push".

    Args:
      command: list of strings containing command and arguments to run

    Returns:
      the program's return code.

    Raises:
      subprocess.CalledProcessError on command exit != 0.
    """
    command = self._command_prefix + command
    logging.info('Running: %s', ' '.join(str(x) for x in command))
    p = subprocess.Popen(command, universal_newlines=True)
    p.wait(timeout_seconds)
    return p.returncode

  def adb_output(self, command):
    """Run an ADB command like "adb push" and return the output.

    Args:
      command: list of strings containing command and arguments to run

    Returns:
      the program's output as a string.

    Raises:
      subprocess.CalledProcessError on command exit != 0.
    """
    command = self._command_prefix + command
    logging.info('Running: %s', ' '.join(str(x) for x in command))
    return subprocess.check_output(command, universal_newlines=True)


def PushMetadata(dut, otafile, metadata_path):
  header_format = ">4sQQL"
  with tempfile.TemporaryDirectory() as tmpdir:
    with zipfile.ZipFile(otafile, "r") as zfp:
      extracted_path = os.path.join(tmpdir, "payload.bin")
      with zfp.open("payload.bin") as payload_fp, \
              open(extracted_path, "wb") as output_fp:
        # Only extract the first |data_offset| bytes from the payload.
        # This is because allocateSpaceForPayload only needs to see
        # the manifest, not the entire payload.
        # Extracting the entire payload works, but is slow for full
        # OTA.
        header = payload_fp.read(struct.calcsize(header_format))
        magic, major_version, manifest_size, metadata_signature_size = struct.unpack(header_format, header)
        assert magic == b"CrAU", "Invalid magic {}, expected CrAU".format(magic)
        assert major_version == 2, "Invalid major version {}, only version 2 is supported".format(major_version)
        output_fp.write(header)
        output_fp.write(payload_fp.read(manifest_size + metadata_signature_size))

      return dut.adb([
          "push",
          extracted_path,
          metadata_path
      ]) == 0


def ParseSpeedLimit(arg: str) -> int:
  arg = arg.strip().upper()
  if not re.match(r"\d+[KkMmGgTt]?", arg):
    raise argparse.ArgumentError(
        "Wrong speed limit format, expected format is number followed by unit, such as 10K, 5m, 3G (case insensitive)")
  unit = 1
  if arg[-1].isalpha():
    if arg[-1] == "K":
      unit = 1024
    elif arg[-1] == "M":
      unit = 1024 * 1024
    elif arg[-1] == "G":
      unit = 1024 * 1024 * 1024
    elif arg[-1] == "T":
      unit = 1024 * 1024 * 1024 * 1024
    else:
      raise argparse.ArgumentError(
          f"Unsupported unit for download speed: {arg[-1]}, supported units are K,M,G,T (case insensitive)")
  return int(float(arg[:-1]) * unit)


def main():
  parser = argparse.ArgumentParser(description='Android A/B OTA helper.')
  parser.add_argument('otafile', metavar='PAYLOAD', type=str,
                      help='the OTA package file (a .zip file) or raw payload \
                      if device uses Omaha.')
  parser.add_argument('--file', action='store_true',
                      help='Push the file to the device before updating.')
  parser.add_argument('--no-push', action='store_true',
                      help='Skip the "push" command when using --file')
  parser.add_argument('-s', type=str, default='', metavar='DEVICE',
                      help='The specific device to use.')
  parser.add_argument('--no-verbose', action='store_true',
                      help='Less verbose output')
  parser.add_argument('--public-key', type=str, default='',
                      help='Override the public key used to verify payload.')
  parser.add_argument('--extra-headers', type=str, default='',
                      help='Extra headers to pass to the device.')
  parser.add_argument('--secondary', action='store_true',
                      help='Update with the secondary payload in the package.')
  parser.add_argument('--no-slot-switch', action='store_true',
                      help='Do not perform slot switch after the update.')
  parser.add_argument('--no-postinstall', action='store_true',
                      help='Do not execute postinstall scripts after the update.')
  parser.add_argument('--allocate-only', action='store_true',
                      help='Allocate space for this OTA, instead of actually \
                        applying the OTA.')
  parser.add_argument('--verify-only', action='store_true',
                      help='Verify metadata then exit, instead of applying the OTA.')
  parser.add_argument('--no-care-map', action='store_true',
                      help='Do not push care_map.pb to device.')
  parser.add_argument('--perform-slot-switch', action='store_true',
                      help='Perform slot switch for this OTA package')
  parser.add_argument('--perform-reset-slot-switch', action='store_true',
                      help='Perform reset slot switch for this OTA package')
  parser.add_argument('--wipe-user-data', action='store_true',
                      help='Wipe userdata after installing OTA')
  parser.add_argument('--vabc-none', action='store_true',
                      help='Set Virtual AB Compression algorithm to none, but still use Android COW format')
  parser.add_argument('--disable-vabc', action='store_true',
                      help='Option to enable or disable vabc. If set to false, will fall back on A/B')
  parser.add_argument('--enable-threading', action='store_true',
                      help='Enable multi-threaded compression for VABC')
  parser.add_argument('--disable-threading', action='store_true',
                      help='Disable multi-threaded compression for VABC')
  parser.add_argument('--batched-writes', action='store_true',
                      help='Enable batched writes for VABC')
  parser.add_argument('--speed-limit', type=str,
                      help='Speed limit for serving payloads over HTTP. For '
                      'example: 10K, 5m, 1G, input is case insensitive')

  args = parser.parse_args()
  if args.speed_limit:
    args.speed_limit = ParseSpeedLimit(args.speed_limit)

  logging.basicConfig(
      level=logging.WARNING if args.no_verbose else logging.INFO)

  start_time = time.perf_counter()

  dut = AdbHost(args.s)

  server_thread = None
  # List of commands to execute on exit.
  finalize_cmds = []
  # Commands to execute when canceling an update.
  cancel_cmd = ['shell', 'su', '0', 'update_engine_client', '--cancel']
  # List of commands to perform the update.
  cmds = []

  help_cmd = ['shell', 'su', '0', 'update_engine_client', '--help']

  metadata_path = "/data/ota_package/metadata"
  if args.allocate_only:
    with zipfile.ZipFile(args.otafile, "r") as zfp:
      headers = zfp.read("payload_properties.txt").decode()
    if PushMetadata(dut, args.otafile, metadata_path):
      dut.adb([
          "shell", "update_engine_client", "--allocate",
          "--metadata={} --headers='{}'".format(metadata_path, headers)])
    # Return 0, as we are executing ADB commands here, no work needed after
    # this point
    return 0
  if args.verify_only:
    if PushMetadata(dut, args.otafile, metadata_path):
      dut.adb([
          "shell", "update_engine_client", "--verify",
          "--metadata={}".format(metadata_path)])
    # Return 0, as we are executing ADB commands here, no work needed after
    # this point
    return 0
  if args.perform_slot_switch:
    assert PushMetadata(dut, args.otafile, metadata_path)
    dut.adb(["shell", "update_engine_client",
            "--switch_slot=true", "--metadata={}".format(metadata_path), "--follow"])
    return 0
  if args.perform_reset_slot_switch:
    assert PushMetadata(dut, args.otafile, metadata_path)
    dut.adb(["shell", "update_engine_client",
            "--switch_slot=false", "--metadata={}".format(metadata_path)])
    return 0

  if args.no_slot_switch:
    args.extra_headers += "\nSWITCH_SLOT_ON_REBOOT=0"
  if args.no_postinstall:
    args.extra_headers += "\nRUN_POST_INSTALL=0"
  if args.wipe_user_data:
    args.extra_headers += "\nPOWERWASH=1"
  if args.vabc_none:
    args.extra_headers += "\nVABC_NONE=1"
  if args.disable_vabc:
    args.extra_headers += "\nDISABLE_VABC=1"
  if args.enable_threading:
    args.extra_headers += "\nENABLE_THREADING=1"
  elif args.disable_threading:
    args.extra_headers += "\nENABLE_THREADING=0"
  if args.batched_writes:
    args.extra_headers += "\nBATCHED_WRITES=1"

  with zipfile.ZipFile(args.otafile) as zfp:
    CARE_MAP_ENTRY_NAME = "care_map.pb"
    if CARE_MAP_ENTRY_NAME in zfp.namelist() and not args.no_care_map:
      # Need root permission to push to /data
      dut.adb(["root"])
      with tempfile.NamedTemporaryFile() as care_map_fp:
        care_map_fp.write(zfp.read(CARE_MAP_ENTRY_NAME))
        care_map_fp.flush()
        dut.adb(["push", care_map_fp.name,
                "/data/ota_package/" + CARE_MAP_ENTRY_NAME])

  if args.file:
    # Update via pushing a file to /data.
    device_ota_file = os.path.join(OTA_PACKAGE_PATH, 'debug.zip')
    payload_url = 'file://' + device_ota_file
    if not args.no_push:
      data_local_tmp_file = '/data/local/tmp/debug.zip'
      cmds.append(['push', args.otafile, data_local_tmp_file])
      cmds.append(['shell', 'su', '0', 'mv', data_local_tmp_file,
                   device_ota_file])
      cmds.append(['shell', 'su', '0', 'chcon',
                   'u:object_r:ota_package_file:s0', device_ota_file])
    cmds.append(['shell', 'su', '0', 'chown', 'system:cache', device_ota_file])
    cmds.append(['shell', 'su', '0', 'chmod', '0660', device_ota_file])
  else:
    # Update via sending the payload over the network with an "adb reverse"
    # command.
    payload_url = 'http://127.0.0.1:%d/payload' % DEVICE_PORT
    serving_range = (0, os.stat(args.otafile).st_size)
    server_thread = StartServer(args.otafile, serving_range, args.speed_limit)
    cmds.append(
        ['reverse', 'tcp:%d' % DEVICE_PORT, 'tcp:%d' % server_thread.port])
    finalize_cmds.append(['reverse', '--remove', 'tcp:%d' % DEVICE_PORT])

  if args.public_key:
    payload_key_dir = os.path.dirname(PAYLOAD_KEY_PATH)
    cmds.append(
        ['shell', 'su', '0', 'mount', '-t', 'tmpfs', 'tmpfs', payload_key_dir])
    # Allow adb push to payload_key_dir
    cmds.append(['shell', 'su', '0', 'chcon', 'u:object_r:shell_data_file:s0',
                 payload_key_dir])
    cmds.append(['push', args.public_key, PAYLOAD_KEY_PATH])
    # Allow update_engine to read it.
    cmds.append(['shell', 'su', '0', 'chcon', '-R', 'u:object_r:system_file:s0',
                 payload_key_dir])
    finalize_cmds.append(['shell', 'su', '0', 'umount', payload_key_dir])

  try:
    # The main update command using the configured payload_url.
    update_cmd = AndroidUpdateCommand(args.otafile, args.secondary,
                                        payload_url, args.extra_headers)
    cmds.append(['shell', 'su', '0'] + update_cmd)

    for cmd in cmds:
      dut.adb(cmd)
  except KeyboardInterrupt:
    dut.adb(cancel_cmd)
  finally:
    if server_thread:
      server_thread.StopServer()
    for cmd in finalize_cmds:
      dut.adb(cmd, 5)

  logging.info('Update took %.3f seconds', (time.perf_counter() - start_time))
  return 0


if __name__ == '__main__':
  sys.exit(main())
