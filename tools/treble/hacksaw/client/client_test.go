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

package client

import (
	"io/ioutil"
	"path"
	"testing"

	"android.googlesource.com/platform/tools/treble.git/hacksaw/bind"
)

func TestClientHelp(t *testing.T) {
	wsTempDir, err := ioutil.TempDir("", "workspace")
	if err != nil {
		t.Error(err)
	}
	wsTopDir := path.Join(wsTempDir, "hacksaw")
	pathBinder := bind.NewFakePathBinder()
	args := []string{"hacksaw", "help"}
	if err = HandleCommand(wsTopDir, pathBinder, args); err != nil {
		t.Error(err)
	}
}
