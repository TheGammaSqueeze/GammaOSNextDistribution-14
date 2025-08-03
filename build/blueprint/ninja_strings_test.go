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
	"strconv"
	"strings"
	"testing"
	"unsafe"
)

type testVariableRef struct {
	start, end int
	name       string
}

func TestParseNinjaString(t *testing.T) {
	testCases := []struct {
		input string
		vars  []string
		value string
		eval  string
		err   string
	}{
		{
			input: "abc def $ghi jkl",
			vars:  []string{"ghi"},
			value: "abc def ${namespace.ghi} jkl",
			eval:  "abc def GHI jkl",
		},
		{
			input: "abc def $ghi$jkl",
			vars:  []string{"ghi", "jkl"},
			value: "abc def ${namespace.ghi}${namespace.jkl}",
			eval:  "abc def GHIJKL",
		},
		{
			input: "foo $012_-345xyz_! bar",
			vars:  []string{"012_-345xyz_"},
			value: "foo ${namespace.012_-345xyz_}! bar",
			eval:  "foo 012_-345XYZ_! bar",
		},
		{
			input: "foo ${012_-345xyz_} bar",
			vars:  []string{"012_-345xyz_"},
			value: "foo ${namespace.012_-345xyz_} bar",
			eval:  "foo 012_-345XYZ_ bar",
		},
		{
			input: "foo ${012_-345xyz_} bar",
			vars:  []string{"012_-345xyz_"},
			value: "foo ${namespace.012_-345xyz_} bar",
			eval:  "foo 012_-345XYZ_ bar",
		},
		{
			input: "foo $$ bar",
			vars:  nil,
			value: "foo $$ bar",
			eval:  "foo $$ bar",
		},
		{
			input: "$foo${bar}",
			vars:  []string{"foo", "bar"},
			value: "${namespace.foo}${namespace.bar}",
			eval:  "FOOBAR",
		},
		{
			input: "$foo$$",
			vars:  []string{"foo"},
			value: "${namespace.foo}$$",
			eval:  "FOO$$",
		},
		{
			input: "foo bar",
			vars:  nil,
			value: "foo bar",
			eval:  "foo bar",
		},
		{
			input: " foo ",
			vars:  nil,
			value: "$ foo ",
			eval:  "$ foo ",
		},
		{
			input: "\tfoo ",
			vars:  nil,
			value: "\tfoo ",
			eval:  "\tfoo ",
		},
		{
			input: "\nfoo ",
			vars:  nil,
			value: "$\nfoo ",
			eval:  "\nfoo ",
		},
		{
			input: " $foo ",
			vars:  []string{"foo"},
			value: "$ ${namespace.foo} ",
			eval:  " FOO ",
		},
		{
			input: "\t$foo ",
			vars:  []string{"foo"},
			value: "\t${namespace.foo} ",
			eval:  "\tFOO ",
		},
		{
			input: "\n$foo ",
			vars:  []string{"foo"},
			value: "$\n${namespace.foo} ",
			eval:  "\nFOO ",
		},
		{
			input: "foo $ bar",
			err:   `error parsing ninja string "foo $ bar": invalid character after '$' at byte offset 5`,
		},
		{
			input: "foo $",
			err:   "unexpected end of string after '$'",
		},
		{
			input: "foo ${} bar",
			err:   `error parsing ninja string "foo ${} bar": empty variable name at byte offset 6`,
		},
		{
			input: "foo ${abc!} bar",
			err:   `error parsing ninja string "foo ${abc!} bar": invalid character in variable name at byte offset 9`,
		},
		{
			input: "foo ${abc",
			err:   "unexpected end of string in variable name",
		},
	}

	for _, testCase := range testCases {
		t.Run(testCase.input, func(t *testing.T) {
			scope := newLocalScope(nil, "namespace.")
			variablesMap := map[Variable]*ninjaString{}
			for _, varName := range testCase.vars {
				_, err := scope.LookupVariable(varName)
				if err != nil {
					v, err := scope.AddLocalVariable(varName, strings.ToUpper(varName))
					if err != nil {
						t.Fatalf("error creating scope: %s", err)
					}
					variablesMap[v] = simpleNinjaString(strings.ToUpper(varName))
				}
			}

			output, err := parseNinjaString(scope, testCase.input)
			if err == nil {
				if g, w := output.Value(&nameTracker{}), testCase.value; g != w {
					t.Errorf("incorrect Value output, want %q, got %q", w, g)
				}

				eval, err := output.Eval(variablesMap)
				if err != nil {
					t.Errorf("unexpected error in Eval: %s", err)
				}
				if g, w := eval, testCase.eval; g != w {
					t.Errorf("incorrect Eval output, want %q, got %q", w, g)
				}
			}
			var errStr string
			if err != nil {
				errStr = err.Error()
			}
			if err != nil && err.Error() != testCase.err {
				t.Errorf("unexpected error:")
				t.Errorf("     input: %q", testCase.input)
				t.Errorf("  expected: %q", testCase.err)
				t.Errorf("       got: %q", errStr)
			}
		})
	}
}

func TestParseNinjaStringWithImportedVar(t *testing.T) {
	pctx := &packageContext{}
	pkgNames := map[*packageContext]string{
		pctx: "impPkg",
	}
	ImpVar := &staticVariable{pctx: pctx, name_: "ImpVar"}
	impScope := newScope(nil)
	impScope.AddVariable(ImpVar)
	scope := newScope(nil)
	scope.AddImport("impPkg", impScope)

	input := "abc def ${impPkg.ImpVar} ghi"
	output, err := parseNinjaString(scope, input)
	if err != nil {
		t.Fatalf("unexpected error: %s", err)
	}

	expect := []variableReference{{8, 24, ImpVar}}
	if !reflect.DeepEqual(*output.variables, expect) {
		t.Errorf("incorrect output:")
		t.Errorf("     input: %q", input)
		t.Errorf("  expected: %#v", expect)
		t.Errorf("       got: %#v", *output.variables)
	}

	if g, w := output.Value(&nameTracker{pkgNames: pkgNames}), "abc def ${g.impPkg.ImpVar} ghi"; g != w {
		t.Errorf("incorrect Value output, want %q got %q", w, g)
	}
}

func Test_parseNinjaOrSimpleStrings(t *testing.T) {
	testCases := []struct {
		name            string
		in              []string
		outStrings      []string
		outNinjaStrings []string
		sameSlice       bool
	}{
		{
			name:      "nil",
			in:        nil,
			sameSlice: true,
		},
		{
			name:      "empty",
			in:        []string{},
			sameSlice: true,
		},
		{
			name:      "string",
			in:        []string{"abc"},
			sameSlice: true,
		},
		{
			name:            "ninja string",
			in:              []string{"$abc"},
			outStrings:      nil,
			outNinjaStrings: []string{"${abc}"},
		},
		{
			name:            "ninja string first",
			in:              []string{"$abc", "def", "ghi"},
			outStrings:      []string{"def", "ghi"},
			outNinjaStrings: []string{"${abc}"},
		},
		{
			name:            "ninja string middle",
			in:              []string{"abc", "$def", "ghi"},
			outStrings:      []string{"abc", "ghi"},
			outNinjaStrings: []string{"${def}"},
		},
		{
			name:            "ninja string last",
			in:              []string{"abc", "def", "$ghi"},
			outStrings:      []string{"abc", "def"},
			outNinjaStrings: []string{"${ghi}"},
		},
	}

	for _, tt := range testCases {
		t.Run(tt.name, func(t *testing.T) {
			inCopy := append([]string(nil), tt.in...)

			scope := newLocalScope(nil, "")
			scope.AddLocalVariable("abc", "abc")
			scope.AddLocalVariable("def", "def")
			scope.AddLocalVariable("ghi", "ghi")
			gotNinjaStrings, gotStrings, err := parseNinjaOrSimpleStrings(scope, tt.in)
			if err != nil {
				t.Errorf("unexpected error %s", err)
			}

			wantStrings := tt.outStrings
			if tt.sameSlice {
				wantStrings = tt.in
			}

			wantNinjaStrings := tt.outNinjaStrings

			var evaluatedNinjaStrings []string
			if gotNinjaStrings != nil {
				evaluatedNinjaStrings = make([]string, 0, len(gotNinjaStrings))
				for _, ns := range gotNinjaStrings {
					evaluatedNinjaStrings = append(evaluatedNinjaStrings, ns.Value(&nameTracker{}))
				}
			}

			if !reflect.DeepEqual(gotStrings, wantStrings) {
				t.Errorf("incorrect strings output, want %q got %q", wantStrings, gotStrings)
			}
			if !reflect.DeepEqual(evaluatedNinjaStrings, wantNinjaStrings) {
				t.Errorf("incorrect ninja strings output, want %q got %q", wantNinjaStrings, evaluatedNinjaStrings)
			}
			if len(inCopy) != len(tt.in) && (len(tt.in) == 0 || !reflect.DeepEqual(inCopy, tt.in)) {
				t.Errorf("input modified, want %#v, got %#v", inCopy, tt.in)
			}

			if (unsafe.SliceData(tt.in) == unsafe.SliceData(gotStrings)) != tt.sameSlice {
				if tt.sameSlice {
					t.Errorf("expected input and output slices to have the same backing arrays")
				} else {
					t.Errorf("expected input and output slices to have different backing arrays")
				}
			}

		})
	}
}

func Benchmark_parseNinjaString(b *testing.B) {
	b.Run("constant", func(b *testing.B) {
		for _, l := range []int{1, 10, 100, 1000} {
			b.Run(strconv.Itoa(l), func(b *testing.B) {
				b.ReportAllocs()
				for n := 0; n < b.N; n++ {
					_ = simpleNinjaString(strings.Repeat("a", l))
				}
			})
		}
	})
	b.Run("variable", func(b *testing.B) {
		for _, l := range []int{1, 10, 100, 1000} {
			scope := newLocalScope(nil, "")
			scope.AddLocalVariable("a", strings.Repeat("b", l/3))
			b.Run(strconv.Itoa(l), func(b *testing.B) {
				b.ReportAllocs()
				for n := 0; n < b.N; n++ {
					_, _ = parseNinjaString(scope, strings.Repeat("a", l/3)+"${a}"+strings.Repeat("a", l/3))
				}
			})
		}
	})
	b.Run("variables", func(b *testing.B) {
		for _, l := range []int{1, 2, 3, 4, 5, 10, 100, 1000} {
			scope := newLocalScope(nil, "")
			str := strings.Repeat("a", 10)
			for i := 0; i < l; i++ {
				scope.AddLocalVariable("a"+strconv.Itoa(i), strings.Repeat("b", 10))
				str += "${a" + strconv.Itoa(i) + "}"
			}
			b.Run(strconv.Itoa(l), func(b *testing.B) {
				b.ReportAllocs()
				for n := 0; n < b.N; n++ {
					_, _ = parseNinjaString(scope, str)
				}
			})
		}
	})

}

func BenchmarkNinjaString_Value(b *testing.B) {
	b.Run("constant", func(b *testing.B) {
		for _, l := range []int{1, 10, 100, 1000} {
			ns := simpleNinjaString(strings.Repeat("a", l))
			b.Run(strconv.Itoa(l), func(b *testing.B) {
				b.ReportAllocs()
				for n := 0; n < b.N; n++ {
					ns.Value(&nameTracker{})
				}
			})
		}
	})
	b.Run("variable", func(b *testing.B) {
		for _, l := range []int{1, 10, 100, 1000} {
			scope := newLocalScope(nil, "")
			scope.AddLocalVariable("a", strings.Repeat("b", l/3))
			ns, _ := parseNinjaString(scope, strings.Repeat("a", l/3)+"${a}"+strings.Repeat("a", l/3))
			b.Run(strconv.Itoa(l), func(b *testing.B) {
				b.ReportAllocs()
				for n := 0; n < b.N; n++ {
					ns.Value(&nameTracker{})
				}
			})
		}
	})
	b.Run("variables", func(b *testing.B) {
		for _, l := range []int{1, 2, 3, 4, 5, 10, 100, 1000} {
			scope := newLocalScope(nil, "")
			str := strings.Repeat("a", 10)
			for i := 0; i < l; i++ {
				scope.AddLocalVariable("a"+strconv.Itoa(i), strings.Repeat("b", 10))
				str += "${a" + strconv.Itoa(i) + "}"
			}
			ns, _ := parseNinjaString(scope, str)
			b.Run(strconv.Itoa(l), func(b *testing.B) {
				b.ReportAllocs()
				for n := 0; n < b.N; n++ {
					ns.Value(&nameTracker{})
				}
			})
		}
	})

}
