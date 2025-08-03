#!/usr/bin/env python3

import argparse
import os
import sys
import subprocess
import time

SRC_MOUNT = "/root/src"


class ContainerImageBuilder:
    """Builds the container image for Floss build environment."""

    def __init__(self, workdir, rootdir, tag, use_docker):
        """ Constructor.

        Args:
            workdir: Working directory for this script. Containerfile should exist here.
            rootdir: Root directory for Bluetooth.
            tag: Label in format |name:version|.
            use_docker: Use docker binary if True (or podman when False).
        """
        self.workdir = workdir
        self.rootdir = rootdir
        (self.name, self.version) = tag.split(':')
        self.build_tag = '{}:{}'.format(self.name, 'buildtemp')
        self.container_name = 'floss-buildtemp'
        self.final_tag = tag
        self.container_binary = 'docker' if use_docker else 'podman'
        self.env = os.environ.copy()

        # Mark dpkg builders for container
        self.env['LIBCHROME_DOCKER'] = '1'
        self.env['MODP_DOCKER'] = '1'

    def run_command(self, target, args, cwd=None, env=None, ignore_rc=False):
        """ Run command and stream the output.
        """
        # Set some defaults
        if not cwd:
            cwd = self.workdir
        if not env:
            env = self.env

        rc = 0
        process = subprocess.Popen(args, cwd=cwd, env=env, stdout=subprocess.PIPE)
        while True:
            line = process.stdout.readline()
            print(line.decode('utf-8'), end="")
            if not line:
                rc = process.poll()
                if rc is not None:
                    break

                time.sleep(0.1)

        if rc != 0 and not ignore_rc:
            raise Exception("{} failed. Return code is {}".format(target, rc))

    def _container_build(self):
        self.run_command(self.container_binary + ' build', [self.container_binary, 'build', '-t', self.build_tag, '.'])

    def _build_dpkg_and_commit(self):
        # Try to remove any previous instance of the container that may be
        # running if this script didn't complete cleanly last time.
        self.run_command(self.container_binary + ' stop', [self.container_binary, 'stop', '-t', '1', self.container_name], ignore_rc=True)
        self.run_command(self.container_binary + ' rm', [self.container_binary, 'rm', self.container_name], ignore_rc=True)

        # Runs never terminating application on the newly built image in detached mode
        mount_str = 'type=bind,src={},dst={},readonly'.format(self.rootdir, SRC_MOUNT)
        self.run_command(self.container_binary + ' run', [
            self.container_binary, 'run', '--name', self.container_name, '--mount', mount_str, '-d', self.build_tag, 'tail', '-f',
            '/dev/null'
        ])

        commands = [
            # Create the output directories
            ['mkdir', '-p', '/tmp/libchrome', '/tmp/modpb64'],

            # Run the dpkg builder for modp_b64
            [f'{SRC_MOUNT}/system/build/dpkg/modp_b64/gen-src-pkg.sh', '/tmp/modpb64'],

            # Install modp_b64 since libchrome depends on it
            ['find', '/tmp/modpb64', '-name', 'modp*.deb', '-exec', 'dpkg', '-i', '{}', '+'],

            # Run the dpkg builder for libchrome
            [f'{SRC_MOUNT}/system/build/dpkg/libchrome/gen-src-pkg.sh', '/tmp/libchrome'],

            # Install libchrome.
            ['find', '/tmp/libchrome', '-name', 'libchrome_*.deb', '-exec', 'dpkg', '-i', '{}', '+'],

            # Delete intermediate files
            ['rm', '-rf', '/tmp/libchrome', '/tmp/modpb64'],
        ]

        try:
            # Run commands in container first to install everything.
            for i, cmd in enumerate(commands):
                self.run_command(self.container_binary + ' exec #{}'.format(i), [self.container_binary, 'exec', '-it', self.container_name] + cmd)

            # Commit changes into the final tag name
            self.run_command(self.container_binary + ' commit', [self.container_binary, 'commit', self.container_name, self.final_tag])
        finally:
            # Stop running the container and remove it
            self.run_command(self.container_binary + ' stop', [self.container_binary, 'stop', '-t', '1', self.container_name])
            self.run_command(self.container_binary + ' rm', [self.container_binary, 'rm', self.container_name])

    def _check_container_runnable(self):
        try:
            subprocess.check_output([self.container_binary, 'ps'], stderr=subprocess.STDOUT)
        except subprocess.CalledProcessError as err:
            if 'denied' in err.output.decode('utf-8'):
                print('Run script as sudo')
            else:
                print('Unexpected error: {}'.format(err.output.decode('utf-8')))

            return False

        # No exception means container is ok
        return True

    def build(self):
        if not self._check_container_runnable():
            return

        # First build the container image
        self._container_build()

        # Then build libchrome and modp-b64 inside the container image and
        # install them. Commit those changes to the final label.
        self._build_dpkg_and_commit()


def main():
    parser = argparse.ArgumentParser(description='Build container image for Floss build environment.')
    parser.add_argument('--tag', required=True, help='Tag for container image. i.e. floss:latest')
    parser.add_argument('--use-docker', action='store_true', default=False, help='Use flag to use Docker to build Floss. Defaults to using podman.')
    args = parser.parse_args()

    # cwd should be set to same directory as this script (that's where
    # Dockerfile is kept).
    workdir = os.path.dirname(os.path.abspath(sys.argv[0]))
    rootdir = os.path.abspath(os.path.join(workdir, '../..'))

    # Build the container image
    pib = ContainerImageBuilder(workdir, rootdir, args.tag, args.use_docker)
    pib.build()


if __name__ == '__main__':
    main()
