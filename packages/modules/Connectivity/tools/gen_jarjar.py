#
# Copyright (C) 2022 The Android Open Source Project
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

""" This script generates jarjar rule files to add a jarjar prefix to all classes, except those
that are API, unsupported API or otherwise excluded."""

import argparse
import io
import re
import subprocess
from xml import sax
from xml.sax.handler import ContentHandler
from zipfile import ZipFile


def parse_arguments(argv):
    parser = argparse.ArgumentParser()
    parser.add_argument(
        'jars', nargs='+',
        help='Path to pre-jarjar JAR. Multiple jars can be specified.')
    parser.add_argument(
        '--prefix', required=True,
        help='Package prefix to use for jarjared classes, '
             'for example "com.android.connectivity" (does not end with a dot).')
    parser.add_argument(
        '--output', required=True, help='Path to output jarjar rules file.')
    parser.add_argument(
        '--apistubs', action='append', default=[],
        help='Path to API stubs jar. Classes that are API will not be jarjared. Can be repeated to '
             'specify multiple jars.')
    parser.add_argument(
        '--unsupportedapi',
        help='Column(:)-separated paths to UnsupportedAppUsage hidden API .txt lists. '
             'Classes that have UnsupportedAppUsage API will not be jarjared.')
    parser.add_argument(
        '--excludes', action='append', default=[],
        help='Path to files listing classes that should not be jarjared. Can be repeated to '
             'specify multiple files.'
             'Each file should contain one full-match regex per line. Empty lines or lines '
             'starting with "#" are ignored.')
    return parser.parse_args(argv)


def _list_toplevel_jar_classes(jar):
    """List all classes in a .class .jar file that are not inner classes."""
    return {_get_toplevel_class(c) for c in _list_jar_classes(jar)}

def _list_jar_classes(jar):
    with ZipFile(jar, 'r') as zip:
        files = zip.namelist()
        assert 'classes.dex' not in files, f'Jar file {jar} is dexed, ' \
                                           'expected an intermediate zip of .class files'
        class_len = len('.class')
        return [f.replace('/', '.')[:-class_len] for f in files
                if f.endswith('.class') and not f.endswith('/package-info.class')]


def _list_hiddenapi_classes(txt_file):
    out = set()
    with open(txt_file, 'r') as f:
        for line in f:
            if not line.strip():
                continue
            assert line.startswith('L') and ';' in line, f'Class name not recognized: {line}'
            clazz = line.replace('/', '.').split(';')[0][1:]
            out.add(_get_toplevel_class(clazz))
    return out


def _get_toplevel_class(clazz):
    """Return the name of the toplevel (not an inner class) enclosing class of the given class."""
    if '$' not in clazz:
        return clazz
    return clazz.split('$')[0]


def _get_excludes(path):
    out = []
    with open(path, 'r') as f:
        for line in f:
            stripped = line.strip()
            if not stripped or stripped.startswith('#'):
                continue
            out.append(re.compile(stripped))
    return out


def make_jarjar_rules(args):
    excluded_classes = set()
    for apistubs_file in args.apistubs:
        excluded_classes.update(_list_toplevel_jar_classes(apistubs_file))

    unsupportedapi_files = (args.unsupportedapi and args.unsupportedapi.split(':')) or []
    for unsupportedapi_file in unsupportedapi_files:
        if unsupportedapi_file:
            excluded_classes.update(_list_hiddenapi_classes(unsupportedapi_file))

    exclude_regexes = []
    for exclude_file in args.excludes:
        exclude_regexes.extend(_get_excludes(exclude_file))

    with open(args.output, 'w') as outfile:
        for jar in args.jars:
            jar_classes = _list_jar_classes(jar)
            jar_classes.sort()
            for clazz in jar_classes:
                if (not clazz.startswith(args.prefix + '.') and
                        _get_toplevel_class(clazz) not in excluded_classes and
                        not any(r.fullmatch(clazz) for r in exclude_regexes)):
                    outfile.write(f'rule {clazz} {args.prefix}.@0\n')
                    # Also include jarjar rules for unit tests of the class if it's not explicitly
                    # excluded, so the package matches
                    if not any(r.fullmatch(clazz + 'Test') for r in exclude_regexes):
                        outfile.write(f'rule {clazz}Test {args.prefix}.@0\n')
                        outfile.write(f'rule {clazz}Test$* {args.prefix}.@0\n')


def _main():
    # Pass in None to use argv
    args = parse_arguments(None)
    make_jarjar_rules(args)


if __name__ == '__main__':
    _main()
