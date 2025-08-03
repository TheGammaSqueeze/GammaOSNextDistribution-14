#!/usr/bin/env python3
#
#   Copyright 2019 - The Android Open Source Project
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

import logging
import time
import traceback
from datetime import datetime, timedelta
from threading import Timer

from blueberry.tests.gd.cert.behavior import when, wait_until
from blueberry.tests.gd.cert.behavior import IHasBehaviors
from blueberry.tests.gd.cert.behavior import anything
from blueberry.tests.gd.cert.behavior import SingleArgumentBehavior
from blueberry.tests.gd.cert.behavior import ReplyStage
from blueberry.tests.gd.cert.event_stream import EventStream, FilteringEventStream
from blueberry.tests.gd.cert.metadata import metadata
from blueberry.tests.gd.cert.truth import assertThat
from bluetooth_packets_python3 import l2cap_packets
import hci_packets as hci

from mobly import asserts
from mobly import signals
from mobly import test_runner
from mobly import base_test


class BogusProto:

    class BogusType:

        def __init__(self):
            self.name = "BogusProto"
            self.is_extension = False
            self.cpp_type = False

        def type(self):
            return 'BogusRpc'

        def label(self):
            return "label"

    class BogusDescriptor:

        def __init__(self, name):
            self.full_name = name

    def __init__(self, value):
        self.value_ = value
        self.DESCRIPTOR = BogusProto.BogusDescriptor(str(value))

    def __str__(self):
        return "BogusRpc value = " + str(self.value_)

    def ListFields(self):
        for field in [BogusProto.BogusType()]:
            yield [field, self.value_]


class FetchEvents:

    def __init__(self, events, delay_ms):
        self.events_ = events
        self.sleep_time_ = (delay_ms * 1.0) / 1000
        self.index_ = 0
        self.done_ = False
        self.then_ = datetime.now()

    def __iter__(self):
        for event in self.events_:
            time.sleep(self.sleep_time_)
            if self.done_:
                return
            logging.debug("yielding %d" % event)
            yield BogusProto(event)

    def done(self):
        return self.done_

    def cancel(self):
        logging.debug("cancel")
        self.done_ = True
        return None


class TestBehaviors(object):

    def __init__(self, parent):
        self.test_request_behavior = SingleArgumentBehavior(lambda: TestBehaviors.TestRequestReplyStage(parent))

    def test_request(self, matcher):
        return self.test_request_behavior.begin(matcher)

    class TestRequestReplyStage(ReplyStage):

        def __init__(self, parent):
            self._parent = parent

        def increment_count(self):
            self._commit(lambda obj: self._increment_count(obj))
            return self

        def _increment_count(self, obj):
            self._parent.count += 1
            self._parent.captured.append(obj)


class ObjectWithBehaviors(IHasBehaviors):

    def __init__(self):
        self.behaviors = TestBehaviors(self)
        self.count = 0
        self.captured = []
        self.unhandled_count = 0

    def get_behaviors(self):
        return self.behaviors

    def increment_unhandled(self):
        self.unhandled_count += 1


class CertSelfTest(base_test.BaseTestClass):

    def setup_test(self):
        return True

    def teardown_test(self):
        return True

    def test_assert_occurs_at_least_passes(self):
        with EventStream(FetchEvents(events=[1, 2, 3, 1, 2, 3], delay_ms=40)) as event_stream:
            event_stream.assert_event_occurs(lambda data: data.value_ == 1,
                                             timeout=timedelta(milliseconds=300),
                                             at_least_times=2)

    def test_assert_occurs_passes(self):
        with EventStream(FetchEvents(events=[1, 2, 3], delay_ms=50)) as event_stream:
            event_stream.assert_event_occurs(lambda data: data.value_ == 1, timeout=timedelta(seconds=1))

    def test_assert_occurs_fails(self):
        try:
            with EventStream(FetchEvents(events=[1, 2, 3], delay_ms=50)) as event_stream:
                event_stream.assert_event_occurs(lambda data: data.value_ == 4, timeout=timedelta(seconds=1))
        except Exception as e:
            logging.debug(e)
            return True  # Failed as expected
        return False

    def test_assert_occurs_at_most_passes(self):
        with EventStream(FetchEvents(events=[1, 2, 3, 4], delay_ms=50)) as event_stream:
            event_stream.assert_event_occurs_at_most(lambda data: data.value_ < 4,
                                                     timeout=timedelta(seconds=1),
                                                     at_most_times=3)

    def test_assert_occurs_at_most_fails(self):
        try:
            with EventStream(FetchEvents(events=[1, 2, 3, 4], delay_ms=50)) as event_stream:
                event_stream.assert_event_occurs_at_most(lambda data: data.value_ > 1,
                                                         timeout=timedelta(seconds=1),
                                                         at_most_times=2)
        except Exception as e:
            logging.debug(e)
            return True  # Failed as expected
        return False

    def test_skip_a_test(self):
        asserts.skip("Skipping this test because it's blocked by b/xyz")
        assert False

    def test_nested_packets(self):
        handle = 123
        inside = hci.ReadScanEnable()
        logging.debug(inside.serialize())
        logging.debug("building outside")
        outside = hci.Acl(handle=handle,
                          packet_boundary_flag=hci.PacketBoundaryFlag.FIRST_NON_AUTOMATICALLY_FLUSHABLE,
                          broadcast_flag=hci.BroadcastFlag.POINT_TO_POINT,
                          payload=inside.serialize())
        logging.debug(outside.serialize())
        logging.debug("Done!")

    def test_l2cap_config_options(self):
        mtu_opt = l2cap_packets.MtuConfigurationOption()
        mtu_opt.mtu = 123
        fcs_opt = l2cap_packets.FrameCheckSequenceOption()
        fcs_opt.fcs_type = l2cap_packets.FcsType.DEFAULT
        request = l2cap_packets.ConfigurationRequestBuilder(
            0x1d,  # Command ID
            0xc1d,  # Channel ID
            l2cap_packets.Continuation.END,
            [mtu_opt, fcs_opt])
        request_b_frame = l2cap_packets.BasicFrameBuilder(0x01, request)
        handle = 123
        wrapped = hci.Acl(handle=handle,
                          packet_boundary_flag=hci.PacketBoundaryFlag.FIRST_NON_AUTOMATICALLY_FLUSHABLE,
                          broadcast_flag=hci.BroadcastFlag.POINT_TO_POINT,
                          payload=bytes(request_b_frame.Serialize()))
        # Size is ACL (4) + L2CAP (4) + Configure (8) + MTU (4) + FCS (3)
        asserts.assert_true(len(wrapped.serialize()) == 23, "Packet serialized incorrectly")

    def test_assertThat_boolean_success(self):
        assertThat(True).isTrue()
        assertThat(False).isFalse()

    def test_assertThat_boolean_falseIsTrue(self):
        try:
            assertThat(False).isTrue()
        except Exception as e:
            return True
        return False

    def test_assertThat_boolean_trueIsFalse(self):
        try:
            assertThat(True).isFalse()
        except Exception as e:
            return True
        return False

    def test_assertThat_object_success(self):
        assertThat("this").isEqualTo("this")
        assertThat("this").isNotEqualTo("that")
        assertThat(None).isNone()
        assertThat("this").isNotNone()

    def test_assertThat_object_isEqualToFails(self):
        try:
            assertThat("this").isEqualTo("that")
        except Exception as e:
            return True
        return False

    def test_assertThat_object_isNotEqualToFails(self):
        try:
            assertThat("this").isNotEqualTo("this")
        except Exception as e:
            return True
        return False

    def test_assertThat_object_isNoneFails(self):
        try:
            assertThat("this").isNone()
        except Exception as e:
            return True
        return False

    def test_assertThat_object_isNotNoneFails(self):
        try:
            assertThat(None).isNotNone()
        except Exception as e:
            return True
        return False

    def test_assertThat_eventStream_emits_passes(self):
        with EventStream(FetchEvents(events=[1, 2, 3], delay_ms=50)) as event_stream:
            assertThat(event_stream).emits(lambda data: data.value_ == 1)

    def test_assertThat_eventStream_emits_then_passes(self):
        with EventStream(FetchEvents(events=[1, 2, 3], delay_ms=50)) as event_stream:
            assertThat(event_stream).emits(lambda data: data.value_ == 1).then(lambda data: data.value_ == 3)

    def test_assertThat_eventStream_emits_fails(self):
        try:
            with EventStream(FetchEvents(events=[1, 2, 3], delay_ms=50)) as event_stream:
                assertThat(event_stream).emits(lambda data: data.value_ == 4)
        except Exception as e:
            logging.debug(e)
            return True  # Failed as expected
        return False

    def test_assertThat_eventStream_emits_then_fails(self):
        try:
            with EventStream(FetchEvents(events=[1, 2, 3], delay_ms=50)) as event_stream:
                assertThat(event_stream).emits(lambda data: data.value_ == 1).emits(lambda data: data.value_ == 4)
        except Exception as e:
            logging.debug(e)
            return True  # Failed as expected
        return False

    def test_assertThat_eventStream_emitsInOrder_passes(self):
        with EventStream(FetchEvents(events=[1, 2, 3], delay_ms=50)) as event_stream:
            assertThat(event_stream).emits(lambda data: data.value_ == 1, lambda data: data.value_ == 2).inOrder()

    def test_assertThat_eventStream_emitsInAnyOrder_passes(self):
        with EventStream(FetchEvents(events=[1, 2, 3], delay_ms=50)) as event_stream:
            assertThat(event_stream).emits(
                lambda data: data.value_ == 2,
                lambda data: data.value_ == 1).inAnyOrder().then(lambda data: data.value_ == 3)

    def test_assertThat_eventStream_emitsInOrder_fails(self):
        try:
            with EventStream(FetchEvents(events=[1, 2, 3], delay_ms=50)) as event_stream:
                assertThat(event_stream).emits(lambda data: data.value_ == 2, lambda data: data.value_ == 1).inOrder()
        except Exception as e:
            logging.debug(e)
            return True  # Failed as expected
        return False

    def test_assertThat_eventStream_emitsInAnyOrder_fails(self):
        try:
            with EventStream(FetchEvents(events=[1, 2, 3], delay_ms=50)) as event_stream:
                assertThat(event_stream).emits(lambda data: data.value_ == 4,
                                               lambda data: data.value_ == 1).inAnyOrder()
        except Exception as e:
            logging.debug(e)
            return True  # Failed as expected
        return False

    def test_assertThat_emitsNone_passes(self):
        with EventStream(FetchEvents(events=[1, 2, 3], delay_ms=50)) as event_stream:
            assertThat(event_stream).emitsNone(lambda data: data.value_ == 4, timeout=timedelta(seconds=0.15)).thenNone(
                lambda data: data.value_ == 5, timeout=timedelta(seconds=0.15))

    def test_assertThat_emitsNone_passes_after_1_second(self):
        with EventStream(FetchEvents(events=[1, 2, 3, 4], delay_ms=400)) as event_stream:
            assertThat(event_stream).emitsNone(lambda data: data.value_ == 4, timeout=timedelta(seconds=1))

    def test_assertThat_emitsNone_fails(self):
        try:
            with EventStream(FetchEvents(events=[1, 2, 3], delay_ms=50)) as event_stream:
                assertThat(event_stream).emitsNone(lambda data: data.value_ == 2, timeout=timedelta(seconds=1))
        except Exception as e:
            logging.debug(e)
            return True  # Failed as expected
        return False

    def test_assertThat_emitsNone_zero_passes(self):
        with EventStream(FetchEvents(events=[], delay_ms=50)) as event_stream:
            assertThat(event_stream).emitsNone(timeout=timedelta(milliseconds=10)).thenNone(timeout=timedelta(
                milliseconds=10))

    def test_assertThat_emitsNone_zero_passes_after_one_second(self):
        with EventStream(FetchEvents([1], delay_ms=1500)) as event_stream:
            assertThat(event_stream).emitsNone(timeout=timedelta(seconds=1.0))

    def test_assertThat_emitsNone_zero_fails(self):
        try:
            with EventStream(FetchEvents(events=[17], delay_ms=50)) as event_stream:
                assertThat(event_stream).emitsNone(timeout=timedelta(seconds=1))
        except Exception as e:
            logging.debug(e)
            return True  # Failed as expected
        return False

    def test_filtering_event_stream_none_filter_function(self):
        with EventStream(FetchEvents(events=[1, 2, 3], delay_ms=50)) as event_stream:
            filtered_event_stream = FilteringEventStream(event_stream, None)
            assertThat(filtered_event_stream) \
                .emits(lambda data: data.value_ == 1) \
                .then(lambda data: data.value_ == 3)

    def test_metadata_empty(self):

        @metadata()
        def simple_pass_test(arg):
            pass

        try:
            simple_pass_test(1)
        except signals.TestFailure:
            pass
        except Exception as e:
            asserts.fail("@metadata() should only raise signals.TestFailure, "
                         "but raised %s with msg %s instead" % (e.__class__.__name__, str(e)))
        else:
            asserts.fail("@metadata() should not work")

    def test_metadata_empty_no_function_call(self):

        @metadata
        def simple_pass_test(arg):
            pass

        try:
            simple_pass_test(1)
        except signals.TestFailure:
            pass
        except Exception as e:
            asserts.fail("@metadata should only raise signals.TestFailure, "
                         "but raised %s with msg %s instead" % (e.__class__.__name__, str(e)))
        else:
            asserts.fail("@metadata should not work")

    def test_metadata_pts_missing_id(self):

        @metadata(pts_test_name="Hello world")
        def simple_pass_test(arg):
            pass

        try:
            simple_pass_test(1)
        except signals.TestFailure:
            pass
        except Exception as e:
            asserts.fail("should only raise signals.TestFailure, "
                         "but raised %s with msg %s instead" % (e.__class__.__name__, str(e)))
        else:
            asserts.fail("missing pts_test_id should not work")

    def test_metadata_pts_missing_name(self):

        @metadata(pts_test_id="A/B/C")
        def simple_pass_test(arg):
            pass

        try:
            simple_pass_test(1)
        except signals.TestFailure:
            pass
        except Exception as e:
            asserts.fail("should only raise signals.TestFailure, "
                         "but raised %s with msg %s instead" % (e.__class__.__name__, str(e)))
        else:
            asserts.fail("missing pts_test_name should not work")

    def test_metadata_pts_test_id_and_description(self):

        @metadata(pts_test_id="A/B/C", pts_test_name="Hello world")
        def simple_pass_test(arg):
            pass

        try:
            simple_pass_test(1)
        except signals.TestPass as e:
            asserts.assert_true("pts_test_id" in e.extras, msg=("pts_test_id not in extra: %s" % str(e.extras)))
            asserts.assert_equal(e.extras["pts_test_id"], "A/B/C")
            asserts.assert_true("pts_test_name" in e.extras, msg=("pts_test_name not in extra: %s" % str(e.extras)))
            asserts.assert_equal(e.extras["pts_test_name"], "Hello world")
        else:
            asserts.fail("Must throw an exception using @metadata decorator")

    def test_metadata_test_with_exception_stacktrace(self):

        @metadata(pts_test_id="A/B/C", pts_test_name="Hello world")
        def simple_fail_test(failure_argument):
            raise ValueError(failure_argument)

        try:
            simple_fail_test("BEEFBEEF")
        except signals.TestError as e:
            asserts.assert_true("pts_test_id" in e.extras, msg=("pts_test_id not in extra: %s" % str(e.extras)))
            asserts.assert_equal(e.extras["pts_test_id"], "A/B/C")
            asserts.assert_true("pts_test_name" in e.extras, msg=("pts_test_name not in extra: %s" % str(e.extras)))
            asserts.assert_equal(e.extras["pts_test_name"], "Hello world")
            trace_str = traceback.format_exc()
            asserts.assert_true("raise ValueError(failure_argument)" in trace_str,
                                msg="Failed test method not in error stack trace: %s" % trace_str)
        else:
            asserts.fail("Must throw an exception using @metadata decorator")

    def test_fluent_behavior_simple(self):
        thing = ObjectWithBehaviors()

        when(thing).test_request(anything()).then().increment_count()

        thing.behaviors.test_request_behavior.run("A")

        assertThat(thing.count).isEqualTo(1)
        assertThat(thing.captured).isEqualTo(["A"])

    def test_fluent_behavior__then_single__captures_one(self):
        thing = ObjectWithBehaviors()

        thing.behaviors.test_request_behavior.set_default_to_ignore()

        when(thing).test_request(anything()).then().increment_count()

        thing.behaviors.test_request_behavior.run("A")
        thing.behaviors.test_request_behavior.run("A")
        thing.behaviors.test_request_behavior.run("A")

        assertThat(thing.count).isEqualTo(1)
        assertThat(thing.captured).isEqualTo(["A"])

    def test_fluent_behavior__then_times__captures_all(self):
        thing = ObjectWithBehaviors()

        when(thing).test_request(anything()).then(times=3).increment_count()

        thing.behaviors.test_request_behavior.run("A")
        thing.behaviors.test_request_behavior.run("B")
        thing.behaviors.test_request_behavior.run("C")

        assertThat(thing.count).isEqualTo(3)
        assertThat(thing.captured).isEqualTo(["A", "B", "C"])

    def test_fluent_behavior__always__captures_all(self):
        thing = ObjectWithBehaviors()

        when(thing).test_request(anything()).always().increment_count()

        thing.behaviors.test_request_behavior.run("A")
        thing.behaviors.test_request_behavior.run("B")
        thing.behaviors.test_request_behavior.run("C")

        assertThat(thing.count).isEqualTo(3)
        assertThat(thing.captured).isEqualTo(["A", "B", "C"])

    def test_fluent_behavior__matcher__captures_relevant(self):
        thing = ObjectWithBehaviors()
        thing.behaviors.test_request_behavior.set_default_to_ignore()

        when(thing).test_request(lambda obj: obj == "B").always().increment_count()

        thing.behaviors.test_request_behavior.run("A")
        thing.behaviors.test_request_behavior.run("B")
        thing.behaviors.test_request_behavior.run("C")

        assertThat(thing.count).isEqualTo(1)
        assertThat(thing.captured).isEqualTo(["B"])

    def test_fluent_behavior__then_repeated__captures_relevant(self):
        thing = ObjectWithBehaviors()
        thing.behaviors.test_request_behavior.set_default_to_ignore()

        when(thing).test_request(anything()).then().increment_count().increment_count()

        thing.behaviors.test_request_behavior.run("A")
        thing.behaviors.test_request_behavior.run("B")
        thing.behaviors.test_request_behavior.run("A")

        assertThat(thing.count).isEqualTo(2)
        assertThat(thing.captured).isEqualTo(["A", "B"])

    def test_fluent_behavior__fallback__captures_relevant(self):
        thing = ObjectWithBehaviors()
        thing.behaviors.test_request_behavior.set_default_to_ignore()

        when(thing).test_request(lambda obj: obj == "B").then(times=1).increment_count()
        when(thing).test_request(lambda obj: obj == "C").always().increment_count()

        thing.behaviors.test_request_behavior.run("A")
        thing.behaviors.test_request_behavior.run("B")
        thing.behaviors.test_request_behavior.run("A")
        thing.behaviors.test_request_behavior.run("C")
        thing.behaviors.test_request_behavior.run("B")
        thing.behaviors.test_request_behavior.run("C")

        assertThat(thing.count).isEqualTo(3)
        assertThat(thing.captured).isEqualTo(["B", "C", "C"])

    def test_fluent_behavior__default_unhandled_crash(self):
        thing = ObjectWithBehaviors()

        when(thing).test_request(anything()).then().increment_count()

        thing.behaviors.test_request_behavior.run("A")
        try:
            thing.behaviors.test_request_behavior.run("A")
        except Exception as e:
            logging.debug(e)
            return True  # Failed as expected
        return False

    def test_fluent_behavior__set_default_works(self):
        thing = ObjectWithBehaviors()
        thing.behaviors.test_request_behavior.set_default(lambda obj: thing.increment_unhandled())

        when(thing).test_request(anything()).then().increment_count()

        thing.behaviors.test_request_behavior.run("A")
        thing.behaviors.test_request_behavior.run("A")
        assertThat(thing.unhandled_count).isEqualTo(1)

    def test_fluent_behavior__wait_until_done(self):
        thing = ObjectWithBehaviors()
        is_a = lambda obj: obj == "A"
        when(thing).test_request(is_a).then().increment_count()

        closure = lambda: thing.behaviors.test_request_behavior.run("A")
        t = Timer(0.5, closure)
        t.start()

        wait_until(thing).test_request(is_a).times(1)
        assertThat(thing.count).isEqualTo(1)
        assertThat(thing.captured).isEqualTo(["A"])

    def test_fluent_behavior__wait_until_done_different_lambda(self):
        thing = ObjectWithBehaviors()
        when(thing).test_request(lambda obj: obj == "A").then().increment_count()

        closure = lambda: thing.behaviors.test_request_behavior.run("A")
        t = Timer(0.5, closure)
        t.start()

        wait_until(thing).test_request(lambda obj: obj == "A").times(1)
        assertThat(thing.count).isEqualTo(1)
        assertThat(thing.captured).isEqualTo(["A"])

    def test_fluent_behavior__wait_until_done_anything(self):
        thing = ObjectWithBehaviors()
        when(thing).test_request(lambda obj: obj == "A").then().increment_count()

        closure = lambda: thing.behaviors.test_request_behavior.run("A")
        t = Timer(0.5, closure)
        t.start()

        wait_until(thing).test_request(anything()).times(1)
        assertThat(thing.count).isEqualTo(1)
        assertThat(thing.captured).isEqualTo(["A"])

    def test_fluent_behavior__wait_until_done_not_happened(self):
        thing = ObjectWithBehaviors()
        thing.behaviors.test_request_behavior.set_default_to_ignore()
        when(thing).test_request(lambda obj: obj == "A").then().increment_count()

        closure = lambda: thing.behaviors.test_request_behavior.run("B")
        t = Timer(0.5, closure)
        t.start()
        assertThat(wait_until(thing).test_request(lambda obj: obj == "A").times(1)).isFalse()

    def test_fluent_behavior__wait_until_done_with_default(self):
        thing = ObjectWithBehaviors()
        thing.behaviors.test_request_behavior.set_default(lambda obj: thing.increment_unhandled())

        closure = lambda: thing.behaviors.test_request_behavior.run("A")
        t = Timer(0.5, closure)
        t.start()

        wait_until(thing).test_request(anything()).times(1)
        assertThat(thing.unhandled_count).isEqualTo(1)

    def test_fluent_behavior__wait_until_done_two_events_AA(self):
        thing = ObjectWithBehaviors()
        when(thing).test_request(lambda obj: obj == "A").then().increment_count().increment_count()

        closure1 = lambda: thing.behaviors.test_request_behavior.run("A")
        t1 = Timer(0.5, closure1)
        t1.start()
        closure2 = lambda: thing.behaviors.test_request_behavior.run("A")
        t2 = Timer(0.5, closure2)
        t2.start()

        wait_until(thing).test_request(lambda obj: obj == "A").times(2)
        assertThat(thing.count).isEqualTo(2)
        assertThat(thing.captured).isEqualTo(["A", "A"])

    def test_fluent_behavior__wait_until_done_two_events_AB(self):
        thing = ObjectWithBehaviors()
        when(thing).test_request(anything()).always().increment_count()

        closure1 = lambda: thing.behaviors.test_request_behavior.run("A")
        t1 = Timer(0.5, closure1)
        t1.start()
        closure2 = lambda: thing.behaviors.test_request_behavior.run("B")
        t2 = Timer(1, closure2)
        t2.start()

        wait_until(thing).test_request(anything()).times(2)
        assertThat(thing.count).isEqualTo(2)
        assertThat(thing.captured).isEqualTo(["A", "B"])

    def test_fluent_behavior__wait_until_done_only_one_event_is_done(self):
        thing = ObjectWithBehaviors()
        when(thing).test_request(anything()).always().increment_count()

        closure1 = lambda: thing.behaviors.test_request_behavior.run("A")
        t1 = Timer(1, closure1)
        t1.start()
        closure2 = lambda: thing.behaviors.test_request_behavior.run("B")
        t2 = Timer(3, closure2)
        t2.start()
        assertThat(wait_until(thing).test_request(lambda obj: obj == "A").times(2)).isFalse()


if __name__ == '__main__':
    test_runner.main()
