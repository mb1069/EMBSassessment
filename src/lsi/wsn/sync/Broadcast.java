package lsi.wsn.sync;

public class Broadcast implements Comparable{
    public int channel;
    public double broadcastTime;
    public double cutoffTime;

    public Broadcast(int channel, double broadcastTime, double cutoffTime) {
        this.channel = channel;
        this.broadcastTime = broadcastTime;
        this.cutoffTime = cutoffTime;
    }


    @Override
    public int compareTo(Object o) {
        Broadcast b = (Broadcast) o;
        return (int) Math.ceil(b.cutoffTime-b.cutoffTime);
    }
}
