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
"""Builds an Android target in a secure sandbox."""

import argparse
import os
from . import config
from . import nsjail
from . import rbe

_DEFAULT_COMMAND_WRAPPER = \
  '/src/tools/treble/build/sandbox/build_android_target.sh'


def build(build_target,
          release_target,
          variant,
          nsjail_bin,
          chroot,
          dist_dir,
          build_id,
          max_cpus,
          build_goals,
          config_file=None,
          command_wrapper=_DEFAULT_COMMAND_WRAPPER,
          use_rbe=False,
          readonly_bind_mounts=[],
          env=[]):
  """Builds an Android target in a secure sandbox.

  Args:
    build_target: A string with the name of the build target.
    release_target: The release target config, e.g., next, trunk_food, ...
    variant: A string with the build variant.
    nsjail_bin: A string with the path to the nsjail binary.
    chroot: A string with the path to the chroot of the NsJail sandbox.
    dist_dir: A string with the path to the Android dist directory.
    build_id: A string with the Android build identifier.
    max_cpus: An integer with maximum number of CPUs.
    build_goals: A list of strings with the goals and options to provide to the
      build command.
    config_file: A string path to an overlay configuration file.
    command_wrapper: A string path to the command wrapper.
    use_rbe: If true, will attempt to use RBE for the build.
    readonly_bind_mounts: A list of string paths to be mounted as read-only.
    env: An array of environment variables to define in the NsJail sandbox in
      the `var=val` syntax.

  Returns:
    A list of commands that were executed. Each command is a list of strings.
  """
  if config_file:
    cfg = config.Config(config_file)
    android_target = cfg.get_build_config_android_target(build_target)
    if cfg.has_tag(build_target, 'skip'):
      print('Warning: skipping build_target "{}" due to tag being set'.format(
          build_target))
      return []
  else:
    android_target = build_target

  # All builds are required to run with the root of the
  # Android source tree as the current directory.
  source_dir = os.getcwd()
  command = [
      command_wrapper,
      '%s-%s-%s' % (android_target, release_target, variant),
      '/src',
      'make',
      '-j',
  ] + build_goals

  extra_nsjail_args = []
  cleanup = lambda: None
  nsjail_wrapper = []
  if use_rbe:
    cleanup = rbe.setup(env)
    env = rbe.prepare_env(env)
    extra_nsjail_args.extend(rbe.get_extra_nsjail_args())
    readonly_bind_mounts.extend(rbe.get_readonlybind_mounts())
    nsjail_wrapper = rbe.get_nsjail_bin_wrapper()

  ret = nsjail.run(
      nsjail_bin=nsjail_bin,
      chroot=chroot,
      overlay_config=config_file,
      source_dir=source_dir,
      command=command,
      build_target=build_target,
      dist_dir=dist_dir,
      build_id=build_id,
      max_cpus=max_cpus,
      extra_nsjail_args=extra_nsjail_args,
      readonly_bind_mounts=readonly_bind_mounts,
      env=env,
      nsjail_wrapper=nsjail_wrapper)

  cleanup()

  return ret


def arg_parser():
  """Returns an ArgumentParser for sanboxed android builds."""
  # Use the top level module docstring for the help description
  parser = argparse.ArgumentParser(
      description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
  parser.add_argument('--build_target', help='The build target.')
  parser.add_argument(
      '--release_target',
      required=True,
      help='Release target config, e.g., next, trunk_food, trunk_staging, ...')
  parser.add_argument(
      '--variant', default='userdebug', help='The Android build variant.')
  parser.add_argument(
      '--nsjail_bin', required=True, help='Path to NsJail binary.')
  parser.add_argument(
      '--chroot',
      required=True,
      help='Path to the chroot to be used for building the Android '
      'platform. This will be mounted as the root filesystem in the '
      'NsJail sandbox.')
  parser.add_argument(
      '--config_file',
      required=True,
      help='Path to the overlay configuration file.')
  parser.add_argument(
      '--command_wrapper',
      default=_DEFAULT_COMMAND_WRAPPER,
      help='Path to the command wrapper. '
      'Defaults to \'%s\'.' % _DEFAULT_COMMAND_WRAPPER)
  parser.add_argument(
      '--readonly_bind_mount',
      type=str,
      default=[],
      action='append',
      help='Path to the a path to be mounted as readonly inside the secure '
      'build sandbox. Can be specified multiple times')
  parser.add_argument(
      '--env',
      '-e',
      type=str,
      default=[],
      action='append',
      help='Specify an environment variable to the NSJail sandbox. Can be specified '
      'muliple times. Syntax: var_name=value')
  parser.add_argument(
      '--dist_dir',
      help='Path to the Android dist directory. This is where '
      'Android platform release artifacts will be written.')
  parser.add_argument(
      '--build_id',
      help='Build identifier what will label the Android platform '
      'release artifacts.')
  parser.add_argument(
      '--max_cpus',
      type=int,
      help='Limit of concurrent CPU cores that the NsJail sanbox '
      'can use.')
  parser.add_argument(
      '--context',
      action='append',
      default=[],
      help='One or more contexts used to select build goals from the '
      'configuration.')
  parser.add_argument(
      '--use_rbe', action='store_true', help='Executes the build on RBE')
  return parser


def parse_args(parser):
  """Parses command line arguments.

  Returns:
    A dict of all the arguments parsed.
  """
  # Convert the Namespace object to a dict
  return vars(parser.parse_args())


def main():
  args = parse_args(arg_parser())

  # The --build_target argument could not be required
  # using the standard 'required' argparse option because
  # the argparser is reused by merge_android_sandboxed.py which
  # does not require --build_target.
  if args['build_target'] is None:
    raise ValueError('--build_target is required.')

  cfg = config.Config(args['config_file'])
  build_goals = cfg.get_build_goals(args['build_target'], set(args['context']))
  build_flags = cfg.get_build_flags(args['build_target'], set(args['context']))

  build(
      build_target=args['build_target'],
      release_target=args['release_target'],
      variant=args['variant'],
      nsjail_bin=args['nsjail_bin'],
      chroot=args['chroot'],
      config_file=args['config_file'],
      command_wrapper=args['command_wrapper'],
      readonly_bind_mounts=args['readonly_bind_mount'],
      env=args['env'],
      dist_dir=args['dist_dir'],
      build_id=args['build_id'],
      max_cpus=args['max_cpus'],
      use_rbe=args['use_rbe'],
      build_goals=build_goals + build_flags)


if __name__ == '__main__':
  main()
