This directory contains .smali files used to generate a .dex file used
by libcore.java.lang.ClassTest.

The use of .smali allows the generation of annotations for class metadata
that cannot be currently produced. For example the NestHost and NestMembers
annotations.

The files are included in core-tests-smali.dex through the
core-tests-smali-dex build component. Manually a .dex file can be generated
with:

make smali
smali assemble libcore/luni/src/test/java/libcore/java/lang/smali/*.smali \
    -o libcore/luni/src/test/resources/libcore/java/lang/smali/testclasses.dex

---------------------

For reference, the classes started out as:

// NestGroupHost.java

package libcore.java.lang.nestgroup;

import dalvik.annotation.NestMembers;

@NestMembers(classes={NestGroupInnerA.class,
                      NestGroupB.class})
public class NestGroupHost {
}

// NestGroupInnerA.java

package libcore.java.lang.nestgroup;

import dalvik.annotation.NestHost;

@NestHost(host=NestGroupHost.class)
public class NestGroupInnerA {
}

// NestGroupB.java

package libcore.java.lang.nestgroup;

import dalvik.annotation.NestHost;

@NestHost(host=NestGroupB.class)
public class NestGroupB {
}

// NestGroupInnerFake.java

package libcore.java.lang.nestgroup;

import dalvik.annotation.NestHost;

@NestHost(host=NestGroupHost.class)
public class NestGroupInnerFake {
}

// NestGroupSelf.java

package libcore.java.lang.nestgroup;

import dalvik.annotation.NestHost;

public class NestGroupSelf {
}

// SealedBaseClass.java

package libcore.java.lang.sealedclasses;

import dalvik.annotation.PermittedSubclasses;

@PermittedSubclasses(classes={FinalDerivedClass.class,
                              SealedDerivedClass.class,
                              StandaloneClass.class})
class SealedBaseClass {
}

// FinalDerivedClass.java

package libcore.java.lang.sealedclasses;

import dalvik.annotation.PermittedSubclasses;

final class FinalDerivedClass extends SealedBaseClass {
}

// SealedDerivedClass.java

package libcore.java.lang.sealedclasses;

import dalvik.annotation.PermittedSubclasses;

@PermittedSubclasses(classes={OpenDerivedClass.class})
class SealedDerivedClass extends SealedBaseClass {
}

// OpenDerivedClass.java

package libcore.java.lang.sealedclasses;

import dalvik.annotation.PermittedSubclasses;

class OpenDerivedClass extends SealedDerivedClass {
}

// StandaloneClass.java

package libcore.java.lang.sealedclasses;

import dalvik.annotation.PermittedSubclasses;

class StandaloneClass {
}

// SealedFinalClass.java

package libcore.java.lang.sealedclasses;

import dalvik.annotation.PermittedSubclasses;

@PermittedSubclasses(classes={FinalDerivedClass.class})
final class SealedFinalClass {
}

// RecordClassA.java

package libcore.java.lang.recordclasses;

import dalvik.annotation.Record;

@Record(componentNames={"x", "y"},
        componentTypes={int.class, Integer.class})
final class RecordClassA {
}

// NonFinalRecordClass.java

package libcore.java.lang.recordclasses;

import dalvik.annotation.Record;

@Record(componentNames={"x", "y"},
        componentTypes={int.class, Integer.class})
class NonFinalRecordClass {
}

// EmptyRecordClass.java

package libcore.java.lang.recordclasses;

import dalvik.annotation.Record;

@Record(componentNames={},
        componentTypes={})
final class EmptyRecordClass {
}

// UnequalComponentArraysRecordClass.java

package libcore.java.lang.recordclasses;

import dalvik.annotation.Record;

@Record(componentNames={"x", "y"},
        componentTypes={int.class})
final class UnequalComponentArraysRecordClass {
}
