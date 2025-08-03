#!/usr/bin/env python3

import argparse
import os
import subprocess
import sys
import time

SRC_MOUNT = "/root/src"
STAGING_MOUNT = "/root/.floss"


class FlossContainerRunner:
    """Runs Floss build inside container."""

    # Commands to run for build
    BUILD_COMMANDS = [
        # First run bootstrap to get latest code + create symlinks
        [f'{SRC_MOUNT}/build.py', '--run-bootstrap', '--partial-staging'],

        # Clean up any previous artifacts inside the volume
        [f'{SRC_MOUNT}/build.py', '--target', 'clean'],

        # Run normal code builder
        [f'{SRC_MOUNT}/build.py', '--target', 'all'],

        # Run tests
        [f'{SRC_MOUNT}/build.py', '--target', 'test'],
    ]

    def __init__(self, workdir, rootdir, image_tag, volume_name, container_name, staging_dir, use_docker,
                 use_pseudo_tty):
        """ Constructor.

        Args:
            workdir: Current working directory (should be the script path).
            rootdir: Root directory for Bluetooth.
            image_tag: Tag for container image used for building.
            volume_name: Volume name used for storing artifacts.
            container_name: Name for running container instance.
            staging_dir: Directory to mount for artifacts instead of using volume.
            use_docker: Use docker binary if True (or podman when False).
            use_pseudo_tty: Run container with pseudo tty if true.
        """
        self.workdir = workdir
        self.rootdir = rootdir
        self.image_tag = image_tag
        self.container_binary = 'docker' if use_docker else 'podman'
        self.env = os.environ.copy()

        # Flags used by container exec:
        # -i: interactive mode keeps STDIN open even if not attached
        # -t: Allocate a pseudo-TTY (better user experience)
        #     Only set if use_pseudo_tty is true.
        self.container_exec_flags = '-it' if use_pseudo_tty else '-i'

        # Name of running container
        self.container_name = container_name

        # Name of volume to write output.
        self.volume_name = volume_name
        # Staging dir where we send output instead of the volume.
        self.staging_dir = staging_dir

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

    def _create_volume_if_needed(self):
        # Check if the volume exists. Otherwise create it.
        try:
            subprocess.check_output([self.container_binary, 'volume', 'inspect', self.volume_name])
        except:
            self.run_command(self.container_binary + ' volume create',
                             [self.container_binary, 'volume', 'create', self.volume_name])

    def start_container(self):
        """Starts the container with correct mounts."""
        # Stop any previously started container.
        self.stop_container(ignore_error=True)

        # Create volume and create mount string
        if self.staging_dir:
            mount_output_volume = 'type=bind,src={},dst={}'.format(self.staging_dir, STAGING_MOUNT)
        else:
            # If not using staging dir, use the volume instead
            self._create_volume_if_needed()
            mount_output_volume = 'type=volume,src={},dst={}'.format(self.volume_name, STAGING_MOUNT)

        # Mount the source directory
        mount_src_dir = 'type=bind,src={},dst={}'.format(self.rootdir, SRC_MOUNT)

        # Run the container image. It will run `tail` indefinitely so the container
        # doesn't close and we can run `<container_binary> exec` on it.
        self.run_command(self.container_binary + ' run', [
            self.container_binary, 'run', '--name', self.container_name, '--mount', mount_output_volume, '--mount',
            mount_src_dir, '-d', self.image_tag, 'tail', '-f', '/dev/null'
        ])

    def stop_container(self, ignore_error=False):
        """Stops the container for build."""
        self.run_command(self.container_binary + ' stop',
                         [self.container_binary, 'stop', '-t', '1', self.container_name],
                         ignore_rc=ignore_error)
        self.run_command(self.container_binary + ' rm', [self.container_binary, 'rm', self.container_name],
                         ignore_rc=ignore_error)

    def do_build(self):
        """Runs the basic build commands."""
        # Start container before building
        self.start_container()

        try:
            # Run all commands
            for i, cmd in enumerate(self.BUILD_COMMANDS):
                self.run_command(self.container_binary + ' exec #{}'.format(i),
                                 [self.container_binary, 'exec', self.container_exec_flags, self.container_name] + cmd)
        finally:
            # Always stop container before exiting
            self.stop_container()

    def print_do_build(self):
        """Prints the commands for building."""
        container_exec = [self.container_binary, 'exec', self.container_exec_flags, self.container_name]
        print('Normally, build would run the following commands: \n')
        for cmd in self.BUILD_COMMANDS:
            print(' '.join(container_exec + cmd))

    def check_container_runnable(self):
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


if __name__ == "__main__":
    parser = argparse.ArgumentParser('Builder Floss inside container image.')
    parser.add_argument('--only-start',
                        action='store_true',
                        default=False,
                        help='Only start the container. Prints the commands it would have ran.')
    parser.add_argument('--only-stop', action='store_true', default=False, help='Only stop the container and exit.')
    parser.add_argument('--image-tag', default='floss:latest', help='Container image to use to build.')
    parser.add_argument('--volume-tag',
                        default='floss-out',
                        help='Name of volume to use. This is where build artifacts will be stored by default.')
    parser.add_argument('--staging-dir',
                        default=None,
                        help='Staging directory to use instead of volume. Build artifacts will be written here.')
    parser.add_argument('--container-name',
                        default='floss-container-runner',
                        help='What to name the started container.')
    parser.add_argument('--use-docker',
                        action='store_true',
                        default=False,
                        help='Use flag to use Docker to build Floss. Defaults to using podman.')
    parser.add_argument('--no-tty',
                        action='store_true',
                        default=False,
                        help='Use flag to disable pseudo tty for docker container.')
    args = parser.parse_args()

    # cwd should be set to same directory as this script (that's where
    # Dockerfile is kept).
    workdir = os.path.dirname(os.path.abspath(sys.argv[0]))
    rootdir = os.path.abspath(os.path.join(workdir, '../..'))

    # Determine staging directory absolute path
    staging = os.path.abspath(args.staging_dir) if args.staging_dir else None

    fdr = FlossContainerRunner(workdir, rootdir, args.image_tag, args.volume_tag, args.container_name, staging,
                               args.use_docker, not args.no_tty)

    # Make sure container is runnable before continuing
    if fdr.check_container_runnable():
        # Handle some flags
        if args.only_start:
            fdr.start_container()
            fdr.print_do_build()
        elif args.only_stop:
            fdr.stop_container()
        else:
            fdr.do_build()
