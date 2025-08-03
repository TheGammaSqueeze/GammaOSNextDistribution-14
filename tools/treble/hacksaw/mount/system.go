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
	"bufio"
	"io"
	"os"
	"strings"
	"syscall"
)

type systemMounter struct {
}

func NewSystemMounter() *systemMounter {
	var f systemMounter
	return &f
}

func (f *systemMounter) Mount(source string, target string, fstype string, flags uintptr, data string) error {
	return syscall.Mount(source, target, fstype, flags, data)
}

func (f *systemMounter) Unmount(target string, flags int) error {
	return syscall.Unmount(target, flags)
}

func (f *systemMounter) List() ([]string, error) {
	mountsFile, err := os.Open("/proc/mounts")
	if err != nil {
		return nil, err
	}
	defer mountsFile.Close()
	mounts, err := f.parseMounts(mountsFile)
	if err != nil {
		return nil, err
	}
	var mountList []string
	for _, mount := range mounts {
		mountList = append(mountList, mount.Path)
	}
	return mountList, err
}

type Mount struct {
	Device string
	Path   string
	Type   string
	Opts   string
}

func (f *systemMounter) parseMounts(mountSource io.Reader) ([]Mount, error) {
	var mounts []Mount
	scanner := bufio.NewScanner(mountSource)
	for scanner.Scan() {
		line := scanner.Text()
		fields := strings.Fields(line)
		mount := Mount{
			Device: fields[0],
			Path:   fields[1],
			Type:   fields[2],
			Opts:   fields[3],
		}
		mounts = append(mounts, mount)
	}
	return mounts, scanner.Err()
}
