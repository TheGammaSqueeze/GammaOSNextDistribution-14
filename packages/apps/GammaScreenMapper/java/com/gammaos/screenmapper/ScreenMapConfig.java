package com.gammaos.screenmapper;

import android.os.SystemProperties;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ScreenMapConfig {
    private static final String TAG = "ScreenMapConfig";
    private static final String CONFIG_DIR = "/data/misc/gammapad/screenmap";

    public static class MappingPoint {
        public enum Type { BUTTON, STICK_LEFT, STICK_RIGHT, DPAD }
        public Type type;
        public int x, y;
        public int radius;
        public int buttonCode;

        public MappingPoint(Type type, int x, int y, int radius, int buttonCode) {
            this.type = type;
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.buttonCode = buttonCode;
        }

        public String getTypeString() {
            switch (type) {
                case BUTTON: return "button";
                case STICK_LEFT: return "stick_left";
                case STICK_RIGHT: return "stick_right";
                case DPAD: return "dpad";
                default: return "button";
            }
        }

        public static Type parseType(String s) {
            switch (s) {
                case "stick_left": return Type.STICK_LEFT;
                case "stick_right": return Type.STICK_RIGHT;
                case "dpad": return Type.DPAD;
                default: return Type.BUTTON;
            }
        }

        public String getLabel() {
            switch (type) {
                case STICK_LEFT: return "L";
                case STICK_RIGHT: return "R";
                case DPAD: return "D";
                case BUTTON:
                    return getButtonLabel(buttonCode);
                default: return "?";
            }
        }

        public static String getButtonLabel(int code) {
            switch (code) {
                case 0x130: return "A";
                case 0x131: return "B";
                case 0x133: return "X";
                case 0x134: return "Y";
                case 0x136: return "LB";
                case 0x137: return "RB";
                case 0x138: return "LT";
                case 0x139: return "RT";
                case 0x13d: return "L3";
                case 0x13e: return "R3";
                case 0x13b: return "Start";
                case 0x13a: return "Sel";
                default: return "?";
            }
        }
    }

    public static boolean save(String pkg, List<MappingPoint> points) {
        File dir = new File(CONFIG_DIR);
        if (!dir.exists()) dir.mkdirs();

        File file = new File(dir, pkg + ".conf");
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("# Screen mapping config for " + pkg + "\n");
            for (MappingPoint p : points) {
                writer.write(p.getTypeString() + " " + p.x + " " + p.y
                        + " " + p.radius + " " + p.buttonCode + "\n");
            }
            // Bump config version to trigger gammapad reload
            bumpConfigVersion();
            Log.i(TAG, "Saved " + points.size() + " mappings for " + pkg);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to save config for " + pkg, e);
            return false;
        }
    }

    public static List<MappingPoint> load(String pkg) {
        List<MappingPoint> points = new ArrayList<>();
        File file = new File(CONFIG_DIR, pkg + ".conf");
        if (!file.exists()) return points;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] parts = line.split("\\s+");
                if (parts.length < 5) continue;

                MappingPoint.Type type = MappingPoint.parseType(parts[0]);
                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);
                int radius = Integer.parseInt(parts[3]);
                int buttonCode = Integer.parseInt(parts[4]);

                points.add(new MappingPoint(type, x, y, radius, buttonCode));
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load config for " + pkg, e);
        }
        return points;
    }

    public static boolean exists(String pkg) {
        return new File(CONFIG_DIR, pkg + ".conf").exists();
    }

    public static void delete(String pkg) {
        File file = new File(CONFIG_DIR, pkg + ".conf");
        if (file.exists()) file.delete();
        bumpConfigVersion();
    }

    private static void bumpConfigVersion() {
        int current = SystemProperties.getInt("persist.gammaos.gamepad.config_version", 0);
        SystemProperties.set("persist.gammaos.gamepad.config_version",
                String.valueOf(current + 1));
    }
}
