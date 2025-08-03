import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.adobe.xmp.impl.XMPMetaParser;
import com.adobe.xmp.options.ParseOptions;
import com.adobe.xmp.XMPException;

public class Fuzzer {

  public static void fuzzerTestOneInput(FuzzedDataProvider data) {
    ParseOptions parseOptions = new ParseOptions();
    parseOptions.setAcceptLatin1(data.consumeBoolean()) ;
    parseOptions.setFixControlChars(data.consumeBoolean()) ;
    parseOptions.setRequireXMPMeta(data.consumeBoolean()) ;
    parseOptions.setStrictAliasing(data.consumeBoolean()) ;
    String input = data.consumeRemainingAsString();
    try {
      XMPMetaParser.parse(input, parseOptions);
    } catch(XMPException e) {
      // Do nothing
    }
  }
}