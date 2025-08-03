import gdb


def parse_address_to_int(address):
    int_address_string = gdb.execute(
        'p/d {}'.format(address), to_string=True)
    int_address = int(int_address_string.split('=')[1].strip())
    return int_address


def parse_gdb_equals(str):
    """
    str is $1 = value. so it returns value
    """
    return str.split("=")[1].strip()


class HeapMapping:
    """
    Wrapper class for dictionary to have customization for the dictionary
    and one entry point
    """

    address_length_mapping = {}
    address_set = set()

    @staticmethod
    def put(address, length):
        HeapMapping.address_length_mapping[address] = length
        HeapMapping.address_set.add(address)

    @staticmethod
    def get(address):
        """
        Gets the length of the dynamic array corresponding to address. Suppose dynamic
        array is {1,2,3,4,5} and starting address is 400 which is passed as address to this
        method, then method would return 20(i.e. 5 * sizeof(int)). When this address
        is offsetted for eg 408 is passed to this method, then it will return remainder
        number of bytes allocated, here it would be 12 (i.e. 420 - 408)
        Algorithm tries to find address in address_length_apping, if it doesn't find it
        then it tries to find the range that can fit the address. if it fails to find such
        mapping then it would return None.
        """

        length_found = HeapMapping.address_length_mapping.get(address)
        if length_found:
            return length_found
        else:
            address_list = list(HeapMapping.address_set)
            address_list.sort()
            left = 0
            right = len(address_list) - 1
            while left <= right:
                mid = int((left + right) / 2)
                if address > address_list[mid]:
                    left = mid + 1
                # only < case would be accounted in else.
                # As == would be handled in the if-check above (outside while)
                else:
                    right = mid - 1

            index = left - 1
            if index == -1:
                return None
            base_address = address_list[index]
            base_len = HeapMapping.address_length_mapping.get(base_address)
            if base_address + base_len > address:
                return base_address + base_len - address
            else:
                return None

    @staticmethod
    def remove(address):
        HeapMapping.address_length_mapping.pop(address, None)
        HeapMapping.address_set.discard(address)


class AllocationFinishedBreakpoint(gdb.FinishBreakpoint):
    """
    Sets temporary breakpoints on returns (specifically returns of memory allocations)
    to record address allocated.
    It get instantiated from AllocationBreakpoint and ReallocationBreakpoint. When it is
    instantiated from ReallocationBreakPoint, it carries prev_address.
    """

    def __init__(self, length, prev_address=None):
        super().__init__(internal=True)
        self.length = length
        self.prev_address = prev_address

    def stop(self):
        """
        Called when the return address in the current frame is hit. It parses hex address
        into int address. If return address is not null then it stores address and length
        into the address_length_mapping dictionary.
        """

        return_address = self.return_value
        if return_address is not None or return_address == 0x0:
            if self.prev_address != None:
                HeapMapping.remove(self.prev_address)

            # Converting hex address to int address
            int_address = parse_address_to_int(return_address)
            HeapMapping.put(int_address, self.length)
        return False


class AllocationBreakpoint(gdb.Breakpoint):
    """
    Handler class when malloc and operator new[] gets hit
    """

    def __init__(self, spec):
        super().__init__(spec, internal=True)

    def stop(self):
        # handle malloc and new
        func_args_string = gdb.execute('info args', to_string=True)
        if func_args_string.find("=") != -1:
            # There will be just 1 argument to malloc. So no need to handle multiline
            length = int(parse_gdb_equals(func_args_string))
            AllocationFinishedBreakpoint(length)
            return False


class ReallocationBreakpoint(gdb.Breakpoint):
    """
    Handler class when realloc gets hit
    """

    def __init__(self, spec):
        super().__init__(spec, internal=True)

    def stop(self):
        # handle realloc
        func_args_string = gdb.execute('info args', to_string=True)
        if func_args_string.find("=") != -1:
            args = func_args_string.split("\n")
            address = parse_gdb_equals(args[0])
            int_address = parse_address_to_int(address)
            length = int(parse_gdb_equals(args[1]))
            AllocationFinishedBreakpoint(length, int_address)
            return False


class DeallocationBreakpoint(gdb.Breakpoint):
    """
    Handler class when free and operator delete[] gets hit
    """

    def __init__(self, spec):
        super().__init__(spec, internal=True)

    def stop(self):
        func_args_string = gdb.execute('info args', to_string=True)
        if func_args_string.find("=") != -1:
            address = parse_gdb_equals(func_args_string)
            int_address = parse_address_to_int(address)
            HeapMapping.remove(int_address)
        return False


class WatchHeap(gdb.Command):
    """
    Custom Command to keep track of Heap Memory Allocation.
    Currently keeps tracks of memory allocated/deallocated using
    malloc, realloc, free, operator new[] and operator delete[]
    """

    def __init__(self):
        super(WatchHeap, self).__init__("watch_heap", gdb.COMMAND_USER)

    def complete(self, text, word):
        return gdb.COMPLETE_COMMAND

    def invoke(self, args, from_tty):
        # TODO : Check whether break location methods are defined
        AllocationBreakpoint("malloc")
        AllocationBreakpoint("operator new[]")
        ReallocationBreakpoint("realloc")
        DeallocationBreakpoint("free")
        DeallocationBreakpoint("operator delete[]")


class PrintHeapPointer(gdb.Command):
    """
    Custom command to print memory allocated at dynamic time
    """

    def __init__(self):
        super(PrintHeapPointer, self).__init__("print_ptr", gdb.COMMAND_USER)

    def complete(self, text, word):
        return gdb.COMPLETE_COMMAND

    def invoke(self, args, from_tty=True):
        try:
            value = gdb.parse_and_eval(args)
            if value.type.code == gdb.TYPE_CODE_PTR:
                print("Type : ", value.type)
                starting_address_string = gdb.execute(
                    'p/x {}'.format(value), to_string=True)
                print("Address: ",
                      parse_gdb_equals(starting_address_string))
                int_address = parse_address_to_int(value)
                # print memory
                self.print_heap(int_address)
        except Exception:
            print('No symbol found!')

    def print_heap(self, address):
        """
        Prints the memory that is being pointed by address in hex format

        Parameters
        ---------
        address : raw pointer
        """

        memory_size = HeapMapping.get(address)
        if memory_size:
            print('Length :', memory_size)
            result = ''
            i = 0
            while i < memory_size:
                byte_string = gdb.execute(
                    'x/1bx {}'.format(address), to_string=True)
                result += byte_string.split(':')[1].strip() + " "
                address += 1
                i += 1
            print(result)
        else:
            print("No address mapping found!")


if __name__ == '__main__':
    WatchHeap()
    PrintHeapPointer()
