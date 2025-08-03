# Copyright 2021 The Android Open Source Project
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

"""Common Utils."""

# pylint: disable=g-importing-member
from dataclasses import dataclass
import io
from pathlib import Path
from pathlib import PurePath
import sys
from typing import List
from typing import Set

# pylint: disable=g-import-not-at-top
try:
  from git import Blob
  from git import Commit
  from git import Tree
except ModuleNotFoundError:
  print(
      'ERROR: Please install GitPython by `pip3 install GitPython`.',
      file=sys.stderr)
  exit(1)

THIS_DIR = Path(__file__).resolve().parent
LIBCORE_DIR = THIS_DIR.parent.parent.resolve()

UPSTREAM_CLASS_PATHS = [
    'jdk/src/share/classes/',
    'src/java.base/share/classes/',
    'src/java.base/linux/classes/',
    'src/java.base/unix/classes/',
    'src/java.sql/share/classes/',
    'src/java.logging/share/classes/',
    'src/java.prefs/share/classes/',
    'src/java.net/share/classes/',
]

UPSTREAM_TEST_PATHS = [
    'jdk/test/',
    'test/jdk/',
]

UPSTREAM_SEARCH_PATHS = UPSTREAM_CLASS_PATHS + UPSTREAM_TEST_PATHS

OJLUNI_JAVA_BASE_PATH = 'ojluni/src/main/java/'
OJLUNI_TEST_PATH = 'ojluni/src/'
TEST_PATH = OJLUNI_TEST_PATH + 'test/'


@dataclass
class ExpectedUpstreamEntry:
  """A map entry in the EXPECTED_UPSTREAM file."""
  dst_path: str  # destination path
  git_ref: str  # a git reference to an upstream commit
  src_path: str  # source path in the commit pointed by the git_ref
  comment_lines: str = ''  # The comment lines above the entry line

  def __eq__(self, other):
    if not isinstance(other, ExpectedUpstreamEntry):
      return False

    return (self.dst_path == other.dst_path and
            self.git_ref == other.git_ref and
            self.src_path == other.src_path and
            self.comment_lines == other.comment_lines)


class ExpectedUpstreamFile:
  """A file object representing the EXPECTED_UPSTREAM file."""

  def __init__(self, file_or_bytes=LIBCORE_DIR / 'EXPECTED_UPSTREAM'):
    if isinstance(file_or_bytes, Path):
      path = Path(file_or_bytes)
      # pylint: disable=unnecessary-lambda
      self.openable = lambda mode: path.open(mode)
    elif isinstance(file_or_bytes, bytes):
      self.openable = lambda mode: io.StringIO(file_or_bytes.decode('utf-8'))
    else:
      raise NotImplementedError('Only support bytes or Path type')

  def read_all_entries(self) -> List[ExpectedUpstreamEntry]:
    """Read all entries from the file."""
    result: List[ExpectedUpstreamEntry] = []
    with self.openable('r') as file:
      comment_lines = ''  # Store the comment lines in the next entry
      for line in file:
        stripped = line.strip()
        # Ignore empty lines and comments starting with '#'
        if not stripped or stripped.startswith('#'):
          comment_lines += line
          continue

        entry = self.parse_line(stripped, comment_lines)
        result.append(entry)
        comment_lines = ''

    return result

  def write_all_entries(self, entries: List[ExpectedUpstreamEntry]) -> None:
    """Write all entries into the file."""
    with self.openable('w') as file:
      for e in entries:
        file.write(e.comment_lines)
        file.write(','.join([e.dst_path, e.git_ref, e.src_path]))
        file.write('\n')

  def write_new_entry(self, entry: ExpectedUpstreamEntry,
                      entries: List[ExpectedUpstreamEntry] = None) -> None:
    if entries is None:
      entries = self.read_all_entries()

    entries.append(entry)
    self.sort_and_write_all_entries(entries)

  def sort_and_write_all_entries(self,
                                 entries: List[ExpectedUpstreamEntry]) -> None:
    header = entries[0].comment_lines
    entries[0].comment_lines = ''
    entries.sort(key=lambda e: e.dst_path)
    # Keep the header above the first entry
    entries[0].comment_lines = header + entries[0].comment_lines
    self.write_all_entries(entries)

  def get_new_or_modified_entries(self, other) -> List[ExpectedUpstreamEntry]:
    r"""Return a list of modified and added entries from the other file.

    Args:
      other: the other file

    Returns:
      A list of modified and added entries
    """
    result: List[ExpectedUpstreamEntry] = []
    this_entries = self.read_all_entries()
    that_entries = other.read_all_entries()
    this_map = {}
    for e in this_entries:
      this_map[e.dst_path] = e

    for e in that_entries:
      value = this_map.get(e.dst_path)
      if value is None or value != e:
        result.append(e)

    return result

  def get_removed_paths(self, other: ExpectedUpstreamEntry) -> Set[str]:
    r"""Returns a list of dst paths removed in the new list.

    Args:
      other: the other file

    Returns:
      A list of removed paths
    """
    this_paths = list(map(lambda e: e.dst_path, self.read_all_entries()))
    that_entries = other.read_all_entries()
    that_map = {}
    for e in that_entries:
      that_map[e.dst_path] = e

    return set(filter(lambda p: p not in that_map, this_paths))

  @staticmethod
  def parse_line(line: str, comment_lines: str) -> ExpectedUpstreamEntry:
    items = line.split(',')
    size = len(items)
    if size != 3:
      raise ValueError(
          f"The size must be 3, but is {size}. The line is '{line}'")

    return ExpectedUpstreamEntry(items[0], items[1], items[2], comment_lines)


class OjluniFinder:
  """Finder for java classes or ojluni/ paths."""

  def __init__(self, existing_paths: List[str]):
    self.existing_paths = existing_paths

  @staticmethod
  def translate_ojluni_path_to_class_name(path: str) -> str:
    r"""Translate an Ojluni file path to full class name.

    Args:
      path: ojluni path

    Returns:
      class name or None if class name isn't found.
    """

    if not path.endswith('.java'):
      return None

    if path.startswith(OJLUNI_JAVA_BASE_PATH):
      base_path = OJLUNI_JAVA_BASE_PATH
    elif path.startswith(OJLUNI_TEST_PATH):
      base_path = OJLUNI_TEST_PATH
    else:
      return None

    base_len = len(base_path)
    return path[base_len:-5].replace('/', '.')

  @staticmethod
  def translate_from_class_name_to_ojluni_path(class_or_path: str) -> str:
    """Returns a ojluni path from a class name."""
    # if it contains '/', then it's a path
    if '/' in class_or_path:
      return class_or_path

    base_path = OJLUNI_TEST_PATH if class_or_path.startswith(
        'test.') else OJLUNI_JAVA_BASE_PATH

    relative_path = class_or_path.replace('.', '/')
    return f'{base_path}{relative_path}.java'

  def match_path_prefix(self, input_path: str) -> Set[str]:
    """Returns a set of existing file paths matching the given partial path."""
    path_matches = list(
        filter(lambda path: path.startswith(input_path), self.existing_paths))
    result_set: Set[str] = set()
    # if it's found, just return the result
    if input_path in path_matches:
      result_set.add(input_path)
    else:
      input_ojluni_path = PurePath(input_path)
      # the input ends with '/', the autocompletion result contain the children
      # instead of the matching the prefix in its parent directory
      input_path_parent_or_self = input_ojluni_path
      if not input_path.endswith('/'):
        input_path_parent_or_self = input_path_parent_or_self.parent
      n_parts = len(input_path_parent_or_self.parts)
      for match in path_matches:
        path = PurePath(match)
        # path.parts[n_parts] should not exceed the index and should be
        # a valid child path because input_path_parent_or_self must be a
        # valid directory
        child = list(path.parts)[n_parts]
        result = (input_path_parent_or_self / child).as_posix()
        # if result is not exact, the result represents a directory.
        if result != match:
          result += '/'
        result_set.add(result)

    return result_set

  def match_classname_prefix(self, input_class_name: str) -> List[str]:
    """Returns a list of package / class names given the partial class name."""
    # If '/' exists, it's probably a path, not a partial class name
    if '/' in input_class_name:
      return []

    result_list = []
    partial_relative_path = input_class_name.replace('.', '/')
    for base_path in [OJLUNI_JAVA_BASE_PATH, OJLUNI_TEST_PATH]:
      partial_ojluni_path = base_path + partial_relative_path
      result_paths = self.match_path_prefix(partial_ojluni_path)
      # pylint: disable=cell-var-from-loop
      result_list.extend(
          map(lambda path: convert_path_to_java_class_name(path, base_path),
              list(result_paths)))

    return result_list


class OpenjdkFinder:
  """Finder for java classes or paths in a upstream OpenJDK commit."""

  def __init__(self, commit: Commit):
    self.commit = commit

  @staticmethod
  def translate_src_path_to_ojluni_path(src_path: str) -> str:
    """Returns None if src_path isn't in a known source directory."""
    relative_path = None
    for base_path in UPSTREAM_TEST_PATHS:
      if src_path.startswith(base_path):
        length = len(base_path)
        relative_path = src_path[length:]
        break

    if relative_path:
      return f'{OJLUNI_TEST_PATH}test/{relative_path}'

    for base_path in UPSTREAM_CLASS_PATHS:
      if src_path.startswith(base_path):
        length = len(base_path)
        relative_path = src_path[length:]
        break

    if relative_path:
      return f'{OJLUNI_JAVA_BASE_PATH}{relative_path}'

    return None

  def find_src_path_from_classname(self, class_or_path: str) -> str:
    """Finds a valid source path given a valid class name or path."""
    # if it contains '/', then it's a path
    if '/' in class_or_path:
      if self.has_file(class_or_path):
        return class_or_path
      else:
        return None

    relative_path = class_or_path.replace('.', '/')
    src_path = None
    for base_path in UPSTREAM_SEARCH_PATHS:
      full_path = f'{base_path}{relative_path}.java'
      if self.has_file(full_path):
        src_path = full_path
        break

    return src_path

  def get_search_paths(self) -> List[str]:
    return UPSTREAM_SEARCH_PATHS

  def find_src_path_from_ojluni_path(self, ojluni_path: str) -> str:
    """Returns a source path that guessed from the ojluni_path."""
    base_paths = None
    relative_path = None

    if ojluni_path.startswith(OJLUNI_JAVA_BASE_PATH):
      base_paths = UPSTREAM_CLASS_PATHS
      length = len(OJLUNI_JAVA_BASE_PATH)
      relative_path = ojluni_path[length:]
    elif ojluni_path.startswith(TEST_PATH):
      base_paths = UPSTREAM_TEST_PATHS
      length = len(TEST_PATH)
      relative_path = ojluni_path[length:]
    else:
      return None

    for base_path in base_paths:
      full_path = base_path + relative_path
      if self.has_file(full_path):
        return full_path

    return None

  def match_path_prefix(self, input_path: str) -> List[str]:
    """Returns a list of source paths matching the given partial string."""
    result_list = []

    search_tree = self.commit.tree
    path_obj = PurePath(input_path)
    is_exact = self.has_file(path_obj.as_posix())
    is_directory_path = input_path.endswith('/')
    exact_obj = search_tree[path_obj.as_posix()] if is_exact else None
    search_word = ''
    if is_exact and isinstance(exact_obj, Blob):
      # an exact file path
      result_list.append(input_path)
      return result_list
    elif is_directory_path:
      # an exact directory path and can't be a prefix directory name.
      if is_exact:
        search_tree = exact_obj
      else:
        # Such path doesn't exist, and thus returns empty list
        return result_list
    elif len(path_obj.parts) >= 2 and not is_directory_path:
      parent_path = path_obj.parent.as_posix()
      if self.has_file(parent_path):
        search_tree = search_tree[parent_path]
        search_word = path_obj.name
      else:
        # Return empty list because no such path is found
        return result_list
    else:
      search_word = input_path

    for tree in search_tree.trees:
      tree_path = PurePath(tree.path)
      if tree_path.name.startswith(search_word):
        # Append '/' to indicate directory type. If the result has this item
        # only, shell should auto-fill the input, and thus
        # next tabbing in shell should fall into the above condition
        # `is_exact and input_path.endswith('/')` and will search in the child
        # tree.
        result_path = tree.path + '/'
        result_list.append(result_path)

    for blob in search_tree.blobs:
      blob_path = PurePath(blob.path)
      if blob_path.name.startswith(search_word):
        result_list.append(blob.path)

    return result_list

  def match_classname_prefix(self, input_class_name: str) -> List[str]:
    """Return a list of package / class names from given commit and input."""
    # If '/' exists, it's probably a path, not a class name.
    if '/' in input_class_name:
      return []

    result_list = []
    for base_path in UPSTREAM_SEARCH_PATHS:
      base_len = len(base_path)
      path = base_path + input_class_name.replace('.', '/')
      path_results = self.match_path_prefix(path)
      for p in path_results:
        relative_path = p[base_len:]
        if relative_path.endswith('.java'):
          relative_path = relative_path[0:-5]
        result_list.append(relative_path.replace('/', '.'))

    return result_list

  def has_file(self, path: str) -> bool:
    """Returns True if the directory / file exists in the tree."""
    return has_file_in_tree(path, self.commit.tree)


def convert_path_to_java_class_name(path: str, base_path: str) -> str:
  base_len = len(base_path)
  result = path[base_len:]
  if result.endswith('.java'):
    result = result[0:-5]
  result = result.replace('/', '.')
  return result


def has_file_in_tree(path: str, tree: Tree) -> bool:
  """Returns True if the directory / file exists in the tree."""
  try:
    # pylint: disable=pointless-statement
    tree[path]
    return True
  except KeyError:
    return False
