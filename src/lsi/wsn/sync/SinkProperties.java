package lsi.wsn.sync;

public class SinkProperties{
    int n;
    double t;

    public SinkProperties(int n, double t) {
        this.n = n;
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
