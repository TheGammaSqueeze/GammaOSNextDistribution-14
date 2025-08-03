package org.apache.qetest.xsl;

import javax.xml.transform.TransformerException;
import javax.xml.transform.ErrorListener;

/*
   An object of this class, can be set on XalanJ TransformerFactory
   to report run-time XSLTC compilation errors.

   @author <a href="mailto:mukulg@apache.org">Mukul Gandhi</a>
*/
public class XsltcTestsErrorListener implements ErrorListener {

      private String errListenerMesg = null;

	  @Override
	  public void warning(TransformerException ex) throws TransformerException {
	     // NO OP
	  }

	  @Override
	  public void error(TransformerException ex) throws TransformerException {
         // NO OP
	  }

	  @Override
	  public void fatalError(TransformerException ex) throws TransformerException {
          this.errListenerMesg = ex.getMessage();
	  }

      public String getErrListenerMesg() {
          return this.errListenerMesg; 
      }
		
}