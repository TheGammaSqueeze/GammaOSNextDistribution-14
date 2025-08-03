<xsl:stylesheet version="1.0"
                      xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

  <!-- Test FileName: mk020.xsl -->
  <!-- Source Attribution: 
       This test was written by Michael Kay and is taken from 
       'XSLT Programmer's Reference' published by Wrox Press Limited in 2000;
       ISBN 1-861003-12-9; copyright Wrox Press Limited 2000; all rights reserved. 
       Now updated in the second edition (ISBN 1861005067), http://www.wrox.com.
       No part of this book may be reproduced, stored in a retrieval system or 
       transmitted in any form or by any means - electronic, electrostatic, mechanical, 
       photocopying, recording or otherwise - without the prior written permission of 
       the publisher, except in the case of brief quotations embodied in critical articles or reviews.
  -->
  <!-- Example: poem.xml, standard-style.xsl -->
  <!-- Chapter/Page: 4-216 -->
  <!-- Purpose: Precedence of template rules -->

<xsl:template match="/">
   <html>
   <head>
   <title><xsl:value-of select="//title"/></title>
   </head>
   <body>
      <xsl:apply-templates/>
   </body>
   </html>
</xsl:template>

<xsl:template match="title">
   <h1><xsl:apply-templates/></h1>
</xsl:template>

<xsl:template match="author">
   <div align="right"><i>by </i>
      <xsl:apply-templates/>
   </div>
</xsl:template>

<xsl:template match="stanza">
   <p><xsl:apply-templates/></p>
</xsl:template>

<xsl:template match="line">
   <xsl:apply-templates/><br/><xsl:text/>
</xsl:template>

<xsl:template match="date"/>

<xsl:template match="text()">
<xsl:value-of select="."/><xsl:text />
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
