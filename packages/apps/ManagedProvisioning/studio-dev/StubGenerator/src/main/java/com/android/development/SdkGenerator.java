package com.android.development;

import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.dexbacked.DexBackedClassDef;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.iface.MultiDexContainer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class SdkGenerator {

    public static void main(String[] args) throws Exception {
        List<DexBackedClassDef> dexClasses = new ArrayList<>();
        List<File> zipFiles = new ArrayList<>();
        File outFile = null;
        File aidlFile = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-h":
                case "--help":
                    printHelp();
                    return;
                case "--dex":
                case "-d": {
                    MultiDexContainer<? extends DexBackedDexFile> dexContainers =
                            DexFileFactory.loadDexContainer(verifyExists(args[++i]), null);
                    for (String name : dexContainers.getDexEntryNames()) {
                        dexClasses.addAll(dexContainers.getEntry(name).getClasses());
                    }
                    break;
                }
                case "--zip":
                case "-z":
                    zipFiles.add(verifyExists(args[++i]));
                    break;
                case "--out":
                case "-o":
                    outFile = new File(args[++i]);
                    break;
                case "--aidl":
                case "-a":
                    aidlFile = new File(args[++i]);
                    break;
            }
        }

        if (outFile == null) {
            throw new IllegalArgumentException("Out not specified");
        }

        HashSet<String> classAdded = new HashSet<>();
        int count = 0;
        DexToStubConverter converter;
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(outFile))) {
            converter = new DexToStubConverter(out);

            // First loop to initialize inner class map
            for (DexBackedClassDef classDef : dexClasses) {
                converter.expectClass(classDef);
            }

            ProgressBar progress = new ProgressBar("Converting classes", dexClasses.size());
            for (DexBackedClassDef classDef : dexClasses) {
                classAdded.add(converter.writeClass(classDef));
                count++;
                progress.update(count);
            }
            progress.finish();

            for (File zip : zipFiles) {
                try (FileInputStream fin = new FileInputStream(zip);
                     FileChannel channel = fin.getChannel();
                     ZipInputStream zipin = new ZipInputStream(fin)) {
                    progress = new ProgressBar("Merging " + zip.getName(), channel.size());
                    ZipEntry nextEntry;
                    while ((nextEntry = zipin.getNextEntry()) != null) {
                        if (classAdded.contains(nextEntry.getName())) {
                            // Skip
                        } else {
                            out.putNextEntry(new ZipEntry(nextEntry.getName()));
                            classAdded.add(nextEntry.getName());
                            copyStream(zipin, out);
                        }
                        progress.update(channel.position());
                    }
                    progress.finish();
                }
            }
            System.out.println("Writing final sdk");
        }

        if (aidlFile != null && aidlFile.exists()) {
            System.out.println("Merging Aidl");

            try (BufferedReader in = new BufferedReader(new FileReader(aidlFile))) {
                String l;
                while ((l = in.readLine()) != null) {
                    converter.expectAidlDef(l);
                }
            }

            try (PrintStream out = new PrintStream(new FileOutputStream(aidlFile, true))) {
                out.println();
                out.println();
                converter.printInterfaces(out);
            }
        }
    }

    private static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int len;
        while ((len = in.read(buffer)) != -1) {
            out.write(buffer, 0, len);
        }
    }

    private static File verifyExists(String path) {
        File file = new File(path);
        if (!file.exists() || file.isDirectory()) {
            throw new IllegalArgumentException("Invalid file argument " + file);
        }
        return file;
    }

    private static void printHelp() {
        System.out.println("SdkGenerator [options]");
        System.out.println("  --help | -h: Print this message");
        System.out.println("  --dex | -d: Decompile and add a dex file to the sdk jar");
        System.out.println("  --zip | -z: Unzip's and adds the content to the sdk jar");
        System.out.println("  --aidl | -a: Aidl file to append any missing definitions");
        System.out.println("  --out | -o: The output zip file");
    }
}
