#!/usr/bin/env python3
#
# Copyright (C) 2021 The Android Open Source Project
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
"""Tests for bp2build-progress."""

import collections
import datetime
import unittest
import unittest.mock
from bp2build_metrics_proto.bp2build_metrics_pb2 import Bp2BuildMetrics
import bp2build_pb2
import bp2build_progress
import dependency_analysis
import queryview_xml
import soong_module_json

_queryview_graph = queryview_xml.make_graph([
    queryview_xml.make_module(
        '//pkg:a', 'a', 'type1', dep_names=['//pkg:b', '//other:c']
    ),
    queryview_xml.make_module('//pkg:b', 'b', 'type2', dep_names=['//pkg:d']),
    queryview_xml.make_module('//pkg:d', 'd', 'type2'),
    queryview_xml.make_module(
        '//other:c', 'c', 'type2', dep_names=['//other:e']
    ),
    queryview_xml.make_module('//other:e', 'e', 'type3'),
    queryview_xml.make_module('//pkg2:f', 'f', 'type4'),
    queryview_xml.make_module('//pkg3:g', 'g', 'type5'),
])

_soong_module_graph = [
    soong_module_json.make_module(
        'a',
        'type1',
        blueprint='pkg/Android.bp',
        deps=[soong_module_json.make_dep('b'), soong_module_json.make_dep('c')],
    ),
    soong_module_json.make_module(
        'b',
        'type2',
        blueprint='pkg/Android.bp',
        deps=[soong_module_json.make_dep('d')],
        json_props=[
            soong_module_json.make_property('Name'),
            soong_module_json.make_property('Sdk_version'),
        ],
    ),
    soong_module_json.make_module('d', 'type2', blueprint='pkg/Android.bp'),
    soong_module_json.make_module(
        'c',
        'type2',
        blueprint='other/Android.bp',
        deps=[soong_module_json.make_dep('e')],
        json_props=[
            soong_module_json.make_property('Visibility'),
        ],
    ),
    soong_module_json.make_module('e', 'type3', blueprint='other/Android.bp'),
    soong_module_json.make_module(
        'f',
        'type4',
        blueprint='pkg2/Android.bp',
        json_props=[
            soong_module_json.make_property('Manifest'),
        ],
    ),
    soong_module_json.make_module('g', 'type5', blueprint='pkg3/Android.bp'),
    soong_module_json.make_module(
        'h', 'type3', blueprint='pkg/pkg4/Android.bp'
    ),
]

_soong_module_graph_created_by_no_loop = [
    soong_module_json.make_module(
        'a',
        'type1',
        blueprint='pkg/Android.bp',
        created_by='b',
        json_props=[
            soong_module_json.make_property('Name'),
            soong_module_json.make_property('Srcs'),
        ],
    ),
    soong_module_json.make_module('b', 'type2', blueprint='pkg/Android.bp'),
]

_soong_module_graph_created_by_loop = [
    soong_module_json.make_module(
        'a',
        'type1',
        deps=[soong_module_json.make_dep('b')],
        blueprint='pkg/Android.bp',
    ),
    soong_module_json.make_module(
        'b',
        'type2',
        blueprint='pkg/Android.bp',
        created_by='a',
        json_props=[
            soong_module_json.make_property('Name'),
            soong_module_json.make_property('Defaults'),
        ],
    ),
]


class Bp2BuildProgressTest(unittest.TestCase):

  @unittest.mock.patch(
      'dependency_analysis.get_queryview_module_info',
      autospec=True,
      return_value=_queryview_graph,
  )
  def test_get_module_adjacency_list_queryview_transitive_deps_and_props_by_converted_module_type(
      self, _
  ):
    self.maxDiff = None
    adjacency_dict, props_by_converted_module_type = (
        bp2build_progress.get_module_adjacency_list_and_props_by_converted_module_type(
            bp2build_progress.GraphFilterInfo(module_names=set(['a', 'f'])),
            True,
            set(),
            set(),
            dependency_analysis.TargetProduct(),
            collect_transitive_dependencies=True,
        )
    )

    a = bp2build_progress.ModuleInfo(
        name='a', kind='type1', dirname='pkg', num_deps=2, created_by=None
    )
    b = bp2build_progress.ModuleInfo(
        name='b', kind='type2', dirname='pkg', num_deps=1, created_by=None
    )
    c = bp2build_progress.ModuleInfo(
        name='c', kind='type2', dirname='other', num_deps=1, created_by=None
    )
    d = bp2build_progress.ModuleInfo(
        name='d', kind='type2', dirname='pkg', num_deps=0, created_by=None
    )
    e = bp2build_progress.ModuleInfo(
        name='e', kind='type3', dirname='other', num_deps=0, created_by=None
    )
    f = bp2build_progress.ModuleInfo(
        name='f', kind='type4', dirname='pkg2', num_deps=0, created_by=None
    )
    expected_adjacency_dict = {}
    expected_adjacency_dict[a] = bp2build_progress.DepInfo(
        direct_deps=set([b, c]), transitive_deps=set([d, e])
    )
    expected_adjacency_dict[b] = bp2build_progress.DepInfo(direct_deps=set([d]))
    expected_adjacency_dict[c] = bp2build_progress.DepInfo(direct_deps=set([e]))
    expected_adjacency_dict[d] = bp2build_progress.DepInfo()
    expected_adjacency_dict[e] = bp2build_progress.DepInfo()
    expected_adjacency_dict[f] = bp2build_progress.DepInfo()

    expected_props_by_converted_module_type = collections.defaultdict(set)

    self.assertDictEqual(adjacency_dict, expected_adjacency_dict)
    self.assertDictEqual(
        props_by_converted_module_type, expected_props_by_converted_module_type
    )

  @unittest.mock.patch(
      'dependency_analysis.get_queryview_module_info',
      autospec=True,
      return_value=_queryview_graph,
  )
  def test_get_module_adjacency_list_queryview_direct_deps_and_props_by_converted_module_type(
      self, _
  ):
    adjacency_dict, props_by_converted_module_type = (
        bp2build_progress.get_module_adjacency_list_and_props_by_converted_module_type(
            bp2build_progress.GraphFilterInfo(module_names=(['a', 'f'])),
            True,
            set(),
            set(),
            dependency_analysis.TargetProduct(),
            False,
            False,
        )
    )

    a = bp2build_progress.ModuleInfo(
        name='a', kind='type1', dirname='pkg', num_deps=2, created_by=None
    )
    b = bp2build_progress.ModuleInfo(
        name='b', kind='type2', dirname='pkg', num_deps=1, created_by=None
    )
    c = bp2build_progress.ModuleInfo(
        name='c', kind='type2', dirname='other', num_deps=1, created_by=None
    )
    d = bp2build_progress.ModuleInfo(
        name='d', kind='type2', dirname='pkg', num_deps=0, created_by=None
    )
    e = bp2build_progress.ModuleInfo(
        name='e', kind='type3', dirname='other', num_deps=0, created_by=None
    )
    f = bp2build_progress.ModuleInfo(
        name='f', kind='type4', dirname='pkg2', num_deps=0, created_by=None
    )

    expected_adjacency_dict = {}
    expected_adjacency_dict[a] = bp2build_progress.DepInfo(
        direct_deps=set([b, c])
    )
    expected_adjacency_dict[b] = bp2build_progress.DepInfo(direct_deps=set([d]))
    expected_adjacency_dict[c] = bp2build_progress.DepInfo(direct_deps=set([e]))
    expected_adjacency_dict[d] = bp2build_progress.DepInfo()
    expected_adjacency_dict[e] = bp2build_progress.DepInfo()
    expected_adjacency_dict[f] = bp2build_progress.DepInfo()

    expected_props_by_converted_module_type = collections.defaultdict(set)

    self.assertDictEqual(adjacency_dict, expected_adjacency_dict)
    self.assertDictEqual(
        props_by_converted_module_type, expected_props_by_converted_module_type
    )

  @unittest.mock.patch(
      'dependency_analysis.get_queryview_module_info_by_type',
      autospec=True,
      return_value=_queryview_graph,
  )
  def test_get_module_adjacency_list_queryview_direct_deps_and_props_by_converted_module_type(
      self, _
  ):
    adjacency_dict, props_by_converted_module_type = (
        bp2build_progress.get_module_adjacency_list_and_props_by_converted_module_type(
            bp2build_progress.GraphFilterInfo(
                module_types=set(['type1', 'type4'])
            ),
            True,
            set(),
            set(),
            dependency_analysis.TargetProduct(),
            collect_transitive_dependencies=False,
        )
    )

    a = bp2build_progress.ModuleInfo(
        name='a', kind='type1', dirname='pkg', num_deps=2, created_by=None
    )
    b = bp2build_progress.ModuleInfo(
        name='b', kind='type2', dirname='pkg', num_deps=1, created_by=None
    )
    c = bp2build_progress.ModuleInfo(
        name='c', kind='type2', dirname='other', num_deps=1, created_by=None
    )
    d = bp2build_progress.ModuleInfo(
        name='d', kind='type2', dirname='pkg', num_deps=0, created_by=None
    )
    e = bp2build_progress.ModuleInfo(
        name='e', kind='type3', dirname='other', num_deps=0, created_by=None
    )
    f = bp2build_progress.ModuleInfo(
        name='f', kind='type4', dirname='pkg2', num_deps=0, created_by=None
    )

    expected_adjacency_dict = {}
    expected_adjacency_dict[a] = bp2build_progress.DepInfo(
        direct_deps=set([b, c])
    )
    expected_adjacency_dict[b] = bp2build_progress.DepInfo(direct_deps=set([d]))
    expected_adjacency_dict[c] = bp2build_progress.DepInfo(direct_deps=set([e]))
    expected_adjacency_dict[d] = bp2build_progress.DepInfo()
    expected_adjacency_dict[e] = bp2build_progress.DepInfo()
    expected_adjacency_dict[f] = bp2build_progress.DepInfo()

    expected_props_by_converted_module_type = collections.defaultdict(set)

    self.assertDictEqual(adjacency_dict, expected_adjacency_dict)
    self.assertDictEqual(
        props_by_converted_module_type, expected_props_by_converted_module_type
    )

  @unittest.mock.patch(
      'dependency_analysis.get_json_module_info',
      autospec=True,
      return_value=_soong_module_graph,
  )
  def test_get_module_adjacency_list_soong_module_transitive_deps_and_props_by_converted_module_type(
      self, _
  ):
    adjacency_dict, props_by_converted_module_type = (
        bp2build_progress.get_module_adjacency_list_and_props_by_converted_module_type(
            bp2build_progress.GraphFilterInfo(
                module_names=set(['a', 'f']), package_dir=None
            ),
            False,
            set(),
            set(['b', 'c', 'e', 'f']),
            dependency_analysis.TargetProduct(),
            collect_transitive_dependencies=True,
        )
    )
    a = bp2build_progress.ModuleInfo(
        name='a', kind='type1', dirname='pkg', num_deps=2, created_by=''
    )
    b = bp2build_progress.ModuleInfo(
        name='b',
        kind='type2',
        dirname='pkg',
        num_deps=1,
        created_by='',
        props=frozenset(['Name', 'Sdk_version']),
    )
    c = bp2build_progress.ModuleInfo(
        name='c',
        kind='type2',
        dirname='other',
        num_deps=1,
        created_by='',
        props=frozenset(['Visibility']),
    )
    d = bp2build_progress.ModuleInfo(
        name='d', kind='type2', dirname='pkg', num_deps=0, created_by=''
    )
    e = bp2build_progress.ModuleInfo(
        name='e', kind='type3', dirname='other', num_deps=0, created_by=''
    )
    f = bp2build_progress.ModuleInfo(
        name='f',
        kind='type4',
        dirname='pkg2',
        num_deps=0,
        created_by='',
        props=frozenset(['Manifest']),
    )

    expected_adjacency_dict = {}
    expected_adjacency_dict[a] = bp2build_progress.DepInfo(
        direct_deps=set([b, c]), transitive_deps=set([d, e])
    )
    expected_adjacency_dict[b] = bp2build_progress.DepInfo(direct_deps=set([d]))
    expected_adjacency_dict[c] = bp2build_progress.DepInfo(direct_deps=set([e]))
    expected_adjacency_dict[d] = bp2build_progress.DepInfo()
    expected_adjacency_dict[e] = bp2build_progress.DepInfo()
    expected_adjacency_dict[f] = bp2build_progress.DepInfo()

    expected_props_by_converted_module_type = collections.defaultdict(set)
    expected_props_by_converted_module_type['type2'].update(
        set(['Name', 'Sdk_version', 'Visibility'])
    )
    expected_props_by_converted_module_type['type3'] = set()
    expected_props_by_converted_module_type['type4'].update(set(['Manifest']))

    self.assertDictEqual(adjacency_dict, expected_adjacency_dict)
    self.assertDictEqual(
        props_by_converted_module_type, expected_props_by_converted_module_type
    )

  @unittest.mock.patch(
      'dependency_analysis.get_json_module_info',
      autospec=True,
      return_value=_soong_module_graph,
  )
  def test_get_module_adjacency_list_soong_module_transitive_deps_and_props_by_converted_module_type(
      self, _
  ):
    adjacency_dict, props_by_converted_module_type = (
        bp2build_progress.get_module_adjacency_list_and_props_by_converted_module_type(
            bp2build_progress.GraphFilterInfo(
                module_types=set(['type1', 'type4']), package_dir=None
            ),
            False,
            set(),
            set(['b', 'c', 'e', 'f']),
            dependency_analysis.TargetProduct(),
            collect_transitive_dependencies=True,
        )
    )

    a = bp2build_progress.ModuleInfo(
        name='a', kind='type1', dirname='pkg', num_deps=2, created_by=''
    )
    b = bp2build_progress.ModuleInfo(
        name='b',
        kind='type2',
        dirname='pkg',
        num_deps=1,
        created_by='',
        props=frozenset(['Name', 'Sdk_version']),
    )
    c = bp2build_progress.ModuleInfo(
        name='c',
        kind='type2',
        dirname='other',
        num_deps=1,
        created_by='',
        props=frozenset(['Visibility']),
    )
    d = bp2build_progress.ModuleInfo(
        name='d', kind='type2', dirname='pkg', num_deps=0, created_by=''
    )
    e = bp2build_progress.ModuleInfo(
        name='e', kind='type3', dirname='other', num_deps=0, created_by=''
    )
    f = bp2build_progress.ModuleInfo(
        name='f',
        kind='type4',
        dirname='pkg2',
        num_deps=0,
        created_by='',
        props=frozenset(['Manifest']),
    )

    expected_adjacency_dict = {}
    expected_adjacency_dict[a] = bp2build_progress.DepInfo(
        direct_deps=set([b, c]), transitive_deps=set([d, e])
    )
    expected_adjacency_dict[b] = bp2build_progress.DepInfo(direct_deps=set([d]))
    expected_adjacency_dict[c] = bp2build_progress.DepInfo(direct_deps=set([e]))
    expected_adjacency_dict[d] = bp2build_progress.DepInfo()
    expected_adjacency_dict[e] = bp2build_progress.DepInfo()
    expected_adjacency_dict[f] = bp2build_progress.DepInfo()

    expected_props_by_converted_module_type = collections.defaultdict(set)
    expected_props_by_converted_module_type['type2'].update(
        set(['Name', 'Sdk_version', 'Visibility'])
    )
    expected_props_by_converted_module_type['type3'] = set()
    expected_props_by_converted_module_type['type4'].update(set(['Manifest']))

    self.assertDictEqual(adjacency_dict, expected_adjacency_dict)
    self.assertDictEqual(
        props_by_converted_module_type, expected_props_by_converted_module_type
    )

  @unittest.mock.patch(
      'dependency_analysis.get_json_module_info',
      autospec=True,
      return_value=_soong_module_graph,
  )
  def test_get_module_adjacency_list_soong_module_transitive_deps_package_dir_and_props_by_converted_module_type(
      self, _
  ):
    adjacency_dict_recursive, props_by_converted_module_type_recursive = (
        bp2build_progress.get_module_adjacency_list_and_props_by_converted_module_type(
            bp2build_progress.GraphFilterInfo(
                package_dir='pkg/', recursive=True
            ),
            False,
            set(),
            set(['b', 'c', 'e']),
            dependency_analysis.TargetProduct(),
            collect_transitive_dependencies=True,
        )
    )

    (
        adjacency_dict_non_recursive,
        props_by_converted_module_type_non_recursive,
    ) = bp2build_progress.get_module_adjacency_list_and_props_by_converted_module_type(
        bp2build_progress.GraphFilterInfo(package_dir='pkg/', recursive=False),
        False,
        set(),
        set(['b', 'c', 'e']),
        dependency_analysis.TargetProduct(),
        collect_transitive_dependencies=True,
    )

    a = bp2build_progress.ModuleInfo(
        name='a', kind='type1', dirname='pkg', num_deps=2, created_by=''
    )
    b = bp2build_progress.ModuleInfo(
        name='b',
        kind='type2',
        dirname='pkg',
        num_deps=1,
        created_by='',
        props=frozenset(['Name', 'Sdk_version']),
    )
    c = bp2build_progress.ModuleInfo(
        name='c',
        kind='type2',
        dirname='other',
        num_deps=1,
        created_by='',
        props=frozenset(['Visibility']),
    )
    d = bp2build_progress.ModuleInfo(
        name='d', kind='type2', dirname='pkg', num_deps=0, created_by=''
    )
    e = bp2build_progress.ModuleInfo(
        name='e', kind='type3', dirname='other', num_deps=0, created_by=''
    )
    h = bp2build_progress.ModuleInfo(
        name='h', kind='type3', dirname='pkg/pkg4', num_deps=0, created_by=''
    )

    expected_adjacency_dict_recursive = {}
    expected_adjacency_dict_recursive[a] = bp2build_progress.DepInfo(
        direct_deps=set([b, c]), transitive_deps=set([d, e])
    )
    expected_adjacency_dict_recursive[b] = bp2build_progress.DepInfo(
        direct_deps=set([d])
    )
    expected_adjacency_dict_recursive[c] = bp2build_progress.DepInfo(
        direct_deps=set([e])
    )
    expected_adjacency_dict_recursive[d] = bp2build_progress.DepInfo()
    expected_adjacency_dict_recursive[e] = bp2build_progress.DepInfo()
    expected_adjacency_dict_recursive[h] = bp2build_progress.DepInfo()

    expected_adjacency_dict_non_recursive = {}
    expected_adjacency_dict_non_recursive[a] = bp2build_progress.DepInfo(
        direct_deps=set([b, c]), transitive_deps=set([d, e])
    )
    expected_adjacency_dict_non_recursive[b] = bp2build_progress.DepInfo(
        direct_deps=set([d])
    )
    expected_adjacency_dict_non_recursive[c] = bp2build_progress.DepInfo(
        direct_deps=set([e])
    )
    expected_adjacency_dict_non_recursive[d] = bp2build_progress.DepInfo()
    expected_adjacency_dict_non_recursive[e] = bp2build_progress.DepInfo()

    expected_props_by_converted_module_type = collections.defaultdict(set)
    expected_props_by_converted_module_type['type2'].update(
        set(['Name', 'Sdk_version', 'Visibility'])
    )
    expected_props_by_converted_module_type['type3'] = set()

    self.assertDictEqual(
        adjacency_dict_recursive, expected_adjacency_dict_recursive
    )
    self.assertDictEqual(
        adjacency_dict_non_recursive, expected_adjacency_dict_non_recursive
    )
    self.assertDictEqual(
        props_by_converted_module_type_recursive,
        expected_props_by_converted_module_type,
    )
    self.assertDictEqual(
        props_by_converted_module_type_non_recursive,
        expected_props_by_converted_module_type,
    )

  @unittest.mock.patch(
      'dependency_analysis.get_json_module_info',
      autospec=True,
      return_value=_soong_module_graph,
  )
  def test_get_module_adjacency_list_soong_module_direct_deps_and_props_by_converted_module_type(
      self, _
  ):
    adjacency_dict, props_by_converted_module_type = (
        bp2build_progress.get_module_adjacency_list_and_props_by_converted_module_type(
            bp2build_progress.GraphFilterInfo(
                set(['a', 'f']), package_dir=None
            ),
            False,
            set(),
            set(['b', 'c', 'e', 'f']),
            dependency_analysis.TargetProduct(),
            collect_transitive_dependencies=False,
        )
    )

    a = bp2build_progress.ModuleInfo(
        name='a', kind='type1', dirname='pkg', num_deps=2, created_by=''
    )
    b = bp2build_progress.ModuleInfo(
        name='b',
        kind='type2',
        dirname='pkg',
        num_deps=1,
        created_by='',
        props=frozenset(['Name', 'Sdk_version']),
    )
    c = bp2build_progress.ModuleInfo(
        name='c',
        kind='type2',
        dirname='other',
        num_deps=1,
        created_by='',
        props=frozenset(['Visibility']),
    )
    d = bp2build_progress.ModuleInfo(
        name='d', kind='type2', dirname='pkg', num_deps=0, created_by=''
    )
    e = bp2build_progress.ModuleInfo(
        name='e', kind='type3', dirname='other', num_deps=0, created_by=''
    )
    f = bp2build_progress.ModuleInfo(
        name='f',
        kind='type4',
        dirname='pkg2',
        num_deps=0,
        created_by='',
        props=frozenset(['Manifest']),
    )

    expected_adjacency_dict = {}
    expected_adjacency_dict[a] = bp2build_progress.DepInfo(
        direct_deps=set([b, c])
    )
    expected_adjacency_dict[b] = bp2build_progress.DepInfo(direct_deps=set([d]))
    expected_adjacency_dict[c] = bp2build_progress.DepInfo(direct_deps=set([e]))
    expected_adjacency_dict[d] = bp2build_progress.DepInfo()
    expected_adjacency_dict[e] = bp2build_progress.DepInfo()
    expected_adjacency_dict[f] = bp2build_progress.DepInfo()

    expected_props_by_converted_module_type = collections.defaultdict(set)
    expected_props_by_converted_module_type['type2'].update(
        set(['Name', 'Sdk_version', 'Visibility'])
    )
    expected_props_by_converted_module_type['type3'] = set()
    expected_props_by_converted_module_type['type4'].update(set(['Manifest']))

    self.assertDictEqual(adjacency_dict, expected_adjacency_dict)
    self.assertDictEqual(
        props_by_converted_module_type, expected_props_by_converted_module_type
    )

  @unittest.mock.patch(
      'dependency_analysis.get_json_module_info',
      autospec=True,
      return_value=_soong_module_graph_created_by_no_loop,
  )
  def test_get_module_adjacency_list_soong_module_created_by_and_props_by_converted_module_type(
      self, _
  ):
    adjacency_dict, props_by_converted_module_type = (
        bp2build_progress.get_module_adjacency_list_and_props_by_converted_module_type(
            bp2build_progress.GraphFilterInfo(
                set(['a', 'f']), package_dir=None
            ),
            False,
            set(),
            set(['a']),
            True,
            False,
        )
    )
    a = bp2build_progress.ModuleInfo(
        name='a',
        kind='type1',
        dirname='pkg',
        num_deps=1,
        created_by='b',
        props=frozenset(['Name', 'Srcs']),
    )
    b = bp2build_progress.ModuleInfo(
        name='b', kind='type2', dirname='pkg', num_deps=0, created_by=''
    )

    expected_adjacency_dict = {}
    expected_adjacency_dict[a] = bp2build_progress.DepInfo(direct_deps=set([b]))
    expected_adjacency_dict[b] = bp2build_progress.DepInfo()

    expected_props_by_converted_module_type = collections.defaultdict(set)
    expected_props_by_converted_module_type['type1'].update(
        set(['Name', 'Srcs'])
    )

    self.assertDictEqual(adjacency_dict, expected_adjacency_dict)
    self.assertDictEqual(
        props_by_converted_module_type, expected_props_by_converted_module_type
    )

  @unittest.mock.patch(
      'dependency_analysis.get_json_module_info',
      autospec=True,
      return_value=_soong_module_graph_created_by_loop,
  )
  def test_get_module_adjacency_list_soong_module_created_by_loop_and_props_by_converted_module_type(
      self, _
  ):
    adjacency_dict, props_by_converted_module_type = (
        bp2build_progress.get_module_adjacency_list_and_props_by_converted_module_type(
            bp2build_progress.GraphFilterInfo(
                set(['a', 'f']), package_dir=None
            ),
            False,
            set(),
            set(['b']),
            True,
            False,
        )
    )

    a = bp2build_progress.ModuleInfo(
        name='a', kind='type1', dirname='pkg', num_deps=1, created_by=''
    )
    b = bp2build_progress.ModuleInfo(
        name='b',
        kind='type2',
        dirname='pkg',
        num_deps=1,
        created_by='a',
        props=frozenset(['Name', 'Defaults']),
    )

    expected_adjacency_dict = {}
    expected_adjacency_dict[a] = bp2build_progress.DepInfo(direct_deps=set([b]))
    expected_adjacency_dict[b] = bp2build_progress.DepInfo()

    expected_props_by_converted_module_type = collections.defaultdict(set)
    expected_props_by_converted_module_type['type2'].update(
        set(['Name', 'Defaults'])
    )

    self.assertDictEqual(adjacency_dict, expected_adjacency_dict)
    self.assertDictEqual(
        props_by_converted_module_type, expected_props_by_converted_module_type
    )

  def test_generate_report_data(self):
    a = bp2build_progress.ModuleInfo(
        name='a', kind='type1', dirname='pkg', num_deps=4, created_by=None
    )
    b = bp2build_progress.ModuleInfo(
        name='b', kind='type2', dirname='pkg', num_deps=1, created_by=None
    )
    c = bp2build_progress.ModuleInfo(
        name='c', kind='type2', dirname='other', num_deps=1, created_by=None
    )
    d = bp2build_progress.ModuleInfo(
        name='d',
        kind='type2',
        dirname='pkg',
        num_deps=0,
        created_by=None,
        converted=True,
    )
    e = bp2build_progress.ModuleInfo(
        name='e', kind='type3', dirname='other', num_deps=0, created_by=None
    )
    f = bp2build_progress.ModuleInfo(
        name='f', kind='type4', dirname='pkg2', num_deps=3, created_by=None
    )
    g = bp2build_progress.ModuleInfo(
        name='g',
        kind='type4',
        dirname='pkg2',
        num_deps=2,
        created_by=None,
        converted=True,
    )

    module_graph = {}
    module_graph[a] = bp2build_progress.DepInfo(
        direct_deps=set([b, c]), transitive_deps=set([d, e])
    )
    module_graph[b] = bp2build_progress.DepInfo(direct_deps=set([d]))
    module_graph[c] = bp2build_progress.DepInfo(direct_deps=set([e]))
    module_graph[d] = bp2build_progress.DepInfo()
    module_graph[e] = bp2build_progress.DepInfo()
    module_graph[f] = bp2build_progress.DepInfo(
        direct_deps=set([b, g]), transitive_deps=set([d])
    )
    module_graph[g] = bp2build_progress.DepInfo()

    report_data = bp2build_progress.generate_report_data(
        module_graph,
        {d.name: {d.kind}, g.name: {g.kind}},
        bp2build_progress.GraphFilterInfo(
            module_names={'a', 'f'}, package_dir=None
        ),
        props_by_converted_module_type=collections.defaultdict(set),
        use_queryview=False,
        hide_unconverted_modules_reasons=True,
        bp2build_metrics=Bp2BuildMetrics(),
    )

    all_unconverted_modules = collections.defaultdict(set)
    all_unconverted_modules[b].update({a, f})
    all_unconverted_modules[c].update({a})
    all_unconverted_modules[e].update({a, c})

    blocked_modules = collections.defaultdict(set)
    blocked_modules[a].update({b, c})
    blocked_modules[b].update(set())
    blocked_modules[c].update({e})
    blocked_modules[f].update({b})
    blocked_modules[e].update(set())

    blocked_modules_transitive = collections.defaultdict(set)
    blocked_modules_transitive[a].update({b, c, e})
    blocked_modules_transitive[b].update(set())
    blocked_modules_transitive[c].update({e})
    blocked_modules_transitive[f].update({b})
    blocked_modules_transitive[e].update(set())

    expected_report_data = bp2build_progress.ReportData(
        input_modules={
            bp2build_progress.InputModule(a, 4, 3),
            bp2build_progress.InputModule(f, 3, 1),
        },
        total_deps={b, c, d, e, g},
        unconverted_deps={b, c, e},
        all_unconverted_modules=all_unconverted_modules,
        blocked_modules=blocked_modules,
        blocked_modules_transitive=blocked_modules_transitive,
        dirs_with_unconverted_modules={'pkg', 'other', 'pkg2'},
        kind_of_unconverted_modules={
            'type1: 1',
            'type2: 2',
            'type3: 1',
            'type4: 1',
        },
        converted={d.name: {d.kind}, g.name: {g.kind}},
        show_converted=False,
        hide_unconverted_modules_reasons=True,
        package_dir=None,
    )
    self.assertEqual(report_data, expected_report_data)

  def test_generate_report_data_by_type(self):
    a = bp2build_progress.ModuleInfo(
        name='a', kind='type1', dirname='pkg', num_deps=4, created_by=None
    )
    b = bp2build_progress.ModuleInfo(
        name='b', kind='type2', dirname='pkg', num_deps=1, created_by=None
    )
    c = bp2build_progress.ModuleInfo(
        name='c', kind='type2', dirname='other', num_deps=1, created_by=None
    )
    d = bp2build_progress.ModuleInfo(
        name='d',
        kind='type2',
        dirname='pkg',
        num_deps=0,
        created_by=None,
        converted=True,
    )
    e = bp2build_progress.ModuleInfo(
        name='e', kind='type3', dirname='other', num_deps=0, created_by=None
    )
    f = bp2build_progress.ModuleInfo(
        name='f', kind='type4', dirname='pkg2', num_deps=3, created_by=None
    )
    g = bp2build_progress.ModuleInfo(
        name='g',
        kind='type4',
        dirname='pkg2',
        num_deps=0,
        created_by=None,
        converted=True,
    )

    module_graph = {}
    module_graph[a] = bp2build_progress.DepInfo(
        direct_deps=set([b, c]), transitive_deps=set([d, e])
    )
    module_graph[b] = bp2build_progress.DepInfo(direct_deps=set([d]))
    module_graph[c] = bp2build_progress.DepInfo(direct_deps=set([e]))
    module_graph[d] = bp2build_progress.DepInfo()
    module_graph[e] = bp2build_progress.DepInfo()
    module_graph[f] = bp2build_progress.DepInfo(
        direct_deps=set([b, g]), transitive_deps=set([d])
    )
    module_graph[g] = bp2build_progress.DepInfo()

    report_data = bp2build_progress.generate_report_data(
        module_graph,
        {d.name: {d.kind}, g.name: {g.kind}},
        bp2build_progress.GraphFilterInfo(
            module_types={'type1', 'type4'}, package_dir=None
        ),
        props_by_converted_module_type=collections.defaultdict(set),
        use_queryview=False,
        hide_unconverted_modules_reasons=True,
        bp2build_metrics=Bp2BuildMetrics(),
    )

    all_unconverted_modules = collections.defaultdict(set)
    all_unconverted_modules['b'].update({a, f})
    all_unconverted_modules['c'].update({a})
    all_unconverted_modules['e'].update({a})

    blocked_modules = collections.defaultdict(set)
    blocked_modules[a].update({'b', 'c'})
    blocked_modules[b].update(set())
    blocked_modules[c].update(set('e'))
    blocked_modules[f].update(set({'b'}))
    blocked_modules[e].update(set())

    blocked_modules_transitive = collections.defaultdict(set)
    blocked_modules_transitive[a].update({'b', 'c', 'e'})
    blocked_modules_transitive[b].update(set())
    blocked_modules_transitive[c].update(set('e'))
    blocked_modules_transitive[f].update(set({'b'}))
    blocked_modules_transitive[e].update(set())

    expected_report_data = bp2build_progress.ReportData(
        input_modules={
            bp2build_progress.InputModule(a, 4, 3),
            bp2build_progress.InputModule(f, 3, 1),
            bp2build_progress.InputModule(g, 0, 0),
        },
        total_deps={b, c, d, e, g},
        unconverted_deps={'b', 'c', 'e'},
        all_unconverted_modules=all_unconverted_modules,
        blocked_modules=blocked_modules,
        blocked_modules_transitive=blocked_modules_transitive,
        dirs_with_unconverted_modules={'pkg', 'other', 'pkg2'},
        kind_of_unconverted_modules={'type1', 'type2', 'type4'},
        converted={d.name: {d.kind}, g.name: {g.kind}},
        show_converted=False,
        hide_unconverted_modules_reasons=True,
        package_dir=None,
    )

    self.assertEqual(
        report_data.input_modules, expected_report_data.input_modules
    )

  def test_generate_report_data_show_converted(self):
    a = bp2build_progress.ModuleInfo(
        name='a', kind='type1', dirname='pkg', num_deps=2, created_by=None
    )
    b = bp2build_progress.ModuleInfo(
        name='b',
        kind='type2',
        dirname='pkg2',
        num_deps=0,
        created_by=None,
        converted=True,
    )
    c = bp2build_progress.ModuleInfo(
        name='c', kind='type3', dirname='other', num_deps=0, created_by=None
    )

    module_graph = collections.defaultdict(set)
    module_graph[a] = bp2build_progress.DepInfo(direct_deps=set([b, c]))
    module_graph[b] = bp2build_progress.DepInfo()
    module_graph[c] = bp2build_progress.DepInfo()

    report_data = bp2build_progress.generate_report_data(
        module_graph,
        {b.name: {b.kind}},
        bp2build_progress.GraphFilterInfo(module_names={'a'}, package_dir=None),
        props_by_converted_module_type=collections.defaultdict(set),
        use_queryview=False,
        show_converted=True,
        hide_unconverted_modules_reasons=True,
        bp2build_metrics=Bp2BuildMetrics(),
    )

    all_unconverted_modules = collections.defaultdict(set)
    all_unconverted_modules[c].update({a})

    blocked_modules = collections.defaultdict(set)
    blocked_modules[a].update({b, c})
    blocked_modules[b].update(set())
    blocked_modules[c].update(set())

    blocked_modules_transitive = collections.defaultdict(set)
    blocked_modules_transitive[a].update({b, c})
    blocked_modules_transitive[b].update(set())
    blocked_modules_transitive[c].update(set())

    expected_report_data = bp2build_progress.ReportData(
        input_modules={
            bp2build_progress.InputModule(a, 2, 1),
        },
        total_deps={b, c},
        unconverted_deps={c},
        all_unconverted_modules=all_unconverted_modules,
        blocked_modules=blocked_modules,
        blocked_modules_transitive=blocked_modules_transitive,
        dirs_with_unconverted_modules={'pkg', 'other'},
        kind_of_unconverted_modules={'type1: 1', 'type3: 1'},
        converted={b.name:{b.kind}},
        show_converted=True,
        hide_unconverted_modules_reasons=True,
        package_dir=None,
    )

    self.assertEqual(report_data, expected_report_data)

  def test_generate_report_data_show_unconverted_modules_reasons(self):
    a = bp2build_progress.ModuleInfo(
        name='a',
        kind='type1',
        dirname='pkg',
        num_deps=4,
        created_by=None,
        reasons_from_heuristics=frozenset(
            {'unconverted dependencies', 'type missing converter'}
        ),
        reason_from_metric='TYPE_UNSUPPORTED',
    )
    b = bp2build_progress.ModuleInfo(
        name='b',
        kind='type2',
        dirname='pkg',
        num_deps=1,
        created_by=None,
        props=frozenset({'Name', 'Srcs', 'BaseName'}),
        reasons_from_heuristics=frozenset(
            {'unconverted properties: [BaseName]'}
        ),
        reason_from_metric='PROPERTY_UNSUPPORTED',
    )
    c = bp2build_progress.ModuleInfo(
        name='c',
        kind='type2',
        dirname='other',
        num_deps=1,
        created_by=None,
        props=frozenset({'Name', 'Defaults'}),
        reasons_from_heuristics=frozenset({'unconverted dependencies'}),
        reason_from_metric='UNCONVERTED_DEP',
    )
    d = bp2build_progress.ModuleInfo(
        name='d',
        kind='type2',
        dirname='pkg',
        num_deps=0,
        created_by=None,
        converted=True,
    )
    e = bp2build_progress.ModuleInfo(
        name='e',
        kind='type3',
        dirname='other',
        num_deps=0,
        created_by=None,
        reasons_from_heuristics=frozenset({'type missing converter'}),
        reason_from_metric='TYPE_UNSUPPORTED',
    )
    f = bp2build_progress.ModuleInfo(
        name='f',
        kind='type4',
        dirname='pkg2',
        num_deps=3,
        created_by=None,
        props=frozenset(
            {'Name', 'Sdk_version', 'Visibility', 'Backend.Java.Platform_apis'}
        ),
        reasons_from_heuristics=frozenset({'unconverted dependencies'}),
        reason_from_metric='UNCONVERTED_DEP',
    )
    g = bp2build_progress.ModuleInfo(
        name='g',
        kind='type4',
        dirname='pkg2',
        num_deps=2,
        created_by=None,
        converted=True,
    )

    module_graph = {}
    module_graph[a] = bp2build_progress.DepInfo(
        direct_deps=set([b, c]), transitive_deps=set([d, e])
    )
    module_graph[b] = bp2build_progress.DepInfo(direct_deps=set([d]))
    module_graph[c] = bp2build_progress.DepInfo(direct_deps=set([e]))
    module_graph[d] = bp2build_progress.DepInfo()
    module_graph[e] = bp2build_progress.DepInfo()
    module_graph[f] = bp2build_progress.DepInfo(
        direct_deps=set([b, g]), transitive_deps=set([d])
    )
    module_graph[g] = bp2build_progress.DepInfo()

    props_by_converted_module_type = collections.defaultdict(set)
    props_by_converted_module_type['type2'].update(
        frozenset(('Name', 'Srcs', 'Resource_dirs', 'Defaults'))
    )
    props_by_converted_module_type['type4'].update(
        frozenset(
            ('Name', 'Sdk_version', 'Visibility', 'Backend.Java.Platform_apis')
        )
    )

    bp2build_metrics = Bp2BuildMetrics()
    bp2build_metrics.unconvertedModules['a'].type = 3
    bp2build_metrics.unconvertedModules['b'].type = 4
    bp2build_metrics.unconvertedModules['c'].type = 5
    bp2build_metrics.unconvertedModules['f'].type = 5
    bp2build_metrics.unconvertedModules['e'].type = 3

    report_data = bp2build_progress.generate_report_data(
        module_graph,
        {d.name: {d.kind}, g.name: {g.kind}},
        bp2build_progress.GraphFilterInfo(
            module_names={'a', 'f'}, package_dir=None
        ),
        props_by_converted_module_type,
        use_queryview=False,
        bp2build_metrics=bp2build_metrics,
    )

    all_unconverted_modules = collections.defaultdict(set)
    all_unconverted_modules[b].update({a, f})
    all_unconverted_modules[c].update({a})
    all_unconverted_modules[e].update({a, c})

    blocked_modules = collections.defaultdict(set)
    blocked_modules[a].update({b, c})
    blocked_modules[b].update(set())
    blocked_modules[c].update({e})
    blocked_modules[f].update({b})
    blocked_modules[e].update(set())

    blocked_modules_transitive = collections.defaultdict(set)
    blocked_modules_transitive[a].update({b, c, e})
    blocked_modules_transitive[b].update(set())
    blocked_modules_transitive[c].update({e})
    blocked_modules_transitive[f].update({b})
    blocked_modules_transitive[e].update(set())

    expected_report_data = bp2build_progress.ReportData(
        input_modules={
            bp2build_progress.InputModule(a, 4, 3),
            bp2build_progress.InputModule(f, 3, 1),
        },
        total_deps={b, c, d, e, g},
        unconverted_deps={b, c, e},
        all_unconverted_modules=all_unconverted_modules,
        blocked_modules=blocked_modules,
        blocked_modules_transitive=blocked_modules_transitive,
        dirs_with_unconverted_modules={'pkg', 'other', 'pkg2'},
        kind_of_unconverted_modules={
            'type1: 1',
            'type2: 2',
            'type3: 1',
            'type4: 1',
        },
        converted={d.name: {d.kind}, g.name: {g.kind}},
        show_converted=False,
        hide_unconverted_modules_reasons=False,
        package_dir=None,
    )
    self.assertEqual(report_data, expected_report_data)

  def test_generate_report_unconverted_modules_reasons(self):
    a = bp2build_progress.ModuleInfo(
        name='a',
        kind='type1',
        dirname='pkg',
        num_deps=2,
        created_by=None,
        props=frozenset({'Flags', 'Stability'}),
    )
    b = bp2build_progress.ModuleInfo(
        name='b',
        kind='type2',
        dirname='pkg2',
        num_deps=0,
        created_by=None,
        converted=True,
        props=frozenset({'Flags', 'Stability', 'Resource_dirs'}),
    )
    c = bp2build_progress.ModuleInfo(
        name='c',
        kind='type2',
        dirname='other',
        num_deps=0,
        created_by=None,
        converted=True,
        props=frozenset({'Name', 'Stability', 'Resource_dirs'}),
    )

    module_graph = collections.defaultdict(set)
    module_graph[a] = bp2build_progress.DepInfo(direct_deps=set([b, c]))
    module_graph[b] = bp2build_progress.DepInfo()
    module_graph[c] = bp2build_progress.DepInfo()

    bp2build_metrics = Bp2BuildMetrics()
    bp2build_metrics.unconvertedModules['a'].type = 3

    props_by_converted_module_type = collections.defaultdict(set)
    props_by_converted_module_type['type2'].update(
        frozenset(('Name', 'Srcs', 'Resource_dirs', 'Defaults'))
    )

    report_data_show_unconverted_modules_reasons = (
        bp2build_progress.generate_report_data(
            module_graph,
            {b.name:{b.kind}, c.name:{c.kind}},
            bp2build_progress.GraphFilterInfo(
                module_names={'a'}, package_dir=None
            ),
            props_by_converted_module_type=props_by_converted_module_type,
            use_queryview=False,
            bp2build_metrics=bp2build_metrics,

        )
    )
    report_data_hide_unconverted_modules_reasons = (
        bp2build_progress.generate_report_data(
            module_graph,
            {b.name:{b.kind}, c.name:{c.kind}},
            bp2build_progress.GraphFilterInfo(
                module_names={'a'}, package_dir=None
            ),
            props_by_converted_module_type=props_by_converted_module_type,
            hide_unconverted_modules_reasons=True,
            use_queryview=False,
            bp2build_metrics=bp2build_metrics,
        )
    )

    report_show_unconverted_modules_reasons = bp2build_progress.generate_report(
        report_data_show_unconverted_modules_reasons
    )
    report_hide_unconverted_modules_reasons = bp2build_progress.generate_report(
        report_data_hide_unconverted_modules_reasons
    )

    self.maxDiff = None
    expected_report_show_unconverted_modules_reasons = f"""# bp2build progress report for: a: 100.0% (2/2) converted

Percent converted: 100.00 (2/2)
Total unique unconverted dependencies: 0
Ignored module types: ['cc_defaults', 'cpython3_python_stdlib', 'hidl_package_root', 'java_defaults', 'license', 'license_kind']

# Transitive dependency closure:

0 unconverted transitive deps remaining:
a [type1] [pkg]
\tunconverted due to:
\t\tunconverted reason from metric: TYPE_UNSUPPORTED
\t\tunconverted reasons from heuristics: type missing converter
\tdirect deps:


# Unconverted deps of a: 100.0% (2/2) converted:



# Dirs with unconverted modules:

pkg


# Kinds with unconverted modules:

type1: 1


# Converted modules not shown


Generated by: https://cs.android.com/android/platform/superproject/+/master:build/bazel/scripts/bp2build_progress/bp2build_progress.py
Generated at: {datetime.datetime.now().strftime("%Y-%m-%dT%H:%M:%S %z")}"""

    expected_report_hide_unconverted_modules_reasons = f"""# bp2build progress report for: a: 100.0% (2/2) converted

Percent converted: 100.00 (2/2)
Total unique unconverted dependencies: 0
Ignored module types: ['cc_defaults', 'cpython3_python_stdlib', 'hidl_package_root', 'java_defaults', 'license', 'license_kind']

# Transitive dependency closure:

0 unconverted transitive deps remaining:
a [type1] [pkg]
\tdirect deps:


# Unconverted deps of a: 100.0% (2/2) converted:



# Dirs with unconverted modules:

pkg


# Kinds with unconverted modules:

type1: 1


# Converted modules not shown


Generated by: https://cs.android.com/android/platform/superproject/+/master:build/bazel/scripts/bp2build_progress/bp2build_progress.py
Generated at: {datetime.datetime.now().strftime("%Y-%m-%dT%H:%M:%S %z")}"""
    self.assertEqual(
        report_show_unconverted_modules_reasons,
        expected_report_show_unconverted_modules_reasons,
    )
    self.assertEqual(
        report_hide_unconverted_modules_reasons,
        expected_report_hide_unconverted_modules_reasons,
    )

  def test_generate_proto_from_soong_module(self):
    a = bp2build_progress.ModuleInfo(
        name='a',
        kind='type1',
        dirname='pkg',
        num_deps=4,
        created_by=None,
        reasons_from_heuristics=frozenset(
            {'unconverted dependencies', 'type missing converter'}
        ),
    )
    b = bp2build_progress.ModuleInfo(
        name='b',
        kind='type2',
        dirname='pkg',
        num_deps=1,
        created_by=None,
        props=frozenset({'Name', 'Srcs', 'BaseName'}),
        reasons_from_heuristics=frozenset(
            {'unconverted properties: [BaseName]'}
        ),
    )
    c = bp2build_progress.ModuleInfo(
        name='c',
        kind='type2',
        dirname='other',
        num_deps=1,
        created_by=None,
        props=frozenset({'Name', 'Defaults'}),
        reasons_from_heuristics=frozenset({'unconverted dependencies'}),
    )
    d = bp2build_progress.ModuleInfo(
        name='d',
        kind='type2',
        dirname='pkg',
        num_deps=0,
        created_by=None,
        converted=True,
    )
    e = bp2build_progress.ModuleInfo(
        name='e',
        kind='type3',
        dirname='other',
        num_deps=0,
        created_by=None,
        reasons_from_heuristics=frozenset({'type missing converter'}),
    )
    f = bp2build_progress.ModuleInfo(
        name='f',
        kind='type4',
        dirname='pkg2',
        num_deps=3,
        created_by=None,
        props=frozenset(
            {'Name', 'Sdk_version', 'Visibility', 'Backend.Java.Platform_apis'}
        ),
        reasons_from_heuristics=frozenset(
            {'unconverted dependencies', 'unconverted properties: [Visibility]'}
        ),
    )
    g = bp2build_progress.ModuleInfo(
        name='g',
        kind='type4',
        dirname='pkg2',
        num_deps=2,
        created_by=None,
        converted=True,
    )

    module_graph = {}
    module_graph[a] = bp2build_progress.DepInfo(
        direct_deps=set([b, c]), transitive_deps=set([d, e])
    )
    module_graph[b] = bp2build_progress.DepInfo(direct_deps=set([d]))
    module_graph[c] = bp2build_progress.DepInfo(direct_deps=set([e]))
    module_graph[d] = bp2build_progress.DepInfo()
    module_graph[e] = bp2build_progress.DepInfo()
    module_graph[f] = bp2build_progress.DepInfo(
        direct_deps=set([b, g]), transitive_deps=set([d])
    )
    module_graph[g] = bp2build_progress.DepInfo()

    blocked_modules_transitive = collections.defaultdict(set)
    blocked_modules_transitive[a].update({b, c, e})
    blocked_modules_transitive[b].update(set())
    blocked_modules_transitive[c].update({e})
    blocked_modules_transitive[f].update({b})
    blocked_modules_transitive[e].update(set())

    props_by_converted_module_type = collections.defaultdict(set)
    props_by_converted_module_type['type2'].update(
        frozenset(('Name', 'Srcs', 'Resource_dirs', 'Defaults'))
    )
    props_by_converted_module_type['type4'].update(
        frozenset(('Name', 'Sdk_version', 'Backend.Java.Platform_apis'))
    )

    report_data = bp2build_progress.generate_report_data(
        module_graph,
        {d.name: {d.kind}, g.name: {g.kind}},
        bp2build_progress.GraphFilterInfo(
            module_names={'a', 'f'}, package_dir=None
        ),
        props_by_converted_module_type,
        use_queryview=False,
        bp2build_metrics=Bp2BuildMetrics(),
    )

    expected_message = bp2build_pb2.Bp2buildConversionProgress(
        root_modules=[m.module.name for m in report_data.input_modules],
        num_deps=len(report_data.total_deps),
    )
    for (
        module,
        unconverted_deps,
    ) in report_data.blocked_modules_transitive.items():
      expected_message.unconverted.add(
          name=module.name,
          directory=module.dirname,
          type=module.kind,
          unconverted_deps={d.name for d in unconverted_deps},
          num_deps=module.num_deps,
          unconverted_reasons_from_heuristics=list(
              module.reasons_from_heuristics
          ),
      )

    message = bp2build_progress.generate_proto(report_data)
    self.assertEqual(message, expected_message)

  def test_generate_proto_from_soong_module_show_converted(self):
    a = bp2build_progress.ModuleInfo(
        name='a',
        kind='type1',
        dirname='pkg',
        num_deps=4,
        created_by=None,
        reasons_from_heuristics=frozenset(
            {'unconverted dependencies', 'type missing converter'}
        ),
    )
    b = bp2build_progress.ModuleInfo(
        name='b',
        kind='type2',
        dirname='pkg',
        num_deps=1,
        created_by=None,
        props=frozenset({'Name', 'Srcs', 'BaseName'}),
        reasons_from_heuristics=frozenset(
            {'unconverted properties: [BaseName]'}
        ),
    )
    c = bp2build_progress.ModuleInfo(
        name='c',
        kind='type2',
        dirname='other',
        num_deps=1,
        created_by=None,
        props=frozenset({'Name', 'Defaults'}),
        reasons_from_heuristics=frozenset({'unconverted dependencies'}),
    )
    d = bp2build_progress.ModuleInfo(
        name='d',
        kind='type2',
        dirname='pkg',
        num_deps=0,
        created_by=None,
        converted=True,
    )
    e = bp2build_progress.ModuleInfo(
        name='e',
        kind='type3',
        dirname='other',
        num_deps=0,
        created_by=None,
        reasons_from_heuristics=frozenset({'type missing converter'}),
    )
    f = bp2build_progress.ModuleInfo(
        name='f',
        kind='type4',
        dirname='pkg2',
        num_deps=3,
        created_by=None,
        props=frozenset(
            {'Name', 'Sdk_version', 'Visibility', 'Backend.Java.Platform_apis'}
        ),
        reasons_from_heuristics=frozenset(
            {'unconverted dependencies', 'unconverted properties: [Visibility]'}
        ),
    )
    g = bp2build_progress.ModuleInfo(
        name='g',
        kind='type4',
        dirname='pkg2',
        num_deps=2,
        created_by=None,
        converted=True,
    )

    module_graph = {}
    module_graph[a] = bp2build_progress.DepInfo(
        direct_deps=set([b, c]), transitive_deps=set([d, e])
    )
    module_graph[b] = bp2build_progress.DepInfo(direct_deps=set([d]))
    module_graph[c] = bp2build_progress.DepInfo(direct_deps=set([e]))
    module_graph[d] = bp2build_progress.DepInfo()
    module_graph[e] = bp2build_progress.DepInfo()
    module_graph[f] = bp2build_progress.DepInfo(
        direct_deps=set([b, g]), transitive_deps=set([d])
    )
    module_graph[g] = bp2build_progress.DepInfo()

    blocked_modules_transitive = collections.defaultdict(set)
    blocked_modules_transitive[a].update({b, c, d, e})
    blocked_modules_transitive[b].update({d})
    blocked_modules_transitive[c].update({e})
    blocked_modules_transitive[d].update(set())
    blocked_modules_transitive[e].update(set())
    blocked_modules_transitive[f].update({g, b, d})
    blocked_modules_transitive[g].update(set())

    props_by_converted_module_type = collections.defaultdict(set)
    props_by_converted_module_type['type2'].update(
        frozenset(('Name', 'Srcs', 'Resource_dirs', 'Defaults'))
    )
    props_by_converted_module_type['type4'].update(
        frozenset(('Name', 'Sdk_version', 'Backend.Java.Platform_apis'))
    )

    report_data = bp2build_progress.generate_report_data(
        module_graph,
        {d.name: {d.kind}, g.name: {g.kind}},
        bp2build_progress.GraphFilterInfo(
            module_names={'a', 'f'}, package_dir=None
        ),
        props_by_converted_module_type,
        use_queryview=False,
        show_converted=True,
        bp2build_metrics=Bp2BuildMetrics(),
    )

    expected_message = bp2build_pb2.Bp2buildConversionProgress(
        root_modules=[m.module.name for m in report_data.input_modules],
        num_deps=len(report_data.total_deps),
    )
    for (
        module,
        unconverted_deps,
    ) in report_data.blocked_modules_transitive.items():
      expected_message.unconverted.add(
          name=module.name,
          directory=module.dirname,
          type=module.kind,
          unconverted_deps={d.name for d in unconverted_deps},
          num_deps=module.num_deps,
          unconverted_reasons_from_heuristics=list(
              module.reasons_from_heuristics
          ),
      )

    message = bp2build_progress.generate_proto(report_data)
    self.assertEqual(message, expected_message)

  def test_generate_dot_file(self):
    self.maxDiff = None
    a = bp2build_progress.ModuleInfo(
        name='a', kind='type1', dirname='pkg', num_deps=2, created_by=None
    )
    b = bp2build_progress.ModuleInfo(
        name='b', kind='type2', dirname='pkg', num_deps=1, created_by=None
    )
    c = bp2build_progress.ModuleInfo(
        name='c', kind='type2', dirname='other', num_deps=1, created_by=None
    )
    d = bp2build_progress.ModuleInfo(
        name='d', kind='type2', dirname='pkg', num_deps=0, created_by=None
    )
    e = bp2build_progress.ModuleInfo(
        name='e', kind='type2', dirname='other', num_deps=0, created_by=None
    )

    module_graph = {}
    module_graph[a] = bp2build_progress.DepInfo(direct_deps=set([b, c]))
    module_graph[b] = bp2build_progress.DepInfo(direct_deps=set([d]))
    module_graph[c] = bp2build_progress.DepInfo(direct_deps=set([e]))
    module_graph[d] = bp2build_progress.DepInfo()
    module_graph[e] = bp2build_progress.DepInfo()

    dot_graph = bp2build_progress.generate_dot_file(
        module_graph, {'e': {'type2'}}, False
    )

    expected_dot_graph = """
digraph mygraph {{
  node [shape=box];

  "a" [label="a\\ntype1" color=black, style=filled, fillcolor=tomato]
  "a" -> "b"
  "a" -> "c"
  "b" [label="b\\ntype2" color=black, style=filled, fillcolor=tomato]
  "b" -> "d"
  "c" [label="c\\ntype2" color=black, style=filled, fillcolor=yellow]
  "d" [label="d\\ntype2" color=black, style=filled, fillcolor=yellow]
}}
"""
    self.assertEqual(dot_graph, expected_dot_graph)

  def test_generate_dot_file_show_converted(self):
    self.maxDiff = None
    a = bp2build_progress.ModuleInfo(
        name='a', kind='type1', dirname='pkg', num_deps=2, created_by=None
    )
    b = bp2build_progress.ModuleInfo(
        name='b', kind='type2', dirname='pkg', num_deps=1, created_by=None
    )
    c = bp2build_progress.ModuleInfo(
        name='c', kind='type2', dirname='other', num_deps=1, created_by=None
    )
    d = bp2build_progress.ModuleInfo(
        name='d', kind='type2', dirname='pkg', num_deps=0, created_by=None
    )
    e = bp2build_progress.ModuleInfo(
        name='e', kind='type2', dirname='other', num_deps=0, created_by=None
    )

    module_graph = {}
    module_graph[a] = bp2build_progress.DepInfo(direct_deps=set([b, c]))
    module_graph[b] = bp2build_progress.DepInfo(direct_deps=set([d]))
    module_graph[c] = bp2build_progress.DepInfo(direct_deps=set([e]))
    module_graph[d] = bp2build_progress.DepInfo()
    module_graph[e] = bp2build_progress.DepInfo()

    dot_graph = bp2build_progress.generate_dot_file(
        module_graph, {'e': {'type2'}}, True
    )

    expected_dot_graph = """
digraph mygraph {{
  node [shape=box];

  "a" [label="a\\ntype1" color=black, style=filled, fillcolor=tomato]
  "a" -> "b"
  "a" -> "c"
  "b" [label="b\\ntype2" color=black, style=filled, fillcolor=tomato]
  "b" -> "d"
  "c" [label="c\\ntype2" color=black, style=filled, fillcolor=yellow]
  "c" -> "e"
  "d" [label="d\\ntype2" color=black, style=filled, fillcolor=yellow]
  "e" [label="e\\ntype2" color=black, style=filled, fillcolor=dodgerblue]
}}
"""
    self.assertEqual(dot_graph, expected_dot_graph)


if __name__ == '__main__':
  unittest.main()
