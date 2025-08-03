package com.android.class2nonsdklist;

import com.android.annotationvisitor.AnnotationConsumer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class AnnotationPropertyWriter implements AnnotationConsumer {

    private static final String SIGNATURE_COLUMN = "signature";
    private final PrintStream mOutput;
    private final List<Map<String, String>> mContents;
    private final Set<String> mColumns;

    public AnnotationPropertyWriter(String csvFile) throws FileNotFoundException {
        mOutput = new PrintStream(new FileOutputStream(new File(csvFile)));
        mContents = new ArrayList<>();
        mColumns = new HashSet<>();
    }

    public AnnotationPropertyWriter(OutputStream output) {
        mOutput = new PrintStream(output);
        mContents = new ArrayList<>();
        mColumns = new HashSet<>();
    }

    public void consume(String apiSignature, Map<String, String> annotationProperties,
            Set<String> parsedFlags) {
        // Clone properties map.
        Map<String, String> contents = new HashMap<>(annotationProperties);

        // Append the member signature.
        contents.put(SIGNATURE_COLUMN, apiSignature);

        // Store data.
        mColumns.addAll(contents.keySet());
        mContents.add(contents);
    }

    private static String escapeCsvColumn(String column) {
        // Using '|' as a quote character, as in frameworks/base/tools/hiddenapi/merge_csv.py
        // Escape '|' characters in the column, then wrap the column in '|' characters.
        column = column.replace("|", "||");
        return "|" + column + "|";
    }

    public void close() {
        // Sort columns by name.
        List<String> columns = new ArrayList<>(mColumns);
        columns.sort(Comparator.naturalOrder());

        // Make sure that the signature column, if present, comes first.
        if (columns.remove(SIGNATURE_COLUMN)) {
            columns.add(0, SIGNATURE_COLUMN);
        }

        // Print header row.
        mOutput.println(String.join(",", columns));

        // Sort rows by signature
        mContents.sort(Comparator.comparing(r -> r.get(SIGNATURE_COLUMN)));

        // Print contents of each row in column order.
        for (Map<String, String> row : mContents) {
            mOutput.println(columns.stream().map(column -> row.getOrDefault(column, ""))
                .map(AnnotationPropertyWriter::escapeCsvColumn)
                .collect(Collectors.joining(",")));
        }

        // Close output.
        mOutput.close();
    }
}
