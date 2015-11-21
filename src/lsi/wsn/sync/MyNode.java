package lsi.wsn.sync;
//   lsi.wsn.sync.MyNode

import ptolemy.actor.TypedAtomicActor;
import ptolemy.actor.TypedIOPort;
import ptolemy.actor.util.Time;
import ptolemy.data.IntToken;
import ptolemy.data.Token;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;

import java.util.*;

public class MyNode extends TypedAtomicActor {
    protected TypedIOPort input;
    protected TypedIOPort output;
    protected TypedIOPort feedbackOutput;
    protected TypedIOPort channelOutput;
    protected ArrayList<ChannelSwitch> channelSwitches = new ArrayList<ChannelSwitch>();
    protected int currentChannel = 11;
    protected int[] channels = {11, 12, 13, 14, 15};
    protected HashMap<Integer, Sink> sinks = new HashMap<Integer, Sink>();
    protected ArrayList<Broadcast> broadcasts = new ArrayList<Broadcast>();
    protected double microDelay = 0.001;
    protected double lastChannelSwitch;

    public static final int N_MAX = 10;
    public static final double T_MIN = 0.5;
    public static final double T_MAX = 1.5;


    public MyNode(CompositeEntity container, String name) throws IllegalActionException, NameDuplicationException {
        super(container, name);
        input = new TypedIOPort(this, "input", true, false);
        output = new TypedIOPort(this, "output", false, true);
        feedbackOutput = new TypedIOPort(this, "output2", false, true);
        channelOutput = new TypedIOPort(this, "channel", false, true);
        for (int channel : channels) {
            sinks.put(channel, new Sink(channel));
        }
    }

    @Override
    public void initialize() throws IllegalActionException {
        super.initialize();
        lastChannelSwitch = getDirector().getModelTime().getDoubleValue();
        setChannel(currentChannel, lastChannelSwitch);
    }



    @Override
    public void fire() throws IllegalActionException {
        super.fire();

        double currentTime = round(getDirector().getModelTime().getDoubleValue());
        System.out.println("Time: "+currentTime);
        // If any broadcasts need to be done
        ArrayList<Broadcast> broadcastsAtTime = timeInBroadcasts(currentTime, broadcasts);

        if (broadcastsAtTime.size() > 0) {
            int channel = currentChannel;
            for (Broadcast b : fireBroadcasts(currentTime, broadcastsAtTime)) {
                broadcasts.remove(b);
            }
            createChannelSwitchCallBack(currentTime+microDelay, channel);
        }

        ChannelSwitch cs = timeInChannelSwitches(currentTime, channelSwitches);
        if (cs!=null && (cs.channel!=currentChannel)){
            setChannel(cs.channel, currentTime);
        }

        if (currentTime==26.4){
            System.out.println("");
        }

        //TODO remove this
        //Process tokens
        if (input.hasToken(0) && sinks.containsKey(currentChannel)) {
            Token countToken = input.get(0);
            int count = ((IntToken) countToken).intValue();
            Beacon b = new Beacon(currentTime, count);
            sinks.get(currentChannel).beacons.add(b);
            int numBeacons = sinks.get(currentChannel).beacons.size();
            System.out.println(String.format("Time: %s Channel: %s Received beacon n: %s w/ count: %s ", currentTime, currentChannel, sinks.get(currentChannel).beacons.size(), b.n));


            // If nothing heard on this channel in more than T_MAX
            if (currentTime-lastChannelSwitch>T_MAX && (currentTime - sinks.get(currentChannel).lastBeaconTime >T_MAX)){
                sinks.get(currentChannel).N=b.n;
            }
            sinks.get(currentChannel).lastBeaconTime = currentTime;


            if (currentTime==34.1){
                System.out.println(" ");
            }

            ArrayList<Beacon> beacons = sinks.get(currentChannel).beacons;
            //Special case for beacons with same n and channel with known N
            if (numBeacons==2 && beaconsHaveSameN(beacons) && sinks.get(currentChannel).N!=null && latestBeaconsWithinMaxCyclePeriod(sinks.get(currentChannel).beacons, numBeacons) && sinks.get(currentChannel).plannedBroadcasts<2){
                sinks.get(currentChannel).T = round((Math.abs(beacons.get(numBeacons-1).t - beacons.get(numBeacons-2).t))/(11+b.n));
                double broadcastTime = createNextPossibleBroadcast(b, currentChannel, sinks.get(currentChannel).T, currentTime);
                create2ndBroadcastUsingPreviousBroadcast(broadcastTime, sinks.get(currentChannel).T, currentChannel, sinks.get(currentChannel).N);
            }


            // If both N and T are known, can create the 2nd broadcast if needed
            if (sinks.get(currentChannel).plannedBroadcasts<2 && sinks.get(currentChannel).N!=null && sinks.get(currentChannel).T!=null){
                System.out.println("N AND T KNOWN: " + sinks.get(currentChannel).plannedBroadcasts);
                create2ndBroadcastUsingPeriod(b, currentChannel, sinks.get(currentChannel).T, sinks.get(currentChannel).N);
            }

            // If more than 2 beacons available and broadcasts needed, can generate possible combinations of T and N
            if (sinks.get(currentChannel).beacons.size() > 1 && sinks.get(currentChannel).plannedBroadcasts<2) {
                // Generate compatible combinations of T and N for latest pair of beacons
                if (currentTime == 40.8){
                    System.out.println(" ");
                }

                Set<SinkProperties> sps = generatePossibleSinkProperties(sinks.get(currentChannel).beacons.get(numBeacons - 2), sinks.get(currentChannel).beacons.get(numBeacons - 1), sinks.get(currentChannel).N);
                if (sinks.get(currentChannel).possibleProperties == null) {
                    sinks.get(currentChannel).possibleProperties = sps;
                } else {
                    sinks.get(currentChannel).possibleProperties= intersection(sinks.get(currentChannel).possibleProperties, sps);
                }
                // If only 1 possible combination, shift to known domain and apply to create all needed broadcasts
                if (sinks.get(currentChannel).possibleProperties.size() == 1) {
                    SinkProperties sp = sinks.get(currentChannel).possibleProperties.iterator().next();
                    sinks.get(currentChannel).N = sp.n;
                    sinks.get(currentChannel).T = sp.t;
                    double broadcast = createNextPossibleBroadcast(b, currentChannel, sp.t, currentTime);
                    if (sinks.get(currentChannel).plannedBroadcasts<2) {
                        create2ndBroadcastUsingPreviousBroadcast(broadcast, sinks.get(currentChannel).T, currentChannel, sinks.get(currentChannel).N);
                    }
                }
            }
            int newChannel = pickNextChannel(currentChannel, currentTime);
            if (newChannel!=currentChannel) {
                // If broadcasts are all sorted for channel, do not return to it
                if (sinks.get(currentChannel).plannedBroadcasts==2){
                    setChannel(newChannel, currentTime);
                    // If one more broadcast to go and 2 known beacons
                } else if (numBeacons==2 && sinks.get(currentChannel).plannedBroadcasts<2){
                    double t = getEarliestPossibleBeacon(sinks.get(currentChannel).possibleProperties, b);
                    createChannelSwitchCallBack(t, currentChannel);
                    setChannel(newChannel, currentTime);
                    // Quick switchback to receive subsequent beacon
                } else if (sinks.get(currentChannel).beacons.size()==1 && b.n!=1){
                    createChannelSwitchCallBack(currentTime+(T_MIN-microDelay), currentChannel);
                    setChannel(newChannel, currentTime);
                    // Long sleep if channel about to enter sleep period
                } else if (sinks.get(currentChannel).beacons.size()==1 && b.n==1){
                    createChannelSwitchCallBack(currentTime+(11*(T_MIN-microDelay)), currentChannel);
                    setChannel(newChannel, currentTime);
                }
            }
        }

    }

    private boolean beaconsHaveSameN(ArrayList<Beacon> beacons) {
        int n = beacons.get(0).n;
        for (Beacon b: beacons){
            if (b.n!=n){
                return false;
            }
        }
        return true;
    }


    private boolean latestBeaconsWithinMaxCyclePeriod(ArrayList<Beacon> beacons, int numBeacons) {
        double timeDiff = Math.abs(beacons.get(numBeacons - 1).t - beacons.get(numBeacons - 2).t);
        double nDiff = (beacons.get(numBeacons-2).n-beacons.get(numBeacons-1).n);
        return timeDiff<=(T_MAX*(11+nDiff));
    }

    private double getEarliestPossibleBeacon(Set<SinkProperties> setSP, Beacon latestBeacon){
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
        return round(latestBeacon.t+((11+possibleN-latestN)*(shortestPeriod/(11+possibleN))));
    }

    private HashSet<SinkProperties> generatePossibleSinkProperties(Beacon b1, Beacon b2, Integer N) {

        HashSet<SinkProperties> sps = new HashSet<SinkProperties>();
        double diffT = Math.abs(b2.t-b1.t);


        int diffN = b1.n-b2.n;

        int maxObservedN = Math.max(b1.n, b2.n);
        int maxI = (int) Math.floor(diffT/(11* T_MIN));

        for (int i=0; i<=maxI; i++){
            for (int n=maxObservedN; n<=N_MAX; n++){
                double period = diffT/(double)(((11+n)*i)+diffN);
                if (T_MIN <=period && period <=T_MAX) {
                    if (N == null) {
                        sps.add(new SinkProperties(n, round(period)));
                    } else if (n==N){
                        sps.add(new SinkProperties(n, round(period)));
                    }
                }
            }
        }
        return sps;
    }

    private double round(double x){
        double factor = 1e3;
        x+=0.0001;
        return Math.floor(x * factor) / factor;
    }
    private ArrayList<Broadcast> timeInBroadcasts(double time, ArrayList<Broadcast> broadcasts) {
        ArrayList<Broadcast> broadcastsAtTime = new ArrayList<Broadcast>();
        for (Broadcast b : broadcasts) {
            if (time == b.broadcastTime) {
                broadcastsAtTime.add(b);
            }
        }
        return broadcastsAtTime;
    }

    private ArrayList<Broadcast> fireBroadcasts(double currentTime, ArrayList<Broadcast> scheduledBroadcasts) throws IllegalActionException {
        ArrayList<Broadcast> fired = new ArrayList<Broadcast>();
        for (Broadcast b : scheduledBroadcasts) {
            if ((b.broadcastTime == currentTime) && (b.cutoffTime - getDirector().getModelTime().getDoubleValue() > 0)) {
                setChannel(b.channel, currentTime);
                System.out.println(String.format("Time: %s CutoffTime: %s Channel: %s Broadcasting!", getDirector().getModelTime(), b.cutoffTime, b.channel));
                feedbackOutput.send(0, new IntToken(666));
                try {
                    sinks.get(b.channel).numTransmitted++;
                } catch (NullPointerException ignored) {
                }
                fired.add(b);
            }
        }
        return fired;

    }

    private int pickNextChannel(int currentChannel, double currentTime) {
        for (Sink s : sinks.values()) {
            if (!channelHasPendingSC(s.channel, currentTime) && sinks.get(s.channel).plannedBroadcasts<2 && s.channel!=currentChannel) {
                return s.channel;
            }
        }
        return currentChannel;
    }

    // If channel has pending switch, it is currently in sleep mode
    private boolean channelHasPendingSC(int channel, double currentTime) {
        for (ChannelSwitch sc : channelSwitches) {
            if (sc.channel == channel && sc.time-currentTime>T_MAX) {
                return true;
            }
        }
        return false;
    }

    private void setChannel(int channel, double time) throws IllegalActionException {
        ArrayList<ChannelSwitch> cs = new ArrayList<ChannelSwitch>();
        for (ChannelSwitch c: channelSwitches){
            if (c.time==time){
                cs.add(c);
            }
        }
        channelSwitches.removeAll(cs);

        System.out.println(String.format("Time: %s Switched from channel %s to channel %s", getDirector().getModelTime(), currentChannel, channel));
        currentChannel = channel;
        lastChannelSwitch = time;
        channelOutput.broadcast(new IntToken(channel));
    }

    private double createNextPossibleBroadcast(Beacon b, int channel, double period, double currentTime) throws IllegalActionException {
        double broadcastTime = round((period  * b.n) + currentTime);
        double deadline = broadcastTime + T_MIN;
        setupBroadcastAndCallBack(broadcastTime, deadline, channel);
        System.out.println("  1st broadcast.");
        return broadcastTime;
    }

    private void create2ndBroadcastUsingPeriod(Beacon b, int channel, double t, int N) throws IllegalActionException {
        double broadcast = round((((N+11)+b.n) * t) + b.t);
        double deadline = broadcast + t;
        if (setupBroadcastAndCallBack(broadcast, deadline, channel)) {
            sinks.get(currentChannel).completed = true;
            removeAllSCforChannel(channel);
            System.out.println("  2nd broadcast w/ 3 beacons.");
        }
    }

    private void create2ndBroadcastUsingPreviousBroadcast(double previousBroadcast, double t, int channel, Integer n) throws IllegalActionException {
        double broadcast = previousBroadcast + ((11 + n) * t);
        double deadline = broadcast + t;
        if (setupBroadcastAndCallBack(broadcast, deadline, channel)) {
            sinks.get(currentChannel).completed = true;
            removeAllSCforChannel(channel);
            System.out.println("  2nd broadcast w/ 2 beacons.");
        }
    }


    //Returns true if managed to schedule task
    private boolean setupBroadcastAndCallBack(double broadcastTime, double deadline, int channel) throws IllegalActionException {
        for(Broadcast b: broadcasts){
            if (b.channel == channel && b.broadcastTime==broadcastTime){
                return false;
            }
        }
        sinks.get(currentChannel).plannedBroadcasts++;
        Broadcast b = new Broadcast(channel, broadcastTime, deadline-microDelay);
        getDirector().fireAt(this, new Time(getDirector(), broadcastTime));
        System.out.println(String.format("Preparing broadcast on channel: %s at time: %s w/ 2 beacons.", channel, broadcastTime));
        broadcasts.add(b);
        return true;
    }

    private ChannelSwitch timeInChannelSwitches(double time, ArrayList<ChannelSwitch> cSwitches) {
        for (ChannelSwitch cs : cSwitches) {
            if (time == cs.time) {
                return cs;
            }
        }
        return null;
    }


    private void createChannelSwitchCallBack(double t, int c) throws IllegalActionException {
        if (timeInBroadcasts(t, broadcasts).size() > 0) {
            createChannelSwitchCallBack(t +T_MIN, c);
            return;
        }

        t = round(t);
        System.out.println(String.format("Time: %s created channel SC at time %s for channel %s", getDirector().getModelTime(), t, c));
        channelSwitches.add(new ChannelSwitch(t, c));
        getDirector().fireAt(this, new Time(getDirector(), t));
    }

    private void removeAllSCforChannel(int channel) {
        ArrayList<ChannelSwitch> toRemove = new ArrayList<ChannelSwitch>();
        for (ChannelSwitch cs : channelSwitches) {
            if (cs.channel == channel) {
                toRemove.add(cs);
            }
        }
        channelSwitches.removeAll(toRemove);
    }


    private static Set<SinkProperties> intersection(Set<SinkProperties> setA, Set<SinkProperties> setB){
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
}
