package android.nfc.cts;

import android.nfc.TagLostException;
import org.junit.Assert;
import org.junit.Test;

public class TagLostExceptionTest {

  @Test
  public void testTagLostException() {
    try {
      throw new TagLostException();
    } catch (TagLostException e) {
      Assert.assertTrue(e.getMessage() == null);
    }
    String s = new String("testTagLostException");
    try {
      throw new TagLostException(s);
    } catch (TagLostException e) {
      Assert.assertTrue(e.getMessage().equals(s));
    }
  }
}
