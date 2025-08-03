#!/usr/bin/env -S python3
#  Copyright (C) 2024 The Android Open Source Project
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
import argparse
import dataclasses
import subprocess
import sys
from pathlib import Path


@dataclasses.dataclass(frozen=True)
class BaselineProject:
    """A project that has a baseline file to update."""
    # The name of the project
    name: str

    # The baseline file path.
    baseline_file: Path


def resource_path(project_dir, resource_path):
    return project_dir / "src" / "test" / "resources" / resource_path


def find_baseline_projects(metalava_dir):
    projects = []
    for buildFile in metalava_dir.glob("*/build.gradle.kts"):
        for line in open(buildFile, 'r'):
            if """id("metalava-model-provider-plugin")""" in line:
                project_dir = buildFile.parent
                baseline = BaselineProject(
                    name=project_dir.name,
                    baseline_file=resource_path(project_dir, "model-test-suite-baseline.txt"),
                )
                projects.append(baseline)
    projects.append(BaselineProject(
        name="metalava",
        baseline_file=resource_path(metalava_dir / "metalava", "source-model-provider-baseline.txt")
    ))
    return projects


def main(args):
    args_parser = argparse.ArgumentParser(description="Refresh the baseline files.")
    args_parser.add_argument("projects", nargs='*')
    args = args_parser.parse_args(args)

    # Get various directories.
    this = Path(__file__)
    script_dir = this.parent
    metalava_dir = script_dir.parent
    out_dir = metalava_dir.parent.parent / "out"
    metalava_out_dir = out_dir / "metalava"

    # Get the projects which have a baseline file to update.
    baseline_projects = find_baseline_projects(metalava_dir)

    # Filter the baseline projects by the names specified on the command line.
    if args.projects:
        baseline_projects = [p for p in baseline_projects if p.name in args.projects]

    for baseline_project in baseline_projects:
        project_name = baseline_project.name

        # Delete all the test report files.
        print(f"Deleting test report files for {project_name}")
        test_reports_dir = metalava_out_dir / project_name / "build" / "test-results" / "test"
        for f in test_reports_dir.glob("**/TEST-*.xml"):
            f.unlink()

        # Delete the baseline file.
        baseline_file = baseline_project.baseline_file
        print(f"Deleting baseline file - {baseline_file}")
        baseline_file.unlink(missing_ok=True)

        # Run the tests.
        print(f"Running all tests in {project_name}")
        subprocess.run(["./gradlew", f":{project_name}:test", "--continue"], cwd=metalava_dir)

        print(f"Updating baseline file - {baseline_file}")
        test_report_files = " ".join([f"'{str(f)}'" for f in test_reports_dir.glob("**/TEST-*.xml")])
        project_dir = metalava_dir / project_name
        subprocess.run(["./gradlew", f":metalava-model-testsuite-cli:run",
                        f"""--args={test_report_files} --baseline-file '{baseline_file}'"""], cwd=metalava_dir)


if __name__ == "__main__":
    main(sys.argv[1:])
