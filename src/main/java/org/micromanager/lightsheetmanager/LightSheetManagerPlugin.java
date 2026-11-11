package org.micromanager.lightsheetmanager;

import com.google.common.eventbus.Subscribe;
import org.micromanager.events.ShutdownCommencingEvent;
import org.micromanager.lightsheetmanager.gui.utils.WindowUtils;
import org.micromanager.MenuPlugin;
import org.micromanager.Studio;

import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

import javax.swing.JFrame;

@Plugin(type = MenuPlugin.class)
public class LightSheetManagerPlugin implements MenuPlugin, SciJavaPlugin {

    public static final String copyright = "Applied Scientific Instrumentation (ASI), 2022-2026";
    public static final String description = "A plugin to control various types of light sheet microscopes.";
    public static final String menuName = "Light Sheet Manager";
    public static final String version = "0.8.22";

    private Studio studio_;

    // volatile for visibility: written on the edt, read from the pycromanager server thread
    private volatile LightSheetManager model_;
    private LightSheetManagerFrame frame_;

    /**
     * Returns the model the plugin is currently running.
     *
     * @return the model, or null if the plugin is not open
     */
    public LightSheetManager getModel() {
        return model_;
    }

    @Override
    public void setContext(final Studio studio) {
        studio_ = studio;
    }

    @Override
    public String getSubMenu() {
        return "Beta"; // TODO: Change to "Device Control" when out of the Beta stage.
    }

    @Override
    public void onPluginSelected() {
        // restore the window if the plugin is already open
        if (WindowUtils.isOpen(frame_)) {
            if (WindowUtils.isMinimized(frame_)) {
                frame_.setState(JFrame.NORMAL);
            }
            frame_.setVisible(true);
            frame_.toFront();
            frame_.requestFocus();
            return; // early exit => plugin is already open
        }

        // start the plugin
        try {
            // create the data model and load settings
            model_ = new LightSheetManager(studio_);
            final boolean isLoaded = model_.setup();

            // create the ui; show an error ui on failure
            frame_ = new LightSheetManagerFrame(model_, isLoaded);
            frame_.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

            // clean up resources before the window is fully closed
            // call the shutdown code if the main ui was loaded, skip if error ui
            WindowUtils.registerWindowClosingEvent(frame_, event -> {
                if (isLoaded) {
                    model_.close();
                }
            });

            // clear references after the window is fully closed
            // prevent memory leaks from closed plugin instances
            WindowUtils.registerWindowClosedEvent(frame_, event -> {
                if (isLoaded) {
                    studio_.events().unregisterForEvents(this);
                }
                frame_ = null;
                model_ = null;
            });

            frame_.pack();
            frame_.setVisible(true);
            frame_.toFront();

            // quitting Micro-Manager does not close this window, so save the settings on exit too
            if (isLoaded) {
                studio_.events().registerForEvents(this);
            }
        } catch (Exception e) {
            // cleanup resources
            frame_ = null;
            model_ = null;
            if (studio_ != null) {
                studio_.logs().showError(e, "Error starting Light Sheet Manager");
            }
        }
    }

    /**
     * Saves the settings when Micro-Manager shuts down, before it writes the user profile to disk.
     */
    @Subscribe
    public void onShutdownCommencing(final ShutdownCommencingEvent event) {
        if (event.isCanceled()) {
            return; // early exit => the shutdown was canceled
        }
        try {
            model_.userSettings().save();
            studio_.logs().logMessage("User settings saved because Micro-Manager is shutting down.");
        } catch (RuntimeException e) {
            studio_.logs().logError(e, "Failed to save user settings while Micro-Manager was shutting down.");
        }
    }

    @Override
    public String getName() {
        return menuName;
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public String getCopyright() {
        return copyright;
    }

    @Override
    public String getHelpText() {
        return description;
    }

}
