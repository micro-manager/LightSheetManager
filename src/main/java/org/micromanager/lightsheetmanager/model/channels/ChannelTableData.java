package org.micromanager.lightsheetmanager.model.channels;

import java.util.ArrayList;

/**
 * The data model's internal representation of the channel table.
 */
public class ChannelTableData {

    private String channelGroup_;
    private final ArrayList<ChannelSpec> channels_;

    public ChannelTableData() {
        channels_ = new ArrayList<>();
        channelGroup_ = "";
    }

    public ChannelTableData(final String channelGroup, final ChannelSpec[] allChannels) {
        channels_ = new ArrayList<>();
        setChannels(channelGroup, allChannels);
    }

    public ChannelSpec getChannelByIndex(final int index) {
        return channels_.get(index);
    }

    public void setChannels(final String channelGroup, final ChannelSpec[] allChannels) {
       channels_.clear();
       channelGroup_ = channelGroup;
       // only add channel data in the channel group
       for (ChannelSpec channel : allChannels) {
          if (channel.getGroup().equals(channelGroup)) {
             channels_.add(channel);
          }
       }
    }

    public ChannelSpec[] getChannels() {
        return channels_.toArray(new ChannelSpec[0]);
    }

    public ChannelSpec[] getUsedChannels() {
        return channels_.stream()
                        .filter(ChannelSpec::useChannel)
                        .toArray(ChannelSpec[]::new);
    }

    public int getNumChannels() {
        return channels_.size();
    }

    public void addEmptyChannel() {
        channels_.add(new ChannelSpec(channelGroup_));
    }

    public void removeChannel(final int index) {
        if (index >= 0 && index < channels_.size()) {
            channels_.remove(index);
        }
    }

    public void setChannelGroup(final String channelGroup) {
        channelGroup_ = channelGroup;
    }

    public String getChannelGroup() {
        return channelGroup_;
    }

    public double getChannelOffset(final String channelName) {
        for (ChannelSpec channel : channels_) {
            if (channel.getName().equals(channelName)) {
                return channel.getOffset();
            }
        }
        return 0.0;
    }
}
