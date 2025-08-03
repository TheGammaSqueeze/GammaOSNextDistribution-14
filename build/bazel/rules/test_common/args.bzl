# Copyright (C) 2022 The Android Open Source Project
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

def get_arg_value(args_list, arg_name):
    """
    Fetches the value of a named argument from the list of args provided by a
    Bazel action. If there are multiple instances of the arg present, this
    function will return the first. This function makes all the same assumptions
    as get_arg_values() below.

    Arguments:
        args_list (string[]): The list of arguments provided by the Bazel action.
                           i.e., bazel_action.argv
        arg_name (string): The name of the argument to fetch the value of
    Return:
        The value corresponding to the specified argument name
    """

    values = get_arg_values(args_list, arg_name)
    if len(values) == 0:
        return None

    if len(values) != 1:
        fail("More than one args found `%s`" % values)

    return values[0]

def get_arg_values(args_list, arg_name):
    """
    Fetches all the values of a named argument from the list of args provided
    by a Bazel action, the argument and its values can repeat multiple times
    and all the values will be returned.

    This function assumes that the only
    one argument is added per call to args.add() or per string passed to
    args.add_all(). It still works when two values are passed to
    args.add() as separate strings, however.

    The above assumption implies that this function does not handle cases where
    an argument name is separated from its value by an =, or any character
    other than a space, in the final command.

    Arguments:
        args_list (string[]): The list of arguments provided by the Bazel action.
                           i.e., bazel_action.argv
        arg_name (string): The name of the argument to fetch the value of
    Return:
        All the values corresponding to the specified argument name
    """

    values = []
    for i in range(1, len(args_list) - 1):
        if args_list[i] == arg_name:
            values.append(args_list[i + 1])

    return values

def get_all_args_with_prefix(input_args, arg_prefix):
    """returns all arguments that start with arg_prefix

    Args:
        input_args (list[str]): list of arguments
        arg_prefix (str): prefix of argument to search for
    Returns:
        args (list[str]): value in args that start with arg_prefix
    """
    args = []
    for a in input_args:
        if a.startswith(arg_prefix):
            args.append(a[len(arg_prefix):])
    return args

def get_single_arg_with_prefix(input_args, arg_prefix):
    """returns all arguments that start with arg_prefix

    Fails if more than one argument exists.

    Args:
        input_args (list[str]): list of arguments
        arg_prefix (str): prefix of argument to search for
    Returns:
        args (str): value in args that starts with arg_prefix
    """
    args = get_all_args_with_prefix(input_args, arg_prefix)
    if len(args) != 1:
        fail("expected single argument with prefix `%s`, got %d; args = `%s`" % (arg_prefix, len(args), args))
    return args[0]
