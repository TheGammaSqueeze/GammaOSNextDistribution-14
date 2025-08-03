#!/usr/bin/env python3

#  Copyright 2021 Google, Inc.
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at:
#
#  http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
""" Build BT targets on the host system.

For building, you will first have to stage a platform directory that has the
following structure:
|-common-mk
|-bt
|-external
|-|-rust
|-|-|-vendor

The simplest way to do this is to check out platform2 to another directory (that
is not a subdir of this bt directory), symlink bt there and symlink the rust
vendor repository as well.
"""
import argparse
import multiprocessing
import os
import platform
import shutil
import six
import subprocess
import sys
import tarfile
import time

# Use flags required by common-mk (find -type f | grep -nE 'use[.]' {})
COMMON_MK_USES = [
    'asan',
    'coverage',
    'cros_host',
    'cros_debug',
    'floss_rootcanal',
    'function_elimination_experiment',
    'fuzzer',
    'fuzzer',
    'lto_experiment',
    'msan',
    'profiling',
    'proto_force_optimize_speed',
    'tcmalloc',
    'test',
    'ubsan',
]

# Use a specific commit version for common-mk to avoid build surprises.
COMMON_MK_COMMIT = "d014d561eaf5ece08166edd98b10c145ef81312d"

# Default use flags.
USE_DEFAULTS = {
    'android': False,
    'bt_nonstandard_codecs': False,
    'test': False,
}

VALID_TARGETS = [
    'all',  # All targets except test and clean
    'clean',  # Clean up output directory
    'docs',  # Build Rust docs
    'hosttools',  # Build the host tools (i.e. packetgen)
    'main',  # Build the main C++ codebase
    'prepare',  # Prepare the output directory (gn gen + rust setup)
    'rust',  # Build only the rust components + copy artifacts to output dir
    'test',  # Run the unit tests
    'utils',  # Build Floss utils
]

# TODO(b/190750167) - Host tests are disabled until we are full bazel build
HOST_TESTS = [
    # 'bluetooth_test_common',
    # 'bluetoothtbd_test',
    # 'net_test_avrcp',
    # 'net_test_btcore',
    # 'net_test_types',
    # 'net_test_btm_iso',
    # 'net_test_btpackets',
]

# Map of git repos to bootstrap and what commit to check them out at. None
# values will just checkout to HEAD.
BOOTSTRAP_GIT_REPOS = {
    'platform2': ('https://chromium.googlesource.com/chromiumos/platform2', COMMON_MK_COMMIT),
    'rust_crates': ('https://chromium.googlesource.com/chromiumos/third_party/rust_crates', None),
    'proto_logging': ('https://android.googlesource.com/platform/frameworks/proto_logging', None),
}

# List of packages required for linux build
REQUIRED_APT_PACKAGES = [
    'bison',
    'build-essential',
    'curl',
    'debmake',
    'flatbuffers-compiler',
    'flex',
    'g++-multilib',
    'gcc-multilib',
    'generate-ninja',
    'gnupg',
    'gperf',
    'libabsl-dev',
    'libc++abi-dev',
    'libc++-dev',
    'libdbus-1-dev',
    'libdouble-conversion-dev',
    'libevent-dev',
    'libevent-dev',
    'libflatbuffers-dev',
    'libgl1-mesa-dev',
    'libglib2.0-dev',
    'libgtest-dev',
    'libgmock-dev',
    'liblc3-dev',
    'liblz4-tool',
    'libncurses5',
    'libnss3-dev',
    'libfmt-dev',
    'libprotobuf-dev',
    'libre2-9',
    'libre2-dev',
    'libssl-dev',
    'libtinyxml2-dev',
    'libx11-dev',
    'libxml2-utils',
    'ninja-build',
    'openssl',
    'protobuf-compiler',
    'unzip',
    'x11proto-core-dev',
    'xsltproc',
    'zip',
    'zlib1g-dev',
]

# List of cargo packages required for linux build
REQUIRED_CARGO_PACKAGES = ['cxxbridge-cmd', 'pdl-compiler']

APT_PKG_LIST = ['apt', '-qq', 'list']
CARGO_PKG_LIST = ['cargo', 'install', '--list']


class UseFlags():

    def __init__(self, use_flags):
        """ Construct the use flags.

        Args:
            use_flags: List of use flags parsed from the command.
        """
        self.flags = {}

        # Import use flags required by common-mk
        for use in COMMON_MK_USES:
            self.set_flag(use, False)

        # Set our defaults
        for use, value in USE_DEFAULTS.items():
            self.set_flag(use, value)

        # Set use flags - value is set to True unless the use starts with -
        # All given use flags always override the defaults
        for use in use_flags:
            value = not use.startswith('-')
            self.set_flag(use, value)

    def set_flag(self, key, value=True):
        setattr(self, key, value)
        self.flags[key] = value


class HostBuild():

    def __init__(self, args):
        """ Construct the builder.

        Args:
            args: Parsed arguments from ArgumentParser
        """
        self.args = args

        # Set jobs to number of cpus unless explicitly set
        self.jobs = self.args.jobs
        if not self.jobs:
            self.jobs = multiprocessing.cpu_count()
            sys.stderr.write("Number of jobs = {}\n".format(self.jobs))

        # Normalize bootstrap dir and make sure it exists
        self.bootstrap_dir = os.path.abspath(self.args.bootstrap_dir)
        os.makedirs(self.bootstrap_dir, exist_ok=True)

        # Output and platform directories are based on bootstrap
        self.output_dir = os.path.join(self.bootstrap_dir, 'output')
        self.platform_dir = os.path.join(self.bootstrap_dir, 'staging')
        self.bt_dir = os.path.join(self.platform_dir, 'bt')
        self.sysroot = self.args.sysroot
        self.libdir = self.args.libdir
        self.install_dir = os.path.join(self.output_dir, 'install')

        assert os.path.samefile(self.bt_dir,
                                os.path.dirname(__file__)), "Please rerun bootstrap for the current project!"

        # If default target isn't set, build everything
        self.target = 'all'
        if hasattr(self.args, 'target') and self.args.target:
            self.target = self.args.target

        target_use = self.args.use if self.args.use else []

        # Unless set, always build test code
        if not self.args.notest:
            target_use.append('test')

        self.use = UseFlags(target_use)

        # Validate platform directory
        assert os.path.isdir(self.platform_dir), 'Platform dir does not exist'
        assert os.path.isfile(os.path.join(self.platform_dir, '.gn')), 'Platform dir does not have .gn at root'

        # Make sure output directory exists (or create it)
        os.makedirs(self.output_dir, exist_ok=True)

        # Set some default attributes
        self.libbase_ver = None

        self.configure_environ()

    def _generate_rustflags(self):
        """ Rustflags to include for the build.
      """
        rust_flags = [
            '-L',
            '{}/out/Default'.format(self.output_dir),
            '-C',
            'link-arg=-Wl,--allow-multiple-definition',
            # exclude uninteresting warnings
            '-A improper_ctypes_definitions -A improper_ctypes -A unknown_lints',
        ]

        return ' '.join(rust_flags)

    def configure_environ(self):
        """ Configure environment variables for GN and Cargo.
        """
        self.env = os.environ.copy()

        # Make sure cargo home dir exists and has a bin directory
        cargo_home = os.path.join(self.output_dir, 'cargo_home')
        os.makedirs(cargo_home, exist_ok=True)
        os.makedirs(os.path.join(cargo_home, 'bin'), exist_ok=True)

        # Configure Rust env variables
        self.custom_env = {}
        self.custom_env['CARGO_TARGET_DIR'] = self.output_dir
        self.custom_env['CARGO_HOME'] = os.path.join(self.output_dir, 'cargo_home')
        self.custom_env['RUSTFLAGS'] = self._generate_rustflags()
        self.custom_env['CXX_ROOT_PATH'] = os.path.join(self.platform_dir, 'bt')
        self.custom_env['CROS_SYSTEM_API_ROOT'] = os.path.join(self.platform_dir, 'system_api')
        self.custom_env['CXX_OUTDIR'] = self._gn_default_output()

        # On ChromeOS, this is /usr/bin/grpc_rust_plugin
        # In the container, this is /root/.cargo/bin/grpc_rust_plugin
        self.custom_env['GRPC_RUST_PLUGIN_PATH'] = shutil.which('grpc_rust_plugin')
        self.env.update(self.custom_env)

    def print_env(self):
        """ Print the custom environment variables that are used in build.

        Useful so that external tools can mimic the environment to be the same
        as build.py, e.g. rust-analyzer.
        """
        for k, v in self.custom_env.items():
            print("export {}='{}'".format(k, v))

    def run_command(self, target, args, cwd=None, env=None):
        """ Run command and stream the output.
        """
        # Set some defaults
        if not cwd:
            cwd = self.platform_dir
        if not env:
            env = self.env

        log_file = os.path.join(self.output_dir, '{}.log'.format(target))
        with open(log_file, 'wb') as lf:
            rc = 0
            process = subprocess.Popen(args, cwd=cwd, env=env, stdout=subprocess.PIPE)
            while True:
                line = process.stdout.readline()
                print(line.decode('utf-8'), end="")
                lf.write(line)
                if not line:
                    rc = process.poll()
                    if rc is not None:
                        break

                    time.sleep(0.1)

            if rc != 0:
                raise Exception("Return code is {}".format(rc))

    def _get_basever(self):
        if self.libbase_ver:
            return self.libbase_ver

        self.libbase_ver = os.environ.get('BASE_VER', '')
        if not self.libbase_ver:
            base_file = os.path.join(self.sysroot, 'usr/share/libchrome/BASE_VER')
            try:
                with open(base_file, 'r') as f:
                    self.libbase_ver = f.read().strip('\n')
            except:
                self.libbase_ver = 'NOT-INSTALLED'

        return self.libbase_ver

    def _gn_default_output(self):
        return os.path.join(self.output_dir, 'out/Default')

    def _gn_configure(self):
        """ Configure all required parameters for platform2.

        Mostly copied from //common-mk/platform2.py
        """
        clang = not self.args.no_clang

        def to_gn_string(s):
            return '"%s"' % s.replace('"', '\\"')

        def to_gn_list(strs):
            return '[%s]' % ','.join([to_gn_string(s) for s in strs])

        def to_gn_args_args(gn_args):
            for k, v in gn_args.items():
                if isinstance(v, bool):
                    v = str(v).lower()
                elif isinstance(v, list):
                    v = to_gn_list(v)
                elif isinstance(v, six.string_types):
                    v = to_gn_string(v)
                else:
                    raise AssertionError('Unexpected %s, %r=%r' % (type(v), k, v))
                yield '%s=%s' % (k.replace('-', '_'), v)

        gn_args = {
            'platform_subdir': 'bt',
            'cc': 'clang' if clang else 'gcc',
            'cxx': 'clang++' if clang else 'g++',
            'ar': 'llvm-ar' if clang else 'ar',
            'pkg-config': 'pkg-config',
            'clang_cc': clang,
            'clang_cxx': clang,
            'OS': 'linux',
            'sysroot': self.sysroot,
            'libdir': os.path.join(self.sysroot, self.libdir),
            'build_root': self.output_dir,
            'platform2_root': self.platform_dir,
            'libbase_ver': self._get_basever(),
            'enable_exceptions': os.environ.get('CXXEXCEPTIONS', 0) == '1',
            'external_cflags': [],
            'external_cxxflags': ["-DNDEBUG"],
            'enable_werror': False,
        }

        if clang:
            # Make sure to mark the clang use flag as true
            self.use.set_flag('clang', True)
            gn_args['external_cxxflags'] += ['-I/usr/include/']

        gn_args_args = list(to_gn_args_args(gn_args))
        use_args = ['%s=%s' % (k, str(v).lower()) for k, v in self.use.flags.items()]
        gn_args_args += ['use={%s}' % (' '.join(use_args))]

        gn_args = [
            'gn',
            'gen',
        ]

        if self.args.verbose:
            gn_args.append('-v')

        gn_args += [
            '--root=%s' % self.platform_dir,
            '--args=%s' % ' '.join(gn_args_args),
            self._gn_default_output(),
        ]

        if 'PKG_CONFIG_PATH' in self.env:
            print('DEBUG: PKG_CONFIG_PATH is', self.env['PKG_CONFIG_PATH'])

        self.run_command('configure', gn_args)

    def _gn_build(self, target):
        """ Generate the ninja command for the target and run it.
        """
        args = ['%s:%s' % ('bt', target)]
        ninja_args = ['ninja', '-C', self._gn_default_output()]
        if self.jobs:
            ninja_args += ['-j', str(self.jobs)]
        ninja_args += args

        if self.args.verbose:
            ninja_args.append('-v')

        self.run_command('build', ninja_args)

    def _rust_configure(self):
        """ Generate config file at cargo_home so we use vendored crates.
        """
        template = """
        [source.systembt]
        directory = "{}/external/rust/vendor"

        [source.crates-io]
        replace-with = "systembt"
        local-registry = "/nonexistent"
        """

        if not self.args.no_vendored_rust:
            contents = template.format(self.platform_dir)
            with open(os.path.join(self.env['CARGO_HOME'], 'config'), 'w') as f:
                f.write(contents)

    def _rust_build(self):
        """ Run `cargo build` from platform2/bt directory.
        """
        cmd = ['cargo', 'build']
        if not self.args.rust_debug:
            cmd.append('--release')

        self.run_command('rust', cmd, cwd=os.path.join(self.platform_dir, 'bt'), env=self.env)

    def _target_prepare(self):
        """ Target to prepare the output directory for building.

        This runs gn gen to generate all rquired files and set up the Rust
        config properly. This will be run
        """
        self._gn_configure()
        self._rust_configure()

    def _target_hosttools(self):
        """ Build the tools target in an already prepared environment.
        """
        self._gn_build('tools')

        # Also copy bluetooth_packetgen to CARGO_HOME so it's available
        shutil.copy(os.path.join(self._gn_default_output(), 'bluetooth_packetgen'),
                    os.path.join(self.env['CARGO_HOME'], 'bin'))

    def _target_docs(self):
        """Build the Rust docs."""
        self.run_command('docs', ['cargo', 'doc'], cwd=os.path.join(self.platform_dir, 'bt'), env=self.env)

    def _target_rust(self):
        """ Build rust artifacts in an already prepared environment.
        """
        self._rust_build()

    def _target_main(self):
        """ Build the main GN artifacts in an already prepared environment.
        """
        self._gn_build('all')

    def _target_test(self):
        """ Runs the host tests.
        """
        # Rust tests first
        rust_test_cmd = ['cargo', 'test']
        if not self.args.rust_debug:
            rust_test_cmd.append('--release')

        if self.args.test_name:
            rust_test_cmd = rust_test_cmd + [self.args.test_name, "--", "--test-threads=1", "--nocapture"]

        self.run_command('test', rust_test_cmd, cwd=os.path.join(self.platform_dir, 'bt'), env=self.env)

        # Host tests second based on host test list
        for t in HOST_TESTS:
            self.run_command('test', [os.path.join(self.output_dir, 'out/Default', t)],
                             cwd=os.path.join(self.output_dir),
                             env=self.env)

    def _target_utils(self):
        """ Builds the utility applications.
        """
        rust_targets = ['hcidoc']

        # Build targets
        for target in rust_targets:
            self.run_command('utils', ['cargo', 'build', '-p', target],
                             cwd=os.path.join(self.platform_dir, 'bt'),
                             env=self.env)

    def _target_install(self):
        """ Installs files required to run Floss to install directory.
        """
        # First make the install directory
        prefix = self.install_dir
        os.makedirs(prefix, exist_ok=True)

        # Next save the cwd and change to install directory
        last_cwd = os.getcwd()
        os.chdir(prefix)

        bindir = os.path.join(self.output_dir, 'debug')
        srcdir = os.path.dirname(__file__)

        install_map = [
            {
                'src': os.path.join(bindir, 'btadapterd'),
                'dst': 'usr/libexec/bluetooth/btadapterd',
                'strip': True
            },
            {
                'src': os.path.join(bindir, 'btmanagerd'),
                'dst': 'usr/libexec/bluetooth/btmanagerd',
                'strip': True
            },
            {
                'src': os.path.join(bindir, 'btclient'),
                'dst': 'usr/local/bin/btclient',
                'strip': True
            },
        ]

        for v in install_map:
            src, partial_dst, strip = (v['src'], v['dst'], v['strip'])
            dst = os.path.join(prefix, partial_dst)

            # Create dst directory first and copy file there
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            print('Installing {}'.format(dst))
            shutil.copy(src, dst)

            # Binary should be marked for strip and no-strip option shouldn't be
            # set. No-strip is useful while debugging.
            if strip and not self.args.no_strip:
                self.run_command('install', ['llvm-strip', dst])

        # Put all files into a tar.gz for easier installation
        tar_location = os.path.join(prefix, 'floss.tar.gz')
        with tarfile.open(tar_location, 'w:gz') as tar:
            for v in install_map:
                tar.add(v['dst'])

        print('Tarball created at {}'.format(tar_location))

    def _target_clean(self):
        """ Delete the output directory entirely.
        """
        shutil.rmtree(self.output_dir)

        # Remove Cargo.lock that may have become generated
        cargo_lock_files = [
            os.path.join(self.platform_dir, 'bt', 'Cargo.lock'),
        ]
        for lock_file in cargo_lock_files:
            try:
                os.remove(lock_file)
                print('Removed {}'.format(lock_file))
            except FileNotFoundError:
                pass

    def _target_all(self):
        """ Build all common targets (skipping doc, test, and clean).
        """
        self._target_prepare()
        self._target_hosttools()
        self._target_main()
        self._target_rust()

    def build(self):
        """ Builds according to self.target
        """
        print('Building target ', self.target)

        # Validate that the target is valid
        if self.target not in VALID_TARGETS:
            print('Target {} is not valid. Must be in {}'.format(self.target, VALID_TARGETS))
            return

        if self.target == 'prepare':
            self._target_prepare()
        elif self.target == 'hosttools':
            self._target_hosttools()
        elif self.target == 'rust':
            self._target_rust()
        elif self.target == 'docs':
            self._target_docs()
        elif self.target == 'main':
            self._target_main()
        elif self.target == 'test':
            self._target_test()
        elif self.target == 'clean':
            self._target_clean()
        elif self.target == 'install':
            self._target_install()
        elif self.target == 'utils':
            self._target_utils()
        elif self.target == 'all':
            self._target_all()


class Bootstrap():

    def __init__(self, base_dir, bt_dir, partial_staging):
        """ Construct bootstrapper.

        Args:
            base_dir: Where to stage everything.
            bt_dir: Where bluetooth source is kept (will be symlinked)
            partial_staging: Whether to do a partial clone for staging.
        """
        self.base_dir = os.path.abspath(base_dir)
        self.bt_dir = os.path.abspath(bt_dir)
        self.partial_staging = partial_staging

        # Create base directory if it doesn't already exist
        os.makedirs(self.base_dir, exist_ok=True)

        if not os.path.isdir(self.bt_dir):
            raise Exception('{} is not a valid directory'.format(self.bt_dir))

        self.git_dir = os.path.join(self.base_dir, 'repos')
        self.staging_dir = os.path.join(self.base_dir, 'staging')
        self.output_dir = os.path.join(self.base_dir, 'output')
        self.external_dir = os.path.join(self.base_dir, 'staging', 'external')

        self.dir_setup_complete = os.path.join(self.base_dir, '.setup-complete')

    def _update_platform2(self):
        """Updates repositories used for build."""
        for project in BOOTSTRAP_GIT_REPOS.keys():
            cwd = os.path.join(self.git_dir, project)
            (repo, commit) = BOOTSTRAP_GIT_REPOS[project]

            # Update to required commit when necessary or pull the latest code.
            if commit:
                head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=cwd).strip()
                if head != commit:
                    subprocess.check_call(['git', 'fetch'], cwd=cwd)
                    subprocess.check_call(['git', 'checkout', commit], cwd=cwd)
            else:
                subprocess.check_call(['git', 'pull'], cwd=cwd)

    def _setup_platform2(self):
        """ Set up platform2.

        This will check out all the git repos and symlink everything correctly.
        """

        # Create all directories we will need to use
        for dirpath in [self.git_dir, self.staging_dir, self.output_dir, self.external_dir]:
            os.makedirs(dirpath, exist_ok=True)

        # If already set up, only update platform2
        if os.path.isfile(self.dir_setup_complete):
            print('{} already set-up. Updating instead.'.format(self.base_dir))
            self._update_platform2()
        else:
            clone_options = []
            # When doing a partial staging, we use a treeless clone which allows
            # us to access all commits but downloads things on demand. This
            # helps speed up the initial git clone during builds but isn't good
            # for long-term development.
            if self.partial_staging:
                clone_options = ['--filter=tree:0']
            # Check out all repos in git directory
            for project in BOOTSTRAP_GIT_REPOS.keys():
                (repo, commit) = BOOTSTRAP_GIT_REPOS[project]
                subprocess.check_call(['git', 'clone', repo, project] + clone_options, cwd=self.git_dir)
                # Pin to commit.
                if commit:
                    subprocess.check_call(['git', 'checkout', commit], cwd=os.path.join(self.git_dir, project))

        # Symlink things
        symlinks = [
            (os.path.join(self.git_dir, 'platform2', 'common-mk'), os.path.join(self.staging_dir, 'common-mk')),
            (os.path.join(self.git_dir, 'platform2', 'system_api'), os.path.join(self.staging_dir, 'system_api')),
            (os.path.join(self.git_dir, 'platform2', '.gn'), os.path.join(self.staging_dir, '.gn')),
            (os.path.join(self.bt_dir), os.path.join(self.staging_dir, 'bt')),
            (os.path.join(self.git_dir, 'rust_crates'), os.path.join(self.external_dir, 'rust')),
            (os.path.join(self.git_dir, 'proto_logging'), os.path.join(self.external_dir, 'proto_logging')),
        ]

        # Create symlinks
        for pairs in symlinks:
            (src, dst) = pairs
            try:
                os.unlink(dst)
            except Exception as e:
                print(e)
            os.symlink(src, dst)

        # Write to setup complete file so we don't repeat this step
        with open(self.dir_setup_complete, 'w') as f:
            f.write('Setup complete.')

    def _pretty_print_install(self, install_cmd, packages, line_limit=80):
        """ Pretty print an install command.

        Args:
            install_cmd: Prefixed install command.
            packages: Enumerate packages and append them to install command.
            line_limit: Number of characters per line.

        Return:
            Array of lines to join and print.
        """
        install = [install_cmd]
        line = '  '
        # Remainder needed = space + len(pkg) + space + \
        # Assuming 80 character lines, that's 80 - 3 = 77
        line_limit = line_limit - 3
        for pkg in packages:
            if len(line) + len(pkg) < line_limit:
                line = '{}{} '.format(line, pkg)
            else:
                install.append(line)
                line = '  {} '.format(pkg)

        if len(line) > 0:
            install.append(line)

        return install

    def _check_package_installed(self, package, cmd, predicate):
        """Check that the given package is installed.

        Args:
            package: Check that this package is installed.
            cmd: Command prefix to check if installed (package appended to end)
            predicate: Function/lambda to check if package is installed based
                       on output. Takes string output and returns boolean.

        Return:
            True if package is installed.
        """
        try:
            output = subprocess.check_output(cmd + [package], stderr=subprocess.STDOUT)
            is_installed = predicate(output.decode('utf-8'))
            print('  {} is {}'.format(package, 'installed' if is_installed else 'missing'))

            return is_installed
        except Exception as e:
            print(e)
            return False

    def _get_command_output(self, cmd):
        """Runs the command and gets the output.

        Args:
            cmd: Command to run.

        Return:
            Tuple (Success, Output). Success represents if the command ran ok.
        """
        try:
            output = subprocess.check_output(cmd, stderr=subprocess.STDOUT)
            return (True, output.decode('utf-8').split('\n'))
        except Exception as e:
            print(e)
            return (False, "")

    def _print_missing_packages(self):
        """Print any missing packages found via apt.

        This will find any missing packages necessary for build using apt and
        print it out as an apt-get install printf.
        """
        print('Checking for any missing packages...')

        (success, output) = self._get_command_output(APT_PKG_LIST)
        if not success:
            raise Exception("Could not query apt for packages.")

        packages_installed = {}
        for line in output:
            if 'installed' in line:
                split = line.split('/', 2)
                packages_installed[split[0]] = True

        need_packages = []
        for pkg in REQUIRED_APT_PACKAGES:
            if pkg not in packages_installed:
                need_packages.append(pkg)

        # No packages need to be installed
        if len(need_packages) == 0:
            print('+ All required packages are installed')
            return

        install = self._pretty_print_install('sudo apt-get install', need_packages)

        # Print all lines so they can be run in cmdline
        print('Missing system packages. Run the following command: ')
        print(' \\\n'.join(install))

    def _print_missing_rust_packages(self):
        """Print any missing packages found via cargo.

        This will find any missing packages necessary for build using cargo and
        print it out as a cargo-install printf.
        """
        print('Checking for any missing cargo packages...')

        (success, output) = self._get_command_output(CARGO_PKG_LIST)
        if not success:
            raise Exception("Could not query cargo for packages.")

        packages_installed = {}
        for line in output:
            # Cargo installed packages have this format
            # <crate name> <version>:
            #   <binary name>
            # We only care about the crates themselves
            if ':' not in line:
                continue

            split = line.split(' ', 2)
            packages_installed[split[0]] = True

        need_packages = []
        for pkg in REQUIRED_CARGO_PACKAGES:
            if pkg not in packages_installed:
                need_packages.append(pkg)

        # No packages to be installed
        if len(need_packages) == 0:
            print('+ All required cargo packages are installed')
            return

        install = self._pretty_print_install('cargo install', need_packages)
        print('Missing cargo packages. Run the following command: ')
        print(' \\\n'.join(install))

    def bootstrap(self):
        """ Bootstrap the Linux build."""
        self._setup_platform2()
        self._print_missing_packages()
        self._print_missing_rust_packages()


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Simple build for host.')
    parser.add_argument('--bootstrap-dir',
                        help='Directory to run bootstrap on (or was previously run on).',
                        default="~/.floss")
    parser.add_argument('--run-bootstrap',
                        help='Run bootstrap code to verify build env is ok to build.',
                        default=False,
                        action='store_true')
    parser.add_argument('--print-env',
                        help='Print environment variables used for build.',
                        default=False,
                        action='store_true')
    parser.add_argument('--no-clang', help='Don\'t use clang compiler.', default=False, action='store_true')
    parser.add_argument('--no-strip',
                        help='Skip stripping binaries during install.',
                        default=False,
                        action='store_true')
    parser.add_argument('--use', help='Set a specific use flag.')
    parser.add_argument('--notest', help='Don\'t compile test code.', default=False, action='store_true')
    parser.add_argument('--test-name', help='Run test with this string in the name.', default=None)
    parser.add_argument('--target', help='Run specific build target')
    parser.add_argument('--sysroot', help='Set a specific sysroot path', default='/')
    parser.add_argument('--libdir', help='Libdir - default = usr/lib', default='usr/lib')
    parser.add_argument('--jobs', help='Number of jobs to run', default=0, type=int)
    parser.add_argument('--no-vendored-rust',
                        help='Do not use vendored rust crates',
                        default=False,
                        action='store_true')
    parser.add_argument('--verbose', help='Verbose logs for build.')
    parser.add_argument('--rust-debug', help='Build Rust code as debug.', default=False, action='store_true')
    parser.add_argument(
        '--partial-staging',
        help='Bootstrap git repositories with partial clones. Use to speed up initial git clone for automated builds.',
        default=False,
        action='store_true')
    args = parser.parse_args()

    # Make sure we get absolute path + expanded path for bootstrap directory
    args.bootstrap_dir = os.path.abspath(os.path.expanduser(args.bootstrap_dir))

    # Possible values for machine() come from 'uname -m'
    # Since this script only runs on Linux, x86_64 machines must have this value
    if platform.machine() != 'x86_64':
        raise Exception("Only x86_64 machines are currently supported by this build script.")

    if args.run_bootstrap:
        bootstrap = Bootstrap(args.bootstrap_dir, os.path.dirname(__file__), args.partial_staging)
        bootstrap.bootstrap()
    elif args.print_env:
        build = HostBuild(args)
        build.print_env()
    else:
        build = HostBuild(args)
        build.build()
