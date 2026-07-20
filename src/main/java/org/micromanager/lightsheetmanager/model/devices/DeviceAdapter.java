package org.micromanager.lightsheetmanager.model.devices;

import mmcorej.DeviceType;
import org.micromanager.Studio;
import org.micromanager.lightsheetmanager.LightSheetManagerPlugin;
import org.micromanager.lightsheetmanager.api.data.GeometryType;
import org.micromanager.lightsheetmanager.api.data.LightSheetType;
import org.micromanager.lightsheetmanager.model.utils.MathUtils;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * The device adapter "LightSheetManager".
 * <p>
 * Contained in the DeviceManager object as "LightSheetDeviceManager".
 */
public class DeviceAdapter extends DeviceBase {

    // used to indicate that the device is not set to any hardware
    public static final String UNDEFINED = "Undefined";

    // TODO: use this for validation
    private String version_;

    // pre-init properties
    private GeometryType geometryType_;
    private LightSheetType lightSheetType_;
    private int numImagingPaths_;
    private int numIlluminationPaths_;
    private int numSimultaneousCameras_;

    public DeviceAdapter(final Studio studio, final String deviceName) {
        super(studio, deviceName);
        loadPreInitProperties();
    }

    /**
     * Queries the pre-init properties from the device adapter and caches them.
     */
    private void loadPreInitProperties() {
        version_ = getProperty("Version");

        // if GeometryType.UNKNOWN then we open the plugin error screen
        geometryType_ = GeometryType.fromString(getProperty("MicroscopeGeometry"));
        studio_.logs().logMessage("LSM-SESSION plugin-v" + LightSheetManagerPlugin.version
                + " adapter-v" + version_ + " " + geometryType_);
        if (geometryType_ == GeometryType.UNKNOWN) {
            studio_.logs().logError("LightSheetDeviceManager: Failed to identify microscope geometry! "
                    + "Device adapter returned: " + getProperty("MicroscopeGeometry"));
        }

        // default to LightSheetType.STATIC on parsing error
        lightSheetType_ = LightSheetType.fromString(getProperty("LightSheetType"));

        // change defaults based on microscope geometry
        int defaultImaging = 1;
        int defaultIllumination = 1;
        if (geometryType_ == GeometryType.DISPIM) {
            defaultImaging = 2;
            defaultIllumination = 2;
        }

        // use the defaults if there is a parsing error
        final int numImaging = parsePropertyInt("ImagingPaths", defaultImaging);
        final int numIllum = parsePropertyInt("IlluminationPaths", defaultIllumination);
        final int numCameras = parsePropertyInt("SimultaneousCameras", 1);

        // constrain the values to the range of the property limits
        numImagingPaths_ = MathUtils.clamp(numImaging,
                (int) getPropertyLowerLimit("ImagingPaths"),
                (int) getPropertyUpperLimit("ImagingPaths"));
        numIlluminationPaths_ = MathUtils.clamp(numIllum,
                (int) getPropertyLowerLimit("IlluminationPaths"),
                (int) getPropertyUpperLimit("IlluminationPaths"));
        numSimultaneousCameras_ = MathUtils.clamp(numCameras,
                (int) getPropertyLowerLimit("SimultaneousCameras"),
                (int) getPropertyUpperLimit("SimultaneousCameras"));
    }

    /**
     * Safely parses a device property to an integer, returns the default value on failure.
     */
    private int parsePropertyInt(final String propertyName, final int defaultValue) {
        try {
            return Integer.parseInt(getProperty(propertyName));
        } catch (NumberFormatException e) {
            studio_.logs().logError("Error parsing " + propertyName
                    + " from the device adapter, use default value " + defaultValue);
            return defaultValue;
        }
    }

    private boolean isPositionDevice(final String deviceName) {
        final DeviceType deviceType = deviceType(deviceName);
        return deviceType == DeviceType.StageDevice
                || deviceType == DeviceType.XYStageDevice
                || deviceType == DeviceType.GalvoDevice;
    }

    private DeviceType deviceType(final String deviceName) {
        try {
            return core_.getDeviceType(deviceName);
        } catch (Exception e) {
            return DeviceType.UnknownType;
        }
    }

    public String version() {
        return version_;
    }

    public GeometryType geometry() {
        return geometryType_;
    }

    public LightSheetType lightSheetType() {
        return lightSheetType_;
    }

    public int numImagingPaths() {
        return numImagingPaths_;
    }

    public int numIlluminationPaths() {
        return numIlluminationPaths_;
    }

    public int numSimultaneousCameras() {
        return numSimultaneousCameras_;
    }

    /**
     * Returns a map of internal device names to hardware device names.
     *
     * @return a map of internal device names to hardware device names
     */
    public Map<String, String> deviceMap() {
        final String[] properties = getDevicePropertyNames();
        return Arrays.stream(properties)
                .filter(p -> !isPropertyPreInit(p) && !isPropertyReadOnly(p))
                .map(p -> Map.entry(p, getProperty(p)))
                .filter(e -> !e.getValue().equals(UNDEFINED))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Returns a map of internal device names to device types.
     *
     * @return a map of internal device names to device types
     */
    public Map<String, DeviceType> deviceTypeMap() {
        return deviceMap().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> deviceType(e.getValue())));
    }

    /**
     * Returns an array of internal device names for all position devices.
     * <p>
     * A "Position Device" is a StageDevice, XYStageDevice, or GalvoDevice.
     *
     * @return an array of internal devices names for all position devices
     */
    public String[] positionDevices() {
        return deviceMap().entrySet().stream()
                .filter(entry -> isPositionDevice(entry.getValue()))
                .map(Map.Entry::getKey)
                .toArray(String[]::new);
    }

    /**
     * Return {@code true} if the device adapter has the deviceName property
     * set to a value other than the default: "Undefined".
     * <p>
     * Example deviceName properties: "SampleXY", "ImagingFocus".
     *
     * @param deviceName the name of the device in the device adapter
     * @return {@code true} if the device is not "Undefined".
     */
    public boolean hasDevice(final String deviceName) {
        return !getProperty(deviceName).equals(UNDEFINED);
    }

}
