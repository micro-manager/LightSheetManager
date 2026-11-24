package org.micromanager.lightsheetmanager.model;

import mmcorej.org.json.JSONException;
import mmcorej.org.json.JSONObject;
import org.micromanager.UserProfile;
import org.micromanager.lightsheetmanager.LightSheetManager;
import org.micromanager.lightsheetmanager.api.AcquisitionSettings;
import org.micromanager.lightsheetmanager.api.data.GeometryType;
import org.micromanager.lightsheetmanager.api.internal.ScapeAcquisitionSettings;
import org.micromanager.lightsheetmanager.gui.components.SettingsListener;
import org.micromanager.lightsheetmanager.model.utils.JsonUtils;
import org.micromanager.propertymap.MutablePropertyMapView;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Micro-Manager user settings for the current user.
 * <p>
 * This class is used to save and load settings using JSON strings.
 */
public class UserSettings {

    private final String userName_;
    private final MutablePropertyMapView settings_;

    private static final String SETTINGS_PLUGIN =  "LSM_PLUGIN_SETTINGS";

    // This is the prefix String for saving the current acquisition settings
    // based on the microscope geometry type, "LSM_ACQ_SETTINGS_SCAPE" for example.
    // Note: GeometryType will be converted to uppercase: "LSM_ACQ_SETTINGS_DISPIM".
    private static final String SETTINGS_PREFIX = "LSM_ACQ_SETTINGS_";
    private static final String SETTINGS_NOT_FOUND = "Settings Not Found";

    // Note: increase this value based on the amount of nested json in the settings
    private static final int MAX_RECURSION_DEPTH_JSON = 4;

    private final List<SettingsListener> listeners = new ArrayList<>();

    private final LightSheetManager model_;

    public UserSettings(final LightSheetManager model) {
        model_ = Objects.requireNonNull(model);
        // setup user profile
        final UserProfile profile = model_.studio().getUserProfile();
        settings_ = profile.getSettings(UserSettings.class);
        userName_ = profile.getProfileName();
    }

    /**
     * Returns an object to save and retrieve settings.
     *
     * @return a reference to MutablePropertyMapView
     */
    public MutablePropertyMapView get() {
        return settings_;
    }

    /**
     * Returns the name of the user profile.
     *
     * @return a {@code String} containing the name
     */
    public String userName() {
        return userName_;
    }

    /**
     * Clears all user settings associated with this profile name.
     */
    public void clear() {
        settings_.clear();
    }

    /**
     * Load user settings.
     */
    public void load() {
        // get JSON from settings based on microscope geometry type
        final GeometryType geometryType = model_.devices().adapter().geometry();

        // get the JSON from the user settings based on geometry type
        final String key = SETTINGS_PREFIX + geometryType.toString().toUpperCase();
        final String json = settings_.getString(key, SETTINGS_NOT_FOUND);

        // use default settings if settings data not found in profile
        if (json.equals(SETTINGS_NOT_FOUND)) {
            model_.studio().logs().logDebugMessage(
                    "settings not found, using default settings for " + geometryType);
            // add the cameras to the imaging camera order
            model_.devices().useDefaultImagingCameraOrder();
        } else {
            // validate user settings and create AcquisitionSettings object
            final Optional<JSONObject> settingsJson = validateUserSettings(json);
            settingsJson.ifPresent(jsonObject -> {
                loadFromJson(jsonObject.toString(), false);
                model_.studio().logs().logDebugMessage("loaded JSON from " + key + ": "
                        + model_.acquisitions().settings().toPrettyJson());
            });
        }

        // load plugin settings or default plugin settings
        final String jsonStr = settings_.getString(SETTINGS_PLUGIN, SETTINGS_NOT_FOUND);
        if (jsonStr.equals(SETTINGS_NOT_FOUND)) {
            model_.studio().logs().logDebugMessage("settings not found, using default plugin settings.");
        } else {
            model_.pluginSettings(PluginSettings.fromJson(jsonStr));
            model_.studio().logs().logDebugMessage("loaded PluginSettings from " + SETTINGS_PLUGIN + ": "
                    + model_.pluginSettings().toPrettyJson());
        }
    }

    /**
     * Load {@link AcquisitionSettings} from JSON text.
     *
     * <p>The text is what a settings file holds, not the path to the file. It is not merged with
     * the default settings as the settings saved in the profile are, so every settings block must
     * be present.
     *
     * @param json the acquisition settings as JSON text
     * @param notify true to show the loaded settings in the user interface
     * @throws IllegalArgumentException if the text is empty or the JSON {@code null}
     * @throws com.google.gson.JsonParseException if the text is not a JSON object or a value has
     *         the wrong type
     * @throws NullPointerException if a settings block is missing
     */
    public void loadFromJson(final String json, final boolean notify) {
        // TODO: switch this based on microscope geometry type
        // TODO: merge in the default settings as the startup load does, so a missing settings
        // block takes its default value instead of throwing a NullPointerException below
        var settings = ScapeAcquisitionSettings.fromJson(json, ScapeAcquisitionSettings.class);
        if (settings == null) {
            throw new IllegalArgumentException("the text holds no acquisition settings");
        }
        // useHardwareTimePoints is a DERIVED value. It is serialized as part of the acquisition settings,
        // but must never be trusted when reloaded: a stale "true" persisted from an earlier short-interval run
        // would otherwise seed acqSettings_ across a plugin restart and be read before the next run recomputes it.
        // Force it false on load; the engine recomputes it each run.
        settings = new ScapeAcquisitionSettings.Builder(settings).useHardwareTimePoints(false).build();
        model_.acquisitions().updateSettings(settings);
        if (notify) {
            notifyListeners(settings); // update the ui
        }
    }

    /**
     * Save user settings.
     */
    public void save() {
        // settings key based on geometry type
        final GeometryType geometryType = model_.devices().adapter().geometry();
        final String key = SETTINGS_PREFIX + geometryType.toString().toUpperCase();

        // Build from the builder, which holds what the user set in the user interface. The
        // acquisition settings can carry run scoped overrides, for example a test acquisition
        // turning saving off, and writing one of those to the profile would change the user's
        // settings for every later session. Building here also means saving no longer rebuilds
        // the settings a running acquisition is reading.
        final ScapeAcquisitionSettings settings = model_.acquisitions().settingsBuilder().build();

        // save acquisition settings
        settings_.putString(key, settings.toJson());
        model_.studio().logs().logDebugMessage("saved JSON to " + key + ": "
                + settings.toPrettyJson());

        // save plugin settings
        settings_.putString(SETTINGS_PLUGIN, model_.pluginSettings().toJson());
        model_.studio().logs().logDebugMessage("saved PluginSettings to " + SETTINGS_PLUGIN + ": "
                + model_.pluginSettings().toPrettyJson());
    }

    // TODO: this can add new keys but doesn't delete renamed keys
    /**
     * Returns the loaded settings, with any missing keys added using their default values.
     *
     * @param loadedSettings the settings loaded as a JSON String
     * @return the settings object, or empty if the text is not a JSON object
     */
    private Optional<JSONObject> validateUserSettings(final String loadedSettings) {
        // create default settings from builder
        final String defaultSettings = ScapeAcquisitionSettings.builder().build().toJson();
        // validate JSON strings
        JSONObject loadedJson;
        JSONObject defaultJson;
        try {
            loadedJson = new JSONObject(loadedSettings);
            defaultJson = new JSONObject(defaultSettings);
        } catch (JSONException e) {
            model_.studio().logs().showError("could not validate the JSON data: " + e.getMessage());
            return Optional.empty();
        }
        // add every default setting the loaded settings lack, including any whose key was renamed
        try {
            JsonUtils.addMissingKeys(defaultJson, loadedJson, MAX_RECURSION_DEPTH_JSON,
                    message -> model_.studio().logs().logMessage("UserSettings: " + message));
        } catch (JSONException e) {
            model_.studio().logs().showError("could not merge new default settings into loaded settings: "
                    + e.getMessage());
            return Optional.empty();
        }
        return Optional.of(loadedJson);
    }

    /**
     * Adds a listener that will update when the settings are loaded.
     *
     * @param listener listens to the settings object
     */
    public void addChangeListener(final SettingsListener listener) {
        listeners.add(listener);
    }

    /**
     * Updates all listeners on the Event Dispatch Thread (EDT).
     *
     * @param settings the acquisition settings
     */
    public void notifyListeners(final AcquisitionSettings settings) {
        // always update listeners on the EDT regardless of where we are called from
        if (SwingUtilities.isEventDispatchThread()) {
            for (SettingsListener listener : listeners) {
                listener.onSettingsChanged(settings);
            }
        } else {
            SwingUtilities.invokeLater(() -> {
                for (SettingsListener listener : listeners) {
                    listener.onSettingsChanged(settings);
                }
            });
        }
    }

}
