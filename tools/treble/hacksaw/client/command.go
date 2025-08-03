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
	"fmt"

	"android.googlesource.com/platform/tools/treble.git/hacksaw/bind"
	"android.googlesource.com/platform/tools/treble.git/hacksaw/codebase"
	"android.googlesource.com/platform/tools/treble.git/hacksaw/workspace"
)

const usage = `Usage: hacksaw <command> <options>

Commands:
	help: show this message
	codebase add <name> <path>: Add codebase to hacksaw's list.
	codebase list: List all codebases that hacksaw knows about.
	codebase default <name>: Change the default codebase.
	codebase remove <name>: Remove a codebase from hacksaw's list.
	workspace new <workspace_name> <codebase_name>: Create a new workspace
	  from a codebase. The codebase is optional if a default codebase
	  has already been set.
	workspace recreate <name>: Recreate an existing workspace.
	  This will recreate any read-only bind mounts which may be necessary
	  when a machine is rebooted.
	workspace list: List all known workspaces.
	workspace remove <name>: Remove a workspace.
	edit <path>: Make a workspace path editable by checking out the parent git project.`

type Command struct {
	workspace workspace.Workspace
}

func NewCommand(bm bind.PathBinder, wtd string) Command {
	return Command{workspace.New(bm, wtd)}
}

func (c Command) addCodebase(args []string) error {
	if len(args) < 5 {
		return fmt.Errorf("Codebase name and path are required\n"+
			"Usage: %s %s %s <codebase_name> <path>",
			args[0], args[1], args[2])
	}
	name := args[3]
	path := args[4]
	_, err := codebase.Add(name, path)
	if err != nil {
		return err
	}
	fmt.Println("Added codebase", name)
	return err
}

func (c Command) defaultCodebase(args []string) error {
	if len(args) < 4 {
		return fmt.Errorf("Codebase name is required\n"+
			"Usage: %s %s %s <codebase_name>",
			args[0], args[1], args[2])
	}
	name := args[3]
	if err := codebase.SetDefault(name); err != nil {
		return err
	}
	fmt.Println("Default codebase set to", name)
	return nil
}

func (c Command) listCodebases() {
	def := codebase.Default()
	if def == "" {
		def = "None"
	}
	fmt.Println("Default codebase:")
	fmt.Println("\t", def)

	list := codebase.List()
	fmt.Println("Codebases:")
	for name, path := range list {
		fmt.Println("\t", name, path)
	}
}

func (c Command) removeCodebase(args []string) error {
	if len(args) < 4 {
		return fmt.Errorf("Codebase name required\n"+
			"Usage: %s %s %s <codebase>",
			args[0], args[1], args[2])
	}
	name := args[3]
	_, err := codebase.Remove(name)
	if err != nil {
		return err
	}
	fmt.Println("Removed codebase", name)
	return nil
}

func (c Command) createWorkspace(args []string) error {
	var codebaseName string
	defaultCodebase := codebase.Default()
	switch len(args) {
	case 4:
		if defaultCodebase == "" {
			return fmt.Errorf("Codebase name is required\n"+
				"Usage: %s %s %s <name> <codebase>",
				args[0], args[1], args[2])
		} else {
			codebaseName = defaultCodebase
		}
	case 5:
		codebaseName = args[4]
	default:
		return fmt.Errorf("Unexpected number of arguments\n"+
			"Usage: %s %s %s <name> <codebase>",
			args[0], args[1], args[2])
	}

	workspaceName := args[3]
	dir, err := c.workspace.Create(workspaceName, codebaseName)
	if err != nil {
		return err
	}
	fmt.Println("Created", workspaceName, "at", dir)
	return nil
}

func (c Command) recreateWorkspace(args []string) error {
	if len(args) < 4 {
		return fmt.Errorf("Workspace name is required\n"+
			"Usage: %s %s %s <name>",
			args[0], args[1], args[2])
	}

	workspaceName := args[3]
	dir, err := c.workspace.Recreate(workspaceName)
	if err != nil {
		return err
	}
	fmt.Println("Recreated", workspaceName, "at", dir)
	return nil
}

func (c Command) listWorkspaces() {
	list := c.workspace.List()
	fmt.Println("Workspaces:")
	for name, codebase := range list {
		fmt.Println("\t", name, codebase)
	}
}

func (c Command) removeWorkspace(args []string) error {
	if len(args) < 4 {
		return fmt.Errorf("Workspace name required\n"+
			"Usage: %s %s %s <name>",
			args[0], args[1], args[2])
	}
	name := args[3]
	_, err := c.workspace.Remove(name)
	if err != nil {
		return err
	}
	fmt.Println("Removed workspace", name)
	return nil
}

func (c Command) editProject(args []string) error {
	if len(args) < 3 {
		return fmt.Errorf("Edit path required\n"+
			"Usage: %s %s <path>",
			args[0], args[1])
	}
	path := args[2]
	branchName, projectPath, err := c.workspace.Edit(path)
	if err != nil {
		return err
	}
	fmt.Printf("Created branch %s on project %s\n",
		branchName, projectPath)
	return nil
}

//Handle parses all command line arguments
func (c Command) Handle(args []string) error {
	if len(args) < 2 || args[1] == "help" {
		fmt.Println(usage)
		return nil
	}

	command := args[1]

	switch command {
	case "codebase", "cb":
		if len(args) < 3 {
			return fmt.Errorf("Not enough arguments for codebase command")
		}
		subcommand := args[2]
		switch subcommand {
		case "add":
			return c.addCodebase(args)
		case "remove", "rm":
			return c.removeCodebase(args)
		case "default", "def":
			return c.defaultCodebase(args)
		case "list", "ls":
			c.listCodebases()
			return nil
		default:
			return fmt.Errorf("Command \"%s %s\" not found", command, subcommand)
		}
	case "workspace", "ws":
		if len(args) < 3 {
			return fmt.Errorf("Not enough arguments for workspace command")
		}
		subcommand := args[2]
		switch subcommand {
		case "new":
			return c.createWorkspace(args)
		case "recreate":
			return c.recreateWorkspace(args)
		case "remove", "rm":
			return c.removeWorkspace(args)
		case "list", "ls":
			c.listWorkspaces()
			return nil
		default:
			return fmt.Errorf("Command \"%s %s\" not found", command, subcommand)
		}
	case "edit":
		return c.editProject(args)
	default:
		return fmt.Errorf("Command \"%s\" not found", command)
	}
	return nil
}
