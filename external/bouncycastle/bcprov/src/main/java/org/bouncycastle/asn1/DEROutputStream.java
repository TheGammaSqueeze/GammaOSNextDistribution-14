package org.bouncycastle.asn1;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Stream that outputs encoding based on distinguished encoding rules.
 */
// BEGIN Android-changed: Class is package-private in upstream.
// Leaving as public as it's used by build/make/tools/signapk/src/com/android/signapk/SignApk.java
public class DEROutputStream
    extends ASN1OutputStream
{
    public DEROutputStream(OutputStream os)
    {
        super(os);
    }

    void writePrimitive(ASN1Primitive primitive, boolean withTag) throws IOException
    {
        primitive.toDERObject().encode(this, withTag);
    }

    DEROutputStream getDERSubStream()
    {
        return this;
    }

    ASN1OutputStream getDLSubStream()
    {
        return this;
    }
}
