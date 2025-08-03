# This file must use Python 1.5 syntax.
import glob
import logging
import os
import re
import sys

PY_GLOBS = {
        3: ['/usr/bin/python3*', '/usr/local/bin/python3*'],
        2: ['/usr/bin/python2*', '/usr/local/bin/python2*']
}


class check_python_version:

    def __init__(self, desired_version=3):
        # In order to ease the migration to Python3, disable the restart logic
        # when AUTOTEST_NO_RESTART is set. This makes it possible to run
        # autotest locally as Python3 before any other environment is switched
        # to Python3.
        if os.getenv("AUTOTEST_NO_RESTART"):
            return
        self.desired_version = desired_version

        # The change to prefer 2.4 really messes up any systems which have both
        # the new and old version of Python, but where the newer is default.
        # This is because packages, libraries, etc are all installed into the
        # new one by default. Some things (like running under mod_python) just
        # plain don't handle python restarting properly. I know that I do some
        # development under ipython and whenever I run (or do anything that
        # runs) 'import common' it restarts my shell. Overall, the change was
        # fairly annoying for me (and I can't get around having 2.4 and 2.5
        # installed with 2.5 being default).
        if sys.version_info.major != self.desired_version:
            try:
                # We can't restart when running under mod_python.
                from mod_python import apache
            except ImportError:
                self.restart()

    def extract_version(self, path):
        """Return a matching python version to the provided path."""
        match = re.search(r'/python(\d+)\.(\d+)$', path)
        if match:
            return (int(match.group(1)), int(match.group(2)))
        else:
            return None

    def find_desired_python(self):
        """Returns the path of the desired python interpreter."""
        pythons = []
        for glob_str in PY_GLOBS[self.desired_version]:
            pythons.extend(glob.glob(glob_str))

        possible_versions = []
        for python in pythons:
            version = self.extract_version(python)
            if not version:
                continue
            # Autotest in Python2 is written to 2.4 and above.
            if self.desired_version == 2:
                if version < (2, 4):
                    continue
            if self.desired_version == 3:
                # Autotest in Python3 is written to 3.6 and above.
                if version < (3, 6):
                    continue
            possible_versions.append((version, python))

        possible_versions.sort()

        if not possible_versions:
            raise ValueError('Python %s.x not found' % self.desired_version)

        # Return the lowest compatible major version possible
        return possible_versions[0][1]

    def restart(self):
        python = self.find_desired_python()
        sys.stderr.write('NOTE: %s switching to %s\n' %
                         (os.path.basename(sys.argv[0]), python))
        sys.argv.insert(0, '-u')
        sys.argv.insert(0, python)
        os.execv(sys.argv[0], sys.argv)
