# Copyright 2020 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Runs a single action remotely with RBE."""

import argparse
import os
import rbe
import subprocess
import sys


def main():
  parser = argparse.ArgumentParser(
      description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
  parser.add_argument(
      '--command',
      default='echo RBE check successful.',
      help='Command to run remotely with RBE.')
  parser.add_argument(
      '--print', '-p',
      action='store_true',
      help='Prints the executed commands')
  args = parser.parse_args()

  env = []
  cleanup = rbe.setup(env, sys.stdout if args.print else subprocess.DEVNULL)
  src_root = os.path.normpath(
      os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../../..'))
  env = rbe.env_array_to_dict(rbe.prepare_env(env))
  env['PATH'] = os.getenv('PATH')
  for d in ['FLAG_log_dir', 'RBE_output_dir', 'RBE_proxy_log_dir']:
    env[d] = '/tmp'  # We want the logs in /tmp instead of out.
  try:
    # Bootstrap the RBE proxy.
    bootstrap_cmd = rbe.get_nsjail_bin_wrapper() + \
      [os.path.join(rbe.TOOLS_DIR, 'bootstrap')]
    shell_env = ' '.join(['%s=%s' % (k,v) for k, v in env.items()])
    if args.print:
      print('Bootstrap RBE reproxy:')
      print('cd ' + src_root)
      print('%s %s' % (shell_env, ' '.join(bootstrap_cmd)))
    subprocess.check_call(
        bootstrap_cmd, env=env, cwd=src_root, stdout=subprocess.DEVNULL)
    # Execute the remote command.
    rewrapper_cmd = rbe.get_nsjail_bin_wrapper() + [
        os.path.join(rbe.TOOLS_DIR, 'rewrapper'),
        '--platform=container-image=docker://gcr.io/androidbuild-re-dockerimage/android-build-remoteexec-image@sha256:582efb38f0c229ea39952fff9e132ccbe183e14869b39888010dacf56b360d62', \
        '--labels=type=tool',
        '--exec_strategy=remote',
        '--dial_timeout=5s',
        '--exec_root=' + src_root,
        '--',
    ] + args.command.split()
    if args.print:
      print('Run remote command with RBE:')
      print('%s %s' % (shell_env, ' '.join(rewrapper_cmd)))
    subprocess.check_call(rewrapper_cmd, env=env, cwd=src_root)
  finally:
    # Shut down the RBE proxy.
    if args.print:
      print('RBE proxy shutdown:')
      print('killall reproxy')
    subprocess.call(
        ['killall', 'reproxy'],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL)
    cleanup()


if __name__ == '__main__':
  main()
