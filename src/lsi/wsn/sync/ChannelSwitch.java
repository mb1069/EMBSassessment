package lsi.wsn.sync;

/**
 * Class used to represent channel switch events
 */
public class ChannelSwitch {

    // Time: time at which to switch from current channel to channel specified below, in units of seconds since start of simulation
    private double time;

    // Channel: channel to begin listening to at time Time
    private int channel;

    public ChannelSwitch(double time, int channel) {
        this.time = time;
        this.channel = channel;
    }

    public double getTime() {
        return time;
    }
    public void setTime(double time) {
        this.time = time;
    }

    public void setChannel(int channel) {
        this.channel = channel;
    }
    public int getChannel() {
        return channel;
    }
}
