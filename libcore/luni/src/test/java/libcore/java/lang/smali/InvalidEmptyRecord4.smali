.class final Llibcore/java/lang/recordclasses/InvalidEmptyRecord4;
.super Ljava/lang/Record;
.source "InvalidEmptyRecord4.java"

.annotation system Ldalvik/annotation/Record;
    componentNames = {
        "x",
        "y"
    }
    componentTypes = {
        I,
        Ljava/lang/Object;
    }
    # componentSignatures should have a size of 2, instead of a size 1.
    # Dexer shouldn't trim the empty tail to save dex size.
    componentSignatures = {
        .subannotation Ldalvik/annotation/Signature;
            value = {
                "TX;"
            }
        .end subannotation
    }
.end annotation


# direct methods
.method constructor <init>()V
    .registers 1

    .line 7
    invoke-direct {p0}, Ljava/lang/Record;-><init>()V

    return-void
.end method
