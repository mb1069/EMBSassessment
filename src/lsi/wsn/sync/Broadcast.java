package lsi.wsn.sync;

/**
 * Created by mb1069 on 16/11/2015.
 */
import ptolemy.actor.util.Time;


public class Broadcast {
    public int channel;
    public Time broadcastTime;
    public Time cutoffTime;

    public Broadcast(int channel, Time broadcastTime, Time cutoffTime) {
        this.channel = channel;
        this.broadcastTime = broadcastTime;
        this.cutoffTime = cutoffTime;
    }


}
