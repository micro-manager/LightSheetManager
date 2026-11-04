package org.micromanager.lightsheetmanager.gui.tabs.acquisition;

import org.micromanager.lightsheetmanager.LightSheetManager;
import org.micromanager.lightsheetmanager.gui.components.Panel;
import org.micromanager.lightsheetmanager.model.acquisitions.AcquisitionEngine;
import org.micromanager.lightsheetmanager.model.acquisitions.RunProgress;

import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import java.util.Objects;

/**
 * The status line of the acquisition tab: where the run is, how long until the next time point
 * and how the last run ended, with a bar of the images so far.
 *
 * <p>Polls the engine on the interface thread while the panel is displayable, so the
 * acquisition's threads never touch Swing, and a run started from a script shows too.
 */
public class StatusPanel extends Panel {

    // often enough that the countdown steps once a second
    private static final int POLL_MS = 250;

    // the bar's resolution
    private static final int BAR_STEPS = 1000;

    private final AcquisitionEngine engine;
    private final JLabel lblStatus;
    private final JProgressBar barImages;
    private final JLabel lblImages;
    private final Timer timer;

    public StatusPanel(final LightSheetManager model) {
        super("Status");
        engine = Objects.requireNonNull(model).acquisitions();

        // the bar takes the width the image count leaves
        setMigLayout("", "[grow, fill][]", "");

        lblStatus = new JLabel();
        // two lines high, so a long message wraps instead of resizing the panel
        lblStatus.setVerticalAlignment(SwingConstants.TOP);
        final int lineHeight = lblStatus.getFontMetrics(lblStatus.getFont()).getHeight();

        barImages = new JProgressBar(0, BAR_STEPS);
        // the count goes beside the bar: some themes paint text on a bar in nearly the color of
        // its filled part
        lblImages = new JLabel();

        add(lblStatus, "span 2, wmin 0, h " + 2 * lineHeight + "!, wrap");
        add(barImages, "wmin 0");
        add(lblImages, "");

        timer = new Timer(POLL_MS, e -> refresh());
        refresh();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        timer.start();
    }

    @Override
    public void removeNotify() {
        // the window is being disposed: a running timer would keep the panel alive
        timer.stop();
        super.removeNotify();
    }

    /**
     * Shows the engine's progress as it is now. The timer calls this while the panel is showing.
     * A label set to the text it already shows is neither laid out again nor repainted.
     */
    public void refresh() {
        final RunProgress now = engine.progress();
        // html, so a message longer than the panel wraps onto the second line
        lblStatus.setText("<html>" + now.describe() + "</html>");
        final long expected = now.imagesExpected();
        barImages.setValue(expected == 0 ? 0
                : (int) (BAR_STEPS * Math.min(now.images(), expected) / expected));
        lblImages.setText(now.describeImages());
    }
}
