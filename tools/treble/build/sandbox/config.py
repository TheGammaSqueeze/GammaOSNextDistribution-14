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
"""Parses config file and provides various ways of using it."""

import xml.etree.ElementTree as ET
import collections

# The config file must be in XML with a structure as descibed below.
#
# The top level config element shall contain one or more "target" child
# elements. Each of these may contain one or more build_config child elements.
# The build_config child elements will inherit the properties of the target
# parent.
#
# Each "target" and "build_config" may contain the following:
#
# Attributes:
#
#   name: The name of the target.
#
#   android_target: The name of the android target used with lunch
#
#   allow_readwrite_all: "true" if the full source folder shall be mounted as
#   read/write. It should be accompanied by a comment with the bug describing
#   why it was required.
#
#   tags: A comma-separated list of strings to be associated with the target
#     and any of its nested build_targets. You can use a tag to associate
#     information with a target in your configuration file, and retrieve that
#     information using the get_tags API or the has_tag API.
#
# Child elements:
#
#   config: A generic name-value configuration element.
#
#     Attributes:
#       name: Name of the configuration
#       value: Value of the configuration
#
#   overlay: An overlay to be mounted while building the target.
#
#     Attributes:
#
#       name: The name of the overlay.
#
#     Child elements:
#
#       replacement_path:  An overlay path that supersedes any conflicts
#         after it.
#
#         Properties:
#
#           name: The name of the replacement path. This path will will
#             superced the same path for any subsequent conflicts. If two
#             overlays have the same replacement path an error will occur.
#
#
#   view: A map (optionally) specifying a filesystem view mapping for each
#     target.
#
#     Attributes:
#
#       name: The name of the view.
#
#   allow_readwrite: A folder to mount read/write
#   inside the Android build nsjail. Each allowed read-write entry should be
#   accompanied by a bug that indicates why it was required and tracks the
#   progress to a fix.
#
#     Attributes:
#
#       path: The path to be allowed read-write mounting.
#
#   build_config: A list of goals to be used while building the target.
#
#     Attributes:
#
#       name: The name of the build config. Defaults to the target name
#         if not set.
#
#     Child elements:
#
#       goal: A build goal.
#
#         Properties:
#
#           name: The name of the build goal. The build tools pass the name
#             attribute as a parameter to make. This can have a value like
#             "droid" or "VAR=value".
#
#           contexts: A comma-separated list of the contexts in which this
#             goal applies. If this attribute is missing or blank, the goal
#             applies to all contexts. Otherwise, it applies only in the
#             requested contexts (see get_build_goals).

Overlay = collections.namedtuple('Overlay', ['name', 'replacement_paths'])


class BuildConfig(object):
  """Represents configuration of a build_target.

  Attributes:
    name: name of the build_target used to pull the configuration.
    android_target: The name of the android target used with lunch.
    tags: List of tags associated with the build target config
    build_goals: List of goals to be used while building the target.
    overlays: List of overlays to be mounted.
    views: A list of (source, destination) string path tuple to be mounted. See
      view nodes in XML.
    allow_readwrite_all: If true, mount source tree as rw.
    allow_readwrite: List of directories to be mounted as rw.
    allowed_projects_file: a string path name of a file with a containing
      allowed projects.
    configurations: a map of name to value configurations
  """

  def __init__(self,
               name,
               android_target,
               tags=frozenset(),
               build_goals=(),
               build_flags=(),
               overlays=(),
               views=(),
               allow_readwrite_all=False,
               allow_readwrite=(),
               allowed_projects_file=None,
               configurations=None):
    super().__init__()
    self.name = name
    self.android_target = android_target
    self.tags = tags
    self.build_goals = list(build_goals)
    self.build_flags = list(build_flags)
    self.overlays = list(overlays)
    self.views = list(views)
    self.allow_readwrite_all = allow_readwrite_all
    self.allow_readwrite = list(allow_readwrite)
    self.allowed_projects_file = allowed_projects_file
    self.configurations = configurations or {}

  def validate(self):
    """Run tests to validate build configuration"""
    if not self.name:
      raise ValueError('Error build_config must have a name.')
    # Validate that a build config does not contain an overlay with
    # conflicting replacement paths.
    if len(self.overlays) > 1 and set.intersection(
        *[o.replacement_paths for o in self.overlays]):
      raise ValueError(
          'Error build_config overlays have conflicting replacement_paths.')

  @classmethod
  def from_config(cls, config_elem, fs_view_map, base_config=None):
    """Creates a BuildConfig from a config XML element and an optional base_config.

    Args:
      config_elem: the config XML node element to build the configuration
      fs_view_map: A map of view names to list of tuple(source, destination)
        paths.
      base_config: the base BuildConfig to use

    Returns:
      A build config generated from the config element and the base
      configuration if provided.
    """
    if base_config is None:
      # Build a base_config with required elements from the new config_elem
      name = config_elem.get('name')
      base_config = cls(
          name=name, android_target=config_elem.get('android_target', name))

    return cls(
        android_target=config_elem.get('android_target',
                                       base_config.android_target),
        name=config_elem.get('name', base_config.name),
        allowed_projects_file=config_elem.get(
            'allowed_projects_file', base_config.allowed_projects_file),
        build_goals=_get_build_config_goals(config_elem,
                                            base_config.build_goals),
        build_flags=_get_build_config_flags(config_elem,
                                            base_config.build_flags),
        tags=_get_config_tags(config_elem, base_config.tags),
        overlays=_get_overlays(config_elem, base_config.overlays),
        allow_readwrite=_get_allow_readwrite(config_elem,
                                             base_config.allow_readwrite),
        views=_get_views(config_elem, fs_view_map, base_config.views),
        allow_readwrite_all=_get_allowed_readwrite_all(
            config_elem, base_config.allow_readwrite_all),
        configurations=_get_configurations(config_elem,
                                           base_config.configurations))


def _get_configurations(config_elem, base):
  configs = dict(base)
  configs.update({
      config.get('name'): config.get('value')
      for config in config_elem.findall('config')
  })
  return configs


def _get_build_config_goals(config_elem, base=None):
  """Retrieves goals from build_config or target.

  Args:
    config_elem: A build_config or target xml element.
    base: Initial list of goals to prepend to the list

  Returns:
    A list of tuples where the first element of the tuple is the build goal
    name, and the second is a list of the contexts to which this goal applies.
  """

  return base + [(goal.get('name'), set(goal.get('contexts').split(','))
                  if goal.get('contexts') else None)
                 for goal in config_elem.findall('goal')]


def _get_build_config_flags(config_elem, base=None):
  """See _get_build_config_goals. Gets 'flag' instead of 'goal'."""
  return base + [(goal.get('name'), set(goal.get('contexts').split(','))
                  if goal.get('contexts') else None)
                 for goal in config_elem.findall('flag')]


def _get_config_tags(config_elem, base=frozenset()):
  """Retrieves tags from build_config or target.

  Args:
    config_elem: A build_config or target xml element.
    base: Initial list of tags to seed the set

  Returns:
    A set of tags for a build_config.
  """
  tags = config_elem.get('tags')
  return base.union(set(tags.split(',')) if tags else set())


def _get_allowed_readwrite_all(config_elem, default=False):
  """Determines if build_config or target is set to allow readwrite for all source paths.

  Args:
    config_elem: A build_config or target xml element.
    default: Value to use if element doesn't contain the allow_readwrite_all
      attribute.

  Returns:
    True if build config is set to allow readwrite for all sorce paths
  """
  value = config_elem.get('allow_readwrite_all')
  return value == 'true' if value else default


def _get_overlays(config_elem, base=None):
  """Retrieves list of overlays from build_config or target.

  Args:
    config_elem: A build_config or target xml element.
    base: Initial list of overlays to prepend to the list

  Returns:
    A list of tuples of overlays and replacement paths to mount for a
    build_config or target.
  """
  overlays = []
  for overlay in config_elem.findall('overlay'):
    overlays.append(
        Overlay(
            name=overlay.get('name'),
            replacement_paths=set([
                path.get('path') for path in overlay.findall('replacement_path')
            ])))
  return base + overlays


def _get_views(config_elem, fs_view_map, base=None):
  """Retrieves list of views from build_config or target.

  Args:
    config_elem: A build_config or target xml element.
    base: Initial list of views to prepend to the list

  Returns:
    A list of (source, destination) string path tuple to be mounted. See view
      nodes in XML.
  """
  return base + [
      fs for o in config_elem.findall('view')
      for fs in fs_view_map[o.get('name')]
  ]


def _get_allow_readwrite(config_elem, base=None):
  """Retrieves list of directories to be mounted rw from build_config or target.

  Args:
    config_elem: A build_config or target xml element.
    base: Initial list of rw directories to prepend to the list

  Returns:
    A list of directories to be mounted rw.
  """
  return (base +
          [o.get('path') for o in config_elem.findall('allow_readwrite')])


def _get_fs_view_map(config):
  """Retrieves the map of filesystem views.

  Args:
    config: An XML Element that is the root of the config XML tree.

  Returns:
    A dict of filesystem views keyed by view name. A filesystem view is a
    list of (source, destination) string path tuples.
  """
  # A valid config file is not required to include FS Views, only overlay
  # targets.
  return {
      view.get('name'): [(path.get('source'), path.get('destination'))
                         for path in view.findall('path')
                        ] for view in config.findall('view')
  }


def _get_build_config_map(config):
  """Retrieves a map of all build config.

  Args:
    config: An XML Element that is the root of the config XML tree.

  Returns:
    A dict of BuildConfig keyed by build_target.
  """
  fs_view_map = _get_fs_view_map(config)
  build_config_map = {}
  for target_config in config.findall('target'):
    base_target = BuildConfig.from_config(target_config, fs_view_map)

    for build_config in target_config.findall('build_config'):
      build_target = BuildConfig.from_config(build_config, fs_view_map,
                                             base_target)
      build_target.validate()
      build_config_map[build_target.name] = build_target

  return build_config_map


class Config:
  """Presents an API to the static XML configuration."""

  def __init__(self, config_filename):
    """Initializes a Config instance from the specificed filename

    This method parses the XML content of the file named by config_filename
    into internal data structures. You can then use various methods to query
    the static config.

    Args:
      config_filename: The name of the file from which to load the config.
    """

    tree = ET.parse(config_filename)
    config = tree.getroot()
    self._build_config_map = _get_build_config_map(config)

  def get_available_build_targets(self):
    """Return a list of available build targets."""
    return sorted(self._build_config_map.keys())

  def get_tags(self, build_target):
    """Given a build_target, return the (possibly empty) set of tags."""
    return self._build_config_map[build_target].tags

  def has_tag(self, build_target, tag):
    """Return true if build_target has tag.

    Args:
      build_target: A string build_target to be queried.
      tag: A string tag that this target may have.

    Returns:
      If the build_target has the tag, True. Otherwise, False.
    """
    return tag in self._build_config_map[build_target].tags

  def get_allowed_projects_file(self, build_target):
    """Given a build_target, return a string with the allowed projects file."""
    return self._build_config_map[build_target].allowed_projects_file

  def get_build_config_android_target(self, build_target):
    """Given a build_target, return an android_target.

    Generally a build_target maps directory to the android_target of the same
    name, but they can differ. In a config.xml file, the name attribute of a
    target element is the android_target (which is used for lunch). The name
    attribute (if any) of a build_config element is the build_target. If a
    build_config element does not have a name attribute, then the build_target
    is the android_target.

    Args:
      build_target: A string build_target to be queried.

    Returns:
      A string android_target that can be used for lunch.
    """
    return self._build_config_map[build_target].android_target

  def get_build_goals(self, build_target, contexts=frozenset()):
    """Given a build_target and a context, return a list of build goals.

    For a given build_target, we may build in a variety of contexts. For
    example we might build in continuous integration, or we might build
    locally, or other contexts defined by the configuration file and scripts
    that use it. The contexts parameter is a set of strings that specify the
    contexts for which this function should retrieve goals.

    In the configuration file, each goal has a contexts attribute, which
    specifies the contexts to which the goal applies. We treat a goal with no
    contexts attribute as applying to all contexts.

    Example:

      <build_config>
        <goal name="droid"/>
        <goal name="dist" contexts="ota"/>
      </build_config>

      Here we have the goal "droid", which matches all contexts, and the goal
      "dist", which matches the "ota" context. Invoking this method with the
      set(['ota']) would return ['droid', 'dist'].

    Args:
      build_target: A string build_target to be queried.
      context: A set of contexts for which to retrieve goals.

    Returns:
      A list of strings, where each string is a goal to be passed to make.
    """

    build_goals = []
    for goal, build_contexts in self._build_config_map[
        build_target].build_goals:
      if not build_contexts:
        build_goals.append(goal)
      elif build_contexts.intersection(contexts):
        build_goals.append(goal)

    return build_goals

  def get_build_flags(self, build_target, contexts=frozenset()):
    """See get_build_goals. Gets flags instead of goals."""
    build_flags = []
    for flag, build_contexts in self._build_config_map[
        build_target].build_flags:
      if not build_contexts:
        build_flags.append(flag)
      elif build_contexts.intersection(contexts):
        build_flags.append(flag)

    return build_flags

  def get_rw_allowlist_map(self):
    """Return read-write allowlist map.

    Returns:
      A dict of string lists of keyed by target name. Each value in the dict is
      a list of allowed read-write paths corresponding to the target.
    """
    return {b.name: b.allow_readwrite for b in self._build_config_map.values()}

  def get_allow_readwrite_all(self, build_target):
    """Return True if the target should mount all its source as read-write.

    Args:
      build_target: A string build_target to be queried.

    Returns:
      True if the target should mount all its source as read-write.
    """
    return self._build_config_map[build_target].allow_readwrite_all

  def get_overlay_map(self):
    """Return the overlay map.

    Returns:
      A dict of keyed by target name. Each value in the dict is a list of
      overlay names corresponding to the target.
    """
    return {
        b.name: [o.name for o in b.overlays
                ] for b in self._build_config_map.values()
    }

  def get_fs_view_map(self):
    """Return the filesystem view map.

    Returns:
      A dict of filesystem views keyed by target name. A filesystem view is a
      list of (source, destination) string path tuples.
    """
    return {b.name: b.views for b in self._build_config_map.values()}

  def get_build_config(self, build_target):
    return self._build_config_map[build_target]


def factory(config_filename):
  """Create an instance of a Config class.

  Args:
    config_filename: The name of the file from which to load the config. This
      can be None, which results in this function returning None.

  Returns:
    If config_filename is None, returns None. Otherwise, a new instance of a
    Config class containing the configuration parsed from config_filename.
  """
  if config_filename is None:
    return None

  return Config(config_filename)
