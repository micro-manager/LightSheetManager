package org.micromanager.lightsheetmanager.gui.utils;

import javax.swing.JOptionPane;
import javax.swing.JTextArea;
import java.awt.Component;

// TODO: is errorLog the best way to send errors to JTextArea? reconsider impl for AcquisitionTable

/**
 * A utility class for making dialog boxes.
 *
 */
public final class DialogUtils {

    /** This class should not be instantiated. */
    private DialogUtils() {
        throw new AssertionError("Utility class; do not instantiate.");
    }

    /**Standard error reporting or delegate to JTextArea component. */
    public static boolean SEND_ERROR_TO_COMPONENT = false;

    /**The component to display the errors. */
    private static JTextArea errorLog = null;

    /**
     * Sets the JTextArea to log errors.
     *
     * @param textArea a reference to the object
     */
    public static void setErrorLog(final JTextArea textArea) {
        errorLog = textArea;
    }

    /**
     * Return the user's input or null if the action is canceled.
     *
     * @param frame the parent frame
     * @param title the title string
     * @param message the message to display
     * @return the user's input or {@code null} meaning the user canceled the input
     */
    public static String showTextEntryDialog(final Component frame, final String title, final String message) {
        return (String) JOptionPane.showInputDialog(frame, message, title,
                JOptionPane.PLAIN_MESSAGE, null, null, "");
    }

    /**
     * Return {@code true} if "Yes" is selected or {@code false} if "No" is selected.
     *
     * @param frame the parent frame
     * @param title the title string
     * @param message the message to display
     * @return true if the user selected "Yes"
     */
    public static boolean showYesNoDialog(final Component frame, final String title, final String message) {
        return JOptionPane.showConfirmDialog(frame, message, title, JOptionPane.YES_NO_OPTION) == 0;
    }

    /**
     * Shows a customized message dialog box, this method does not log the error.<P>
     * This is used for reporting errors in the AcquisitionTable.
     *
     * @param frame the parent frame
     * @param title the title string
     * @param message the message to display
     */
    public static void showErrorMessage(final Component frame, final String title, final String message) {
        JOptionPane.showMessageDialog(frame, message, title, JOptionPane.ERROR_MESSAGE);
    }

}
