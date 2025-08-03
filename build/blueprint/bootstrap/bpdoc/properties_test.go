// Copyright 2019 Google Inc. All rights reserved.
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

package bpdoc

import (
	"reflect"
	"strings"
	"testing"
)

func TestExcludeByTag(t *testing.T) {
	r := NewReader(pkgFiles)
	ps, err := r.PropertyStruct(pkgPath, "tagTestProps", reflect.ValueOf(tagTestProps{}))
	if err != nil {
		t.Fatal(err)
	}

	ps.ExcludeByTag("tag1", "a")

	expected := []string{"c", "d", "g"}
	actual := actualProperties(t, ps.Properties)
	if !reflect.DeepEqual(expected, actual) {
		t.Errorf("unexpected ExcludeByTag result, expected: %q, actual: %q", expected, actual)
	}
}

func TestIncludeByTag(t *testing.T) {
	r := NewReader(pkgFiles)
	ps, err := r.PropertyStruct(pkgPath, "tagTestProps", reflect.ValueOf(tagTestProps{A: "B"}))
	if err != nil {
		t.Fatal(err)
	}

	ps.IncludeByTag("tag1", "c")

	expected := []string{"b", "c", "d", "f", "g"}
	actual := actualProperties(t, ps.Properties)
	if !reflect.DeepEqual(expected, actual) {
		t.Errorf("unexpected IncludeByTag result, expected: %q, actual: %q", expected, actual)
	}
}

func TestPropertiesOfReflectionStructs(t *testing.T) {
	testCases := []struct {
		fields             map[string]interface{}
		expectedProperties map[string]Property
		description        string
	}{
		{
			fields: map[string]interface{}{
				"A": "A is a string",
				"B": 0, //B is an int
			},
			expectedProperties: map[string]Property{
				"a": *createProperty("a", "string", ""),
				"b": *createProperty("b", "int", ""),
			},
			description: "struct is composed of primitive types",
		},
		{
			fields: map[string]interface{}{
				"A": "A is a string",
				"B": 0, //B is an int
				"C": props{},
			},
			expectedProperties: map[string]Property{
				"a": *createProperty("a", "string", ""),
				"b": *createProperty("b", "int", ""),
				"c": *createProperty("c", "props", "props docs."),
			},
			description: "struct is composed of primitive types and other structs",
		},
	}

	r := NewReader(pkgFiles)
	for _, testCase := range testCases {
		structType := reflectionStructType(testCase.fields)
		ps, err := r.PropertyStruct(structType.PkgPath(), structType.String(), reflect.New(structType).Elem())
		if err != nil {
			t.Fatal(err)
		}
		for _, actualProperty := range ps.Properties {
			propName := actualProperty.Name
			assertProperties(t, testCase.expectedProperties[propName], actualProperty)
		}
	}
}

func TestNestUnique(t *testing.T) {
	testCases := []struct {
		src         []Property
		target      []Property
		expected    []Property
		description string
	}{
		{
			src:         []Property{},
			target:      []Property{},
			expected:    []Property{},
			description: "Nest Unique fails for empty slice",
		},
		{
			src:         []Property{*createProperty("a", "string", ""), *createProperty("b", "string", "")},
			target:      []Property{},
			expected:    []Property{*createProperty("a", "string", ""), *createProperty("b", "string", "")},
			description: "Nest Unique fails when all elements are unique",
		},
		{
			src:         []Property{*createProperty("a", "string", ""), *createProperty("b", "string", "")},
			target:      []Property{*createProperty("c", "string", "")},
			expected:    []Property{*createProperty("a", "string", ""), *createProperty("b", "string", ""), *createProperty("c", "string", "")},
			description: "Nest Unique fails when all elements are unique",
		},
		{
			src:         []Property{*createProperty("a", "string", ""), *createProperty("b", "string", "")},
			target:      []Property{*createProperty("a", "string", "")},
			expected:    []Property{*createProperty("a", "string", ""), *createProperty("b", "string", "")},
			description: "Nest Unique fails when nested elements are duplicate",
		},
	}

	errMsgTemplate := "%s. Expected: %q, Actual: %q"
	for _, testCase := range testCases {
		actual := nestUnique(testCase.src, testCase.target)
		if len(actual) != len(testCase.expected) {
			t.Errorf(errMsgTemplate, testCase.description, testCase.expected, actual)
		}
		for i := 0; i < len(actual); i++ {
			if !actual[i].Equal(testCase.expected[i]) {
				t.Errorf(errMsgTemplate, testCase.description, testCase.expected[i], actual[i])
			}
		}
	}
}

// Creates a struct using reflection and return its type
func reflectionStructType(fields map[string]interface{}) reflect.Type {
	var structFields []reflect.StructField
	for fieldname, obj := range fields {
		structField := reflect.StructField{
			Name: fieldname,
			Type: reflect.TypeOf(obj),
		}
		structFields = append(structFields, structField)
	}
	return reflect.StructOf(structFields)
}

// Creates a Property object with a subset of its props populated
func createProperty(propName string, propType string, propDocs string) *Property {
	return &Property{Name: propName, Type: propType, Text: formatText(propDocs)}
}

// Asserts that two Property objects are "similar"
// Name, Type and Text properties are checked for similarity
func assertProperties(t *testing.T, expected Property, actual Property) {
	assertStrings(t, expected.Name, actual.Name)
	assertStrings(t, expected.Type, actual.Type)
	assertStrings(t, strings.TrimSpace(string(expected.Text)), strings.TrimSpace(string(actual.Text)))
}

func assertStrings(t *testing.T, expected string, actual string) {
	if expected != actual {
		t.Errorf("expected: %s, actual: %s", expected, actual)
	}
}

func actualProperties(t *testing.T, props []Property) []string {
	t.Helper()

	actual := []string{}
	for _, p := range props {
		actual = append(actual, p.Name)
		actual = append(actual, actualProperties(t, p.Properties)...)
	}
	return actual
}
