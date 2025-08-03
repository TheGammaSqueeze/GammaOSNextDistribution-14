/*
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
 */
/*
 * $Id$
 */

package org.apache.qetest;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;


/**
 * Simple utility for writing XML documents from character tables.  
 *  
 * @author scott_boag@lotus.com
 * @author shane_curcuru@lotus.com
 * @version $Id$
 */
public class CharTables
{

    /**
     * Write a chars table to a file.  
     *
     * Simply uses new OutputStreamWriter(..., fileencoding).  
     *
     * @param chars array of Objects, Integer char code and 
     * String description thereof (only including applicable codes)
     * @param includeUnencoded, or simply don't write them out at all
     * @param xmlencoding the XML name used in encoding= attr
     * @param fileencoding the encoding to output to
     * @param filename to write to
     * @throws any underlying exceptions
     */
    public static void writeCharTableFile(Object[][] chars, boolean includeUnencoded, 
            String xmlencoding, String fileencoding, String filename)
            throws Exception
    {
        File f = new File(filename);
        FileOutputStream fos = new FileOutputStream(f);
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(fos, fileencoding));

        writer.println("<?xml version=\"1.0\" encoding=\"" + xmlencoding + "\"?>");
        writer.println("<chartables fileencoding=\"" + fileencoding + "\">");
        CharTables.writeCharTable(chars, includeUnencoded, xmlencoding, writer);
        writer.println("</chartables>");
        writer.close();
    }

    /**
     * Write a chars table to a stream.
     *
     * @param chars array of Objects, Integer char code and 
     * String description thereof (only including applicable codes)
     * @param includeUnencoded, or simply don't write them out at all
     * @param encoding the encoding to output to
     * @param writer where to write to
     * @throws any underlying exceptions
     */
    public static void writeCharTable(Object[][] chars, boolean includeUnencoded, 
            String encoding, PrintWriter writer)
            throws Exception
    {
        writer.println(CHARS_HEADER + encoding + "\" includeUnencoded=\"" + includeUnencoded + "\">");
        int numChars = chars.length;

        for ( int x = 0x20; x <= 0x03CE+4/* 0xD7FF */; x++ )
        {
            int i;
            for ( i = 0; i < numChars; i++ )
            {
                final int code = ((Integer)(chars[i][0])).intValue(); 
                
                if ( code == x )
                {     
                    writer.print(CHAR_HEADER + code + CHAR_HEADER2 + chars[i][1] + "\">");
                    switch ( code )
                    {
                    case '&': 
                        writer.print(C_HEADER); 
                        writer.print("&amp;"); 
                        writer.print(C_ENDER); 
                        break;
                    case '<': 
                        writer.print(C_HEADER); 
                        writer.print("&lt;"); 
                        writer.print(C_ENDER); 
                        break;
                    default:
                        writer.print(C_HEADER); 
                        writer.print(((char)code));
                        writer.print(C_ENDER); 
                    }
                    writer.print(E_HEADER); 
                    writer.print("&#x"); 
                    writer.print(Integer.toHexString(code)); 
                    writer.print(";"); 
                    writer.print(E_ENDER);
                    writer.println(CHAR_ENDER);
                    break; // from for...
                }
            } // of for(i...
            // This character is not provided in the specified encoding
            if ( includeUnencoded && ( i == numChars ))
            {
                writer.print(CHAR_HEADER + x + CHAR_HEADER2 + "not encoded" + "\">");
                // Since this character isn't in this encoding, 
                //  don't bother writing out the ELEM_C
                writer.print(E_HEADER); 
                writer.print("&#x"); 
                writer.print(Integer.toHexString(x)); 
                writer.print(";"); 
                writer.print(E_ENDER);
                writer.println(CHAR_ENDER);
            }

        }// of for(x...
        
        writer.println(CHARS_ENDER);
        writer.flush();
    } // of writeCharTable
 

    /** chars elem - the whole table.  */
    public static final String ELEM_CHARS = "chars";

    /** chars elem, enc attr - encoding of these chars.  */
    public static final String ATTR_ENC = "enc";

    /** Convenience precalculated string.  */
    public static String CHARS_HEADER = "<" + ELEM_CHARS + " " + ATTR_ENC + "=\"";
    
    /** Convenience precalculated string.  */
    public static String CHARS_ENDER = "</" + ELEM_CHARS + ">";

    /** char elem - a single character.  */
    public static final String ELEM_CHAR = "char";

    /** char elem, dec attr - decimal char code.  */
    public static final String ATTR_DEC = "dec";

    /** char elem, desc attr - description.  */
    public static final String ATTR_DESC = "desc";

    /** Convenience precalculated string.  */
    public static String CHAR_HEADER = "<" + ELEM_CHAR + " " + ATTR_DEC + "=\"";
    
    /** Convenience precalculated string.  */
    public static String CHAR_HEADER2 = "\" " + ATTR_DESC + "=\"";

    /** Convenience precalculated string.  */
    public static String CHAR_ENDER = "</" + ELEM_CHAR + ">";


    /** c elem - just the character in the encoding.  */
    public static final String ELEM_C = "c";

    /** Convenience precalculated string.  */
    public static String C_HEADER = "<" + ELEM_C + ">";
    
    /** Convenience precalculated string.  */
    public static String C_ENDER = "</" + ELEM_C + ">";


    /** e elem - the entity reference to the character.  */
    public static final String ELEM_E = "e";

    /** Convenience precalculated string.  */
    public static String E_HEADER = "<" + ELEM_E + ">";
    
    /** Convenience precalculated string.  */
    public static String E_ENDER = "</" + ELEM_E + ">";


    /**
     * Main method to run from the command line; sample usage.
     * @param args cmd line arguments
     */
    public static void main(String[] args)
    {
        String filename = "chartable.xml";
        if (args.length >= 1)
        {
            filename = args[0];
        }
        String xmlencoding = "ISO-8859-7";
        String fileencoding = "ISO8859_7";
        try
        {
            // Sample usage with greek table, below
            CharTables.writeCharTableFile(greek, false, xmlencoding, fileencoding, filename);
            System.out.println("Wrote " + filename + " output in encodings " + xmlencoding + "/" + fileencoding);
        } 
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }


    /** Sample data: greek/ISO-8859-7/ISO8859_7 .  */
    public static final Object greek[][] = 
    {  
        {new Integer(0x0020),	"SPACE"}
        , {new Integer(0x0021),	"EXCLAMATION MARK"}
        , {new Integer(0x0022),	"QUOTATION MARK"}
        , {new Integer(0x0023),	"NUMBER SIGN"}
        , {new Integer(0x0024),	"DOLLAR SIGN"}
        , {new Integer(0x0025),	"PERCENT SIGN"}
        , {new Integer(0x0026),	"AMPERSAND"}
        , {new Integer(0x0027),	"APOSTROPHE"}
        , {new Integer(0x0028),	"LEFT PARENTHESIS"}
        , {new Integer(0x0029),	"RIGHT PARENTHESIS"}
        , {new Integer(0x002A),	"ASTERISK"}
        , {new Integer(0x002B),	"PLUS SIGN"}
        , {new Integer(0x002C),	"COMMA"}
        , {new Integer(0x002D),	"HYPHEN-MINUS"}
        , {new Integer(0x002E),	"FULL STOP"}
        , {new Integer(0x002F),	"SOLIDUS"}
        , {new Integer(0x0030),	"DIGIT ZERO"}
        , {new Integer(0x0031),	"DIGIT ONE"}
        , {new Integer(0x0032),	"DIGIT TWO"}
        , {new Integer(0x0033),	"DIGIT THREE"}
        , {new Integer(0x0034),	"DIGIT FOUR"}
        , {new Integer(0x0035),	"DIGIT FIVE"}
        , {new Integer(0x0036),	"DIGIT SIX"}
        , {new Integer(0x0037),	"DIGIT SEVEN"}
        , {new Integer(0x0038),	"DIGIT EIGHT"}
        , {new Integer(0x0039),	"DIGIT NINE"}
        , {new Integer(0x003A),	"COLON"}
        , {new Integer(0x003B),	"SEMICOLON"}
        , {new Integer(0x003C),	"LESS-THAN SIGN"}
        , {new Integer(0x003D),	"EQUALS SIGN"}
        , {new Integer(0x003E),	"GREATER-THAN SIGN"}
        , {new Integer(0x003F),	"QUESTION MARK"}
        , {new Integer(0x0040),	"COMMERCIAL AT"}
        , {new Integer(0x0041),	"LATIN CAPITAL LETTER A"}
        , {new Integer(0x0042),	"LATIN CAPITAL LETTER B"}
        , {new Integer(0x0043),	"LATIN CAPITAL LETTER C"}
        , {new Integer(0x0044),	"LATIN CAPITAL LETTER D"}
        , {new Integer(0x0045),	"LATIN CAPITAL LETTER E"}
        , {new Integer(0x0046),	"LATIN CAPITAL LETTER F"}
        , {new Integer(0x0047),	"LATIN CAPITAL LETTER G"}
        , {new Integer(0x0048),	"LATIN CAPITAL LETTER H"}
        , {new Integer(0x0049),	"LATIN CAPITAL LETTER I"}
        , {new Integer(0x004A),	"LATIN CAPITAL LETTER J"}
        , {new Integer(0x004B),	"LATIN CAPITAL LETTER K"}
        , {new Integer(0x004C),	"LATIN CAPITAL LETTER L"}
        , {new Integer(0x004D),	"LATIN CAPITAL LETTER M"}
        , {new Integer(0x004E),	"LATIN CAPITAL LETTER N"}
        , {new Integer(0x004F),	"LATIN CAPITAL LETTER O"}
        , {new Integer(0x0050),	"LATIN CAPITAL LETTER P"}
        , {new Integer(0x0051),	"LATIN CAPITAL LETTER Q"}
        , {new Integer(0x0052),	"LATIN CAPITAL LETTER R"}
        , {new Integer(0x0053),	"LATIN CAPITAL LETTER S"}
        , {new Integer(0x0054),	"LATIN CAPITAL LETTER T"}
        , {new Integer(0x0055),	"LATIN CAPITAL LETTER U"}
        , {new Integer(0x0056),	"LATIN CAPITAL LETTER V"}
        , {new Integer(0x0057),	"LATIN CAPITAL LETTER W"}
        , {new Integer(0x0058),	"LATIN CAPITAL LETTER X"}
        , {new Integer(0x0059),	"LATIN CAPITAL LETTER Y"}
        , {new Integer(0x005A),	"LATIN CAPITAL LETTER Z"}
        , {new Integer(0x005B),	"LEFT SQUARE BRACKET"}
        , {new Integer(0x005C),	"REVERSE SOLIDUS"}
        , {new Integer(0x005D),	"RIGHT SQUARE BRACKET"}
        , {new Integer(0x005E),	"CIRCUMFLEX ACCENT"}
        , {new Integer(0x005F),	"LOW LINE"}
        , {new Integer(0x0060),	"GRAVE ACCENT"}
        , {new Integer(0x0061),	"LATIN SMALL LETTER A"}
        , {new Integer(0x0062),	"LATIN SMALL LETTER B"}
        , {new Integer(0x0063),	"LATIN SMALL LETTER C"}
        , {new Integer(0x0064),	"LATIN SMALL LETTER D"}
        , {new Integer(0x0065),	"LATIN SMALL LETTER E"}
        , {new Integer(0x0066),	"LATIN SMALL LETTER F"}
        , {new Integer(0x0067),	"LATIN SMALL LETTER G"}
        , {new Integer(0x0068),	"LATIN SMALL LETTER H"}
        , {new Integer(0x0069),	"LATIN SMALL LETTER I"}
        , {new Integer(0x006A),	"LATIN SMALL LETTER J"}
        , {new Integer(0x006B),	"LATIN SMALL LETTER K"}
        , {new Integer(0x006C),	"LATIN SMALL LETTER L"}
        , {new Integer(0x006D),	"LATIN SMALL LETTER M"}
        , {new Integer(0x006E),	"LATIN SMALL LETTER N"}
        , {new Integer(0x006F),	"LATIN SMALL LETTER O"}
        , {new Integer(0x0070),	"LATIN SMALL LETTER P"}
        , {new Integer(0x0071),	"LATIN SMALL LETTER Q"}
        , {new Integer(0x0072),	"LATIN SMALL LETTER R"}
        , {new Integer(0x0073),	"LATIN SMALL LETTER S"}
        , {new Integer(0x0074),	"LATIN SMALL LETTER T"}
        , {new Integer(0x0075),	"LATIN SMALL LETTER U"}
        , {new Integer(0x0076),	"LATIN SMALL LETTER V"}
        , {new Integer(0x0077),	"LATIN SMALL LETTER W"}
        , {new Integer(0x0078),	"LATIN SMALL LETTER X"}
        , {new Integer(0x0079),	"LATIN SMALL LETTER Y"}
        , {new Integer(0x007A),	"LATIN SMALL LETTER Z"}
        , {new Integer(0x007B),	"LEFT CURLY BRACKET"}
        , {new Integer(0x007C),	"VERTICAL LINE"}
        , {new Integer(0x007D),	"RIGHT CURLY BRACKET"}
        , {new Integer(0x007E),	"TILDE"}
        , {new Integer(0x00A0),	"NO-BREAK SPACE"}
        , {new Integer(0x02BD),	"MODIFIER LETTER REVERSED COMMA"}
        , {new Integer(0x02BC),	"MODIFIER LETTER APOSTROPHE"}
        , {new Integer(0x00A3),	"POUND SIGN"}
        , {new Integer(0x00A6),	"BROKEN BAR"}
        , {new Integer(0x00A7),	"SECTION SIGN"}
        , {new Integer(0x00A8),	"DIAERESIS"}
        , {new Integer(0x00A9),	"COPYRIGHT SIGN"}
        , {new Integer(0x00AB),	"LEFT-POINTING DOUBLE ANGLE QUOTATION MARK"}
        , {new Integer(0x00AC),	"NOT SIGN"}
        , {new Integer(0x00AD),	"SOFT HYPHEN"}
        , {new Integer(0x2015),	"HORIZONTAL BAR"}
        , {new Integer(0x00B0),	"DEGREE SIGN"}
        , {new Integer(0x00B1),	"PLUS-MINUS SIGN"}
        , {new Integer(0x00B2),	"SUPERSCRIPT TWO"}
        , {new Integer(0x00B3),	"SUPERSCRIPT THREE"}
        , {new Integer(0x0384),	"GREEK TONOS"}
        , {new Integer(0x0385),	"GREEK DIALYTIKA TONOS"}
        , {new Integer(0x0386),	"GREEK CAPITAL LETTER ALPHA WITH TONOS"}
        , {new Integer(0x00B7),	"MIDDLE DOT"}
        , {new Integer(0x0388),	"GREEK CAPITAL LETTER EPSILON WITH TONOS"}
        , {new Integer(0x0389),	"GREEK CAPITAL LETTER ETA WITH TONOS"}
        , {new Integer(0x038A),	"GREEK CAPITAL LETTER IOTA WITH TONOS"}
        , {new Integer(0x00BB),	"RIGHT-POINTING DOUBLE ANGLE QUOTATION MARK"}
        , {new Integer(0x038C),	"GREEK CAPITAL LETTER OMICRON WITH TONOS"}
        , {new Integer(0x00BD),	"VULGAR FRACTION ONE HALF"}
        , {new Integer(0x038E),	"GREEK CAPITAL LETTER UPSILON WITH TONOS"}
        , {new Integer(0x038F),	"GREEK CAPITAL LETTER OMEGA WITH TONOS"}
        , {new Integer(0x0390),	"GREEK SMALL LETTER IOTA WITH DIALYTIKA AND TONOS"}
        , {new Integer(0x0391),	"GREEK CAPITAL LETTER ALPHA"}
        , {new Integer(0x0392),	"GREEK CAPITAL LETTER BETA"}
        , {new Integer(0x0393),	"GREEK CAPITAL LETTER GAMMA"}
        , {new Integer(0x0394),	"GREEK CAPITAL LETTER DELTA"}
        , {new Integer(0x0395),	"GREEK CAPITAL LETTER EPSILON"}
        , {new Integer(0x0396),	"GREEK CAPITAL LETTER ZETA"}
        , {new Integer(0x0397),	"GREEK CAPITAL LETTER ETA"}
        , {new Integer(0x0398),	"GREEK CAPITAL LETTER THETA"}
        , {new Integer(0x0399),	"GREEK CAPITAL LETTER IOTA"}
        , {new Integer(0x039A),	"GREEK CAPITAL LETTER KAPPA"}
        , {new Integer(0x039B),	"GREEK CAPITAL LETTER LAMDA"}
        , {new Integer(0x039C),	"GREEK CAPITAL LETTER MU"}
        , {new Integer(0x039D),	"GREEK CAPITAL LETTER NU"}
        , {new Integer(0x039E),	"GREEK CAPITAL LETTER XI"}
        , {new Integer(0x039F),	"GREEK CAPITAL LETTER OMICRON"}
        , {new Integer(0x03A0),	"GREEK CAPITAL LETTER PI"}
        , {new Integer(0x03A1),	"GREEK CAPITAL LETTER RHO"}
        , {new Integer(0x03A3),	"GREEK CAPITAL LETTER SIGMA"}
        , {new Integer(0x03A4),	"GREEK CAPITAL LETTER TAU"}
        , {new Integer(0x03A5),	"GREEK CAPITAL LETTER UPSILON"}
        , {new Integer(0x03A6),	"GREEK CAPITAL LETTER PHI"}
        , {new Integer(0x03A7),	"GREEK CAPITAL LETTER CHI"}
        , {new Integer(0x03A8),	"GREEK CAPITAL LETTER PSI"}
        , {new Integer(0x03A9),	"GREEK CAPITAL LETTER OMEGA"}
        , {new Integer(0x03AA),	"GREEK CAPITAL LETTER IOTA WITH DIALYTIKA"}
        , {new Integer(0x03AB),	"GREEK CAPITAL LETTER UPSILON WITH DIALYTIKA"}
        , {new Integer(0x03AC),	"GREEK SMALL LETTER ALPHA WITH TONOS"}
        , {new Integer(0x03AD),	"GREEK SMALL LETTER EPSILON WITH TONOS"}
        , {new Integer(0x03AE),	"GREEK SMALL LETTER ETA WITH TONOS"}
        , {new Integer(0x03AF),	"GREEK SMALL LETTER IOTA WITH TONOS"}
        , {new Integer(0x03B0),	"GREEK SMALL LETTER UPSILON WITH DIALYTIKA AND TONOS"}
        , {new Integer(0x03B1),	"GREEK SMALL LETTER ALPHA"}
        , {new Integer(0x03B2),	"GREEK SMALL LETTER BETA"}
        , {new Integer(0x03B3),	"GREEK SMALL LETTER GAMMA"}
        , {new Integer(0x03B4),	"GREEK SMALL LETTER DELTA"}
        , {new Integer(0x03B5),	"GREEK SMALL LETTER EPSILON"}
        , {new Integer(0x03B6),	"GREEK SMALL LETTER ZETA"}
        , {new Integer(0x03B7),	"GREEK SMALL LETTER ETA"}
        , {new Integer(0x03B8),	"GREEK SMALL LETTER THETA"}
        , {new Integer(0x03B9),	"GREEK SMALL LETTER IOTA"}
        , {new Integer(0x03BA),	"GREEK SMALL LETTER KAPPA"}
        , {new Integer(0x03BB),	"GREEK SMALL LETTER LAMDA"}
        , {new Integer(0x03BC),	"GREEK SMALL LETTER MU"}
        , {new Integer(0x03BD),	"GREEK SMALL LETTER NU"}
        , {new Integer(0x03BE),	"GREEK SMALL LETTER XI"}
        , {new Integer(0x03BF),	"GREEK SMALL LETTER OMICRON"}
        , {new Integer(0x03C0),	"GREEK SMALL LETTER PI"}
        , {new Integer(0x03C1),	"GREEK SMALL LETTER RHO"}
        , {new Integer(0x03C2),	"GREEK SMALL LETTER FINAL SIGMA"}
        , {new Integer(0x03C3),	"GREEK SMALL LETTER SIGMA"}
        , {new Integer(0x03C4),	"GREEK SMALL LETTER TAU"}
        , {new Integer(0x03C5),	"GREEK SMALL LETTER UPSILON"}
        , {new Integer(0x03C6),	"GREEK SMALL LETTER PHI"}
        , {new Integer(0x03C7),	"GREEK SMALL LETTER CHI"}
        , {new Integer(0x03C8),	"GREEK SMALL LETTER PSI"}
        , {new Integer(0x03C9),	"GREEK SMALL LETTER OMEGA"}
        , {new Integer(0x03CA),	"GREEK SMALL LETTER IOTA WITH DIALYTIKA"}
        , {new Integer(0x03CB),	"GREEK SMALL LETTER UPSILON WITH DIALYTIKA"}
        , {new Integer(0x03CC),	"GREEK SMALL LETTER OMICRON WITH TONOS"}
        , {new Integer(0x03CD),	"GREEK SMALL LETTER UPSILON WITH TONOS"}
        , {new Integer(0x03CE),	"GREEK SMALL LETTER OMEGA WITH TONOS"}
    };

}
