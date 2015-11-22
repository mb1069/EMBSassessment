package lsi.wsn.sync;

/**
 * Represents a possible tuple (n, t) for a configuration of a sink
 */
public class SinkProperties{
    private int n; // n: Number of beacons sent by sink per cycle
    private double t; // t: interval between consecutive beacon in range [0.5, 1.5]

    public SinkProperties(int n, double t) {
        this.n = n;
        this.t = t;
    }

    public int getN() {
        return n;
    }
    public void setN(int n) {
        this.n = n;
    }

    public double getT() {
        return t;
    }
    public void setT(double t) {
        this.t = t;
    }

    @Override
    public String toString() {
        return String.format("SP:{N:%s T:%s}", this.n, this.t);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SinkProperties that = (SinkProperties) o;

        return n == that.n && Double.compare(that.t, t) == 1;

    }
}
