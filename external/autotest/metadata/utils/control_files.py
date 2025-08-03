# Lint as: python3
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import ast
import collections
import glob
import logging
import os
import pdb
from typing import Generator, List, Optional, Tuple


class ControlFileError(Exception):
  """Generic error from this package."""


Control = collections.namedtuple('Control',
                                 'path, category, name, suites, main_package')


def load_all() -> List[Control]:
  controls = []
  for path in _enumerate_files():
    logging.debug('Processing %s', path)
    control = _load_one(path)
    logging.debug('  -> %s', control)
    controls.append(control)
  return controls


_ROOT_DIR = os.path.realpath(
    os.path.join(os.path.realpath(__file__), "../../.."))


def _enumerate_files() -> Generator[str, None, None]:
  for ttype in ['client', 'server']:
    tpath = os.path.join(_ROOT_DIR, ttype)
    for path in glob.iglob(tpath + '/site_tests/*/control*'):
      # There are some text files with names like control_sequence.txt
      _, ext = os.path.splitext(path)
      if ext:
        continue
      yield path


def _load_one(path: str) -> Control:
  with open(path) as r:
    text = r.read()
  module = ast.parse(text)
  name = _extract_name(module)
  category, name = _categorize_name(name)
  return Control(
      path=path,
      category=category,
      name=name,
      suites=_extract_suites(module),
      main_package=_extract_main_package(path, module) or '',
  )


def _extract_name(module: ast.Module) -> Optional[str]:
  stmt = _find_last_global_assignment(module.body, 'NAME')
  if stmt is None:
    raise ControlFileError('No global NAME assignment')
  name = _extract_str_value(stmt)
  if not name:
    raise ControlFileError('Empty value')
  return name


def _find_last_global_assignment(stmts: List[ast.Assign],
                                 name: str) -> Optional[ast.Assign]:
  found = _find_global_assignments(stmts, name)
  if len(found) > 0:
    return found[-1]
  return None


def _find_global_assignments(stmts: List[ast.Assign],
                             name: str) -> List[ast.Assign]:
  found = []
  for stmt in stmts:
    if isinstance(stmt, ast.Assign) and _contains_name(stmt.targets, name):
      found.append(stmt)
  return found


def _contains_name(targets: List[ast.Expr], want: str) -> bool:
  for target in targets:
    if not isinstance(target, ast.Name):
      # We do not support complex lvalues.
      # In particular, multi-valued assignments are not handled properly.
      continue
    name: ast.Name = target
    if name.id == want:
      return True
  return False


def _extract_str_value(stmt: ast.Assign) -> str:
  if not isinstance(stmt.value, ast.Constant):
    raise ControlFileError(
        'Name assignment value is of type %s, want ast.Constant' %
        type(stmt.value))
  v = str(stmt.value.value)
  return v


def _categorize_name(name: str) -> Tuple[str, str]:
  parts = name.split('_', 1)
  if len(parts) == 2:
    category, rest = parts[0], parts[1]
  else:
    category, rest = '', parts[0]
  return category, rest


_SUITE_PREFIX_LEN = len('suite:')


def _extract_suites(module: ast.Module) -> List[str]:
  stmt = _find_last_global_assignment(module.body, 'ATTRIBUTES')
  if stmt is None:
    return []
  v = _extract_str_value(stmt)
  suites = []
  for attr in v.split(','):
    attr = attr.strip()
    if attr.startswith('suite:'):
      suites.append(attr[_SUITE_PREFIX_LEN:])
  return suites


def _extract_main_package(path: str, module: ast.Module) -> Optional[str]:
  fname = _extract_main_file(path, module)
  if fname is None:
    return None
  relpath = os.path.relpath(os.path.dirname(path), _ROOT_DIR)
  assert '.' not in relpath
  return 'autotest_lib.%s.%s' % (relpath.replace('/', '.'), fname)


def _extract_main_file(path: str, module: ast.Module) -> Optional[str]:
  calls = _find_run_test_calls(module)
  if not calls:
    logging.warning('Found no job.run_test() calls in %s', path)
    return None
  if len(calls) > 1:
    logging.warning('Found %d job.run_test() calls in %s, want 1', len(calls),
                    path)
    return None
  return _extract_run_test_target(path, calls[0])


def _find_run_test_calls(module: ast.Module) -> List[ast.Call]:
  calls = []
  for stmt in module.body:
    if isinstance(stmt, ast.Expr) and isinstance(stmt.value, ast.Call):
      call = stmt.value
      func = call.func
      if (isinstance(func, ast.Attribute) and func.attr == 'run_test' and
          isinstance(func.value, ast.Name) and func.value.id == 'job'):
        calls.append(call)
  return calls


def _extract_run_test_target(path: str, call: ast.Call) -> Optional[str]:
  if len(call.args) != 1:
    logging.warning('job.run_test() has %d arguments in %s, want 1',
                    len(call.args), path)
    return None
  arg = call.args[0]
  if not isinstance(arg, ast.Constant):
    logging.warning('job.run_test() has a non constant argument in %s', path)
    return None
  return arg.value
