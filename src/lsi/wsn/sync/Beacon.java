package lsi.wsn.sync;

/**
 * Created by mb1069 on 16/11/2015.
 */
import ptolemy.actor.util.Time;
public class Beacon {
    public Time t;
    public int n;

    public Beacon(Time t, int n) {
        this.t = t;
        this.n = n;
    }
}
