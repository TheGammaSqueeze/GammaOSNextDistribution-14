package org.bouncycastle.its.asn1;

import org.bouncycastle.asn1.ASN1Sequence;

public class ImplicitCertificate
    extends CertificateBase
{
    private ImplicitCertificate(ASN1Sequence seq)
    {
        super(seq);
    }
}
