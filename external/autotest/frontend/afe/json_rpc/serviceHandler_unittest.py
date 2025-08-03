#!/usr/bin/python3

import json
import unittest

from autotest_lib.frontend import setup_django_environment

import common

from . import serviceHandler


class RpcMethodHolder(object):
    """Fake rpc service for testing."""

    @staticmethod
    def service_1(x, y):
        """Returns x + y"""
        return x + y

    @staticmethod
    def service_2(path):
        """Returns the parts of the path."""
        return path.split('/')[-1]


json_request1 = """
{
    "method": "service_1",
    "params": [7, 9],
    "id": null
}
"""

expected_response1 = '{"error": null, "result": 16, "id": null}'


json_request2 = """
{
    "method": "service_2",
    "params": ["http://www.some.url.com/path/to/package.rpm"],
    "id": null
}
"""

expected_response2 = '{"error": null, "result": "package.rpm", "id": null}'

json_request3 = """
{
    "method": "service_3",
    "params": ["I wonder if this works"],
    "id": null
}
"""


class TestServiceHandler(unittest.TestCase):
    """Tests ServiceHandler using a fake service."""

    def setUp(self):
        holder = RpcMethodHolder()
        self.serviceHandler = serviceHandler.ServiceHandler(holder)


    def test_handleRequest1(self):
        response = self.serviceHandler.handleRequest(json_request1)
        self.assertEquals(json.loads(response), json.loads(expected_response1))


    def test_handleRequest2(self):
        response = self.serviceHandler.handleRequest(json_request2)
        self.assertEquals(json.loads(response), json.loads(expected_response2))


    def test_handleRequest3(self):
        response = self.serviceHandler.handleRequest(json_request3)
        response_obj = eval(response.replace('null', 'None'))
        self.assertNotEquals(response_obj['error'], 'None')


if __name__ == "__main__":
    unittest.main()
