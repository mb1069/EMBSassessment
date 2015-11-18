package lsi.wsn.sync;

import ptolemy.actor.util.Time;

public class ChannelSwitch {

    public Time time;
    public int channel;

    public ChannelSwitch(Time time, int channel) {
        this.time = time;
        this.channel = channel;
    }
}
