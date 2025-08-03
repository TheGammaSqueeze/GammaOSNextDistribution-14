#!/usr/bin/python
#
# Copyright (C) 2023 The Android Open Source Project
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

"""Parse ABI functions declarations and generate trampolines.

Input files are C++ files with very limited syntax. They can contain function
declarations and comments only.

Function declaration should not specify parameter names, just parameter types.

Comments should be C++-comments and they should appear before or after function
declaration but not inside it.

Special comments define attributes of the subsequent declaration:
- // BERBERIS_CUSTOM_TRAMPOLINE(<name>)
  This declaration already has a custom hand-coded trampoline <name>;

Unrecognized comment discards all preceding attributes, so if declaration with
attributes is commented out, its attributes are not attached to the next
declaration.
"""

import json
import sys


_ARG = {
    'JNIEnv *': {
        'init': '  JNIEnv* arg_{index} = ToHostJNIEnv(arg_env);'
    },
    '...': {
        'init': """\
  std::vector<jvalue> arg_vector = ConvertVAList(
    arg_0, arg_{arg_va_method_id}, GuestParamsValues<PFN_callee>(state));
  jvalue* arg_{index} = &arg_vector[0];""",
    },
  # Note: we couldn't teach GuestParams class to distinguish Va_list because on
  # ARM it's simply a char*, not a distint type.
    'va_list': {
        'init': """\
  std::vector<jvalue> arg_vector = ConvertVAList(
    arg_0, arg_{arg_va_method_id}, ToGuestAddr(arg_va));
  jvalue* arg_{index} = &arg_vector[0];""",
    },
}


def _set_callee(decl):
  param_types = decl['param_types']
  # TODO(eaeltsin): introduce attributes to
  # - indicate this is a virtual method and not a global function
  # - specify custom thunk
  # - specify how to convert variable arguments list
  # instead of the code below.
  if 'jmethodID' in param_types and 'va_list' in param_types:
    # NewObjectV, CallObjectMethodV, ...
    assert decl['name'].endswith('V')
    decl['callee'] = '(arg_0->functions)->%sA' % decl['name'][:-1]
    decl['arg_va_method_id'] = param_types.index('jmethodID')
  elif 'jmethodID' in param_types and '...' in param_types:
    # NewObject, CallObjectMethod, ...
    decl['callee'] = '(arg_0->functions)->%sA' % decl['name']
    decl['arg_va_method_id'] = param_types.index('jmethodID')
  else:
    decl['callee'] = '(arg_0->functions)->%s' % decl['name']


def _print_jni_call(out, args, decl):
  # TODO(eaeltsin): implement printing using printf-style LOG_JNI!
  pass


def _print_jni_result(out, res, decl):
  # TODO(eaeltsin): implement printing using printf-style LOG_JNI!
  pass


def _gen_trampoline(out, decl):
  _set_callee(decl)

  args = decl['param_types']

  if '...' in args:
    assert args[-1] == '...'
    arglist = ['arg_%d' %i for i in range(1, len(args) - 1)]
  elif 'va_list' in args:
    assert args[-1] == 'va_list'
    arglist = ['arg_%d' %i for i in range(1, len(args) - 1)] + ['arg_va']
  else:
    arglist = ['arg_%d' %i for i in range(1, len(args))]
  assert args[0] == 'JNIEnv *'
  decl['arglist'] = ', '.join(['arg_env'] + arglist)

  print("""
void DoTrampoline_JNIEnv_{name}(
    HostCode /* callee */,
    ProcessState* state) {{
  using PFN_callee = decltype(std::declval<JNIEnv>().functions->{name});
  auto [{arglist}] = GuestParamsValues<PFN_callee>(state);""".format(**decl), file=out)

  for i, type in enumerate(args):
    if type in _ARG:
      arg = _ARG[type]
      print(arg['init'].format(index=i, **decl), file=out)

  _print_jni_call(out, args, decl)
  if decl['return_type'] == 'void':
    print(' {callee}('.format(**decl), file=out)
  else:
    print('  auto&& [ret] = GuestReturnReference<PFN_callee>(state);', file=out)
    print('  ret = {callee}('.format(**decl), file=out)
  for i in range(len(args) - 1):
    print('      arg_%d,' % i, file=out)
  print('      arg_%d);' % (len(args) - 1), file=out)
  if decl['return_type'] != 'void':
    _print_jni_result(out, 'ret', decl)

  print('}', file=out)


def main(argv):
  # Usage: gen_jni_trampolines.py <gen-header> <abi-def>
  header_name = argv[1]
  abi_def_name = argv[2]

  with open(abi_def_name) as json_file:
    decls = json.load(json_file)

  out = open(header_name, 'w')

  for decl in decls:
    if 'trampoline' not in decl:
      _gen_trampoline(out, decl)

  print("""
void WrapJNIEnv(void* jni_env) {
  HostCode* jni_vtable = *reinterpret_cast<HostCode**>(jni_env);
  // jni_vtable[0] is NULL
  // jni_vtable[1] is NULL
  // jni_vtable[2] is NULL
  // jni_vtable[3] is NULL""", file=out)
  for i in range(len(decls)):
    decl = decls[i]
    print("""
  WrapHostFunctionImpl(
    jni_vtable[%d],
    DoTrampoline_JNIEnv_%s,
    "JNIEnv::%s");""" % (4 + i, decl['name'], decl['name']), file=out)
  print('}', file=out)


if __name__ == '__main__':
  sys.exit(main(sys.argv))
