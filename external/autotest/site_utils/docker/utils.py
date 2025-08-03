# Copyright 2021 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import os
import logging
try:
    import docker
except ImportError:
    logging.info("Docker API is not installed in this environment")

env_vars = os.environ

# Default docker socker.
DOCKER_SOCKET = env_vars.get('DOCKER_SOCKET', '/var/run/docker.sock')

# This the default IP where the docker daemon is running on the Satlab.
DEFAULT_DOCKER_SERVER_IP = '192.168.231.1'
# This the default IP where the docker daemon is listening on the Satlab.
DEFAULT_DOCKER_TCP_SERVER_PORT = '2375'
# Optional docker tcp ip address/port dockerd listens to.
DOCKER_TCP_SERVER_IP = env_vars.get('DOCKER_TCP_SERVER_IP',
                                    DEFAULT_DOCKER_SERVER_IP)
DOCKER_TCP_SERVER_PORT = env_vars.get('DOCKER_TCP_SERVER_PORT',
                                      DEFAULT_DOCKER_TCP_SERVER_PORT)


def get_docker_client(timeout=300):
    """
    Get the client of the host Docker server either via default Docker socket or TCP connection.
    """
    # Use default TCP connection IP to create docker client if docker socket(
    # /var/run/docker.sock) doesn't exists on the machine or when TCP connection IP
    # is not default IP, otherwise use docker socket file to create docker client.
    if os.path.exists(DOCKER_SOCKET
                      ) and DEFAULT_DOCKER_SERVER_IP == DOCKER_TCP_SERVER_IP:
        client = docker.from_env(timeout=timeout)
    else:
        tcp_connection = "tcp://{}:{}".format(DOCKER_TCP_SERVER_IP,
                                              DOCKER_TCP_SERVER_PORT)
        client = docker.DockerClient(base_url=tcp_connection, timeout=timeout)
    return client


def get_running_containers(client=None):
    """
    Return the names of running containers
    """
    if client is None:
        client = get_docker_client()
    containers = client.containers.list()
    return [c.name for c in containers]


def get_container_networks(container_name, client=None):
    """
    Return the list of networks of the container. Return [] if container is not found.
    """
    if client is None:
        client = get_docker_client()
    containers = get_running_containers(client)
    if container_name not in containers:
        return []
    else:
        container = client.containers.get(container_name)
        return container.attrs['NetworkSettings']['Networks'].keys()


def get_container_ip(container_name, client=None):
    """
    Return the IP Address of networks of the container. Return None if container is not found.
    """
    if client is None:
        client = get_docker_client()
    try:
        container = client.containers.get(container_name)
        if container and container.status == 'running':
            container_network = os.environ.get("DOCKER_DEFAULT_NETWORK",
                                               "default_satlab")
            return container.attrs['NetworkSettings']['Networks'][
                    container_network]['IPAddress']
        logging.exception("Servod container %s found but not running",
                          container_name)
    except docker.errors.APIError:
        logging.exception("Failed to access servod container.")
    except docker.errors.NotFound:
        logging.exception("Servod container %s Not Found", container_name)
    return None
