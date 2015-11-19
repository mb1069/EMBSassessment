package lsi.wsn.sync;

import java.util.ArrayList;
import java.util.Set;

public class Sink{
    public Double T;
    public int channel;
    public Integer N;
    public ArrayList<Beacon> beacons = new ArrayList<Beacon>();
    public int numTransmitted = 0;
    public Set<SinkProperties> possibleProperties;
    public boolean completed = false;

    public Sink(int channel) {
        this.channel = channel;
    }

    public double getMinPossiblePeriod() {
        double min = Double.MAX_VALUE;
        for (SinkProperties sp: possibleProperties){
            if (sp.t<min){
                min =sp.t;
            }
        }
        return min;
    }
}
