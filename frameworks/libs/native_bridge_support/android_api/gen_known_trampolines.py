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

"""Compare Host API against Guest API.

Generate trampolines for compatible symbols and bad trampolines for others.
"""

import argparse
import json
import sys

import api_analysis


def _get_type_str(guest_api, type_name, is_return_type):
  if type_name == 'void':
    return 'void'

  type = guest_api['types'][type_name]
  kind = type['kind']

  if kind == 'int' or kind == 'char':
    size = int(type['size'])
    # default signed to false for backward compatibility.
    # TODO: Once all json files are updated and have this flag
    # for every int and char - remove the default and fail if 'signed'
    # attribute not provided.
    signed = bool(type.get('signed', 'false'))
    prefix = ''
    if not signed:
      prefix = 'u'
    if size == 8:
      return prefix + 'int8_t'
    if size == 16:
      return prefix + 'int16_t'
    if size == 32:
      return prefix + 'int32_t'
    if size == 64:
      return prefix + 'int64_t'
    raise Exception('%s: unknown integer type size %d' % (type_name, size))

  if kind == 'fp' or kind == 'float':
    size = int(type['size'])
    if size == 32:
      return 'float'
    if size == 64:
      return 'double'
    raise Exception('%s: unknown fp type size %d' % (type_name, size))

  if kind == 'complex':
    size = int(type['size'])
    if size == 32:
      return 'float _Complex'
    if size == 64:
      return 'double _Complex'
    raise Exception('%s: unknown complex type size %d' % (type_name, size))

  # Handle functions.
  if kind == 'function':
    return _get_function_type_str(guest_api, type, 'auto(%s) -> %s')

  # Handle pointers to functions.
  if kind == 'pointer':
    pointee_type = guest_api['types'][type['pointee_type']]
    if pointee_type['kind'] == 'function':
      return _get_function_type_str(guest_api, pointee_type,
                                    'auto(*)(%s) -> %s')

  # Handle pointers and references to objects.
  if kind == 'pointer' or kind == 'reference' or kind == 'rvalue_reference':
    return 'void*'

  if kind == 'const':
    return _get_type_str(guest_api, type['base_type'], is_return_type)

  raise Exception("%s: unknown type kind '%s'" % (type_name, kind))


def _get_function_type_str(guest_api, type, format_string):
  assert type['kind'] == 'function'

  return_type = _get_type_str(guest_api, type['return_type'], True)
  arg_types = ', '.join(
      _get_type_str(guest_api, param_type, False)
      for param_type in type['params'])
  if arg_types == '':
    arg_types = 'void'
  return format_string % (arg_types, return_type)


def _get_function_type_str_from_signature(signature):
  signature_chars = {
      'v': 'void',
      'i': 'int32_t',
      'u': 'uint32_t',
      'l': 'long',
      'p': 'void*',
      'z': 'size_t',
  }

  if len(signature) == 1:
    signature += 'v'
  return 'auto(%s) -> %s' % (', '.join(signature_chars[c] for c in signature[1:]),
                             signature_chars[signature[0]])


def _get_default_trampoline(symbol, guest_api):
  trampoline = 'GetTrampolineFunc<'

  if 'type' in guest_api['symbols'][symbol]:
    if 'signature' in guest_api['symbols'][symbol]:
      raise Exception(('custom signature must not be defined for'
                       ' a symbol with defined type: %s') % symbol)

    type_name = guest_api['symbols'][symbol]['type']
    params_str = _get_type_str(guest_api, type_name, False)
  else:
    custom_signature = guest_api['symbols'][symbol].get('signature', None)
    assert custom_signature
    params_str = _get_function_type_str_from_signature(custom_signature)

  trampoline += params_str
  trampoline += '>()'
  return trampoline


def _generate_trampolines(output_file, guest_api):
  # Table of function trampolines and thunks.
  #
  # Trampoline is used to convert arguments from guest to host, then to call thunk and then
  # to convert result from host to guest. Several trampolines do not call thunks and do the
  # whole work themselves.
  #
  # Thunk gets called from trampoline and implements behavior of Bionic function. If thunk
  # is NULL, host function of the same name is searched with dlsym. If you want function
  # to link but fail if called, use DoBadTrampoline, and thunk will be ignored.
  trampolines = set()
  items = []
  for symbol, descr in guest_api['symbols'].items():
    if (descr['call_method'] == 'default'):
      if (descr['is_compatible']):
        try:
          trampoline = _get_default_trampoline(symbol, guest_api)
        except Exception as err:
          raise Exception('Unable to generate trampoline for %s: %s' %
                          (symbol, err))
        thunk = 'NULL'
      else:
        trampoline = 'DoBadTrampoline'
        thunk = 'DoBadThunk'
    elif (descr['call_method'] == 'custom_trampoline'):
      trampoline = 'DoCustomTrampoline_' + symbol
      thunk = 'DoBadThunk'
    elif (descr['call_method'] == 'custom_trampoline_with_thunk'):
      trampoline = 'DoCustomTrampolineWithThunk_' + symbol
      thunk = symbol
    elif (descr['call_method'] == 'custom_thunk'):
      try:
        trampoline = _get_default_trampoline(symbol, guest_api)
      except Exception as err:
        raise Exception('Unable to generate trampoline for %s: %s' %
                        (symbol, err))
      thunk = descr.get('custom_thunk', 'DoThunk_' + symbol)
    elif (descr['call_method'] == 'ignore'):
      continue
    else:
      assert (descr['call_method'] == 'do_not_call')
      continue
    trampolines.add(trampoline)
    items.append("{\"%s\", %s, reinterpret_cast<void*>(%s)}," % \
        (symbol, trampoline, thunk))

  print('const KnownTrampoline kKnownTrampolines[] = {', file=output_file)
  for item in sorted(items):
    print(item, file=output_file)
  print('};  // kKnownTrampolines', file=output_file)


def _generate_variables(output_file, guest_api):
  items = []
  for symbol, descr in guest_api['symbols'].items():
    if (descr['call_method'] == 'do_not_call'):
      if (descr['is_compatible']):
        if 'type' in descr:
          type_name = descr['type']
          type = guest_api['types'][type_name]
          bit_size = int(type['size'])
          assert bit_size % 8 == 0
          size = bit_size / 8
          items.append("{\"%s\", %d}," % (symbol, size))
        else:
          # TODO(eaeltsin): libraries such as libc don't have complete api description, thus
          # symbols lack types. Assume these symbols are pointers. Fix when all libraries have
          # complete api description.
          items.append("{\"%s\", sizeof(void*)}," % (symbol))

  print('const KnownVariable kKnownVariables[] = {', file=output_file)
  for item in sorted(items):
    print(item, file=output_file)
  print('};  // kKnownVariables', file=output_file)


def _generate_redirect_stubs(output_file, library, guest_api):
  items = []
  inits = []
  for symbol, descr in guest_api['symbols'].items():
    if descr['call_method'] == 'ignore':
      continue
    if descr['call_method'] == 'do_not_call':
      if (descr['is_compatible']):
        items.append('DEFINE_INTERCEPTABLE_STUB_VARIABLE(%s);' % symbol)
        inits.append('INIT_INTERCEPTABLE_STUB_VARIABLE(%s, %s);' %
                     (library, symbol))
    else:
      items.append('DEFINE_INTERCEPTABLE_STUB_FUNCTION(%s);' % symbol)
      inits.append('INIT_INTERCEPTABLE_STUB_FUNCTION(%s, %s);' %
                   (library, symbol))
  for item in sorted(items) + [
      '\nstatic void __attribute__((constructor(0))) init_stub_library() {\n'
      '  %s\n'
      '}' % '\n  '.join(init for init in sorted(inits))
  ]:
    print(item, file=output_file)


def main(argv):
  parser = argparse.ArgumentParser()
  parser.add_argument('--trampolines', action='store_true')
  parser.add_argument('--stubs', action='store_true')
  parser.add_argument('output_file')
  parser.add_argument('library')
  parser.add_argument('guest_api_descr_file')
  parser.add_argument('host_api_descr_file')
  parser.add_argument('custom_trampolines_descr_file')
  parser.add_argument('-v', '--verbose', action='store_true')
  args = parser.parse_args()

  library = args.library
  guest_api = json.load(open(args.guest_api_descr_file))
  host_api = json.load(open(args.host_api_descr_file))
  custom_api = json.load(open(args.custom_trampolines_descr_file))

  api_analysis.mark_incompatible_and_custom_api(
      guest_api, host_api, custom_api, verbose=args.verbose)

  if args.trampolines:
    with open(args.output_file, 'w') as output_file:
      print('// clang-format off', file=output_file)
      _generate_trampolines(output_file, guest_api)
      _generate_variables(output_file, guest_api)
      print('// clang-format on', file=output_file)

  if args.stubs:
    with open(args.output_file, 'w') as output_file:
      print(\
"""//
// Copyright (C) 2024 The Android Open Source Project
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
//
""", file=output_file)
      print('// clang-format off', file=output_file)
      print((
          '#include '
          "\"native_bridge_support/vdso/interceptable_functions.h\"\n"), file=output_file)
      _generate_redirect_stubs(output_file, '"%s.so"' % library, guest_api)
      print('// clang-format on', file=output_file)


if __name__ == '__main__':
  sys.exit(main(sys.argv))
