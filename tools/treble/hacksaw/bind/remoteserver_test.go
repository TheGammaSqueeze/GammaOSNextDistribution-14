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

package bind

import (
	"io/ioutil"
	"os"
	"path"
	"reflect"
	"testing"
)

func TestServerBind(t *testing.T) {
	mountTempDir, err := ioutil.TempDir("", "mount")
	if err != nil {
		t.Error(err)
	}
	fakeBinder := NewFakePathBinder()
	server := NewServer(fakeBinder)
	roSourceDir := path.Join(mountTempDir, "path/to/readonly/source")
	if err = os.MkdirAll(roSourceDir, os.ModePerm); err != nil {
		t.Error(err)
	}
	roDestDir := path.Join(mountTempDir, "path/to/hacksaw/readonly/destination")
	if err = os.MkdirAll(roDestDir, os.ModePerm); err != nil {
		t.Error(err)
	}
	bindROArgs := BindReadOnlyArgs{
		Source:      roSourceDir,
		Destination: roDestDir,
	}
	var bindROReply BindReadOnlyReply
	if err := server.BindReadOnly(&bindROArgs, &bindROReply); err != nil {
		t.Error(err)
	}
	if bindROReply.Err != "" {
		t.Error(bindROReply.Err)
	}
	rwSourceDir := path.Join(mountTempDir, "path/to/readwrite/source")
	if err = os.MkdirAll(rwSourceDir, os.ModePerm); err != nil {
		t.Error(err)
	}
	rwDestDir := path.Join(mountTempDir, "path/to/hacksaw/readwrite/destination")
	if err = os.MkdirAll(rwDestDir, os.ModePerm); err != nil {
		t.Error(err)
	}
	bindRWArgs := BindReadWriteArgs{
		Source:      rwSourceDir,
		Destination: rwDestDir,
	}
	var bindRWReply BindReadWriteReply
	if err := server.BindReadWrite(&bindRWArgs, &bindRWReply); err != nil {
		t.Error(err)
	}
	if bindRWReply.Err != "" {
		t.Error(bindRWReply.Err)
	}
	var listArgs ListArgs
	var listReply ListReply
	err = server.List(&listArgs, &listReply)
	if err != nil {
		t.Error(err)
	}
	if listReply.Err != "" {
		t.Error(listReply.Err)
	}
	expectedList := []string{
		roDestDir,
		rwDestDir,
	}
	if !reflect.DeepEqual(listReply.BindList, expectedList) {
		t.Errorf("Bind list %v is different than expected bind %v",
			listReply.BindList, expectedList)
	}
	unbindArgs := UnbindArgs{
		Destination: rwDestDir,
	}
	var unbindReply UnbindReply
	if err := server.Unbind(&unbindArgs, &unbindReply); err != nil {
		t.Error(err)
	}
	if unbindReply.Err != "" {
		t.Error(unbindReply.Err)
	}
	err = server.List(&listArgs, &listReply)
	if err != nil {
		t.Error(err)
	}
	if listReply.Err != "" {
		t.Error(listReply.Err)
	}
	expectedList = []string{
		roDestDir,
	}
	if !reflect.DeepEqual(listReply.BindList, expectedList) {
		t.Errorf("Bind list %v is different than expected bind %v",
			listReply.BindList, expectedList)
	}
}
