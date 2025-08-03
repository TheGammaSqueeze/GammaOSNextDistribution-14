// Copyright 2020 Google Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//	http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package main

import (
	"strings"
	"testing"

	"github.com/google/blueprint/parser"
	"github.com/google/blueprint/proptools"
)

var testCases = []struct {
	name            string
	input           string
	output          string
	property        string
	addSet          string
	removeSet       string
	addLiteral      *string
	setString       *string
	setBool         *string
	removeProperty  bool
	replaceProperty string
	moveProperty    bool
	newLocation     string
}{
	{
		name: "add",
		input: `
			cc_foo {
				name: "foo",
			}
		`,
		output: `
			cc_foo {
				name: "foo",
				deps: ["bar"],
			}
		`,
		property: "deps",
		addSet:   "bar",
	},
	{
		name: "remove",
		input: `
			cc_foo {
				name: "foo",
				deps: ["bar"],
			}
		`,
		output: `
			cc_foo {
				name: "foo",
				deps: [],
			}
		`,
		property:  "deps",
		removeSet: "bar",
	},
	{
		name: "nested add",
		input: `
			cc_foo {
				name: "foo",
			}
		`,
		output: `
			cc_foo {
				name: "foo",
				arch: {
					arm: {
						deps: [
							"dep2",
							"nested_dep",],
					},
				},
			}
		`,
		property: "arch.arm.deps",
		addSet:   "nested_dep,dep2",
	},
	{
		name: "nested remove",
		input: `
			cc_foo {
				name: "foo",
				arch: {
					arm: {
						deps: [
							"dep2",
							"nested_dep",
						],
					},
				},
			}
		`,
		output: `
			cc_foo {
				name: "foo",
				arch: {
					arm: {
						deps: [
						],
					},
				},
			}
		`,
		property:  "arch.arm.deps",
		removeSet: "nested_dep,dep2",
	},
	{
		name: "add existing",
		input: `
			cc_foo {
				name: "foo",
				arch: {
					arm: {
						deps: [
							"nested_dep",
							"dep2",
						],
					},
				},
			}
		`,
		output: `
			cc_foo {
				name: "foo",
				arch: {
					arm: {
						deps: [
							"nested_dep",
							"dep2",
						],
					},
				},
			}
		`,
		property: "arch.arm.deps",
		addSet:   "dep2,dep2",
	},
	{
		name: "remove missing",
		input: `
			cc_foo {
				name: "foo",
				arch: {
					arm: {
						deps: [
							"nested_dep",
							"dep2",
						],
					},
				},
			}
		`,
		output: `
			cc_foo {
				name: "foo",
				arch: {
					arm: {
						deps: [
							"nested_dep",
							"dep2",
						],
					},
				},
			}
		`,
		property:  "arch.arm.deps",
		removeSet: "dep3,dep4",
	},
	{
		name: "remove non existent",
		input: `
			cc_foo {
				name: "foo",
			}
		`,
		output: `
			cc_foo {
				name: "foo",
			}
		`,
		property:  "deps",
		removeSet: "bar",
	},
	{
		name: "remove non existent nested",
		input: `
			cc_foo {
				name: "foo",
				arch: {},
			}
		`,
		output: `
			cc_foo {
				name: "foo",
				arch: {},
			}
		`,
		property:  "arch.arm.deps",
		removeSet: "dep3,dep4",
	},
	{
		name: "add numeric sorted",
		input: `
			cc_foo {
				name: "foo",
				versions: ["1", "2"],
			}
		`,
		output: `
			cc_foo {
				name: "foo",
				versions: [
					"1",
					"2",
					"10",
				],
			}
		`,
		property: "versions",
		addSet:   "10",
	},
	{
		name: "add mixed sorted",
		input: `
			cc_foo {
				name: "foo",
				deps: ["bar-v1-bar", "bar-v2-bar"],
			}
		`,
		output: `
			cc_foo {
				name: "foo",
				deps: [
					"bar-v1-bar",
					"bar-v2-bar",
					"bar-v10-bar",
				],
			}
		`,
		property: "deps",
		addSet:   "bar-v10-bar",
	},
	{
		name:  "add a struct with literal",
		input: `cc_foo {name: "foo"}`,
		output: `cc_foo {
    name: "foo",
    structs: [
        {
            version: "1",
            imports: [
                "bar1",
                "bar2",
            ],
        },
    ],
}
`,
		property:   "structs",
		addLiteral: proptools.StringPtr(`{version: "1", imports: ["bar1", "bar2"]}`),
	},
	{
		name: "set string",
		input: `
			cc_foo {
				name: "foo",
			}
		`,
		output: `
			cc_foo {
				name: "foo",
				foo: "bar",
			}
		`,
		property:  "foo",
		setString: proptools.StringPtr("bar"),
	},
	{
		name: "set existing string",
		input: `
			cc_foo {
				name: "foo",
				foo: "baz",
			}
		`,
		output: `
			cc_foo {
				name: "foo",
				foo: "bar",
			}
		`,
		property:  "foo",
		setString: proptools.StringPtr("bar"),
	},
	{
		name: "set bool",
		input: `
			cc_foo {
				name: "foo",
			}
		`,
		output: `
			cc_foo {
				name: "foo",
				foo: true,
			}
		`,
		property: "foo",
		setBool:  proptools.StringPtr("true"),
	},
	{
		name: "set existing bool",
		input: `
			cc_foo {
				name: "foo",
				foo: true,
			}
		`,
		output: `
			cc_foo {
				name: "foo",
				foo: false,
			}
		`,
		property: "foo",
		setBool:  proptools.StringPtr("false"),
	},
	{
		name: "remove existing property",
		input: `
			cc_foo {
				name: "foo",
				foo: "baz",
			}
		`,
		output: `
			cc_foo {
				name: "foo",
			}
		`,
		property:       "foo",
		removeProperty: true,
	}, {
		name: "remove nested property",
		input: `
		cc_foo {
			name: "foo",
			foo: {
				bar: "baz",
			},
		}
	`,
		output: `
		cc_foo {
			name: "foo",
			foo: {},
		}
	`,
		property:       "foo.bar",
		removeProperty: true,
	}, {
		name: "remove non-existing property",
		input: `
			cc_foo {
				name: "foo",
				foo: "baz",
			}
		`,
		output: `
			cc_foo {
				name: "foo",
				foo: "baz",
			}
		`,
		property:       "bar",
		removeProperty: true,
	}, {
		name:     "replace property",
		property: "deps",
		input: `
			cc_foo {
				name: "foo",
				deps: ["baz", "unchanged"],
			}
		`,
		output: `
			cc_foo {
				name: "foo",
				deps: [
                "baz_lib",
                "unchanged",
				],
			}
		`,
		replaceProperty: "baz=baz_lib,foobar=foobar_lib",
	}, {
		name:     "replace property multiple modules",
		property: "deps,required",
		input: `
			cc_foo {
				name: "foo",
				deps: ["baz", "unchanged"],
				unchanged: ["baz"],
				required: ["foobar"],
			}
		`,
		output: `
			cc_foo {
				name: "foo",
				deps: [
								"baz_lib",
								"unchanged",
				],
				unchanged: ["baz"],
				required: ["foobar_lib"],
			}
		`,
		replaceProperty: "baz=baz_lib,foobar=foobar_lib",
	}, {
		name:     "replace property string value",
		property: "name",
		input: `
			cc_foo {
				name: "foo",
				deps: ["baz"],
				unchanged: ["baz"],
				required: ["foobar"],
			}
		`,
		output: `
			cc_foo {
				name: "foo_lib",
				deps: ["baz"],
				unchanged: ["baz"],
				required: ["foobar"],
			}
		`,
		replaceProperty: "foo=foo_lib",
	}, {
		name:     "replace property string and list values",
		property: "name,deps",
		input: `
			cc_foo {
				name: "foo",
				deps: ["baz"],
				unchanged: ["baz"],
				required: ["foobar"],
			}
		`,
		output: `
			cc_foo {
				name: "foo_lib",
				deps: ["baz_lib"],
				unchanged: ["baz"],
				required: ["foobar"],
			}
		`,
		replaceProperty: "foo=foo_lib,baz=baz_lib",
	}, {
		name: "move contents of property into non-existing property",
		input: `
			cc_foo {
				name: "foo",
				bar: ["barContents"],
				}
`,
		output: `
			cc_foo {
				name: "foo",
				baz: ["barContents"],
			}
		`,
		property:     "bar",
		moveProperty: true,
		newLocation:  "baz",
	}, {
		name: "move contents of property into existing property",
		input: `
			cc_foo {
				name: "foo",
				baz: ["bazContents"],
				bar: ["barContents"],
			}
		`,
		output: `
			cc_foo {
				name: "foo",
				baz: [
					"bazContents",
					"barContents",
				],

			}
		`,
		property:     "bar",
		moveProperty: true,
		newLocation:  "baz",
	}, {
		name: "replace nested",
		input: `
		cc_foo {
			name: "foo",
			foo: {
				bar: "baz",
			},
		}
	`,
		output: `
		cc_foo {
			name: "foo",
			foo: {
				bar: "baz2",
			},
		}
	`,
		property:        "foo.bar",
		replaceProperty: "baz=baz2",
	},
}

func simplifyModuleDefinition(def string) string {
	var result string
	for _, line := range strings.Split(def, "\n") {
		result += strings.TrimSpace(line)
	}
	return result
}
func TestProcessModule(t *testing.T) {
	for i, testCase := range testCases {
		t.Run(testCase.name, func(t *testing.T) {
			targetedProperties.Set(testCase.property)
			addIdents.Set(testCase.addSet)
			removeIdents.Set(testCase.removeSet)
			removeProperty = &testCase.removeProperty
			moveProperty = &testCase.moveProperty
			newLocation = testCase.newLocation
			setString = testCase.setString
			setBool = testCase.setBool
			addLiteral = testCase.addLiteral
			replaceProperty.Set(testCase.replaceProperty)

			inAst, errs := parser.ParseAndEval("", strings.NewReader(testCase.input), parser.NewScope(nil))
			if len(errs) > 0 {
				for _, err := range errs {
					t.Errorf("  %s", err)
				}
				t.Errorf("failed to parse:")
				t.Errorf("%+v", testCase)
				t.FailNow()
			}
			if inModule, ok := inAst.Defs[0].(*parser.Module); !ok {
				t.Fatalf("  input must only contain a single module definition: %s", testCase.input)
			} else {
				for _, p := range targetedProperties.properties {
					_, errs := processModuleProperty(inModule, "", inAst, p)
					if len(errs) > 0 {
						t.Errorf("test case %d:", i)
						for _, err := range errs {
							t.Errorf("  %s", err)
						}
					}

				}
				inModuleText, _ := parser.Print(inAst)
				inModuleString := string(inModuleText)
				if simplifyModuleDefinition(inModuleString) != simplifyModuleDefinition(testCase.output) {
					t.Errorf("test case %d:", i)
					t.Errorf("expected module definition:")
					t.Errorf("  %s", testCase.output)
					t.Errorf("actual module definition:")
					t.Errorf("  %s", inModuleString)
				}
			}
		})
	}
}

func TestReplacementsCycleError(t *testing.T) {
	cycleString := "old1=new1,new1=old1"
	err := replaceProperty.Set(cycleString)

	if err.Error() != "Duplicated replacement name new1" {
		t.Errorf("Error message did not match")
		t.Errorf("Expected ")
		t.Errorf(" Duplicated replacement name new1")
		t.Errorf("actual error:")
		t.Errorf("  %s", err.Error())
		t.FailNow()
	}
}

func TestReplacementsDuplicatedError(t *testing.T) {
	cycleString := "a=b,a=c"
	err := replaceProperty.Set(cycleString)

	if err.Error() != "Duplicated replacement name a" {
		t.Errorf("Error message did not match")
		t.Errorf("Expected ")
		t.Errorf(" Duplicated replacement name a")
		t.Errorf("actual error:")
		t.Errorf("  %s", err.Error())
		t.FailNow()
	}
}

func TestReplacementsMultipleReplacedToSame(t *testing.T) {
	cycleString := "a=c,d=c"
	err := replaceProperty.Set(cycleString)

	if err.Error() != "Duplicated replacement name c" {
		t.Errorf("Error message did not match")
		t.Errorf("Expected ")
		t.Errorf(" Duplicated replacement name c")
		t.Errorf("actual error:")
		t.Errorf("  %s", err.Error())
		t.FailNow()
	}
}
