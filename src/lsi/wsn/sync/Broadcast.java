package lsi.wsn.sync;

/**
 * Created by mb1069 on 16/11/2015.
 */
import ptolemy.actor.util.Time;


public class Broadcast implements Comparable{
    public int channel;
    public Time broadcastTime;
    public Time cutoffTime;

    public Broadcast(int channel, Time broadcastTime, Time cutoffTime) {
        this.channel = channel;
        this.broadcastTime = broadcastTime;
        this.cutoffTime = cutoffTime;
    }


    @Override
    public int compareTo(Object o) {
        Broadcast b = (Broadcast) o;
        return (int) Math.ceil(b.cutoffTime.getDoubleValue()-b.cutoffTime.getDoubleValue());
    }
}
