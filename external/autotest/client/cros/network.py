# Lint as: python2, python3
# Copyright (c) 2019 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging
from six.moves import urllib
import socket
import time

from autotest_lib.client.bin import utils
from autotest_lib.client.common_lib import error


def CheckThatInterfaceCanAccessDestination(host,
                                           interface,
                                           families=[socket.AF_UNSPEC]):
    """
    Checks that we can access a host using a specific interface.

    @param host: Destination host
    @param interface: Name of the network interface to be used
    @raises: error.TestFail if the interface cannot access the specified host.

    """
    logging.debug('Check connection to %s', host)
    # addrinfo records: (family, type, proto, canonname, (addr, port))
    server_addresses = []
    for family in families:
        try:
            records = socket.getaddrinfo(host, 80, family)
        except:
            # Just ignore this family.
            continue
        server_addresses.extend(record[4][0] for record in records)

    found_route = False
    failing_addresses = []
    for address in set(server_addresses):
        # Routes may not always be up by this point. Note that routes for v4 or
        # v6 may come up before the other, so we simply do this poll for all
        # addresses.
        try:
            utils.poll_for_condition(condition=lambda: utils.ping(
                    address, interface=interface, tries=2, timeout=3) == 0,
                                     exception=Exception('No route to %s' %
                                                         address),
                                     timeout=2)
        except Exception as e:
            logging.info(e)
            failing_addresses.append(address)
        else:
            found_route = True

    if not found_route:
        raise error.TestFail('Interface %s cannot connect to %s' % (interface,
                             failing_addresses))


FETCH_URL_PATTERN_FOR_TEST = \
    'http://testing-chargen.appspot.com/download?size=%d'

def FetchUrl(url_pattern, bytes_to_fetch=10, fetch_timeout=10):
    """
    Fetches a specified number of bytes from a URL.

    @param url_pattern: URL pattern for fetching a specified number of bytes.
            %d in the pattern is to be filled in with the number of bytes to
            fetch.
    @param bytes_to_fetch: Number of bytes to fetch.
    @param fetch_timeout: Number of seconds to wait for the fetch to complete
            before it times out.
    @return: The time in seconds spent for fetching the specified number of
            bytes.
    @raises: error.TestError if one of the following happens:
            - The fetch takes no time.
            - The number of bytes fetched differs from the specified
              number.

    """
    # Limit the amount of bytes to read at a time.
    _MAX_FETCH_READ_BYTES = 1024 * 1024

    url = url_pattern % bytes_to_fetch
    logging.info('FetchUrl %s', url)
    start_time = time.time()
    result = urllib.request.urlopen(url, timeout=fetch_timeout)
    bytes_fetched = 0
    while bytes_fetched < bytes_to_fetch:
        bytes_left = bytes_to_fetch - bytes_fetched
        bytes_to_read = min(bytes_left, _MAX_FETCH_READ_BYTES)
        bytes_read = len(result.read(bytes_to_read))
        bytes_fetched += bytes_read
        if bytes_read != bytes_to_read:
            raise error.TestError('FetchUrl tried to read %d bytes, but got '
                                  '%d bytes instead.' %
                                  (bytes_to_read, bytes_read))
        fetch_time = time.time() - start_time
        if fetch_time > fetch_timeout:
            raise error.TestError('FetchUrl exceeded timeout.')

    return fetch_time
