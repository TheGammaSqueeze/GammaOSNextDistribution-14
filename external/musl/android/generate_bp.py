#!/usr/bin/env python3

# This replicates the logic in Makefile to produce lists of generic
# and architecture specific sources, then writes them into sources.bp
# as cc_defaults modules for use by the Android.bp file.

import copy
import glob
import os.path

BP_TO_MUSL_ARCHES = {
  'arm': 'arm',
  'arm64': 'aarch64',
  'x86': 'i386',
  'x86_64': 'x86_64',
}

SRC_DIRS = (
  'src/*',
  'src/malloc/mallocng',
)
COMPAT32_SRC_DIRS = (
  'compat/time32',
)
OPT_DIRS = (
  'src/internal',
  'src/malloc',
  'src/string',
)
NOSSP_SRCS = (
  'src/env/__libc_start_main.c',
  'src/env/__init_tls.c',
  'src/env/__stack_chk_fail.c',
  'src/thread/__set_thread_area.c',
  'src/string/memset.c',
  'src/string/memcpy.c',
)
LDSO_SRC_DIRS = (
  'ldso',
)
CRT_SRCS = (
  'crt/crt1.c',
  'crt/crti.c',
  'crt/crtn.c',
  'crt/rcrt1.c',
  'crt/Scrt1.c',
)

def overridden_base_src(arch, src):
  src = src.replace('/'+arch+'/','/')
  if src.endswith('.s') or src.endswith('.S'):
    src = src[:-2]+'.c'
  return src

def overridden_base_srcs(arch, srcs):
  return [overridden_base_src(arch, src) for src in srcs]

def glob_dirs(*args):
  """glob_dirs takes a list of parts of glob patterns, some of which may be
  lists or tuples, expands the lists and tuples into every combination
  of patterns, and returns the glob expansion of the patterns."""
  ret = []
  for i, arg in enumerate(args):
    if type(arg) is list or type(arg) is tuple:
      for entry in arg:
        new_args = list(args)
        new_args[i] = entry
        ret += glob_dirs(*new_args)
      return ret
  return sorted(glob.glob('/'.join(args)))

def force_exists(files):
  """force_exists raises an exception if any of the input files are missing
  and returns the sorted list of inputs files"""
  files = sorted(files)
  glob_files = glob_dirs(files)
  if glob_files != files:
    raise(Exception('failed to find sources: %s' % ', '.join(['"'+x+'"' for x in list(set(files) - set(glob_files))])))
  return files

def files_to_arch_files(base_files, arch):
  files=[]
  for f in base_files:
    base, ext = os.path.splitext(os.path.basename(f))
    pattern = os.path.join(os.path.dirname(f), arch, base+'.[csS]')
    glob_files = glob.glob(pattern)
    if len(glob_files) > 1:
      raise(Exception('expected at most one file for %s, found %s' % (pattern, glob_files)))
    elif glob_files:
      files.append(glob_files[0])

  return files

class SourceSet(object):
  def __init__(self, *, dirs=[], files=[]):
    self.srcs = self._srcs(dirs, files)
    self.arch_srcs, self.arch_exclude_srcs = self._arch_srcs(dirs, files)

  def _srcs(self, dirs, files):
    return glob_dirs(dirs, '*.c') + force_exists(files)

  def _arch_srcs(self, dirs, files):
    srcs = {}
    exclude_srcs = {}
    for bp_arch, musl_arch in BP_TO_MUSL_ARCHES.items():
      arch_srcs = glob_dirs(dirs, musl_arch, '*.[csS]')
      arch_srcs += files_to_arch_files(files, musl_arch)
      arch_exclude_srcs = overridden_base_srcs(musl_arch, arch_srcs)
      if arch_srcs:
        srcs[bp_arch] = arch_srcs
      if arch_exclude_srcs:
        exclude_srcs[bp_arch] = arch_exclude_srcs
    return srcs, exclude_srcs

  def intersect(self, other):
    diff = self.subtract(other)
    return self.subtract(diff)

  def subtract(self, other):
    ret = copy.deepcopy(self)
    ret.srcs = sorted(list(set(ret.srcs) - set(other.srcs)))
    for bp_arch in BP_TO_MUSL_ARCHES:
      ret.arch_srcs[bp_arch] = sorted(list(set(ret.arch_srcs[bp_arch]) - set(other.arch_srcs[bp_arch])))
      ret.arch_exclude_srcs[bp_arch] = sorted(list(set(ret.arch_exclude_srcs[bp_arch]) - set(other.arch_exclude_srcs[bp_arch])))
    return ret

  def union(self, other):
    ret = copy.deepcopy(self)
    ret.srcs = sorted(ret.srcs + other.srcs)
    for bp_arch in BP_TO_MUSL_ARCHES:
      ret.arch_srcs[bp_arch] = sorted(ret.arch_srcs[bp_arch] + other.arch_srcs[bp_arch])
      ret.arch_exclude_srcs[bp_arch] = sorted(ret.arch_exclude_srcs[bp_arch] + other.arch_exclude_srcs[bp_arch])
    return self

class Blueprint(object):
  def __init__(self, out):
    self.out = out

  def PrintHeader(self):
    self.out.write(
"""// Copyright (C) 2021 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// This file is created by generate_bp.py. Do not edit manually.
""")

  def PrintDefaults(self, name, srcs):
    """Print a cc_defaults section from a list of source files, architecture
    specific source files, and source files replaced by architecture specific
    source files"""
    self.out.write('\n')
    self.out.write('cc_defaults {\n')
    self.out.write('    name: "%s",\n' % name)
    self.out.write('    srcs: [\n')
    for f in srcs.srcs:
      self.out.write('        "%s",\n' % f)
    self.out.write('    ],\n')

    if srcs.arch_srcs or srcs.arch_exclude_srcs:
      self.out.write('    arch: {\n')
      for arch in BP_TO_MUSL_ARCHES.keys():
        if srcs.arch_srcs[arch] or srcs.arch_exclude_srcs[arch]:
          self.out.write('        %s: {\n' % arch)
          if srcs.arch_srcs[arch]:
            self.out.write('            srcs: [\n')
            for f in srcs.arch_srcs[arch]:
              self.out.write('                "%s",\n' % f)
            self.out.write('            ],\n')
          if srcs.arch_exclude_srcs[arch]:
            self.out.write('            exclude_srcs: [\n')
            for f in srcs.arch_exclude_srcs[arch]:
              self.out.write('                "%s",\n' % f)
            self.out.write('            ],\n')
          self.out.write('        },\n')
      self.out.write('    },\n')

    self.out.write('}\n')


libc = SourceSet(dirs=SRC_DIRS)
compat32 = SourceSet(dirs=COMPAT32_SRC_DIRS)
opt = SourceSet(dirs=OPT_DIRS)
nossp = SourceSet(files=NOSSP_SRCS)
ldso = SourceSet(dirs=LDSO_SRC_DIRS)

crts = {}
for crt in CRT_SRCS:
  srcs = SourceSet(files=[crt])
  name = os.path.splitext(os.path.basename(crt))[0]
  crts[name] = srcs

libc = libc.subtract(opt).subtract(nossp)
opt_nossp = opt.intersect(nossp)
opt = opt.subtract(opt_nossp)
nossp = nossp.subtract(opt_nossp)

dir_name = os.path.dirname(os.path.realpath(__file__))

with open(os.path.join(dir_name, '..', 'sources.bp'), 'w+') as out:
  bp = Blueprint(out)
  bp.PrintHeader()
  bp.PrintDefaults('libc_musl_sources', libc)
  bp.PrintDefaults('libc_musl_compat32_sources', compat32)
  bp.PrintDefaults('libc_musl_opt_sources', opt)
  bp.PrintDefaults('libc_musl_opt_nossp_sources', opt_nossp)
  bp.PrintDefaults('libc_musl_nossp_sources', nossp)
  bp.PrintDefaults('libc_musl_ldso_sources', ldso)
  for crt in sorted(crts.keys()):
    bp.PrintDefaults('libc_musl_%s_sources' % crt, crts[crt])
