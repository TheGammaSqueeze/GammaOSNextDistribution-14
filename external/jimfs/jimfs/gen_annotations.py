#!/usr/bin/env python3

# Copyright (C) 2020 The Android Open Source Project
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
"""Generates stubs for annotations that aren't in the Android source tree."""

import pathlib
import string
import sys

_ANNOTATIONS_CLASSES = ['org.checkerframework.checker.nullness.compatqual.NullableDecl']

_CLASS_TEMPLATE = string.Template("""
package ${package_name};

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({
    ElementType.ANNOTATION_TYPE,
    ElementType.CONSTRUCTOR,
    ElementType.FIELD,
    ElementType.LOCAL_VARIABLE,
    ElementType.METHOD,
    ElementType.PACKAGE,
    ElementType.PARAMETER,
    ElementType.TYPE,
    ElementType.TYPE_PARAMETER,
    ElementType.TYPE_USE})
@Retention(RetentionPolicy.SOURCE)
public @interface ${class_name} {}
""")

if __name__ == '__main__':
  out_dir = pathlib.Path(sys.argv[1])

  for c in _ANNOTATIONS_CLASSES:
    parts = c.split('.')
    src_path = out_dir.joinpath(*parts).with_suffix('.java')
    src_path.parent.mkdir(parents=True)
    src_path.write_text(
        _CLASS_TEMPLATE.substitute(
            package_name='.'.join(parts[:-1]), class_name=parts[-1]))
