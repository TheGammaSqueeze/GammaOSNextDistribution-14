#!/bin/bash
set -e
set -u

# depends on 'm apf_disassembler'
"${ANDROID_HOST_OUT}/bin/apf_disassembler" < apf2java.in > apf2java.txt

sed -r \
's@: li +r([01]), (-?[0-9]+)@: gen.addLoadImmediate(R\1, \2);@;'\
's@: and +r0, (-?[0-9]+)@: gen.addAnd(\1);@;'\
's@: add +r0, (-?[0-9]+)@: gen.addAdd(\1);@;'\
's@: add +r0, r1@: gen.addAddR1();@;'\
's@: swap +@: gen.addSwap();@;'\
's@: neg +r([01])@: gen.addNeg(R\1);@;'\
's@: jmp +(PASS|DROP)@: gen.addJump(\1_LABEL);@;'\
's@: jnebs +r0, 0x([0-9a-f]+), ([0-9]+), ([0-9a-f]+)@: gen.addJumpIfBytesAtR0NotEqual(hexStringToByteArray("\3"), LABEL_\2);@;'\
's@: jeq +r([01]), 0x([0-9a-f]+), ([0-9]+)@: gen.addJumpIfR\1Equals(0x\2, LABEL_\3);@;'\
's@: jne +r([01]), 0x([0-9a-f]+), ([0-9]+)@: gen.addJumpIfR\1NotEquals(0x\2, LABEL_\3);@;'\
's@: jlt +r([01]), 0x([0-9a-f]+), ([0-9]+)@: gen.addJumpIfR\1LessThan(0x\2, LABEL_\3);@;'\
's@: jgt +r([01]), 0x([0-9a-f]+), ([0-9]+)@: gen.addJumpIfR\1GreaterThan(0x\2, LABEL_\3);@;'\
's@: jset +r([01]), 0x([0-9a-f]+), ([0-9]+)@: gen.addJumpIfR\1AnyBitsSet(0x\2, LABEL_\3);@;'\
's@: jmp +([0-9]+)@: gen.addJump(LABEL_\1);@;'\
's@: lddw +r0, \[r1\]@: gen.addLoadData(R0, 0);@;'\
's@: stdw +r0, \[r1\]@: gen.addStoreData(R0, 0);@;'\
's@: ldb +r([01]), \[([0-9]+)\]@: gen.addLoad8(R\1, \2);@;'\
's@: ldh +r([01]), \[([0-9]+)\]@: gen.addLoad16(R\1, \2);@;'\
's@: ldw +r([01]), \[([0-9]+)\]@: gen.addLoad32(R\1, \2);@;'\
's@: ldbx +r([01]), \[r1\+([0-9]+)\]@: gen.addLoad8Indexed(R\1, \2);@;'\
's@: ldhx +r([01]), \[r1\+([0-9]+)\]@: gen.addLoad16Indexed(R\1, \2);@;'\
's@: ldwx +r([01]), \[r1\+([0-9]+)\]@: gen.addLoad32Indexed(R\1, \2);@;'\
's@: ldm +r([01]), m\[([0-9]+)\]@: gen.addLoadFromMemory(R\1, \2);@;'\
'/addJumpIfR0(Greater|Less)Than/s@(0x[8-f][0-9a-f]{7})@\1L@;'\
< apf2java.txt > tmp
declare -ar LABELS=($(sed -rn 's@.*LABEL_([0-9]+).*@\1@p' < tmp | sort -u))
for L in "${LABELS[@]}"; do
  #echo "[LABEL_${L}]"
  sed -r "s@^( +${L}:)@\ngen.defineLabel(LABEL_${L});\n\1@" < tmp > tmp2
  cat tmp2 > tmp
done

sed -r \
's@^ +[0-9]+: @@;'\
's@(LABEL_[0-9]+)@"\1"@;'\
"s@\"LABEL_${LABELS[-1]}\"@\"LABEL_INC_AND_DROP\"@;"\
"s@\"LABEL_${LABELS[-2]}\"@\"LABEL_INC_AND_PASS\"@;"\
"s@\"LABEL_${LABELS[-3]}\"@\"LABEL_UNSOLICITED_MULTICAST_NA\"@;"\
< tmp > tmp2
# The above label renames are based on what our current generator emits as prologue.

if [[ "$(egrep -v '^$|gen' < tmp2 | wc -l)" != 0 ]]; then
  echo 'Failure to translate:'
  egrep -v '^$|gen' < tmp2
  exit 1
fi

{
  echo '    @Test'
  echo '    public void testFullApfV4ProgramGeneration() throws IllegalInstructionException {'
  echo '        ApfV4Generator gen = new ApfV4Generator(APF_VERSION_4);'
  sed -r 's@^(.+)$@        \1@' < tmp2
  echo
  echo '        byte[] program = gen.generate();'
  echo '        final String programString = toHexString(program).toLowerCase();'
  echo -n '        final String referenceProgramHexString = "'
  tr -d '\n' < apf2java.in
  echo '";'
  echo '        assertEquals(referenceProgramHexString, programString);'
  echo '    }'
} > apf2java.out

rm -f tmp tmp2
