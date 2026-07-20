package org.micromanager.lightsheetmanager.model.devices.vendor;

import org.micromanager.Studio;
import org.micromanager.lightsheetmanager.model.devices.DeviceBase;

public abstract class ASITigerBase extends DeviceBase {

    public static class Properties {
        public static final String NAME = "Name";
        public static final String DESCRIPTION = "Description";
        public static final String TIGER_HEX_ADDRESS = "TigerHexAddress";

        public static final String FIRMWARE_BUILD = "FirmwareBuild";
        public static final String FIRMWARE_DATE = "FirmwareDate";
        public static final String FIRMWARE_VERSION = "FirmwareVersion";

        public static final String REFRESH_PROPERTY_VALUES = "RefreshPropertyValues";
    }

    public static class Values {
        public static final String NO = "No";
        public static final String YES = "Yes";
    }

    public ASITigerBase(final Studio studio) {
        super(studio);
    }

    public ASITigerBase(final Studio studio, final String deviceName) {
        super(studio, deviceName);
    }

    public String getName() {
        String result = "";
        try {
            result = core_.getProperty(deviceName_, Properties.NAME);
        } catch (Exception e) {
            studio_.logs().logError("could not get the name property");
        }
        return result;
    }

    public String getDescription() {
        String result = "";
        try {
            result = core_.getProperty(deviceName_, Properties.DESCRIPTION);
        } catch (Exception e) {
            studio_.logs().logError("could not get the description property");
        }
        return result;
    }

    public String getFirmwareBuild() {
        try {
            return core_.getProperty(deviceName_, Properties.FIRMWARE_BUILD);
        } catch (Exception e) {
            studio_.logs().logError("could not get the firmware build property");
        }
        return "";
    }

    public String getFirmwareDate() {
        try {
            return core_.getProperty(deviceName_, Properties.FIRMWARE_DATE);
        } catch (Exception e) {
            studio_.logs().logError("could not get the firmware date property");
        }
        return "";
    }

    public double getFirmwareVersion() {
        try {
            return Double.parseDouble(core_.getProperty(deviceName_, Properties.FIRMWARE_VERSION));
        } catch (Exception e) {
            studio_.logs().logError("could not get the firmware version property");
        }
        return 0.0;
    }

    public int getTigerHexAddress() {
        try {
            return Integer.parseInt(core_.getProperty(deviceName_, Properties.TIGER_HEX_ADDRESS));
        } catch (Exception e) {
            studio_.logs().logError("could not get the tiger hex address property");
        }
        return -1;
    }

    public void setRefreshPropertyValues(final boolean state) {
        try {
            core_.setProperty(deviceName_, Properties.REFRESH_PROPERTY_VALUES, state ? Values.YES : Values.NO);
        } catch (Exception e) {
            studio_.logs().logError("setRefreshPropertyValues failed.");
        }
    }

    public boolean isRefreshPropertyValuesOn() {
        try {
            return core_.getProperty(deviceName_, Properties.REFRESH_PROPERTY_VALUES).equals(Values.YES);
        } catch (Exception e) {
            studio_.logs().logError("getRefreshPropertyValues failed.");
        }
        return false;
    }

    public String getPropertyForceRefresh(final String propertyName) {
        final boolean wasOn = isRefreshPropertyValuesOn();
        if (!wasOn) {
            setRefreshPropertyValues(true);
        }
        try {
            return getProperty(propertyName);
        } finally {
            if (!wasOn) {
                setRefreshPropertyValues(false);  // always restore; do not get stuck on "Yes"
            }
        }
    }

}
