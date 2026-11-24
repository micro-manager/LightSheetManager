package org.micromanager.lightsheetmanager.api.internal;

import org.micromanager.lightsheetmanager.api.ChannelSettings;
import org.micromanager.lightsheetmanager.api.data.ChannelMode;
import org.micromanager.lightsheetmanager.model.channels.ChannelSpec;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class DefaultChannelSettings implements ChannelSettings {

    private final boolean enabled;
    private final ChannelMode mode;
    private final String group;
    private final HashMap<String, ChannelSpec[]> groups;

    // default value for when the channel group key is not found
    private static final ChannelSpec[] EMPTY_CHANNELS = new ChannelSpec[0];

    private DefaultChannelSettings(Builder builder) {
        enabled = builder.enabled;
        mode = builder.mode;
        group = builder.group;
        groups = builder.groups;
    }

    // Note: used by GSON library for deserialization
    private DefaultChannelSettings() {
        this(new Builder());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(ChannelSettings settings) {
        Objects.requireNonNull(settings, "Cannot copy from null settings");
        return new Builder(settings);
    }

    @Override
    public Builder copyBuilder() {
        return new Builder(this);
    }

    /**
     * Return true if channels are enabled.
     *
     * @return true if channels are enabled
     */
    @Override
    public boolean enabled() {
        return enabled;
    }

    /**
     * Returns the number of channels a run acquires: the used channels in the channel group
     * when channels are enabled, otherwise 1.
     *
     * @return the number of channels a run acquires
     */
    @Override
    public int count() {
        if (enabled) {
            return used().length;
        } else {
            return 1;
        }
    }

    /**
     * Returns the number of channel groups.
     *
     * @return the number of channel groups
     */
    @Override
    public int numGroups() {
        return groups.size();
    }

    /**
     * Returns the channel group.
     *
     * @return the channel group
     */
    @Override
    public String group() {
        return group;
    }

    /**
     * Returns the channel mode.
     *
     * @return the channel mode
     */
    @Override
    public ChannelMode mode() {
        return mode;
    }

    /**
     * Returns an array of all channel group names.
     *
     * @return an array of channel group names
     */
    @Override
    public String[] groupNames() {
        return groups.keySet().toArray(String[]::new);
    }

    /**
     * Returns the used channels in the channel group.
     *
     * @return the used channels in the channel group
     */
    @Override
    public ChannelSpec[] used() {
        return Arrays.stream(groups.getOrDefault(group, EMPTY_CHANNELS))
                .filter(ChannelSpec::useChannel)
                .toArray(ChannelSpec[]::new);
    }

    /**
     * Returns all channels for the selected channel group.
     *
     * @return all channels for the selected channel group
     */
    @Override
    public ChannelSpec[] data() {
        return groups.getOrDefault(group, EMPTY_CHANNELS);
    }

    @Override
    public Map<String, ChannelSpec[]> groups() {
        return Collections.unmodifiableMap(groups);
    }

    // TODO: add groups_ to toString, equals, and hashCode methods

    @Override
    public String toString() {
        return String.format("%s[enabled=%s, group=%s, mode=%s]",
                getClass().getSimpleName(), enabled, group, mode);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        DefaultChannelSettings other = (DefaultChannelSettings) obj;
        return enabled == other.enabled
                && Objects.equals(group, other.group)
                && mode == other.mode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, group, mode);
    }

    public static class Builder implements ChannelSettings.Builder {

        private boolean enabled = false;
        private ChannelMode mode = ChannelMode.VOLUME;
        private String group = "";
        private HashMap<String, ChannelSpec[]> groups = new HashMap<>();

        private Builder() {
        }

        public Builder(final ChannelSettings settings) {
            enabled = settings.enabled();
            mode = settings.mode();
            group = settings.group();
            groups = new HashMap<>();
            // deep copy
            settings.groups().forEach((name, channels) -> {
                ChannelSpec[] array = new ChannelSpec[channels.length];
                for (int i = 0; i < channels.length; i++) {
                    array[i] = new ChannelSpec(channels[i]);
                }
                groups.put(name, array);
            });
        }

        @Override
        public Builder enabled(final boolean state) {
            enabled = state;
            return this;
        }

        @Override
        public Builder group(final String group) {
            this.group = group;
            return this;
        }

        @Override
        public Builder mode(final ChannelMode mode) {
            this.mode = mode;
            return this;
        }

        @Override
        public Builder data(final ChannelSpec[] channels) {
            groups.put(group, channels);
            return this;
        }

        @Override
        public DefaultChannelSettings build() {
            return new DefaultChannelSettings(this);
        }

    }

}
