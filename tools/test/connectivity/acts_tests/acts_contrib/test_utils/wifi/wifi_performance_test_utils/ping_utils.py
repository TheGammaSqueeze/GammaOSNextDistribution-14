#!/usr/bin/env python3.4
#
#   Copyright 2021 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the 'License');
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an 'AS IS' BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

import re

RTT_REGEX = re.compile(r'^\[(?P<timestamp>\S+)\] .*? time=(?P<rtt>\S+)')
LOSS_REGEX = re.compile(r'(?P<loss>\S+)% packet loss')


class PingResult(object):
    """An object that contains the results of running ping command.

    Attributes:
        connected: True if a connection was made. False otherwise.
        packet_loss_percentage: The total percentage of packets lost.
        transmission_times: The list of PingTransmissionTimes containing the
            timestamps gathered for transmitted packets.
        rtts: An list-like object enumerating all round-trip-times of
            transmitted packets.
        timestamps: A list-like object enumerating the beginning timestamps of
            each packet transmission.
        ping_interarrivals: A list-like object enumerating the amount of time
            between the beginning of each subsequent transmission.
    """
    def __init__(self, ping_output):
        self.packet_loss_percentage = 100
        self.transmission_times = []

        self.rtts = _ListWrap(self.transmission_times, lambda entry: entry.rtt)
        self.timestamps = _ListWrap(self.transmission_times,
                                    lambda entry: entry.timestamp)
        self.ping_interarrivals = _PingInterarrivals(self.transmission_times)

        self.start_time = 0
        for line in ping_output:
            if 'loss' in line:
                match = re.search(LOSS_REGEX, line)
                self.packet_loss_percentage = float(match.group('loss'))
            if 'time=' in line:
                match = re.search(RTT_REGEX, line)
                if self.start_time == 0:
                    self.start_time = float(match.group('timestamp'))
                self.transmission_times.append(
                    PingTransmissionTimes(
                        float(match.group('timestamp')) - self.start_time,
                        float(match.group('rtt'))))
        self.connected = len(
            ping_output) > 1 and self.packet_loss_percentage < 100

    def __getitem__(self, item):
        if item == 'rtt':
            return self.rtts
        if item == 'connected':
            return self.connected
        if item == 'packet_loss_percentage':
            return self.packet_loss_percentage
        raise ValueError('Invalid key. Please use an attribute instead.')

    def as_dict(self):
        return {
            'connected': 1 if self.connected else 0,
            'rtt': list(self.rtts),
            'time_stamp': list(self.timestamps),
            'ping_interarrivals': list(self.ping_interarrivals),
            'packet_loss_percentage': self.packet_loss_percentage
        }


class PingTransmissionTimes(object):
    """A class that holds the timestamps for a packet sent via the ping command.

    Attributes:
        rtt: The round trip time for the packet sent.
        timestamp: The timestamp the packet started its trip.
    """
    def __init__(self, timestamp, rtt):
        self.rtt = rtt
        self.timestamp = timestamp


class _ListWrap(object):
    """A convenient helper class for treating list iterators as native lists."""
    def __init__(self, wrapped_list, func):
        self.__wrapped_list = wrapped_list
        self.__func = func

    def __getitem__(self, key):
        return self.__func(self.__wrapped_list[key])

    def __iter__(self):
        for item in self.__wrapped_list:
            yield self.__func(item)

    def __len__(self):
        return len(self.__wrapped_list)


class _PingInterarrivals(object):
    """A helper class for treating ping interarrivals as a native list."""
    def __init__(self, ping_entries):
        self.__ping_entries = ping_entries

    def __getitem__(self, key):
        return (self.__ping_entries[key + 1].timestamp -
                self.__ping_entries[key].timestamp)

    def __iter__(self):
        for index in range(len(self.__ping_entries) - 1):
            yield self[index]

    def __len__(self):
        return max(0, len(self.__ping_entries) - 1)
