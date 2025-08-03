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

from abc import ABC, abstractmethod
from datetime import datetime, timedelta
from mobly import signals
from threading import Condition

from blueberry.tests.gd.cert.event_stream import static_remaining_time_delta
from blueberry.tests.gd.cert.truth import assertThat


class IHasBehaviors(ABC):

    @abstractmethod
    def get_behaviors(self):
        pass


def anything():
    return lambda obj: True


def when(has_behaviors):
    assertThat(isinstance(has_behaviors, IHasBehaviors)).isTrue()
    return has_behaviors.get_behaviors()


def IGNORE_UNHANDLED(obj):
    pass


class SingleArgumentBehavior(object):

    def __init__(self, reply_stage_factory):
        self._reply_stage_factory = reply_stage_factory
        self._instances = []
        self._invoked_obj = []
        self._invoked_condition = Condition()
        self.set_default_to_crash()

    def begin(self, matcher):
        return PersistenceStage(self, matcher, self._reply_stage_factory)

    def append(self, behavior_instance):
        self._instances.append(behavior_instance)

    def set_default(self, fn):
        assertThat(fn).isNotNone()
        self._default_fn = fn

    def set_default_to_crash(self):
        self._default_fn = None

    def set_default_to_ignore(self):
        self._default_fn = IGNORE_UNHANDLED

    def run(self, obj):
        for instance in self._instances:
            if instance.try_run(obj):
                self.__obj_invoked(obj)
                return
        if self._default_fn is not None:
            # IGNORE_UNHANDLED is also a default fn
            self._default_fn(obj)
            self.__obj_invoked(obj)
        else:
            raise signals.TestFailure(
                "%s: behavior for %s went unhandled" % (self._reply_stage_factory().__class__.__name__, obj),
                extras=None)

    def __obj_invoked(self, obj):
        self._invoked_condition.acquire()
        self._invoked_obj.append(obj)
        self._invoked_condition.notify()
        self._invoked_condition.release()

    def wait_until_invoked(self, matcher, times, timeout):
        end_time = datetime.now() + timeout
        invoked_times = 0
        while datetime.now() < end_time and invoked_times < times:
            remaining = static_remaining_time_delta(end_time)
            invoked_times = sum((matcher(i) for i in self._invoked_obj))
            self._invoked_condition.acquire()
            self._invoked_condition.wait(remaining.total_seconds())
            self._invoked_condition.release()
        return invoked_times == times


class PersistenceStage(object):

    def __init__(self, behavior, matcher, reply_stage_factory):
        self._behavior = behavior
        self._matcher = matcher
        self._reply_stage_factory = reply_stage_factory

    def then(self, times=1):
        reply_stage = self._reply_stage_factory()
        reply_stage.init(self._behavior, self._matcher, times)
        return reply_stage

    def always(self):
        return self.then(times=-1)


class ReplyStage(object):

    def init(self, behavior, matcher, persistence):
        self._behavior = behavior
        self._matcher = matcher
        self._persistence = persistence

    def _commit(self, fn):
        self._behavior.append(BehaviorInstance(self._matcher, self._persistence, fn))


class BehaviorInstance(object):

    def __init__(self, matcher, persistence, fn):
        self._matcher = matcher
        self._persistence = persistence
        self._fn = fn
        self._called_count = 0

    def try_run(self, obj):
        if not self._matcher(obj):
            return False
        if self._persistence >= 0:
            if self._called_count >= self._persistence:
                return False
        self._called_count += 1
        self._fn(obj)
        return True


class BoundVerificationStage(object):

    def __init__(self, behavior, matcher, timeout):
        self._behavior = behavior
        self._matcher = matcher
        self._timeout = timeout

    def times(self, times=1):
        return self._behavior.wait_until_invoked(self._matcher, times, self._timeout)


class WaitForBehaviorSubject(object):

    def __init__(self, behaviors, timeout):
        self._behaviors = behaviors
        self._timeout = timeout

    def __getattr__(self, item):
        behavior = getattr(self._behaviors, item + "_behavior")
        t = self._timeout
        return lambda matcher: BoundVerificationStage(behavior, matcher, t)


def wait_until(i_has_behaviors, timeout=timedelta(seconds=3)):
    return WaitForBehaviorSubject(i_has_behaviors.get_behaviors(), timeout)
