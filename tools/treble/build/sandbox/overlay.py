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

"""Mounts all the projects required by a selected Build target.

For details on how filesystem overlays work see the filesystem overlays
section of the README.md.
"""

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import collections
import os
import subprocess
import tempfile
import xml.etree.ElementTree as ET
from . import config

BindMount = collections.namedtuple(
    'BindMount', ['source_dir', 'readonly', 'allows_replacement'])


class BindOverlay(object):
  """Manages filesystem overlays of Android source tree using bind mounts.
  """

  MAX_BIND_MOUNTS = 10000

  def _HideDir(self, target_dir):
    """Temporarily replace the target directory for an empty directory.

    Args:
      target_dir: A string path to the target directory.

    Returns:
      A string path to the empty directory that replaced the target directory.
    """
    empty_dir = tempfile.mkdtemp(prefix='empty_dir_')
    self._AddBindMount(empty_dir, target_dir)
    return empty_dir

  def _FindBindMountConflict(self, path):
    """Finds any path in the bind mounts that conflicts with the provided path.

    Args:
      path: A string path to be checked.

    Returns:
      A tuple containing a string of the conflicting path in the bind mounts and
      whether or not to allow this path to supersede any conflicts.
      None, False if there was no conflict found.
    """
    conflict_path = None
    allows_replacement = False
    for bind_destination, bind_mount in self._bind_mounts.items():
      allows_replacement = bind_mount.allows_replacement
      # Check if the path is a subdir or the bind destination
      if path == bind_destination:
        conflict_path = bind_mount.source_dir
        break
      elif path.startswith(bind_destination + os.sep):
        relative_path = os.path.relpath(path, bind_destination)
        path_in_source = os.path.join(bind_mount.source_dir, relative_path)
        if os.path.exists(path_in_source) and os.listdir(path_in_source):
          # A conflicting path exists within this bind mount
          # and it's not empty
          conflict_path = path_in_source
          break

    return conflict_path, allows_replacement

  def _AddOverlay(self, source_dir, overlay_dir, intermediate_work_dir,
                  skip_subdirs, allowed_projects, destination_dir,
                  allowed_read_write, contains_read_write,
                  is_replacement_allowed):
    """Adds a single overlay directory.

    Args:
      source_dir: A string with the path to the Android platform source.
      overlay_dir: A string path to the overlay directory to apply.
      intermediate_work_dir: A string path to the intermediate work directory used as the
        base for constructing the overlay filesystem.
      skip_subdirs: A set of string paths to skip from overlaying.
      allowed_projects: If not None, any .git project path not in this list
        is excluded from overlaying.
      destination_dir: A string with the path to the source with the overlays
        applied to it.
      allowed_read_write: A function returns true if the path input should
        be allowed read/write access.
      contains_read_write: A function returns true if the path input contains
        a sub-path that should be allowed read/write access.
      is_replacement_allowed: A function returns true if the path can replace a
        subsequent path.
    """
    # Traverse the overlay directory twice
    # The first pass only process git projects
    # The second time process all other files that are not in git projects

    # We need to process all git projects first because
    # the way we process a non-git directory will depend on if
    # it contains a git project in a subdirectory or not.

    dirs_with_git_projects = set('/')
    for current_dir_origin, subdirs, files in os.walk(overlay_dir):

      if current_dir_origin in skip_subdirs:
        del subdirs[:]
        continue

      current_dir_relative = os.path.relpath(current_dir_origin, overlay_dir)
      current_dir_destination = os.path.normpath(
        os.path.join(destination_dir, current_dir_relative))

      if '.git' in subdirs or '.git' in files or '.bindmount' in files:
        # The current dir is a git project
        # so just bind mount it
        del subdirs[:]

        if '.bindmount' in files or (not allowed_projects or
            os.path.relpath(current_dir_origin, source_dir) in allowed_projects):
            self._AddBindMount(
                current_dir_origin, current_dir_destination,
                False if allowed_read_write(current_dir_origin) else True,
                is_replacement_allowed(
                    os.path.basename(overlay_dir), current_dir_relative))

        current_dir_ancestor = current_dir_origin
        while current_dir_ancestor and current_dir_ancestor not in dirs_with_git_projects:
          dirs_with_git_projects.add(current_dir_ancestor)
          current_dir_ancestor = os.path.dirname(current_dir_ancestor)

    # Process all other files that are not in git projects
    for current_dir_origin, subdirs, files in os.walk(overlay_dir):

      if current_dir_origin in skip_subdirs:
        del subdirs[:]
        continue

      if '.git' in subdirs or '.git' in files or '.bindmount' in files:
        del subdirs[:]
        continue

      current_dir_relative = os.path.relpath(current_dir_origin, overlay_dir)
      current_dir_destination = os.path.normpath(
        os.path.join(destination_dir, current_dir_relative))

      bindCurrentDir = True

      # Directories with git projects can't be bind mounted
      # because git projects are individually mounted
      if current_dir_origin in dirs_with_git_projects:
        bindCurrentDir = False

      # A directory that contains read-write paths should only
      # ever be bind mounted if the directory itself is read-write
      if contains_read_write(current_dir_origin) and not allowed_read_write(current_dir_origin):
        bindCurrentDir = False

      if bindCurrentDir:
        # The current dir can be bind mounted wholesale
        del subdirs[:]
        if allowed_read_write(current_dir_origin):
          self._AddBindMount(current_dir_origin, current_dir_destination, False)
        else:
          self._AddBindMount(current_dir_origin, current_dir_destination, True)
        continue

      # If we've made it this far then we're going to process
      # each file and subdir individually

      for subdir in subdirs:
        subdir_origin = os.path.join(current_dir_origin, subdir)
        # Symbolic links to subdirectories
        # have to be copied to the intermediate work directory.
        # We can't bind mount them because bind mounts dereference
        # symbolic links, and the build system filters out any
        # directory symbolic links.
        if os.path.islink(subdir_origin):
          if subdir_origin not in skip_subdirs:
            subdir_destination = os.path.join(intermediate_work_dir,
                current_dir_relative, subdir)
            self._CopyFile(subdir_origin, subdir_destination)

      # bind each file individually then keep traversing
      for file in files:
        file_origin = os.path.join(current_dir_origin, file)
        file_destination = os.path.join(current_dir_destination, file)
        if allowed_read_write(file_origin):
          self._AddBindMount(file_origin, file_destination, False)
        else:
          self._AddBindMount(file_origin, file_destination, True)


  def _AddArtifactDirectories(self, source_dir, destination_dir, skip_subdirs):
    """Add directories that were not synced as workspace source.

    Args:
      source_dir: A string with the path to the Android platform source.
      destination_dir: A string with the path to the source where the overlays
        will be applied.
      skip_subdirs: A set of string paths to be skipped from overlays.

    Returns:
      A list of string paths to be skipped from overlaying.
    """

    # Ensure the main out directory exists
    main_out_dir = os.path.join(source_dir, 'out')
    if not os.path.exists(main_out_dir):
      os.makedirs(main_out_dir)

    for subdir in os.listdir(source_dir):
      if subdir.startswith('out'):
        out_origin = os.path.join(source_dir, subdir)
        if out_origin in skip_subdirs:
          continue
        out_destination = os.path.join(destination_dir, subdir)
        self._AddBindMount(out_origin, out_destination, False)
        skip_subdirs.add(out_origin)

    repo_origin = os.path.join(source_dir, '.repo')
    if os.path.exists(repo_origin):
      repo_destination = os.path.normpath(
        os.path.join(destination_dir, '.repo'))
      self._AddBindMount(repo_origin, repo_destination, True)
      skip_subdirs.add(repo_origin)

    return skip_subdirs

  def _AddOverlays(self, source_dir, overlay_dirs, destination_dir,
                   skip_subdirs, allowed_projects, allowed_read_write,
                   contains_read_write, is_replacement_allowed):
    """Add the selected overlay directories.

    Args:
      source_dir: A string with the path to the Android platform source.
      overlay_dirs: A list of strings with the paths to the overlay
        directory to apply.
      destination_dir: A string with the path to the source where the overlays
        will be applied.
      skip_subdirs: A set of string paths to be skipped from overlays.
      allowed_projects: If not None, any .git project path not in this list
        is excluded from overlaying.
      allowed_read_write: A function returns true if the path input should
        be allowed read/write access.
      contains_read_write: A function returns true if the path input contains
        a sub-path that should be allowed read/write access.
      is_replacement_allowed: A function returns true if the path can replace a
        subsequent path.
    """

    # Create empty intermediate workdir
    intermediate_work_dir = self._HideDir(destination_dir)
    overlay_dirs.append(source_dir)

    skip_subdirs = self._AddArtifactDirectories(source_dir, destination_dir,
        skip_subdirs)


    # Bind mount each overlay directory using a
    # depth first traversal algorithm.
    #
    # The algorithm described works under the condition that the overlaid file
    # systems do not have conflicting projects or that the conflict path is
    # specifically called-out as a replacement path.
    #
    # The results of attempting to overlay two git projects on top
    # of each other are unpredictable and may push the limits of bind mounts.

    skip_subdirs.add(os.path.join(source_dir, 'overlays'))

    for overlay_dir in overlay_dirs:
      self._AddOverlay(source_dir, overlay_dir, intermediate_work_dir,
                       skip_subdirs, allowed_projects, destination_dir,
                       allowed_read_write, contains_read_write,
                       is_replacement_allowed)


  def _AddBindMount(self,
                    source_dir,
                    destination_dir,
                    readonly=False,
                    allows_replacement=False):
    """Adds a bind mount for the specified directory.

    Args:
      source_dir: A string with the path of a source directory to bind.
        It must already exist.
      destination_dir: A string with the path ofa destination
        directory to bind the source into. If it does not exist,
        it will be created.
      readonly: A flag to indicate whether this path should be bind mounted
        with read-only access.
      allow_replacement: A flag to indicate whether this path is allowed to replace a
        conflicting path.
    """
    conflict_path, replacement = self._FindBindMountConflict(destination_dir)
    if conflict_path and not replacement:
      raise ValueError("Project %s could not be overlaid at %s "
        "because it conflicts with %s"
        % (source_dir, destination_dir, conflict_path))
    elif not conflict_path:
      if len(self._bind_mounts) >= self.MAX_BIND_MOUNTS:
        raise ValueError("Bind mount limit of %s reached" % self.MAX_BIND_MOUNTS)
      self._bind_mounts[destination_dir] = BindMount(
          source_dir=source_dir,
          readonly=readonly,
          allows_replacement=allows_replacement)

  def _CopyFile(self, source_path, dest_path):
    """Copies a file to the specified destination.

    Args:
      source_path: A string with the path of a source file to copy. It must
        exist.
      dest_path: A string with the path to copy the file to. It should not
        exist.
    """
    dest_dir = os.path.dirname(dest_path)
    if not os.path.exists(dest_dir):
      os.makedirs(dest_dir)
    subprocess.check_call(['cp', '--no-dereference', source_path, dest_path])

  def GetBindMounts(self):
    """Enumerates all bind mounts required by this Overlay.

    Returns:
      An ordered dict of BindMount objects keyed by destination path string.
      The order of the bind mounts does matter, this is why it's an ordered
      dict instead of a standard dict.
    """
    return self._bind_mounts

  def _GetReadWriteFunction(self, build_config, source_dir):
    """Returns a function that tells you how to mount a path.

    Args:
      build_config: A config.BuildConfig instance of the build target to be
                    prepared.
      source_dir: A string with the path to the Android platform source.

    Returns:
      A function that takes a string path as an input and returns
      True if the path should be mounted read-write or False if
      the path should be mounted read-only.
    """

    # The read/write allowlist provides paths relative to the source dir. It
    # needs to be updated with absolute paths to make lookup possible.
    rw_allowlist = {os.path.join(source_dir, p) for p in build_config.allow_readwrite}

    def AllowReadWrite(path):
      return build_config.allow_readwrite_all or path in rw_allowlist

    return AllowReadWrite

  def _GetContainsReadWriteFunction(self, build_config, source_dir):
    """Returns a function that tells you if a directory contains a read-write dir

    Args:
      build_config: A config.BuildConfig instance of the build target to be
                    prepared.
      source_dir: A string with the path to the Android platform source.

    Returns:
      A function that takes a string path as an input and returns
      True if the path contains a read-write path
    """

    # Get all dirs with allowed read-write
    # and all their ancestor directories
    contains_rw = set()
    for path in build_config.allow_readwrite:
      while path not in ["", "/"]:
      # The read/write allowlist provides paths relative to the source dir. It
      # needs to be updated with absolute paths to make lookup possible.
        contains_rw.add(os.path.join(source_dir, path))
        path = os.path.dirname(path)

    def ContainsReadWrite(path):
      return build_config.allow_readwrite_all or path in contains_rw

    return ContainsReadWrite

  def _GetAllowedProjects(self, build_config):
    """Returns a set of paths that are allowed to contain .git projects.

    Args:
      build_config: A config.BuildConfig instance of the build target to be
                    prepared.

    Returns:
      If the target has an allowed projects file: a set of paths. Any .git
        project path not in this set should be excluded from overlaying.
      Otherwise: None
    """
    if not build_config.allowed_projects_file:
      return None
    allowed_projects = ET.parse(build_config.allowed_projects_file)
    paths = set()
    for child in allowed_projects.getroot().findall("project"):
      paths.add(child.attrib.get("path", child.attrib["name"]))
    return paths

  def _IsReplacementAllowedFunction(self, build_config):
    """Returns a function to determin if a given path is replaceable.

    Args:
      build_config: A config.BuildConfig instance of the build target to be
                    prepared.

    Returns:
      A function that takes an overlay name and string path as input and
      returns True if the path is replaceable.
    """
    def is_replacement_allowed_func(overlay_name, path):
      for overlay in build_config.overlays:
        if overlay_name == overlay.name and path in overlay.replacement_paths:
          return True
      return False

    return is_replacement_allowed_func

  def __init__(self,
               build_target,
               source_dir,
               cfg,
               whiteout_list = [],
               destination_dir=None,
               quiet=False):
    """Inits Overlay with the details of what is going to be overlaid.

    Args:
      build_target: A string with the name of the build target to be prepared.
      source_dir: A string with the path to the Android platform source.
      cfg: A config.Config instance.
      whiteout_list: A list of directories to hide from the build system.
      destination_dir: A string with the path where the overlay filesystem
        will be created. If none is provided, the overlay filesystem
        will be applied directly on top of source_dir.
      quiet: A boolean that, when True, suppresses debug output.
    """
    self._quiet = quiet

    if not destination_dir:
      destination_dir = source_dir

    self._overlay_dirs = None
    # The order of the bind mounts does matter, this is why it's an ordered
    # dict instead of a standard dict.
    self._bind_mounts = collections.OrderedDict()

    # We will be repeateadly searching for items to skip so a set
    # seems appropriate
    skip_subdirs = set(whiteout_list)

    build_config = cfg.get_build_config(build_target)

    allowed_read_write = self._GetReadWriteFunction(build_config, source_dir)
    contains_read_write = self._GetContainsReadWriteFunction(build_config, source_dir)
    allowed_projects = self._GetAllowedProjects(build_config)
    is_replacement_allowed = self._IsReplacementAllowedFunction(build_config)

    overlay_dirs = []
    for overlay in build_config.overlays:
      overlay_dir = os.path.join(source_dir, 'overlays', overlay.name)
      overlay_dirs.append(overlay_dir)

    self._AddOverlays(
        source_dir, overlay_dirs, destination_dir,
        skip_subdirs, allowed_projects, allowed_read_write, contains_read_write,
        is_replacement_allowed)

    # If specified for this target, create a custom filesystem view
    for path_relative_from, path_relative_to in build_config.views:
      path_from = os.path.join(source_dir, path_relative_from)
      if os.path.isfile(path_from) or os.path.isdir(path_from):
        path_to = os.path.join(destination_dir, path_relative_to)
        if allowed_read_write(path_from):
          self._AddBindMount(path_from, path_to, False)
        else:
          self._AddBindMount(path_from, path_to, True)
      else:
        raise ValueError("Path '%s' must be a file or directory" % path_from)

    self._overlay_dirs = overlay_dirs
    if not self._quiet:
      print('Applied overlays ' + ' '.join(self._overlay_dirs))
