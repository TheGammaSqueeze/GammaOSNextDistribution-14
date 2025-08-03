#! /bin/bash -eu

# This script prints linker trace for a given ELF file.
# It extracts the command that has built this ELF file from the
# build log (verbose.log.gz file in the given output directory),
# appends `-t` linker option to it in order to print its source
# files and runs this command
# This script can be used when we want to compare ELF executables
# built by two different configurations (e.g., conventional and mixed
# builds). In this case, we run the build for one configuration, then
# rename `out` directory to something else, and then run the build
# for another configuration. To accommodate this scenario, an optional
# second argument specifies the renamed output directory. The linker
# command then runs inside nsjail that maps the renamed output
# directory to `out`.

function die() { format=$1; shift; printf >&2 "$format\n" $@; exit 1; }
function usage() {
  die "usage: ${0##*/} [-v] ELF [DIR]"
}

# Delouse
declare show_command=
while getopts "v" opt; do
  case $opt in
  v) show_command=t ;;
  *) usage ;;
  esac
done
shift $(($OPTIND-1))
(($# >= 1)) || usage

declare -r elf="$1"; shift
declare -r outdir="${1:-out}"
[[ -d "$outdir" ]] || die "$outdir does not exist"
[[ -f "$outdir/verbose.log.gz" ]] || \
  die "$outdir does not contain Android build (verbose.log.gz is missing)"

function zgrep_command() {
    zgrep -e "bin/clang\+\+.* -o [^ ]*$elf " $outdir/verbose.log.gz
}

# Locate the command that builds this ELF file and write it to
# the temporary file editing it on the way:
#  * remove step number (`[nn/NN]`) prefix
#  * linker should write to the bit bucket
#  * add `-Wl,-t` (linker's `-t` option)
cmdfile=$(mktemp); trap 'rm -f $cmdfile' EXIT
zgrep_command |\
  sed -r 's| -o ([^ ]+) | -Wl,-t -o /dev/null |;s|^\[.*\]||' > $cmdfile
[[ -z "${show_command}" ]] || cat $cmdfile >&2
[[ -s $cmdfile ]] || die "no ELF file ending with $elf was built in $outdir"
(($(wc -l $cmdfile | cut -f1 -d ' ') == 1)) || \
  { printf >&2 "Multiple elf files ending with $elf were built in $outdir:\n";
    die "  %s" $(zgrep_command | sed -r 's|.* -o ([^ ]+) .*|\1|'); }

# Run the linker (i.e., the command we have written into $cmdfile). Its output
# is the list of the object files it read. If output directory has been renamed,
# run it inside `nsjail`, mapping output directory ot `out/`
if [[ "$outdir" == out ]]; then
    /bin/bash $cmdfile
else
    prebuilts/build-tools/linux-x86/bin/nsjail \
    -Mo -q -e -t 0 -B / -B /tmp -B $(realpath $outdir):$PWD/out \
    --cwd $PWD --skip_setsid --keep_caps --disable_clone_newcgroup --disable_clone_newnet \
    --rlimit_as soft --rlimit_core soft --rlimit_cpu soft --rlimit_fsize soft --rlimit_nofile soft \
    --proc_rw --hostname "$(hostname)" -- /bin/bash $cmdfile
fi
