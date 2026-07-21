package org.micromanager.lightsheetmanager;

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
    public static final String version = "0.7.3";

    private Studio studio_;
    private LightSheetManager model_;
    private LightSheetManagerFrame frame_;

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
                frame_ = null;
                model_ = null;
            });

            frame_.pack();
            frame_.setVisible(true);
            frame_.toFront();
        } catch (Exception e) {
            // cleanup resources
            frame_ = null;
            model_ = null;
            if (studio_ != null) {
                studio_.logs().showError(e, "Error starting Light Sheet Manager");
            }
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
