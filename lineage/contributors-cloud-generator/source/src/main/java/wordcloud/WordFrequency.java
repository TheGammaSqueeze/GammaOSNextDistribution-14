package wordcloud;

/**
 * Created by kenny on 6/29/14.
 */
public class WordFrequency implements Comparable<WordFrequency> {

    private final int id;
    private final String word;
    private final String filter;
    private final int frequency;

    public WordFrequency(int id, String word, String filter, int frequency) {
        this.id = id;
        this.word = word;
        this.filter = filter;
        this.frequency = frequency;
    }

    public int getId() {
        return id;
    }

    public String getWord() {
        return word;
    }

    public String getFilter() {
        return filter;
    }

    public int getFrequency() {
        return frequency;
    }

    @Override
    public int compareTo(WordFrequency wordFrequency) {
        int ret = wordFrequency.frequency - frequency;
        if (ret == 0) ret = wordFrequency.word.length() - word.length();
        return ret;
    }

}
