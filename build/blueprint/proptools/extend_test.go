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
	"errors"
	"fmt"
	"reflect"
	"strings"
	"testing"
)

type appendPropertyTestCase struct {
	name   string
	dst    interface{}
	src    interface{}
	out    interface{}
	order  Order // default is Append
	filter ExtendPropertyFilterFunc
	err    error
}

func appendPropertiesTestCases() []appendPropertyTestCase {
	return []appendPropertyTestCase{
		// Valid inputs

		{
			name: "Append bool",
			dst: &struct{ B1, B2, B3, B4 bool }{
				B1: true,
				B2: false,
				B3: true,
				B4: false,
			},
			src: &struct{ B1, B2, B3, B4 bool }{
				B1: true,
				B2: true,
				B3: false,
				B4: false,
			},
			out: &struct{ B1, B2, B3, B4 bool }{
				B1: true,
				B2: true,
				B3: true,
				B4: false,
			},
		},
		{
			name: "Prepend bool",
			dst: &struct{ B1, B2, B3, B4 bool }{
				B1: true,
				B2: false,
				B3: true,
				B4: false,
			},
			src: &struct{ B1, B2, B3, B4 bool }{
				B1: true,
				B2: true,
				B3: false,
				B4: false,
			},
			out: &struct{ B1, B2, B3, B4 bool }{
				B1: true,
				B2: true,
				B3: true,
				B4: false,
			},
			order: Prepend,
		},
		{
			name: "Append strings",
			dst: &struct{ S string }{
				S: "string1",
			},
			src: &struct{ S string }{
				S: "string2",
			},
			out: &struct{ S string }{
				S: "string1string2",
			},
		},
		{
			name: "Prepend strings",
			dst: &struct{ S string }{
				S: "string1",
			},
			src: &struct{ S string }{
				S: "string2",
			},
			out: &struct{ S string }{
				S: "string2string1",
			},
			order: Prepend,
		},
		{
			name: "Append pointer to bool",
			dst: &struct{ B1, B2, B3, B4, B5, B6, B7, B8, B9 *bool }{
				B1: BoolPtr(true),
				B2: BoolPtr(false),
				B3: nil,
				B4: BoolPtr(true),
				B5: BoolPtr(false),
				B6: nil,
				B7: BoolPtr(true),
				B8: BoolPtr(false),
				B9: nil,
			},
			src: &struct{ B1, B2, B3, B4, B5, B6, B7, B8, B9 *bool }{
				B1: nil,
				B2: nil,
				B3: nil,
				B4: BoolPtr(true),
				B5: BoolPtr(true),
				B6: BoolPtr(true),
				B7: BoolPtr(false),
				B8: BoolPtr(false),
				B9: BoolPtr(false),
			},
			out: &struct{ B1, B2, B3, B4, B5, B6, B7, B8, B9 *bool }{
				B1: BoolPtr(true),
				B2: BoolPtr(false),
				B3: nil,
				B4: BoolPtr(true),
				B5: BoolPtr(true),
				B6: BoolPtr(true),
				B7: BoolPtr(false),
				B8: BoolPtr(false),
				B9: BoolPtr(false),
			},
		},
		{
			name: "Prepend pointer to bool",
			dst: &struct{ B1, B2, B3, B4, B5, B6, B7, B8, B9 *bool }{
				B1: BoolPtr(true),
				B2: BoolPtr(false),
				B3: nil,
				B4: BoolPtr(true),
				B5: BoolPtr(false),
				B6: nil,
				B7: BoolPtr(true),
				B8: BoolPtr(false),
				B9: nil,
			},
			src: &struct{ B1, B2, B3, B4, B5, B6, B7, B8, B9 *bool }{
				B1: nil,
				B2: nil,
				B3: nil,
				B4: BoolPtr(true),
				B5: BoolPtr(true),
				B6: BoolPtr(true),
				B7: BoolPtr(false),
				B8: BoolPtr(false),
				B9: BoolPtr(false),
			},
			out: &struct{ B1, B2, B3, B4, B5, B6, B7, B8, B9 *bool }{
				B1: BoolPtr(true),
				B2: BoolPtr(false),
				B3: nil,
				B4: BoolPtr(true),
				B5: BoolPtr(false),
				B6: BoolPtr(true),
				B7: BoolPtr(true),
				B8: BoolPtr(false),
				B9: BoolPtr(false),
			},
			order: Prepend,
		},
		{
			name: "Append pointer to integer",
			dst: &struct{ I1, I2, I3, I4, I5, I6, I7, I8, I9 *int64 }{
				I1: Int64Ptr(55),
				I2: Int64Ptr(-3),
				I3: nil,
				I4: Int64Ptr(100),
				I5: Int64Ptr(33),
				I6: nil,
				I7: Int64Ptr(77),
				I8: Int64Ptr(0),
				I9: nil,
			},
			src: &struct{ I1, I2, I3, I4, I5, I6, I7, I8, I9 *int64 }{
				I1: nil,
				I2: nil,
				I3: nil,
				I4: Int64Ptr(1),
				I5: Int64Ptr(-2),
				I6: Int64Ptr(8),
				I7: Int64Ptr(9),
				I8: Int64Ptr(10),
				I9: Int64Ptr(11),
			},
			out: &struct{ I1, I2, I3, I4, I5, I6, I7, I8, I9 *int64 }{
				I1: Int64Ptr(55),
				I2: Int64Ptr(-3),
				I3: nil,
				I4: Int64Ptr(1),
				I5: Int64Ptr(-2),
				I6: Int64Ptr(8),
				I7: Int64Ptr(9),
				I8: Int64Ptr(10),
				I9: Int64Ptr(11),
			},
		},
		{
			name: "Prepend pointer to integer",
			dst: &struct{ I1, I2, I3 *int64 }{
				I1: Int64Ptr(55),
				I3: nil,
			},
			src: &struct{ I1, I2, I3 *int64 }{
				I2: Int64Ptr(33),
			},
			out: &struct{ I1, I2, I3 *int64 }{
				I1: Int64Ptr(55),
				I2: Int64Ptr(33),
				I3: nil,
			},
			order: Prepend,
		},
		{
			name: "Append pointer to strings",
			dst: &struct{ S1, S2, S3, S4 *string }{
				S1: StringPtr("string1"),
				S2: StringPtr("string2"),
			},
			src: &struct{ S1, S2, S3, S4 *string }{
				S1: StringPtr("string3"),
				S3: StringPtr("string4"),
			},
			out: &struct{ S1, S2, S3, S4 *string }{
				S1: StringPtr("string3"),
				S2: StringPtr("string2"),
				S3: StringPtr("string4"),
				S4: nil,
			},
		},
		{
			name: "Prepend pointer to strings",
			dst: &struct{ S1, S2, S3, S4 *string }{
				S1: StringPtr("string1"),
				S2: StringPtr("string2"),
			},
			src: &struct{ S1, S2, S3, S4 *string }{
				S1: StringPtr("string3"),
				S3: StringPtr("string4"),
			},
			out: &struct{ S1, S2, S3, S4 *string }{
				S1: StringPtr("string1"),
				S2: StringPtr("string2"),
				S3: StringPtr("string4"),
				S4: nil,
			},
			order: Prepend,
		},
		{
			name: "Append slice",
			dst: &struct{ S []string }{
				S: []string{"string1"},
			},
			src: &struct{ S []string }{
				S: []string{"string2"},
			},
			out: &struct{ S []string }{
				S: []string{"string1", "string2"},
			},
		},
		{
			name: "Prepend slice",
			dst: &struct{ S []string }{
				S: []string{"string1"},
			},
			src: &struct{ S []string }{
				S: []string{"string2"},
			},
			out: &struct{ S []string }{
				S: []string{"string2", "string1"},
			},
			order: Prepend,
		},
		{
			name: "Replace slice",
			dst: &struct{ S []string }{
				S: []string{"string1"},
			},
			src: &struct{ S []string }{
				S: []string{"string2"},
			},
			out: &struct{ S []string }{
				S: []string{"string2"},
			},
			order: Replace,
		},
		{
			name: "Append empty slice",
			dst: &struct{ S1, S2 []string }{
				S1: []string{"string1"},
				S2: []string{},
			},
			src: &struct{ S1, S2 []string }{
				S1: []string{},
				S2: []string{"string2"},
			},
			out: &struct{ S1, S2 []string }{
				S1: []string{"string1"},
				S2: []string{"string2"},
			},
		},
		{
			name: "Prepend empty slice",
			dst: &struct{ S1, S2 []string }{
				S1: []string{"string1"},
				S2: []string{},
			},
			src: &struct{ S1, S2 []string }{
				S1: []string{},
				S2: []string{"string2"},
			},
			out: &struct{ S1, S2 []string }{
				S1: []string{"string1"},
				S2: []string{"string2"},
			},
			order: Prepend,
		},
		{
			name: "Replace empty slice",
			dst: &struct{ S1, S2 []string }{
				S1: []string{"string1"},
				S2: []string{},
			},
			src: &struct{ S1, S2 []string }{
				S1: []string{},
				S2: []string{"string2"},
			},
			out: &struct{ S1, S2 []string }{
				S1: []string{},
				S2: []string{"string2"},
			},
			order: Replace,
		},
		{
			name: "Append nil slice",
			dst: &struct{ S1, S2, S3 []string }{
				S1: []string{"string1"},
			},
			src: &struct{ S1, S2, S3 []string }{
				S2: []string{"string2"},
			},
			out: &struct{ S1, S2, S3 []string }{
				S1: []string{"string1"},
				S2: []string{"string2"},
				S3: nil,
			},
		},
		{
			name: "Prepend nil slice",
			dst: &struct{ S1, S2, S3 []string }{
				S1: []string{"string1"},
			},
			src: &struct{ S1, S2, S3 []string }{
				S2: []string{"string2"},
			},
			out: &struct{ S1, S2, S3 []string }{
				S1: []string{"string1"},
				S2: []string{"string2"},
				S3: nil,
			},
			order: Prepend,
		},
		{
			name: "Replace nil slice",
			dst: &struct{ S1, S2, S3 []string }{
				S1: []string{"string1"},
			},
			src: &struct{ S1, S2, S3 []string }{
				S2: []string{"string2"},
			},
			out: &struct{ S1, S2, S3 []string }{
				S1: []string{"string1"},
				S2: []string{"string2"},
				S3: nil,
			},
			order: Replace,
		},
		{
			name: "Replace embedded slice",
			dst: &struct{ S *struct{ S1 []string } }{
				S: &struct{ S1 []string }{
					S1: []string{"string1"},
				},
			},
			src: &struct{ S *struct{ S1 []string } }{
				S: &struct{ S1 []string }{
					S1: []string{"string2"},
				},
			},
			out: &struct{ S *struct{ S1 []string } }{
				S: &struct{ S1 []string }{
					S1: []string{"string2"},
				},
			},
			order: Replace,
		},
		{
			name: "Append slice of structs",
			dst: &struct{ S []struct{ F string } }{
				S: []struct{ F string }{
					{F: "foo"}, {F: "bar"},
				},
			},
			src: &struct{ S []struct{ F string } }{
				S: []struct{ F string }{
					{F: "baz"},
				},
			},
			out: &struct{ S []struct{ F string } }{
				S: []struct{ F string }{
					{F: "foo"}, {F: "bar"}, {F: "baz"},
				},
			},
			order: Append,
		},
		{
			name: "Prepend slice of structs",
			dst: &struct{ S []struct{ F string } }{
				S: []struct{ F string }{
					{F: "foo"}, {F: "bar"},
				},
			},
			src: &struct{ S []struct{ F string } }{
				S: []struct{ F string }{
					{F: "baz"},
				},
			},
			out: &struct{ S []struct{ F string } }{
				S: []struct{ F string }{
					{F: "baz"}, {F: "foo"}, {F: "bar"},
				},
			},
			order: Prepend,
		},
		{
			name: "Append map",
			dst: &struct{ S map[string]string }{
				S: map[string]string{
					"key0": "",
					"key1": "dst_value1",
					"key2": "dst_value2",
				},
			},
			src: &struct{ S map[string]string }{
				S: map[string]string{
					"key0": "src_value0",
					"key1": "src_value1",
					"key3": "src_value3",
				},
			},
			out: &struct{ S map[string]string }{
				S: map[string]string{
					"key0": "src_value0",
					"key1": "src_value1",
					"key2": "dst_value2",
					"key3": "src_value3",
				},
			},
			order: Append,
		},
		{
			name: "Prepend map",
			dst: &struct{ S map[string]string }{
				S: map[string]string{
					"key0": "",
					"key1": "dst_value1",
					"key2": "dst_value2",
				},
			},
			src: &struct{ S map[string]string }{
				S: map[string]string{
					"key0": "src_value0",
					"key1": "src_value1",
					"key3": "src_value3",
				},
			},
			out: &struct{ S map[string]string }{
				S: map[string]string{
					"key0": "",
					"key1": "dst_value1",
					"key2": "dst_value2",
					"key3": "src_value3",
				},
			},
			order: Prepend,
		},
		{
			name: "Replace map",
			dst: &struct{ S map[string]string }{
				S: map[string]string{
					"key0": "",
					"key1": "dst_value1",
					"key2": "dst_value2",
				},
			},
			src: &struct{ S map[string]string }{
				S: map[string]string{
					"key0": "src_value0",
					"key1": "src_value1",
					"key3": "src_value3",
				},
			},
			out: &struct{ S map[string]string }{
				S: map[string]string{
					"key0": "src_value0",
					"key1": "src_value1",
					"key3": "src_value3",
				},
			},
			order: Replace,
		},
		{
			name: "Append empty map",
			dst: &struct{ S1, S2 map[string]string }{
				S1: map[string]string{"key0": "dst_value0"},
				S2: map[string]string{},
			},
			src: &struct{ S1, S2 map[string]string }{
				S1: map[string]string{},
				S2: map[string]string{"key0": "src_value0"},
			},
			out: &struct{ S1, S2 map[string]string }{
				S1: map[string]string{"key0": "dst_value0"},
				S2: map[string]string{"key0": "src_value0"},
			},
			order: Append,
		},
		{
			name: "Prepend empty map",
			dst: &struct{ S1, S2 map[string]string }{
				S1: map[string]string{"key0": "dst_value0"},
				S2: map[string]string{},
			},
			src: &struct{ S1, S2 map[string]string }{
				S1: map[string]string{},
				S2: map[string]string{"key0": "src_value0"},
			},
			out: &struct{ S1, S2 map[string]string }{
				S1: map[string]string{"key0": "dst_value0"},
				S2: map[string]string{"key0": "src_value0"},
			},
			order: Prepend,
		},
		{
			name: "Replace empty map",
			dst: &struct{ S1, S2 map[string]string }{
				S1: map[string]string{"key0": "dst_value0"},
				S2: map[string]string{},
			},
			src: &struct{ S1, S2 map[string]string }{
				S1: map[string]string{},
				S2: map[string]string{"key0": "src_value0"},
			},
			out: &struct{ S1, S2 map[string]string }{
				S1: map[string]string{},
				S2: map[string]string{"key0": "src_value0"},
			},
			order: Replace,
		},
		{
			name: "Append nil map",
			dst: &struct{ S1, S2, S3 map[string]string }{
				S1: map[string]string{"key0": "dst_value0"},
			},
			src: &struct{ S1, S2, S3 map[string]string }{
				S2: map[string]string{"key0": "src_value0"},
			},
			out: &struct{ S1, S2, S3 map[string]string }{
				S1: map[string]string{"key0": "dst_value0"},
				S2: map[string]string{"key0": "src_value0"},
			},
			order: Append,
		},
		{
			name: "Prepend nil map",
			dst: &struct{ S1, S2, S3 map[string]string }{
				S1: map[string]string{"key0": "dst_value0"},
			},
			src: &struct{ S1, S2, S3 map[string]string }{
				S2: map[string]string{"key0": "src_value0"},
			},
			out: &struct{ S1, S2, S3 map[string]string }{
				S1: map[string]string{"key0": "dst_value0"},
				S2: map[string]string{"key0": "src_value0"},
			},
			order: Prepend,
		},
		{
			name: "Replace nil map",
			dst: &struct{ S1, S2, S3 map[string]string }{
				S1: map[string]string{"key0": "dst_value0"},
			},
			src: &struct{ S1, S2, S3 map[string]string }{
				S2: map[string]string{"key0": "src_value0"},
			},
			out: &struct{ S1, S2, S3 map[string]string }{
				S1: map[string]string{"key0": "dst_value0"},
				S2: map[string]string{"key0": "src_value0"},
				S3: nil,
			},
			order: Replace,
		},
		{
			name: "Replace slice of structs",
			dst: &struct{ S []struct{ F string } }{
				S: []struct{ F string }{
					{F: "foo"}, {F: "bar"},
				},
			},
			src: &struct{ S []struct{ F string } }{
				S: []struct{ F string }{
					{F: "baz"},
				},
			},
			out: &struct{ S []struct{ F string } }{
				S: []struct{ F string }{
					{F: "baz"},
				},
			},
			order: Replace,
		},
		{
			name: "Append pointer",
			dst: &struct{ S *struct{ S string } }{
				S: &struct{ S string }{
					S: "string1",
				},
			},
			src: &struct{ S *struct{ S string } }{
				S: &struct{ S string }{
					S: "string2",
				},
			},
			out: &struct{ S *struct{ S string } }{
				S: &struct{ S string }{
					S: "string1string2",
				},
			},
		},
		{
			name: "Prepend pointer",
			dst: &struct{ S *struct{ S string } }{
				S: &struct{ S string }{
					S: "string1",
				},
			},
			src: &struct{ S *struct{ S string } }{
				S: &struct{ S string }{
					S: "string2",
				},
			},
			out: &struct{ S *struct{ S string } }{
				S: &struct{ S string }{
					S: "string2string1",
				},
			},
			order: Prepend,
		},
		{
			name: "Append interface",
			dst: &struct{ S interface{} }{
				S: &struct{ S string }{
					S: "string1",
				},
			},
			src: &struct{ S interface{} }{
				S: &struct{ S string }{
					S: "string2",
				},
			},
			out: &struct{ S interface{} }{
				S: &struct{ S string }{
					S: "string1string2",
				},
			},
		},
		{
			name: "Prepend interface",
			dst: &struct{ S interface{} }{
				S: &struct{ S string }{
					S: "string1",
				},
			},
			src: &struct{ S interface{} }{
				S: &struct{ S string }{
					S: "string2",
				},
			},
			out: &struct{ S interface{} }{
				S: &struct{ S string }{
					S: "string2string1",
				},
			},
			order: Prepend,
		},
		{
			name: "Unexported field",
			dst: &struct{ s string }{
				s: "string1",
			},
			src: &struct{ s string }{
				s: "string2",
			},
			out: &struct{ s string }{
				s: "string1",
			},
		},
		{
			name: "Unexported field",
			dst: &struct{ i *int64 }{
				i: Int64Ptr(33),
			},
			src: &struct{ i *int64 }{
				i: Int64Ptr(5),
			},
			out: &struct{ i *int64 }{
				i: Int64Ptr(33),
			},
		},
		{
			name: "Empty struct",
			dst:  &struct{}{},
			src:  &struct{}{},
			out:  &struct{}{},
		},
		{
			name: "Interface nil",
			dst: &struct{ S interface{} }{
				S: nil,
			},
			src: &struct{ S interface{} }{
				S: nil,
			},
			out: &struct{ S interface{} }{
				S: nil,
			},
		},
		{
			name: "Pointer nil",
			dst: &struct{ S *struct{} }{
				S: nil,
			},
			src: &struct{ S *struct{} }{
				S: nil,
			},
			out: &struct{ S *struct{} }{
				S: nil,
			},
		},
		{
			name: "Anonymous struct",
			dst: &struct {
				EmbeddedStruct
				Nested struct{ EmbeddedStruct }
			}{
				EmbeddedStruct: EmbeddedStruct{
					S: "string1",
					I: Int64Ptr(55),
				},
				Nested: struct{ EmbeddedStruct }{
					EmbeddedStruct: EmbeddedStruct{
						S: "string2",
						I: Int64Ptr(-4),
					},
				},
			},
			src: &struct {
				EmbeddedStruct
				Nested struct{ EmbeddedStruct }
			}{
				EmbeddedStruct: EmbeddedStruct{
					S: "string3",
					I: Int64Ptr(66),
				},
				Nested: struct{ EmbeddedStruct }{
					EmbeddedStruct: EmbeddedStruct{
						S: "string4",
						I: Int64Ptr(-8),
					},
				},
			},
			out: &struct {
				EmbeddedStruct
				Nested struct{ EmbeddedStruct }
			}{
				EmbeddedStruct: EmbeddedStruct{
					S: "string1string3",
					I: Int64Ptr(66),
				},
				Nested: struct{ EmbeddedStruct }{
					EmbeddedStruct: EmbeddedStruct{
						S: "string2string4",
						I: Int64Ptr(-8),
					},
				},
			},
		},
		{
			name: "BlueprintEmbed struct",
			dst: &struct {
				BlueprintEmbed EmbeddedStruct
				Nested         struct{ BlueprintEmbed EmbeddedStruct }
			}{
				BlueprintEmbed: EmbeddedStruct{
					S: "string1",
					I: Int64Ptr(55),
				},
				Nested: struct{ BlueprintEmbed EmbeddedStruct }{
					BlueprintEmbed: EmbeddedStruct{
						S: "string2",
						I: Int64Ptr(-4),
					},
				},
			},
			src: &struct {
				BlueprintEmbed EmbeddedStruct
				Nested         struct{ BlueprintEmbed EmbeddedStruct }
			}{
				BlueprintEmbed: EmbeddedStruct{
					S: "string3",
					I: Int64Ptr(66),
				},
				Nested: struct{ BlueprintEmbed EmbeddedStruct }{
					BlueprintEmbed: EmbeddedStruct{
						S: "string4",
						I: Int64Ptr(-8),
					},
				},
			},
			out: &struct {
				BlueprintEmbed EmbeddedStruct
				Nested         struct{ BlueprintEmbed EmbeddedStruct }
			}{
				BlueprintEmbed: EmbeddedStruct{
					S: "string1string3",
					I: Int64Ptr(66),
				},
				Nested: struct{ BlueprintEmbed EmbeddedStruct }{
					BlueprintEmbed: EmbeddedStruct{
						S: "string2string4",
						I: Int64Ptr(-8),
					},
				},
			},
		},
		{
			name: "Anonymous interface",
			dst: &struct {
				EmbeddedInterface
				Nested struct{ EmbeddedInterface }
			}{
				EmbeddedInterface: &struct {
					S string
					I *int64
				}{
					S: "string1",
					I: Int64Ptr(-8),
				},
				Nested: struct{ EmbeddedInterface }{
					EmbeddedInterface: &struct {
						S string
						I *int64
					}{
						S: "string2",
						I: Int64Ptr(55),
					},
				},
			},
			src: &struct {
				EmbeddedInterface
				Nested struct{ EmbeddedInterface }
			}{
				EmbeddedInterface: &struct {
					S string
					I *int64
				}{
					S: "string3",
					I: Int64Ptr(6),
				},
				Nested: struct{ EmbeddedInterface }{
					EmbeddedInterface: &struct {
						S string
						I *int64
					}{
						S: "string4",
						I: Int64Ptr(6),
					},
				},
			},
			out: &struct {
				EmbeddedInterface
				Nested struct{ EmbeddedInterface }
			}{
				EmbeddedInterface: &struct {
					S string
					I *int64
				}{
					S: "string1string3",
					I: Int64Ptr(6),
				},
				Nested: struct{ EmbeddedInterface }{
					EmbeddedInterface: &struct {
						S string
						I *int64
					}{
						S: "string2string4",
						I: Int64Ptr(6),
					},
				},
			},
		},
		{
			name: "Nil pointer to a struct",
			dst: &struct {
				Nested *struct {
					S string
				}
			}{},
			src: &struct {
				Nested *struct {
					S string
				}
			}{
				Nested: &struct {
					S string
				}{
					S: "string",
				},
			},
			out: &struct {
				Nested *struct {
					S string
				}
			}{
				Nested: &struct {
					S string
				}{
					S: "string",
				},
			},
		},
		{
			name: "Nil pointer to a struct in an interface",
			dst: &struct {
				Nested interface{}
			}{
				Nested: (*struct{ S string })(nil),
			},
			src: &struct {
				Nested interface{}
			}{
				Nested: &struct {
					S string
				}{
					S: "string",
				},
			},
			out: &struct {
				Nested interface{}
			}{
				Nested: &struct {
					S string
				}{
					S: "string",
				},
			},
		},
		{
			name: "Interface src nil",
			dst: &struct{ S interface{} }{
				S: &struct{ S string }{
					S: "string1",
				},
			},
			src: &struct{ S interface{} }{
				S: nil,
			},
			out: &struct{ S interface{} }{
				S: &struct{ S string }{
					S: "string1",
				},
			},
		},

		// Errors

		{
			name: "Non-pointer dst",
			dst:  struct{}{},
			src:  &struct{}{},
			err:  errors.New("expected pointer to struct, got struct {}"),
			out:  struct{}{},
		},
		{
			name: "Non-pointer src",
			dst:  &struct{}{},
			src:  struct{}{},
			err:  errors.New("expected pointer to struct, got struct {}"),
			out:  &struct{}{},
		},
		{
			name: "Non-struct dst",
			dst:  &[]string{"bad"},
			src:  &struct{}{},
			err:  errors.New("expected pointer to struct, got *[]string"),
			out:  &[]string{"bad"},
		},
		{
			name: "Non-struct src",
			dst:  &struct{}{},
			src:  &[]string{"bad"},
			err:  errors.New("expected pointer to struct, got *[]string"),
			out:  &struct{}{},
		},
		{
			name: "Mismatched types",
			dst: &struct{ A string }{
				A: "string1",
			},
			src: &struct{ B string }{
				B: "string2",
			},
			out: &struct{ A string }{
				A: "string1",
			},
			err: errors.New("expected matching types for dst and src, got *struct { A string } and *struct { B string }"),
		},
		{
			name: "Unsupported kind",
			dst: &struct{ I int }{
				I: 1,
			},
			src: &struct{ I int }{
				I: 2,
			},
			out: &struct{ I int }{
				I: 1,
			},
			err: extendPropertyErrorf("i", "unsupported kind int"),
		},
		{
			name: "Unsupported kind",
			dst: &struct{ I int64 }{
				I: 1,
			},
			src: &struct{ I int64 }{
				I: 2,
			},
			out: &struct{ I int64 }{
				I: 1,
			},
			err: extendPropertyErrorf("i", "unsupported kind int64"),
		},
		{
			name: "Interface nilitude mismatch",
			dst: &struct{ S interface{} }{
				S: nil,
			},
			src: &struct{ S interface{} }{
				S: &struct{ S string }{
					S: "string1",
				},
			},
			out: &struct{ S interface{} }{
				S: nil,
			},
			err: extendPropertyErrorf("s", "nilitude mismatch"),
		},
		{
			name: "Interface type mismatch",
			dst: &struct{ S interface{} }{
				S: &struct{ A string }{
					A: "string1",
				},
			},
			src: &struct{ S interface{} }{
				S: &struct{ B string }{
					B: "string2",
				},
			},
			out: &struct{ S interface{} }{
				S: &struct{ A string }{
					A: "string1",
				},
			},
			err: extendPropertyErrorf("s", "mismatched types struct { A string } and struct { B string }"),
		},
		{
			name: "Interface not a pointer",
			dst: &struct{ S interface{} }{
				S: struct{ S string }{
					S: "string1",
				},
			},
			src: &struct{ S interface{} }{
				S: struct{ S string }{
					S: "string2",
				},
			},
			out: &struct{ S interface{} }{
				S: struct{ S string }{
					S: "string1",
				},
			},
			err: extendPropertyErrorf("s", "interface not a pointer"),
		},
		{
			name: "Pointer not a struct",
			dst: &struct{ S *[]string }{
				S: &[]string{"string1"},
			},
			src: &struct{ S *[]string }{
				S: &[]string{"string2"},
			},
			out: &struct{ S *[]string }{
				S: &[]string{"string1"},
			},
			err: extendPropertyErrorf("s", "pointer is a slice"),
		},
		{
			name: "Error in nested struct",
			dst: &struct{ S interface{} }{
				S: &struct{ I int }{
					I: 1,
				},
			},
			src: &struct{ S interface{} }{
				S: &struct{ I int }{
					I: 2,
				},
			},
			out: &struct{ S interface{} }{
				S: &struct{ I int }{
					I: 1,
				},
			},
			err: extendPropertyErrorf("s.i", "unsupported kind int"),
		},

		// Filters

		{
			name: "Filter true",
			dst: &struct{ S string }{
				S: "string1",
			},
			src: &struct{ S string }{
				S: "string2",
			},
			out: &struct{ S string }{
				S: "string1string2",
			},
			filter: func(dstField, srcField reflect.StructField) (bool, error) {
				return true, nil
			},
		},
		{
			name: "Filter false",
			dst: &struct{ S string }{
				S: "string1",
			},
			src: &struct{ S string }{
				S: "string2",
			},
			out: &struct{ S string }{
				S: "string1",
			},
			filter: func(dstField, srcField reflect.StructField) (bool, error) {
				return false, nil
			},
		},
		{
			name: "Filter check args",
			dst: &struct{ S string }{
				S: "string1",
			},
			src: &struct{ S string }{
				S: "string2",
			},
			out: &struct{ S string }{
				S: "string1string2",
			},
			filter: func(dstField, srcField reflect.StructField) (bool, error) {
				return dstField.Name == "S" && srcField.Name == "S", nil
			},
		},
		{
			name: "Filter mutated",
			dst: &struct {
				S string `blueprint:"mutated"`
			}{
				S: "string1",
			},
			src: &struct {
				S string `blueprint:"mutated"`
			}{
				S: "string2",
			},
			out: &struct {
				S string `blueprint:"mutated"`
			}{
				S: "string1",
			},
		},
		{
			name: "Filter mutated",
			dst: &struct {
				S *int64 `blueprint:"mutated"`
			}{
				S: Int64Ptr(4),
			},
			src: &struct {
				S *int64 `blueprint:"mutated"`
			}{
				S: Int64Ptr(5),
			},
			out: &struct {
				S *int64 `blueprint:"mutated"`
			}{
				S: Int64Ptr(4),
			},
		},
		{
			name: "Filter error",
			dst: &struct{ S string }{
				S: "string1",
			},
			src: &struct{ S string }{
				S: "string2",
			},
			out: &struct{ S string }{
				S: "string1",
			},
			filter: func(dstField, srcField reflect.StructField) (bool, error) {
				return true, fmt.Errorf("filter error")
			},
			err: extendPropertyErrorf("s", "filter error"),
		},
	}
}

func TestAppendProperties(t *testing.T) {
	for _, testCase := range appendPropertiesTestCases() {
		t.Run(testCase.name, func(t *testing.T) {

			got := testCase.dst
			var err error
			var testType string

			switch testCase.order {
			case Append:
				testType = "append"
				err = AppendProperties(got, testCase.src, testCase.filter)
			case Prepend:
				testType = "prepend"
				err = PrependProperties(got, testCase.src, testCase.filter)
			case Replace:
				testType = "replace"
				err = ExtendProperties(got, testCase.src, testCase.filter, OrderReplace)
			}

			check(t, testType, testCase.name, got, err, testCase.out, testCase.err)
		})
	}
}

func TestExtendProperties(t *testing.T) {
	for _, testCase := range appendPropertiesTestCases() {
		t.Run(testCase.name, func(t *testing.T) {

			got := testCase.dst
			var err error
			var testType string

			order := func(dstField, srcField reflect.StructField) (Order, error) {
				switch testCase.order {
				case Append:
					return Append, nil
				case Prepend:
					return Prepend, nil
				case Replace:
					return Replace, nil
				}
				return Append, errors.New("unknown order")
			}

			switch testCase.order {
			case Append:
				testType = "prepend"
			case Prepend:
				testType = "append"
			case Replace:
				testType = "replace"
			}

			err = ExtendProperties(got, testCase.src, testCase.filter, order)

			check(t, testType, testCase.name, got, err, testCase.out, testCase.err)
		})
	}
}

type appendMatchingPropertiesTestCase struct {
	name   string
	dst    []interface{}
	src    interface{}
	out    []interface{}
	order  Order // default is Append
	filter ExtendPropertyFilterFunc
	err    error
}

func appendMatchingPropertiesTestCases() []appendMatchingPropertiesTestCase {
	return []appendMatchingPropertiesTestCase{
		{
			name: "Append strings",
			dst: []interface{}{&struct{ S string }{
				S: "string1",
			}},
			src: &struct{ S string }{
				S: "string2",
			},
			out: []interface{}{&struct{ S string }{
				S: "string1string2",
			}},
		},
		{
			name: "Prepend strings",
			dst: []interface{}{&struct{ S string }{
				S: "string1",
			}},
			src: &struct{ S string }{
				S: "string2",
			},
			out: []interface{}{&struct{ S string }{
				S: "string2string1",
			}},
			order: Prepend,
		},
		{
			name: "Append all",
			dst: []interface{}{
				&struct{ S, A string }{
					S: "string1",
				},
				&struct{ S, B string }{
					S: "string2",
				},
			},
			src: &struct{ S string }{
				S: "string3",
			},
			out: []interface{}{
				&struct{ S, A string }{
					S: "string1string3",
				},
				&struct{ S, B string }{
					S: "string2string3",
				},
			},
		},
		{
			name: "Append some",
			dst: []interface{}{
				&struct{ S, A string }{
					S: "string1",
				},
				&struct{ B string }{},
			},
			src: &struct{ S string }{
				S: "string2",
			},
			out: []interface{}{
				&struct{ S, A string }{
					S: "string1string2",
				},
				&struct{ B string }{},
			},
		},
		{
			name: "Append mismatched structs",
			dst: []interface{}{&struct{ S, A string }{
				S: "string1",
			}},
			src: &struct{ S string }{
				S: "string2",
			},
			out: []interface{}{&struct{ S, A string }{
				S: "string1string2",
			}},
		},
		{
			name: "Append mismatched pointer structs",
			dst: []interface{}{&struct{ S *struct{ S, A string } }{
				S: &struct{ S, A string }{
					S: "string1",
				},
			}},
			src: &struct{ S *struct{ S string } }{
				S: &struct{ S string }{
					S: "string2",
				},
			},
			out: []interface{}{&struct{ S *struct{ S, A string } }{
				S: &struct{ S, A string }{
					S: "string1string2",
				},
			}},
		},
		{
			name: "Append through mismatched types",
			dst: []interface{}{
				&struct{ B string }{},
				&struct{ S interface{} }{
					S: &struct{ S, A string }{
						S: "string1",
					},
				},
			},
			src: &struct{ S struct{ S string } }{
				S: struct{ S string }{
					S: "string2",
				},
			},
			out: []interface{}{
				&struct{ B string }{},
				&struct{ S interface{} }{
					S: &struct{ S, A string }{
						S: "string1string2",
					},
				},
			},
		},
		{
			name: "Append through mismatched types and nil",
			dst: []interface{}{
				&struct{ B string }{},
				&struct{ S interface{} }{
					S: (*struct{ S, A string })(nil),
				},
			},
			src: &struct{ S struct{ S string } }{
				S: struct{ S string }{
					S: "string2",
				},
			},
			out: []interface{}{
				&struct{ B string }{},
				&struct{ S interface{} }{
					S: &struct{ S, A string }{
						S: "string2",
					},
				},
			},
		},
		{
			name: "Append through multiple matches",
			dst: []interface{}{
				&struct {
					S struct{ S, A string }
				}{
					S: struct{ S, A string }{
						S: "string1",
					},
				},
				&struct {
					S struct{ S, B string }
				}{
					S: struct{ S, B string }{
						S: "string2",
					},
				},
			},
			src: &struct{ S struct{ B string } }{
				S: struct{ B string }{
					B: "string3",
				},
			},
			out: []interface{}{
				&struct {
					S struct{ S, A string }
				}{
					S: struct{ S, A string }{
						S: "string1",
					},
				},
				&struct {
					S struct{ S, B string }
				}{
					S: struct{ S, B string }{
						S: "string2",
						B: "string3",
					},
				},
			},
		},
		{
			name: "Append through embedded struct",
			dst: []interface{}{
				&struct{ B string }{},
				&struct{ EmbeddedStruct }{
					EmbeddedStruct: EmbeddedStruct{
						S: "string1",
					},
				},
			},
			src: &struct{ S string }{
				S: "string2",
			},
			out: []interface{}{
				&struct{ B string }{},
				&struct{ EmbeddedStruct }{
					EmbeddedStruct: EmbeddedStruct{
						S: "string1string2",
					},
				},
			},
		},
		{
			name: "Append through BlueprintEmbed struct",
			dst: []interface{}{
				&struct{ B string }{},
				&struct{ BlueprintEmbed EmbeddedStruct }{
					BlueprintEmbed: EmbeddedStruct{
						S: "string1",
					},
				},
			},
			src: &struct{ S string }{
				S: "string2",
			},
			out: []interface{}{
				&struct{ B string }{},
				&struct{ BlueprintEmbed EmbeddedStruct }{
					BlueprintEmbed: EmbeddedStruct{
						S: "string1string2",
					},
				},
			},
		},
		{
			name: "Append through embedded pointer to struct",
			dst: []interface{}{
				&struct{ B string }{},
				&struct{ *EmbeddedStruct }{
					EmbeddedStruct: &EmbeddedStruct{
						S: "string1",
					},
				},
			},
			src: &struct{ S string }{
				S: "string2",
			},
			out: []interface{}{
				&struct{ B string }{},
				&struct{ *EmbeddedStruct }{
					EmbeddedStruct: &EmbeddedStruct{
						S: "string1string2",
					},
				},
			},
		},
		{
			name: "Append through BlueprintEmbed pointer to struct",
			dst: []interface{}{
				&struct{ B string }{},
				&struct{ BlueprintEmbed *EmbeddedStruct }{
					BlueprintEmbed: &EmbeddedStruct{
						S: "string1",
					},
				},
			},
			src: &struct{ S string }{
				S: "string2",
			},
			out: []interface{}{
				&struct{ B string }{},
				&struct{ BlueprintEmbed *EmbeddedStruct }{
					BlueprintEmbed: &EmbeddedStruct{
						S: "string1string2",
					},
				},
			},
		},
		{
			name: "Append through embedded nil pointer to struct",
			dst: []interface{}{
				&struct{ B string }{},
				&struct{ *EmbeddedStruct }{},
			},
			src: &struct{ S string }{
				S: "string2",
			},
			out: []interface{}{
				&struct{ B string }{},
				&struct{ *EmbeddedStruct }{
					EmbeddedStruct: &EmbeddedStruct{
						S: "string2",
					},
				},
			},
		},
		{
			name: "Append through BlueprintEmbed nil pointer to struct",
			dst: []interface{}{
				&struct{ B string }{},
				&struct{ BlueprintEmbed *EmbeddedStruct }{},
			},
			src: &struct{ S string }{
				S: "string2",
			},
			out: []interface{}{
				&struct{ B string }{},
				&struct{ BlueprintEmbed *EmbeddedStruct }{
					BlueprintEmbed: &EmbeddedStruct{
						S: "string2",
					},
				},
			},
		},

		// Errors

		{
			name: "Non-pointer dst",
			dst:  []interface{}{struct{}{}},
			src:  &struct{}{},
			err:  errors.New("expected pointer to struct, got struct {}"),
			out:  []interface{}{struct{}{}},
		},
		{
			name: "Non-pointer src",
			dst:  []interface{}{&struct{}{}},
			src:  struct{}{},
			err:  errors.New("expected pointer to struct, got struct {}"),
			out:  []interface{}{&struct{}{}},
		},
		{
			name: "Non-struct dst",
			dst:  []interface{}{&[]string{"bad"}},
			src:  &struct{}{},
			err:  errors.New("expected pointer to struct, got *[]string"),
			out:  []interface{}{&[]string{"bad"}},
		},
		{
			name: "Non-struct src",
			dst:  []interface{}{&struct{}{}},
			src:  &[]string{"bad"},
			err:  errors.New("expected pointer to struct, got *[]string"),
			out:  []interface{}{&struct{}{}},
		},
		{
			name: "Append none",
			dst: []interface{}{
				&struct{ A string }{},
				&struct{ B string }{},
			},
			src: &struct{ S string }{
				S: "string1",
			},
			out: []interface{}{
				&struct{ A string }{},
				&struct{ B string }{},
			},
			err: extendPropertyErrorf("s", "failed to find property to extend"),
		},
		{
			name: "Append mismatched kinds",
			dst: []interface{}{
				&struct{ S string }{
					S: "string1",
				},
			},
			src: &struct{ S []string }{
				S: []string{"string2"},
			},
			out: []interface{}{
				&struct{ S string }{
					S: "string1",
				},
			},
			err: extendPropertyErrorf("s", "mismatched types string and []string"),
		},
		{
			name: "Append mismatched types",
			dst: []interface{}{
				&struct{ S []int }{
					S: []int{1},
				},
			},
			src: &struct{ S []string }{
				S: []string{"string2"},
			},
			out: []interface{}{
				&struct{ S []int }{
					S: []int{1},
				},
			},
			err: extendPropertyErrorf("s", "mismatched types []int and []string"),
		},
	}
}

func TestAppendMatchingProperties(t *testing.T) {
	for _, testCase := range appendMatchingPropertiesTestCases() {
		t.Run(testCase.name, func(t *testing.T) {

			got := testCase.dst
			var err error
			var testType string

			switch testCase.order {
			case Append:
				testType = "append"
				err = AppendMatchingProperties(got, testCase.src, testCase.filter)
			case Prepend:
				testType = "prepend"
				err = PrependMatchingProperties(got, testCase.src, testCase.filter)
			case Replace:
				testType = "replace"
				err = ExtendMatchingProperties(got, testCase.src, testCase.filter, OrderReplace)
			}

			check(t, testType, testCase.name, got, err, testCase.out, testCase.err)
		})
	}
}

func TestExtendMatchingProperties(t *testing.T) {
	for _, testCase := range appendMatchingPropertiesTestCases() {
		t.Run(testCase.name, func(t *testing.T) {

			got := testCase.dst
			var err error
			var testType string

			order := func(dstField, srcField reflect.StructField) (Order, error) {
				switch testCase.order {
				case Append:
					return Append, nil
				case Prepend:
					return Prepend, nil
				case Replace:
					return Replace, nil
				}
				return Append, errors.New("unknown order")
			}

			switch testCase.order {
			case Append:
				testType = "prepend matching"
			case Prepend:
				testType = "append matching"
			case Replace:
				testType = "replace matching"
			}

			err = ExtendMatchingProperties(got, testCase.src, testCase.filter, order)

			check(t, testType, testCase.name, got, err, testCase.out, testCase.err)
		})
	}
}

func check(t *testing.T, testType, testString string,
	got interface{}, err error,
	expected interface{}, expectedErr error) {

	printedTestCase := false
	e := func(s string, expected, got interface{}) {
		if !printedTestCase {
			t.Errorf("test case %s: %s", testType, testString)
			printedTestCase = true
		}
		t.Errorf("incorrect %s", s)
		t.Errorf("  expected: %s", p(expected))
		t.Errorf("       got: %s", p(got))
	}

	if err != nil {
		if expectedErr != nil {
			if err.Error() != expectedErr.Error() {
				e("unexpected error", expectedErr.Error(), err.Error())
			}
		} else {
			e("unexpected error", nil, err.Error())
		}
	} else {
		if expectedErr != nil {
			e("missing error", expectedErr, nil)
		}
	}

	if !reflect.DeepEqual(expected, got) {
		e("output:", expected, got)
	}
}

func p(in interface{}) string {
	if v, ok := in.([]interface{}); ok {
		s := make([]string, len(v))
		for i := range v {
			s[i] = fmt.Sprintf("%#v", v[i])
		}
		return "[" + strings.Join(s, ", ") + "]"
	} else {
		return fmt.Sprintf("%#v", in)
	}
}
