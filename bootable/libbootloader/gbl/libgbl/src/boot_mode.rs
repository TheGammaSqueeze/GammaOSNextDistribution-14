// Copyright 2023, The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

//! Possible boot modes.

// TODO: b/312605899 - find full list of supported boot modes
// Looks like we need only compliant items from map: system/core/bootstat/bootstat.cpp
// kBootReasonMap
// It might be required to assemble this type in format: <reason>,<sub_reason>,<detil>,...
// Bootloaders must provide a kernel set or a blunt set reason, and are strongly encouraged to
// provide a subreason if it can be determined. For example, a power key long press that may or may
// not have ramoops backup would have the boot reason "reboot,longkey".
/* good reasons from kBootReasonMap
{"reboot,[empty]", kEmptyBootReason},
{"recovery", 3},
{"reboot", 4},
{"kernel_panic", 7},
{"watchdog", 40},
{"shutdown,", 45},  // Trailing comma is intentional. Do NOT use.
{"shutdown,userrequested", 46},
{"reboot,bootloader", 47},
{"reboot,cold", 48},
{"reboot,recovery", 49},
{"kernel_panic,sysrq", 52},
{"kernel_panic,null", 53},
{"kernel_panic,bug", 54},
{"bootloader", 55},
{"cold", 56},
{"hard", 57},
{"warm", 58},
{"reboot,kernel_power_off_charging__reboot_system", 59},  // Can not happen
{"shutdown,thermal", 61},
{"shutdown,battery", 62},
{"reboot,ota", 63},
{"reboot,factory_reset", 64},
{"reboot,", 65},
{"reboot,shell", 66},
{"reboot,adb", 67},
{"reboot,userrequested", 68},
{"shutdown,container", 69},  // Host OS asking Android Container to shutdown
{"cold,powerkey", 70},
{"warm,s3_wakeup", 71},
{"hard,hw_reset", 72},
{"shutdown,suspend", 73},    // Suspend to RAM
{"shutdown,hibernate", 74},  // Suspend to DISK
{"reboot,by_key", 84},
{"reboot,longkey", 85},
{"reboot,2sec", 86},  // Deprecate in two years, replaced with cold,rtc,2sec
{"shutdown,thermal,battery", 87},
{"reboot,its_just_so_hard", 88},  // produced by boot_reason_test
{"reboot,rescueparty", 90},
{"reboot,powerloss", 119},
{"reboot,undervoltage", 120},
{"cold,charger", 148},
{"cold,rtc", 149},
{"cold,rtc,2sec", 150},   // Mediatek
{"reboot,tool", 151},     // Mediatek
{"reboot,wdt", 152},      // Mediatek
{"reboot,unknown", 153},  // Mediatek
{"kernel_panic,audit", 154},
{"kernel_panic,atomic", 155},
{"kernel_panic,hung", 156},
{"kernel_panic,hung,rcu", 157},
{"kernel_panic,init", 158},
{"kernel_panic,oom", 159},
{"kernel_panic,stack", 160},
{"kernel_panic,sysrq,livelock,alarm", 161},   // llkd
{"kernel_panic,sysrq,livelock,driver", 162},  // llkd
{"kernel_panic,sysrq,livelock,zombie", 163},  // llkd
{"kernel_panic,modem", 164},
{"kernel_panic,adsp", 165},
{"kernel_panic,dsps", 166},
{"kernel_panic,wcnss", 167},
{"kernel_panic,_sde_encoder_phys_cmd_handle_ppdone_timeout", 168},
{"recovery,quiescent", 169},
{"reboot,quiescent", 170},
{"reboot,rtc", 171},
{"reboot,dm-verity_device_corrupted", 172},
{"reboot,dm-verity_enforcing", 173},
{"reboot,keys_clear", 174},
{"reboot,pmic_off_fault,.*", 175},
{"reboot,pmic_off_s3rst,.*", 176},
{"reboot,pmic_off_other,.*", 177},
{"reboot,userrequested,fastboot", 178},
{"reboot,userrequested,recovery", 179},
{"reboot,userrequested,recovery,ui", 180},
{"shutdown,userrequested,fastboot", 181},
{"shutdown,userrequested,recovery", 182},
{"reboot,unknown[0-9]*", 183},
{"reboot,longkey,.*", 184},
{"reboot,boringssl-self-check-failed", 185},
{"reboot,userspace_failed,shutdown_aborted", 186},
{"reboot,userspace_failed,watchdog_triggered", 187},
{"reboot,userspace_failed,watchdog_fork", 188},
{"reboot,userspace_failed,*", 189},
{"reboot,mount_userdata_failed", 190},
{"reboot,forcedsilent", 191},
{"reboot,forcednonsilent", 192},
{"reboot,thermal,tj", 193},
{"reboot,emergency", 194},
{"reboot,factory", 195},
{"reboot,fastboot", 196},
{"reboot,gsa,hard", 197},
{"reboot,gsa,soft", 198},
{"reboot,master_dc,fault_n", 199},
{"reboot,master_dc,reset", 200},
{"reboot,ocp", 201},
{"reboot,pin", 202},
{"reboot,rom_recovery", 203},
{"reboot,uvlo", 204},
{"reboot,uvlo,pmic,if", 205},
{"reboot,uvlo,pmic,main", 206},
{"reboot,uvlo,pmic,sub", 207},
{"reboot,warm", 208},
{"watchdog,aoc", 209},
{"watchdog,apc", 210},
{"watchdog,apc,bl,debug,early", 211},
{"watchdog,apc,bl,early", 212},
{"watchdog,apc,early", 213},
{"watchdog,apm", 214},
{"watchdog,gsa,hard", 215},
{"watchdog,gsa,soft", 216},
{"watchdog,pmucal", 217},
{"reboot,early,bl", 218},
{"watchdog,apc,gsa,crashed", 219},
{"watchdog,apc,bl31,crashed", 220},
{"watchdog,apc,pbl,crashed", 221},
{"reboot,memory_protect,hyp", 222},
{"reboot,tsd,pmic,main", 223},
{"reboot,tsd,pmic,sub", 224},
{"reboot,ocp,pmic,main", 225},
{"reboot,ocp,pmic,sub", 226},
{"reboot,sys_ldo_ok,pmic,main", 227},
{"reboot,sys_ldo_ok,pmic,sub", 228},
{"reboot,smpl_timeout,pmic,main", 229},
{"reboot,ota,.*", 230},
{"reboot,periodic,.*", 231},
*/

/// Boot mode
///
/// This is subset of compliant tems from map: system/core/bootstat/bootstat.cpp kBootReasonMap
// Underlying format is <reason>,<sub_reason>,<detil>,...
#[derive(Debug, PartialEq, Copy, Clone)]
pub enum BootMode {
    /// Normal system start
    Normal,
    /// Recovery mode
    Recovery,
    /// Request to boot into bootloader mode staying in CMD-line or fastboot mode.
    Bootloader,
    // TODO: b/312605899 - need full list of supported modes
    // Quiescent,
}
