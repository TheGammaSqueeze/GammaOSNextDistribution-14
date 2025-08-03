<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
     xmlns:sql="org.apache.xalan.lib.sql.XConnection" 
     xmlns:xalan="http://xml.apache.org/xalan" 
     exclude-result-prefixes="xalan java" 
     extension-element-prefixes="sql"
     xmlns:java="http://xml.apache.org/xslt/java">
        <xsl:output method="xml" omit-xml-declaration="yes" standalone="yes"/>

        <!-- Varaible that will be replaced by the XSL Dynamic Query Processor -->
        <xsl:param name="stylesheets">
                <STYLESHEETS>
                        <SUCCESS>
                                <XSL_SHEET MEDIA="ns">success1.xsl</XSL_SHEET>
                                <XSL_SHEET MEDIA="ie">success2.xsl</XSL_SHEET>
                                <XSL_SHEET MEDIA="123">success3.xsl</XSL_SHEET>
                        </SUCCESS>
                        <ERROR>
                                <XSL_SHEET MEDIA="456">error1.xsl</XSL_SHEET>
                                <XSL_SHEET MEDIA="789">error2.xsl</XSL_SHEET>
                                <XSL_SHEET MEDIA="000">error3.xsl</XSL_SHEET>
                        </ERROR>
                </STYLESHEETS>
        </xsl:param>

        <xsl:template match="/">
                <!-- P911X Response Element -->
                <xsl:element name="TEMPLATES">
                    <!--xsl:copy-of select="xalan:nodeset($stylesheets)"/-->
                    <!-- This is a test to make sure we can still call methods on the 
                             passed in node. -->
                    <xsl:text>&#10;===== Test java:getNodeName from RTF param. =====&#10;</xsl:text>
                    <xsl:value-of select="java:getNodeName($stylesheets)" />

                    <xsl:text>&#10;===== Test xsl:copy-of of RTF param. =====&#10;</xsl:text>
                    <xsl:copy-of select="$stylesheets"/>
                    
                    <xsl:text>&#10;===== Test return of xalan:nodeset of RTF param. =====&#10;</xsl:text>
                    <xsl:copy-of select="xalan:nodeset($stylesheets)"/>
                    
                    <xsl:text>&#10;===== Test return of DTM from extension. =====&#10;</xsl:text>
                    <xsl:copy-of select="java:Bugzilla2925.dtmTest('Bugzilla2925Params.xml')"/>
                    
                    <xsl:text>&#10;===== Test return of DTMAxisIterator from extension. =====&#10;</xsl:text>
                    <xsl:copy-of select="java:Bugzilla2925.DTMAxisIteratorTest('Bugzilla2925Params.xml')"/>
                    
                    <xsl:text>&#10;===== Test return of DTMIterator from extension. =====&#10;</xsl:text>
                    <xsl:copy-of select="java:Bugzilla2925.DTMIteratorTest('Bugzilla2925Params.xml')"/>
                </xsl:element>
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
