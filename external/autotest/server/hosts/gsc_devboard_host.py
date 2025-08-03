# Lint as: python2, python3
# Copyright (c) 2022 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
#
# Expects to be run in an environment with sudo and no interactive password
# prompt, such as within the Chromium OS development chroot.
"""Host class for GSC devboard connected host."""

import contextlib
import logging
try:
    import docker
except ImportError:
    logging.info("Docker API is not installed in this environment")

DOCKER_IMAGE = "gcr.io/satlab-images/gsc_dev_board:release"

SATLAB_DOCKER_HOST = 'tcp://192.168.231.1:2375'
LOCAL_DOCKER_HOST = 'tcp://127.0.0.1:2375'
DEFAULT_DOCKER_HOST = 'unix:///var/run/docker.sock'

DEFAULT_SERVICE_PORT = 39999

ULTRADEBUG = '18d1:0304'


class GSCDevboardHost(object):
    """
    A host that is physically connected to a GSC devboard.

    It could either be a SDK workstation (chroot) or a SatLab box.
    """

    def _initialize(self,
                    hostname,
                    service_debugger_serial=None,
                    service_ip=None,
                    service_port=DEFAULT_SERVICE_PORT,
                    *args,
                    **dargs):
        """Construct a GSCDevboardHost object.

        @hostname: Name of the devboard host, will be used in future to look up
                   the debugger serial, not currently used.
        @service_debugger_serial: debugger connected to devboard, defaults to
                                  the first one found on the container.
        @service_ip: devboard service ip, default is to start a new container.
        @service_port: devboard service port, defaults to 39999.
        """

        # Use docker host from environment or by probing a list of candidates.
        self._client = None
        try:
            self._client = docker.from_env()
            logging.info("Created docker host from env")
        except NameError:
            raise NameError('Please install docker using '
                            '"autotest/files/utils/install_docker_chroot.sh"')
        except docker.errors.DockerException:
            docker_host = None
            candidate_hosts = [
                    SATLAB_DOCKER_HOST, DEFAULT_DOCKER_HOST, LOCAL_DOCKER_HOST
            ]
            for h in candidate_hosts:
                try:
                    c = docker.DockerClient(base_url=h, timeout=2)
                    c.close()
                    docker_host = h
                    break
                except docker.errors.DockerException:
                    pass
            if docker_host is not None:
                self._client = docker.DockerClient(base_url=docker_host,
                                                   timeout=300)
            else:
                raise ValueError('Invalid DOCKER_HOST, ensure dockerd is'
                                 ' running.')
            logging.info("Using docker host at %s", docker_host)

        self._satlab = False
        # GSCDevboardHost should only be created on Satlab or localhost, so
        # assume Satlab if a drone container is running.
        if len(self._client.containers.list(filters={'name': 'drone'})) > 0:
            logging.info("In Satlab")
            self._satlab = True

        self._service_debugger_serial = service_debugger_serial
        self._service_ip = service_ip
        self._service_port = service_port
        logging.info("Using service port %s", self._service_port)

        self._docker_network = 'default_satlab' if self._satlab else 'host'
        self._docker_container = None

        serials = self._list_debugger_serials()
        if len(serials) == 0:
            raise ValueError('No debuggers found')
        logging.info("Available debuggers: [%s]", ', '.join(serials))

        if self._service_debugger_serial is None:
            self._service_debugger_serial = serials[0]
        else:
            if self._service_debugger_serial not in serials:
                raise ValueError(
                        '%s debugger not found in [%s]' %
                        (self._service_debugger_serial, ', '.join(serials)))
        logging.info("Using debugger %s", self._service_debugger_serial)
        self._docker_container_name = "gsc_dev_board_{}".format(
                self._service_debugger_serial)

    def _list_debugger_serials(self):
        """List all attached debuggers."""

        c = self._client.containers.run(DOCKER_IMAGE,
                                        remove=True,
                                        privileged=True,
                                        name='list_debugger_serial',
                                        hostname='list_debugger_serial',
                                        detach=True,
                                        volumes=["/dev:/hostdev"],
                                        command=['sleep', '5'])

        res, output = c.exec_run(['lsusb', '-v', '-d', ULTRADEBUG],
                                 stderr=False,
                                 privileged=True)
        c.kill()
        if res != 0:
            return []
        output = output.decode("utf-8").split('\n')
        serials = [
                l.strip().split(' ')[-1] for l in output
                if l.strip()[:7] == 'iSerial'
        ]
        return serials

    @contextlib.contextmanager
    def service_context(self):
        """Service context manager that provides the service endpoint."""
        self.start_service()
        try:
            yield "{}:{}".format(self.service_ip, self.service_port)
        finally:
            self.stop_service()

    def start_service(self):
        """Starts service if needed."""
        if self._docker_container is not None:
            return

        if self._service_ip:
            # Assume container was manually started if service_ip was set
            logging.info("Skip start_service due to set service_ip")
            return

        #TODO(b/215767105): Pull image onto Satlab box if not present.

        environment = {
                'DEVBOARDSVC_PORT': self._service_port,
                'DEBUGGER_SERIAL': self._service_debugger_serial
        }
        start_cmd = ['/opt/gscdevboard/start_devboardsvc.sh']

        # Stop any leftover containers
        try:
            c = self._client.containers.get(self._docker_container_name)
            c.kill()
        except docker.errors.NotFound:
            pass

        self._client.containers.run(DOCKER_IMAGE,
                                    remove=True,
                                    privileged=True,
                                    name=self._docker_container_name,
                                    hostname=self._docker_container_name,
                                    network=self._docker_network,
                                    cap_add=["NET_ADMIN"],
                                    detach=True,
                                    volumes=["/dev:/hostdev"],
                                    environment=environment,
                                    command=start_cmd)

        # A separate containers.get call is needed to capture network attributes
        self._docker_container = self._client.containers.get(
                self._docker_container_name)

    def stop_service(self):
        """Stops service by killing the container."""
        if self._docker_container is None:
            return
        self._docker_container.kill()
        self._docker_container = None

    @property
    def service_port(self):
        """Return service port (local to the container host)."""
        return self._service_port

    @property
    def service_ip(self):
        """Return service ip (local to the container host)."""
        if self._service_ip is not None:
            return self._service_ip

        if self._docker_network == 'host':
            return '127.0.0.1'
        else:
            if self._docker_container is None:
                return ''
            else:
                settings = self._docker_container.attrs['NetworkSettings']
                return settings['Networks'][self._docker_network]['IPAddress']
