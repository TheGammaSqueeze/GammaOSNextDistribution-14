package com.android.class2nonsdklist;

import com.android.annotationvisitor.AnnotationConsumer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

public class HiddenapiFlagsWriter implements AnnotationConsumer {

    private final PrintStream mOutput;

    // A sorted map from signature to a comma separated sorted list of flags
    private final SortedMap<String, String> mContents;

    public HiddenapiFlagsWriter(String csvFile) throws FileNotFoundException {
        mOutput = new PrintStream(new FileOutputStream(new File(csvFile)));
        mContents = new TreeMap<>();
    }

    public void consume(String apiSignature, Map<String, String> annotationProperties,
            Set<String> parsedFlags) {
        if (parsedFlags.size() > 0) {
            mContents.put(apiSignature, String.join(",", asSortedList(parsedFlags)));
        }
    }

    public void close() {
        for (Map.Entry<String, String> row : mContents.entrySet()) {
            mOutput.println(row.getKey() + "," + row.getValue());
        }

        mOutput.close();
    }

    private static List<String> asSortedList(Set<String> s) {
        List<String> list = new ArrayList<>(s);
        Collections.sort(list);
        return list;
    }
}
