package wordcloud;

import ch.lambdaj.Lambda;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import wordcloud.bg.Background;
import wordcloud.bg.RectangleBackground;
import wordcloud.collide.RectanglePixelCollidable;
import wordcloud.collide.checkers.CollisionChecker;
import wordcloud.collide.checkers.RectangleCollisionChecker;
import wordcloud.collide.checkers.RectanglePixelCollisionChecker;
import wordcloud.font.CloudFont;
import wordcloud.font.FontWeight;
import wordcloud.font.scale.FontScalar;
import wordcloud.font.scale.LinearFontScalar;
import wordcloud.image.AngleGenerator;
import wordcloud.image.CollisionRaster;
import wordcloud.image.ImageRotation;
import wordcloud.padding.Padder;
import wordcloud.padding.RectanglePadder;
import wordcloud.padding.WordPixelPadder;
import wordcloud.palette.ColorPalette;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static ch.lambdaj.Lambda.on;

/**
 * Created by kenny on 6/29/14.
 */
public class WordCloud {

    private static final Logger LOGGER = LogManager.getLogger(WordCloud.class);

    protected final int width;

    protected final int height;

    protected CollisionMode collisionMode;

    protected CollisionChecker collisionChecker;

    protected Padder padder;

    protected int padding = 0;

    protected Background background;

    protected final RectanglePixelCollidable backgroundCollidable;

    protected Color backgroundColor = Color.BLACK;

    protected FontScalar fontScalar = new LinearFontScalar(10, 40);

    protected CloudFont cloudFont = new CloudFont("Comic Sans MS", FontWeight.BOLD);
    private CloudFont sansFont = new CloudFont(Font.SANS_SERIF, FontWeight.BOLD);

    protected AngleGenerator angleGenerator = new AngleGenerator();

    protected final CollisionRaster collisionRaster;

    protected final BufferedImage bufferedImage;

    protected final ArrayList<Word> placedWords = new ArrayList<>();

    protected final ArrayList<Word> skipped = new ArrayList<>();
    protected boolean fitAll = false;

    protected ColorPalette colorPalette = new ColorPalette(Color.ORANGE, Color.WHITE, Color.YELLOW, Color.GRAY, Color.GREEN);

    public WordCloud(int width, int height, CollisionMode collisionMode) {
        this(width, height, collisionMode, false);
    }

    public WordCloud(int width, int height, CollisionMode collisionMode, boolean fitAll) {
        this.width = width;
        this.height = height;
        this.collisionMode = collisionMode;
        this.fitAll = fitAll;
        switch(collisionMode) {
            case PIXEL_PERFECT:
                this.padder = new WordPixelPadder();
                this.collisionChecker = new RectanglePixelCollisionChecker();
                break;

            case RECTANGLE:
            default:
                this.padder = new RectanglePadder();
                this.collisionChecker = new RectangleCollisionChecker();
                break;
        }
        this.collisionRaster = new CollisionRaster(width, height);
        this.bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        this.backgroundCollidable = new RectanglePixelCollidable(collisionRaster, 0, 0);
        this.background = new RectangleBackground(width, height);
    }

    private static String createProgress(int progress) {
        int a = (progress * 20) / 100;
        int b = 20 - a;
        return "[" + StringUtils.leftPad("", a, '=') + StringUtils.leftPad("", b, ' ') + "]"; 
    }

    public void build(List<WordFrequency> wordFrequencies) {
        Collections.sort(wordFrequencies);

        System.out.println("Building the cloud image ...");

        List<Word> words = buildwords(wordFrequencies, this.colorPalette);
        int i = 0;
        double t = 0.0;
        double dt = Math.PI / 180;
        int total = words.size();

        System.out.print("\rProcessing words " + createProgress(0));
        for(Word word : words) {
            int startX = width / 2;
            int startY = height / 2;
            if (i > 50) {
                int w = width / 4;
                int h = height / 4;
                if (t >= 2 * Math.PI) t = 0;
                startX = (int) (w * Math.sin(5 * t) + w);
                startY = (int) (h * Math.sin(4 * t) + h);
                t += dt;
            }
            
            place(word, startX, startY);
            i++;
            String skip = (this.skipped.size() > 0) ? " [skipped " + this.skipped.size() + "]" : "";
            System.out.print("\rProcessing words " + createProgress((i * 100)/total) + " " + i + " / " + total + " (" + (i * 100)/total + "%)" + skip);
        }
        System.out.println("\r\nProcessing words. DONE");
    }

    public boolean fill(List<WordFrequency> wordFrequencies, int pass) {
        this.padder = new WordPixelPadder();
        this.collisionChecker = new RectanglePixelCollisionChecker();
        List<Word> skipped = new ArrayList<>(this.skipped);
        this.skipped.clear();
        int total = skipped.size();
        if (total == 0) return true;

        System.out.print("\rProcessing skipped (pass " + (pass) + ") " + createProgress(0));
        int j = 0;
        for (Word sw : skipped) {
            int freq = sw.getFrequency() / 2;
            if (freq <= 0) {
                freq = 1;
            }
            Word word = buildWord(new WordFrequency(sw.getId(), sw.getWord(), sw.getFilter(), freq), maxFrequency(wordFrequencies), colorPalette);
            int startX = (width / 2) + (width / 4);
            int startY = (height / 2) + (height / 4);
            place(word, startX, startY);
            j++;
            String skip = (this.skipped.size() > 0) ? " [skipped " + this.skipped.size() + "]" : "";
            System.out.print("\rProcessing skipped (pass " + (pass) + ") " +
                    createProgress((j * 100)/total) + " " + j + " / " + total + " (" + (j * 100)/total + "%)" + skip);
        }
        System.out.println("\r\nProcessing skipped (pass " + (pass) + "). DONE");
        boolean ret = this.skipped.size() == 0;
        return ret;
    }

    public void fillWithOtherWords(List<WordFrequency> wordFrequencies, String[] extraWords) {
        System.out.println("Filling...");
        int count = extraWords.length;
        this.fontScalar.reduceBy(2);
        for (int i = 0; i < count * 5; i++) {
            Word word = buildWord(new WordFrequency(-1, extraWords[i % 5], extraWords[i % 5], 0), maxFrequency(wordFrequencies), colorPalette);
            int startX = (width / 2) + (width / 4);
            int startY = (height / 2) + (height / 4);
            place(word, startX, startY);
        }
        for (int i = 0; i < count * 5; i++) {
            Word word = buildWord(new WordFrequency(-1, extraWords[i % 5], extraWords[i % 5], 0), maxFrequency(wordFrequencies), colorPalette);
            int startX = width;
            int startY = height;
            place(word, startX, startY);
        }
        for (int i = 0; i < count * 5; i++) {
            Word word = buildWord(new WordFrequency(-1, extraWords[i % 5], extraWords[i % 5], 0), maxFrequency(wordFrequencies), colorPalette);
            int startX = width / 2;
            int startY = height;
            place(word, startX, startY);
        }
        for (int i = 0; i < count * 5; i++) {
            Word word = buildWord(new WordFrequency(-1, extraWords[i % 5], extraWords[i % 5], 0), maxFrequency(wordFrequencies), colorPalette);
            int startX = width;
            int startY = height / 2;
            place(word, startX, startY);
        }
        for (int i = 0; i < count * 5; i++) {
            Word word = buildWord(new WordFrequency(-1, extraWords[i % 5], extraWords[i % 5], 0), maxFrequency(wordFrequencies), colorPalette);
            int startX = 0;
            int startY = height;
            place(word, startX, startY);
        }
        this.skipped.clear();
    }
    
    public void printSkippedWords() {
        LOGGER.info("Cloud processing DONE. Skipped words: " + this.skipped.size());
    }

    public void writeToImage(final String outputFileName) {
        String extension = "";
        int i = outputFileName.lastIndexOf('.');
        if (i > 0) {
            extension = outputFileName.substring(i + 1);
        }
        try {
            LOGGER.info("Saving WordCloud to " + outputFileName);
            ImageIO.write(bufferedImage, extension, new File(outputFileName));
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    public void writeWordsToFile(final String outputFileName, int size) {
        OutputStreamWriter fw = null;
        try {
            LOGGER.info("Saving Words to " + outputFileName);
            fw = new OutputStreamWriter(new FileOutputStream(outputFileName), Charset.forName("UTF-8").newEncoder());
            fw.write(String.format("%d,%d\n", System.currentTimeMillis(), size));
            for (Word word : placedWords) {
                fw.write(String.format("%d,%d,%d,%d,%d,%d,%s,%s|%s\n",
                        word.getId(), word.getX(), word.getY(), word.getWidth(), word.getHeight(), word.getRotation(),
                        word.getFontSize(), word.getWord(), word.getFilter()));
            }
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
        } finally {
            if (fw != null) {
                try {
                    fw.close();
                } catch (IOException e) {
                }
            }
        }
        
    }

    /**
     * Write to output stream as PNG
     *
     * @param outputStream the output stream to write the image data to
     */
    public void writeToStreamAsPNG(final OutputStream outputStream) {
        writeToStream("png", outputStream);
    }

    /**
     * Write wordcloud image data to stream in the given format
     *
     * @param format       the image format
     * @param outputStream the output stream to write image data to
     */
    public void writeToStream(final String format, final OutputStream outputStream) {
        try {
            LOGGER.debug("Writing WordCloud image data to output stream");
            ImageIO.write(bufferedImage, format, outputStream);
            LOGGER.debug("Done writing WordCloud image data to output stream");
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException("Could not write wordcloud to outputstream due to an IOException", e);
        }
    }

    /**
     * create background, then draw current word cloud on top of it.
     * Doing it this way preserves the transparency of the this.bufferedImage's pixels
     * for a more flexible pixel perfect collision
     */
    public void drawForgroundToBackground() {
        if(backgroundColor == null) { return; }

        final BufferedImage backgroundBufferedImage = new BufferedImage(width, height, this.bufferedImage.getType());
        final Graphics graphics = backgroundBufferedImage.getGraphics();

        // draw current color
        graphics.setColor(backgroundColor);
        graphics.fillRect(0, 0, width, height);
        graphics.drawImage(bufferedImage, 0, 0, null);

        // draw back to original
        final Graphics graphics2 = bufferedImage.getGraphics();
        graphics2.drawImage(backgroundBufferedImage, 0, 0, null);
    }

    /**
     * try to place in center, build out in a spiral trying to place words for N steps
     * @param word The word to place
     * @param startX The x start position
     * @param startY The y start position
     * @return if the word was placed
     */
    protected boolean place(final Word word, final int startX, final int startY) {
        final Graphics graphics = this.bufferedImage.getGraphics();

        final int maxRadius = width;

        
            for(int r = 0; r < maxRadius; r += 2) {
                for(int x = -r; x <= r; x++) {
                    if(startX + x < 0) { continue; }
                    if(startX + x >= width) { continue; }
    
                    boolean placed = false;
                    word.setX(startX + x);
    
                    // try positive root
                    int y1 = (int) Math.sqrt(r * r - x * x);
                    if(startY + y1 >= 0 && startY + y1 < height) {
                        word.setY(startY + y1);
                        placed = tryToPlace(word);
                    }
                    // try negative root
                    int y2 = -y1;
                    if(!placed && startY + y2 >= 0 && startY + y2 < height) {
                        word.setY(startY + y2);
                        placed = tryToPlace(word);
                    }
                    if(placed) {
                        collisionRaster.mask(word.getCollisionRaster(), word.getX(), word.getY());
                        graphics.drawImage(word.getBufferedImage(), word.getX(), word.getY(), null);
                        return true;
                    }
    
                }
            }
   
        LOGGER.debug("skipped: " + word.getWord());
        skipped.add(word);
        return false;
    }

    private boolean tryToPlace(final Word word) {
        if(!background.isInBounds(word)) { return false; }

        switch(this.collisionMode) {
            case RECTANGLE:
                for(Word placeWord : this.placedWords) {
                    if(placeWord.collide(word)) {
                        return false;
                    }
                }
                LOGGER.debug("place: " + word.getWord());
                placedWords.add(word);
                return true;

            case PIXEL_PERFECT:
                if(backgroundCollidable.collide(word)) { return false; }
                LOGGER.debug("place: " + word.getWord());
                placedWords.add(word);
                return true;

        }
        return false;
    }

    protected List<Word> buildwords(final List<WordFrequency> wordFrequencies, final ColorPalette colorPalette) {
        final int maxFrequency = maxFrequency(wordFrequencies);

        final List<Word> words = new ArrayList<>();
        for(final WordFrequency wordFrequency : wordFrequencies) {
            words.add(buildWord(wordFrequency, maxFrequency, colorPalette));
        }
        return words;
    }

    private Word buildWord(final WordFrequency wordFrequency, int maxFrequency, final ColorPalette colorPalette) {
        final Graphics graphics = this.bufferedImage.getGraphics();

        final int frequency = wordFrequency.getFrequency();
        final float fontHeight = this.fontScalar.scale(frequency, 0, maxFrequency);
        final Font font = needSansFont(wordFrequency.getWord())
                ? sansFont.getFont().deriveFont(fontHeight)
                : cloudFont.getFont().deriveFont(fontHeight);

        final FontMetrics fontMetrics = graphics.getFontMetrics(font);
        final Word word = new Word(wordFrequency.getId(), wordFrequency.getWord(),
                wordFrequency.getFilter(), colorPalette.next(), wordFrequency.getFrequency(),
                fontHeight, fontMetrics, this.collisionChecker);

        final double theta = angleGenerator.next();
        if(theta != 0) {
            word.setBufferedImage(ImageRotation.rotate(word.getBufferedImage(), theta));
            word.setRotation((int)theta);
        }
        if(padding > 0) {
            padder.pad(word, padding);
        }
        return word;
    }

    private int maxFrequency(final Collection<WordFrequency> wordFrequencies) {
        if(wordFrequencies.isEmpty()) { return 1; }
        return Lambda.max(wordFrequencies, on(WordFrequency.class).getFrequency());
    }

    public void setBackgroundColor(Color backgroundColor) {
        this.backgroundColor = backgroundColor;
    }

    public void setPadding(int padding) {
        this.padding = padding;
    }

    public void setColorPalette(ColorPalette colorPalette) {
        this.colorPalette = colorPalette;
    }

    public void setBackground(Background background) {
        this.background = background;
    }

    public void setFontScalar(FontScalar fontScalar) {
        this.fontScalar = fontScalar;
    }

    public void setCloudFont(CloudFont cloudFont) {
        this.cloudFont = cloudFont;
    }

    public void setAngleGenerator(AngleGenerator angleGenerator) {
        this.angleGenerator = angleGenerator;
    }

    public BufferedImage getBufferedImage() {
        return bufferedImage;
    }

    public ArrayList<Word> getSkipped() {
        return skipped;
    }

    private boolean needSansFont(String src) {
        for (int i = 0; i < src.length(); ) {
            int c = src.codePointAt(i);
            i += Character.charCount(c);
            if ((Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS)
                    || (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A)
                    || (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B)
                    || (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_COMPATIBILITY_FORMS)
                    || (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS)
                    || (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_RADICALS_SUPPLEMENT)
                    || (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION)
                    || (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.ENCLOSED_CJK_LETTERS_AND_MONTHS)
                    || (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.HANGUL_SYLLABLES)
                    || (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.HANGUL_JAMO)
                    || (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO)
                    || (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.HANGUL_JAMO_EXTENDED_A)
                    || (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.HANGUL_JAMO_EXTENDED_B)
                    || (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.ARABIC)
                    || (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_A)
                    || (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_B)
                    || (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.ARABIC_SUPPLEMENT)
                    || (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.HEBREW)
                    || (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.KAITHI)
                    || (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.DEVANAGARI)
                    || (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.DEVANAGARI_EXTENDED)) {
                return true;
            }
        }
        return false;
    }
}
