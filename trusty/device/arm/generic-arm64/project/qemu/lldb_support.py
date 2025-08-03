import os
import re
from glob import glob
from uuid import UUID


SEC_DRAM_BASE = 0xe200000
TRUSTY_PROJECT_FOLDER = os.path.dirname(os.path.realpath(__file__))
TRUSTY_PROJECT = (
    # pylint: disable=line-too-long
    re.match(r"^.*/build-([^/]+)$", TRUSTY_PROJECT_FOLDER).group(1)  #type: ignore
)
KERNEL_ELF_FILE = f"{TRUSTY_PROJECT_FOLDER}/lk.elf"
ADDR_SIZE_BITS = 64
ARCH_ID = "aarch64"


def kernel_entry_point(debugger):
    """Finds the starting virtual address of the kernel.

    Assumes the kernel is the first target loaded into the debugger.
    """
    target = debugger.GetTargetAtIndex(0)
    module = target.GetModuleAtIndex(0)
    section = module.GetSectionAtIndex(0)
    return section.GetFileAddress()


def lldb_module_offset(original_entry_point, new_entry_point):
    """Calculate an LLDB module load offset.

    Because LLDB only support sliding ELF entry points, not setting them, this
    function calculates the appropriate offset given the original entry point
    and the desired entry point.
    """
    assert original_entry_point > 0
    assert original_entry_point < (1 << ADDR_SIZE_BITS)

    assert new_entry_point > 0
    assert new_entry_point < (1 << ADDR_SIZE_BITS)

    if original_entry_point == new_entry_point:
        return 0

    return (
        (new_entry_point - original_entry_point) &
        ((1 << ADDR_SIZE_BITS) - 1)
    )


def move_kernel_lldb_module(debugger, new_entry_point):
    original_entry_point = kernel_entry_point(debugger)
    offset = lldb_module_offset(original_entry_point, new_entry_point)
    lk_elf = os.path.basename(KERNEL_ELF_FILE)
    debugger.HandleCommand(
        f"target modules load -f {lk_elf} -s {hex(offset)}"
    )


def parse_uuid_bytes(uuid_bytes):
    """Parse a UUID from a manifest file header's bytes."""
    # pylint: disable=line-too-long
    return UUID(
        f"{uuid_bytes[3]:02x}{uuid_bytes[2]:02x}{uuid_bytes[1]:02x}{uuid_bytes[0]:02x}-"
        f"{uuid_bytes[5]:02x}{uuid_bytes[4]:02x}-"
        f"{uuid_bytes[7]:02x}{uuid_bytes[6]:02x}-"
        f"{uuid_bytes[8]:02x}{uuid_bytes[9]:02x}-"
        f"{uuid_bytes[10]:02x}{uuid_bytes[11]:02x}{uuid_bytes[12]:02x}{uuid_bytes[13]:02x}{uuid_bytes[14]:02x}{uuid_bytes[15]:02x}"
    )


def parse_uuid_struct(uuid_struct):
    """Parse a UUID from a Trusty UUID struct pulled from LLDB."""
    time_low = uuid_struct.GetChildMemberWithName(
        "time_low").GetValueAsUnsigned()
    time_mid = uuid_struct.GetChildMemberWithName(
        "time_mid").GetValueAsUnsigned()
    time_hi_and_version = uuid_struct.GetChildMemberWithName(
        "time_hi_and_version").GetValueAsUnsigned()
    clock_seq_and_node = [
        uuid_struct.GetChildMemberWithName(
            "clock_seq_and_node").GetChildAtIndex(i).GetValueAsUnsigned()
        for i in range(8)
    ]

    return UUID(
        f"{time_low:08x}-"
        f"{time_mid:04x}-"
        f"{time_hi_and_version:04x}-"
        f"{''.join([f'{byte:02x}' for byte in clock_seq_and_node])}"
    )


# After initialization, this will be a dict mapping TA UUIDs to symbol file
# paths.
uuid_symbol_map = None


def init_symbols_file_map():
    global uuid_symbol_map  # pylint: disable=global-statement

    uuid_symbol_map = {}

    for manifest_path in glob(
            f"{TRUSTY_PROJECT_FOLDER}/user_tasks/**/*.manifest",
            recursive=True):
        symbol_path = manifest_path.removesuffix(".manifest") + ".syms.elf"

        if not os.path.exists(symbol_path):
            continue

        with open(manifest_path, "rb") as manifest_file:
            uuid_bytes = manifest_file.read(16)

        uuid = parse_uuid_bytes(uuid_bytes)
        uuid_symbol_map[uuid] = symbol_path


def trusty_thread_start_hook(debugger, _command, context, result,
                             _internal_dict):
    """Breakpoint command for extracting TA load_biases and loading
     corresponding ELF files with those load biases.
    """
    trusty_app = context.GetFrame().FindVariable(
        "trusty_thread").GetChildMemberWithName("app")
    load_bias = trusty_app.GetChildMemberWithName(
        "load_bias").GetValueAsUnsigned()
    uuid_struct = trusty_app.GetChildMemberWithName(
        "props").GetChildMemberWithName("uuid")

    uuid = parse_uuid_struct(uuid_struct)

    symbols_file_path = uuid_symbol_map.get(uuid)
    if symbols_file_path is None:
        print(f"Could not find symbols file for UUID {uuid}", file=result)
        return

    debugger.HandleCommand(f"target modules add {symbols_file_path}")
    symbols_file_name = os.path.basename(symbols_file_path)
    print(f"Setting {symbols_file_name} entry point to {hex(load_bias)}")
    debugger.HandleCommand(
        f"target modules load -f {symbols_file_name} -s {hex(load_bias)}"
    )
    debugger.HandleCommand("continue")


def initialize_kernel_lldb_module(debugger, _command, _context, result,
                                  _internal_dict):
    print("Loading kernel ELF file", file=result)
    debugger.HandleCommand(f"file -a {ARCH_ID} {KERNEL_ELF_FILE}")

    print(
        f"Setting kernel ELF entry point to {hex(SEC_DRAM_BASE)}",
        file=result
    )
    move_kernel_lldb_module(debugger, new_entry_point=SEC_DRAM_BASE)


def relocate_kernel_hook(debugger, _command, context, result, _internal_dict):
    new_base = context.GetFrame().FindVariable("new_base").GetValueAsUnsigned()

    print(f"Setting kernel ELF entry point to {hex(new_base)}", file=result)
    move_kernel_lldb_module(debugger, new_entry_point=new_base)
    debugger.HandleCommand("continue")


def register_command(debugger, function):
    fname = function.__name__
    debugger.HandleCommand(f"command script add -f {__name__}.{fname} {fname}")


def __lldb_init_module(debugger, _internal_dict):
    init_symbols_file_map()

    register_command(debugger, initialize_kernel_lldb_module)
    register_command(debugger, relocate_kernel_hook)
    register_command(debugger, trusty_thread_start_hook)
