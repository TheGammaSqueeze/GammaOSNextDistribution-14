// Copyright 2015 Google Inc. All rights reserved.
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

package proptools

import (
	"bytes"
	"os/exec"
	"reflect"
	"testing"
	"unsafe"
)

type escapeTestCase struct {
	name string
	in   string
	out  string
}

var ninjaEscapeTestCase = []escapeTestCase{
	{
		name: "no escaping",
		in:   `test`,
		out:  `test`,
	},
	{
		name: "leading $",
		in:   `$test`,
		out:  `$$test`,
	},
	{
		name: "trailing $",
		in:   `test$`,
		out:  `test$$`,
	},
	{
		name: "leading and trailing $",
		in:   `$test$`,
		out:  `$$test$$`,
	},
}

var shellEscapeTestCase = []escapeTestCase{
	{
		name: "no escaping",
		in:   `test`,
		out:  `test`,
	},
	{
		name: "leading $",
		in:   `$test`,
		out:  `'$test'`,
	},
	{
		name: "trailing $",
		in:   `test$`,
		out:  `'test$'`,
	},
	{
		name: "leading and trailing $",
		in:   `$test$`,
		out:  `'$test$'`,
	},
	{
		name: "single quote",
		in:   `'`,
		out:  `''\'''`,
	},
	{
		name: "multiple single quote",
		in:   `''`,
		out:  `''\'''\'''`,
	},
	{
		name: "double quote",
		in:   `""`,
		out:  `'""'`,
	},
	{
		name: "ORIGIN",
		in:   `-Wl,--rpath,${ORIGIN}/../bionic-loader-test-libs`,
		out:  `'-Wl,--rpath,${ORIGIN}/../bionic-loader-test-libs'`,
	},
}

var shellEscapeIncludingSpacesTestCase = []escapeTestCase{
	{
		name: "no escaping",
		in:   `test`,
		out:  `test`,
	},
	{
		name: "spacing",
		in:   `arg1 arg2`,
		out:  `'arg1 arg2'`,
	},
	{
		name: "single quote",
		in:   `'arg'`,
		out:  `''\''arg'\'''`,
	},
}

func TestNinjaEscaping(t *testing.T) {
	for _, testCase := range ninjaEscapeTestCase {
		got := NinjaEscape(testCase.in)
		if got != testCase.out {
			t.Errorf("%s: expected `%s` got `%s`", testCase.name, testCase.out, got)
		}
	}
}

func TestShellEscaping(t *testing.T) {
	for _, testCase := range shellEscapeTestCase {
		got := ShellEscape(testCase.in)
		if got != testCase.out {
			t.Errorf("%s: expected `%s` got `%s`", testCase.name, testCase.out, got)
		}
	}
}

func TestShellEscapeIncludingSpaces(t *testing.T) {
	for _, testCase := range shellEscapeIncludingSpacesTestCase {
		got := ShellEscapeIncludingSpaces(testCase.in)
		if got != testCase.out {
			t.Errorf("%s: expected `%s` got `%s`", testCase.name, testCase.out, got)
		}
	}
}

func TestExternalShellEscaping(t *testing.T) {
	if testing.Short() {
		return
	}
	for _, testCase := range shellEscapeTestCase {
		cmd := "echo " + ShellEscape(testCase.in)
		got, err := exec.Command("/bin/sh", "-c", cmd).Output()
		got = bytes.TrimSuffix(got, []byte("\n"))
		if err != nil {
			t.Error(err)
		}
		if string(got) != testCase.in {
			t.Errorf("%s: expected `%s` got `%s`", testCase.name, testCase.in, got)
		}
	}
}

func TestExternalShellEscapeIncludingSpaces(t *testing.T) {
	if testing.Short() {
		return
	}
	for _, testCase := range shellEscapeIncludingSpacesTestCase {
		cmd := "echo " + ShellEscapeIncludingSpaces(testCase.in)
		got, err := exec.Command("/bin/sh", "-c", cmd).Output()
		got = bytes.TrimSuffix(got, []byte("\n"))
		if err != nil {
			t.Error(err)
		}
		if string(got) != testCase.in {
			t.Errorf("%s: expected `%s` got `%s`", testCase.name, testCase.in, got)
		}
	}
}

func TestNinjaEscapeList(t *testing.T) {
	type testCase struct {
		name                 string
		in                   []string
		ninjaEscaped         []string
		shellEscaped         []string
		ninjaAndShellEscaped []string
		sameSlice            bool
	}
	testCases := []testCase{
		{
			name:      "empty",
			in:        []string{},
			sameSlice: true,
		},
		{
			name:      "nil",
			in:        nil,
			sameSlice: true,
		},
		{
			name:      "no escaping",
			in:        []string{"abc", "def", "ghi"},
			sameSlice: true,
		},
		{
			name:                 "escape first",
			in:                   []string{`$\abc`, "def", "ghi"},
			ninjaEscaped:         []string{`$$\abc`, "def", "ghi"},
			shellEscaped:         []string{`'$\abc'`, "def", "ghi"},
			ninjaAndShellEscaped: []string{`'$$\abc'`, "def", "ghi"},
		},
		{
			name:                 "escape middle",
			in:                   []string{"abc", `$\def`, "ghi"},
			ninjaEscaped:         []string{"abc", `$$\def`, "ghi"},
			shellEscaped:         []string{"abc", `'$\def'`, "ghi"},
			ninjaAndShellEscaped: []string{"abc", `'$$\def'`, "ghi"},
		},
		{
			name:                 "escape last",
			in:                   []string{"abc", "def", `$\ghi`},
			ninjaEscaped:         []string{"abc", "def", `$$\ghi`},
			shellEscaped:         []string{"abc", "def", `'$\ghi'`},
			ninjaAndShellEscaped: []string{"abc", "def", `'$$\ghi'`},
		},
	}

	testFuncs := []struct {
		name     string
		f        func([]string) []string
		expected func(tt testCase) []string
	}{
		{name: "NinjaEscapeList", f: NinjaEscapeList, expected: func(tt testCase) []string { return tt.ninjaEscaped }},
		{name: "ShellEscapeList", f: ShellEscapeList, expected: func(tt testCase) []string { return tt.shellEscaped }},
		{name: "NinjaAndShellEscapeList", f: NinjaAndShellEscapeList, expected: func(tt testCase) []string { return tt.ninjaAndShellEscaped }},
	}

	for _, tf := range testFuncs {
		t.Run(tf.name, func(t *testing.T) {
			for _, tt := range testCases {
				t.Run(tt.name, func(t *testing.T) {
					inCopy := append([]string(nil), tt.in...)

					got := tf.f(tt.in)

					want := tf.expected(tt)
					if tt.sameSlice {
						want = tt.in
					}

					if !reflect.DeepEqual(got, want) {
						t.Errorf("incorrect output, want %q got %q", want, got)
					}
					if len(inCopy) != len(tt.in) && (len(tt.in) == 0 || !reflect.DeepEqual(inCopy, tt.in)) {
						t.Errorf("input modified, want %#v, got %#v", inCopy, tt.in)
					}

					if (unsafe.SliceData(tt.in) == unsafe.SliceData(got)) != tt.sameSlice {
						if tt.sameSlice {
							t.Errorf("expected input and output slices to have the same backing arrays")
						} else {
							t.Errorf("expected input and output slices to have different backing arrays")
						}
					}
				})
			}
		})
	}
}
