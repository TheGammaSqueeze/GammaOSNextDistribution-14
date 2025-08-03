.class final Llibcore/java/lang/recordclasses/InvalidEmptyRecord5;
.super Ljava/lang/Record;
.source "InvalidEmptyRecord5.java"

.annotation system Ldalvik/annotation/Record;
    componentNames = {
        "x",
        "y"
    }
    componentTypes = {
        I,
        Ljava/lang/Object;
    }
    # componentAnnotationVisibilities must exist if componentAnnotations exists
    componentAnnotations = {
        {
            .subannotation  Lcrossvmtest/java/lang/RecordComponentTest$CustomAnnotation;
                value = "a"
            .end subannotation
        },
        {
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
