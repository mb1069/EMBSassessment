package lsi.wsn.sync;

import java.util.ArrayList;
import java.util.Set;

/**
 * Class representing a Sink node.
 */
public class Sink{

    private Double T; // T: period of sink in range [0.5, 1.5]
    private Integer N; // N: number of beacon frames per cycle
    private int channel; // Channel: channel on which the sourceNode can communicate with this sink
    private ArrayList<Beacon> beacons = new ArrayList<Beacon>(); // Beacons: List of all received beacons
    private int numTransmitted = 0;  // numTransmitted: number of broadcasts to this sink from the SourceNode
    private Set<SinkProperties> possibleProperties; // possibleProperties: set of all potential tuples (T,N) that are compatible with the received beacons of this sink
    private int plannedBroadcasts = 0; // plannedBroadcasts: number of scheduled broadcasts to this sink
    private double lastBeaconTime; // lastBeaconTime: time at which the last beacon was received on this channel

    public Sink(int channel) {
        this.channel = channel;
    }

    public int getPlannedBroadcasts() {
        return plannedBroadcasts;
    }
    public void setPlannedBroadcasts(int plannedBroadcasts) {
        this.plannedBroadcasts = plannedBroadcasts;
    }

    public Double getT() {
        return T;
    }
    public void setT(Double t) {
        T = t;
    }

    public Integer getN() {
        return N;
    }
    public void setN(Integer n) {
        N = n;
    }

    public int getChannel() {
        return channel;
    }
    public void setChannel(int channel) {
        this.channel = channel;
    }

    public ArrayList<Beacon> getBeacons() {
        return beacons;
    }
    public void setBeacons(ArrayList<Beacon> beacons) {
        this.beacons = beacons;
    }

    public int getNumTransmitted() {
        return numTransmitted;
    }
    public void setNumTransmitted(int numTransmitted) {
        this.numTransmitted = numTransmitted;
    }

    public Set<SinkProperties> getPossibleProperties() {
        return possibleProperties;
    }
    public void setPossibleProperties(Set<SinkProperties> possibleProperties) {
        this.possibleProperties = possibleProperties;
    }

    public double getLastBeaconTime() {
        return lastBeaconTime;
    }
    public void setLastBeaconTime(double lastBeaconTime) {
        this.lastBeaconTime = lastBeaconTime;
    }

    public void incrementPlannedBroadcasts() {
        this.plannedBroadcasts++;
    }

    public void incrementNumTransmitted() {
        this.numTransmitted++;
    }
}
