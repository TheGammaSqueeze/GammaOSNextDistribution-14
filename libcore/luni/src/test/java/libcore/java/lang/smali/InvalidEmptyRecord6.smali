.class final Llibcore/java/lang/recordclasses/InvalidEmptyRecord6;
.super Ljava/lang/Record;
.source "InvalidEmptyRecord6.java"

.annotation system Ldalvik/annotation/Record;
    componentNames = {
        "x"
    }
    componentTypes = {
        I
    }
    componentAnnotationVisibilities = {
        {
            1 # visibility needs to byte, not int.
        }
    }
    componentAnnotations = {
        {
            .subannotation  Lcrossvmtest/java/lang/RecordComponentTest$CustomAnnotation;
                value = "a"
            .end subannotation
        }
    }
.end annotation


# direct methods
.method constructor <init>()V
    .registers 1

    .line 7
    invoke-direct {p0}, Ljava/lang/Record;-><init>()V

    return-void
.end method
