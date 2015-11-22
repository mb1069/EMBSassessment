package lsi.wsn.sync;

/**
 * Represents a scheduled broadcast
 */
public class Broadcast implements Comparable{
    private int channel; // channel: channel on which to send a packet
    private double broadcastTime; // broadcastTime: time at which to broadcast
    private double deadline; // deadline: time at which

    public Broadcast(int channel, double broadcastTime, double cutoffTime) {
        this.channel = channel;
        this.broadcastTime = broadcastTime;
        this.deadline = cutoffTime;
    }

    public int getChannel() {
        return channel;
    }
    public void setChannel(int channel) {
        this.channel = channel;
    }

    public double getBroadcastTime() {
        return broadcastTime;
    }
    public void setBroadcastTime(double broadcastTime) {
        this.broadcastTime = broadcastTime;
    }

    public double getDeadline() {
        return deadline;
    }
    public void setDeadline(double deadline) {
        this.deadline = deadline;
    }

    @Override
    public int compareTo(Object o) {
        Broadcast b = (Broadcast) o;
        return (int) Math.ceil(b.deadline -b.deadline);
    }
}
