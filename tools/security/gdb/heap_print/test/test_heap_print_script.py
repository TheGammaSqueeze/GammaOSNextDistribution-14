import unittest
import gdb


def get_n(n_str):
    return int(n_str.split("=")[1].strip())


class HeapMemoryTest(unittest.TestCase):

    def setUp(self):
        gdb.execute('set pagination on')
        gdb.execute("file test/sample_heap_test.o")
        gdb.execute("source heap_print_script.py")
        gdb.execute("delete")
        gdb.execute("watch_heap")

    def check_memory(self, n, array_ptr_str, offset=1):
        """
        It is used to test what we got from 'print_ptr' is what we expect.
        Sample test program allocates array of n int's using malloc and then
        assigns 1 to n values to that array. So checking that malloc_ptr_str
        is 1 to n, following big endian size and size of int as 32 bits

        Parameters
        ----------
        n : int
            array length
        array_ptr_str : str
            whole output from print_ptr command including memory content
        offset : int
            checking memory content starts from offset value. By default it is 1
        """

        data = array_ptr_str.split("\n")[3]
        bytes_from_heap = data.split(" ")
        actual_start = offset
        for i in range(0, n * 4, 4):
            hex_str = bytes_from_heap[i+3][2:]
            hex_str += bytes_from_heap[i+2][2:]
            hex_str += bytes_from_heap[i+1][2:]
            hex_str += bytes_from_heap[i][2:]
            int_of_hex = int(hex_str, 16)
            self.assertEqual(actual_start, int_of_hex)
            actual_start += 1

    def test_malloc(self):
        print("malloc test")
        gdb.execute("b 20")
        gdb.execute("r")
        n_str = gdb.execute("print n", to_string=True)
        n = get_n(n_str)
        malloc_ptr_array_str = gdb.execute(
            "print_ptr malloc_ptr", to_string=True)
        print(malloc_ptr_array_str)
        self.check_memory(n, malloc_ptr_array_str)
        self.assertTrue(True)

    def test_realloc(self):
        print("realloc test")
        gdb.execute("b 27")
        gdb.execute("r")
        new_n = gdb.execute("print new_n", to_string=True)
        n = get_n(new_n)
        malloc_ptr_str = gdb.execute("print_ptr malloc_ptr", to_string=True)
        print(malloc_ptr_str)
        self.check_memory(n, malloc_ptr_str)

    def test_offset(self):
        """
        Testcase to test raw_pointers that are offset
        """

        print("offset test. we have array of 20 (80 bytes) and \
                we offset it by 3, so new length should be 68")
        offset = 3
        gdb.execute("b 27")
        gdb.execute("r")
        new_n = gdb.execute("print new_n", to_string=True)
        n = get_n(new_n)
        malloc_ptr_str = gdb.execute(
            "print_ptr malloc_ptr + {}".format(offset), to_string=True)
        print(malloc_ptr_str)
        self.check_memory(n - offset, malloc_ptr_str, offset+1)

    def test_free(self):
        print("free test")
        gdb.execute("b 28")
        gdb.execute("r")
        malloc_ptr_str = gdb.execute("print_ptr malloc_ptr", to_string=True)
        data = malloc_ptr_str.split("\n")[2].strip()
        self.assertEqual(data, "No address mapping found!")

    def test_new(self):
        print("operator new[] test")
        gdb.execute("b 41")
        gdb.execute("r")
        n_str = gdb.execute("print n", to_string=True)
        n = get_n(n_str)
        new_ptr_array_str = gdb.execute("print_ptr new_ptr", to_string=True)
        self.check_memory(n, new_ptr_array_str)

    def test_delete(self):
        print("operator delete[]")
        gdb.execute("b 42")
        gdb.execute("r")
        new_ptr_array_str = gdb.execute("print_ptr new_ptr", to_string=True)
        data = new_ptr_array_str.split("\n")[2].strip()
        self.assertEqual(data, "No address mapping found!")


if __name__ == '__main__':
    unittest.main()
