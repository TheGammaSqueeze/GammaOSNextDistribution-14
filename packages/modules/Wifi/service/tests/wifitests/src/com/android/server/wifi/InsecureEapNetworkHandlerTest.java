/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.wifi;

import static com.android.server.wifi.InsecureEapNetworkHandler.TOFU_ANONYMOUS_IDENTITY;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiContext;
import android.net.wifi.WifiEnterpriseConfig;
import android.os.Handler;
import android.os.test.TestLooper;
import android.text.TextUtils;
import android.text.format.DateFormat;

import androidx.test.filters.SmallTest;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.util.CertificateSubjectInfo;
import com.android.wifi.resources.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.stubbing.Answer;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Unit tests for {@link com.android.server.wifi.InsecureEapNetworkHandlerTest}.
 */
@SmallTest
public class InsecureEapNetworkHandlerTest extends WifiBaseTest {

    private static final int ACTION_ACCEPT = 0;
    private static final int ACTION_REJECT = 1;
    private static final int ACTION_TAP = 2;
    private static final int ACTION_FORGET = 3;
    private static final String WIFI_IFACE_NAME = "wlan-test-9";
    private static final int FRAMEWORK_NETWORK_ID = 2;
    private static final String TEST_SSID = "\"test_ssid\"";
    private static final String TEST_IDENTITY = "userid";
    private static final String TEST_PASSWORD = "myPassWord!";
    private static final String TEST_EXPECTED_SHA_256_SIGNATURE = "54:59:5D:FC:64:9C:17:72:C0:59:"
            + "9D:25:BD:1F:04:18:E6:00:AB:F4:0A:F0:78:D8:9A:FF:56:C0:7C:89:96:2F";
    private static final int TEST_GEN_CA_CERT = 0;
    private static final int TEST_GEN_CA2_CERT = 1;
    private static final int TEST_GEN_SERVER_CERT = 2;
    private static final int TEST_GEN_SELF_SIGNED_CERT = 3;
    private static final int TEST_GEN_FAKE_CA_CERT = 4;

    private static final String TEST_SERVER_CERTIFICATE = "-----BEGIN CERTIFICATE-----\n"
            + "MIIGPjCCBCagAwIBAgIUN2Ss1JmvjveRe97iWoNh4V+Y5LYwDQYJKoZIhvcNAQEM\n"
            + "BQAwgZcxCzAJBgNVBAYTAlVTMRMwEQYDVQQIDApDYWxpZm9ybmlhMRswGQYDVQQK\n"
            + "DBJBbmRyb2lkIFdpLUZpIFRlc3QxGDAWBgNVBAsMD2FuZHJvaWR3aWZpLm9lbTE8\n"
            + "MDoGA1UEAwwzQW5kcm9pZCBQYXJ0bmVyIFJvb3QgQ0EgZm9yIHRlc3RpbmcgYW5k\n"
            + "IGRldmVsb3BtZW50MB4XDTIzMDQxMzAyMTYwMVoXDTQzMDQwODAyMTYwMVowgYMx\n"
            + "CzAJBgNVBAYTAlVTMRMwEQYDVQQIDApDYWxpZm9ybmlhMR0wGwYDVQQKDBRBbmRy\n"
            + "b2lkIFdpLUZpIFRlc3RlcjEYMBYGA1UECwwPYW5kcm9pZHdpZmkub2VtMSYwJAYD\n"
            + "VQQDDB1BbmRyb2lkIFdpLUZpIE9FTSBUZXN0IFNlcnZlcjCCAiIwDQYJKoZIhvcN\n"
            + "AQEBBQADggIPADCCAgoCggIBAKveC9QnsxvM2TMzkUINabtM2Bi5M5gzV4v1MN0h\n"
            + "n1XjXhfRXwwLMK9xtV05r91YQaOTPkHNgA6nhjmL7agcquGPlR7nuS04oxCaqfo4\n"
            + "unbroyyqDMaXd8U6B1VlvWSbWAAhBEEAPYDhFXF9V83XHEGcp61Hs4VetGmlC3tW\n"
            + "W1CLIk+o9JRYsZeK4Q1DurAY7YPU8U84QNxPG7OXg+ensGtspuLLNFEdnd9tSi45\n"
            + "u5KyPpnSwTdRGSCfMVocxj0EINpdrLnWZyf9NX8Uo7tg/D0TFVBo+MbKjgItIdMg\n"
            + "STLQwceOdOGHZTPiItzpFcP9EA5ug5gXobPjzDTJO2S3NhUt5NURfGr/wyepxR25\n"
            + "PDRhBgc/xwc7JrtDGaqmknguZuf7Zai/m4iquC0Wh38bWKms8R0ND/H923aFppxp\n"
            + "vzX/sWotsTYWiGMehh7v6iwIYADifsXBlJXTUhTZt6cnwttZYfp5oqymCsIhXKVU\n"
            + "IXOE/PLcU71G9U+jCa7PNs5X5LgqorNPABOpkVL+fDpvopNCdhOEVvwCAIl4tIxl\n"
            + "M0goFbBmY1wnFFYIUki91UfbeUimCUbBq/RSxuXn3liVB/X+dnyjJ3RnNxJ3Wy1m\n"
            + "mcHFIVV5VxN6tC7XTXYgZAv0EJGCcVn0RN3ldPWGRLTEIQu7cXRSfqs89N4S31Et\n"
            + "SjaxAgMBAAGjgZMwgZAwHQYDVR0OBBYEFHh9fcIU3LHamK7PdpasvHmzyRoLMB8G\n"
            + "A1UdIwQYMBaAFH7ro7AWsBlMNpyRXHGW1hG4c1ocMAkGA1UdEwQCMAAwCwYDVR0P\n"
            + "BAQDAgWgMBMGA1UdJQQMMAoGCCsGAQUFBwMBMCEGA1UdEQQaMBiCFnNlcnZlci5h\n"
            + "bmRyb2lkd2lmaS5vZW0wDQYJKoZIhvcNAQEMBQADggIBAOIkOLyF8mmYvj8TeM2V\n"
            + "d4YMj4sWf7L5C2lq9OGBJwZad1xytymWWZ7PpNf1MopabfUzxPjw5EfMC94MJmpf\n"
            + "gqYOwFAye5fXQ8CLC39tb681u44tv/B5vqP74TKVhCR8O1YCsIssa8t8e5nIwcYr\n"
            + "fj3SBu7iOLtL7zjfEXFo3oSEwVYnvS3lhZL8NTrrHscy/ZLFE3nGRq2d3jPbyuoH\n"
            + "1FJwenxnD6a/AztERPkRNGk2oSFkWecNU9PC9w3bI5wF4I2AIaFgBOj20S7pVtq7\n"
            + "7nhKnQFrZYVeWbqbInQcRAcSopI6D6tB/F/T9R1WCWBxvpwdciv7BeNgOtGKAszA\n"
            + "z0sOxI6O4U77R+tFeb0vCwC0OhVL3W0zX3Fy2835D/hC2P1jmMBlxLVKYHY48RBC\n"
            + "sG1I1qAMD4eXle8rG9MkB9cE5KfncjCrzSQjT8gs7QBTafb6B3WDdwzfaCaQTOOF\n"
            + "Tsyrdq0TTJP71bt5qWTr6UZIBE5Tjel+DPpvQlPZPYygXPrI3WBcT12VLhti0II6\n"
            + "1jgkS8fPLR0VypHR02V5fqCRmy9ln0rSyHXFwL3JpeXYD92eLOKdS1MhIUN4bDxZ\n"
            + "fiXXVKpKU4gqqWAan2RjbBzQjsi6Eh3yuDm2SAqNZVacpOt7BIslqEZ+Og6KhTTk\n"
            + "DCzyEOB87ySrUWu3PN3r2sJN\n"
            + "-----END CERTIFICATE-----";

    private static final String TEST_CA_CERTIFICATE = "-----BEGIN CERTIFICATE-----\n"
            + "MIIGADCCA+igAwIBAgIUFkmrYCj/UYNrizDdMATu6dE3lBIwDQYJKoZIhvcNAQEM\n"
            + "BQAwgZcxCzAJBgNVBAYTAlVTMRMwEQYDVQQIDApDYWxpZm9ybmlhMRswGQYDVQQK\n"
            + "DBJBbmRyb2lkIFdpLUZpIFRlc3QxGDAWBgNVBAsMD2FuZHJvaWR3aWZpLm9lbTE8\n"
            + "MDoGA1UEAwwzQW5kcm9pZCBQYXJ0bmVyIFJvb3QgQ0EgZm9yIHRlc3RpbmcgYW5k\n"
            + "IGRldmVsb3BtZW50MB4XDTIzMDQxMzAyMTYwMVoXDTQzMDQwODAyMTYwMVowgZcx\n"
            + "CzAJBgNVBAYTAlVTMRMwEQYDVQQIDApDYWxpZm9ybmlhMRswGQYDVQQKDBJBbmRy\n"
            + "b2lkIFdpLUZpIFRlc3QxGDAWBgNVBAsMD2FuZHJvaWR3aWZpLm9lbTE8MDoGA1UE\n"
            + "AwwzQW5kcm9pZCBQYXJ0bmVyIFJvb3QgQ0EgZm9yIHRlc3RpbmcgYW5kIGRldmVs\n"
            + "b3BtZW50MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEA9JERd2HVp/PI\n"
            + "3WmaaHDKHDuWZoxUDlyVrTDd1Vu2zhH3A5KJ232QOMxJiLdZ/KxgcpGlAEllijae\n"
            + "xihxhkHEYr7ff2/p6ZhUWr+0vuk8f4TZsKDAE0SoZoDBHTIbrJf8hHM5/+R//sx1\n"
            + "/fTf8abOj20zyeWmXqvUNXoVKiRvjiZD69tcRHmfmTOMX0lAirOel8ZwwDFamH8d\n"
            + "wov0IIyd58m6CV91WnScgg7TOzw/IGpccft73RbDw7cHU5i3G3KhOqamwJbErgya\n"
            + "x97AsSVCqjBz7rEwm6pHjUagbgVAk9ULmI1McQzMINIrOWRF0Q8awWpvDNwPu86J\n"
            + "W/LfyzAruWtriimycpl7wv0b/f7JhKerG0+44JUI0sgTz/kobAsU8nfYSyVu8+cX\n"
            + "HwnDE2jBGB6co2Y00eVKxy6+gWTekpQTyHuPoCieNDukC/38Mj+U0KUZkgGv4CL7\n"
            + "zaVBGzjSjtnAp47aXciaDvDbpST23ICS7TN5cUnXQ1fWfNUMNkEbIPy2mrlRoCxg\n"
            + "OJ67UEvGIygE0IUvwDfFvF21+1yKk6D/kU9gMgd6DKtvWj1CIyKXWf+rQ01OHNhX\n"
            + "YcOTkF5aF2WU558DuS+utGBzXWFsLxqBRe9nDb9W/SlrT2jajfwLelMddvtZmVsY\n"
            + "NG8IeY8lDs5hcFBvm/BDr0SvBDhs9H0CAwEAAaNCMEAwHQYDVR0OBBYEFH7ro7AW\n"
            + "sBlMNpyRXHGW1hG4c1ocMA8GA1UdEwEB/wQFMAMBAf8wDgYDVR0PAQH/BAQDAgGG\n"
            + "MA0GCSqGSIb3DQEBDAUAA4ICAQBINF6auWUhCO0l/AqO5pLgOqZ7BjAzHGDNapW+\n"
            + "3nn2YicDD/X2eJASfsd3jN5JluBlbLqRBBWnIsNG/fyKxY8I4+IrR1x8ovwBjeJ3\n"
            + "McQeCW2zedluVp2SW3LaNQS+aptXHATJ6O8EOny2LDM+obEtFyLuDC89a1TXjEdj\n"
            + "XGIYmSJ8RwpKAi4u6ff4jhtNTSEa/eIUE5zUREV0916xtmu5y1vlmsEbpLEquOph\n"
            + "ZWxpUVTqGEyc0hHaivAWyBG1dtRgov5olzHchM2TsEq/VufiRAw5uzRQ/sAyVjj4\n"
            + "pcvWnLDLTYk/+uIG1zmbc0rNpAC7b3tplA4OqTtFb3yX0ppPFUg4OaxhMyu4WqS3\n"
            + "roNiXc8BmtfzMqyWAG21QUfosLa8heiiHgnvkiUa9V2oJ4kWAhOTmLdU70aocu4N\n"
            + "pcN5jcT5hSl/A91Lvfht0C9BLOrXU+RDCNAVIUnnWSrgduUPTydKVdUkLxau4G/+\n"
            + "G8fKAyeCouFNq7bp4DEMkgqAWpx96Qe6FLxAS59Ig3tI8MZSieBZezJyjP4GWtuq\n"
            + "QsnARbwD7z73FWQ+eqXOhkoqDoQc8E2lQGe8OGbacGuUwXo3PUgGaJobz+2Hqa9g\n"
            + "6AnBkH6AbvooUwSWSCyYIf2LA+GvZotI+PXWuQL7dqWtkaNf98qqfnlZXjp51e+h\n"
            + "B8nquw==\n"
            + "-----END CERTIFICATE-----";

    private static final String TEST_CA2_CERTIFICATE = "-----BEGIN CERTIFICATE-----\n"
            + "MIIGADCCA+igAwIBAgIUGm2nmrZw4ADU7h/TGKd67Uz5bJIwDQYJKoZIhvcNAQEM\n"
            + "BQAwgZcxCzAJBgNVBAYTAlVTMRMwEQYDVQQIDApDYWxpZm9ybmlhMRswGQYDVQQK\n"
            + "DBJBbmRyb2lkIFdpLUZpIFRlc3QxGDAWBgNVBAsMD2FuZHJvaWR3aWZpLm9lbTE8\n"
            + "MDoGA1UEAwwzQW5vdGhlciBBbmRyb2lkIFJvb3QgQ0EgZm9yIHRlc3RpbmcgYW5k\n"
            + "IGRldmVsb3BtZW50MB4XDTIzMDQxMzAyMTkxOVoXDTQzMDQwODAyMTkxOVowgZcx\n"
            + "CzAJBgNVBAYTAlVTMRMwEQYDVQQIDApDYWxpZm9ybmlhMRswGQYDVQQKDBJBbmRy\n"
            + "b2lkIFdpLUZpIFRlc3QxGDAWBgNVBAsMD2FuZHJvaWR3aWZpLm9lbTE8MDoGA1UE\n"
            + "AwwzQW5vdGhlciBBbmRyb2lkIFJvb3QgQ0EgZm9yIHRlc3RpbmcgYW5kIGRldmVs\n"
            + "b3BtZW50MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAvv7PYhFHK+nC\n"
            + "KoDiQI7dhDFTNU4RxTTsxMRSt1n/FJGZX/r8nnr76gB+oofFVjKQusYhuquKGPGq\n"
            + "ZfrfmtsNhcVBMnNRjZkBWpNb3XO+7F+Qd/gT7yoiZ0L3Ef4QMCGqNrf10EWmXvVQ\n"
            + "tpaM7RrkmlW6Zu2VbfP/iQQ7EVFrFWmnZfkCxpkLT+LK+pxwNxtJz5l7VRYkXelw\n"
            + "9vFdq81C+obBpLWg62mNVNa25g6y46YrSOPyxhiemiRih+avIZ9Z6/7qRoVu7t8U\n"
            + "NpxzMdsDL5bJREadsjpQWZr7A+umm0nlod1DB204K18Y5Z4GuOEGifdHIUmb+3c4\n"
            + "Kz14FzBahyc3xsZL73AsGEVWLHIQQ/kjepomVl8HuSHdgw6SZR30JhWgU/bcVl01\n"
            + "8qc6qH7x3e64Ip9xHdng42oPJHEKYipRed3AXzlCQ7Lc9MeAeR+nB9JuSNc6HW0L\n"
            + "eh9Po0cDJa194UfNeqJ7SG2uNpeg/OUbM+M3iO3dmCRcV3GzirbT8eHZk3Cor3gb\n"
            + "h9AzmJnHyRaRc9Xtj7AE8swJRvAoWVlCzcBcvaLAW0hn2DWXbWXHDf63Q8n5F4J5\n"
            + "pf//2eXWaOXFLvkm9wYUj6kXOehcibB2O1F1YvqWE3XZ5GTDq/+E5wK55aifq+bz\n"
            + "l1Mb1ILIB3cEEL9w+0ClHCno+2XGMOkCAwEAAaNCMEAwHQYDVR0OBBYEFH0KeaUK\n"
            + "koS2PMYfpcanoTkRBTzmMA8GA1UdEwEB/wQFMAMBAf8wDgYDVR0PAQH/BAQDAgGG\n"
            + "MA0GCSqGSIb3DQEBDAUAA4ICAQCnnL83fEA3g8fiJHpeSNSVZ4R3VunQU2BDkwUj\n"
            + "NgFWPMsZNQcKoChUA5mb8wOM/fk9tdjMsQR5fRO30B94Eoo9NM39HztBcvvLV9i7\n"
            + "qNQCTjFE7zf4weX6K3tZICR8nZ1Ogccp3itEDkNpOylFLdQxkc29RrKcuculb3PM\n"
            + "C7IMREKROKFzrAwWkFAaxJGfByTRfjOqWJFgdRq/GHU2yCKkCLN4zRLjr5ZaAk2J\n"
            + "+8b+Y1/pIW4j2FAB7ebmq0ZbMbdc+EFdVf36WrsWf54L3DsZOuoaC+2wTsyWQ0b/\n"
            + "8tqJ/XS39I4uo8KpI5//aQpM1usxP0/pWUm9sTXE618Yf2Ynh64eDQHPIAmt+Xoh\n"
            + "BfIx+nXVkCl4DGGdwvOURUULdHN9wf6YPOXxaMEYxQRGMwmBAlmiDaH41xeaht/A\n"
            + "+iv3y918rJFDAXWKvGia8oDi1xIL+IDZ1AGVByNp+C/AE5BTV2m9UHZyXsXrMiQA\n"
            + "ezUrVpiWB6h4C4rUuaucQv1gO6gEPZGEDdvIG8TGJg8wvLL0oZiyaL3gQxlGs0CZ\n"
            + "tbDGqugtlh4RLeJ1N/TTFkLzf4CAgDTxfqhMKXkFvpMvO6ZHOT7xC0sdaD2FbZRj\n"
            + "h5ziC9nvWEdTA8RLr0i/r5nFb6GsxmEk6NYFmpnyo5pvlxf5xqOhsJZlcKnUJ8SQ\n"
            + "NIGLmw==\n"
            + "-----END CERTIFICATE-----";

    private static final String TEST_SELF_SIGNED_CERTIFICATE = "-----BEGIN CERTIFICATE-----\n"
            + "MIIFxzCCA6+gAwIBAgIUB8Kqwhhhs1liW23ve7pZsFlv0zAwDQYJKoZIhvcNAQEM\n"
            + "BQAwezELMAkGA1UEBhMCVVMxEzARBgNVBAgMCkNhbGlmb3JuaWExGzAZBgNVBAoM\n"
            + "EkFuZHJvaWQgV2ktRmkgVGVzdDEYMBYGA1UECwwPYW5kcm9pZHdpZmkuZGV2MSAw\n"
            + "HgYDVQQDDBdTZWxmLXNpZ25lZCBjZXJ0aWZpY2F0ZTAeFw0yMzA0MTMwMjE0MTda\n"
            + "Fw00MzA0MDgwMjE0MTdaMHsxCzAJBgNVBAYTAlVTMRMwEQYDVQQIDApDYWxpZm9y\n"
            + "bmlhMRswGQYDVQQKDBJBbmRyb2lkIFdpLUZpIFRlc3QxGDAWBgNVBAsMD2FuZHJv\n"
            + "aWR3aWZpLmRldjEgMB4GA1UEAwwXU2VsZi1zaWduZWQgY2VydGlmaWNhdGUwggIi\n"
            + "MA0GCSqGSIb3DQEBAQUAA4ICDwAwggIKAoICAQDD0oI2c+1D4D2wc4PnhkXbtA7g\n"
            + "64Mp/JSbnVbl7PseJKnFD/pdos5joFXFbySFqu60S905T1a3AWNwbucKc7C7IwQw\n"
            + "gtO7uMEPr35j7MhItyAbmj89dY729yXJ8gBnNnqc8PyYEIfZmnBvSry0Tsm60298\n"
            + "GGZ9yCQfOOb4TJFX/CIKjniI170eLCiGybOrBvG11Rx6BwwHnk1cjkDspejrkhb0\n"
            + "13RfkQ1S0cEnylrgnn/nRDAAnOscpHRerJ6Ud2vM64iIJy206ZyU/CrhcGeBWwi9\n"
            + "C1F4ojzvgoFW7bJahXiyEaC5R3G5WdvX5qOr/eu/yMaCAner0LHUibHc5XA02F/c\n"
            + "LO0LpN59tTT4dx9sLJVjZQGSUxyXnKHiR5TKkoAMWAZSO5hbE4drgivKLnYmYnhC\n"
            + "Z1rGM5R0D0gB2llAvecItmynDJNApY6L1F8wnNA9NfGUYFpeqJ8uEOn7RxAvyYmB\n"
            + "trmUFOqL7W84d1/XzORPGQ7n1wyPfBG3xyGIm2MMvanVsLs0/9NXAYAz2ZAHJPnS\n"
            + "DsiV+7OHtMCdgTI5BJFmiJpXKgVE+IaewQdSjXDU7bgMlll3lTVoVAiKJmxpOmZ6\n"
            + "FFz7mkd0pYhsO5jQpNGMfl+IaoIiTx4Zg9ZjwjTcPn9eGunBLJJ8SofkhM4boLrC\n"
            + "KSen8NYuHVDPwAOwpQIDAQABo0MwQTAdBgNVHQ4EFgQU2IB1Q35ysx0HpRttAqMU\n"
            + "FO9OhIAwCwYDVR0PBAQDAgWgMBMGA1UdJQQMMAoGCCsGAQUFBwMBMA0GCSqGSIb3\n"
            + "DQEBDAUAA4ICAQBqf4zbooJ4hHeZs7Nd31G7i2WEr6/8OWTuEuDAmJkGW8WBeyUG\n"
            + "JMrxF3U+kGxVGops4mSxMwVpcsqQa+IwkrP807dSqkuntLVGFqVo7rcQ+l8OcNUv\n"
            + "oNQIFGPLlWPyMnjXvmWbfvgbgFoY9yUFoxFlHqsVf+1mEvTmW9ya4BGT2hlfvtb6\n"
            + "Jfvrrocg9zGSnBs9oyI+GzP4Xdqd0riXfk6OuFH3R05/cQj7SlPm8LU1J7ZML/4H\n"
            + "1AuMg+Ql8vxql4IzIk93CDR8Hq1jb3MhF/ae9UfttuNnHT4vu5X/6qLqWNKMs3zP\n"
            + "DQQaYkqxWTUWiNlWV7i7pXn8e2J8ZkRHVELvrpdXLKIfL6RxjzKWY+TKiHY+F48I\n"
            + "JwCAbL1FX+NzB2dS0RxXk/RTAxagenfmDcY1notHNsnDZB54cP9nv+N3wqkDoaKg\n"
            + "nqOZTlIRWJ4agygqGaxieUuZRgy/AE/dSGpetlXAScKUvhCcO22qXL2jSjBAg5+k\n"
            + "AynUuiZxdogXbvXrAwSWAVwlz8qEOK3NPFYnEKcjgNbTxiUHp3P/ULBgHQo55o9K\n"
            + "DdUEbIurd02xG6usEDWxR5ds/RPy6VZ5c6bFUiTEsfMMmQotPL/btuPVXsSdJUR4\n"
            + "xcxpcV7zx9IjFs/IylyQ1YEYDKWV+nH7iiOigO5WiZ5ck2Wa/Tk3uXg1Ew==\n"
            + "-----END CERTIFICATE-----\n";

    private static final String TEST_FAKE_CA_CERTIFICATE = "-----BEGIN CERTIFICATE-----\n"
            + "MIIGADCCA+igAwIBAgIUIxVGWM5Wrs86DpDA2+fo53UryqMwDQYJKoZIhvcNAQEM\n"
            + "BQAwgZcxCzAJBgNVBAYTAlVTMRMwEQYDVQQIDApDYWxpZm9ybmlhMRswGQYDVQQK\n"
            + "DBJBbmRyb2lkIFdpLUZpIFRlc3QxGDAWBgNVBAsMD2FuZHJvaWR3aWZpLm9lbTE8\n"
            + "MDoGA1UEAwwzQW5kcm9pZCBQYXJ0bmVyIFJvb3QgQ0EgZm9yIHRlc3RpbmcgYW5k\n"
            + "IGRldmVsb3BtZW50MB4XDTIzMDQxMzE1MzkyM1oXDTQzMDQwODE1MzkyM1owgZcx\n"
            + "CzAJBgNVBAYTAlVTMRMwEQYDVQQIDApDYWxpZm9ybmlhMRswGQYDVQQKDBJBbmRy\n"
            + "b2lkIFdpLUZpIFRlc3QxGDAWBgNVBAsMD2FuZHJvaWR3aWZpLm9lbTE8MDoGA1UE\n"
            + "AwwzQW5kcm9pZCBQYXJ0bmVyIFJvb3QgQ0EgZm9yIHRlc3RpbmcgYW5kIGRldmVs\n"
            + "b3BtZW50MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAhTF8MJsucR5P\n"
            + "6oN/Nho92EYz9b3m7n52m9KgI/G6/9bK9PSDZ6Z6U3qNxpG7nFML+5qyk+qeBHP8\n"
            + "39lGNNoH1c2dQDXw3oLjOmd1UoN+zSZBznLwkDD8YQYafz1GWRcI34FYDgiPuSx7\n"
            + "o4+O4hxcimrelhoNRQcRsrZFoUyJZjtPy2Z5DTZTB7udg1QwZ+7+pHCme3DB2Im/\n"
            + "Eszsmm2TAG6yM3G/lxphLZMhUFy6kjeeIiuar56ED6dg7qEqdeIznt2gGKolXRWs\n"
            + "vPW4a5NX1RUjsQxOcKEQnrXZXJ9mATptY1hOxuP6kg8Jzh0tN/NzyyERGFvnvhGz\n"
            + "sN7CkTUhPOKUW3dVrKl9ZJ9PbYZ6xbpbOWOR/5znYQ/f3+bxxibbFI3WN/89VO50\n"
            + "WEzwfmiGiWC6Bz0iBoAmGjCxySbJg8iDCjrbRexkFsOJ84jlY0fDrfaqY1+WuyYu\n"
            + "vdk+w4lzk0wYRbp+oRuIXplMyZDsS15CPq+svoYeNCCOXlkRiMLuq/SpkdM8lRKp\n"
            + "Mrsc1AckI+BGVqh8S9lyJoP67uDmba1FUw7X3IMCkZQwvFduLkJLNYwO6QDV2M6R\n"
            + "nUCVCx+vxJdlIOLNQIAeKW9jzfASom4ehZY2HHErbUYGKzFQJJ/2+uQLLYn7PsaE\n"
            + "gYTYA1naakQegCgbD2UsbKqrEfOiHEECAwEAAaNCMEAwHQYDVR0OBBYEFBiYeS/E\n"
            + "IQ5+IoQ3bsXoibK3QuMzMA8GA1UdEwEB/wQFMAMBAf8wDgYDVR0PAQH/BAQDAgGG\n"
            + "MA0GCSqGSIb3DQEBDAUAA4ICAQACOOZdfcQ53BF43glA/0VuFeXQ+VS+keR9aFBX\n"
            + "caKrIbFXSns9EsnvKj1L/IoHem5k4LkMFoNgE1Skit2iktPYs6unV83p99PG6EQo\n"
            + "RG1CeZ50cHzZK6N56MbwZUGRy/p6Zr9ak9d6XE8GpvSwMW8XebrLPtgSBvpI+WIZ\n"
            + "epMVg7v8AIIRQuoR2VtZ7RZF/X1kwfU5t2aASVBnxTjlSy6KtBLuL+Vu4Aefa+Z0\n"
            + "d9Ma2jZV+hwWp0X6piSrVKkMZIR5tlvwJootNBlO0J1Jn4J0ecGNEGXmFwz4adnK\n"
            + "eYfpuNBJI4CKq7mv2Aszsvg0rQxfKlN8LV7gSNu3H6BjjkNUtHI6uwsajJfEmGKD\n"
            + "YRpAFgZq7FzRwoI8uWr0Bucz6+qxpISi48t0pmceSVpn6UV1UdSebLo8CX5P283F\n"
            + "yUqlw2hMpo22Gm3uW8GfPyHfMfsqfMU+7BCP38DDnhcGUO3CTINjREXUGtn6CuWS\n"
            + "ImhmATld6KJNtRCql3zQnaEO84IvKdFVOkm5q9qQjNWDr1oYsLhxoZJZjKK2rP5F\n"
            + "GRbMvqDhmzrV0yG+sIyW+aEjBl44bVjWQnFhGjtNr1BOOftSyjnseYiioLbiiaYG\n"
            + "9Mqu78VmTWJzfxyOP2QPK5K00jnVBZ+jQH0NyIE9yf2Cg/llfYRoHsz80cfY/DNt\n"
            + "jUR49A==\n"
            + "-----END CERTIFICATE-----";

    @Mock WifiContext mContext;
    @Mock WifiConfigManager mWifiConfigManager;
    @Mock WifiNative mWifiNative;
    @Mock FrameworkFacade mFrameworkFacade;
    @Mock WifiNotificationManager mWifiNotificationManager;
    @Mock WifiDialogManager mWifiDialogManager;
    @Mock InsecureEapNetworkHandler.InsecureEapNetworkHandlerCallbacks mCallbacks;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS) private Notification.Builder mNotificationBuilder;
    @Mock private WifiDialogManager.DialogHandle mTofuAlertDialog;
    @Mock private java.text.DateFormat mDateFormat;
    @Mock private KeyStore mKeyStore;

    @Captor ArgumentCaptor<BroadcastReceiver> mBroadcastReceiverCaptor;
    @Captor ArgumentCaptor<WifiConfigManager.OnNetworkUpdateListener>
        mOnNetworkUpdateListenerCaptor;

    TestLooper mLooper;
    Handler mHandler;
    MockResources mResources;
    InsecureEapNetworkHandler mInsecureEapNetworkHandler;

    private MockitoSession mSession;

    /**
     * Sets up for unit test
     */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mResources = new MockResources();
        when(mContext.getString(anyInt())).thenReturn("TestString");
        when(mContext.getString(anyInt(), any())).thenReturn("TestStringWithArgument");
        when(mContext.getText(anyInt())).thenReturn("TestStr");
        when(mContext.getString(eq(R.string.wifi_ca_cert_dialog_message_issuer_name_text),
                anyString()))
                .thenAnswer((Answer<String>) invocation ->
                        "Issuer Name:\n" + invocation.getArguments()[1] + "\n\n");
        when(mContext.getString(eq(R.string.wifi_ca_cert_dialog_message_server_name_text),
                anyString()))
                .thenAnswer((Answer<String>) invocation ->
                        "Server Name:\n" + invocation.getArguments()[1] + "\n\n");
        when(mContext.getString(eq(R.string.wifi_ca_cert_dialog_message_organization_text),
                anyString()))
                .thenAnswer((Answer<String>) invocation ->
                        "Organization:\n" + invocation.getArguments()[1] + "\n\n");
        when(mContext.getString(eq(R.string.wifi_ca_cert_dialog_message_contact_text),
                anyString()))
                .thenAnswer((Answer<String>) invocation ->
                        "Contact:\n" + invocation.getArguments()[1] + "\n\n");
        when(mContext.getString(eq(R.string.wifi_ca_cert_dialog_message_signature_name_text),
                anyString()))
                .thenAnswer((Answer<String>) invocation ->
                        "SHA-256 Fingerprint:\n" + invocation.getArguments()[1] + "\n\n");
        when(mContext.getWifiOverlayApkPkgName()).thenReturn("test.com.android.wifi.resources");
        when(mContext.getResources()).thenReturn(mResources);
        when(mWifiDialogManager.createLegacySimpleDialogWithUrl(
                any(), any(), any(), anyInt(), anyInt(), any(), any(), any(), any(), any()))
                .thenReturn(mTofuAlertDialog);
        when(mWifiDialogManager.createLegacySimpleDialog(
                any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(mTofuAlertDialog);

        when(mFrameworkFacade.makeNotificationBuilder(any(), any()))
                .thenReturn(mNotificationBuilder);

        // static mocking
        mSession = ExtendedMockito.mockitoSession()
                .mockStatic(DateFormat.class, withSettings().lenient())
                .mockStatic(KeyStore.class, withSettings().lenient())
                .startMocking();
        when(DateFormat.getMediumDateFormat(any())).thenReturn(mDateFormat);
        when(mDateFormat.format(any())).thenReturn("April 12, 2023");
        when(KeyStore.getInstance(anyString())).thenReturn(mKeyStore);

        mLooper = new TestLooper();
        mHandler = new Handler(mLooper.getLooper());
    }

    @After
    public void cleanUp() throws Exception {
        validateMockitoUsage();
        mSession.finishMocking();
    }

    /**
     * Verify Trust On First Use flow.
     * - This network is selected by a user.
     * - Accept the connection.
     */
    @Test
    public void verifyTrustOnFirstUseAcceptWhenConnectByUser() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        boolean isAtLeastT = true, isTrustOnFirstUseSupported = true, isUserSelected = true;
        boolean needUserApproval = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);
        verifyTrustOnFirstUseFlowWithDefaultCerts(config, ACTION_ACCEPT,
                isTrustOnFirstUseSupported, isUserSelected, needUserApproval);
    }

    /**
     * Verify Trust On First Use flow.
     * - This network is selected by a user.
     * - Reject the connection.
     */
    @Test
    public void verifyTrustOnFirstUseRejectWhenConnectByUser() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        boolean isAtLeastT = true, isTrustOnFirstUseSupported = true, isUserSelected = true;
        boolean needUserApproval = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);
        verifyTrustOnFirstUseFlowWithDefaultCerts(config, ACTION_REJECT,
                isTrustOnFirstUseSupported, isUserSelected, needUserApproval);
    }

    /**
     * Verify Trust On First Use flow.
     * - This network is auto-connected.
     * - Accept the connection.
     */
    @Test
    public void verifyTrustOnFirstUseAcceptWhenConnectByAutoConnect() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        boolean isAtLeastT = true, isTrustOnFirstUseSupported = true, isUserSelected = false;
        boolean needUserApproval = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);
        verifyTrustOnFirstUseFlowWithDefaultCerts(config, ACTION_ACCEPT,
                isTrustOnFirstUseSupported, isUserSelected, needUserApproval);
    }

    /**
     * Verify Trust On First Use flow.
     * - This network is auto-connected.
     * - Reject the connection.
     */
    @Test
    public void verifyTrustOnFirstUseRejectWhenConnectByAutoConnect() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        boolean isAtLeastT = true, isTrustOnFirstUseSupported = true, isUserSelected = false;
        boolean needUserApproval = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);
        verifyTrustOnFirstUseFlowWithDefaultCerts(config, ACTION_REJECT,
                isTrustOnFirstUseSupported, isUserSelected, needUserApproval);
    }

    /**
     * Verify Trust On First Use flow.
     * - This network is auto-connected.
     * - Tap the notification to show the dialog.
     */
    @Test
    public void verifyTrustOnFirstUseTapWhenConnectByAutoConnect() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        boolean isAtLeastT = true, isTrustOnFirstUseSupported = true, isUserSelected = false;
        boolean needUserApproval = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);
        verifyTrustOnFirstUseFlowWithDefaultCerts(config, ACTION_TAP,
                isTrustOnFirstUseSupported, isUserSelected, needUserApproval);
    }

    /**
     * Verify that it reports errors if there is no pending Root CA certifiate
     * with Trust On First Use support.
     */
    @Test
    public void verifyTrustOnFirstUseWhenTrustOnFirstUseNoPendingCert() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        boolean isAtLeastT = true, isTrustOnFirstUseSupported = true, isUserSelected = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);
        mInsecureEapNetworkHandler.startUserApprovalIfNecessary(isUserSelected);
        verify(mCallbacks).onError(eq(config.SSID));
        verify(mWifiConfigManager, atLeastOnce()).updateNetworkSelectionStatus(eq(config.networkId),
                eq(WifiConfiguration.NetworkSelectionStatus
                        .DISABLED_BY_WIFI_MANAGER));
    }

    /**
     * Verify that Trust On First Use is not supported on T.
     * It follows the same behavior on preT release.
     */
    @Test
    public void verifyTrustOnFirstUseWhenTrustOnFirstUseNotSupported() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        boolean isAtLeastT = true, isTrustOnFirstUseSupported = false, isUserSelected = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);
        mInsecureEapNetworkHandler.startUserApprovalIfNecessary(isUserSelected);
        verify(mCallbacks, never()).onError(any());
    }

    /**
     * Verify legacy insecure EAP network flow.
     * - This network is selected by a user.
     * - Accept the connection.
     */
    @Test
    public void verifyLegacyEapNetworkAcceptWhenConnectByUser() throws Exception {
        assumeFalse(SdkLevel.isAtLeastT());
        boolean isAtLeastT = false, isTrustOnFirstUseSupported = false, isUserSelected = true;
        boolean needUserApproval = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);
        verifyTrustOnFirstUseFlowWithDefaultCerts(config, ACTION_ACCEPT,
                isTrustOnFirstUseSupported, isUserSelected, needUserApproval);
    }

    /**
     * Verify legacy insecure EAP network flow.
     * - Trust On First Use is not supported.
     * - This network is selected by a user.
     * - Reject the connection.
     */
    @Test
    public void verifyLegacyEapNetworkRejectWhenConnectByUser() throws Exception {
        assumeFalse(SdkLevel.isAtLeastT());
        boolean isAtLeastT = false, isTrustOnFirstUseSupported = false, isUserSelected = true;
        boolean needUserApproval = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);
        verifyTrustOnFirstUseFlowWithDefaultCerts(config, ACTION_REJECT,
                isTrustOnFirstUseSupported, isUserSelected, needUserApproval);
    }

    /**
     * Verify legacy insecure EAP network flow.
     * - This network is auto-connected.
     * - Accept the connection.
     */
    @Test
    public void verifyLegacyEapNetworkAcceptWhenAutoConnect() throws Exception {
        assumeFalse(SdkLevel.isAtLeastT());
        boolean isAtLeastT = false, isTrustOnFirstUseSupported = false, isUserSelected = false;
        boolean needUserApproval = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);
        verifyTrustOnFirstUseFlowWithDefaultCerts(config, ACTION_ACCEPT,
                isTrustOnFirstUseSupported, isUserSelected, needUserApproval);
    }

    /**
     * Verify legacy insecure EAP network flow.
     * - Trust On First Use is not supported.
     * - This network is auto-connected.
     * - Reject the connection.
     */
    @Test
    public void verifyLegacyEapNetworkRejectWhenAutoConnect() throws Exception {
        assumeFalse(SdkLevel.isAtLeastT());
        boolean isAtLeastT = false, isTrustOnFirstUseSupported = false, isUserSelected = false;
        boolean needUserApproval = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);
        verifyTrustOnFirstUseFlowWithDefaultCerts(config, ACTION_REJECT,
                isTrustOnFirstUseSupported, isUserSelected, needUserApproval);
    }

    /**
     * Verify legacy insecure EAP network flow.
     * - This network is selected by a user.
     * - Tap the notification
     */
    @Test
    public void verifyLegacyEapNetworkOpenLinkWhenConnectByUser() throws Exception {
        assumeFalse(SdkLevel.isAtLeastT());
        boolean isAtLeastT = false, isTrustOnFirstUseSupported = false, isUserSelected = true;
        boolean needUserApproval = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);
        verifyTrustOnFirstUseFlowWithDefaultCerts(config, ACTION_TAP,
                isTrustOnFirstUseSupported, isUserSelected, needUserApproval);
    }

    /**
     * Verify Trust On First Use flow with server certificate pinning
     * - Single depth server certificate by signed by some unknown issuer, CA flag not set
     * - This network is selected by a user.
     * - Accept the connection.
     */
    @Test
    public void verifyTrustOnFirstUseFlowWithServerCertPinning1() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        runServerCertPinningTest(TEST_GEN_SERVER_CERT);
    }

    /**
     * Verify Trust On First Use flow with server certificate pinning
     * - Single depth server certificate by signed by some unknown issuer, CA flag set
     * - This network is selected by a user.
     * - Accept the connection.
     */
    @Test
    public void verifyTrustOnFirstUseFlowWithServerCertPinning2() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        runServerCertPinningTest(TEST_GEN_CA_CERT);
    }

    private void runServerCertPinningTest(int type)
            throws Exception {
        WifiConfiguration config = prepareWifiConfiguration(true);
        setupTest(config, true, true);

        CertificateEventInfo mockServerCert = generateMockCertEventInfo(type);
        mInsecureEapNetworkHandler.addPendingCertificate(config.networkId, 0, mockServerCert);
        verifyTrustOnFirstUseFlow(config, ACTION_ACCEPT, true,
                true, false, null, mockServerCert.getCert(),
                null);
    }

    /**
     * Verify Trust On First Use flow with the CA certificate path.
     * - Single depth server certificate by signed by a trusted Root CA from the trust store.
     * - This network is selected by a user.
     * - Accept the connection.
     */
    @Test
    public void verifyTrustOnFirstUseFlowWithRootCaFromFromStore() throws Exception {
        assumeTrue(SdkLevel.isAtLeastU());
        runServerCertFromTrustStoreTest();
    }

    private void runServerCertFromTrustStoreTest() throws Exception {
        WifiConfiguration config = configureTofuWithAidlVersion2Value(true);

        // CA_CERT is also in the trust store
        CertificateEventInfo mockCaCert = generateMockCertEventInfo(TEST_GEN_CA_CERT);
        CertificateEventInfo mockServerCert = generateMockCertEventInfo(TEST_GEN_SERVER_CERT);
        mInsecureEapNetworkHandler.addPendingCertificate(config.networkId, 1, mockCaCert);
        mInsecureEapNetworkHandler.addPendingCertificate(config.networkId, 0, mockServerCert);

        List<String> aliases = new ArrayList<>();
        aliases.add("TEST_GEN_CA_CERT");
        aliases.add("TEST_GEN_CA2_CERT");
        when(mKeyStore.aliases()).thenReturn(Collections.enumeration(aliases));
        when(mKeyStore.getCertificate("TEST_GEN_CA_CERT"))
                .thenReturn(getCertificate(TEST_GEN_CA_CERT));
        when(mKeyStore.getCertificate("TEST_GEN_CA2_CERT"))
                .thenReturn(getCertificate(TEST_GEN_CA2_CERT));
        verifyTrustOnFirstUseFlow(config, ACTION_ACCEPT, true,
                true, false, mockCaCert.getCert(), mockServerCert.getCert(),
                WifiConfigurationUtil.getSystemTrustStorePath());
    }

    private CertificateEventInfo generateMockCertEventInfo(int type) throws Exception {
        CertificateEventInfo certificateEventInfo = mock(CertificateEventInfo.class);
        X509Certificate cert = getCertificate(type);

        when(certificateEventInfo.getCert()).thenReturn(cert);
        when(certificateEventInfo.getCertHash()).thenReturn("12345678");
        return certificateEventInfo;
    }

    private X509Certificate getCertificate(int type) throws Exception {
        String certString;

        if (type == TEST_GEN_CA_CERT) {
            certString = TEST_CA_CERTIFICATE;
        } else if (type == TEST_GEN_CA2_CERT) {
            certString = TEST_CA2_CERTIFICATE;
        } else if (type == TEST_GEN_SERVER_CERT) {
            certString = TEST_SERVER_CERTIFICATE;
        } else if (type == TEST_GEN_SELF_SIGNED_CERT) {
            certString = TEST_SELF_SIGNED_CERTIFICATE;
        } else if (type == TEST_GEN_FAKE_CA_CERT) {
            certString = TEST_FAKE_CA_CERTIFICATE;
        } else {
            throw (new Exception());
        }

        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        InputStream in = new ByteArrayInputStream(certString.getBytes());
        return (X509Certificate) certFactory.generateCertificate(in);
    }

    private WifiConfiguration prepareWifiConfiguration(boolean isAtLeastT) {
        WifiConfiguration config = spy(WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.TTLS, WifiEnterpriseConfig.Phase2.MSCHAPV2));
        config.networkId = FRAMEWORK_NETWORK_ID;
        config.SSID = TEST_SSID;
        if (isAtLeastT) {
            config.enterpriseConfig.enableTrustOnFirstUse(true);
        }
        config.enterpriseConfig.setCaPath("");
        config.enterpriseConfig.setDomainSuffixMatch("");
        config.enterpriseConfig.setIdentity(TEST_IDENTITY);
        config.enterpriseConfig.setPassword(TEST_PASSWORD);
        return config;
    }

    private void setupTest(WifiConfiguration config,
            boolean isAtLeastT, boolean isTrustOnFirstUseSupported) {
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported, false);
    }

    private void setupTest(WifiConfiguration config,
            boolean isAtLeastT, boolean isTrustOnFirstUseSupported,
            boolean isInsecureEnterpriseConfigurationAllowed) {
        mInsecureEapNetworkHandler = new InsecureEapNetworkHandler(
                mContext,
                mWifiConfigManager,
                mWifiNative,
                mFrameworkFacade,
                mWifiNotificationManager,
                mWifiDialogManager,
                isTrustOnFirstUseSupported,
                isInsecureEnterpriseConfigurationAllowed,
                mCallbacks,
                WIFI_IFACE_NAME,
                mHandler);

        if (isTrustOnFirstUseSupported
                && (config.enterpriseConfig.getEapMethod() == WifiEnterpriseConfig.Eap.TTLS
                || config.enterpriseConfig.getEapMethod() == WifiEnterpriseConfig.Eap.PEAP)
                && config.enterpriseConfig.getPhase2Method() != WifiEnterpriseConfig.Phase2.NONE) {
            // Verify that the configuration contains an identity
            assertEquals(TEST_IDENTITY, config.enterpriseConfig.getIdentity());
            assertTrue(TextUtils.isEmpty(config.enterpriseConfig.getAnonymousIdentity()));
            assertEquals(TEST_PASSWORD, config.enterpriseConfig.getPassword());
        }
        mInsecureEapNetworkHandler.prepareConnection(config);

        if (isTrustOnFirstUseSupported && config.enterpriseConfig.isTrustOnFirstUseEnabled()
                && (config.enterpriseConfig.getEapMethod() == WifiEnterpriseConfig.Eap.TTLS
                || config.enterpriseConfig.getEapMethod() == WifiEnterpriseConfig.Eap.PEAP)
                && config.enterpriseConfig.getPhase2Method() != WifiEnterpriseConfig.Phase2.NONE) {
            // Verify identities are cleared
            assertTrue(TextUtils.isEmpty(config.enterpriseConfig.getIdentity()));
            assertEquals(TOFU_ANONYMOUS_IDENTITY, config.enterpriseConfig.getAnonymousIdentity());
            assertTrue(TextUtils.isEmpty(config.enterpriseConfig.getPassword()));
        }

        if (isTrustOnFirstUseSupported && config.enterpriseConfig.isTrustOnFirstUseEnabled()) {
            verify(mContext, atLeastOnce()).registerReceiver(
                    mBroadcastReceiverCaptor.capture(),
                    argThat(f -> f.hasAction(InsecureEapNetworkHandler.ACTION_CERT_NOTIF_TAP)),
                    eq(null),
                    eq(mHandler));
        } else if ((isTrustOnFirstUseSupported
                && !config.enterpriseConfig.isTrustOnFirstUseEnabled()
                && isInsecureEnterpriseConfigurationAllowed)
                || !isTrustOnFirstUseSupported) {
            verify(mContext, atLeastOnce()).registerReceiver(
                    mBroadcastReceiverCaptor.capture(),
                    argThat(f -> f.hasAction(InsecureEapNetworkHandler.ACTION_CERT_NOTIF_ACCEPT)
                            && f.hasAction(InsecureEapNetworkHandler.ACTION_CERT_NOTIF_REJECT)),
                    eq(null),
                    eq(mHandler));
        }

        verify(mWifiConfigManager).addOnNetworkUpdateListener(
                mOnNetworkUpdateListenerCaptor.capture());
    }

    /**
     * Verify Trust On First Use flow with a minimal cert chain
     * - This network is selected by a user.
     * - Accept the connection.
     */
    @Test
    public void verifyTrustOnFirstUseAcceptWhenConnectByUserWithMinimalChain() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        boolean isAtLeastT = true, isTrustOnFirstUseSupported = true, isUserSelected = true;
        boolean needUserApproval = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);

        CertificateEventInfo mockCaCert = generateMockCertEventInfo(TEST_GEN_CA_CERT);
        CertificateEventInfo mockServerCert = generateMockCertEventInfo(TEST_GEN_SERVER_CERT);
        mInsecureEapNetworkHandler.addPendingCertificate(config.networkId, 1, mockCaCert);
        mInsecureEapNetworkHandler.addPendingCertificate(config.networkId, 0, mockServerCert);

        verifyTrustOnFirstUseFlow(config, ACTION_ACCEPT, isTrustOnFirstUseSupported,
                isUserSelected, needUserApproval, mockCaCert.getCert(), mockServerCert.getCert(),
                null);
    }

    /**
     * Verify Trust On First Use flow with a self-signed CA cert.
     * - This network is selected by a user.
     * - Forget the connection.
     */
    @Test
    public void verifyTrustOnFirstUseForgetWhenConnectByUserWithSelfSignedCaCert()
            throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        boolean isAtLeastT = true, isTrustOnFirstUseSupported = true, isUserSelected = true;
        boolean needUserApproval = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);

        CertificateEventInfo mockSelfSignedCert =
                generateMockCertEventInfo(TEST_GEN_SELF_SIGNED_CERT);
        mInsecureEapNetworkHandler.addPendingCertificate(config.networkId, 0, mockSelfSignedCert);

        verifyTrustOnFirstUseFlow(config, ACTION_FORGET, isTrustOnFirstUseSupported,
                isUserSelected, needUserApproval, mockSelfSignedCert.getCert(),
                mockSelfSignedCert.getCert(), null);
    }

    /**
     * Verify that the connection should be terminated.
     * - TOFU is supported.
     * - Insecure EAP network is not allowed.
     * - No cert is received.
     */
    @Test
    public void verifyOnErrorWithoutCert() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        boolean isAtLeastT = true, isTrustOnFirstUseSupported = true, isUserSelected = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);

        mInsecureEapNetworkHandler.startUserApprovalIfNecessary(isUserSelected);
        verify(mCallbacks).onError(eq(config.SSID));
        verify(mWifiConfigManager, atLeastOnce()).updateNetworkSelectionStatus(eq(config.networkId),
                eq(WifiConfiguration.NetworkSelectionStatus
                        .DISABLED_BY_WIFI_MANAGER));
    }

    /**
     * Verify that the connection should be upgraded to TOFU.
     * - TOFU is supported.
     * - Insecure EAP network is not allowed.
     * - TOFU is not enabled
     */
    @Test
    public void verifyOnErrorWithTofuDisabledWhenInsecureEapNetworkIsNotAllowed()
            throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        boolean isAtLeastT = true, isTrustOnFirstUseSupported = true, isUserSelected = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        config.enterpriseConfig.enableTrustOnFirstUse(false);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);

        mInsecureEapNetworkHandler.addPendingCertificate(config.networkId, 1,
                generateMockCertEventInfo(TEST_GEN_CA_CERT));
        mInsecureEapNetworkHandler.addPendingCertificate(config.networkId, 0,
                generateMockCertEventInfo(TEST_GEN_SERVER_CERT));

        mInsecureEapNetworkHandler.startUserApprovalIfNecessary(isUserSelected);
        assertTrue(config.enterpriseConfig.isTrustOnFirstUseEnabled());
    }

    /**
     * Verify that no error occurs in insecure network handling flow.
     * - TOFU is supported.
     * - Insecure EAP network is allowed.
     * - TOFU is not enabled
     * - No user approval is needed.
     */
    @Test
    public void verifyNoErrorWithTofuDisabledWhenInsecureEapNetworkIsAllowed()
            throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        boolean isAtLeastT = true, isTrustOnFirstUseSupported = true, isUserSelected = true;
        boolean isInsecureEnterpriseConfigurationAllowed = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        config.enterpriseConfig.enableTrustOnFirstUse(false);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported,
                isInsecureEnterpriseConfigurationAllowed);

        mInsecureEapNetworkHandler.addPendingCertificate(config.networkId, 1,
                generateMockCertEventInfo(TEST_GEN_CA_CERT));
        mInsecureEapNetworkHandler.addPendingCertificate(config.networkId, 0,
                generateMockCertEventInfo(TEST_GEN_SERVER_CERT));

        mInsecureEapNetworkHandler.startUserApprovalIfNecessary(isUserSelected);
        verify(mCallbacks, never()).onError(any());
    }

    /**
     * Verify that is reports errors if the server cert issuer does not match the parent subject.
     */
    @Test
    public void verifyOnErrorWithIncompleteChain() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        boolean isAtLeastT = true, isTrustOnFirstUseSupported = true, isUserSelected = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);

        CertificateEventInfo mockCaCert = generateMockCertEventInfo(TEST_GEN_CA2_CERT);
        // Missing intermediate cert.
        CertificateEventInfo mockServerCert = generateMockCertEventInfo(TEST_GEN_SERVER_CERT);
        mInsecureEapNetworkHandler.addPendingCertificate(config.networkId, 1, mockCaCert);
        mInsecureEapNetworkHandler.addPendingCertificate(config.networkId, 0, mockServerCert);

        mInsecureEapNetworkHandler.startUserApprovalIfNecessary(isUserSelected);
        verify(mCallbacks).onError(eq(config.SSID));
        verify(mWifiConfigManager, atLeastOnce()).updateNetworkSelectionStatus(eq(config.networkId),
                eq(WifiConfiguration.NetworkSelectionStatus
                        .DISABLED_BY_WIFI_MANAGER));
    }

    /**
     * Verify that it reports errors if the issuer is a fake Root CA with the same subject of the
     * real Root CA. Simulates an attack where the leaf is copied from the real server but a fake
     * Root CA that an attacker controls is attached.
     */
    @Test
    public void verifyOnErrorWithFakeRootCaCertInTheChain() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        boolean isAtLeastT = true, isTrustOnFirstUseSupported = true, isUserSelected = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);

        // Fake Root CA that didn't sign the server cert
        CertificateEventInfo mockCaCert = generateMockCertEventInfo(TEST_GEN_FAKE_CA_CERT);
        CertificateEventInfo mockServerCert = generateMockCertEventInfo(TEST_GEN_SERVER_CERT);
        mInsecureEapNetworkHandler.addPendingCertificate(config.networkId, 1, mockCaCert);
        mInsecureEapNetworkHandler.addPendingCertificate(config.networkId, 0, mockServerCert);

        mInsecureEapNetworkHandler.startUserApprovalIfNecessary(isUserSelected);
        verify(mCallbacks).onError(eq(config.SSID));
        verify(mWifiConfigManager, atLeastOnce()).updateNetworkSelectionStatus(eq(config.networkId),
                eq(WifiConfiguration.NetworkSelectionStatus
                        .DISABLED_BY_WIFI_MANAGER));
    }

    /**
     * Verify that setting pending certificate won't crash with no current configuration.
     */
    @Test
    public void verifySetPendingCertificateNoCrashWithNoConfig()
            throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        mInsecureEapNetworkHandler = new InsecureEapNetworkHandler(
                mContext,
                mWifiConfigManager,
                mWifiNative,
                mFrameworkFacade,
                mWifiNotificationManager,
                mWifiDialogManager,
                true /* isTrustOnFirstUseSupported */,
                false /* isInsecureEnterpriseConfigurationAllowed */,
                mCallbacks,
                WIFI_IFACE_NAME,
                mHandler);
        CertificateEventInfo mockSelfSignedCert =
                generateMockCertEventInfo(TEST_GEN_SELF_SIGNED_CERT);
        mInsecureEapNetworkHandler.addPendingCertificate(WifiConfiguration.INVALID_NETWORK_ID, 0,
                mockSelfSignedCert);
        mInsecureEapNetworkHandler.addPendingCertificate(0xF1AF, 0, mockSelfSignedCert);
    }

    @Test
    public void testExistingCertChainIsClearedOnPreparingNewConnection() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        boolean isAtLeastT = true, isTrustOnFirstUseSupported = true, isUserSelected = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);

        // Missing root CA cert.
        mInsecureEapNetworkHandler.addPendingCertificate(config.networkId, 0,
                generateMockCertEventInfo(TEST_GEN_SERVER_CERT));

        // The wrong cert chain should be cleared after this call.
        mInsecureEapNetworkHandler.prepareConnection(config);

        CertificateEventInfo mockSelfSignedCert =
                generateMockCertEventInfo(TEST_GEN_SELF_SIGNED_CERT);
        mInsecureEapNetworkHandler.addPendingCertificate(config.networkId, 0, mockSelfSignedCert);

        mInsecureEapNetworkHandler.startUserApprovalIfNecessary(isUserSelected);
        verify(mCallbacks, never()).onError(any());
    }

    @Test
    public void verifyUserApprovalIsNotNeededWithDifferentTargetConfig() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        boolean isAtLeastT = true, isTrustOnFirstUseSupported = true, isUserSelected = true;

        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);

        CertificateEventInfo mockSelfSignedCert =
                generateMockCertEventInfo(TEST_GEN_SELF_SIGNED_CERT);
        mInsecureEapNetworkHandler.addPendingCertificate(config.networkId, 0, mockSelfSignedCert);

        // Pass another PSK config which is not the same as the current one.
        WifiConfiguration pskConfig = WifiConfigurationTestUtil.createPskNetwork();
        pskConfig.networkId = FRAMEWORK_NETWORK_ID + 2;
        mInsecureEapNetworkHandler.prepareConnection(pskConfig);
        mInsecureEapNetworkHandler.startUserApprovalIfNecessary(isUserSelected);
        verify(mCallbacks, never()).onError(any());

        // Pass another non-TOFU EAP config which is not the same as the current one.
        WifiConfiguration anotherEapConfig = spy(WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.SIM, WifiEnterpriseConfig.Phase2.NONE));
        anotherEapConfig.networkId = FRAMEWORK_NETWORK_ID + 1;
        assertFalse(mInsecureEapNetworkHandler.addPendingCertificate(anotherEapConfig.networkId, 0,
                mockSelfSignedCert));
        mInsecureEapNetworkHandler.prepareConnection(anotherEapConfig);
        mInsecureEapNetworkHandler.startUserApprovalIfNecessary(isUserSelected);
        verify(mCallbacks, never()).onError(any());
    }

    private void verifyTrustOnFirstUseFlowWithDefaultCerts(WifiConfiguration config,
            int action, boolean isTrustOnFirstUseSupported, boolean isUserSelected,
            boolean needUserApproval) throws Exception {
        CertificateEventInfo mockCaCert = generateMockCertEventInfo(TEST_GEN_CA_CERT);
        CertificateEventInfo mockServerCert = generateMockCertEventInfo(TEST_GEN_SERVER_CERT);
        if (isTrustOnFirstUseSupported) {
            mInsecureEapNetworkHandler.addPendingCertificate(config.networkId, 1, mockCaCert);
            mInsecureEapNetworkHandler.addPendingCertificate(config.networkId, 0, mockServerCert);
        }
        verifyTrustOnFirstUseFlow(config, action, isTrustOnFirstUseSupported,
                isUserSelected, needUserApproval, mockCaCert.getCert(), mockServerCert.getCert(),
                null);
    }

    private void verifyTrustOnFirstUseFlow(WifiConfiguration config,
            int action, boolean isTrustOnFirstUseSupported, boolean isUserSelected,
            boolean needUserApproval, X509Certificate expectedCaCert,
            X509Certificate expectedServerCert, String expectedCaPath) throws Exception {
        mInsecureEapNetworkHandler.startUserApprovalIfNecessary(isUserSelected);

        ArgumentCaptor<String> dialogMessageCaptor = ArgumentCaptor.forClass(String.class);
        if (isUserSelected) {
            ArgumentCaptor<WifiDialogManager.SimpleDialogCallback> dialogCallbackCaptor =
                    ArgumentCaptor.forClass(WifiDialogManager.SimpleDialogCallback.class);
            verify(mWifiDialogManager).createLegacySimpleDialogWithUrl(
                    any(), dialogMessageCaptor.capture(), any(), anyInt(), anyInt(), any(), any(),
                    any(), dialogCallbackCaptor.capture(), any());
            if (isTrustOnFirstUseSupported) {
                assertTofuDialogMessage(expectedServerCert,
                        dialogMessageCaptor.getValue());
            }
            if (action == ACTION_ACCEPT) {
                dialogCallbackCaptor.getValue().onPositiveButtonClicked();
            } else if (action == ACTION_REJECT) {
                dialogCallbackCaptor.getValue().onNegativeButtonClicked();
            } else if (action == ACTION_FORGET) {
                mOnNetworkUpdateListenerCaptor.getValue().onNetworkRemoved(config);
            }
        } else {
            verify(mFrameworkFacade, never()).makeAlertDialogBuilder(any());
            verify(mFrameworkFacade).makeNotificationBuilder(
                    eq(mContext), eq(WifiService.NOTIFICATION_NETWORK_ALERTS));

            // Trust On First Use notification has no accept and reject action buttons.
            // It only supports TAP and launch the dialog.
            if (isTrustOnFirstUseSupported) {
                Intent intent = new Intent(InsecureEapNetworkHandler.ACTION_CERT_NOTIF_TAP);
                intent.putExtra(InsecureEapNetworkHandler.EXTRA_PENDING_CERT_SSID, TEST_SSID);
                BroadcastReceiver br = mBroadcastReceiverCaptor.getValue();
                br.onReceive(mContext, intent);
                ArgumentCaptor<WifiDialogManager.SimpleDialogCallback> dialogCallbackCaptor =
                        ArgumentCaptor.forClass(WifiDialogManager.SimpleDialogCallback.class);
                verify(mWifiDialogManager).createLegacySimpleDialogWithUrl(
                        any(), dialogMessageCaptor.capture(), any(), anyInt(), anyInt(), any(),
                        any(), any(), dialogCallbackCaptor.capture(), any());
                assertTofuDialogMessage(expectedServerCert,
                        dialogMessageCaptor.getValue());
                if (action == ACTION_ACCEPT) {
                    dialogCallbackCaptor.getValue().onPositiveButtonClicked();
                } else if (action == ACTION_REJECT) {
                    dialogCallbackCaptor.getValue().onNegativeButtonClicked();
                }
            } else {
                Intent intent = new Intent();
                if (action == ACTION_ACCEPT) {
                    intent = new Intent(InsecureEapNetworkHandler.ACTION_CERT_NOTIF_ACCEPT);
                } else if (action == ACTION_REJECT) {
                    intent = new Intent(InsecureEapNetworkHandler.ACTION_CERT_NOTIF_REJECT);
                } else if (action == ACTION_TAP) {
                    intent = new Intent(InsecureEapNetworkHandler.ACTION_CERT_NOTIF_TAP);
                }
                intent.putExtra(InsecureEapNetworkHandler.EXTRA_PENDING_CERT_SSID, TEST_SSID);
                BroadcastReceiver br = mBroadcastReceiverCaptor.getValue();
                br.onReceive(mContext, intent);
            }
        }

        if (action == ACTION_ACCEPT) {
            verify(mWifiConfigManager).updateNetworkSelectionStatus(eq(config.networkId),
                    eq(WifiConfiguration.NetworkSelectionStatus.DISABLED_NONE));
            if (isTrustOnFirstUseSupported) {
                int postConnectionMethod;
                if (!TextUtils.isEmpty(expectedCaPath)) {
                    // Simulate Root CA from trust store
                    postConnectionMethod = WifiEnterpriseConfig.TOFU_STATE_CONFIGURE_ROOT_CA;
                    verify(mWifiConfigManager).updateCaCertificate(
                            eq(config.networkId), eq(expectedCaCert), eq(expectedServerCert),
                            eq(null), eq(true));
                } else if (expectedCaCert == null) {
                    // Simulate server cert pinning case where there is no Root CA
                    postConnectionMethod = WifiEnterpriseConfig.TOFU_STATE_CERT_PINNING;
                    verify(mWifiConfigManager).updateCaCertificate(
                            eq(config.networkId), eq(expectedServerCert), eq(expectedServerCert),
                            eq("12345678"), eq(false)); // Server certificate hash
                } else {
                    postConnectionMethod = WifiEnterpriseConfig.TOFU_STATE_CONFIGURE_ROOT_CA;
                    verify(mWifiConfigManager).updateCaCertificate(
                            eq(config.networkId), eq(expectedCaCert), eq(expectedServerCert),
                            eq(null), eq(false)); // Cert pinning not used
                }
                verify(mWifiConfigManager).setTofuPostConnectionState(
                        eq(config.networkId), eq(postConnectionMethod));
            } else {
                verify(mWifiConfigManager, never()).updateCaCertificate(
                        anyInt(), any(), any(), any(), anyBoolean());
                verify(mWifiConfigManager, never()).setTofuPostConnectionState(anyInt(), anyInt());
            }
            verify(mWifiConfigManager).setTofuDialogApproved(eq(config.networkId), eq(true));
            verify(mCallbacks).onAccept(eq(config.SSID), eq(config.networkId));
        } else if (action == ACTION_REJECT) {
            verify(mWifiConfigManager).setTofuDialogApproved(eq(config.networkId), eq(false));
            verify(mWifiConfigManager, atLeastOnce())
                    .updateNetworkSelectionStatus(eq(config.networkId),
                            eq(WifiConfiguration.NetworkSelectionStatus
                            .DISABLED_BY_WIFI_MANAGER));
            verify(mCallbacks).onReject(eq(config.SSID), eq(!isTrustOnFirstUseSupported));
        } else if (action == ACTION_TAP) {
            verify(mWifiDialogManager).createLegacySimpleDialogWithUrl(
                    any(), any(), any(), anyInt(), anyInt(), any(), any(), any(), any(), any());
            verify(mTofuAlertDialog).launchDialog();
        } else if (action == ACTION_FORGET) {
            verify(mTofuAlertDialog).dismissDialog();
        }
        verify(mCallbacks, never()).onError(any());
    }

    private void assertTofuDialogMessage(
            X509Certificate serverCert,
            String message) {
        CertificateSubjectInfo serverCertSubjectInfo =
                CertificateSubjectInfo.parse(serverCert.getSubjectX500Principal().getName());
        CertificateSubjectInfo serverCertIssuerInfo =
                CertificateSubjectInfo.parse(serverCert.getIssuerX500Principal().getName());
        assertNotNull("Server cert subject info is null", serverCertSubjectInfo);
        assertNotNull("Server cert issuer info is null", serverCertIssuerInfo);

        assertTrue("TOFU dialog message does not contain server cert subject name ",
                message.contains(serverCertSubjectInfo.commonName));
        assertTrue("TOFU dialog message does not contain server cert issuer name",
                message.contains(serverCertIssuerInfo.commonName));
        if (!TextUtils.isEmpty(serverCertSubjectInfo.organization)) {
            assertTrue("TOFU dialog message does not contain server cert organization",
                    message.contains(serverCertSubjectInfo.organization));
        }
    }

    @Test
    public void testCleanUp() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());

        boolean isAtLeastT = true, isTrustOnFirstUseSupported = true;
        WifiConfiguration config = prepareWifiConfiguration(isAtLeastT);
        setupTest(config, isAtLeastT, isTrustOnFirstUseSupported);

        BroadcastReceiver br = mBroadcastReceiverCaptor.getValue();
        mInsecureEapNetworkHandler.cleanup();
        verify(mContext).unregisterReceiver(br);
        verify(mWifiConfigManager).removeOnNetworkUpdateListener(
                mOnNetworkUpdateListenerCaptor.capture());
    }

    /**
     * Verify the getDigest and fingerprint methods
     */
    @Test
    public void verifyGetDigest() throws Exception {
        CertificateEventInfo mockServerCert = generateMockCertEventInfo(TEST_GEN_SERVER_CERT);
        assertEquals(TEST_EXPECTED_SHA_256_SIGNATURE,
                mInsecureEapNetworkHandler.getDigest(mockServerCert.getCert(), "SHA256"));
    }

    @Test
    public void verifyTrustStoreUsedForAidlVersion2AndUp() throws Exception {
        WifiConfiguration config = configureTofuWithAidlVersion2Value(true);
        verify(config.enterpriseConfig)
                .setCaPath(eq(WifiConfigurationUtil.getSystemTrustStorePath()));
    }

    @Test
    public void verifyTrustStoreUsedForAidlVersionBelow2() throws Exception {
        WifiConfiguration config = configureTofuWithAidlVersion2Value(false);
        verify(config.enterpriseConfig, never()).setCaPath(anyString());
    }

    private WifiConfiguration configureTofuWithAidlVersion2Value(boolean aidlVersion2OrUp) {
        mInsecureEapNetworkHandler = new InsecureEapNetworkHandler(
                mContext,
                mWifiConfigManager,
                mWifiNative,
                mFrameworkFacade,
                mWifiNotificationManager,
                mWifiDialogManager,
                true,
                false,
                mCallbacks,
                WIFI_IFACE_NAME,
                mHandler);

        when(mWifiNative.isSupplicantAidlServiceVersionAtLeast(anyInt()))
                .thenReturn(aidlVersion2OrUp);

        WifiConfiguration config = spy(WifiConfigurationTestUtil.createEapNetwork(
                WifiEnterpriseConfig.Eap.TTLS, WifiEnterpriseConfig.Phase2.MSCHAPV2));
        config.networkId = FRAMEWORK_NETWORK_ID;
        config.SSID = TEST_SSID;
        WifiEnterpriseConfig enterpriseConfig = mock(WifiEnterpriseConfig.class);
        when(enterpriseConfig.isTrustOnFirstUseEnabled()).thenReturn(true);
        when(enterpriseConfig.isEapMethodServerCertUsed()).thenReturn(true);
        when(enterpriseConfig.hasCaCertificate()).thenReturn(false);
        when(enterpriseConfig.isUserApproveNoCaCert()).thenReturn(false);
        config.enterpriseConfig = enterpriseConfig;

        mInsecureEapNetworkHandler.prepareConnection(config);
        return config;
    }
}
