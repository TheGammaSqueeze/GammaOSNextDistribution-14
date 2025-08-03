#!/usr/bin/env python3
#
#   Copyright 2023 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#           http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.
"""Provides utilities for generating CMW500 CA scenarios based on band/mimo combinations.

NOTE: Currently does not support:
    1. Scenarios requiring multiple CMW500s
    2. SUA coprocessing
    3. Fading scenarios
"""

from collections import defaultdict
from collections import namedtuple

# Maps CMW scenario names to antenna (MIMO) configuration.
SCENARIO_NAME_MAPPING = {
    # 1CC
    "SCEL": (1, ),
    "TRO": (2, ),
    "AD": (4, ),
    # 2CC
    "CATR": (1, 1),
    "CAFR": (2, 2),
    "BF": (4, 2),
    "BFSM4": (2, 4),
    "BH": (4, 4),
    # 3CC
    "CC": (1, 1, 1),
    "CF": (2, 2, 2),
    "CCMP": (2, 1, 1),
    "CCMS1": (1, 2, 1),
    # 4CC
    "DD": (1, 1, 1, 1),
    "DH": (2, 2, 2, 2),
    # 5CC - 8CC require multiple CMWs
}

# Maps antenna combinations to CMW scenario name.
SCENARIO_ANTENNA_MAPPING = {v: k for k, v in SCENARIO_NAME_MAPPING.items()}


def get_scenario(bands, antennas):
    """Gets a compatible scenario for the given band/antenna combination.

    Args:
        bands: a list defining the bands to use for each CC.
        antennas: a list of integers defining the number of antennas to use for each CC.

    Returns:
        CMW500Scenario: the generated scenario.

    Raises:
        ValueError: if there is no scenario available for the given antenna/band combination.
    """
    antennas = tuple(antennas)
    if not antennas in SCENARIO_ANTENNA_MAPPING:
        raise ValueError(
            "No CMW scenario matches antenna combination: {}".format(antennas))

    generator = CMW500ScenarioGenerator()
    port_configs = [generator.get_next(b, a) for b, a in zip(bands, antennas)]

    scenario_name = SCENARIO_ANTENNA_MAPPING[antennas]
    return CMW500Scenario(scenario_name, port_configs)


def get_antennas(name):
    """Gets the antenna combination mimo corresponding to a scenario name.

    Args:
        name: a string defining the scenario name.

    Returns:
        antennas: a list of integers defining the number of antennas for each CC.

    Raises:
        ValueError: if the scenario name is unknown.
    """
    if not name in SCENARIO_NAME_MAPPING:
        raise ValueError("Unknown scenario name: {}".format(name))

    return list(SCENARIO_NAME_MAPPING[name])


class CMW500Scenario(object):
    """A routed scenario in a CMW500."""

    def __init__(self, name, configs):
        """Initialize a CMW500 scenario from a name and PortConfiguration list.

        Args:
            name: a string defining the CMW500 scenario name.
            configs: list(list(PortConfigurations)) defining the configurations for each CC.
        """
        self.name = name
        self.configs = configs

    @property
    def routing(self):
        """Gets the CMW routing text for the scenario.

        Returns:
            routing: a string defining the routing text for the CMW scenario command.
        """
        routing = []
        i = 1
        for carrier_config in self.configs:
            routing.append("SUA{}".format(i))
            # Assume PCC antenna & uplink are always on port 1
            if i == 1:
                routing.append("RF1C")
                routing.append("RX1")
            for config in carrier_config:
                routing.append("RF{}C".format(config.port_id))
                routing.append("TX{}".format(config.converter_id))
            i += 1
        return ",".join(routing)


# A port/converter combination for a single callbox port in a component carrier.
PortConfiguration = namedtuple("PortConfiguration", "port_id converter_id")


class CMW500ScenarioGenerator(object):
    """Class that is responsible for generating port/converter configurations for cmw500.

    Generator prioritizes using the fewest total number of antenna ports it can.

    Generation rules:
        - There are 4 'converters'
        - Each converter can be used up to a maximum of twice (if both CCs use the same band)
        - Each converter has 2 potental antenna ports that it can be used with
    """

    # Maps converters to possible antenna ports.
    PORT_MAPPING = {1: (1, 2), 2: (3, 4), 3: (1, 2), 4: (3, 4)}

    # Maps antennas to possible converters.
    CONVERTER_MAPPING = {1: (1, 3), 2: (1, 3), 3: (2, 4), 4: (2, 4)}

    def __init__(self):
        self.used_once = defaultdict(list)
        self.free_converters = set([1, 2, 3, 4])

    def get_next(self, band, antenna_count):
        """Generates a routing configuration for the next component carrier in the sequence.

        Returns:
            configs a list of PortConfigurations defining the configuration to use for each port

        Raises:
            ValueError: if the generator fails to find a compatible scenario routing.
        """
        if antenna_count < 1:
            raise ValueError("antenna_count must be greater than 0")

        configs = []
        free_ports = [1, 3, 2, 4]
        converters_temp = []
        # First, try to reuse previously used converters where we can.
        for converter in self.used_once[band]:
            port = next(
                (a for a in self.PORT_MAPPING[converter] if a in free_ports),
                None,
            )
            if port is None:
                # No port available to be used with this converter, save for later.
                converters_temp.append(converter)
                continue

            free_ports.remove(port)
            configs.append(PortConfiguration(port, converter))
            if len(configs) == antenna_count:
                break

        self.used_once[band] = converters_temp
        if len(configs) == antenna_count:
            return configs

        # Try to use unused converters.
        for port in free_ports:
            converter = next(
                (c for c in self.CONVERTER_MAPPING[port]
                 if c in self.free_converters),
                None,
            )
            if converter is None:
                continue

            # Save converter to reuse later.
            self.free_converters.remove(converter)
            self.used_once[band].append(converter)
            configs.append(PortConfiguration(port, converter))
            if len(configs) == antenna_count:
                break

        if len(configs) != antenna_count:
            raise ValueError(
                "Unable to generate CMW500 scenario for requested combination")

        return configs
