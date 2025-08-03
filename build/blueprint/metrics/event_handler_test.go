// Copyright 2022 Google Inc. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package metrics

import (
	"fmt"
	"reflect"
	"strings"
	"testing"
)

func Map[A any, B any](in []A, f func(A) B) []B {
	r := make([]B, len(in))
	for i, a := range in {
		r[i] = f(a)
	}
	return r
}

func TestEventNameWithDot(t *testing.T) {
	defer func() {
		r := fmt.Sprintf("%v", recover())
		if !strings.HasPrefix(r, "illegal event name") {
			t.Errorf("The code did not panic in the expected manner: %s", r)
		}
	}()
	eh := EventHandler{}
	eh.Begin("a.")
}

func TestEventNesting(t *testing.T) {
	eh := EventHandler{}
	eh.Begin("a")
	eh.Begin("b")
	eh.End("b")
	eh.Begin("c")
	eh.End("c")
	eh.End("a")
	expected := []string{"a.b", "a.c", "a"}
	actual := Map(eh.CompletedEvents(), func(e Event) string {
		return e.Id
	})
	if !reflect.DeepEqual(expected, actual) {
		t.Errorf("expected: %s actual %s", expected, actual)
	}
}

func TestEventOverlap(t *testing.T) {
	defer func() {
		r := fmt.Sprintf("%v", recover())
		if !strings.Contains(r, "unexpected scope end 'a'") {
			t.Errorf("expected panic but: %s", r)
		}
	}()
	eh := EventHandler{}
	eh.Begin("a")
	eh.Begin("b")
	eh.End("a")
}

func TestEventDuplication(t *testing.T) {
	eh := EventHandler{}
	eh.Begin("a")
	eh.Begin("b")
	eh.End("b")
	eh.Begin("b")
	eh.End("b")
	eh.End("a")
	defer func() {
		r := fmt.Sprintf("%v", recover())
		if !strings.HasPrefix(r, "duplicate event") {
			t.Errorf("expected panic but: %s", r)
		}
	}()
	eh.CompletedEvents()
}

func TestIncompleteEvent(t *testing.T) {
	eh := EventHandler{}
	eh.Begin("a")
	defer func() {
		r := fmt.Sprintf("%v", recover())
		if !strings.HasPrefix(r, "retrieving events before all events have been closed.") {
			t.Errorf("expected panic but: %s", r)
		}
	}()
	eh.CompletedEvents()
}
