#!/usr/bin/env python3
#
#   Copyright 2020 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

import grpc
import re

from blueberry.facade import common_pb2 as common
from google.protobuf import text_format


def custom_message_formatter(m, ident, as_one_line):
    if m.DESCRIPTOR == common.Data.DESCRIPTOR:
        return 'payload: (hex) "{}"'.format(m.payload.hex(" "))
    return None


def pretty_print(request):
    return '{} {}'.format(
        type(request).__name__,
        text_format.MessageToString(request, as_one_line=True, message_formatter=custom_message_formatter))


class LoggingRandezvousWrapper():

    def __init__(self, server_stream_call, logTag):
        if server_stream_call is None:
            raise ValueError("server_stream_call cannot be None")
        self.server_stream_call = server_stream_call
        self.logTag = logTag

    def cancel(self):
        self.server_stream_call.cancel()

    def cancelled(self):
        return self.server_stream_call.cancelled()

    def __iter__(self):
        return self

    def __next__(self):
        resp = self.server_stream_call.__next__()
        print("%s %s" % (self.logTag, pretty_print(resp)))
        return resp


class LoggingClientInterceptor(grpc.UnaryUnaryClientInterceptor, grpc.UnaryStreamClientInterceptor):

    TAG_MIN_WIDTH = 24

    def __init__(self, name):
        self.name = name
        self.inLogTag = "[host ▶▶▶▶▶ %s]" % self.name
        self.outLogTag = "[host ◀◀◀◀◀ %s]" % self.name
        tagLength = len(re.sub('[^\w\s]', '', self.inLogTag)) + 11
        if tagLength < self.TAG_MIN_WIDTH:
            self.inLogTag += " " * (self.TAG_MIN_WIDTH - tagLength)
            self.outLogTag += " " * (self.TAG_MIN_WIDTH - tagLength)

    def intercept_unary_unary(self, continuation, client_call_details, request):
        """
        This interceptor logs the requests from host
        """
        print("%s%s %s" % (self.inLogTag, client_call_details.method, pretty_print(request)))
        return continuation(client_call_details, request)

    def intercept_unary_stream(self, continuation, client_call_details, request):
        """
        This interceptor wraps the server response, and logs all the messages coming to host
        """
        print("%s%s %s" % (self.inLogTag, client_call_details.method, pretty_print(request)))
        server_stream_call = continuation(client_call_details, request)
        retuningMsgLogTag = self.outLogTag + client_call_details.method
        return LoggingRandezvousWrapper(server_stream_call, retuningMsgLogTag)
