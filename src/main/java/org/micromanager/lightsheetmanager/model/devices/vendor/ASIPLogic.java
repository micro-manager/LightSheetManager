package org.micromanager.lightsheetmanager.model.devices.vendor;

import org.micromanager.Studio;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The ASI Programmable Logic Card is a field-programmable card for digital logic.
 *
 * <p>In LightSheetManager the PLogic card is used to coordinate hardware triggering.
 *
 * <p>Documentation:
 *      <a href="http://asiimaging.com/docs/tiger_programmable_logic_card">ASI PLogic</a>
 */
public class ASIPLogic extends ASITigerBase {

    public static final int addrZero = 0;
    public static final int addrEdge = 128;
    public static final int addrInvert = 64;

    // front panel BNC 1-8
    public static final int addrBNC1 = 33;
    public static final int addrBNC2 = 34;
    public static final int addrBNC3 = 35;
    public static final int addrBNC4 = 36;
    public static final int addrBNC5 = 37;
    public static final int addrBNC6 = 38;
    public static final int addrBNC7 = 39;
    public static final int addrBNC8 = 40;

    // backplane TTL 0-7
    public static final int addrBackplaneTTL0 = 41;
    public static final int addrBackplaneTTL1 = 42;
    public static final int addrBackplaneTTL2 = 43;
    public static final int addrBackplaneTTL3 = 44;
    public static final int addrBackplaneTTL4 = 45;
    public static final int addrBackplaneTTL5 = 46;
    public static final int addrBackplaneTTL6 = 47;
    public static final int addrBackplaneTTL7 = 48;

    public ASIPLogic(final Studio studio, final String deviceName) {
        super(studio, deviceName);
    }

    public int getNumCells() {
        return getPropertyInt(Properties.ReadOnly.NUM_LOGIC_CELLS);
    }

    /**
     * Returns true if the card has preset 60, the mod3 counter that can be reset, which came with
     * firmware 3.51. A firmware version that cannot be read counts as older.
     *
     * @return true if preset 60 is available
     */
    public boolean hasPreset60() {
        return getFirmwareVersion() > 3.501;
    }

    public String getAxisLetter() {
        return getProperty(Properties.ReadOnly.AXIS_LETTER);
    }

    public int getBackplaneOutputState() {
        return getPropertyInt(Properties.ReadOnly.BACKPLANE_OUTPUT_STATE);
    }

    public int getFrontPanelOutputState() {
        return getPropertyInt(Properties.ReadOnly.FRONTPANEL_OUTPUT_STATE);
    }

    public int getPLogicOutputState() {
        return getPropertyInt(Properties.ReadOnly.PLOGIC_OUTPUT_STATE);
    }

    public void clearAllCellStates() {
        setProperty(Properties.CLEAR_ALL_CELL_STATES, Values.DO_IT);
    }

    public void setTriggerSource(final String source) {
        setProperty(Properties.TRIGGER_SOURCE, source);
    }

    public TriggerSource getTriggerSource() {
        return TriggerSource.fromString(getProperty(Properties.TRIGGER_SOURCE));
    }

    public void setPointerPosition(final int position) {
        setPropertyInt(Properties.POINTER_POSITION, position);
    }

    public int getPointerPosition() {
        return getPropertyInt(Properties.POINTER_POSITION);
    }

    public void setCellType(final CellType cellType) {
        setProperty(Properties.EDIT_CELL_TYPE, cellType.toString());
    }

    public CellType getCellType() {
        return CellType.fromString(getProperty(Properties.EDIT_CELL_TYPE));
    }

    public void setCellConfig(final int value) {
        setPropertyInt(Properties.EDIT_CELL_CONFIG, value);
    }

    public int getCellConfig() {
        return getPropertyInt(Properties.EDIT_CELL_CONFIG);
    }

    public void setAutoUpdateCells(final boolean state) {
        setProperty(Properties.EDIT_CELL_UPDATE_AUTO, state ? Values.YES : Values.NO);
    }

    public boolean isAutoUpdateCellsOn() {
        return getProperty(Properties.EDIT_CELL_UPDATE_AUTO).equals(Values.YES);
    }

    public void setPreset(final int code) {
        setProperty(Properties.SET_CARD_PRESET, Preset.fromCode(code));
    }

    public void setPreset(final Preset preset) {
        setProperty(Properties.SET_CARD_PRESET, preset.toString());
    }

    public Preset getPreset() {
        return Preset.fromString(getProperty(Properties.SET_CARD_PRESET));
    }

    public int getPresetCode() {
        return Preset.fromString(getProperty(Properties.SET_CARD_PRESET)).toCode();
    }

    public void setCellInput(final int input, final int value) {
        if (input < 1 || input > 4) {
            throw new IllegalArgumentException("Each cell only has inputs 1-4.");
        }
        setPropertyInt(Properties.EDIT_CELL_INPUT + input, value);
    }

    public int getCellInput(final int input) {
        if (input < 1 || input > 4) {
            throw new IllegalArgumentException("Each cell only has inputs 1-4.");
        }
        return getPropertyInt(Properties.EDIT_CELL_INPUT + input);
    }

    public ShutterMode getShutterMode() {
        return ShutterMode.fromString(getProperty(Properties.PLOGIC_MODE));
    }


    // Device Properties
    public static class Properties {

        public static final String CLEAR_ALL_CELL_STATES = "ClearAllCellStates";
        public static final String EDIT_CELL_UPDATE_AUTO = "EditCellUpdateAutomatically";
        public static final String POINTER_POSITION = "PointerPosition";
        public static final String TRIGGER_SOURCE = "TriggerSource";
        public static final String SET_CARD_PRESET = "SetCardPreset";

        public static final String EDIT_CELL_TYPE = "EditCellCellType";
        public static final String EDIT_CELL_CONFIG = "EditCellConfig";
        public static final String EDIT_CELL_INPUT = "EditCellInput"; // EditCellInput1 - EditCellInput4

        public static final String PLOGIC_MODE = "PLogicMode";

        public static class ReadOnly {
            public static final String BACKPLANE_OUTPUT_STATE = "BackplaneOutputState";
            public static final String FRONTPANEL_OUTPUT_STATE = "FrontpanelOutputState";
            public static final String PLOGIC_OUTPUT_STATE = "PLogicOutputState";
            public static final String NUM_LOGIC_CELLS = "NumLogicCells";
            public static final String AXIS_LETTER = "AxisLetter";
        }
    }

    public static class Values {

        public static final String NO = "No";
        public static final String YES = "Yes";

        public static final String DO_IT = "Do it";
    }

    public enum Preset {
        NO_PRESET("no preset", -1), // TODO: is code correct? does this make sense?
        ALL_CELLS_ZERO("0 - cells all 0", 0),
        ORIGINAL_SPIM_TTL_CARD("1 - original SPIM TTL card", 1),
        CELL_1_LOW("2 - cell 1 low", 2),
        CELL_1_HIGH("3 - cell 1 high", 3),
        COUNTER_16BIT("4 - 16 bit counter", 4),
        BNC5_ENABLED("5 - BNC5 enabled", 5),
        BNC6_ENABLED("6 - BNC6 enabled", 6),
        BNC7_ENABLED("7 - BNC7 enabled", 7),
        BNC8_ENABLED("8 - BNC8 enabled", 8),
        BNC5_TO_BNC8_DISABLED("9 - BNC5-8 all disabled", 9),
        CELL_8_LOW("10 - cell 8 low", 10),
        CELL_8_HIGH("11 - cell 8 high", 11),
        PRESET_12("12 - cell 10 = (TTL1 AND cell 8)", 12),
        PRESET_13("13 - BNC4 source = (TTL3 AND (cell 10 OR cell 1))", 13),
        DISPIM_TLL("14 - diSPIM TTL", 14),
        MOD4_COUNTER("15 - mod4 counter", 15),
        MOD3_COUNTER("16 - mod3 counter", 16),
        COUNTER_CLOCK_FALLING_TTL1("17 - counter clock = falling TTL1", 17),
        COUNTER_CLOCK_FALLING_TTL3("18 - counter clock = falling TTL3", 18),
        BNC1_8_ON_9_16("19 - cells 9-16 on BNC1-8", 19),
        BNC5_8_ON_13_16("20 - cells 13-16 on BNC5-8", 20), // CELLS_13_TO_16_ON_BNC5_TO_BNC8
        MOD2_COUNTER("21 - mod2 counter", 21),
        NO_COUNTER("22 - no counter", 22),
        TTL0_7_ON_BNC1_8("23 - TTL0-7 on BNC1-8", 23),
        BNC3_SOURCE_CELL_1("24 - BNC3 source = cell 1", 24),
        BNC3_SOURCE_CELL_8("25 - BNC3 source = cell 8", 25),
        COUNTER_CLOCK_RISING_TTL3("26 - counter clock = rising TTL3", 26),
        BNC3_SOURCE_EQ_CELL_10("27 - BNC3 source = cell 10", 27),
        BNC6_AND_BNC7_ENABLED("28 - BNC6 and BNC7 enabled", 28),
        BNC5_TO_BNC7_ENABLED("29 - BNC5-BNC7 enabled", 29),
        BNC5_TO_BNC8_ENABLED("30 - BNC5-BNC8 enabled", 30),
        BNC5_7_SIDEA_BNC6_8_SIDEB("31 - BNC5/7 side A, BNC6/8 side B", 31),
        BNC1_2_AS_CAMERA_A_AND_B("32 - BNC1/2 as cameras A/B", 32),
        BNC1_2_AS_CAMERA_A_OR_B("33 - BNC1/2 as cameras A or B", 33),
        CELL_11_TRIGGER_DIV_2("34 - cell 11 as trigger/2", 34),
        BNC3_SOURCE_CELL_11("35 - BNC3 source = cell 11", 35),
        CELL_10_EQ_CELL_8("36 - cell 10 = cell 8", 36),
        BNC1_8_ON_17_24("51 - cells 17-24 on BNC1-8", 51),
        BNC3_SOURCE_TTL5("52 - BNC3 source = TTL5", 52),
        MOD3_COUNTER_RESETTABLE("60 - mod3 counter", 60);

        private final String text_;
        private final int code_;

        private static final Map<String, Preset> stringToEnum =
                Stream.of(values()).collect(Collectors.toMap(Object::toString, e -> e));

        private static final Map<Integer, String> presets =
                Stream.of(values()).collect(Collectors.toMap(Preset::getPresetCode, Object::toString));

        Preset(final String text, final int code) {
            text_ = text;
            code_ = code;
        }

        @Override
        public String toString() {
            return text_;
        }

        public int toCode() {
            return code_;
        }

        public int getPresetCode() {
            return code_;
        }

        // TODO: handle errors without getOrDefault?
        public static Preset fromString(final String symbol) {
            return stringToEnum.getOrDefault(symbol, Preset.NO_PRESET);
        }

        public static String fromCode(final int code) {
            return presets.getOrDefault(code, "NONE");
        }
    }

    public enum CellType {
        CONSTANT("0 - constant"),
        INPUT("0 - input"),
        D_FLOP("1 - D flop"),
        OUTPUT_OPEN_DRAIN("1 - output (open-drain)"),
        LUT2("2 - 2-input LUT"),
        OUTPUT_PUSH_PULL("2 - output (push-pull)"),
        LUT3("3 - 3-input LUT"),
        LUT4("4 - 4-input LUT"),
        AND2("5 - 2-input AND"),
        OR2("6 - 2-input OR"),
        XOR2("7 - 2-input XOR"),
        ONE_SHOT("8 - one shot"),
        DELAY("9 - delay"),
        AND4("10 - 4-input AND"),
        OR4("11 - 4-input OR"),
        D_FLOP_SYNC("12 - D flop (sync)"),
        JK_FLOP("13 - JK flop"),
        ONE_SHOT_NRT("14 - one shot (NRT)"),
        DELAY_NRT("15 - delay (NRT)");

        private final String text_;

        private static final Map<String, CellType> stringToEnum =
                Stream.of(values()).collect(Collectors.toMap(Object::toString, e -> e));

        CellType(final String text) {
            text_ = text;
        }

        @Override
        public String toString() {
            return text_;
        }

        public static CellType fromString(final String symbol) {
            return stringToEnum.getOrDefault(symbol, CellType.CONSTANT);
        }
    }

    public enum TriggerSource {
        INTERNAL("0 - internal 4kHz"),
        MICRO_MIRROR_CARD("1 - Micro-mirror card"),
        BACKPLANE_TTL5("2 - backplane TTL5"),
        BACKPLANE_TTL7("3 - backplane TTL7"),
        FRONTPANEL_BNC1("4 - frontpanel BNC 1");

        private final String text_;

        private static final Map<String, TriggerSource> stringToEnum =
                Stream.of(values()).collect(Collectors.toMap(Object::toString, e -> e));

        TriggerSource(final String text) {
            text_ = text;
        }

        @Override
        public String toString() {
            return text_;
        }

        public static TriggerSource fromString(final String symbol) {
            return stringToEnum.getOrDefault(symbol, TriggerSource.INTERNAL);
        }
    }

    // pre-init property
    public enum ShutterMode {
        NONE("None"),
        FOUR_CHANNEL_SHUTTER("Four-channel shutter"),
        DISPIM_SHUTTER("diSPIM Shutter"),
        SEVEN_CHANNEL_SHUTTER("Seven-channel shutter"),
        SEVEN_CHANNEL_TTL_SHUTTER("Seven-channel TTL shutter");

        private final String text_;

        private static final Map<String, ShutterMode> stringToEnum =
                Stream.of(values()).collect(Collectors.toMap(Object::toString, e -> e));

        ShutterMode(final String text) {
            text_ = text;
        }

        @Override
        public String toString() {
            return text_;
        }

        public static ShutterMode fromString(final String symbol) {
            return stringToEnum.getOrDefault(symbol, ShutterMode.NONE);
        }
    }

}
