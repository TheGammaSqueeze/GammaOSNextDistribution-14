<?xml version="1.0" encoding="ISO-8859-1"?> 
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
<xsl:output method="xml"/>

  <!-- FileName: OUTP24 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 16.1 XML Output Method -->
  <!-- Purpose: Escape of non-ASCII chars in URI attribute values using method 
       cited in Section B.2.1 of HTML 4.0 Spec. This test is a duplicate of
       OUTP31, except that the output is XML.  -->

<xsl:template match="/">
  <out>
    1. "&#165;"  <A attr="&#165;"/>
    2. "&quot;"  <A attr="&quot;"/>
    3. "&lt;"    <A attr="&lt;"/>
    4. "&gt;"    <A attr="&gt;"/>
    5. "&amp;"   <A attr="&amp;"/>
    6. "&#035;"  <A attr="&#035;"/>
    7. "&#039;"  <A attr="&#039;"/>
    8. "&#032;"  <A attr="&#032;"/>	<img src="Test 31 Gif.gif"/>
    9. "&#169;"  <A attr="&#169;"/>
  </out>
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
