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

import os
import shutil
import subprocess
import sys
from distutils import cmd
from distutils import log

import setuptools
from setuptools.command.develop import develop
from setuptools.command.install import install

FRAMEWORK_DIR = 'acts_framework'
LOCAL_FRAMEWORK_DIR = '../acts/framework'

acts_tests_dir = os.path.abspath(os.path.dirname(__file__))

install_requires = [
    # Require an older version of setuptools that does not enforce PEP 440.
    # This must be added first.
    'setuptools<66.0.0',
    'soundfile'
]

if sys.version_info < (3, 6):
    # Python <= 3.5 uses bokeh up to 1.4.x
    install_requires.append('bokeh<1.5')
elif sys.version_info < (3, 7):
    # Python 3.6 uses bokeh up to 2.3.x
    install_requires.append('bokeh<2.4')
elif sys.version_info < (3, 8):
    # Python 3.7+ uses bokeh up to 2.4.x
    install_requires.append('bokeh<2.5')
else:
    # Python 3.8+ is support by latest bokeh
    install_requires.append('bokeh')

def _setup_acts_framework(option, *args):
    """Locates and runs setup.py for the ACTS framework.

    Args:
        option: the option to use with setup.py, e.g. install, develop
        args: additional args for the command
    """
    acts_framework_dir = os.path.join(acts_tests_dir, FRAMEWORK_DIR)
    if not os.path.isdir(acts_framework_dir):
        log.info('Directory "%s" not found. Attempting to locate ACTS '
                 'framework through local repository.' % acts_framework_dir)
        acts_framework_dir = os.path.join(acts_tests_dir, LOCAL_FRAMEWORK_DIR)
        if not os.path.isdir(acts_framework_dir):
            log.error('Cannot install ACTS framework. Framework dir "%s" not '
                      'found.' % acts_framework_dir)
            exit(1)
    acts_setup_bin = os.path.join(acts_framework_dir, 'setup.py')
    if not os.path.isfile(acts_setup_bin):
        log.error('Cannot install ACTS framework. Setup script not found.')
        exit(1)
    command = [sys.executable, acts_setup_bin, option, *args]
    subprocess.check_call(command, cwd=acts_framework_dir)


class ActsContribInstall(install):
    """Custom installation of the acts_contrib package.

    Also installs the required ACTS framework via its own setup.py script.

    The installation requires the ACTS framework to exist under the
    "acts_framework" directory, at the same level of this setup script.
    Otherwise, it will attempt to locate the ACTS framework from the local
    repository.
    """
    def do_egg_install(self):
        # Ref. https://stackoverflow.com/a/20196065
        _setup_acts_framework('install')
        install.do_egg_install(self)


class ActsContribDevelop(develop):
    """Custom installation of the acts_contrib package (in develop mode).

    See ActsContribInstall for more details.
    """

    def run(self):
        super().run()
        if self.uninstall:
            _setup_acts_framework('develop', '-u')
        else:
            _setup_acts_framework('develop')


class ActsContribUninstall(cmd.Command):
    """acts_contrib uninstaller.

    Uninstalls acts_contrib from the current version of python. This will
    attempt to import acts_contrib from any of the python egg locations. If it
    finds an import it will use the modules file location to delete it. This is
    repeated until acts_contrib can no longer be imported and thus is
    uninstalled.
    """

    description = 'Uninstall acts_contrib from the local machine.'
    user_options = []

    def initialize_options(self):
        pass

    def finalize_options(self):
        pass

    def uninstall_acts_contrib_module(self, acts_contrib_module):
        """Uninstalls acts_contrib from a module.

        Args:
            acts_contrib_module: The acts_contrib module to uninstall.
        """
        for acts_contrib_install_dir in acts_contrib_module.__path__:
            self.announce(
                'Deleting acts_contrib from: %s' % acts_contrib_install_dir,
                log.INFO)
            shutil.rmtree(acts_contrib_install_dir)

    def run(self):
        """Entry point for the uninstaller."""
        # Remove the working directory from the python path. This ensures that
        # Source code is not accidentally targeted.
        our_dir = os.path.abspath(os.path.dirname(__file__))
        if our_dir in sys.path:
            sys.path.remove(our_dir)

        if os.getcwd() in sys.path:
            sys.path.remove(os.getcwd())

        try:
            import acts_contrib as acts_contrib_module
        except ImportError:
            self.announce(
                'acts_contrib is not installed, nothing to uninstall.',
                level=log.ERROR)
            return

        while acts_contrib_module:
            self.uninstall_acts_contrib_module(acts_contrib_module)
            try:
                del sys.modules['acts_contrib']
                import acts_contrib as acts_contrib_module
            except ImportError:
                acts_contrib_module = None

        self.announce('Finished uninstalling acts_contrib.')

        # Uninstall the ACTS framework
        _setup_acts_framework('uninstall')


def main():
    os.chdir(acts_tests_dir)
    packages = setuptools.find_packages(include=('acts_contrib*', ))
    setuptools.setup(name='acts_contrib',
                     version='0.9',
                     description='Android Comms Test Suite',
                     license='Apache2.0',
                     packages=packages,
                     include_package_data=True,
                     install_requires=install_requires,
                     cmdclass={
                         'install': ActsContribInstall,
                         'develop': ActsContribDevelop,
                         'uninstall': ActsContribUninstall
                     },
                     url="http://www.android.com/")


if __name__ == '__main__':
    main()
