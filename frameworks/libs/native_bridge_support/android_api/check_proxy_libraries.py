#!/usr/bin/env python3
#
# Copyright (C) 2024 The Android Open Source Project
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

import argparse

import filecmp
import multiprocessing
import os
import shutil
import subprocess
import sys
import tempfile


def run_and_compare(args, output_path, source_path):
  p = subprocess.Popen(args, shell=False)
  if p.wait() != 0:
    raise Exception('Error running %s', args)

  return filecmp.cmp(output_path, source_path, shallow=False)


class ProxyChecker:
  native_bridge_support_root = os.path.abspath('%s/' %
                                                os.path.dirname(__file__))
  proxy_generator = '%s/gen_known_trampolines.py' % native_bridge_support_root

  proxy_libraries = [
      'libaaudio',
      'libamidi',
      'libandroid',
      'libandroid_runtime',
      'libbinder_ndk',
      'libc',
      'libcamera2ndk',
      'libEGL',
      'libGLESv1_CM',
      'libGLESv2',
      'libGLESv3',
      'libjnigraphics',
      'libmediandk',
      'libnativehelper',
      'libnativewindow',
      'libneuralnetworks',
      'libOpenMAXAL',
      'libOpenSLES',
      'libvulkan',
      'libwebviewchromium_plat_support',
  ]

  def check_proxy_library(self,
                          library,
                          guest_json_suffix,
                          host_json_suffix,
                          custom_json_suffix,
                          trampoline_suffix,
                          stubs_suffix):
    inputs = []
    guest_json_path = '%s/proxy/api_%s.json' % (library,
                                                         guest_json_suffix)
    host_json_path = '%s/proxy/api_%s.json' % (library,
                                                        host_json_suffix)
    custom_trampolines_path = '%s/proxy/custom_trampolines_%s.json' % (
        library, custom_json_suffix)
    trampoline_path = '%s/proxy/trampolines_%s-inl.h' % (
        library, trampoline_suffix)
    if library == 'libc':
      stubs_path = 'native_bridge_support/%s/stubs_%s.cpp' % (
        library, stubs_suffix)
    else:
      stubs_path = 'native_bridge_support/%s/stubs_%s.cc' % (
        library, stubs_suffix)

    inputs = [
        ('%s/%s' % (self.native_bridge_support_root, guest_json_path)),
        ('%s/%s' % (self.native_bridge_support_root, host_json_path)),
        ('%s/%s' % (self.native_bridge_support_root, custom_trampolines_path)),
    ]
    trampoline_src = '%s/%s' % (self.native_bridge_support_root, trampoline_path)
    stubs_src = 'frameworks/libs/%s' % stubs_path

    return self.check_proxy_library_impl(library, inputs, trampoline_src, stubs_src)

  def check_proxy_library_impl(self, library, inputs, trampoline_src,
                               stubs_src):
    tmp_output_trampolines = tempfile.mkstemp()
    tmp_output_stubs = tempfile.mkstemp()

    try:
      success = run_and_compare([
          self.proxy_generator, '--trampolines', tmp_output_trampolines[1],
          library
      ] + inputs, tmp_output_trampolines[1], trampoline_src)

      success = success and run_and_compare(
          [self.proxy_generator, '--stubs', tmp_output_stubs[1], library] +
          inputs, tmp_output_stubs[1], stubs_src)

      return success
    finally:
      os.unlink(tmp_output_trampolines[1])
      os.unlink(tmp_output_stubs[1])


def check_one_library(checker, library, return_dict):
  return_dict[library + '_arm_to_x86'] = checker.check_proxy_library(
      library,
      guest_json_suffix='arm',
      host_json_suffix='x86',
      custom_json_suffix='arm_to_x86',
      trampoline_suffix='arm_to_x86',
      stubs_suffix='arm')
  return_dict[library + '_arm64_to_x86_64'] = checker.check_proxy_library(
      library,
      guest_json_suffix='arm64',
      host_json_suffix='x86_64',
      custom_json_suffix='arm64_to_x86_64',
      trampoline_suffix='arm64_to_x86_64',
      stubs_suffix='arm64')


def main():
  checker = ProxyChecker()

  libraries = checker.proxy_libraries

  success = True

  out_of_sync_libraries = []

  manager = multiprocessing.Manager()
  return_dict = manager.dict()
  jobs = []

  for lib in libraries:
    p = multiprocessing.Process(
        target=check_one_library, args=(checker, lib, return_dict))
    jobs.append(p)
    p.start()

  for p in jobs:
    p.join()

  for lib in libraries:
    if not return_dict[lib +
                       '_arm_to_x86'] or not return_dict[lib +
                                                         '_arm64_to_x86_64']:
      out_of_sync_libraries.append(lib)
      success = False

  if success:
    return 0

  gen_proxy_library_script = ('%s/gen_proxy_libraries.py' %
                              checker.native_bridge_support_root)

  print(
      'proxy libraries %s are out of sync, please run following command to fix'
      " this issue '%s --trampolines'" %
      (out_of_sync_libraries, gen_proxy_library_script), file=sys.stderr)
  return 1


if __name__ == '__main__':
  sys.exit(main())
