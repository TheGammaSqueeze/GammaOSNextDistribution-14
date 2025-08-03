.class Llibcore/java/lang/sealedclasses/SealedDerivedClass;
.super Llibcore/java/lang/sealedclasses/SealedBaseClass;
.source "SealedDerivedClass.java"


# annotations
.annotation system Ldalvik/annotation/PermittedSubclasses;
    value = {
        Llibcore/java/lang/sealedclasses/OpenDerivedClass;
    }
.end annotation


# direct methods
.method constructor <init>()V
    .registers 1

    .line 6
    invoke-direct {p0}, Llibcore/java/lang/sealedclasses/SealedBaseClass;-><init>()V

    return-void
.end method
