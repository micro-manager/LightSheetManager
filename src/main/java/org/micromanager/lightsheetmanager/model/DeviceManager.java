package org.micromanager.lightsheetmanager.model;

import mmcorej.Configuration;
import mmcorej.StrVector;
import org.micromanager.lightsheetmanager.LightSheetManager;
import org.micromanager.lightsheetmanager.api.data.CameraData;
import org.micromanager.lightsheetmanager.api.data.CameraLibrary;
import mmcorej.CMMCore;
import mmcorej.DeviceType;
import org.micromanager.Studio;
import org.micromanager.lightsheetmanager.gui.utils.DialogUtils;
import org.micromanager.lightsheetmanager.model.devices.DeviceBase;
import org.micromanager.lightsheetmanager.model.devices.Galvo;
import org.micromanager.lightsheetmanager.model.devices.DeviceAdapter;
import org.micromanager.lightsheetmanager.model.devices.NIDAQ;
import org.micromanager.lightsheetmanager.model.devices.Stage;
import org.micromanager.lightsheetmanager.model.devices.XYStage;
import org.micromanager.lightsheetmanager.model.devices.cameras.AndorCamera;
import org.micromanager.lightsheetmanager.model.devices.cameras.CameraBase;
import org.micromanager.lightsheetmanager.model.devices.cameras.DemoCamera;
import org.micromanager.lightsheetmanager.model.devices.cameras.HamamatsuCamera;
import org.micromanager.lightsheetmanager.model.devices.cameras.PcoCamera;
import org.micromanager.lightsheetmanager.model.devices.cameras.PvCamera;
import org.micromanager.lightsheetmanager.model.devices.vendor.ASIPLogic;
import org.micromanager.lightsheetmanager.model.devices.vendor.ASIPiezo;
import org.micromanager.lightsheetmanager.model.devices.vendor.ASIScanner;
import org.micromanager.lightsheetmanager.model.devices.vendor.ASIXYStage;
import org.micromanager.lightsheetmanager.model.devices.vendor.ASIZStage;

import javax.swing.JFrame;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A utility for extracting information from LightSheetDeviceManager.
 * <p>
 * This class maps device strings to device objects.
 */
public class DeviceManager {

    public static class DeviceException extends RuntimeException {
        public DeviceException(String message) {
            super(message);
        }
    }

    public static class DeviceNotFoundException extends DeviceException {
        public DeviceNotFoundException(String name) {
            super("Device '" + name + "' not found.");
        }
    }

    public static class DeviceTypeMismatchException extends DeviceException {
        public DeviceTypeMismatchException(String name, String actual, String expected) {
            super(String.format("Device '%s' is a %s, but you requested a %s.",
                    name, actual, expected));
        }
    }

    public static final String LSM_DEVICE_LIBRARY = "LightSheetManager";

    private final Studio studio_;
    private final CMMCore core_;

    /** Maps the Device Adapter device name "SampleXY, ImagingFocus, etc" to a DeviceBase object. */
    private final Map<String, DeviceBase> deviceMap_;

    private static String deviceAdapterName_;

    private final LightSheetManager model_;

    public DeviceManager(final Studio studio, final LightSheetManager model) {
        studio_ = Objects.requireNonNull(studio);
        model_ = Objects.requireNonNull(model);
        core_ = studio_.core();

        deviceAdapterName_ = ""; // set by hasDeviceAdapter
        deviceMap_ = new ConcurrentHashMap<>();
    }

    /**
     * Creates the device map from the device adapter properties.
     * <p>
     * Properties that are set to "Undefined" are ignored.
     */
    public void setup() {
        studio_.logs().logMessage("DeviceManager: [Begin Setup]");

        // always add an entry for the device adapter
        final DeviceAdapter lsm = new DeviceAdapter(studio_, deviceAdapterName_);
        deviceMap_.put("LightSheetDeviceManager", lsm);

        // keep track of devices we have already added to the map
        // used when multiple properties are mapped to the same device
        final HashMap<String, DeviceBase> devicesAdded = new HashMap<>();

        String[] props = lsm.getDevicePropertyNames();
        String[] properties = lsm.getEditableProperties(props);

        for (String propertyName : properties) {
            // skip properties that don't have a device assigned
            final String deviceName = lsm.getProperty(propertyName);
            if (deviceName.equals(DeviceAdapter.UNDEFINED)) {
                continue;
            }

            // skip properties with unknown DeviceType
            final DeviceType deviceType = getDeviceType(deviceName);
            if (deviceType == DeviceType.UnknownType) {
                continue;
            }

            // object was already created so grab a reference to it
            if (devicesAdded.containsKey(deviceName)) {
                deviceMap_.put(propertyName, devicesAdded.get(deviceName));
                final String className = devicesAdded.get(deviceName).getClass().getSimpleName();
                studio_.logs().logMessage("DeviceManager: " + propertyName + " set to "
                        + className + "(" + deviceName + ") (reused)");
                continue;
            }

            final String deviceLibrary = getDeviceLibrary(deviceName);

            // add device objects to the device map
            if (deviceType == DeviceType.XYStageDevice) {
                if (deviceLibrary.equals("ASITiger")) {
                    ASIXYStage xyStage = new ASIXYStage(studio_, deviceName);
                    addDevice(propertyName, deviceName, xyStage);
                    devicesAdded.put(deviceName, xyStage);
                } else {
                    // generic XY stage device
                    XYStage xyStage = new XYStage(studio_, deviceName);
                    addDevice(propertyName, deviceName, xyStage);
                    devicesAdded.put(deviceName, xyStage);
                }
            } else if (deviceType == DeviceType.StageDevice) {
                if (deviceLibrary.equals("ASITiger")) {
                    if (deviceName.contains("Piezo")) {
                        ASIPiezo piezo = new ASIPiezo(studio_, deviceName);
                        addDevice(propertyName, deviceName, piezo);
                        devicesAdded.put(deviceName, piezo);
                    }
                    if (deviceName.contains("ZStage")) {
                        ASIZStage zStage = new ASIZStage(studio_, deviceName);
                        addDevice(propertyName, deviceName, zStage);
                        devicesAdded.put(deviceName, zStage);
                    }
                } else {
                    // generic stage device
                    Stage stage = new Stage(studio_, deviceName);
                    addDevice(propertyName, deviceName, stage);
                    devicesAdded.put(deviceName, stage);
                }
            } else if (deviceType == DeviceType.GalvoDevice) {
                if (deviceLibrary.equals("ASITiger")) {
                    ASIScanner scanner = new ASIScanner(studio_, deviceName);
                    addDevice(propertyName, deviceName, scanner);
                    devicesAdded.put(deviceName, scanner);
                } else {
                    // use generic galvo device
                    Galvo galvo = new Galvo(studio_, deviceName);
                    addDevice(propertyName, deviceName, galvo);
                    devicesAdded.put(deviceName, galvo);
                }
            } else if (deviceType == DeviceType.ShutterDevice) {
                // Check if ASI PLogic or NIDAQ board is present
                if (deviceLibrary.equals("ASITiger")) {
                    ASIPLogic plc = new ASIPLogic(studio_, deviceName);
                    addDevice(propertyName, deviceName, plc);
                    devicesAdded.put(deviceName, plc);
                } else if (deviceLibrary.equals("NIDAQ")) {
                    NIDAQ nidaq = new NIDAQ(studio_, deviceName);
                    addDevice(propertyName, deviceName, nidaq);
                    devicesAdded.put(deviceName, nidaq);
                }
            } else if (deviceType == DeviceType.CameraDevice) {
                createCameraDevice(propertyName, deviceName,
                        CameraLibrary.fromString(deviceLibrary));
            }
            //deviceMap_.put(propertyName, "");
        }
        //System.out.println("----------------");

        // we don't need this array anymore
        //devicesAdded_.clear();

        studio_.logs().logMessage("DeviceManager: [End Setup]");
    }

    private void addDevice(final String propertyName, final String deviceName, final DeviceBase device) {
        deviceMap_.put(propertyName, device);
        studio_.logs().logMessage("DeviceManager: " + propertyName + " set to "
                + device.getClass().getSimpleName() + "(" + deviceName + ")");
    }

    private void createCameraDevice(final String propertyName, final String deviceName, CameraLibrary cameraLibrary) {
        switch (cameraLibrary) {
            case ANDORSDK3:
                AndorCamera andorCamera = new AndorCamera(studio_, deviceName);
                addDevice(propertyName, deviceName, andorCamera);
                break;
            case HAMAMATSU:
                HamamatsuCamera hamaCamera = new HamamatsuCamera(studio_, deviceName);
                addDevice(propertyName, deviceName, hamaCamera);
                break;
            case PCOCAMERA:
                PcoCamera pcoCamera = new PcoCamera(studio_, deviceName);
                addDevice(propertyName, deviceName, pcoCamera);
                break;
            case PVCAM:
                PvCamera pvCamera = new PvCamera(studio_, deviceName);
                addDevice(propertyName, deviceName, pvCamera);
                break;
            case DEMOCAMERA:
                DemoCamera demoCamera = new DemoCamera(studio_, deviceName);
                addDevice(propertyName, deviceName, demoCamera);
                break;
            default:
                CameraBase camera = new CameraBase(studio_, deviceName);
                addDevice(propertyName, deviceName, camera);
                studio_.logs().logError(
                        "Camera device library \"" + cameraLibrary + "\" not supported, using basic camera.");
                break;
        }
    }

    private void createShutterDevice() {

    }

    private void createXYStageDevice() {

    }

    // Note: clients should use var when we support Java 11
//    public DeviceBase getDevice(final String deviceName) {
//        return deviceMap_.get(deviceName);
//    }

    // TODO: consider using Optional to improve the API
    /**
     * Returns the device given by {@code deviceName} as type {@code T},
     * if the device is not found, {@code null} is returned.
     * The caller is responsible for assigning the returned
     * value to the correct type.
     * <P><P>
     * Typesafe: The client can only cast the return value to a subclass
     * of DeviceBase, avoiding the ClassCastException at compile time.
     *
     * @param deviceName the device name
     * @return the device or null if device not found
     * @param <T> the generic type to cast the result to
     */
    @SuppressWarnings("unchecked")
    public <T extends DeviceBase> T device(final String deviceName) {
        return (T) deviceMap_.get(deviceName);
    }

    public <T extends DeviceBase> Optional<T> device2(final String deviceName, final Class<T> type) {
        final DeviceBase device = deviceMap_.get(deviceName);
        // is the device in the map?
        if (device == null) {
            studio_.logs().logError("Device '" + deviceName + "' not found.");
            return Optional.empty();
        }
        // are both types the same?
        if (!type.isInstance(device)) {
            studio_.logs().logError(String.format(
                    "Device '%s' is a %s, but you requested a %s.",
                    deviceName, device.getClass().getSimpleName(), type.getSimpleName()));
            return Optional.empty();
        }
        // cast at runtime
        return Optional.of(type.cast(device));
    }

    public <T extends DeviceBase> T requiredDevice(final String name, final Class<T> type) {
        return device2(name, type)
                .orElseThrow(() -> new DeviceNotFoundException(name));
    }

    public CameraBase firstImagingCamera() {
        return (CameraBase) deviceMap_.get(firstActiveCameraName());
    }

    // For simultaneous cameras
    public String firstActiveCameraName() {
        final CameraData[] cameras = model_.acquisitions().settings().imagingCameraOrder();
        for (CameraData camera : cameras) {
            if (camera.isActive()) {
                return camera.name();
            }
        }
        return "";
    }

    public CameraBase imagingCamera(final int view, final int num) {
        final DeviceAdapter adapter = model_.devices().adapter();
        String cameraName = "Imaging";
        if (adapter.numImagingPaths() > 1) {
            cameraName += String.valueOf(view);
        }
        cameraName += "Camera";
        if (adapter.numSimultaneousCameras() > 1) {
            cameraName += String.valueOf(num);
        }
        return (CameraBase)deviceMap_.get(cameraName);
    }

    public String[] imagingCameraNames() {
        List<String> names = new ArrayList<>();
        final DeviceAdapter adapter = model_.devices().adapter();
        final int numImagingPaths = adapter.numImagingPaths();
        final int numCameras = adapter.numSimultaneousCameras();
        for (int i = 0; i < numImagingPaths; i++) {
            for (int j = 0; j < numCameras; j++) {
                String cameraName = "Imaging";
                if (numImagingPaths > 1) {
                    cameraName += String.valueOf(i + 1);
                }
                cameraName += "Camera";
                if (numCameras > 1) {
                    cameraName += String.valueOf(j + 1);
                }
                names.add(cameraName);
            }
        }
        return names.toArray(String[]::new);
    }

    public CameraBase[] imagingCameras() {
        final List<String> names = new ArrayList<>();
        final CameraData[] cameras = model_.acquisitions().settings().imagingCameraOrder();
        for (CameraData camera : cameras) {
            if (camera.isActive()) {
                names.add(camera.name());
            }
        }
        final String[] cameraNames = names.toArray(String[]::new);
        return Arrays.stream(cameraNames)
                .map(name -> (CameraBase)deviceMap_.get(name))
                .filter(Objects::nonNull)
                .toArray(CameraBase[]::new);
    }

    /**
     * Returns {@code true} if all cameras in the settings are valid.
     * This is used to detect changes in pre-init properties.
     *
     * @return {@code true} if the cameras are valid
     */
    public boolean validateCameras() {
        // cameras in settings
        final CameraData[] cameras = model_.acquisitions().settings().imagingCameraOrder();

        // check for no cameras
        if (cameras.length == 0) {
            final String message = "No cameras found in the settings.";
            model_.studio().logs().logError(message);
            if (DialogUtils.showYesNoDialog(null, "No Imaging Cameras",
                    message + "\nWould you like to use the default imaging camera order?")) {
                useDefaultImagingCameraOrder();
                return true;
            } else {
                model_.setupErrorMessage(message);
                return false;
            }
        }

        // valid camera names
        final List<String> validNames = Arrays.asList(imagingCameraNames());

        // check for camera name mismatches
        for (CameraData camera : cameras) {
            if (!validNames.contains(camera.name())) {
                final String message = "Camera in settings not found in hardware: " + camera.name()
                        + ", consider creating a new user profile if the pre-init properties changed.";
                model_.studio().logs().logError(message);
                model_.setupErrorMessage(message);
                return false;
            }
        }

        return true;
    }

    /**
     * Used to create the default imaging camera order.
     */
    public void useDefaultImagingCameraOrder() {
        final List<CameraData> cameras = new ArrayList<>();
        final String[] cameraNames = imagingCameraNames();
        for (int i = 0; i < cameraNames.length; i++) {
            // the first camera is always the default primary camera, so make sure it's active
            cameras.add(new CameraData(cameraNames[i], i == 0));
        }
        // update settings with default camera order
        model_.acquisitions().settingsBuilder()
                .imagingCameraOrder(cameras.toArray(new CameraData[0]));
        model_.acquisitions().updateSettings();
    }

    public DeviceAdapter adapter() {
        return (DeviceAdapter)deviceMap_.get("LightSheetDeviceManager");
    }

    public String getDeviceLibrary(final String deviceName) {
        String result = "";
        try {
            result = core_.getDeviceLibrary(deviceName);
        } catch (Exception e) {
            studio_.logs().logError(e.getMessage());
        }
        return result;
    }

    private DeviceType getDeviceType(final String deviceName) {
        try {
            return core_.getDeviceType(deviceName);
        } catch (Exception e) {
            return DeviceType.UnknownType;
        }
    }

    public String[] getLoadedDevices() {
        StrVector loadedDevices = new StrVector();
        try {
            loadedDevices = core_.getLoadedDevices();
        } catch (Exception e) {
            studio_.logs().logError(e.getMessage());
        }
        return loadedDevices.toArray();
    }

    /**
     * Returns true if the hardware configuration has the LightSheetManager device adapter. The user can
     * change the device name of the adapter, but not the device library so that's what we detect. Also,
     * the name of the device adapter is cached for later usage. This also set the error text on the model
     * when an error is encountered, this is used in the error user interface.
     *
     * @return true if the hardware configuration has the device adapter
     */
    public boolean hasDeviceAdapter() {
        int count = 0;
        final String[] devices = getLoadedDevices();
        for (String device : devices) {
            try {
                final String deviceLibrary = core_.getDeviceLibrary(device);
                if (deviceLibrary.equals(LSM_DEVICE_LIBRARY)) {
                    deviceAdapterName_ = device;
                    count++;
                    if (count > 1) {
                        model_.setupErrorMessage("You have multiple instances of the LightSheetManager " +
                                "device adapter in your hardware configuration.");
                        break; // exit loop because this a failure condition
                    }
                }
            } catch (Exception e) {
                studio_.logs().logError("could not get the device " +
                        "library for the device \"" + device + "\".");
            }
        }
        // no device adapters found
        if (count == 0) {
            model_.setupErrorMessage("Please add the LightSheetManager device adapter to your " +
                    "hardware configuration to use this plugin.");
        }
        return count == 1;
    }

    // check for ASI hardware triggering device
    public boolean isUsingPLogic() {
        if (deviceMap_.get("TriggerLaser") == null && deviceMap_.get("TriggerCamera") == null) {
            return false; // early exit => devices not set
        }
        // check if both device names contain "PLogic"
        boolean result = false;
        final boolean isLaserPLogic = deviceMap_.get("TriggerLaser").getDeviceName().contains("PLogic");
        final boolean isCameraPLogic = deviceMap_.get("TriggerCamera").getDeviceName().contains("PLogic");
        if (isLaserPLogic && !isCameraPLogic || !isLaserPLogic && isCameraPLogic) {
            studio_.logs().showError("PLogic must be set as both the camera and laser trigger.");
        }
        if (isLaserPLogic && isCameraPLogic) {
            result = true;
        }
        return result;
    }

    // check for ASI stage scanning
    public boolean hasStageScanning() {
        if (deviceMap_.get("SampleXY") == null) {
            return false; // early exit => device not set
        }
        return deviceMap_.get("SampleXY")
                .hasProperty(ASIXYStage.Properties.SCAN_NUM_LINES);
    }

    /**
     * Creates a configuration group named "System" that includes all device properties
     * the Light Sheet Manager device adapter.
     */
    public void createConfigGroup() {
        final String groupName = "System";
        final String configName = "Startup";

        // create group
        if (!core_.isGroupDefined(groupName)) {
            try {
                core_.defineConfigGroup(groupName);
            } catch (Exception e) {
                studio_.logs().logError("could not create the \"" + groupName + "\" configuration group.");
                return; // early exit
            }
            // create config
            if (!core_.isConfigDefined(groupName, configName)) {
                try {
                    core_.defineConfig(groupName, configName);
                } catch (Exception e) {
                    studio_.logs().logError("could not create the \"" + configName + "\" configuration preset.");
                    return; // early exit
                }
            }
        }

        ArrayList<String> updatedProperties = updateConfig(groupName, configName);

        if (updatedProperties.isEmpty()) {
            studio_.logs().showMessage("All device adapter properties are present in the "
                    + groupName + "::" + configName + " configuration group.");
        } else {
            StringBuilder sb = new StringBuilder();
            for (String property : updatedProperties) {
                sb.append(property).append("\n");
            }
            studio_.logs().showMessage("Added properties to the "
                    + groupName + "::" + configName + " configuration group: \n" + sb);
        }
    }


    /**
     * Returns a list of new properties added to the configuration group.
     *
     * @param groupName the group name to check
     * @param configName the configuration group to check
     * @return an ArrayList of new properties
     */
    private ArrayList<String> updateConfig(final String groupName, final String configName) {
        ArrayList<String> newProperties = new ArrayList<>();
        final String[] props = adapter().getDevicePropertyNames();
        final String[] properties = adapter().getEditableProperties(props);

        Configuration config;
        try {
            config = core_.getConfigData(groupName, configName);
        } catch (Exception e) {
            studio_.logs().showError("could not get configuration data!");
            return newProperties; // early exit => could not get config data to compare
        }

        for (String propertyName : properties) {
            if (!config.isPropertyIncluded(deviceAdapterName_, propertyName)) {
                try {
                    core_.defineConfig(groupName, configName,
                            deviceAdapterName_, propertyName, DeviceAdapter.UNDEFINED);
                    newProperties.add(propertyName);
                } catch (Exception e) {
                    studio_.logs().logError("Could not create the \"" + propertyName
                            + "\" property for the \"" + groupName + "\" configuration group.");
                }
            }
        }

        // update MM ui
        if (!newProperties.isEmpty()) {
            studio_.getApplication().refreshGUI();
        }
        return newProperties;
    }

    // TODO: adapt for diSPIM and multiple cameras
    /**
     * Check user settings and ask to change settings with dialogs.
     */
    public void checkDevices(final JFrame frame) {

        final String cameraKey = "ImagingCamera";
        CameraBase cameraDevice = device(cameraKey);
        CameraLibrary cameraLib = CameraLibrary.UNKNOWN;
        if (cameraDevice != null) {
            cameraLib = CameraLibrary.fromString(cameraDevice.getDeviceLibrary());
        }

        switch (cameraLib) {
            case HAMAMATSU:
                // Flash4, Fusion, etc
                HamamatsuCamera camera = device(cameraKey);
                if (camera.getTriggerPolarity().equals(HamamatsuCamera.Values.NEGATIVE)) {
                    final boolean result = DialogUtils.showYesNoDialog(frame, "Hamamatsu Camera",
                            "The trigger polarity should be set to POSITIVE. Set it now?");
                    if (result) {
                        camera.setTriggerPolarity(HamamatsuCamera.Values.POSITIVE);
                    }
                }
                break;
            case PVCAM:
                // Kinetix, etc
                break;
            default:
                break;
        }
    }

    /**
     * Return true if the device exists in the device map.
     *
     * @param deviceName the name of the device in the device adapter
     * @return true if the device is present
     */
    public boolean hasDevice(final String deviceName) {
        return !deviceMap_.get(deviceName).getDeviceName().equals("Undefined");
    }

    /**
     * Halt all XYStageDevice and StageDevice devices.
     */
    public void haltDevices() {
        for (DeviceBase device : deviceMap_.values()) {
            final DeviceType deviceType = device.getDeviceType();
            if (deviceType == DeviceType.XYStageDevice || deviceType == DeviceType.StageDevice) {
                device.halt();
            }
        }
    }

}
