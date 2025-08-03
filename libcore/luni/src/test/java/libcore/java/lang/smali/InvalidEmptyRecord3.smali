.class final Llibcore/java/lang/recordclasses/InvalidEmptyRecord3;
.super Ljava/lang/Record;
.source "InvalidEmptyRecord3.java"


# annotations
.annotation system Ldalvik/annotation/Record;
    # componentNames can't have sub-array.
    componentNames = { { "j" } }
    componentTypes = { I }
.end annotation


# direct methods
.method constructor <init>()V
    .registers 1

    .line 7
    invoke-direct {p0}, Ljava/lang/Record;-><init>()V

    return-void
.end method
