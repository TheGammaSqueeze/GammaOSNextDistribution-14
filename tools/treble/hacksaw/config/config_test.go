// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package config

import (
	"bytes"
	"reflect"
	"testing"
)

const testWriteExpectedOutput = `{
 "DefaultCodebase": "test-codebase",
 "Codebases": {
  "test-codebase": "/path/to/test/codebase"
 },
 "Workspaces": {}
}`

func TestWrite(t *testing.T) {
	inputConfig := Config{
		DefaultCodebase: "test-codebase",
		Codebases: map[string]string{
			"test-codebase": "/path/to/test/codebase",
		},
		Workspaces: map[string]string{}}
	var outputBuffer bytes.Buffer
	if err := inputConfig.Write(&outputBuffer); err != nil {
		t.Error(err)
	}
	expectedOutput := []byte(testWriteExpectedOutput)
	if bytes.Compare(outputBuffer.Bytes(), expectedOutput) != 0 {
		t.Errorf("Output %s is different that expected output %s",
			string(outputBuffer.Bytes()), string(expectedOutput))
	}
}

const testReadInput = `{
 "DefaultCodebase": "test-codebase",
 "Codebases": {
  "test-codebase": "/path/to/test/codebase"
 },
 "Workspaces": {}
}`

func TestRead(t *testing.T) {
	inputBytes := []byte(testReadInput)
	inputBuffer := bytes.NewBuffer(inputBytes)
	var outputConfig Config
	if err := outputConfig.Read(inputBuffer); err != nil {
		t.Error(err)
	}
	expectedOutput := Config{
		DefaultCodebase: "test-codebase",
		Codebases: map[string]string{
			"test-codebase": "/path/to/test/codebase",
		},
		Workspaces: map[string]string{}}
	if !reflect.DeepEqual(outputConfig, expectedOutput) {
		t.Errorf("Written config %v is different than read config %v",
			outputConfig, expectedOutput)
	}
}
