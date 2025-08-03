#!/usr/bin/env python3
#
# Copyright 2021 The Android Open Source Project
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

from pathlib import Path
import re
import shutil
import subprocess
import sys
from typing import List, Union
from xml.dom import minidom


# Use resolve to ensure consistent capitalization between runs of this script, which is important
# for patching functions that insert a path only if it doesn't already exist.
PYTHON_SRC = Path(__file__).parent.parent.resolve()
TOP = PYTHON_SRC.parent.parent.parent

CMAKE_BIN = TOP / 'prebuilts/cmake/windows-x86/bin/cmake.exe'
VS_GENERATOR = 'Visual Studio 17 2022'


def create_new_dir(path: Path) -> None:
    if path.exists():
        shutil.rmtree(path)
    path.mkdir(parents=True)


def run_cmd(args: List[Union[str, Path]], cwd: Path) -> None:
    print(f'cd {cwd}')
    str_args = [str(arg) for arg in args]
    print(subprocess.list2cmdline(str_args))
    subprocess.run(str_args, cwd=cwd, check=True)


def read_xml_file(path: Path) -> minidom.Element:
    doc = minidom.parse(str(path))
    return doc.documentElement


def write_xml_file(root: minidom.Element, path: Path) -> None:
    with open(path, 'w', encoding='utf8') as out:
        out.write('<?xml version="1.0" encoding="utf-8"?>\n')
        root.writexml(out)


def get_text_element(root: minidom.Element, tag: str) -> str:
    (node,) = root.getElementsByTagName(tag)
    (node,) = node.childNodes
    assert node.nodeType == root.TEXT_NODE
    assert isinstance(node.data, str)
    return node.data


def set_text_element(root: minidom.Element, tag: str, new_text: str) -> None:
    (node,) = root.getElementsByTagName(tag)
    (node,) = node.childNodes
    assert node.nodeType == root.TEXT_NODE
    node.data = new_text


def patch_python_for_licenses():
    # Python already handles bzip2 and libffi itself.
    notice_files = [
        TOP / 'external/zlib/LICENSE',
        TOP / 'toolchain/xz/COPYING',
    ]

    xml_path = PYTHON_SRC / 'PCbuild/regen.targets'
    proj = read_xml_file(xml_path)

    # Pick the unconditional <_LicenseSources> element and add extra notices to the end.
    elements = proj.getElementsByTagName('_LicenseSources')
    (element,) = [e for e in elements if not e.hasAttribute('Condition')]
    includes = element.getAttribute('Include').split(';')
    for notice in notice_files:
        if str(notice) not in includes:
            includes.append(str(notice))
    element.setAttribute('Include', ';'.join(includes))

    write_xml_file(proj, xml_path)


def remove_modules_from_pcbuild_proj():
    modules_to_remove = [
        '_sqlite3',
    ]

    xml_path = PYTHON_SRC / 'PCbuild/pcbuild.proj'
    proj = read_xml_file(xml_path)
    for tag in ('ExternalModules', 'ExtensionModules'):
        for element in proj.getElementsByTagName(tag):
            deps = element.getAttribute('Include').split(';')
            for unwanted in modules_to_remove:
                if unwanted in deps:
                    deps.remove(unwanted)
                    element.setAttribute('Include', ';'.join(deps))
    write_xml_file(proj, xml_path)


def build_using_cmake(out: Path, src: Path) -> None:
    create_new_dir(out)
    run_cmd([CMAKE_BIN, src, '-G', VS_GENERATOR, '-A', 'x64'], cwd=out)
    run_cmd([CMAKE_BIN, '--build', '.', '--config', 'Release'], cwd=out)


def build_using_cmake_and_install(install_dir: Path, build_dir: Path, src: Path) -> None:
    create_new_dir(build_dir)
    run_cmd([CMAKE_BIN, src, '-G', VS_GENERATOR, '-A', 'x64', f'-DCMAKE_INSTALL_PREFIX={install_dir}'], cwd=build_dir)
    run_cmd([CMAKE_BIN, '--build', '.', '--config', 'Release', '--target', 'install'], cwd=build_dir)


def patch_libffi_props() -> None:
    """The libffi.props file uses libffi-N.lib and libffi-N.dll. (At time of writing, N is 7, but
    upstream Python uses 8 instead.) The CMake-based build of libffi produces unsuffixed files, so
    fix libffi.props to match.
    """
    path = PYTHON_SRC / 'PCbuild/libffi.props'
    content = path.read_text(encoding='utf8')
    content = re.sub(r'libffi-\d+\.lib', 'libffi.lib', content)
    content = re.sub(r'libffi-\d+\.dll', 'libffi.dll', content)
    path.write_text(content, encoding='utf8')


def patch_pythoncore_for_zlib() -> None:
    """pythoncore.vcxproj builds zlib into itself by listing individual zlib C files. AOSP uses
    Chromium's zlib fork, which has a different set of C files and defines. Switch to AOSP zlib:
     - Build a static library using CMake: zlibstatic.lib, zconf.h, zlib.h
     - Strip ClCompile/ClInclude elements from the project file that point to $(zlibDir).
     - Add a dependency on the static library.
    """

    xml_path = PYTHON_SRC / 'PCbuild/pythoncore.vcxproj'
    proj = read_xml_file(xml_path)

    # Strip ClCompile/ClInclude that point into the zlib directory.
    for tag in ('ClCompile', 'ClInclude'):
        for element in proj.getElementsByTagName(tag):
            if element.getAttribute('Include').startswith('$(zlibDir)'):
                element.parentNode.removeChild(element)

    # Add a dependency on the static zlib archive.
    deps = get_text_element(proj, 'AdditionalDependencies').split(';')
    libz_path = str(TOP / 'out/zlib-install/lib/zlibstatic.lib')
    if libz_path not in deps:
        deps.insert(0, libz_path)
        set_text_element(proj, 'AdditionalDependencies', ';'.join(deps))

    write_xml_file(proj, xml_path)


def main() -> None:
    # Point the Python MSBuild project at the paths where repo/Kokoro would put the various
    # dependencies. The existing python.props uses trailing slashes in the path, and some (but not
    # all) uses of these variables expect the trailing slash.
    xml_path = PYTHON_SRC / 'PCbuild/python.props'
    root = read_xml_file(xml_path)
    set_text_element(root, 'bz2Dir', str(TOP / 'external/bzip2') + '\\')
    set_text_element(root, 'libffiDir', str(TOP / 'external/libffi') + '\\') # Provides LICENSE
    set_text_element(root, 'libffiIncludeDir', str(TOP / 'out/libffi/dist/include') + '\\') # headers
    set_text_element(root, 'libffiOutDir', str(TOP / 'out/libffi/Release') + '\\') # dll+lib
    set_text_element(root, 'lzmaDir', str(TOP / 'toolchain/xz') + '\\')
    set_text_element(root, 'zlibDir', str(TOP / 'out/zlib-install/include') + '\\')
    write_xml_file(root, xml_path)

    # liblzma.vcxproj adds $(lzmaDir)windows to the include path for config.h, but AOSP has a newer
    # version of xz that moves config.h into a subdir like vs2017 or vs2019. See this upstream
    # commit [1]. Copy the file into the place Python currently expects. (This can go away if Python
    # updates its xz dependency.)
    #
    # [1] https://git.tukaani.org/?p=xz.git;a=commit;h=82388980187b0e3794d187762054200bbdcc9a53
    #
    # Use vs2019 because there is no vs2022 header currently. (The existing vs2013, vs2017, and
    # vs2019 headers are all the same anyway.)
    xz = TOP / 'toolchain/xz'
    shutil.copy2(xz / 'windows/vs2019/config.h',
                 xz / 'windows/config.h')

    patch_python_for_licenses()
    remove_modules_from_pcbuild_proj()
    build_using_cmake(TOP / 'out/libffi', TOP / 'external/libffi')
    build_using_cmake_and_install(TOP / 'out/zlib-install', TOP / 'out/zlib-build', TOP / 'external/zlib')
    patch_libffi_props()
    patch_pythoncore_for_zlib()


if __name__ == '__main__':
    main()
