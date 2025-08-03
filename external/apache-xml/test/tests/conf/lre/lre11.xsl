<?xml version="1.0"?>
<xsl:transform xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0"
    xmlns:ext="http://somebody.elses.extension"
    xmlns:java="http://xml.apache.org/xslt/java"
    xmlns:jad="http://administrator.com"
    xmlns="www.lotus.com"
    xmlns:ped="http://tester.com"
    xmlns:bdd="http://buster.com"
    extension-element-prefixes="ext"
    exclude-result-prefixes="java jad #default">

  <!-- FileName: lre11 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 2.2 Stylesheet Element -->
  <!-- Creator: Paul Dick -->
  <!-- AdditionalSpec: 2.1 of XSLT for namespaces on attributes -->
  <!-- Purpose: Testing the xsl:transform element and its attributes. english
       attribute and #default,ped,bdd namespace nodes are all that should be output.
       (#default is used.) xsl:if must be assumed to be a directive to the processor,
       so it could raise an error. -->

<xsl:template match="doc">
  <out xsl:if= "my if" english="to leave"/>
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

</xsl:transform>
