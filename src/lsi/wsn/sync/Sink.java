package lsi.wsn.sync;

import ptolemy.actor.util.Time;

import java.util.ArrayList;

public class Sink{
    public Double t;
    public int channel;
    public Integer N;
    public ArrayList<Beacon> beacons = new ArrayList<Beacon>();
    public int numTransmitted = 0;

    public Sink(int channel) {
        this.channel = channel;
    }

}
