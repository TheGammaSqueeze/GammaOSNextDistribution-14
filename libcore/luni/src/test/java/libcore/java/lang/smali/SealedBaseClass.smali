.class Llibcore/java/lang/sealedclasses/SealedBaseClass;
.super Ljava/lang/Object;
.source "SealedBaseClass.java"


# annotations
.annotation system Ldalvik/annotation/PermittedSubclasses;
    value = {
        Llibcore/java/lang/sealedclasses/FinalDerivedClass;,
        Llibcore/java/lang/sealedclasses/SealedDerivedClass;,
        Llibcore/java/lang/sealedclasses/StandaloneClass;
    }
.end annotation


# direct methods
.method constructor <init>()V
    .registers 1

    .line 8
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method
