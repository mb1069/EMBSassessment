package lsi.wsn.sync;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

public class TestProperties {
    public static final int N_MAX = 10;
    public static final int N_MIN = 1;
    public static final double T_MIN = 0.5;
    public static final double T_MAX = 1.5;
    public static final double P_MAX = (11+N_MAX)*T_MAX;

    public static void main(String[] args) throws Exception {
        Beacon a = new Beacon(18.7, 2);
        Beacon b = new Beacon(39, 5);
        Beacon c = new Beacon(13.5, 6);
        Set<SinkProperties> sps = generatePossibleSinkProperties(a, b);
        System.out.println(getEarliestPossibleBeacon(sps, b));
        Set<SinkProperties> sps2 = generatePossibleSinkProperties(a,c);
        System.out.println(sps);
        System.out.println(sps2);
        System.out.println(spIntersection(sps, sps2));
    }
    private static double getEarliestPossibleBeacon(Set<SinkProperties> setSP, Beacon latestBeacon){
        double shortestPeriod = Double.MAX_VALUE;
        int possibleN = 0;
        int latestN = latestBeacon.n;
        for (SinkProperties sp: setSP){
            double potentialPeriod = (11+sp.n)*sp.t;
            if (potentialPeriod<shortestPeriod){
                shortestPeriod = potentialPeriod;
                possibleN = sp.n;
            }
        }
        //CurrentTime - diffN (to go to start of series) + sleep/receive period * estimated short period
        return latestBeacon.t+((11+possibleN-latestN)*(shortestPeriod/(11+possibleN)));
    }

    private static Set<SinkProperties> spIntersection(Set<SinkProperties> setA, Set<SinkProperties> setB){
        Set<SinkProperties> temp = new HashSet<SinkProperties>();
        for (SinkProperties sp: setA){
            for (SinkProperties sp2: setB){
                if (sp.n==sp2.n && sp.t==sp2.t){
                    temp.add(sp);
                }
            }
        }
        return temp;
    }

    private static HashSet<SinkProperties> generatePossibleSinkProperties(Beacon b1, Beacon b2) {
        HashSet<SinkProperties> sps = new HashSet<SinkProperties>();
        double diffT = Math.abs(b2.t - b1.t);
        int diffN = b1.n-b2.n;

        int maxObservedN = Math.max(b1.n, b2.n);
        int maxI = (int) Math.floor(diffT/(11*T_MIN));

        for (int i=0; i<=18; i++){
            for (int n=maxObservedN; n<=N_MAX; n++){
                double period = diffT/(double)(((11+n)*i)+diffN);
                if (T_MIN<=period && period <=T_MAX) {
                    sps.add(new SinkProperties(n, period));
                }
            }
        }
        return sps;
    }
}