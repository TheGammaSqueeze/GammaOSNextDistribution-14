import os
import re
import six
import sys

# This must run on Python versions less than 2.4.
dirname = os.path.dirname(sys.modules[__name__].__file__)
common_dir = os.path.abspath(os.path.join(dirname, 'common_lib'))
sys.path.insert(0, common_dir)
import check_version
sys.path.pop(0)


def _get_pyversion_from_args():
    """Extract, format, & pop the current py_version from args, if provided."""
    py_version = 3
    py_version_re = re.compile(r'--py_version=(\w+)\b')

    version_found = False
    for i, arg in enumerate(sys.argv):
        if not arg.startswith('--py_version'):
            continue
        result = py_version_re.search(arg)
        if result:
            if version_found:
                raise ValueError('--py_version may only be specified once.')
            py_version = result.group(1)
            version_found = True
            if py_version not in ('2', '3'):
                raise ValueError('Python version must be "2" or "3".')

            # Remove the arg so other argparsers don't get grumpy.
            sys.argv.pop(i)

    return py_version


def _desired_version():
    """
    Returns desired python version.

    If the PY_VERSION env var is set, just return that. This is the case
    when autoserv kicks of autotest on the server side via a job.run(), or
    a process created a subprocess.

    Otherwise, parse & pop the sys.argv for the '--py_version' flag. If no
    flag is set, default to python 3.

    """
    # Even if the arg is in the env vars, we will attempt to get it from the
    # args, so that it can be popped prior to other argparsers hitting.
    py_version = _get_pyversion_from_args()

    if os.getenv('PY_VERSION'):
        return int(os.getenv('PY_VERSION'))

    os.environ['PY_VERSION'] = str(py_version)
    return int(py_version)


desired_version = _desired_version()
if desired_version == sys.version_info.major:
    os.environ['AUTOTEST_NO_RESTART'] = 'True'
else:
    # There are cases were this can be set (ie by test_that), but a subprocess
    # is launched in the incorrect version.
    if os.getenv('AUTOTEST_NO_RESTART'):
        del os.environ['AUTOTEST_NO_RESTART']
    check_version.check_python_version(desired_version)

import glob, traceback


def import_module(module, from_where):
    """Equivalent to 'from from_where import module'
    Returns the corresponding module"""
    from_module = __import__(from_where, globals(), locals(), [module])
    return getattr(from_module, module)


def _autotest_logging_handle_error(self, record):
    """Method to monkey patch into logging.Handler to replace handleError."""
    # The same as the default logging.Handler.handleError but also prints
    # out the original record causing the error so there is -some- idea
    # about which call caused the logging error.
    import logging
    if logging.raiseExceptions:
        # Avoid recursion as the below output can end up back in here when
        # something has *seriously* gone wrong in autotest.
        logging.raiseExceptions = 0
        sys.stderr.write('Exception occurred formatting message: '
                         '%r using args %r\n' % (record.msg, record.args))
        traceback.print_stack()
        sys.stderr.write('-' * 50 + '\n')
        traceback.print_exc()
        sys.stderr.write('Future logging formatting exceptions disabled.\n')


def _monkeypatch_logging_handle_error():
    # Hack out logging.py*
    logging_py = os.path.join(os.path.dirname(__file__), 'common_lib',
                              'logging.py*')
    if glob.glob(logging_py):
        os.system('rm -f %s' % logging_py)

    # Monkey patch our own handleError into the logging module's StreamHandler.
    # A nicer way of doing this -might- be to have our own logging module define
    # an autotest Logger instance that added our own Handler subclass with this
    # handleError method in it.  But that would mean modifying tons of code.
    import logging
    assert callable(logging.Handler.handleError)
    logging.Handler.handleError = _autotest_logging_handle_error


def _insert_site_packages(root):
    # Allow locally installed third party packages to be found
    # before any that are installed on the system itself when not.
    # running as a client.
    # This is primarily for the benefit of frontend and tko so that they
    # may use libraries other than those available as system packages.
    if six.PY2:
        sys.path.insert(0, os.path.join(root, 'site-packages'))


import importlib

ROOT_MODULE_NAME_ALLOW_LIST = (
        'autotest_lib',
        'autotest_lib.client',
)


def _setup_top_level_symlink(base_path):
    """Create a self pointing symlink in the base_path)."""
    if os.path.islink(os.path.join(base_path, 'autotest_lib')):
        return
    os.chdir(base_path)
    os.symlink('.', 'autotest_lib')


def _setup_client_symlink(base_path):
    """Setup the client symlink for the DUT.

    Creates a "autotest_lib" folder in client, then creates a symlink called
    "client" pointing back to ../, as well as an __init__ for the folder.
    """

    def _create_client_symlink():
        os.chdir(autotest_lib_dir)
        with open('__init__.py', 'w'):
            pass
        os.symlink('../', 'client')

    autotest_lib_dir = os.path.join(base_path, 'autotest_lib')
    link_path = os.path.join(autotest_lib_dir, 'client')

    # TODO: Use os.makedirs(..., exist_ok=True) after switching to Python 3
    if not os.path.isdir(autotest_lib_dir):
        try:
            os.mkdir(autotest_lib_dir)
        except FileExistsError as e:
            if not os.path.isdir(autotest_lib_dir):
                raise e

    if os.path.islink(link_path):
        return

    try:
        _create_client_symlink()
    # It's possible 2 autotest processes are running at once, and one
    # creates the symlink in the time between checking and creating.
    # Thus if the symlink DNE, and we cannot create it, check for its
    # existence and exit if it exists.
    except FileExistsError as e:
        if os.path.islink(link_path):
            return
        raise e


def _symlink_check(base_path, root_dir):
    """Verify the required symlinks are present, and add them if not."""
    # Note the starting cwd to later change back to it.
    starting_dir = os.getcwd()
    if root_dir == 'autotest_lib':
        _setup_top_level_symlink(base_path)
    elif root_dir == 'autotest_lib.client':
        _setup_client_symlink(base_path)

    os.chdir(starting_dir)


def setup(base_path, root_module_name):
    _symlink_check(base_path, root_module_name)
    if root_module_name not in ROOT_MODULE_NAME_ALLOW_LIST:
        raise Exception('Unexpected root module: ' + root_module_name)

    _insert_site_packages(base_path)

    # Ie, server (or just not /client)
    if root_module_name == 'autotest_lib':
        # Base path is just x/x/x/x/autotest/files
        _setup_autotest_lib(base_path)
        _preimport_top_level_packages(os.path.join(base_path, 'autotest_lib'),
                                      parent='autotest_lib')
    else:  # aka, in /client/
        if os.path.exists(os.path.join(os.path.dirname(base_path), 'server')):

            # Takes you from /client/ to /files
            # this is because on DUT there is no files/client
            autotest_base_path = os.path.dirname(base_path)

        else:
            autotest_base_path = base_path

        _setup_autotest_lib(autotest_base_path)
        _preimport_top_level_packages(os.path.join(autotest_base_path,
                                                   'autotest_lib'),
                                      parent='autotest_lib')
        _preimport_top_level_packages(
                os.path.join(autotest_base_path, 'autotest_lib', 'client'),
                parent='autotest_lib.client',
        )

    _monkeypatch_logging_handle_error()


def _setup_autotest_lib(path):
    sys.path.insert(0, path)
    # This is a symlink back to the root directory, that does all the magic.
    importlib.import_module('autotest_lib')
    sys.path.pop(0)


def _preimport_top_level_packages(root, parent):
    # The old code to setup the packages used to fetch the top-level packages
    # inside autotest_lib. We keep that behaviour in order to avoid having to
    # add import statements for the top-level packages all over the codebase.
    #
    # e.g.,
    #  import common
    #  from autotest_lib.server import utils
    #
    # must continue to work. The _right_ way to do that import would be.
    #
    #  import common
    #  import autotest_lib.server
    #  from autotest_lib.server import utils
    names = []
    for filename in os.listdir(root):
        path = os.path.join(root, filename)
        if not os.path.isdir(path):
            continue  # skip files
        if '.' in filename:
            continue  # if "." is in the name it's not a valid package name
        if not os.access(path, os.R_OK | os.X_OK):
            continue  # need read + exec access to make a dir importable
        if '__init__.py' in os.listdir(path):
            names.append(filename)

    for name in names:
        pname = parent + '.' + name
        importlib.import_module(pname)
        if name != 'autotest_lib':
            sys.modules[name] = sys.modules[pname]
