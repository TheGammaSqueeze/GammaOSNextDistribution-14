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
# limitations under the License.#

import argparse

import filecmp
import os
import shutil
import subprocess
import sys
import tempfile


class ProxyGenerator:

  def __init__(self):
    self.android_api_root = os.path.abspath('%s/' %
                                            os.path.dirname(__file__))
    self.android_tree_root = os.path.abspath('%s/../../../../' %
                                             self.android_api_root)

    self.dwarf_reader = '%s/out/host/linux-x86/bin/dwarf_reader' % self.android_tree_root
    self.proxy_generator = '%s/gen_known_trampolines.py' % self.android_api_root

    self.arches = {
        'arm': '%s/out/target/product/generic_arm64/symbols/%s/lib/%s.so',
        'arm64': '%s/out/target/product/generic_arm64/symbols/%s/lib64/%s.so',
        'riscv64': '%s/out/target/product/generic_arm64/symbols/%s/lib64/%s.so',
        'x86': '%s/out/target/product/generic_x86_64/symbols/%s/lib/%s.so',
        'x86_64': '%s/out/target/product/generic_x86_64/symbols/%s/lib64/%s.so',
    }

    self.proxy_libraries = {}

    self._init_lib('libaaudio')
    self._init_lib('libamidi')
    self._init_lib('libandroid')
    self._init_lib('libandroid_runtime')
    self._init_lib('libbinder_ndk')

    self._init_lib('libc', gen_json=False, stubs_ext='cpp')

    self._init_lib('libcamera2ndk')
    self._init_lib('libEGL')

    # TODO(b/110068220) generate json for these libraries.
    self._init_lib('libGLESv1_CM', gen_json=False)
    self._init_lib('libGLESv2', gen_json=False)
    self._init_lib('libGLESv3', gen_json=False)

    self._init_lib('libjnigraphics')
    self._init_lib('libmediandk')
    self._init_lib('libnativehelper', location='apex/com.android.art')
    self._init_lib('libnativewindow')
    self._init_lib('libneuralnetworks', location='apex/com.android.neuralnetworks')
    self._init_lib('libOpenMAXAL')
    self._init_lib('libOpenSLES')
    self._init_lib('libvulkan')

    # The guest copy of the library should never be used. Note that
    # JniStaticTest#test_linker_namespaces still checks if apps can load this
    # and this is why we want to preserve an empty copy of the library.
    self._init_lib('libwebviewchromium_plat_support', gen_json=False)

  def _init_lib(self, name, **kwargs):
    assert name not in self.proxy_libraries
    self.proxy_libraries.setdefault(name, {
        'location': 'system',
        'gen_json': True,
        'stubs_ext': 'cc',
      }).update(kwargs)

  def _build(self, product, target=''):
    p = subprocess.Popen(
        'build/soong/soong_ui.bash --make-mode -j TARGET_BUILD_VARIANT=userdebug TARGET_PRODUCT=%s %s'
        % (product, target),
        cwd=self.android_tree_root,
        shell=True)

    retcode = p.wait()
    if retcode != 0:
      raise Exception('Unable to build %s %s' % (product, target))

  def build_source_libraries(self):
    self._build('aosp_arm64')
    self._build('aosp_x86_64')
    self._build('aosp_riscv64')

  def build_nogrod_dwarf_reader(self):
    self._build('aosp_x86_64', 'dwarf_reader')

  def generate_json_files_for_arch(self, arch, path_template, library):
    library_path = path_template % (self.android_tree_root,
                                    self.proxy_libraries[library]['location'], library)

    tmp_output = tempfile.mkstemp()
    p = subprocess.Popen([self.dwarf_reader, library_path],
                         stdout=tmp_output[0],
                         shell=False)

    return arch, library_path, p, tmp_output,

  def generate_json_files(self, library):
    if library not in self.proxy_libraries:
      print((
          'Skipping json api files generation for %s - '
          'it is not in the list of known proxy_libraries') % library)
      return
    if not self.proxy_libraries[library]['gen_json']:
      print((
          'Skipping json api files generation for %s - '
          'it has hand written json api files') % library)
      return

    processes = []

    for arch, path_template in self.arches.items():
      processes.append(
          self.generate_json_files_for_arch(arch, path_template, library))

    for proc in processes:
      arch, library_path, p, _ = proc
      if (p.wait() != 0):
        raise Exception('Error while generating %s api for %s' % (arch, library_path))

    for proc in processes:
      arch, _, _, tmp_output = proc
      json_output = ('%s/%s/proxy/api_%s.json' % (self.android_api_root, library, arch))

      if not os.path.isfile(json_output) or not filecmp.cmp(
          tmp_output[1], json_output, shallow=False):
        shutil.move(tmp_output[1], json_output)

  def generate_proxy_library(self,
                             library,
                             guest_json_suffix,
                             host_json_suffix,
                             custom_json_suffix,
                             trampoline_suffix,
                             stubs_suffix,
                             verbose=False):
    if library not in self.proxy_libraries:
      print((
          'Skipping trampoline and stub generation for %s - '
          'it is not in the list of known proxy_libraries') % library)
      return

    inputs = [
        library,
        '%s/%s/proxy/api_%s.json' %
        (self.android_api_root, library, guest_json_suffix),
        '%s/%s/proxy/api_%s.json' %
        (self.android_api_root, library, host_json_suffix),
        '%s/%s/proxy/custom_trampolines_%s.json' %
        (self.android_api_root, library, custom_json_suffix),
    ]

    tmp_output_trampolines = tempfile.mkstemp()
    tmp_output_stubs = tempfile.mkstemp()
    if verbose:
      inputs += ['--verbose']

    trampoline_out = '%s/%s/proxy/trampolines_%s-inl.h' % (
        self.android_api_root, library, trampoline_suffix)

    print('Generating %s trampolines for %s, logs: %s' % (
        trampoline_suffix, library, tmp_output_trampolines[1]))
    p_trampolines = subprocess.Popen(
        [self.proxy_generator, '--trampolines', trampoline_out] + inputs,
        stdout=tmp_output_trampolines[0],
        stderr=tmp_output_trampolines[0],
        shell=False)
    if p_trampolines.wait() != 0:
      raise Exception('Error while generating trampolines for %s' % library)

    info = self.proxy_libraries[library]
    stubs_out = '%s/%s/stubs_%s.%s' % (
        self.android_api_root, library, stubs_suffix, info['stubs_ext'])

    print('Generating %s stubs for %s, logs: %s' % (stubs_suffix, library,
                                                    tmp_output_stubs[1]))
    p_stubs = subprocess.Popen(
        [self.proxy_generator, '--stubs', stubs_out] + inputs,
        stdout=tmp_output_stubs[0],
        stderr=tmp_output_stubs[0],
        shell=False)
    if p_stubs.wait() != 0:
      raise Exception('Error while generating stubs for %s' % library)


def main(argv):
  parser = argparse.ArgumentParser(description='(re)generate proxy libraries')
  parser.add_argument('--json', action='store_true', help='generate json files')
  parser.add_argument(
      '--trampolines',
      action='store_true',
      help='generate trampolines and/or stubs (whichever is necessary)')
  parser.add_argument(
      '--skip_build',
      action='store_true',
      help='skip build step for libraries and nogrod dwarf reader')
  parser.add_argument(
      '-v',
      '--verbose',
      action='store_true',
      help='generate verbose output when calling gen_known_trampolines')
  parser.add_argument(
      'libraries',
      metavar='library',
      nargs='*',
      help=('The name of a library (use libc for libc.so), '
            'if none specified will generate all proxy libraries'))

  args = parser.parse_args()

  libraries = args.libraries

  generator = ProxyGenerator()

  # If libraries are not specified - generate everything
  if not libraries:
    libraries = generator.proxy_libraries.keys()

  if args.json and not args.skip_build:
    print('Building x86/x86_64/arm/arm64/riscv64 versions of proxy libraries ... ')
    generator.build_source_libraries()
    print('Building nogrod dwarf reader ... ')
    generator.build_nogrod_dwarf_reader()

  for lib in libraries:
    if args.json:
      print('Generating json files for %s ...' % lib)
      generator.generate_json_files(lib)
    if args.trampolines:
      print('Generating proxy library trampolines and stubs for %s ... ' % lib)
      generator.generate_proxy_library(
          lib,
          guest_json_suffix='arm',
          host_json_suffix='x86',
          custom_json_suffix='arm_to_x86',
          trampoline_suffix='arm_to_x86',
          stubs_suffix='arm',
          verbose=args.verbose)
      generator.generate_proxy_library(
          lib,
          guest_json_suffix='arm64',
          host_json_suffix='x86_64',
          custom_json_suffix='arm64_to_x86_64',
          trampoline_suffix='arm64_to_x86_64',
          stubs_suffix='arm64',
          verbose=args.verbose)
      generator.generate_proxy_library(
          lib,
          guest_json_suffix='riscv64',
          host_json_suffix='x86_64',
          custom_json_suffix='riscv64_to_x86_64',
          trampoline_suffix='riscv64_to_x86_64',
          stubs_suffix='riscv64',
          verbose=args.verbose)



if __name__ == '__main__':
  sys.exit(main(sys.argv))
