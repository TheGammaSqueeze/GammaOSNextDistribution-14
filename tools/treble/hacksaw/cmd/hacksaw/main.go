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

// hacksaw let's you create lightweight workspaces of large codebases
package main

import (
	"fmt"
	"net"
	"net/http"
	"net/rpc"
	"os"
	"os/exec"
	"os/user"
	"path/filepath"
	"strconv"
	"syscall"

	"android.googlesource.com/platform/tools/treble.git/hacksaw/bind"
	"android.googlesource.com/platform/tools/treble.git/hacksaw/client"
)

const hacksawdSocketPath = "/var/run/hacksaw.sock"
const tmpSocketPath = "/tmp/hacksaw.sock"

func getPathBinder() bind.PathBinder {
	if os.Geteuid() == 0 {
		// Called by root without SUDO_USER
		// Most likely in a mount namepace
		return bind.NewLocalPathBinder()
	}
	_, err := os.Stat(tmpSocketPath)
	if err == nil {
		return bind.NewRemoteBindClient(tmpSocketPath)
	} else {
		return bind.NewRemoteBindClient(hacksawdSocketPath)
	}
}

func getWorkspaceTopDir() (string, error) {
	home, err := os.UserHomeDir()
	if err != nil {
		return "", err
	}
	// The hacksaw mount daemon requires all mounts
	// to be contained in a directory named "hacksaw"
	topDir := filepath.Join(home, "hacksaw")
	_, err = os.Stat(topDir)
	if err == nil {
		// expected case
	} else if os.IsNotExist(err) {
		return topDir, nil
	} else {
		return "", err
	}
	topDir, err = filepath.EvalSymlinks(topDir)
	if err != nil {
		return "", err
	}
	return topDir, nil
}

func dropPrivileges(sudoUser string, socketPath string) error {
	usr, err := user.Lookup(sudoUser)
	if err != nil {
		return err
	}
	sudoUid, err := strconv.ParseUint(usr.Uid, 10, 32)
	if err != nil {
		return err
	}
	sudoGid, err := strconv.ParseUint(usr.Gid, 10, 32)
	if err != nil {
		return err
	}
	if err = os.Chown(socketPath, int(sudoUid), int(sudoGid)); err != nil {
		return err
	}
	cmd := exec.Command(os.Args[0], os.Args[1:]...)
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	cmd.Env = append(os.Environ(), "HOME="+usr.HomeDir)
	cmd.SysProcAttr = &syscall.SysProcAttr{
		Credential: &syscall.Credential{
			Uid: uint32(sudoUid),
			Gid: uint32(sudoGid),
		},
		Setsid: true,
	}
	if err := cmd.Start(); err != nil {
		return err
	}
	_, err = cmd.Process.Wait()
	if err != nil {
		return err
	}
	if err = cmd.Process.Release(); err != nil {
		return err
	}
	return nil
}

func createPathBinderListener(socketPath string) (net.Listener, error) {
	listener, err := net.Listen("unix", socketPath)
	if err != nil {
		return nil, err
	}

	binder := bind.NewLocalPathBinder()
	server := bind.NewServer(binder)
	if err = rpc.Register(server); err != nil {
		return nil, err
	}
	rpc.HandleHTTP()
	return listener, nil
}

func handleSudoUser(sudoUser string) error {
	if err := os.RemoveAll(tmpSocketPath); err != nil {
		return err
	}
	listener, err := createPathBinderListener(tmpSocketPath)
	if err != nil {
		return err
	}
	defer os.RemoveAll(tmpSocketPath)
	go http.Serve(listener, nil)
	return dropPrivileges(sudoUser, tmpSocketPath)
}

func run(args []string) error {
	sudoUser := os.Getenv("SUDO_USER")
	if os.Geteuid() == 0 && sudoUser != "" {
		return handleSudoUser(sudoUser)
	}
	workspaceTopDir, err := getWorkspaceTopDir()
	if err != nil {
		return err
	}
	pathBinder := getPathBinder()
	return client.HandleCommand(workspaceTopDir, pathBinder, args)
}

func main() {
	if err := run(os.Args); err != nil {
		fmt.Println("Error:", err)
		os.Exit(1)
	}
}
