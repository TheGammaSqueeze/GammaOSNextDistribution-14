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

package mount

import (
	"fmt"
)

type MountEntry struct {
	Source string
	FSType string
	Flags  uintptr
	Data   string
}

type fakeMounter struct {
	mountMap map[string]MountEntry //keyed by target
}

func NewFakeMounter() *fakeMounter {
	var f fakeMounter
	f.mountMap = make(map[string]MountEntry)
	return &f
}

func (f *fakeMounter) Mount(source string, target string, fstype string, flags uintptr, data string) error {
	//Using the target as the key prevents more
	//than one source mapping to the same target
	f.mountMap[target] = MountEntry{
		Source: source,
		FSType: fstype,
		Flags:  flags,
		Data:   data,
	}
	return nil
}

func (f *fakeMounter) Unmount(target string, flags int) error {
	_, ok := f.mountMap[target]
	if !ok {
		return fmt.Errorf("Mount %s not found", target)
	}
	delete(f.mountMap, target)
	return nil
}

func (f *fakeMounter) List() ([]string, error) {
	var list []string
	for target, _ := range f.mountMap {
		list = append(list, target)
	}

	return list, nil
}
