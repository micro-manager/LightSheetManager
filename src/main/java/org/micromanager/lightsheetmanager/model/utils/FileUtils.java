package org.micromanager.lightsheetmanager.model.utils;

import org.micromanager.internal.MMStudio;

import java.io.File;
import java.io.IOException;

/**
 * Utilities to read and write to files.
 */
public final class FileUtils {

    /** This class should not be instantiated. */
    private FileUtils() {
        throw new AssertionError("Utility class; do not instantiate.");
    }

    /**
     * Reads a text file with UTF-8 encoding into a String.
     *
     * @param filePath - the location to write the text file
     * @return a String with the contents of the text file
     */
    public static String readFileToString(final String filePath) {
        String result = "";
        try {
            result = org.apache.commons.io.FileUtils.readFileToString(new File(filePath), "UTF-8");
        } catch (IOException e) {
            MMStudio.getInstance().logs().logError("FileUtils: readFileToString error");
        }
        return result;
    }

    /**
     * Writes contents to the text file using UTF-8 encoding.
     *
     * @param filePath - the location to write the text file
     * @param contents - the String to write to the file
     */
    public static void writeStringToFile(final String filePath, final String contents) {
        try {
            org.apache.commons.io.FileUtils.writeStringToFile(new File(filePath), contents, "UTF-8");
        } catch (IOException e) {
            MMStudio.getInstance().logs().logError("FileUtils: writeStringToFile error");
        }
    }

    public static String createUniquePath(final String directory, final String name) {
        // check if the file path is available => early exit if true
        if (!(new File(directory + File.separator + name).exists())) {
            return directory + File.separator + name;
        }
        // otherwise look for an unused file path
        String path = "";
        int count = 0;
        boolean found = false;
        while (!found && count < 1_000_000) {
            path = directory + File.separator + name + "_" + count;
            found = !(new File(path).exists());
            count++;
        }
        return path;
    }
}