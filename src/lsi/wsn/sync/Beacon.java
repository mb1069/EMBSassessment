package lsi.wsn.sync;

/**
 * Created by mb1069 on 16/11/2015.
 */
import ptolemy.actor.util.Time;
public class Beacon {
    public double t;
    public int n;

    public Beacon(double t, int n) {
        this.t = t;
        this.n = n;
    }
}
