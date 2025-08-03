#!/usr/bin/env python3
# Copyright 2016 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Repo pre-upload hook.

Normally this is loaded indirectly by repo itself, but it can be run directly
when developing.
"""

import argparse
import concurrent.futures
import datetime
import os
import signal
import sys
from typing import List, Optional


# Assert some minimum Python versions as we don't test or support any others.
if sys.version_info < (3, 6):
    print('repohooks: error: Python-3.6+ is required', file=sys.stderr)
    sys.exit(1)


_path = os.path.dirname(os.path.realpath(__file__))
if sys.path[0] != _path:
    sys.path.insert(0, _path)
del _path

# We have to import our local modules after the sys.path tweak.  We can't use
# relative imports because this is an executable program, not a module.
# pylint: disable=wrong-import-position
import rh
import rh.results
import rh.config
import rh.git
import rh.hooks
import rh.terminal
import rh.utils


# Repohooks homepage.
REPOHOOKS_URL = 'https://android.googlesource.com/platform/tools/repohooks/'


class Output(object):
    """Class for reporting hook status."""

    COLOR = rh.terminal.Color()
    COMMIT = COLOR.color(COLOR.CYAN, 'COMMIT')
    RUNNING = COLOR.color(COLOR.YELLOW, 'RUNNING')
    PASSED = COLOR.color(COLOR.GREEN, 'PASSED')
    FAILED = COLOR.color(COLOR.RED, 'FAILED')
    WARNING = COLOR.color(COLOR.YELLOW, 'WARNING')
    FIXUP = COLOR.color(COLOR.MAGENTA, 'FIXUP')

    # How long a hook is allowed to run before we warn that it is "too slow".
    _SLOW_HOOK_DURATION = datetime.timedelta(seconds=30)

    def __init__(self, project_name):
        """Create a new Output object for a specified project.

        Args:
          project_name: name of project.
        """
        self.project_name = project_name
        self.hooks = None
        self.num_hooks = None
        self.num_commits = None
        self.commit_index = 0
        self.success = True
        self.start_time = datetime.datetime.now()
        self.hook_start_time = None
        # Cache number of invisible characters in our banner.
        self._banner_esc_chars = len(self.COLOR.color(self.COLOR.YELLOW, ''))

    def set_num_commits(self, num_commits: int) -> None:
        """Keep track of how many commits we'll be running.

        Args:
          num_commits: Number of commits to be run.
        """
        self.num_commits = num_commits
        self.commit_index = 1

    def commit_start(self, hooks, commit, commit_summary):
        """Emit status for new commit.

        Args:
          hooks: All the hooks to be run for this commit.
          commit: commit hash.
          commit_summary: commit summary.
        """
        status_line = (
            f'[{self.COMMIT} '
            f'{self.commit_index}/{self.num_commits} '
            f'{commit[0:12]}] {commit_summary}'
        )
        rh.terminal.print_status_line(status_line, print_newline=True)
        self.commit_index += 1

        # Initialize the pending hooks line too.
        self.hooks = set(hooks)
        self.num_hooks = len(hooks)
        self.hook_banner()

    def hook_banner(self):
        """Display the banner for current set of hooks."""
        pending = ', '.join(x.name for x in self.hooks)
        status_line = (
            f'[{self.RUNNING} '
            f'{self.num_hooks - len(self.hooks)}/{self.num_hooks}] '
            f'{pending}'
        )
        if self._banner_esc_chars and sys.stderr.isatty():
            cols = os.get_terminal_size(sys.stderr.fileno()).columns
            status_line = status_line[0:cols + self._banner_esc_chars]
        rh.terminal.print_status_line(status_line)

    def hook_finish(self, hook, duration):
        """Finish processing any per-hook state."""
        self.hooks.remove(hook)
        if duration >= self._SLOW_HOOK_DURATION:
            d = rh.utils.timedelta_str(duration)
            self.hook_warning(
                hook,
                f'This hook took {d} to finish which is fairly slow for '
                'developers.\nPlease consider moving the check to the '
                'server/CI system instead.')

        # Show any hooks still pending.
        if self.hooks:
            self.hook_banner()

    def hook_error(self, hook, error):
        """Print an error for a single hook.

        Args:
          hook: The hook that generated the output.
          error: error string.
        """
        self.error(f'{hook.name} hook', error)

    def hook_warning(self, hook, warning):
        """Print a warning for a single hook.

        Args:
          hook: The hook that generated the output.
          warning: warning string.
        """
        status_line = f'[{self.WARNING}] {hook.name}'
        rh.terminal.print_status_line(status_line, print_newline=True)
        print(warning, file=sys.stderr)

    def error(self, header, error):
        """Print a general error.

        Args:
          header: A unique identifier for the source of this error.
          error: error string.
        """
        status_line = f'[{self.FAILED}] {header}'
        rh.terminal.print_status_line(status_line, print_newline=True)
        print(error, file=sys.stderr)
        self.success = False

    def hook_fixups(
        self,
        project_results: rh.results.ProjectResults,
        hook_results: List[rh.results.HookResult],
    ) -> None:
        """Display summary of possible fixups for a single hook."""
        for result in (x for x in hook_results if x.fixup_cmd):
            cmd = result.fixup_cmd + list(result.files)
            for line in (
                f'[{self.FIXUP}] {result.hook} has automated fixups available',
                f'  cd {rh.shell.quote(project_results.workdir)} && \\',
                f'    {rh.shell.cmd_to_str(cmd)}',
            ):
                rh.terminal.print_status_line(line, print_newline=True)

    def finish(self):
        """Print summary for all the hooks."""
        header = self.PASSED if self.success else self.FAILED
        status = 'passed' if self.success else 'failed'
        d = rh.utils.timedelta_str(datetime.datetime.now() - self.start_time)
        rh.terminal.print_status_line(
            f'[{header}] repohooks for {self.project_name} {status} in {d}',
            print_newline=True)


def _process_hook_results(results):
    """Returns an error string if an error occurred.

    Args:
      results: A list of HookResult objects, or None.

    Returns:
      error output if an error occurred, otherwise None
      warning output if an error occurred, otherwise None
    """
    if not results:
        return (None, None)

    # We track these as dedicated fields in case a hook doesn't output anything.
    # We want to treat silent non-zero exits as failures too.
    has_error = False
    has_warning = False

    error_ret = ''
    warning_ret = ''
    for result in results:
        if result or result.is_warning():
            ret = ''
            if result.files:
                ret += f'  FILES: {rh.shell.cmd_to_str(result.files)}\n'
            lines = result.error.splitlines()
            ret += '\n'.join(f'    {x}' for x in lines)
            if result.is_warning():
                has_warning = True
                warning_ret += ret
            else:
                has_error = True
                error_ret += ret

    return (error_ret if has_error else None,
            warning_ret if has_warning else None)


def _get_project_config(from_git=False):
    """Returns the configuration for a project.

    Args:
      from_git: If true, we are called from git directly and repo should not be
          used.
    Expects to be called from within the project root.
    """
    if from_git:
        global_paths = (rh.git.find_repo_root(),)
    else:
        global_paths = (
            # Load the global config found in the manifest repo.
            (os.path.join(rh.git.find_repo_root(), '.repo', 'manifests')),
            # Load the global config found in the root of the repo checkout.
            rh.git.find_repo_root(),
        )

    paths = (
        # Load the config for this git repo.
        '.',
    )
    return rh.config.PreUploadSettings(paths=paths, global_paths=global_paths)


def _attempt_fixes(projects_results: List[rh.results.ProjectResults]) -> None:
    """Attempts to fix fixable results."""
    # Filter out any result that has a fixup.
    fixups = []
    for project_results in projects_results:
        fixups.extend((project_results.workdir, x)
                      for x in project_results.fixups)
    if not fixups:
        return

    if len(fixups) > 1:
        banner = f'Multiple fixups ({len(fixups)}) are available.'
    else:
        banner = 'Automated fixups are available.'
    print(Output.COLOR.color(Output.COLOR.MAGENTA, banner), file=sys.stderr)

    # If there's more than one fixup available, ask if they want to blindly run
    # them all, or prompt for them one-by-one.
    mode = 'some'
    if len(fixups) > 1:
        while True:
            response = rh.terminal.str_prompt(
                'What would you like to do',
                ('Run (A)ll', 'Run (S)ome', '(D)ry-run', '(N)othing [default]'))
            if not response:
                print('', file=sys.stderr)
                return
            if response.startswith('a') or response.startswith('y'):
                mode = 'all'
                break
            elif response.startswith('s'):
                mode = 'some'
                break
            elif response.startswith('d'):
                mode = 'dry-run'
                break
            elif response.startswith('n'):
                print('', file=sys.stderr)
                return

    # Walk all the fixups and run them one-by-one.
    for workdir, result in fixups:
        if mode == 'some':
            if not rh.terminal.boolean_prompt(
                f'Run {result.hook} fixup for {result.commit}'
            ):
                continue

        cmd = tuple(result.fixup_cmd) + tuple(result.files)
        print(
            f'\n[{Output.RUNNING}] cd {rh.shell.quote(workdir)} && '
            f'{rh.shell.cmd_to_str(cmd)}', file=sys.stderr)
        if mode == 'dry-run':
            continue

        cmd_result = rh.utils.run(cmd, cwd=workdir, check=False)
        if cmd_result.returncode:
            print(f'[{Output.WARNING}] command exited {cmd_result.returncode}',
                  file=sys.stderr)
        else:
            print(f'[{Output.PASSED}] great success', file=sys.stderr)

    print(f'\n[{Output.FIXUP}] Please amend & rebase your tree before '
          'attempting to upload again.\n', file=sys.stderr)

def _run_project_hooks_in_cwd(
    project_name: str,
    proj_dir: str,
    output: Output,
    jobs: Optional[int] = None,
    from_git: bool = False,
    commit_list: Optional[List[str]] = None,
) -> rh.results.ProjectResults:
    """Run the project-specific hooks in the cwd.

    Args:
      project_name: The name of this project.
      proj_dir: The directory for this project (for passing on in metadata).
      output: Helper for summarizing output/errors to the user.
      jobs: How many hooks to run in parallel.
      from_git: If true, we are called from git directly and repo should not be
          used.
      commit_list: A list of commits to run hooks against.  If None or empty
          list then we'll automatically get the list of commits that would be
          uploaded.

    Returns:
      All the results for this project.
    """
    ret = rh.results.ProjectResults(project_name, proj_dir)

    try:
        config = _get_project_config(from_git)
    except rh.config.ValidationError as e:
        output.error('Loading config files', str(e))
        return ret._replace(internal_failure=True)

    # If the repo has no pre-upload hooks enabled, then just return.
    hooks = list(config.callable_hooks())
    if not hooks:
        return ret

    # Set up the environment like repo would with the forall command.
    try:
        remote = rh.git.get_upstream_remote()
        upstream_branch = rh.git.get_upstream_branch()
    except rh.utils.CalledProcessError as e:
        output.error('Upstream remote/tracking branch lookup',
                     f'{e}\nDid you run repo start?  Is your HEAD detached?')
        return ret._replace(internal_failure=True)

    project = rh.Project(name=project_name, dir=proj_dir)
    rel_proj_dir = os.path.relpath(proj_dir, rh.git.find_repo_root())

    # Filter out the hooks to process.
    hooks = [x for x in hooks if rel_proj_dir not in x.scope]
    if not hooks:
        return ret

    os.environ.update({
        'REPO_LREV': rh.git.get_commit_for_ref(upstream_branch),
        'REPO_PATH': rel_proj_dir,
        'REPO_PROJECT': project_name,
        'REPO_REMOTE': remote,
        'REPO_RREV': rh.git.get_remote_revision(upstream_branch, remote),
    })

    if not commit_list:
        commit_list = rh.git.get_commits(
            ignore_merged_commits=config.ignore_merged_commits)
    output.set_num_commits(len(commit_list))

    def _run_hook(hook, project, commit, desc, diff):
        """Run a hook, gather stats, and process its results."""
        start = datetime.datetime.now()
        results = hook.hook(project, commit, desc, diff)
        (error, warning) = _process_hook_results(results)
        duration = datetime.datetime.now() - start
        return (hook, results, error, warning, duration)

    with concurrent.futures.ThreadPoolExecutor(max_workers=jobs) as executor:
        for commit in commit_list:
            # Mix in some settings for our hooks.
            os.environ['PREUPLOAD_COMMIT'] = commit
            diff = rh.git.get_affected_files(commit)
            desc = rh.git.get_commit_desc(commit)
            os.environ['PREUPLOAD_COMMIT_MESSAGE'] = desc

            commit_summary = desc.split('\n', 1)[0]
            output.commit_start(hooks, commit, commit_summary)

            futures = (
                executor.submit(_run_hook, hook, project, commit, desc, diff)
                for hook in hooks
            )
            future_results = (
                x.result() for x in concurrent.futures.as_completed(futures)
            )
            for hook, hook_results, error, warning, duration in future_results:
                ret.add_results(hook_results)
                if error is not None or warning is not None:
                    if warning is not None:
                        output.hook_warning(hook, warning)
                    if error is not None:
                        output.hook_error(hook, error)
                        output.hook_fixups(ret, hook_results)
                output.hook_finish(hook, duration)

    return ret


def _run_project_hooks(
    project_name: str,
    proj_dir: Optional[str] = None,
    jobs: Optional[int] = None,
    from_git: bool = False,
    commit_list: Optional[List[str]] = None,
) -> rh.results.ProjectResults:
    """Run the project-specific hooks in |proj_dir|.

    Args:
      project_name: The name of project to run hooks for.
      proj_dir: If non-None, this is the directory the project is in.  If None,
          we'll ask repo.
      jobs: How many hooks to run in parallel.
      from_git: If true, we are called from git directly and repo should not be
          used.
      commit_list: A list of commits to run hooks against.  If None or empty
          list then we'll automatically get the list of commits that would be
          uploaded.

    Returns:
      All the results for this project.
    """
    output = Output(project_name)

    if proj_dir is None:
        cmd = ['repo', 'forall', project_name, '-c', 'pwd']
        result = rh.utils.run(cmd, capture_output=True)
        proj_dirs = result.stdout.split()
        if not proj_dirs:
            print(f'{project_name} cannot be found.', file=sys.stderr)
            print('Please specify a valid project.', file=sys.stderr)
            return False
        if len(proj_dirs) > 1:
            print(f'{project_name} is associated with multiple directories.',
                  file=sys.stderr)
            print('Please specify a directory to help disambiguate.',
                  file=sys.stderr)
            return False
        proj_dir = proj_dirs[0]

    pwd = os.getcwd()
    try:
        # Hooks assume they are run from the root of the project.
        os.chdir(proj_dir)
        return _run_project_hooks_in_cwd(
            project_name, proj_dir, output, jobs=jobs, from_git=from_git,
            commit_list=commit_list)
    finally:
        output.finish()
        os.chdir(pwd)


def _run_projects_hooks(
    project_list: List[str],
    worktree_list: List[Optional[str]],
    jobs: Optional[int] = None,
    from_git: bool = False,
    commit_list: Optional[List[str]] = None,
) -> bool:
    """Run all the hooks

    Args:
      project_list: List of project names.
      worktree_list: List of project checkouts.
      jobs: How many hooks to run in parallel.
      from_git: If true, we are called from git directly and repo should not be
          used.
      commit_list: A list of commits to run hooks against.  If None or empty
          list then we'll automatically get the list of commits that would be
          uploaded.

    Returns:
      True if everything passed, else False.
    """
    results = []
    for project, worktree in zip(project_list, worktree_list):
        result = _run_project_hooks(
            project,
            proj_dir=worktree,
            jobs=jobs,
            from_git=from_git,
            commit_list=commit_list,
        )
        results.append(result)
        if result:
            # If a repo had failures, add a blank line to help break up the
            # output.  If there were no failures, then the output should be
            # very minimal, so we don't add it then.
            print('', file=sys.stderr)

    _attempt_fixes(results)
    return not any(results)


def main(project_list, worktree_list=None, **_kwargs):
    """Main function invoked directly by repo.

    We must use the name "main" as that is what repo requires.

    This function will exit directly upon error so that repo doesn't print some
    obscure error message.

    Args:
      project_list: List of projects to run on.
      worktree_list: A list of directories.  It should be the same length as
          project_list, so that each entry in project_list matches with a
          directory in worktree_list.  If None, we will attempt to calculate
          the directories automatically.
      kwargs: Leave this here for forward-compatibility.
    """
    if not worktree_list:
        worktree_list = [None] * len(project_list)
    if not _run_projects_hooks(project_list, worktree_list):
        color = rh.terminal.Color()
        print(color.color(color.RED, 'FATAL') +
              ': Preupload failed due to above error(s).\n'
              f'For more info, see: {REPOHOOKS_URL}',
              file=sys.stderr)
        sys.exit(1)


def _identify_project(path, from_git=False):
    """Identify the repo project associated with the given path.

    Returns:
      A string indicating what project is associated with the path passed in or
      a blank string upon failure.
    """
    if from_git:
        cmd = ['git', 'rev-parse', '--show-toplevel']
        project_path = rh.utils.run(cmd, capture_output=True).stdout.strip()
        cmd = ['git', 'rev-parse', '--show-superproject-working-tree']
        superproject_path = rh.utils.run(
            cmd, capture_output=True).stdout.strip()
        module_path = project_path[len(superproject_path) + 1:]
        cmd = ['git', 'config', '-f', '.gitmodules',
               '--name-only', '--get-regexp', r'^submodule\..*\.path$',
               f"^{module_path}$"]
        module_name = rh.utils.run(cmd, cwd=superproject_path,
                                   capture_output=True).stdout.strip()
        return module_name[len('submodule.'):-len(".path")]
    else:
        cmd = ['repo', 'forall', '.', '-c', 'echo ${REPO_PROJECT}']
        return rh.utils.run(cmd, capture_output=True, cwd=path).stdout.strip()


def direct_main(argv):
    """Run hooks directly (outside of the context of repo).

    Args:
      argv: The command line args to process.

    Returns:
      0 if no pre-upload failures, 1 if failures.

    Raises:
      BadInvocation: On some types of invocation errors.
    """
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--git', action='store_true',
                        help='This hook is called from git instead of repo')
    parser.add_argument('--dir', default=None,
                        help='The directory that the project lives in.  If not '
                        'specified, use the git project root based on the cwd.')
    parser.add_argument('--project', default=None,
                        help='The project repo path; this can affect how the '
                        'hooks get run, since some hooks are project-specific.'
                        'If not specified, `repo` will be used to figure this '
                        'out based on the dir.')
    parser.add_argument('-j', '--jobs', type=int,
                        help='Run up to this many hooks in parallel. Setting '
                        'to 1 forces serial execution, and the default '
                        'automatically chooses an appropriate number for the '
                        'current system.')
    parser.add_argument('commits', nargs='*',
                        help='Check specific commits')
    opts = parser.parse_args(argv)

    # Check/normalize git dir; if unspecified, we'll use the root of the git
    # project from CWD.
    if opts.dir is None:
        cmd = ['git', 'rev-parse', '--git-dir']
        git_dir = rh.utils.run(cmd, capture_output=True).stdout.strip()
        if not git_dir:
            parser.error('The current directory is not part of a git project.')
        opts.dir = os.path.dirname(os.path.abspath(git_dir))
    elif not os.path.isdir(opts.dir):
        parser.error(f'Invalid dir: {opts.dir}')
    elif not rh.git.is_git_repository(opts.dir):
        parser.error(f'Not a git repository: {opts.dir}')

    # Identify the project if it wasn't specified; this _requires_ the repo
    # tool to be installed and for the project to be part of a repo checkout.
    if not opts.project:
        opts.project = _identify_project(opts.dir, opts.git)
        if not opts.project:
            parser.error(f"Couldn't identify the project of {opts.dir}")

    try:
        if _run_projects_hooks([opts.project], [opts.dir], jobs=opts.jobs,
                               from_git=opts.git, commit_list=opts.commits):
            return 0
    except KeyboardInterrupt:
        print('Aborting execution early due to user interrupt', file=sys.stderr)
        return 128 + signal.SIGINT
    return 1


if __name__ == '__main__':
    sys.exit(direct_main(sys.argv[1:]))
