#!/usr/bin/env python3
#
# Copyright 2017 - The Android Open Source Project
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
from distutils import cmd
from distutils import log
import subprocess
import os
import shutil
import setuptools
from setuptools.command import test
import sys

install_requires = [
    # Require an older version of setuptools that does not enforce PEP 440.
    # This must be added first.
    'setuptools<66.0.0',
    # Future needs to have a newer version that contains urllib.
    'future>=0.16.0',
    'mobly==1.12.0',
    # Latest version of mock (4.0.0b) causes a number of compatibility issues with ACTS unit tests
    # b/148695846, b/148814743
    'mock==3.0.5',
    'Monsoon',
    'paramiko[ed25519]',
    'pylibftdi',
    'pyserial',
    'pyyaml>=5.1',
    'requests',
    'retry',
    'scapy',
    'scp',
    'usbinfo',
    'zeroconf'
]

versioned_deps = {
    'backoff': 'backoff',
    'numpy': 'numpy',
    'scipy': 'scipy',
    'protobuf': 'protobuf==4.21.5',
    'grpcio': 'grpcio',
}

# numpy and scipy version matrix per:
# https://docs.scipy.org/doc/scipy/dev/toolchain.html
if sys.version_info < (3, 9):
    versioned_deps['scipy'] = 'scipy<1.11'
if sys.version_info < (3, 8):
    versioned_deps['numpy'] = 'numpy<1.22'
    versioned_deps['scipy'] = 'scipy<1.8'
if sys.version_info < (3, 7):
    versioned_deps['backoff'] = 'backoff<2.0'
    versioned_deps['numpy'] = 'numpy<1.20'
    versioned_deps['scipy'] = 'scipy<1.6'
    versioned_deps['protobuf'] = 'protobuf==3.20.1'
    versioned_deps['grpcio'] = 'grpcio==1.48.2'
    versioned_deps['typing_extensions'] = 'typing_extensions==4.1.1'
    versioned_deps['cryptography'] = 'cryptography<41.0'
if (sys.version_info.major, sys.version_info.minor) == (3, 6):
    versioned_deps['dataclasses'] = 'dataclasses==0.8'
if sys.version_info < (3, 6):
    versioned_deps['numpy'] = 'numpy<1.19'
    versioned_deps['scipy'] = 'scipy<1.5'
    versioned_deps['typing_extensions'] = 'typing_extensions<4.0.0'

install_requires += list(versioned_deps.values())

if sys.version_info < (3, ):
    install_requires.append('enum34')
    install_requires.append('statistics')
    # "futures" is needed for py2 compatibility and it only works in 2.7
    install_requires.append('futures')
    install_requires.append('py2-ipaddress')
    install_requires.append('subprocess32')

DEV_PACKAGES = ['shiv']

framework_dir = os.path.dirname(os.path.realpath(__file__))


class PyTest(test.test):
    """Class used to execute unit tests using PyTest. This allows us to execute
    unit tests without having to install the package.
    """

    def finalize_options(self):
        test.test.finalize_options(self)
        self.test_args = ['-x', "tests"]
        self.test_suite = True

    def run_tests(self):
        test_path = os.path.join(framework_dir,
                                 '../tests/meta/ActsUnitTest.py')
        result = subprocess.Popen('python3 %s' % test_path,
                                  stdout=sys.stdout,
                                  stderr=sys.stderr,
                                  shell=True)
        result.communicate()
        sys.exit(result.returncode)


class ActsUninstall(cmd.Command):
    """Acts uninstaller.

    Uninstalls acts from the current version of python. This will attempt to
    import acts from any of the python egg locations. If it finds an import
    it will use the modules file location to delete it. This is repeated until
    acts can no longer be imported and thus is uninstalled.
    """

    description = 'Uninstall acts from the local machine.'
    user_options = []

    def initialize_options(self):
        pass

    def finalize_options(self):
        pass

    def uninstall_acts_module(self, acts_module):
        """Uninstalls acts from a module.

        Args:
            acts_module: The acts module to uninstall.
        """
        for acts_install_dir in acts_module.__path__:
            self.announce('Deleting acts from: %s' % acts_install_dir,
                          log.INFO)
            shutil.rmtree(acts_install_dir)

    def run(self):
        """Entry point for the uninstaller."""
        # Remove the working directory from the python path. This ensures that
        # Source code is not accidentally targeted.
        if framework_dir in sys.path:
            sys.path.remove(framework_dir)

        if os.getcwd() in sys.path:
            sys.path.remove(os.getcwd())

        try:
            import acts as acts_module
        except ImportError:
            self.announce('Acts is not installed, nothing to uninstall.',
                          level=log.ERROR)
            return

        while acts_module:
            self.uninstall_acts_module(acts_module)
            try:
                del sys.modules['acts']
                import acts as acts_module
            except ImportError:
                acts_module = None

        self.announce('Finished uninstalling acts.')


def main():
    scripts = [
        os.path.join('acts', 'bin', 'act.py'),
        os.path.join('acts', 'bin', 'monsoon.py')
    ]
    # cd to framework directory so the correct package namespace is found
    os.chdir(framework_dir)
    setuptools.setup(name='acts',
                     version='0.9',
                     description='Android Comms Test Suite',
                     license='Apache2.0',
                     packages=setuptools.find_packages(),
                     include_package_data=True,
                     tests_require=['pytest'],
                     install_requires=install_requires,
                     extras_require={
                         'dev': DEV_PACKAGES,
                         'digital_loggers_pdu': ['dlipower'],
                     },
                     scripts=scripts,
                     cmdclass={
                         'test': PyTest,
                         'uninstall': ActsUninstall
                     },
                     url="http://www.android.com/")

    if {'-u', '--uninstall', 'uninstall'}.intersection(sys.argv):
        installed_scripts = [
            '/usr/local/bin/act.py', '/usr/local/bin/monsoon.py'
        ]
        for act_file in installed_scripts:
            if os.path.islink(act_file):
                os.unlink(act_file)
            elif os.path.exists(act_file):
                os.remove(act_file)


if __name__ == '__main__':
    main()
