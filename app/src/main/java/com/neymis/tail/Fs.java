package com.neymis.tail;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

final class Fs {
    private Fs() {
    }

    static boolean copy(File src, File dst) throws IOException {
        if (src.isDirectory()) {
            if (!dst.isDirectory() && !dst.mkdirs()) return false;
            File[] children = src.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!copy(child, new File(dst, child.getName()))) return false;
                }
            }
            return true;
        }
        File parent = dst.getParentFile();
        if (parent != null && !parent.isDirectory()) parent.mkdirs();
        try (InputStream in = new FileInputStream(src);
             OutputStream out = new FileOutputStream(dst)) {
            byte[] buffer = new byte[16384];
            int count;
            while ((count = in.read(buffer)) > 0) out.write(buffer, 0, count);
        }
        return true;
    }

    static boolean move(File src, File dst) {
        if (src.renameTo(dst)) return true;
        try {
            return copy(src, dst) && delete(src);
        } catch (IOException e) {
            return false;
        }
    }

    static boolean delete(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) delete(child);
        }
        return file.delete();
    }

    static long size(File file) {
        if (file.isFile()) return file.length();
        long total = 0;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) total += size(child);
        return total;
    }

    static String human(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    static byte[] readAll(File file) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[16384];
            int count;
            while ((count = in.read(buffer)) > 0) out.write(buffer, 0, count);
        }
        return out.toByteArray();
    }

    static List<String> readLines(File file) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) lines.add(line);
        }
        return lines;
    }

    static void writeAll(File file, byte[] data) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory()) parent.mkdirs();
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(data);
        }
    }

    static String hash(File file, String algorithm) throws Exception {
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[16384];
            int count;
            while ((count = in.read(buffer)) > 0) digest.update(buffer, 0, count);
        }
        byte[] raw = digest.digest();
        StringBuilder builder = new StringBuilder();
        for (byte b : raw) builder.append(String.format("%02x", b));
        return builder.toString();
    }

    static void zip(File source, File output) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(output))) {
            addToZip(source, source.getName(), zos);
        }
    }

    private static void addToZip(File file, String entry, ZipOutputStream zos) throws IOException {
        if (file.isDirectory()) {
            zos.putNextEntry(new ZipEntry(entry + "/"));
            zos.closeEntry();
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) addToZip(child, entry + "/" + child.getName(), zos);
            }
            return;
        }
        zos.putNextEntry(new ZipEntry(entry));
        byte[] buffer = new byte[16384];
        try (InputStream in = new FileInputStream(file)) {
            int count;
            while ((count = in.read(buffer)) > 0) zos.write(buffer, 0, count);
        }
        zos.closeEntry();
    }

    static int unzip(File archive, File destination) throws IOException {
        if (!destination.isDirectory() && !destination.mkdirs()) return -1;
        String root = destination.getCanonicalPath() + File.separator;
        byte[] buffer = new byte[16384];
        int count = 0;
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File out = new File(destination, entry.getName());
                if (!out.getCanonicalPath().startsWith(root)) continue;
                if (entry.isDirectory()) {
                    out.mkdirs();
                    continue;
                }
                File parent = out.getParentFile();
                if (parent != null) parent.mkdirs();
                try (OutputStream os = new FileOutputStream(out)) {
                    int n;
                    while ((n = zis.read(buffer)) > 0) os.write(buffer, 0, n);
                }
                count++;
            }
        }
        return count;
    }

    static List<File> search(File dir, String query, int limit) {
        List<File> result = new ArrayList<>();
        walk(dir, query.toLowerCase(), result, limit);
        return result;
    }

    private static void walk(File dir, String needle, List<File> result, int limit) {
        if (result.size() >= limit) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (result.size() >= limit) return;
            if (child.getName().toLowerCase().contains(needle)) result.add(child);
            if (child.isDirectory()) walk(child, needle, result, limit);
        }
    }

    static File unique(File dir, String name) {
        File target = new File(dir, name);
        if (!target.exists()) return target;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 1; i < 1000; i++) {
            target = new File(dir, base + " (" + i + ")" + ext);
            if (!target.exists()) return target;
        }
        return target;
    }
}
