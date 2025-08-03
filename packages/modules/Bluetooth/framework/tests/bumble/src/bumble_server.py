# Copyright 2023 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import asyncio
import click
import logging
import json

from bumble import pandora as bumble_server
from bumble.pandora import PandoraDevice, Config, serve

from bumble_experimental.asha import AshaService
from bumble_experimental.dck import DckService
from bumble_experimental.gatt import GATTService

from pandora_experimental.asha_grpc_aio import add_AshaServicer_to_server
from pandora_experimental.dck_grpc_aio import add_DckServicer_to_server
from pandora_experimental.gatt_grpc_aio import add_GATTServicer_to_server

from typing import Dict, Any

BUMBLE_SERVER_GRPC_PORT = 7999
ROOTCANAL_PORT_CUTTLEFISH = 7300


@click.command()
@click.option('--grpc-port', help='gRPC port to serve', default=BUMBLE_SERVER_GRPC_PORT)
@click.option('--rootcanal-port', help='Rootcanal TCP port', default=ROOTCANAL_PORT_CUTTLEFISH)
@click.option(
    '--transport',
    help='HCI transport',
    default=f'tcp-client:127.0.0.1:<rootcanal-port>',
)
@click.option(
    '--config',
    help='Bumble json configuration file',
)
def main(grpc_port: int, rootcanal_port: int, transport: str, config: str) -> None:
    bumble_server.register_servicer_hook(
        lambda bumble, _, server: add_AshaServicer_to_server(AshaService(bumble.device), server))
    bumble_server.register_servicer_hook(
        lambda bumble, _, server: add_DckServicer_to_server(DckService(bumble.device), server))
    bumble_server.register_servicer_hook(
        lambda bumble, _, server: add_GATTServicer_to_server(GATTService(bumble.device), server))

    if '<rootcanal-port>' in transport:
        transport = transport.replace('<rootcanal-port>', str(rootcanal_port))

    bumble_config = retrieve_config(config)
    bumble_config.setdefault('transport', transport)
    device = PandoraDevice(bumble_config)

    server_config = Config()
    server_config.load_from_dict(bumble_config.get('server', {}))

    logging.basicConfig(level=logging.DEBUG)
    asyncio.run(serve(device, config=server_config, port=grpc_port))


def retrieve_config(config: str) -> Dict[str, Any]:
    if not config:
        return {}

    with open(config, 'r') as f:
        return json.load(f)


if __name__ == '__main__':
    main()  # pylint: disable=no-value-for-parameter
