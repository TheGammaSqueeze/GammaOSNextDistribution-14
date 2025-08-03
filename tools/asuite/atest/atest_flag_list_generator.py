# Copyright 2022, The Android Open Source Project
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

"""Script that generates arguments for autocompletion."""

import argparse

from atest import arg_parser


def _get_optional_args(parser: argparse.ArgumentParser) -> list[str]:
  """Get args from actions and return optional args.

  Returns:
      A list of optional arguments.
  """
  argument_list = []
  # The output of _get_optional_actions(): [['-t', '--test']]
  # return an argument list: ['-t', '--test']
  for arg in parser._get_optional_actions():
    argument_list.extend(arg.option_strings)
  return argument_list


if __name__ == '__main__':
  print('\n'.join(_get_optional_args(arg_parser.create_atest_arg_parser())))
