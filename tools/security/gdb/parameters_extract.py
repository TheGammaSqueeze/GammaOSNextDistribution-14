import os
import re

# If doing it on device with gdbserver
DEVICE = os.environ.get('GDBSCRIPT_ON_DEVICE', False)
# Path of the file on device
DEVICE_FILEPATH = os.environ.get('GDBSCRIPT_FILENAME', None)
# GDBServer's port
DEVICE_PORT = os.environ.get('GDBSCRIPT_DEVICE_PORT', 4444)
# Serial number of device for adb
DEVICE_SERIAL = os.environ.get('GDBSCRIPT_DEVICE_SERIAL', None)

def check_device_args():
    """
    Checks if FILEPATH is provided if the execution is on device
    """
    if not DEVICE:
        return

    if not DEVICE_FILEPATH:
        raise ValueError("Filename (GDBSCRIPT_FILEPATH) not provided")

class RecordPoint(gdb.Breakpoint):
    """
    A custom breakpoint that records the arguments when the breakpoint is hit and continues
    Also enables the next breakpoint and disables all the ones after it
    """
    def stop(self):
        """
        The function that's called when a breakpoint is hit. If we return true,
        it halts otherwise it continues
        We always return false because we just need to record the value and we
        can do it without halting the program
        """
        self.args[self.times_hit % self.count] = get_function_args()
        self.times_hit += 1

        if self.next_bp != None:
            self.next_bp.previous_hit()

        return False

    def previous_hit(self):
        """
        This function is called if the previous breakpoint is hit so it can enable
        itself and disable the next ones
        """
        self.enabled = True

        if self.next_bp != None:
            self.next_bp.propagate_disable()

    def propagate_disable(self):
        """
        Disabled all the breakpoints after itself
        """
        self.enabled = False
        if self.next_bp != None:
            self.next_bp.propagate_disable()

    def process_arguments(self):
        """
        Orders the recorded arguments into the right order (oldest to newest)
        """
        current_hit_point = self.times_hit % self.count
        # Split at the point of current_hit_point because all the entries after it
        # are older than the ones before it
        self.processed_args = self.args[current_hit_point:] + self.args[:current_hit_point]
        self.current_arg_idx = 0

    def get_arguments(self):
        """
        Gets the current argument value.
        Should be called the same amount of times as the function was called
        in the stacktrace
        First call returns the arguments recorded for the first call in the stacktrace
        and so on.
        """
        if self.current_arg_idx >= len(self.processed_args):
            raise ValueError("Cannot get arguments more times than the function \
                    was present in stacktrace")

        cur = self.processed_args[self.current_arg_idx]
        self.current_arg_idx += 1
        return cur

def init_gdb():
    """
    Initialized the GDB specific stuff
    """
    gdb.execute('set pagination off')
    gdb.execute('set print frame-arguments all')
    if DEVICE:
        gdb.execute('target extended-remote :{}'.format(DEVICE_PORT))
        gdb.execute('set remote exec-file /data/local/tmp/{}'.format(DEVICE_FILEPATH))

def initial_run():
    """
    The initial run of the program which captures the stacktrace in init.log file
    """
    gdb.execute('r > init.log 2>&1',from_tty=True, to_string=True)
    if DEVICE:
        if DEVICE_SERIAL:
            os.system('adb -s "{}" pull /data/local/tmp/init.log'.format(DEVICE_SERIAL))
        else:
            os.system("adb pull /data/local/tmp/init.log")
    with open("init.log", "rb") as f:
        out = f.read().decode()
    return out

def gdb_exit():
    """
    Exits the GDB instance
    """
    gdb.execute('q')

def get_stacktrace_functions(stacktrace):
    """
    Gets the functions from ASAN/HWASAN's stacktrace
    Args:
        stacktrace: (string) ASAN/HWASAN's stacktrace output
    Returns:
        functions: (list) functions in the stacktrace
    """
    stacktrace_start = stacktrace[stacktrace.index('==ERROR: '):].split("\n")
    functions = []

    # skip the first two lines of stacktrace
    for line in stacktrace_start[2:]:
        if line == "":
            break

        # Extracts the function name from a line like this
        # "#0 0xaddress in function_name() file/path.cc:xx:yy"
        func_name = line.strip().split(" ")[3]
        if '(' in func_name:
            func_name = func_name[:func_name.index('(')]

        functions.append(func_name)

    #remove last function from stacktrace because it would be _start
    return functions

def parse_function_arguments(func_info):
    """
    Parses the output of 'whatis' command into a list of arguments
    "void (teststruct)" --> ["teststruct"]
    "int (int (*)(int, char **, char **), int, char **, int (*)(int, char **, char **),
    void (*)(void), void (*)(void), void *)" --> ['int (*)(int, char **, char **)',
    'int', 'char **', 'int (*)(int, char **, char **)', 'void (*)(void)',
    'void (*)(void)', ' void *']

    Args:
        func_info: (string) output of gdb's 'whatis' command for a function
    Returns:
        parsed_params: (list) parsed parameters of the function
    """
    if '(' not in func_info:
        return []
    func_params = func_info[func_info.index('(')+1:-1]
    parentheses_count = 0
    current_param = ""
    parsed_params = []

    for token in func_params:
        # Essentially trying to get the data types from a function declaration
        if token == '(':
            parentheses_count += 1
        elif token == ')':
            parentheses_count -= 1

        # If we are not inside any paren and see a ',' it signals the start of
        #the next parameter
        if token == ',' and parentheses_count == 0:
            parsed_params.append(current_param.strip())
            current_param = ""
        else:
            current_param += token

    parsed_params.append(current_param)
    return parsed_params

def parse_stacktrace(stacktrace):
    """
    Parses the ASAN/HWASAN's stacktrace to a list of functions, their addresses
    and argument types
    Args:
        stacktrace: (string) ASAN/HWASAN's stacktrace output
    Returns:
        functions_info: (list) parsed function information as a dictionary
    """
    stacktrace_functions = get_stacktrace_functions(stacktrace)[:-1]
    functions_info = []
    for function in stacktrace_functions:
        # Gets the value right hand side of gdb's whatis command.
        # "type = {function info}" -> "{function info}"
        func_info = gdb.execute('whatis {}'.format(function),
                to_string=True).split(' = ')[1].strip()
        # Uses gdb's x/i to print its address and parse it from hex to int
        address = int(gdb.execute("x/i {}".format(function),
            to_string=True).strip().split(" ")[0], 16)
        functions_info.append({'name': function, 'address':address,
            'arguments' : parse_function_arguments(func_info)})
    #In the order they are called in the execution
    return functions_info[::-1]

def get_function_args():
    """
    Gets the current function arguments
    """
    args = gdb.execute('info args -q', to_string=True).strip()
    return args

def functions_to_breakpoint(parsed_functions):
    """
    Sets the breakpoint at every function and returns a dictionary mapping the
    function to it's breakpoint
    Args:
        parsed_functions: (list) functions in the stacktrace (in the same order) as
        dictionary with "name" referring to the function name
        ({"name" : function_name})
    Returns:
        function_breakpoints: (dictionary) maps the function name to its
        breakpoint object
    """
    function_breakpoints = {}
    last_bp = None

    for function in reversed(parsed_functions):
        function_name = function['name']
        if function_name in function_breakpoints:
            function_breakpoints[function_name].count += 1
            function_breakpoints[function_name].args.append(None)
            continue

        cur_bp = RecordPoint("{}".format(function_name))
        cur_bp.count = 1
        cur_bp.times_hit = 0
        cur_bp.args = []
        cur_bp.args.append(None)
        cur_bp.next_bp = last_bp

        function_breakpoints[function['name']] = cur_bp
        last_bp = cur_bp

    return function_breakpoints

def run(parsed_functions):
    """
    Runs the whole thing by setting up breakpoints and printing them after
    excecution is done
    Args:
        parsed_functions: A list of functions in the stacktrace (in the same order)
        as dictionary with "name" referring to the function name
        ({"name" : function_name})
    """
    names = [function['name'] for function in parsed_functions]
    breakpoints = functions_to_breakpoint(parsed_functions)

    #Disable all breakpoints at start
    for bp in breakpoints:
        breakpoints[bp].enabled = False

    breakpoints[names[0]].enabled = True

    gdb.execute('r')
    for breakpoint in breakpoints:
        breakpoints[breakpoint].process_arguments()

    function_args = []
    for name in names:
        print("-----------")
        print("Function -> {}".format(name))

        function_args.append({'function':name,
            'arguments' : breakpoints[name].get_arguments()})
        print(function_args[-1]['arguments'])

    return function_args


if __name__ == '__main__':
    check_device_args()
    init_gdb()
    initial_out = initial_run()
    function_data = parse_stacktrace(initial_out)
    run(function_data)
    gdb_exit()
