package com.ucms.grading.service;

import java.io.InputStream;

public final class Files {
    private Files() {}

    public record Download(String fileName, String contentType, InputStream content) {}

    /** Keeps uploaded names usable as object-key segments and Content-Disposition values. */
    static String safeName(String original) {
        String name = original == null || original.isBlank() ? "file" : original;
        name = name.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = name.replaceAll("[^A-Za-z0-9._-]", "_");
        return name.length() > 120 ? name.substring(name.length() - 120) : name;
    }
}
