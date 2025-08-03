.class Llibcore/java/lang/recordclasses/ValidEmptyRecordWithoutRecordAnnotation;
.super Ljava/lang/Record;
.source "ValidEmptyRecordWithoutRecordAnnotation.java"

# This class has only @Signature, but no @Record annotation, but doesn't cause ART to throw.
.annotation system Ldalvik/annotation/Signature;
    value={ "Ljava/lang/Object;" }
.end annotation


# direct methods
.method constructor <init>()V
    .registers 1

    .line 7
    invoke-direct {p0}, Ljava/lang/Record;-><init>()V

    return-void
.end method
