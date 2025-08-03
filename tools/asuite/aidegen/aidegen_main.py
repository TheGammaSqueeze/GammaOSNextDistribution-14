#!/usr/bin/env python3
#
# Copyright 2018 - The Android Open Source Project
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

"""AIDEgen

This CLI generates project files for using in IntelliJ, such as:
    - iml
    - .idea/compiler.xml
    - .idea/misc.xml
    - .idea/modules.xml
    - .idea/vcs.xml
    - .idea/.name
    - .idea/copyright/Apache_2.xml
    - .idea/copyright/profiles_settings.xml

- Sample usage:
    - Change directory to AOSP root first.
    $ cd /user/home/aosp/
    - Generating project files under packages/apps/Settings folder.
    $ aidegen packages/apps/Settings
    or
    $ aidegen Settings
    or
    $ cd packages/apps/Settings;aidegen
"""

from __future__ import absolute_import

import argparse
import os
import sys
import traceback

from pathlib import Path

from aidegen import constant
from aidegen.lib import aidegen_metrics
from aidegen.lib import common_util
from aidegen.lib import eclipse_project_file_gen
from aidegen.lib import errors
from aidegen.lib import ide_util
from aidegen.lib import module_info
from aidegen.lib import native_module_info
from aidegen.lib import native_project_info
from aidegen.lib import native_util
from aidegen.lib import project_config
from aidegen.lib import project_file_gen
from aidegen.lib import project_info
from aidegen.vscode import vscode_native_project_file_gen
from aidegen.vscode import vscode_workspace_file_gen

AIDEGEN_REPORT_LINK = ('To report an AIDEGen tool problem, please use this '
                       'link: https://goto.google.com/aidegen-bug')
_CONGRATULATIONS = common_util.COLORED_PASS('CONGRATULATIONS:')
_LAUNCH_SUCCESS_MSG = (
    'IDE launched successfully. Please check your IDE window.')
_LAUNCH_ECLIPSE_SUCCESS_MSG = (
    'The project files .classpath and .project are generated under '
    '{PROJECT_PATH} and AIDEGen doesn\'t import the project automatically, '
    'please import the project manually by steps: File -> Import -> select \''
    'General\' -> \'Existing Projects into Workspace\' -> click \'Next\' -> '
    'Choose the root directory -> click \'Finish\'.')
_IDE_CACHE_REMINDER_MSG = (
    'To prevent the existed IDE cache from impacting your IDE dependency '
    'analysis, please consider to clear IDE caches if necessary. To do that, '
    'in IntelliJ IDEA, go to [File > Invalidate Caches -> '
    'Invalidate and Restart].')

_MAX_TIME = 1
_SKIP_BUILD_INFO_FUTURE = ''.join([
    'AIDEGen build time exceeds {} minute(s).\n'.format(_MAX_TIME),
    project_config.SKIP_BUILD_INFO.rstrip('.'), ' in the future.'
])
_INFO = common_util.COLORED_INFO('INFO:')
_SKIP_MSG = _SKIP_BUILD_INFO_FUTURE.format(
    common_util.COLORED_INFO('aidegen [ module(s) ] -s'))
_TIME_EXCEED_MSG = '\n{} {}\n'.format(_INFO, _SKIP_MSG)
_LAUNCH_CLION_IDES = [
    constant.IDE_CLION, constant.IDE_INTELLIJ, constant.IDE_ECLIPSE]
_CHOOSE_LANGUAGE_MSG = ('The scope of your modules contains {} different '
                        'languages as follows:\n{}\nPlease select the one you '
                        'would like to implement.\t')
_LANGUAGE_OPTIONS = [constant.JAVA, constant.C_CPP]
_NO_ANY_PROJECT_EXIST = 'There is no Java, C/C++ or Rust target.'
_NO_LANGUAGE_PROJECT_EXIST = 'There is no {} target.'
_NO_IDE_LAUNCH_PATH = 'Can not find the IDE path : {}'
_AIDEGEN_TRANSITION_MSG = (
    'Please note that AIDEGen is no longer supported. '
    'We encourage you to use Android Studio for Platform (ASfP). '
    'Visit go/asfp or google Android Studio for Platform '
    'for more information.')


def _parse_args(args):
    """Parse command line arguments.

    Args:
        args: A list of arguments.

    Returns:
        An argparse.Namespace class instance holding parsed args.
    """
    parser = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
        usage=('aidegen [module_name1 module_name2... '
               'project_path1 project_path2...]'))
    parser.required = False
    parser.add_argument(
        'targets',
        type=str,
        nargs='*',
        default=[''],
        help=('Android module name or path.'
              'e.g. Settings or packages/apps/Settings'))
    parser.add_argument(
        '-d',
        '--depth',
        type=int,
        choices=range(10),
        default=0,
        help='The depth of module referenced by source.')
    parser.add_argument(
        '-v',
        '--verbose',
        action='store_true',
        help='Display DEBUG level logging.')
    parser.add_argument(
        '-i',
        '--ide',
        default=['u'],
        help=('Launch IDE type, j: IntelliJ, s: Android Studio, e: Eclipse, '
              'c: CLion, v: VS Code. The default value is \'u\': undefined.'))
    parser.add_argument(
        '-p',
        '--ide-path',
        dest='ide_installed_path',
        help='IDE installed path.')
    parser.add_argument(
        '-n', '--no_launch', action='store_true', help='Do not launch IDE.')
    parser.add_argument(
        '-r',
        '--config-reset',
        dest='config_reset',
        action='store_true',
        help='Reset all saved configurations, e.g., preferred IDE version.')
    parser.add_argument(
        '-s',
        '--skip-build',
        dest='skip_build',
        action='store_true',
        help=('Skip building jars or modules that create java files in build '
              'time, e.g. R/AIDL/Logtags.'))
    parser.add_argument(
        '-a',
        '--android-tree',
        dest='android_tree',
        action='store_true',
        help='Generate whole Android source tree project file for IDE.')
    parser.add_argument(
        '-e',
        '--exclude-paths',
        dest='exclude_paths',
        nargs='*',
        help='Exclude the directories in IDE.')
    parser.add_argument(
        '-V',
        '--version',
        action='store_true',
        help='Print aidegen version string.')
    parser.add_argument(
        '-l',
        '--language',
        default=['u'],
        help=('Launch IDE with a specific language, j: Java, c: C/C++, r: '
              'Rust. The default value is \'u\': undefined.'))
    return parser.parse_args(args)


def _generate_project_files(projects):
    """Generate project files by IDE type.

    Args:
        projects: A list of ProjectInfo instances.
    """
    config = project_config.ProjectConfig.get_instance()
    if config.ide_name == constant.IDE_ECLIPSE:
        eclipse_project_file_gen.EclipseConf.generate_ide_project_files(
            projects)
    else:
        project_file_gen.ProjectFileGenerator.generate_ide_project_files(
            projects)


def _launch_ide(ide_util_obj, project_absolute_path):
    """Launch IDE through ide_util instance.

    To launch IDE,
    1. Set IDE config.
    2. For IntelliJ, use .idea as open target is better than .iml file,
       because open the latter is like to open a kind of normal file.
    3. Show _LAUNCH_SUCCESS_MSG to remind users IDE being launched.

    Args:
        ide_util_obj: An ide_util instance.
        project_absolute_path: A string of project absolute path.
    """
    ide_util_obj.config_ide(project_absolute_path)
    if ide_util_obj.ide_name() == constant.IDE_ECLIPSE:
        launch_msg = ' '.join([_LAUNCH_SUCCESS_MSG,
                               _LAUNCH_ECLIPSE_SUCCESS_MSG.format(
                                   PROJECT_PATH=project_absolute_path)])
    else:
        launch_msg = _LAUNCH_SUCCESS_MSG
    print('\n{} {}\n'.format(_CONGRATULATIONS, launch_msg))
    print('\n{} {}\n'.format(_INFO, _IDE_CACHE_REMINDER_MSG))
    # Send the end message to Clearcut server before launching IDE to make sure
    # the execution time is correct.
    aidegen_metrics.ends_asuite_metrics(constant.EXIT_CODE_EXCEPTION)
    ide_util_obj.launch_ide()


def _launch_native_projects(ide_util_obj, args, cmakelists):
    """Launches C/C++ projects with IDE.

    AIDEGen provides the IDE argument for CLion, but there's still an implicit
    way to launch it. The rules to launch it are:
    1. If no target IDE, we don't have to launch any IDE for C/C++ project.
    2. If the target IDE is IntelliJ or Eclipse, we should launch C/C++
       projects with CLion.

    Args:
        ide_util_obj: An ide_util instance.
        args: An argparse.Namespace class instance holding parsed args.
        cmakelists: A list of CMakeLists.txt file paths.
    """
    if not ide_util_obj:
        return
    native_ide_util_obj = ide_util_obj
    ide_name = constant.IDE_NAME_DICT[args.ide[0]]
    if ide_name in _LAUNCH_CLION_IDES:
        native_ide_util_obj = ide_util.get_ide_util_instance('c')
    if native_ide_util_obj:
        _launch_ide(native_ide_util_obj, ' '.join(cmakelists))


def _create_and_launch_java_projects(ide_util_obj, targets):
    """Launches Android of Java(Kotlin) projects with IDE.

    Args:
        ide_util_obj: An ide_util instance.
        targets: A list of build targets.
    """
    projects = project_info.ProjectInfo.generate_projects(targets)

    project_info.ProjectInfo.multi_projects_locate_source(projects)
    _generate_project_files(projects)
    if ide_util_obj:
        _launch_ide(ide_util_obj, projects[0].project_absolute_path)


def _launch_ide_by_module_contents(args, ide_util_obj, language,
                                   lang_targets, all_langs=False):
    """Deals with the suitable IDE launch action.

    The rules of AIDEGen launching IDE with languages are:
      1. If no IDE or language is specific, the priority of the language is:
         a) Java
            aidegen frameworks/base
            launch Java projects of frameworks/base in IntelliJ.
         b) C/C++
            aidegen hardware/interfaces/vibrator/aidl/default
            launch C/C++ project of hardware/interfaces/vibrator/aidl/default
            in CLion.
         c) Rust
            aidegen external/rust/crates/protobuf
            launch Rust project of external/rust/crates/protobuf in VS Code.
      2. If the IDE is specific, launch related projects in the IDE.
         a) aidegen frameworks/base -i j
            launch Java projects of frameworks/base in IntelliJ.
            aidegen frameworks/base -i s
            launch Java projects of frameworks/base in Android Studio.
            aidegen frameworks/base -i e
            launch Java projects of frameworks/base in Eclipse.
         b) aidegen frameworks/base -i c
            launch C/C++ projects of frameworks/base in CLion.
         c) aidegen external/rust/crates/protobuf -i v
            launch Rust project of external/rust/crates/protobuf in VS Code.
      3. If the language is specific, launch relative language projects in the
         relative IDE.
         a) aidegen frameworks/base -l j
            launch Java projects of frameworks/base in IntelliJ.
         b) aidegen frameworks/base -l c
            launch C/C++ projects of frameworks/base in CLion.
         c) aidegen external/rust/crates/protobuf -l r
            launch Rust projects of external/rust/crates/protobuf in VS Code.
      4. Both of the IDE and language are specific, launch the IDE with the
         relative language projects. If the IDE conflicts with the language, the
         IDE is prior to the language.
         a) aidegen frameworks/base -i j -l j
            launch Java projects of frameworks/base in IntelliJ.
         b) aidegen frameworks/base -i s -l c
            launch C/C++ projects of frameworks/base in Android Studio.
         c) aidegen frameworks/base -i c -l j
            launch Java projects of frameworks/base in CLion.

    Args:
        args: A list of system arguments.
        ide_util_obj: An ide_util instance.
        language: A string of the language to be edited in the IDE.
        lang_targets: A dict contains None or list of targets of different
            languages. E.g.
            {
               'Java': ['modules1', 'modules2'],
               'C/C++': ['modules3'],
               'Rust': None,
               ...
            }
        all_langs: A boolean, True to launch all languages else False.
    """
    if lang_targets is None:
        print(constant.WARN_MSG.format(
            common_util.COLORED_INFO('Warning:'), _NO_ANY_PROJECT_EXIST))
        return

    targets = lang_targets
    java_list = targets[constant.JAVA] if constant.JAVA in targets else None
    c_cpp_list = targets[constant.C_CPP] if constant.C_CPP in targets else None
    rust_list = targets[constant.RUST] if constant.RUST in targets else None

    if all_langs:
        _launch_vscode(ide_util_obj, project_info.ProjectInfo.modules_info,
                       java_list, c_cpp_list, rust_list)
        return
    if not (java_list or c_cpp_list or rust_list):
        print(constant.WARN_MSG.format(
            common_util.COLORED_INFO('Warning:'), _NO_ANY_PROJECT_EXIST))
        return
    if language == constant.JAVA:
        if not java_list:
            print(constant.WARN_MSG.format(
                common_util.COLORED_INFO('Warning:'),
                _NO_LANGUAGE_PROJECT_EXIST.format(constant.JAVA)))
            return
        _create_and_launch_java_projects(ide_util_obj, java_list)
        return
    if language == constant.C_CPP:
        if not c_cpp_list:
            print(constant.WARN_MSG.format(
                common_util.COLORED_INFO('Warning:'),
                _NO_LANGUAGE_PROJECT_EXIST.format(constant.C_CPP)))
            return
        native_project_info.NativeProjectInfo.generate_projects(c_cpp_list)
        native_project_file = native_util.generate_clion_projects(c_cpp_list)
        if native_project_file:
            _launch_native_projects(ide_util_obj, args, [native_project_file])


def _launch_vscode(ide_util_obj, atest_module_info, jtargets, ctargets,
                   rtargets):
    """Launches targets with VSCode IDE.

    Args:
        ide_util_obj: An ide_util instance.
        atest_module_info: A ModuleInfo instance contains the data of
                module-info.json.
        jtargets: A list of Java project targets.
        ctargets: A list of C/C++ project targets.
        rtargets: A list of Rust project targets.
    """
    abs_paths = []
    if jtargets:
        abs_paths.extend(_get_java_project_paths(jtargets, atest_module_info))
    if ctargets:
        abs_paths.extend(_get_cc_project_paths(ctargets))
    if rtargets:
        root_dir = common_util.get_android_root_dir()
        abs_paths.extend(_get_rust_project_paths(rtargets, root_dir))
    if not (jtargets or ctargets or rtargets):
        print(constant.WARN_MSG.format(
            common_util.COLORED_INFO('Warning:'), _NO_ANY_PROJECT_EXIST))
        return
    vs_path = vscode_workspace_file_gen.generate_code_workspace_file(abs_paths)
    if not ide_util_obj:
        return
    _launch_ide(ide_util_obj, vs_path)


def _get_java_project_paths(jtargets, atest_module_info):
    """Gets the Java absolute project paths from the input Java targets.

    Args:
        jtargets: A list of strings of Java targets.
        atest_module_info: A ModuleInfo instance contains the data of
                module-info.json.

    Returns:
        A list of the Java absolute project paths.
    """
    abs_paths = []
    for target in jtargets:
        _, abs_path = common_util.get_related_paths(atest_module_info, target)
        if abs_path:
            abs_paths.append(abs_path)
    return abs_paths


def _get_cc_project_paths(ctargets):
    """Gets the C/C++ absolute project paths from the input C/C++ targets.

    Args:
        ctargets: A list of strings of C/C++ targets.

    Returns:
        A list of the C/C++ absolute project paths.
    """
    abs_paths = []
    cc_module_info = native_module_info.NativeModuleInfo()
    native_project_info.NativeProjectInfo.generate_projects(ctargets)
    vs_gen = vscode_native_project_file_gen.VSCodeNativeProjectFileGenerator
    for target in ctargets:
        _, abs_path = common_util.get_related_paths(cc_module_info, target)
        if not abs_path:
            continue
        vs_native = vs_gen(abs_path)
        vs_native.generate_c_cpp_properties_json_file()
        if abs_path not in abs_paths:
            abs_paths.append(abs_path)
    return abs_paths


def _get_rust_project_paths(rtargets, root_dir):
    """Gets the Rust absolute project paths from the input Rust targets.

    Args:
        rtargets: A list of strings of Rust targets.
        root_dir: A string of the Android root directory.

    Returns:
        A list of the Rust absolute project paths.
    """
    abs_paths = []
    for rtarget in rtargets:
        path = rtarget
        # If rtarget is not an absolute path, make it an absolute one.
        if not common_util.is_source_under_relative_path(rtarget, root_dir):
            path = os.path.join(root_dir, rtarget)
        abs_paths.append(path)
    return abs_paths


def _get_targets_from_args(targets, android_tree):
    """Gets targets for specific argument.

    For example:
        $aidegen     : targets = ['.']
        $aidegen -a  : targets = []
        $aidegen .   : targets = ['.']
        $aidegen . -a: targets = []

    Args:
        targets: A list of strings of targets.
        android_tree: A boolean, True with '-a' argument else False.

    Returns:
        A list of the Rust absolute project paths.
    """
    if targets == [''] and not android_tree:
        return ['.']
    if android_tree:
        return []
    return targets


@common_util.time_logged(message=_TIME_EXCEED_MSG, maximum=_MAX_TIME)
def main_with_message(args):
    """Main entry with skip build message.

    Args:
        args: A list of system arguments.
    """
    aidegen_main(args)


@common_util.time_logged
def main_without_message(args):
    """Main entry without skip build message.

    Args:
        args: A list of system arguments.
    """
    aidegen_main(args)


# pylint: disable=broad-except
def main(argv):
    """Main entry.

    Show skip build message in aidegen main process if users command skip_build
    otherwise remind them to use it and include metrics supports.

    Args:
        argv: A list of system arguments.
    """
    exit_code = constant.EXIT_CODE_NORMAL
    launch_ide = True
    ask_version = False
    try:
        args = _parse_args(argv)
        args.targets = _get_targets_from_args(args.targets, args.android_tree)
        if args.version:
            ask_version = True
            version_file = os.path.join(os.path.dirname(__file__),
                                        constant.VERSION_FILE)
            print(common_util.read_file_content(version_file))
            sys.exit(constant.EXIT_CODE_NORMAL)
        if args.ide_installed_path:
            if not Path(args.ide_installed_path).exists():
                print(_NO_IDE_LAUNCH_PATH.format(args.ide_installed_path))
                sys.exit(constant.EXIT_CODE_NORMAL)

        launch_ide = not args.no_launch
        common_util.configure_logging(args.verbose)
        is_whole_android_tree = project_config.is_whole_android_tree(
            args.targets, args.android_tree)
        references = [constant.ANDROID_TREE] if is_whole_android_tree else []
        aidegen_metrics.starts_asuite_metrics(references)
        print(constant.WARN_MSG.format(
              common_util.COLORED_INFO('INFO:'), _AIDEGEN_TRANSITION_MSG))
        if args.skip_build:
            main_without_message(args)
        else:
            main_with_message(args)
    except BaseException as err:
        exit_code = constant.EXIT_CODE_EXCEPTION
        _, exc_value, exc_traceback = sys.exc_info()
        if isinstance(err, errors.AIDEgenError):
            exit_code = constant.EXIT_CODE_AIDEGEN_EXCEPTION
        # Filter out sys.Exit(0) case, which is not an exception case.
        if isinstance(err, SystemExit) and exc_value.code == 0:
            exit_code = constant.EXIT_CODE_NORMAL
        if exit_code is not constant.EXIT_CODE_NORMAL:
            error_message = str(exc_value)
            traceback_list = traceback.format_tb(exc_traceback)
            traceback_list.append(error_message)
            traceback_str = ''.join(traceback_list)
            aidegen_metrics.ends_asuite_metrics(exit_code, traceback_str,
                                                error_message)
            # print out the traceback message for developers to debug
            print(traceback_str)
            raise err
    finally:
        if not ask_version:
            print('\n{0} {1}\n'.format(_INFO, AIDEGEN_REPORT_LINK))
            # Send the end message here on ignoring launch IDE case.
            if not launch_ide and exit_code is constant.EXIT_CODE_NORMAL:
                aidegen_metrics.ends_asuite_metrics(exit_code)


def aidegen_main(args):
    """AIDEGen main entry.

    Try to generate project files for using in IDE. The process is:
      1. Instantiate a ProjectConfig singleton object and initialize its
         environment. After creating a singleton instance for ProjectConfig,
         other modules can use project configurations by
         ProjectConfig.get_instance().
      2. Get an IDE instance from ide_util, ide_util.get_ide_util_instance will
         use ProjectConfig.get_instance() inside the function.
      3. Setup project_info.ProjectInfo.modules_info by instantiate
         AidegenModuleInfo.
      4. Check if projects contain C/C++ projects and launch related IDE.

    Args:
        args: A list of system arguments.
    """
    config = project_config.ProjectConfig(args)
    config.init_environment()
    targets = config.targets
    project_info.ProjectInfo.modules_info = module_info.AidegenModuleInfo()
    cc_module_info = native_module_info.NativeModuleInfo()
    jtargets, ctargets, rtargets = native_util.get_java_cc_and_rust_projects(
        project_info.ProjectInfo.modules_info, cc_module_info, targets)
    config.language, config.ide_name = common_util.determine_language_ide(
        args.language[0], args.ide[0], jtargets, ctargets, rtargets)
    # Called ide_util for pre-check the IDE existence state.
    ide_util_obj = ide_util.get_ide_util_instance(
        constant.IDE_DICT[config.ide_name])
    all_langs = config.ide_name == constant.IDE_VSCODE
    # Backward compatible strategy, when both java and C/C++ module exist,
    # check the preferred target from the user and launch single one.
    language_targets = {constant.JAVA: jtargets,
                        constant.C_CPP: ctargets,
                        constant.RUST: rtargets}
    _launch_ide_by_module_contents(args, ide_util_obj, config.language,
                                   language_targets, all_langs)


if __name__ == '__main__':
    main(sys.argv[1:])
