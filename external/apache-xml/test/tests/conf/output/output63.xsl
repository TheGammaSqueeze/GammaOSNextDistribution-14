<?xml version="1.0"?> 
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0"
                xmlns:jsp="http://www.w3.org/jsp"
				xmlns="http://www.w3.org/TR/REC-html40"
                exclude-result-prefixes="jsp">

  <!-- FileName: OUTP63 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 16.2 HTML Output Method -->
  <!-- Purpose: The html output method should not output an element 
                differently from the xml output method unless the 
                expanded-name of the element has a null namespace URI; an 
                element whose expanded-name has a non-null namespace URI 
                should be output as XML. So the html tags <p>, <hr> and
                <br> in this case, due to the default html namespace will
                be output as xml not html. -->
  <!-- Creator: Paul Dick -->

<xsl:output method="html"/>

<xsl:template match="/">
  <HTML>
    <jsp:setProperty name="blah" property="blah" value="blah"/>
	<P></P>
	<p/>
	<P/>
	<hr size="8"></hr>
	<hr size="8"/>
	<br/>
	<br>
	</br>
  </HTML>
</xsl:template>


  <!--
   * Licensed to the Apache Software Foundation (ASF) under one
   * or more contributor license agreements. See the NOTICE file
   * distributed with this work for additional information
   * regarding copyright ownership. The ASF licenses this file
   * to you under the Apache License, Version 2.0 (the  "License");
   * you may not use this file except in compliance with the License.
   * You may obtain a copy of the License at
   *
   *     http://www.apache.org/licenses/LICENSE-2.0
   *
   * Unless required by applicable law or agreed to in writing, software
   * distributed under the License is distributed on an "AS IS" BASIS,
   * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   * See the License for the specific language governing permissions and
   * limitations under the License.
  -->

</xsl:stylesheet>
