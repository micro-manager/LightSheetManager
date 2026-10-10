package org.micromanager.lightsheetmanager.model;

import org.micromanager.lightsheetmanager.LightSheetManager;
import org.micromanager.lightsheetmanager.gui.utils.DialogUtils;

import java.util.Objects;

/**
 * The plugin's logging and error-reporting service.
 *
 * <p>Two method families with different contracts:
 * <ul>
 *   <li>{@code logMessage}/{@code logDebugMessage}/{@code logError} write to the CoreLog and
 *       never show UI, so they are safe from any thread in any mode.</li>
 *   <li>{@code reportError}/{@code confirmOrDefault} are user-facing and honor the
 *       "Acquisition failures are quiet" setting: quiet only logs, otherwise the standard
 *       dialogs are also shown.</li>
 * </ul>
 *
 * <p>{@code reportError} shows its dialog through MM's {@code showError}, which returns
 * immediately, so it never holds the acquisition thread; only {@code confirmOrDefault}
 * waits for an answer, which quiet mode gives in an unattended run (Playlist, scripting).
 *
 * <p>This is deliberately an instance class, not static utilities: the backend it writes
 * through must be swappable per model instance for headless operation.
 */
public final class Logging {

    private final LightSheetManager model_;

    public Logging(final LightSheetManager model) {
        model_ = Objects.requireNonNull(model);
    }

    // The setting is read through the model on every call because loading user settings
    // replaces the PluginSettings object; a reference captured here would go stale.
    private boolean isQuiet() {
        return model_.pluginSettings().isAcquireFailQuietly();
    }

    // --- pure logging - never shows UI ---

    /**
     * Logs a message.
     *
     * @param message the message to log
     */
    public void logMessage(final String message) {
        model_.studio().logs().logMessage(message);
    }

    /**
     * Logs a debug message.
     *
     * @param message the message to log
     */
    public void logDebugMessage(final String message) {
        model_.studio().logs().logDebugMessage(message);
    }

    /**
     * Logs an error message.
     *
     * @param message the error message
     */
    public void logError(final String message) {
        model_.studio().logs().logError(message);
    }

    /**
     * Logs an exception.
     *
     * @param e the exception to log
     */
    public void logError(final Exception e) {
        model_.studio().logs().logError(e);
    }

    /**
     * Logs an exception with a message.
     *
     * @param e the exception to log
     * @param message the error message
     */
    public void logError(final Exception e, final String message) {
        model_.studio().logs().logError(e, message);
    }

    // --- user-facing - honors "Acquisition failures are quiet" ---

    /**
     * Reports an error message.
     *
     * @param message the error message
     */
    public void reportError(final String message) {
        if (isQuiet()) {
            logError(message);
        } else {
            model_.studio().logs().showError(message);
        }
    }

    /**
     * Reports an exception.
     *
     * @param e the exception to report
     */
    public void reportError(final Exception e) {
        if (isQuiet()) {
            logError(e);
        } else {
            model_.studio().logs().showError(e);
        }
    }

    /**
     * Reports an exception with a message.
     *
     * @param e the exception to report
     * @param message the error message
     */
    public void reportError(final Exception e, final String message) {
        if (isQuiet()) {
            logError(e, message);
        } else {
            model_.studio().logs().showError(e, message);
        }
    }

    /**
     * Asks the user a yes/no question, unless acquisition failures are quiet, in which case
     * {@code quietAnswer} is returned without showing a dialog and the choice is logged.
     *
     * <p>{@code quietAnswer} should be the answer an attended user is expected to give, so quiet
     * runs follow the recommended path rather than silently declining it. 1.4 had no quiet
     * variant of {@code getConfirmDialogResult} at all, so its confirm dialogs could still hang
     * an unattended acquisition.
     *
     * @param title the dialog title
     * @param message the yes/no question
     * @param quietAnswer the answer to use without asking when failures are quiet
     * @return the user's answer, or {@code quietAnswer} when failures are quiet
     */
    public boolean confirmOrDefault(final String title, final String message, final boolean quietAnswer) {
        if (isQuiet()) {
            logMessage("Quiet acquisition: answered \"" + (quietAnswer ? "Yes" : "No")
                    + "\" without showing the dialog [" + title + "]: " + message);
            return quietAnswer;
        }
        return DialogUtils.showYesNoDialog(null, title, message);
    }

}
