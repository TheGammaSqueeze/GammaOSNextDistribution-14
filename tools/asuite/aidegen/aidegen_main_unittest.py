#!/usr/bin/env python3
#
# Copyright 2019, The Android Open Source Project
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

"""Unittests for aidegen_main."""

from __future__ import print_function

import os
import sys
import unittest
from unittest import mock

from aidegen import aidegen_main
from aidegen import constant
from aidegen import unittest_constants
from aidegen.lib import aidegen_metrics
from aidegen.lib import common_util
from aidegen.lib import eclipse_project_file_gen
from aidegen.lib import errors
from aidegen.lib import ide_util
from aidegen.lib import module_info
from aidegen.lib import native_util
from aidegen.lib import native_module_info
from aidegen.lib import native_project_info
from aidegen.lib import project_config
from aidegen.lib import project_file_gen
from aidegen.lib import project_info
from aidegen.vscode import vscode_workspace_file_gen
from aidegen.vscode import vscode_native_project_file_gen


# pylint: disable=protected-access
# pylint: disable=too-many-arguments
# pylint: disable=too-many-statements
# pylint: disable=too-many-locals
class AidegenMainUnittests(unittest.TestCase):
    """Unit tests for aidegen_main.py"""

    def _init_project_config(self, args):
        """Initialize project configurations."""
        self.assertIsNotNone(args)
        config = project_config.ProjectConfig(args)
        config.init_environment()

    def test_parse_args(self):
        """Test _parse_args with different conditions."""
        args = aidegen_main._parse_args([])
        self.assertEqual(args.targets, [''])
        self.assertEqual(args.ide[0], 'u')
        target = 'tradefed'
        args = aidegen_main._parse_args([target])
        self.assertEqual(args.targets, [target])
        depth = '2'
        args = aidegen_main._parse_args(['-d', depth])
        self.assertEqual(args.depth, int(depth))
        args = aidegen_main._parse_args(['-v'])
        self.assertTrue(args.verbose)
        args = aidegen_main._parse_args(['-v'])
        self.assertTrue(args.verbose)
        args = aidegen_main._parse_args(['-i', 's'])
        self.assertEqual(args.ide[0], 's')
        args = aidegen_main._parse_args(['-i', 'e'])
        self.assertEqual(args.ide[0], 'e')
        args = aidegen_main._parse_args(['-p', unittest_constants.TEST_MODULE])
        self.assertEqual(args.ide_installed_path,
                         unittest_constants.TEST_MODULE)
        args = aidegen_main._parse_args(['-n'])
        self.assertTrue(args.no_launch)
        args = aidegen_main._parse_args(['-r'])
        self.assertTrue(args.config_reset)
        args = aidegen_main._parse_args(['-s'])
        self.assertTrue(args.skip_build)
        self.assertEqual(args.exclude_paths, None)
        excludes = 'path/to/a', 'path/to/b'
        args = aidegen_main._parse_args(['-e', excludes])
        self.assertEqual(args.exclude_paths, [excludes])

    @mock.patch.object(project_config.ProjectConfig, 'init_environment')
    @mock.patch.object(project_config, 'ProjectConfig')
    @mock.patch.object(project_file_gen.ProjectFileGenerator,
                       'generate_ide_project_files')
    @mock.patch.object(eclipse_project_file_gen.EclipseConf,
                       'generate_ide_project_files')
    def test_generate_project_files(self, mock_eclipse, mock_ide, mock_config,
                                    mock_init):
        """Test _generate_project_files with different conditions."""
        projects = ['module_a', 'module_v']
        args = aidegen_main._parse_args([projects, '-i', 'e'])
        mock_init.return_value = None
        self._init_project_config(args)
        mock_config.ide_name = constant.IDE_ECLIPSE
        aidegen_main._generate_project_files(projects)
        self.assertTrue(mock_eclipse.called_with(projects))
        mock_config.ide_name = constant.IDE_ANDROID_STUDIO
        aidegen_main._generate_project_files(projects)
        self.assertTrue(mock_ide.called_with(projects))
        mock_config.ide_name = constant.IDE_INTELLIJ
        aidegen_main._generate_project_files(projects)
        self.assertTrue(mock_ide.called_with(projects))

    @mock.patch.object(aidegen_main, 'main_with_message')
    @mock.patch.object(aidegen_main, 'main_without_message')
    def test_main(self, mock_without, mock_with):
        """Test main with conditions."""
        aidegen_main.main(['-s'])
        self.assertEqual(mock_without.call_count, 1)
        aidegen_main.main([''])
        self.assertEqual(mock_with.call_count, 1)

    @mock.patch.object(aidegen_metrics, 'starts_asuite_metrics')
    @mock.patch.object(project_config, 'is_whole_android_tree')
    @mock.patch.object(aidegen_metrics, 'ends_asuite_metrics')
    @mock.patch.object(aidegen_main, 'main_with_message')
    def test_main_with_normal(self, mock_main, mock_ends_metrics,
                              mock_is_whole_tree, mock_starts_metrics):
        """Test main with normal conditions."""
        aidegen_main.main(['-h'])
        self.assertFalse(mock_main.called)
        mock_is_whole_tree.return_value = True
        aidegen_main.main([''])
        mock_starts_metrics.assert_called_with([constant.ANDROID_TREE])
        self.assertFalse(mock_ends_metrics.called)
        aidegen_main.main(['-n'])
        mock_ends_metrics.assert_called_with(constant.EXIT_CODE_NORMAL)

    @mock.patch.object(aidegen_metrics, 'ends_asuite_metrics')
    @mock.patch.object(aidegen_main, 'main_with_message')
    def test_main_with_build_fail_errors(self, mock_main, mock_ends_metrics):
        """Test main with raising build failure error conditions."""
        mock_main.side_effect = errors.BuildFailureError
        with self.assertRaises(errors.BuildFailureError):
            aidegen_main.main([''])
            _, exc_value, exc_traceback = sys.exc_info()
            msg = str(exc_value)
            mock_ends_metrics.assert_called_with(
                constant.EXIT_CODE_AIDEGEN_EXCEPTION, exc_traceback, msg)

    @mock.patch.object(aidegen_metrics, 'ends_asuite_metrics')
    @mock.patch.object(aidegen_main, 'main_with_message')
    def test_main_with_io_errors(self, mock_main, mock_ends_metrics):
        """Test main with raising IO error conditions."""
        mock_main.side_effect = IOError
        with self.assertRaises(IOError):
            aidegen_main.main([''])
            _, exc_value, exc_traceback = sys.exc_info()
            msg = str(exc_value)
            mock_ends_metrics.assert_called_with(constant.EXIT_CODE_EXCEPTION,
                                                 exc_traceback, msg)

    @mock.patch.object(aidegen_main, '_launch_ide')
    def test_main_with_wrong_ide_path(self, mock_launch_ide):
        """Test main function with wrong ide path."""
        target = 'libui'
        args = [target, '-p', '/opt/abc/bin/idea.sh']
        aidegen_main.main(args)
        self.assertFalse(mock_launch_ide.called)

    def test_get_targets_from_args(self):
        """Test get targets from different arguments."""
        self.assertEqual(
            aidegen_main._get_targets_from_args([''], True), [])
        self.assertEqual(
            aidegen_main._get_targets_from_args(['.'], False), ['.'])
        self.assertEqual(
            aidegen_main._get_targets_from_args([''], False), ['.'])
        self.assertEqual(
            aidegen_main._get_targets_from_args(['.'], True), [])
        self.assertEqual(
            aidegen_main._get_targets_from_args(['test'], False), ['test'])

    @mock.patch.object(aidegen_main, '_launch_ide')
    @mock.patch.object(ide_util, 'get_ide_util_instance')
    def test_launch_native_projects_without_ide_object(
            self, mock_get_ide, mock_launch_ide):
        """Test _launch_native_projects function without ide object."""
        target = 'libui'
        args = aidegen_main._parse_args([target, '-i', 'e'])
        aidegen_main._launch_native_projects(None, args, [])
        self.assertFalse(mock_get_ide.called)
        self.assertFalse(mock_launch_ide.called)

    @mock.patch.object(aidegen_main, '_launch_ide')
    @mock.patch.object(ide_util, 'get_ide_util_instance')
    def test_launch_native_projects_with_ide_object(
            self, mock_get_ide, mock_launch_ide):
        """Test _launch_native_projects function without ide object."""
        target = 'libui'
        args = aidegen_main._parse_args([target, '-i', 'e'])
        ide_util_obj = 'some_obj'
        mock_get_ide.return_value = None
        aidegen_main._launch_native_projects(ide_util_obj, args, [])
        self.assertTrue(mock_get_ide.called_with('c'))
        self.assertFalse(mock_launch_ide.called)
        mock_get_ide.reset_mock()
        mock_launch_ide.reset_mock()
        args.ide = ['j']
        aidegen_main._launch_native_projects(ide_util_obj, args, [])
        self.assertTrue(mock_get_ide.called_with('c'))
        self.assertFalse(mock_launch_ide.called)
        mock_get_ide.reset_mock()
        mock_launch_ide.reset_mock()
        mock_get_ide.return_value = 'some_native_obj'
        aidegen_main._launch_native_projects(ide_util_obj, args, [])
        self.assertTrue(mock_get_ide.called_with('c'))
        self.assertTrue(mock_launch_ide.called)
        mock_get_ide.reset_mock()
        mock_launch_ide.reset_mock()
        args.ide = ['e']
        aidegen_main._launch_native_projects(ide_util_obj, args, [])
        self.assertTrue(mock_get_ide.called_with('c'))
        self.assertTrue(mock_launch_ide.called)
        mock_get_ide.reset_mock()
        mock_launch_ide.reset_mock()
        args.ide = ['s']
        aidegen_main._launch_native_projects(ide_util_obj, args, [])
        self.assertFalse(mock_get_ide.called)
        self.assertTrue(mock_launch_ide.called)

    @mock.patch('builtins.print')
    def test_launch_ide(self, mock_print):
        """Test _launch_ide function with config parameter."""
        mock_ide_util = mock.MagicMock()
        mock_ide_util.launch_ide.return_value = None
        mock_ide_util.config_ide.return_value = None
        launch_path = '/test/launch/ide/method'
        aidegen_main._launch_ide(mock_ide_util, launch_path)
        self.assertTrue(mock_ide_util.config_ide.called)
        self.assertTrue(mock_ide_util.launch_ide.called)
        mock_print.return_value = None

    @mock.patch.object(project_config.ProjectConfig, 'init_environment')
    @mock.patch('builtins.print')
    @mock.patch.object(aidegen_main, '_launch_vscode')
    @mock.patch.object(aidegen_main, '_launch_native_projects')
    @mock.patch.object(native_util, 'generate_clion_projects')
    @mock.patch.object(native_project_info.NativeProjectInfo,
                       'generate_projects')
    @mock.patch.object(aidegen_main, '_create_and_launch_java_projects')
    def test_launch_ide_by_module_contents(self, mock_j, mock_c_prj, mock_genc,
                                           mock_c, mock_vs, mock_print,
                                           mock_init):
        """Test _launch_ide_by_module_contents with different conditions."""
        args = aidegen_main._parse_args(['', '-i', 's'])
        mock_init.return_value = None
        self._init_project_config(args)
        ide_obj = 'ide_obj'
        test_all = False
        lang = constant.C_CPP
        java_targets = {constant.JAVA: ['a', 'b', 'c'],
                        constant.C_CPP: None, constant.RUST: None}

        aidegen_main._launch_ide_by_module_contents(args, ide_obj, lang,
                                                    java_targets, test_all)

        self.assertFalse(mock_vs.called)
        self.assertTrue(mock_print.called)
        self.assertFalse(mock_j.called)
        self.assertFalse(mock_c_prj.called)
        self.assertFalse(mock_genc.called)
        self.assertFalse(mock_c.called)

        lang = constant.JAVA
        none_targets = {constant.JAVA: None,
                        constant.C_CPP: None,
                        constant.RUST: None}

        aidegen_main._launch_ide_by_module_contents(args, ide_obj, lang,
                                                    none_targets)

        self.assertFalse(mock_vs.called)
        self.assertTrue(mock_print.called)
        self.assertFalse(mock_j.called)
        self.assertFalse(mock_c_prj.called)
        self.assertFalse(mock_genc.called)
        self.assertFalse(mock_c.called)

        c_targets = {constant.JAVA: None,
                     constant.C_CPP: ['1', '2', '3'],
                     constant.RUST: None}

        aidegen_main._launch_ide_by_module_contents(args, ide_obj, lang,
                                                    c_targets, False)
        self.assertFalse(mock_vs.called)
        self.assertTrue(mock_print.called)
        self.assertFalse(mock_j.called)
        self.assertFalse(mock_c_prj.called)
        self.assertFalse(mock_genc.called)
        self.assertFalse(mock_c.called)

        test_all = True

        aidegen_main._launch_ide_by_module_contents(args, ide_obj, lang,
                                                    none_targets, test_all)
        self.assertTrue(mock_vs.called)
        self.assertFalse(mock_j.called)
        self.assertFalse(mock_genc.called)
        self.assertFalse(mock_c.called)
        mock_vs.reset_mock()

        lang_targets = {constant.JAVA: ['a', 'b', 'c'],
                        constant.C_CPP: ['1', '2', '3'],
                        constant.RUST: None}

        aidegen_main._launch_ide_by_module_contents(args, ide_obj, lang,
                                                    lang_targets)
        self.assertFalse(mock_vs.called)
        self.assertTrue(mock_j.called)
        self.assertFalse(mock_genc.called)
        self.assertFalse(mock_c.called)

        mock_vs.reset_mock()
        mock_c.reset_mock()
        mock_genc.reset_mock()
        mock_j.reset_mock()
        args = aidegen_main._parse_args(['', '-l', 'c'])
        mock_init.return_value = None
        self._init_project_config(args)
        lang = constant.C_CPP
        aidegen_main._launch_ide_by_module_contents(args, ide_obj, lang,
                                                    lang_targets)
        self.assertTrue(mock_c_prj.called)
        self.assertFalse(mock_vs.called)
        self.assertTrue(mock_genc.called)
        self.assertTrue(mock_c.called)
        self.assertFalse(mock_j.called)

        mock_vs.reset_mock()
        mock_c.reset_mock()
        mock_genc.reset_mock()
        mock_j.reset_mock()
        aidegen_main._launch_ide_by_module_contents(args, ide_obj, lang,
                                                    c_targets)
        self.assertFalse(mock_vs.called)
        self.assertTrue(mock_genc.called)
        self.assertTrue(mock_c.called)
        self.assertFalse(mock_j.called)

        mock_vs.reset_mock()
        mock_c.reset_mock()
        mock_genc.reset_mock()
        mock_j.reset_mock()
        lang = constant.JAVA
        aidegen_main._launch_ide_by_module_contents(args, ide_obj, lang,
                                                    java_targets)
        self.assertFalse(mock_vs.called)
        self.assertTrue(mock_j.called)
        self.assertFalse(mock_c.called)
        self.assertFalse(mock_genc.called)

        args = aidegen_main._parse_args(['frameworks/base', '-i', 'c'])
        mock_vs.reset_mock()
        mock_c.reset_mock()
        mock_genc.reset_mock()
        mock_c_prj.reset_mock()
        mock_j.reset_mock()
        lang = constant.C_CPP
        aidegen_main._launch_ide_by_module_contents(args, ide_obj, lang,
                                                    lang_targets)
        self.assertFalse(mock_vs.called)
        self.assertFalse(mock_j.called)
        self.assertTrue(mock_c.called)
        self.assertTrue(mock_c_prj.called)
        self.assertTrue(mock_genc.called)

        args = aidegen_main._parse_args(['frameworks/base'])
        mock_vs.reset_mock()
        mock_c.reset_mock()
        mock_genc.reset_mock()
        mock_c_prj.reset_mock()
        mock_j.reset_mock()
        os.environ[constant.AIDEGEN_TEST_MODE] = 'true'
        lang = constant.JAVA
        aidegen_main._launch_ide_by_module_contents(args, None, lang,
                                                    lang_targets)
        self.assertFalse(mock_vs.called)
        self.assertTrue(mock_j.called)
        self.assertFalse(mock_c.called)
        self.assertFalse(mock_c_prj.called)
        self.assertFalse(mock_genc.called)
        del os.environ[constant.AIDEGEN_TEST_MODE]

    @mock.patch.object(aidegen_main, '_launch_ide')
    @mock.patch.object(aidegen_main, '_generate_project_files')
    @mock.patch.object(project_info.ProjectInfo, 'multi_projects_locate_source')
    @mock.patch.object(project_info.ProjectInfo, 'generate_projects')
    def test_create_and_launch_java_projects(self, mock_prj, mock_compile,
                                             mock_gen_file, mock_launch):
        """Test _create_and_launch_java_projects."""
        ide_obj = 'test_ide'
        target = ['a', 'b']
        mock_prj_list = mock.MagicMock()
        mock_prj_list.project_absolute_path = 'test_path'
        prj = [mock_prj_list]
        mock_prj.return_value = prj
        aidegen_main._create_and_launch_java_projects(ide_obj, target)
        self.assertTrue(mock_prj.called_with(target))
        self.assertTrue(mock_compile.called_with(prj))
        self.assertTrue(mock_gen_file.called_with(prj))
        self.assertTrue(mock_launch.called_with(ide_obj))
        mock_launch.reset_mock()

        aidegen_main._create_and_launch_java_projects(None, target)
        self.assertFalse(mock_launch.called)

    @mock.patch.object(aidegen_main, '_launch_ide')
    @mock.patch.object(vscode_workspace_file_gen,
                       'generate_code_workspace_file')
    @mock.patch.object(common_util, 'get_related_paths')
    def test_launch_vscode_without_ide_object(
            self, mock_get_rel, mock_gen_code, mock_launch_ide):
        """Test _launch_vscode function without ide object."""
        mock_get_rel.return_value = 'rel', 'abs'
        aidegen_main._launch_vscode(None, mock.Mock(), ['Settings'], [], [])
        self.assertTrue(mock_get_rel.called)
        self.assertTrue(mock_gen_code.called)
        self.assertFalse(mock_launch_ide.called)

    @mock.patch.object(aidegen_main, '_launch_ide')
    @mock.patch.object(vscode_workspace_file_gen,
                       'generate_code_workspace_file')
    @mock.patch.object(common_util, 'get_related_paths')
    def test_launch_vscode_with_ide_object(
            self, mock_get_rel, mock_gen_code, mock_get_ide):
        """Test _launch_vscode function with ide object."""
        mock_get_rel.return_value = 'rel', 'abs'
        aidegen_main._launch_vscode(
            mock.Mock(), mock.Mock(), ['Settings'], [], [])
        self.assertTrue(mock_get_rel.called)
        self.assertTrue(mock_gen_code.called)
        self.assertTrue(mock_get_ide.called)

    @mock.patch.object(aidegen_main, '_launch_vscode')
    @mock.patch.object(aidegen_main, '_launch_ide')
    @mock.patch.object(vscode_workspace_file_gen,
                       'generate_code_workspace_file')
    @mock.patch.object(common_util, 'get_related_paths')
    def test_launch_vscode_with_both_languages(
            self, mock_get_rel, mock_gen_code, mock_get_ide, mock_vscode):
        """Test _launch_vscode function without ide object."""
        mock_get_rel.return_value = 'rel', 'abs'
        aidegen_main._launch_vscode(None, mock.Mock(), ['Settings'], [], True)
        self.assertFalse(mock_get_rel.called)
        self.assertFalse(mock_gen_code.called)
        self.assertFalse(mock_get_ide.called)
        self.assertTrue(mock_vscode.called)

    @mock.patch('builtins.print')
    @mock.patch.object(aidegen_main, '_launch_ide')
    @mock.patch.object(vscode_workspace_file_gen,
                       'generate_code_workspace_file')
    @mock.patch.object(common_util, 'get_android_root_dir')
    @mock.patch.object(aidegen_main, '_get_rust_project_paths')
    @mock.patch.object(aidegen_main, '_get_cc_project_paths')
    @mock.patch.object(aidegen_main, '_get_java_project_paths')
    def test_launch_vscode_with_logic(self, mock_get_java, mock_get_cc,
                                      mock_get_rust, mock_get_root, mock_gen,
                                      mock_launch, mock_print):
        """Test _launch_vscode with the logic tests."""
        aidegen_main._launch_vscode(None, mock.Mock(), [], [], [])
        self.assertFalse(mock_get_java.called)
        self.assertFalse(mock_get_cc.called)
        self.assertFalse(mock_get_rust.called)
        self.assertFalse(mock_get_root.called)
        self.assertFalse(mock_gen.called)
        self.assertFalse(mock_launch.called)
        self.assertTrue(mock_print.called)

        mock_get_java.mock_reset()
        mock_get_cc.mock_reset()
        mock_get_rust.mock_reset()
        mock_get_root.mock_reset()
        mock_gen.mock_reset()
        mock_launch.mock_reset()
        mock_print.mock_reset()

        aidegen_main._launch_vscode(
            mock.Mock(), mock.Mock(), ['Java'], ['CC'], ['Rust'])
        self.assertTrue(mock_get_java.called)
        self.assertTrue(mock_get_cc.called)
        self.assertTrue(mock_get_rust.called)
        self.assertTrue(mock_get_root.called)
        self.assertTrue(mock_gen.called)
        self.assertTrue(mock_launch.called)

    @mock.patch.object(common_util, 'get_related_paths')
    def test_get_java_project_paths(self, mock_get_related):
        """Test _get_java_project_paths with conditions."""
        abs_path = 'a/b/c/d'
        rel_path = 'c/d'
        mock_get_related.return_value = rel_path, abs_path
        self.assertEqual(
            [abs_path], aidegen_main._get_java_project_paths(
                ['Java'], mock.Mock()))
        mock_get_related.return_value = None, None
        self.assertEqual(
            [], aidegen_main._get_java_project_paths(['Java'], mock.Mock()))

    @mock.patch.object(
        vscode_native_project_file_gen.VSCodeNativeProjectFileGenerator,
        'generate_c_cpp_properties_json_file')
    @mock.patch.object(
        vscode_native_project_file_gen, 'VSCodeNativeProjectFileGenerator')
    @mock.patch.object(common_util, 'get_related_paths')
    @mock.patch.object(
        native_project_info.NativeProjectInfo, 'generate_projects')
    @mock.patch.object(native_module_info, 'NativeModuleInfo')
    def test_get_cc_project_paths(self, mock_mod_info, mock_gen, mock_get_rel,
                                  mock_gen_vs, mock_gen_vs_file):
        """Test _get_cc_project_paths with conditions."""
        mock_get_rel.return_value = None, None
        self.assertEqual([], aidegen_main._get_cc_project_paths(['Java']))
        self.assertTrue(mock_mod_info.called)
        self.assertTrue(mock_gen.called)
        self.assertTrue(mock_get_rel.called)
        self.assertFalse(mock_gen_vs.called)
        self.assertFalse(mock_gen_vs_file.called)

        mock_mod_info.mock_reset()
        mock_gen.mock_reset()
        mock_get_rel.mock_reset()
        mock_gen_vs.mock_reset()
        mock_gen_vs_file.mock_reset()

        abs_path = 'a/b/c/d'
        rel_path = 'c/d'
        mock_get_rel.return_value = rel_path, abs_path
        self.assertEqual([abs_path], aidegen_main._get_cc_project_paths(['CC']))
        self.assertTrue(mock_mod_info.called)
        self.assertTrue(mock_gen.called)
        self.assertTrue(mock_get_rel.called)
        self.assertTrue(mock_gen_vs.called)

    def test_get_rust_project_paths(self):
        """Test _get_rust_project_paths with conditions."""
        abs_path = 'a/b/c/d'
        rel_path = 'c/d'
        root = 'a/b'
        self.assertEqual(
            [abs_path], aidegen_main._get_rust_project_paths([abs_path], root))
        self.assertEqual(
            [abs_path], aidegen_main._get_rust_project_paths([rel_path], root))

    @mock.patch.object(aidegen_main, '_launch_ide_by_module_contents')
    @mock.patch.object(native_util, 'get_java_cc_and_rust_projects')
    @mock.patch.object(native_module_info, 'NativeModuleInfo')
    @mock.patch.object(module_info, 'AidegenModuleInfo')
    @mock.patch.object(ide_util, 'get_ide_util_instance')
    @mock.patch.object(project_config, 'ProjectConfig')
    def test_aidegen_main(self, mock_config, mock_get_ide, mock_mod_info,
                          mock_native, mock_get_project, mock_launch_ide):
        """Test aidegen_main function with conditions."""
        target = 'Settings'
        args = aidegen_main._parse_args([target, '-i', 'v'])
        config = mock.Mock()
        config.targets = [target]
        config.ide_name = constant.IDE_VSCODE
        mock_config.return_value = config
        ide = mock.Mock()
        mock_get_ide.return_value = ide
        mock_get_project.return_value = config.targets, [], []
        aidegen_main.aidegen_main(args)
        self.assertTrue(mock_config.called)
        self.assertTrue(mock_get_ide.called)
        self.assertTrue(mock_mod_info.called)
        self.assertTrue(mock_native.called)
        self.assertTrue(mock_get_project.called)
        mock_launch_ide.assert_called_with(
            args, ide, constant.JAVA,
            {constant.JAVA: [target], constant.C_CPP: [], constant.RUST: []},
            True)

        mock_config.mock_reset()
        mock_get_ide.mock_reset()
        mock_mod_info.mock_reset()
        mock_native.mock_reset()
        mock_get_project.mock_reset()
        mock_launch_ide.mock_reset()

        args = aidegen_main._parse_args([target])
        config.ide_name = constant.IDE_INTELLIJ
        aidegen_main.aidegen_main(args)
        self.assertTrue(mock_config.called)
        self.assertTrue(mock_get_ide.called)
        self.assertTrue(mock_mod_info.called)
        self.assertTrue(mock_native.called)
        self.assertTrue(mock_get_project.called)
        mock_launch_ide.assert_called_with(
            args, ide, constant.JAVA,
            {constant.JAVA: [target], constant.C_CPP: [], constant.RUST: []},
            False)


if __name__ == '__main__':
    unittest.main()
