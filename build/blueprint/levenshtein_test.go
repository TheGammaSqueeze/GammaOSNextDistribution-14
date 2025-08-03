// Copyright 2014 Google Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package blueprint

import (
	"reflect"
	"testing"
)

func mods(mods []string) []*moduleGroup {
	ret := []*moduleGroup{}

	for _, v := range mods {
		m := moduleGroup{name: v}
		ret = append(ret, &m)
	}

	return ret
}

func assertEqual(t *testing.T, a, b []string) {
	if len(a) == 0 && len(b) == 0 {
		return
	}

	if !reflect.DeepEqual(a, b) {
		t.Errorf("Expected the following to be equal:\n\t%q\n\t%q", a, b)
	}
}

func TestLevenshteinWontGuessUnlike(t *testing.T) {
	assertEqual(t, namesLike("a", "test", mods([]string{"test"})), []string{})
}
func TestLevenshteinInsert(t *testing.T) {
	assertEqual(t, namesLike("a", "test", mods([]string{"ab", "ac", "not_this"})), []string{"ab", "ac"})
}
func TestLevenshteinDelete(t *testing.T) {
	assertEqual(t, namesLike("ab", "test", mods([]string{"a", "b", "not_this"})), []string{"a", "b"})
}
func TestLevenshteinReplace(t *testing.T) {
	assertEqual(t, namesLike("aa", "test", mods([]string{"ab", "ac", "not_this"})), []string{"ab", "ac"})
}
