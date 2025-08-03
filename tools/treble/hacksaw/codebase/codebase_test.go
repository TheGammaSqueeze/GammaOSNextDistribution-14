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

package codebase

import (
	"io/ioutil"
	"os"
	"path"
	"reflect"
	"testing"

	"android.googlesource.com/platform/tools/treble.git/hacksaw/config"
)

func TestAdd(t *testing.T) {
	defer config.GetConfig().Reset()
	firstCodebaseDir, err := ioutil.TempDir("", "first")
	if err != nil {
		t.Error(err)
	}
	defer os.RemoveAll(firstCodebaseDir)
	outputConfig, err := Add("first-codebase", firstCodebaseDir)
	if err != nil {
		t.Error(err)
	}
	expectedConfig := config.Config{
		DefaultCodebase: "",
		Codebases: map[string]string{
			"first-codebase": firstCodebaseDir,
		},
		Workspaces: map[string]string{}}
	if !reflect.DeepEqual(expectedConfig, *outputConfig) {
		t.Errorf("Expected config %v different than output config %v",
			expectedConfig, *outputConfig)
	}

	secondCodebaseDir, err := ioutil.TempDir("", "second")
	if err != nil {
		t.Error(err)
	}
	defer os.RemoveAll(secondCodebaseDir)
	if outputConfig, err = Add("second-codebase", secondCodebaseDir); err != nil {
		t.Error(err)
	}
	expectedConfig = config.Config{
		DefaultCodebase: "",
		Codebases: map[string]string{
			"first-codebase":  firstCodebaseDir,
			"second-codebase": secondCodebaseDir,
		},
		Workspaces: map[string]string{}}
	if !reflect.DeepEqual(expectedConfig, *outputConfig) {
		t.Errorf("Expected config %v different than output config %v",
			expectedConfig, *outputConfig)
	}
}

func TestAddDuplicate(t *testing.T) {
	defer config.GetConfig().Reset()
	codebaseDir, err := ioutil.TempDir("", "first")
	if err != nil {
		t.Error(err)
	}
	defer os.RemoveAll(codebaseDir)
	_, err = Add("codebase", codebaseDir)
	if err != nil {
		t.Error(err)
	}
	_, err = Add("codebase", codebaseDir)
	if err == nil {
		t.Error("Allowed duplicate codebase")
	}
}

func TestAddInvalidCodebase(t *testing.T) {
	defer config.GetConfig().Reset()
	tempDir, err := ioutil.TempDir("", "codebase")
	if err != nil {
		t.Error(err)
	}
	defer os.RemoveAll(tempDir)
	testCodebaseDir := path.Join(tempDir, "doesnotexist")
	_, err = Add("test-codebase", testCodebaseDir)
	if err == nil {
		t.Error("Adding a codebase with an invalid directory should not be allowed")
	}
}

func TestList(t *testing.T) {
	defer config.GetConfig().Reset()
	firstCodebaseDir, err := ioutil.TempDir("", "first")
	if err != nil {
		t.Error(err)
	}
	defer os.RemoveAll(firstCodebaseDir)
	_, err = Add("first-codebase", firstCodebaseDir)
	if err != nil {
		t.Error(err)
	}
	secondCodebaseDir, err := ioutil.TempDir("", "second")
	if err != nil {
		t.Error(err)
	}
	defer os.RemoveAll(secondCodebaseDir)
	if _, err = Add("second-codebase", secondCodebaseDir); err != nil {
		t.Error(err)
	}
	list := List()
	expectedList := map[string]string{
		"first-codebase":  firstCodebaseDir,
		"second-codebase": secondCodebaseDir,
	}
	if !reflect.DeepEqual(expectedList, list) {
		t.Errorf("Codebase list %v is different than expected list %v",
			list, expectedList)
	}
}

func TestSetGetDefault(t *testing.T) {
	defer config.GetConfig().Reset()
	firstCodebaseDir, err := ioutil.TempDir("", "first")
	if err != nil {
		t.Error(err)
	}
	defer os.RemoveAll(firstCodebaseDir)
	_, err = Add("first-codebase", firstCodebaseDir)
	if err != nil {
		t.Error(err)
	}
	secondCodebaseDir, err := ioutil.TempDir("", "second")
	if err != nil {
		t.Error(err)
	}
	defer os.RemoveAll(secondCodebaseDir)
	_, err = Add("second-codebase", secondCodebaseDir)
	if err != nil {
		t.Error(err)
	}
	if err = SetDefault("second-codebase"); err != nil {
		t.Error(err)
	}
	def := Default()
	if def != "second-codebase" {
		t.Error("Setting default codebase failed")
	}
}

func TestBadDefault(t *testing.T) {
	defer config.GetConfig().Reset()
	testCodebaseDir, err := ioutil.TempDir("", "test")
	if err != nil {
		t.Error(err)
	}
	defer os.RemoveAll(testCodebaseDir)
	_, err = Add("test-codebase", testCodebaseDir)
	if err != nil {
		t.Error(err)
	}
	if err = SetDefault("does-not-exist"); err == nil {
		t.Error("Allowed invalid default codebase")
	}
}

func TestRemove(t *testing.T) {
	defer config.GetConfig().Reset()
	testCodebaseDir, err := ioutil.TempDir("", "test")
	if err != nil {
		t.Error(err)
	}
	defer os.RemoveAll(testCodebaseDir)
	_, err = Add("remove-me", testCodebaseDir)
	if err != nil {
		t.Error(err)
	}
	SetDefault("remove-me")
	cfg, err := Remove("remove-me")
	if err != nil {
		t.Error(err)
	}
	_, ok := cfg.Codebases["remove-me"]
	if ok {
		t.Error("Removed codebase is still in the configuration")
	}
	if cfg.DefaultCodebase != "" {
		t.Error("Removing the default codebase did not reset the default")
	}
}

func TestBadRemove(t *testing.T) {
	defer config.GetConfig().Reset()
	testCodebaseDir, err := ioutil.TempDir("", "test")
	if err != nil {
		t.Error(err)
	}
	defer os.RemoveAll(testCodebaseDir)
	_, err = Add("remove-me", testCodebaseDir)
	if err != nil {
		t.Error(err)
	}
	_, err = Remove("does-not-exist")
	if err == nil {
		t.Error("Attempt to remove an unexistant codebase",
			"should have failed")
	}
}
