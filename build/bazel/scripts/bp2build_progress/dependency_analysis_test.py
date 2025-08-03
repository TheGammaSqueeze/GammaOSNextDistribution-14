#!/usr/bin/env python3

# Copyright (C) 2022 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
"""Tests for dependency_analysis.py."""

import unittest
import dependency_analysis
import queryview_xml
import soong_module_json


class DependencyAnalysisTest(unittest.TestCase):

  def test_visit_json_module_graph_post_order_visits_all_in_post_order(self):
    graph = [
        soong_module_json.make_module(
            'q',
            'module',
            [
                soong_module_json.make_dep('a'),
                soong_module_json.make_dep('b'),
            ],
        ),
        soong_module_json.make_module(
            'a',
            'module',
            [
                soong_module_json.make_dep('b'),
                soong_module_json.make_dep('c'),
            ],
        ),
        soong_module_json.make_module(
            'b',
            'module',
            [
                soong_module_json.make_dep('d'),
            ],
        ),
        soong_module_json.make_module(
            'c',
            'module',
            [
                soong_module_json.make_dep('e'),
            ],
        ),
        soong_module_json.make_module('d', 'module', []),
        soong_module_json.make_module('e', 'module', []),
    ]

    def only_a(json):
      return json['Name'] == 'a'

    visited_modules = []

    def visit(module, _):
      visited_modules.append(module['Name'])

    dependency_analysis.visit_json_module_graph_post_order(
        graph, set(), False, only_a, visit
    )

    expected_visited = ['d', 'b', 'e', 'c', 'a']
    self.assertListEqual(visited_modules, expected_visited)

  def test_visit_json_module_graph_post_order_visits_all_variants_in_post_order(
      self,
  ):
    graph = [
        soong_module_json.make_module(
            'd',
            'module',
            [
                soong_module_json.make_dep('g'),
            ],
            variations=[soong_module_json.make_variation('foo', '1')],
        ),
        soong_module_json.make_module(
            'd',
            'module',
            [
                soong_module_json.make_dep('f'),
            ],
            variations=[soong_module_json.make_variation('foo', '2')],
        ),
        soong_module_json.make_module('g', 'module', []),
        soong_module_json.make_module('f', 'module', []),
        soong_module_json.make_module('e', 'module', []),
        soong_module_json.make_module(
            'c',
            'module',
            [
                soong_module_json.make_dep('e'),
            ],
        ),
        soong_module_json.make_module(
            'a',
            'module',
            [
                soong_module_json.make_dep('b'),
                soong_module_json.make_dep('c'),
            ],
        ),
        soong_module_json.make_module(
            'b',
            'module',
            [
                soong_module_json.make_dep(
                    'd',
                    variations=[
                        soong_module_json.make_variation('foo', '1'),
                    ],
                ),
            ],
        ),
        soong_module_json.make_module(
            'q',
            'module',
            [
                soong_module_json.make_dep('a'),
                soong_module_json.make_dep('b'),
            ],
        ),
    ]

    def only_a(json):
      return json['Name'] == 'a'

    visited_modules = []

    def visit(module, _):
      visited_modules.append(module['Name'])

    dependency_analysis.visit_json_module_graph_post_order(
        graph, set(), False, only_a, visit
    )

    expected_visited = ['g', 'f', 'd', 'd', 'b', 'e', 'c', 'a']
    self.assertListEqual(visited_modules, expected_visited)

  def test_visit_json_module_graph_post_order_skips_ignored_by_name_and_transitive(
      self,
  ):
    graph = [
        soong_module_json.make_module(
            'a',
            'module',
            [
                soong_module_json.make_dep('b'),
                soong_module_json.make_dep('c'),
            ],
        ),
        soong_module_json.make_module(
            'b',
            'module',
            [
                soong_module_json.make_dep('d'),
            ],
        ),
        soong_module_json.make_module(
            'c',
            'module',
            [
                soong_module_json.make_dep('e'),
            ],
        ),
        soong_module_json.make_module('d', 'module', []),
        soong_module_json.make_module('e', 'module', []),
    ]

    def only_a(json):
      return json['Name'] == 'a'

    visited_modules = []

    def visit(module, _):
      visited_modules.append(module['Name'])

    dependency_analysis.visit_json_module_graph_post_order(
        graph, set('b'), False, only_a, visit
    )

    expected_visited = ['e', 'c', 'a']
    self.assertListEqual(visited_modules, expected_visited)

  def test_visit_json_module_graph_post_order_skips_defaults_and_transitive(
      self,
  ):
    graph = [
        soong_module_json.make_module(
            'a',
            'module',
            [
                soong_module_json.make_dep('b'),
                soong_module_json.make_dep('c'),
            ],
        ),
        soong_module_json.make_module(
            'b',
            'module_defaults',
            [
                soong_module_json.make_dep('d'),
            ],
        ),
        soong_module_json.make_module(
            'c',
            'module',
            [
                soong_module_json.make_dep('e'),
            ],
        ),
        soong_module_json.make_module('d', 'module', []),
        soong_module_json.make_module('e', 'module', []),
    ]

    def only_a(json):
      return json['Name'] == 'a'

    visited_modules = []

    def visit(module, _):
      visited_modules.append(module['Name'])

    dependency_analysis.visit_json_module_graph_post_order(
        graph, set(), False, only_a, visit
    )

    expected_visited = ['e', 'c', 'a']
    self.assertListEqual(visited_modules, expected_visited)

  def test_visit_json_module_graph_post_order_skips_windows_and_transitive(
      self,
  ):
    windows_variation = soong_module_json.make_variation('os', 'windows')
    graph = [
        soong_module_json.make_module(
            'a',
            'module',
            [
                soong_module_json.make_dep('b', variations=[windows_variation]),
                soong_module_json.make_dep('c'),
            ],
        ),
        soong_module_json.make_module(
            'b',
            'module',
            [
                soong_module_json.make_dep('d'),
            ],
            variations=[windows_variation],
        ),
        soong_module_json.make_module(
            'c',
            'module',
            [
                soong_module_json.make_dep('e'),
            ],
        ),
        soong_module_json.make_module('d', 'module', []),
        soong_module_json.make_module('e', 'module', []),
    ]

    def only_a(json):
      return json['Name'] == 'a'

    visited_modules = []

    def visit(module, _):
      visited_modules.append(module['Name'])

    dependency_analysis.visit_json_module_graph_post_order(
        graph, set(), False, only_a, visit
    )

    expected_visited = ['e', 'c', 'a']
    self.assertListEqual(visited_modules, expected_visited)

  def test_visit_json_module_graph_post_order_skips_prebuilt_tag_deps(self):
    graph = [
        soong_module_json.make_module(
            'a',
            'module',
            [
                soong_module_json.make_dep(
                    'b', 'android.prebuiltDependencyTag {BaseDependencyTag:{}}'
                ),
                soong_module_json.make_dep('c'),
            ],
        ),
        soong_module_json.make_module(
            'b',
            'module',
            [
                soong_module_json.make_dep('d'),
            ],
        ),
        soong_module_json.make_module(
            'c',
            'module',
            [
                soong_module_json.make_dep('e'),
            ],
        ),
        soong_module_json.make_module('d', 'module', []),
        soong_module_json.make_module('e', 'module', []),
    ]

    def only_a(json):
      return json['Name'] == 'a'

    visited_modules = []

    def visit(module, _):
      visited_modules.append(module['Name'])

    dependency_analysis.visit_json_module_graph_post_order(
        graph, set(), False, only_a, visit
    )

    expected_visited = ['e', 'c', 'a']
    self.assertListEqual(visited_modules, expected_visited)

  def test_visit_json_module_graph_post_order_no_infinite_loop_for_self_dep(
      self,
  ):
    graph = [
        soong_module_json.make_module(
            'a', 'module', [soong_module_json.make_dep('a')]
        ),
    ]

    def only_a(json):
      return json['Name'] == 'a'

    visited_modules = []

    def visit(module, _):
      visited_modules.append(module['Name'])

    dependency_analysis.visit_json_module_graph_post_order(
        graph, set(), False, only_a, visit
    )

    expected_visited = ['a']
    self.assertListEqual(visited_modules, expected_visited)

  def test_visit_json_module_graph_post_order_visits_all_variants(self):
    m1_variation = [soong_module_json.make_variation('m', '1')]
    graph = [
        soong_module_json.make_module(
            'a',
            'module',
            [
                soong_module_json.make_dep('b', variations=m1_variation),
            ],
            variations=[soong_module_json.make_variation('m', '1')],
        ),
        soong_module_json.make_module(
            'a',
            'module',
            [
                soong_module_json.make_dep('c', variations=m1_variation),
            ],
            variations=[soong_module_json.make_variation('m', '2')],
        ),
        soong_module_json.make_module(
            'b',
            'module',
            [
                soong_module_json.make_dep('d', variations=m1_variation),
            ],
            variations=[soong_module_json.make_variation('m', '1')],
        ),
        soong_module_json.make_module(
            'c',
            'module',
            [
                soong_module_json.make_dep('e', variations=m1_variation),
            ],
            variations=[soong_module_json.make_variation('m', '1')],
        ),
        soong_module_json.make_module(
            'd',
            'module',
            [],
            variations=[soong_module_json.make_variation('m', '1')],
        ),
        soong_module_json.make_module(
            'e',
            'module',
            [],
            variations=[soong_module_json.make_variation('m', '1')],
        ),
    ]

    def only_a(json):
      return json['Name'] == 'a'

    visited_modules = []

    def visit(module, _):
      visited_modules.append(module['Name'])

    dependency_analysis.visit_json_module_graph_post_order(
        graph, set(), False, only_a, visit
    )

    expected_visited = ['d', 'b', 'e', 'c', 'a', 'a']
    self.assertListEqual(visited_modules, expected_visited)

  def test_visit_json_module_skips_filegroup_with_src_same_as_name(self):
    graph = [
        soong_module_json.make_module(
            'a',
            'filegroup',
            [
                soong_module_json.make_dep('b'),
            ],
            json_props=[
                soong_module_json.make_property(
                    name='Srcs',
                    values=['other_file'],
                ),
            ],
        ),
        soong_module_json.make_module(
            'b',
            'filegroup',
            json_props=[
                soong_module_json.make_property(
                    name='Srcs',
                    values=['b'],
                ),
            ],
        ),
    ]

    def only_a(json):
      return json['Name'] == 'a'

    visited_modules = []

    def visit(module, _):
      visited_modules.append(module['Name'])

    dependency_analysis.visit_json_module_graph_post_order(
        graph, set(), False, only_a, visit
    )

    expected_visited = ['a']
    self.assertListEqual(visited_modules, expected_visited)

  def test_visit_json_module_graph_post_order_include_created_by(self):
    graph = [
        soong_module_json.make_module(
            'a',
            'module',
            [
                soong_module_json.make_dep('b'),
                soong_module_json.make_dep('c'),
            ],
        ),
        soong_module_json.make_module('b', 'module', created_by='d'),
        soong_module_json.make_module(
            'c',
            'module',
            [
                soong_module_json.make_dep('e'),
            ],
        ),
        soong_module_json.make_module('d', 'module', []),
        soong_module_json.make_module('e', 'module', []),
    ]

    def only_a(json):
      return json['Name'] == 'a'

    visited_modules = []

    def visit(module, _):
      visited_modules.append(module['Name'])

    dependency_analysis.visit_json_module_graph_post_order(
        graph, set(), False, only_a, visit
    )

    expected_visited = ['d', 'b', 'e', 'c', 'a']
    self.assertListEqual(visited_modules, expected_visited)

  def test_visit_json_module_graph_post_order_include_required(self):
    graph = [
        soong_module_json.make_module(
            'a',
            'module',
            [
                soong_module_json.make_dep('b'),
            ],
        ),
        soong_module_json.make_module(
            'b',
            'module',
            json_props=[
                soong_module_json.make_property(
                    # we explicitly specify a non-Soong module because there can
                    # be Soong -> Kati edges in Required
                    'Required', values=['c', 'not_soong']
                ),
                soong_module_json.make_property('Host_required', values=['d']),
                soong_module_json.make_property(
                    'Target_required', values=['e']
                ),
                soong_module_json.make_property('Linux.Host_required', values=['f']),
            ],
        ),
        soong_module_json.make_module('c', 'module', []),
        soong_module_json.make_module('d', 'module', []),
        soong_module_json.make_module('e', 'module', []),
        soong_module_json.make_module('f', 'module', []),
    ]

    def only_a(json):
      return json['Name'] == 'a'

    visited_modules = []

    def visit(module, _):
      visited_modules.append(module['Name'])

    dependency_analysis.visit_json_module_graph_post_order(
        graph, set(), False, only_a, visit
    )

    expected_visited = ['c', 'd', 'e', 'f', 'b', 'a']
    self.assertListEqual(visited_modules, expected_visited)

  def test_visit_queryview_xml_module_graph_post_order_visits_all(self):
    graph = queryview_xml.make_graph([
        queryview_xml.make_module(
            '//pkg:a', 'a', 'module', dep_names=['//pkg:b', '//pkg:c']
        ),
        queryview_xml.make_module(
            '//pkg:b', 'b', 'module', dep_names=['//pkg:d']
        ),
        queryview_xml.make_module(
            '//pkg:c', 'c', 'module', dep_names=['//pkg:e']
        ),
        queryview_xml.make_module('//pkg:d', 'd', 'module'),
        queryview_xml.make_module('//pkg:e', 'e', 'module'),
    ])

    def only_a(module):
      return module.name == 'a'

    visited_modules = []

    def visit(module, _):
      visited_modules.append(module.name)

    dependency_analysis.visit_queryview_xml_module_graph_post_order(
        graph, set(), only_a, visit
    )

    expected_visited = ['d', 'b', 'e', 'c', 'a']
    self.assertListEqual(visited_modules, expected_visited)

  def test_visit_queryview_xml_module_graph_post_order_skips_ignore_by_name(
      self,
  ):
    graph = queryview_xml.make_graph([
        queryview_xml.make_module(
            '//pkg:a', 'a', 'module', dep_names=['//pkg:b', '//pkg:c']
        ),
        queryview_xml.make_module(
            '//pkg:b', 'b', 'module', dep_names=['//pkg:d']
        ),
        queryview_xml.make_module(
            '//pkg:c', 'c', 'module', dep_names=['//pkg:e']
        ),
        queryview_xml.make_module('//pkg:d', 'd', 'module'),
        queryview_xml.make_module('//pkg:e', 'e', 'module'),
    ])

    def only_a(module):
      return module.name == 'a'

    visited_modules = []

    def visit(module, _):
      visited_modules.append(module.name)

    dependency_analysis.visit_queryview_xml_module_graph_post_order(
        graph, set('b'), only_a, visit
    )

    expected_visited = ['e', 'c', 'a']
    self.assertListEqual(visited_modules, expected_visited)

  def test_visit_queryview_xml_module_graph_post_order_skips_default(self):
    graph = queryview_xml.make_graph([
        queryview_xml.make_module(
            '//pkg:a', 'a', 'module', dep_names=['//pkg:b', '//pkg:c']
        ),
        queryview_xml.make_module(
            '//pkg:b', 'b', 'module_defaults', dep_names=['//pkg:d']
        ),
        queryview_xml.make_module(
            '//pkg:c', 'c', 'module', dep_names=['//pkg:e']
        ),
        queryview_xml.make_module('//pkg:d', 'd', 'module'),
        queryview_xml.make_module('//pkg:e', 'e', 'module'),
    ])

    def only_a(module):
      return module.name == 'a'

    visited_modules = []

    def visit(module, _):
      visited_modules.append(module.name)

    dependency_analysis.visit_queryview_xml_module_graph_post_order(
        graph, set(), only_a, visit
    )

    expected_visited = ['e', 'c', 'a']
    self.assertListEqual(visited_modules, expected_visited)

  def test_visit_queryview_xml_module_graph_post_order_skips_cc_prebuilt(self):
    graph = queryview_xml.make_graph([
        queryview_xml.make_module(
            '//pkg:a', 'a', 'module', dep_names=['//pkg:b', '//pkg:c']
        ),
        queryview_xml.make_module(
            '//pkg:b', 'b', 'cc_prebuilt_library', dep_names=['//pkg:d']
        ),
        queryview_xml.make_module(
            '//pkg:c', 'c', 'module', dep_names=['//pkg:e']
        ),
        queryview_xml.make_module('//pkg:d', 'd', 'module'),
        queryview_xml.make_module('//pkg:e', 'e', 'module'),
    ])

    def only_a(module):
      return module.name == 'a'

    visited_modules = []

    def visit(module, _):
      visited_modules.append(module.name)

    dependency_analysis.visit_queryview_xml_module_graph_post_order(
        graph, set(), only_a, visit
    )

    expected_visited = ['e', 'c', 'a']
    self.assertListEqual(visited_modules, expected_visited)

  def test_visit_queryview_xml_module_graph_post_order_skips_filegroup_duplicate_name(
      self,
  ):
    graph = queryview_xml.make_graph([
        queryview_xml.make_module(
            '//pkg:a', 'a', 'module', dep_names=['//pkg:b', '//pkg:c']
        ),
        queryview_xml.make_module(
            '//pkg:b', 'b', 'filegroup', dep_names=['//pkg:d'], srcs=['b']
        ),
        queryview_xml.make_module(
            '//pkg:c', 'c', 'module', dep_names=['//pkg:e']
        ),
        queryview_xml.make_module('//pkg:d', 'd', 'module'),
        queryview_xml.make_module('//pkg:e', 'e', 'module'),
    ])

    def only_a(module):
      return module.name == 'a'

    visited_modules = []

    def visit(module, _):
      visited_modules.append(module.name)

    dependency_analysis.visit_queryview_xml_module_graph_post_order(
        graph, set(), only_a, visit
    )

    expected_visited = ['e', 'c', 'a']
    self.assertListEqual(visited_modules, expected_visited)

  def test_visit_queryview_xml_module_graph_post_order_skips_windows(self):
    graph = queryview_xml.make_graph([
        queryview_xml.make_module(
            '//pkg:a', 'a', 'module', dep_names=['//pkg:b', '//pkg:c']
        ),
        queryview_xml.make_module(
            '//pkg:b',
            'b',
            'module',
            dep_names=['//pkg:d'],
            variant='windows-x86',
        ),
        queryview_xml.make_module(
            '//pkg:c', 'c', 'module', dep_names=['//pkg:e']
        ),
        queryview_xml.make_module('//pkg:d', 'd', 'module'),
        queryview_xml.make_module('//pkg:e', 'e', 'module'),
    ])

    def only_a(module):
      return module.name == 'a'

    visited_modules = []

    def visit(module, _):
      visited_modules.append(module.name)

    dependency_analysis.visit_queryview_xml_module_graph_post_order(
        graph, set(), only_a, visit
    )

    expected_visited = ['e', 'c', 'a']
    self.assertListEqual(visited_modules, expected_visited)

  def test_visit_queryview_xml_module_graph_post_order_self_dep_no_infinite_loop(
      self,
  ):
    graph = queryview_xml.make_graph([
        queryview_xml.make_module(
            '//pkg:a', 'a', 'module', dep_names=['//pkg:b--variant1', '//pkg:c']
        ),
        queryview_xml.make_module(
            '//pkg:b--variant1',
            'b',
            'module',
            variant='variant1',
            dep_names=['//pkg:b--variant2'],
        ),
        queryview_xml.make_module(
            '//pkg:b--variant2',
            'b',
            'module',
            variant='variant2',
            dep_names=['//pkg:d'],
        ),
        queryview_xml.make_module(
            '//pkg:c', 'c', 'module', dep_names=['//pkg:e']
        ),
        queryview_xml.make_module('//pkg:d', 'd', 'module'),
        queryview_xml.make_module('//pkg:e', 'e', 'module'),
    ])

    def only_a(module):
      return module.name == 'a'

    visited_modules = []

    def visit(module, _):
      visited_modules.append(module.name)

    dependency_analysis.visit_queryview_xml_module_graph_post_order(
        graph, set(), only_a, visit
    )

    expected_visited = ['d', 'b', 'b', 'e', 'c', 'a']
    self.assertListEqual(visited_modules, expected_visited)

  def test_visit_queryview_xml_module_graph_post_order_skips_prebuilt_with_same_name(
      self,
  ):
    graph = queryview_xml.make_graph([
        queryview_xml.make_module(
            '//pkg:a',
            'a',
            'module',
            dep_names=['//other_pkg:prebuilt_a', '//pkg:b', '//pkg:c'],
        ),
        queryview_xml.make_module(
            '//other_pkg:prebuilt_a', 'prebuilt_a', 'prebuilt_module'
        ),
        queryview_xml.make_module(
            '//pkg:b', 'b', 'module', dep_names=['//pkg:d']
        ),
        queryview_xml.make_module(
            '//pkg:c', 'c', 'module', dep_names=['//pkg:e']
        ),
        queryview_xml.make_module('//pkg:d', 'd', 'module'),
        queryview_xml.make_module('//pkg:e', 'e', 'module'),
    ])

    def only_a(module):
      return module.name == 'a'

    visited_modules = []

    def visit(module, _):
      visited_modules.append(module.name)

    dependency_analysis.visit_queryview_xml_module_graph_post_order(
        graph, set(), only_a, visit
    )

    expected_visited = ['d', 'b', 'e', 'c', 'a']
    self.assertListEqual(visited_modules, expected_visited)


if __name__ == '__main__':
  unittest.main()
