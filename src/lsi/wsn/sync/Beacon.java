package lsi.wsn.sync;

/**
 * Represents a beacon received from a sink
 */
public class Beacon {
    public double t; // t: time at which beacons was received, in seconds from start of simulation
    public int n; // n: integer represented in payload of beacon

    public Beacon(double t, int n) {
        this.t = t;
        this.n = n;
    }
}
