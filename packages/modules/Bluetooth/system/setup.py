#!/usr/bin/env python3
#
#   Copyright 2020 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

from distutils import log
import os
from setuptools import find_packages
from setuptools import setup
from setuptools.command.install import install
import stat
import subprocess
import sys

reuse_libraries = False
force_install = False

install_requires = [
    'grpcio',
    'psutil',
    'protobuf>=3.14.0, <4.0',
    'mobly',
]

host_executables = [
    'root-canal',
    'bluetooth_stack_with_facade',  # c++
    'bluetooth_with_facades',  # rust
    'bt_topshim_facade',  # topshim
]


def set_permissions_for_host_executables(outputs):
    for file in outputs:
        if os.path.basename(file) in host_executables:
            current_mode = os.stat(file).st_mode
            new_mode = current_mode | stat.S_IEXEC
            os.chmod(file, new_mode)
            log.log(log.INFO, "Changed file mode of %s from %s to %s" % (file, oct(current_mode), oct(new_mode)))


class InstallLocalPackagesForInstallation(install):

    def run(self):
        global reuse_libraries, force_install
        install_args = [sys.executable, '-m', 'pip', 'install']
        subprocess.check_call(install_args + ['--upgrade', 'pip'])

        for package in install_requires:
            self.announce('Installing %s...' % package, log.INFO)
            cmd = install_args + ['-v', '--no-cache-dir', package]
            if force_install and not reuse_libraries:
                cmd.append("--force-reinstall")
            subprocess.check_call(cmd)
        self.announce('Dependencies installed.')

        install.run(self)
        set_permissions_for_host_executables(self.get_outputs())


def main():
    global reuse_libraries, force_install
    if sys.argv[-1] == "--reuse-libraries":
        reuse_libraries = True
        sys.argv = sys.argv[:-1]
    if "--force" in sys.argv:
        force_install = True
    # Relative path from calling directory to this file
    our_dir = os.path.dirname(__file__)
    # Must cd into this dir for package resolution to work
    # This won't affect the calling shell
    os.chdir(our_dir)
    setup(
        name='bluetooth_cert_tests',
        version='1.0',
        author='Android Open Source Project',
        license='Apache2.0',
        description="""Bluetooth Cert Tests Package""",
        # Include root package so that bluetooth_packets_python3.so can be
        # included as well
        packages=[''] + find_packages(exclude=['llvm_binutils', 'llvm_binutils.*']),
        install_requires=install_requires,
        package_data={
            '': host_executables + ['*.so', 'lib64/*.so', 'target/*', 'llvm_binutils/bin/*', 'llvm_binutils/lib64/*'],
        },
        cmdclass={
            'install': InstallLocalPackagesForInstallation,
        })


if __name__ == '__main__':
    main()
