#!/usr/bin/env python3
#
# Copyright 2023, The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Preserves and restores the state of a repository.

This module includes a `Snapshot` class that provides methods to:
- Take snapshots of a directory, including or excluding specified paths.
- Preserve environment variables.
- Restore the directory state from previously taken snapshots, managing file and
directory deletions and replacements.
"""

import functools
import glob
import hashlib
import json
import logging
import os
import pathlib
import threading
from typing import Any, Optional


def _synchronized(func):
  """Ensures thread-safe execution of the wrapped function."""
  lock = threading.Lock()

  @functools.wraps(func)
  def _synchronized_func(*args, **kwargs):
    with lock:
      return func(*args, **kwargs)

  return _synchronized_func


class Snapshot:
  """Provides functionality to take and restore snapshots of a directory."""

  def __init__(self, storage_dir: pathlib.Path):
    """Initializes a Snapshot object.

    Args:
        storage_dir: The directory where snapshots will be stored.
    """
    self._dir_snapshot = _DirSnapshot(storage_dir)
    self._env_snapshot = _EnvSnapshot(storage_dir)
    self._obj_snapshot = _ObjectSnapshot(storage_dir)
    self._lock = self._get_threading_lock(storage_dir)

  @_synchronized
  def _get_threading_lock(
      self,
      name: str,
  ):
    """Gets a threading lock for the snapshot directory."""
    locks_dict_attr_name = 'threading_locks'
    current_function = self._get_threading_lock.__func__
    if not hasattr(current_function, locks_dict_attr_name):
      setattr(current_function, locks_dict_attr_name, {})
    if name not in getattr(current_function, locks_dict_attr_name):
      getattr(current_function, locks_dict_attr_name)[name] = threading.Lock()
    return getattr(current_function, locks_dict_attr_name)[name]

  # pylint: disable=too-many-arguments
  def take_snapshot(
      self,
      name: str,
      root_path: str,
      include_paths: list[str],
      exclude_paths: Optional[list[str]] = None,
      env_keys: Optional[list[str]] = None,
      env: Optional[dict[str, str]] = None,
      objs: Optional[dict[str, Any]] = None,
  ) -> None:
    """Takes a snapshot of the directory at the given path.

    Args:
        name: The name of the snapshot.
        root_path: The path to the directory to snapshot.
        include_paths: A list of relative paths to include in the snapshot.
        exclude_paths: A list of relative paths to exclude from the snapshot.
        env_keys: A list of environment variable keys to save.
        env: Environment variables to use while restoring.
        objs: A dictionary of objects to save. The current implementation limits
          the type of objects to the types that can be serialized by the json
          module.
    """
    with self._lock:
      self._dir_snapshot.take_snapshot(
          name, root_path, include_paths, exclude_paths, env
      )
      self._env_snapshot.take_snapshot(name, env_keys)
      self._obj_snapshot.take_snapshot(name, objs)

  def restore_snapshot(
      self,
      name: str,
      root_path: str,
      exclude_paths: Optional[list[str]] = None,
  ) -> tuple[dict[str, str], dict[str, Any]]:
    """Restores directory at given path to a snapshot with the given name.

    Args:
        name: The name of the snapshot.
        root_path: The path to the target directory.
        exclude_paths: A list of paths to ignore during restore.

    Returns:
        A tuple of restored environment variables and object dictionary.
    """
    env = self._env_snapshot.restore_snapshot(name, root_path)
    objs = self._obj_snapshot.restore_snapshot(name)
    with self._lock:
      self._dir_snapshot.restore_snapshot(name, root_path, exclude_paths, env)
    return env, objs


class _ObjectSnapshot:
  """Save and restore a dictionary of objects through json."""

  def __init__(self, storage_path: pathlib.Path):
    self._storage_path = storage_path

  def take_snapshot(
      self,
      name: str,
      objs: Optional[dict[str, Any]] = None,
  ) -> None:
    """Save a dictionary of objects in snapshot.

    Args:
        name: The name of the snapshot
        objs: A dictionary of objects to snapshot. Note: The current
          implementation limits the type of objects to the types that can be
          serialized by the json module.
    """
    if objs is None:
      objs = {}
    with open(
        self._storage_path.joinpath('%s.objs.json' % name),
        'w',
        encoding='utf-8',
    ) as f:
      json.dump(objs, f)

  def restore_snapshot(self, name: str) -> dict[str, Any]:
    """Restore saved objects from snapshot."""
    with open(
        self._storage_path.joinpath('%s.objs.json' % name),
        'r',
        encoding='utf-8',
    ) as f:
      return json.load(f)


class _EnvSnapshot:
  """Save and restore environment variables."""

  _repo_root_placeholder = '<repo_root_placeholder>'

  def __init__(self, storage_path: pathlib.Path):
    self._storage_path = storage_path

  def take_snapshot(
      self,
      name: str,
      env_keys: Optional[list[str]] = None,
  ) -> None:
    """Save a subset of environment variables."""
    if env_keys is None:
      env_keys = []
    original_env = os.environ.copy()
    subset_env = {
        key: os.environ[key] for key in env_keys if key in original_env
    }
    modified_env = {
        key: value.replace(
            os.environ['ANDROID_BUILD_TOP'], self._repo_root_placeholder
        )
        for key, value in subset_env.items()
    }
    with open(self._get_env_file_path(name), 'w', encoding='utf-8') as f:
      json.dump(modified_env, f)

  def restore_snapshot(self, name: str, root_path: str) -> dict[str, str]:
    """Load saved environment variables."""
    with self._get_env_file_path(name).open('r') as f:
      loaded_env = json.load(f)
    restored_env = {
        key: value.replace(
            self._repo_root_placeholder,
            root_path,
        )
        for key, value in loaded_env.items()
    }
    if 'PATH' in os.environ:
      if 'PATH' in restored_env:
        restored_env['PATH'] = restored_env['PATH'] + ':' + os.environ['PATH']
      else:
        restored_env['PATH'] = os.environ['PATH']
    return restored_env

  def _get_env_file_path(self, name: str) -> pathlib.Path:
    """Get environment file path."""
    return self._storage_path / (name + '_env.json')


class _FileInfo:
  """An object to save file information."""

  # pylint: disable=too-many-arguments
  def __init__(
      self,
      path: str,
      timestamp: float,
      content_hash: str,
      permissions: int,
      symlink_target: str,
      is_directory: bool,
  ):
    self.path = path
    self.timestamp = timestamp
    self.content_hash = content_hash
    self.permissions = permissions
    self.symlink_target = symlink_target
    self.is_directory = is_directory


class _BlobStore:
  """Class to save and load file content."""

  def __init__(self, path: str):
    self.path = pathlib.Path(path)
    self.cache = self._load_cache()

  def add(self, path: pathlib.Path, timestamp: float) -> str:
    """Add a file path to the store."""
    cache_key = path.as_posix() + str(timestamp)
    if cache_key in self.cache:
      return self.cache[cache_key]
    content = path.read_bytes()
    content_hash = hashlib.sha256(content).hexdigest()
    content_path = self.path.joinpath(content_hash[:2], content_hash[2:])
    if not content_path.exists():
      content_path.parent.mkdir(parents=True, exist_ok=True)
      content_path.write_bytes(content)
    self.cache[cache_key] = content_hash
    return content_hash

  def get(self, content_hash: str) -> bytes:
    """Read file content from a content hash."""
    file_path = self.path.joinpath(content_hash[:2], content_hash[2:])
    if file_path.exists():
      return file_path.read_bytes()
    return None

  def dump_cache(self) -> None:
    """Dump the saved file path cache to speed up next run."""
    self._get_cache_path().parent.mkdir(parents=True, exist_ok=True)
    with self._get_cache_path().open('w', encoding='utf-8') as f:
      json.dump(self.cache, f)

  def _load_cache(self) -> dict[str, str]:
    if not self._get_cache_path().exists():
      return {}
    with self._get_cache_path().open('r', encoding='utf-8') as f:
      return json.load(f)

  def _get_cache_path(self) -> pathlib.Path:
    return self.path.joinpath('cache.json')


class _DirSnapshot:
  """Class to take and restore snapshot for a directory path."""

  def __init__(self, storage_path: pathlib.Path):
    self._storage_path = storage_path
    self._blob_store = _BlobStore(self._storage_path.joinpath('blobs'))

  def _expand_vars_paths(
      self, paths: list[str], variables: dict[str, str]
  ) -> list[str]:
    """Expand variables in paths with the given environment variables.

    This function is similar to os.path.expandvars(path) which relies on
    os.environ.

    Args:
        paths: A list of paths that might contains variables to expand.
        variables: A dictionary of variable names and values.

    Returns:
        A list containing paths whose variables have been expanded if known.
    """
    if not variables:
      return paths
    path_result = paths.copy()
    for idx, _ in enumerate(path_result):
      for key, val in sorted(
          variables.items(), key=lambda item: len(item[0]), reverse=True
      ):
        path_result[idx] = path_result[idx].replace(f'${key}', val)
    return path_result

  def _expand_wildcard_paths(
      self,
      root_path: str,
      paths: list[str],
      env: Optional[dict[str, str]] = None,
  ) -> list[str]:
    """Expand wildcard paths."""
    compose = lambda inner, outer: lambda path: outer(inner(path))
    get_abs_path = (
        lambda path: path
        if os.path.isabs(path)
        else os.path.join(root_path, path)
    )
    glob_path = functools.partial(glob.glob, recursive=True)
    return sum(
        map(
            compose(get_abs_path, glob_path),
            self._expand_vars_paths(paths, env),
        ),
        [],
    )

  def _is_excluded(self, path: str, exclude_paths: list[pathlib.Path]) -> bool:
    """Check whether a path should be excluded."""
    return exclude_paths and any(
        path.startswith(exclude_path) for exclude_path in exclude_paths
    )

  def _filter_excluded_paths(
      self,
      root: pathlib.Path,
      paths: list[pathlib.Path],
      exclude_paths: list[pathlib.Path],
  ) -> None:
    """Filter a list of paths with a list of exclude paths."""
    new_paths = [
        path
        for path in paths
        if not self._is_excluded(os.path.join(root, path), exclude_paths)
    ]
    if len(new_paths) == len(paths):
      return
    paths.clear()
    paths.extend(new_paths)

  def take_snapshot(
      self,
      name: str,
      root_path: str,
      include_paths: list[str],
      exclude_paths: Optional[list[str]] = None,
      env: Optional[dict[str, str]] = None,
  ) -> tuple[dict[str, _FileInfo], list[str]]:
    """Creates a snapshot of the directory at the given path.

    Args:
        name: The name of the snapshot.
        root_path: The path to the root directory.
        include_paths: A list of relative paths to include in the snapshot.
        exclude_paths: A list of relative paths to exclude from the snapshot.
        env: Environment variables to use while restoring.

    Returns:
        A tuple containing:
            - A dictionary of _FileInfo objects keyed by their relative path
            within the directory.
    """
    include_paths = (
        self._expand_wildcard_paths(root_path, include_paths, env)
        if include_paths
        else []
    )
    exclude_paths = (
        self._expand_wildcard_paths(root_path, exclude_paths, env)
        if exclude_paths
        else []
    )

    file_infos = {}

    def process_directory(path: pathlib.Path) -> None:
      if path.is_symlink():
        process_link(path)
        return
      relative_path = path.relative_to(root_path).as_posix()
      if relative_path == '.':
        return
      file_infos[relative_path] = _FileInfo(
          relative_path,
          timestamp=None,
          content_hash=None,
          permissions=path.stat().st_mode,
          symlink_target=None,
          is_directory=True,
      )

    def process_file(path: pathlib.Path) -> None:
      if path.is_symlink():
        process_link(path)
        return
      relative_path = path.relative_to(root_path).as_posix()
      timestamp = path.stat().st_mtime
      file_infos[relative_path] = _FileInfo(
          relative_path,
          timestamp=timestamp,
          content_hash=self._blob_store.add(path, timestamp)
          if path.stat().st_size
          else None,
          permissions=path.stat().st_mode,
          symlink_target=None,
          is_directory=False,
      )

    def process_link(path: pathlib.Path) -> None:
      symlink_target = path.resolve()
      if symlink_target.is_relative_to(root_path):
        symlink_target = symlink_target.relative_to(root_path)
      else:
        # We are not throwing error here as we are working on supporting
        # external links for bazel cache. This will be removed later.
        logging.error(
            'Unexpected external link: '
            + path.as_posix()
            + ' -> '
            + symlink_target.as_posix()
        )
      relative_path = path.relative_to(root_path).as_posix()
      file_infos[relative_path] = _FileInfo(
          relative_path,
          timestamp=None,
          content_hash=None,
          permissions=None,
          symlink_target=symlink_target.as_posix(),
          is_directory=False,
      )

    def process_path(path: pathlib.Path) -> None:
      if self._is_excluded(path.as_posix(), exclude_paths):
        return
      if path.is_symlink():
        process_link(path)
      elif path.is_file():
        process_file(path)
      elif path.is_dir():
        process_directory(path)
        for root, directories, files in os.walk(path):
          self._filter_excluded_paths(root, directories, exclude_paths)
          self._filter_excluded_paths(root, files, exclude_paths)
          for directory in directories:
            process_directory(pathlib.Path(root).joinpath(directory))
          for file in files:
            process_file(pathlib.Path(root).joinpath(file))
      else:
        # We are not throwing error here because it might be just a
        # corner case which likely doesn't affect the test process.
        logging.error('Unexpected path type: %s', path.as_posix())

    for path in include_paths:
      process_path(pathlib.Path(path))

    snapshot_path = self._storage_path.joinpath(name + '_metadata.json')
    snapshot_path.parent.mkdir(parents=True, exist_ok=True)
    with snapshot_path.open('w') as f:
      json.dump(file_infos, f, default=lambda o: o.__dict__)

    self._blob_store.dump_cache()

    return file_infos

  def restore_snapshot(
      self,
      name: str,
      root_path: str,
      exclude_paths: Optional[list[str]] = None,
      env: Optional[dict[str, str]] = None,
  ) -> tuple[list[str], list[str], list[str]]:
    """Restores directory at given path to snapshot with given name.

    Args:
        name: The name of the snapshot.
        root_path: The path to the root directory.
        exclude_paths: A list of relative paths to ignore during restoring.
        env: Environment variables to use while restoring.

    Returns:
        A tuple containing 3 lists:
            - Files and directories that were deleted.
            - Files that were replaced.
            - A list of paths to symbolic links that point to files outside
            the
            source directory.
    """
    with self._storage_path.joinpath(name + '_metadata.json').open('r') as f:
      file_infos_dict = {
          key: _FileInfo(**val) for key, val in json.load(f).items()
      }

    exclude_paths = (
        self._expand_wildcard_paths(root_path, exclude_paths, env)
        if exclude_paths
        else []
    )

    deleted = self._remove_extra_files(
        file_infos_dict, root_path, exclude_paths
    )
    self._restore_directories(file_infos_dict, root_path, exclude_paths)
    replaced, external_symlinks = self._restore_files(
        file_infos_dict, root_path, exclude_paths
    )

    return deleted, replaced, external_symlinks

  def _remove_extra_files(
      self,
      file_infos_dict: dict[str, _FileInfo],
      root_path: str,
      exclude_paths: list[str],
  ):
    """Internal method to remove extra files during snapshot restore."""
    deleted = []
    for root, directories, files in os.walk(root_path):
      self._filter_excluded_paths(root, directories, exclude_paths)
      self._filter_excluded_paths(root, files, exclude_paths)
      for directory in directories:
        dir_path = pathlib.Path(root).joinpath(directory)
        # Ignore non link directories because complicated to deal
        # with file paths in include filters and unnecessary
        if dir_path.is_symlink():
          dir_path.unlink()
      for file in files:
        file_path = pathlib.Path(root).joinpath(file)
        if file_path.is_symlink():
          file_path.unlink()
        elif file_path.relative_to(root_path).as_posix() not in file_infos_dict:
          file_path.unlink()
          deleted.append(file_path.as_posix())
    return deleted

  def _restore_directories(
      self,
      file_infos_dict: dict[str, _FileInfo],
      root_path: str,
      exclude_paths: list[str],
  ):
    """Internal method to restore directories during snapshot restore."""
    for relative_path, file_info in file_infos_dict.items():
      if not file_info.is_directory:
        continue
      dir_path = pathlib.Path(root_path).joinpath(relative_path)
      if self._is_excluded(dir_path.as_posix(), exclude_paths):
        continue
      dir_path.mkdir(parents=True, exist_ok=True)
      os.chmod(dir_path, file_info.permissions)

  def _restore_files(
      self,
      file_infos_dict: dict[str, _FileInfo],
      root_path: str,
      exclude_paths: list[str],
  ):
    """Internal method to restore files during snapshot restore."""
    replaced = []
    external_symlinks = []
    for relative_path, file_info in file_infos_dict.items():
      file_path = pathlib.Path(root_path).joinpath(relative_path)
      if self._is_excluded(file_path.as_posix(), exclude_paths):
        continue
      if file_info.symlink_target:
        if os.path.isabs(file_info.symlink_target):
          msg = file_info.symlink_target + ' <- ' + file_path.as_posix()
          external_symlinks.append(msg)
          logging.error('Unexpected external link: %s', msg)
          continue
        target = pathlib.Path(root_path).joinpath(file_info.symlink_target)
        file_path.parent.mkdir(parents=True, exist_ok=True)
        file_path.symlink_to(target)
        continue

      if file_info.is_directory:
        continue

      if (
          file_path.exists()
          and file_path.stat().st_mtime == file_info.timestamp
      ):
        continue

      file_path.parent.mkdir(parents=True, exist_ok=True)
      file_path.unlink(missing_ok=True)
      if not file_info.content_hash:
        file_path.touch()
      else:
        file_path.write_bytes(self._blob_store.get(file_info.content_hash))
      os.utime(file_path, (file_info.timestamp, file_info.timestamp))
      os.chmod(file_path, file_info.permissions)
      replaced.append(file_path.as_posix())
    return replaced, external_symlinks
