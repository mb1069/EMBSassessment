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
    public static final double tttt_MIN = 0.5;
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
        setSinkAndChannel(currentChannel, lastChannelSwitch);
    }

    @Override
    public void fire() throws IllegalActionException {
        super.fire();
        double currentTime = getDirector().getModelTime().getDoubleValue();
        System.out.println("Time: "+currentTime);
        // If any broadcasts need to be done

        ArrayList<Broadcast> broadcastsAtTime = timeInBroadcasts(currentTime, broadcasts);
        if (broadcastsAtTime.size() > 0) {
            int channel = currentChannel;
            for (Broadcast b : fireBroadcasts(currentTime, broadcastsAtTime)) {
                broadcasts.remove(b);
            }
            createChannelSwitchCallBack( currentTime+microDelay, channel);
        }

//        ChannelSwitch cs = timeInChannelSwitches(currentTime, channelSwitches);
//        if (cs!=null){
//            System.out.println(String.format("Time: %s Switching to channel:%s", currentTime, cs.channel));
//            setSinkAndChannel(cs.channel, currentTime);
//            return;
//        }

        if (input.hasToken(0) && sinks.containsKey(currentChannel)) {
            Token countToken = input.get(0);
            int count = ((IntToken) countToken).intValue();
            Beacon b = new Beacon(currentTime, count);
            sinks.get(currentChannel).beacons.add(b);
            System.out.println(String.format("Time: %s Channel: %s Received beacon n: %s w/ count: %s ", currentTime, currentChannel, sinks.get(currentChannel).beacons.size(), b.n));

//            if (waitedLongerThanMinPeriod(currentTime, lastChannelSwitch, tttt_MIN)) {
//                sinks.get(currentChannel).N = b.n;
//            }
//            if (sinks.get(currentChannel).T != null) {
//                create2ndBroadcastUsingPeriod(b, currentChannel, sinks.get(currentChannel).T);
//                setSinkAndChannel(pickNextChannel(), currentTime);
//            } else {
//            if (sinks.get(currentChannel).N != null) {
//
//                //  generate both broadcasts
//                double broadcast = createEarliestPossibleBroadcast(sinks.get(currentChannel).beacons.get(0), b, currentChannel, sinks.get(currentChannel).N);
//                double period = calculatePossiblePeriods(sinks.get(currentChannel).beacons.get(0), b, sinks.get(currentChannel).N);
//                sinks.get(currentChannel).T = period;
//                create2ndBroadcastUsingPreviousBroadcast(broadcast, period, currentChannel, sinks.get(currentChannel).N);
//                deleteSink(currentChannel);
//            } else {
            if (sinks.get(currentChannel).beacons.size() > 1) {
                int numBeacons = sinks.get(currentChannel).beacons.size();
                if (currentTime==13.5){
                    System.out.println();
                }
                Set<SinkProperties> sps = generatePossibleSinkProperties(sinks.get(currentChannel).beacons.get(numBeacons - 2), sinks.get(currentChannel).beacons.get(numBeacons - 1));
                if (sinks.get(currentChannel).possibleProperties == null) {
                    sinks.get(currentChannel).possibleProperties = sps;
                } else {
                    sinks.get(currentChannel).possibleProperties= intersection(sinks.get(currentChannel).possibleProperties, sps);
                }
                if (sinks.get(currentChannel).possibleProperties.size() == 1) {
                    SinkProperties sp = sinks.get(currentChannel).possibleProperties.toArray(new SinkProperties[1])[0];
                    sinks.get(currentChannel).N = sp.n;
                    sinks.get(currentChannel).T = sp.t;
                    double broadcast = createEarliestPossibleBroadcast(sinks.get(currentChannel).beacons.get(0), b, currentChannel, sp.t, currentTime);
                    create2ndBroadcastUsingPreviousBroadcast(broadcast, sinks.get(currentChannel).T, currentChannel, sinks.get(currentChannel).N);
                    sinks.get(currentChannel).completed = true;
                    setSinkAndChannel(pickNextChannel(currentChannel), currentTime);
                }
            }
        }
//        output.send(0, new IntToken(currentChannel));
    }

    private HashSet<SinkProperties> generatePossibleSinkProperties(Beacon b1, Beacon b2) {
        if (getDirector().getModelTime().getDoubleValue()==16.0){
            System.out.print(" ");
        }
        HashSet<SinkProperties> sps = new HashSet<SinkProperties>();
        double diffT = Math.abs(b2.t-b1.t);
        int diffN = b1.n-b2.n;

        int maxObservedN = Math.max(b1.n, b2.n);
        int maxI = (int) Math.floor(diffT/(11* tttt_MIN));

        for (int i=0; i<=maxI; i++){
            for (int n=maxObservedN; n<=N_MAX; n++){
                double period = diffT/(double)(((11+n)*i)+diffN);
                if (tttt_MIN <=period && period <=T_MAX) {
                    sps.add(new SinkProperties(n, period));
                }
            }
        }
        return sps;
    }

//    private Time getNextBroadcastTime(Time currentTime, ArrayList<Broadcast> broadcasts) throws IllegalActionException {
//        double minDifference = Double.MAX_VALUE;
//        double cTime = currentTime;
//        for (Broadcast b: broadcasts){
//            double broadcastTime = b.broadcastTime.getDoubleValue();
//            if (broadcastTime>cTime){
//                double diff = broadcastTime-cTime;
//                if (diff<minDifference){
//                    minDifference = diff;
//                }
//            }
//        }
//        return new Time(getDirector(),cTime+minDifference);
//    }

    private ArrayList<Broadcast> timeInBroadcasts(double time, ArrayList<Broadcast> broadcasts) {
        ArrayList<Broadcast> broadcastsAtTime = new ArrayList<Broadcast>();
        for (Broadcast b : broadcasts) {
            if (time == b.broadcastTime) {
                broadcastsAtTime.add(b);
            }
        }
        return broadcastsAtTime;
    }

    private double calculatePossiblePeriods(Beacon b1, Beacon b2, int N) {
        double diffTime = Math.abs(b1.t - b2.t);
        double diffN = Math.abs(b1.n - b2.n);
        if (N == 1 && b1.n == b2.n) {
            return diffTime / 12;
        }
        ArrayList<Double> possiblePeriods = new ArrayList<Double>();
        double period = Double.MAX_VALUE;
        int i = 0;
        while (period > tttt_MIN) {
            period = diffTime / (((11 + N) * i) + diffN);
            if (tttt_MIN <= period && period <= T_MAX) {
                possiblePeriods.add(period);
            }
            i++;
        }
        if (possiblePeriods.size() > 1) {
            throw new IllegalStateException("More than one possible period");
        }
        if (possiblePeriods.size() == 0) {
            throw new IllegalStateException("No possible period.");
        }
        return possiblePeriods.get(0);
    }

    private ArrayList<Broadcast> fireBroadcasts(double currentTime, ArrayList<Broadcast> scheduledBroadcasts) throws IllegalActionException {
        ArrayList<Broadcast> fired = new ArrayList<Broadcast>();
        for (Broadcast b : scheduledBroadcasts) {
            if ((b.broadcastTime == currentTime) && (b.cutoffTime - getDirector().getModelTime().getDoubleValue() > 0)) {
                setSinkAndChannel(b.channel, currentTime);
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

    private int tryPickDifferentChannel(int currentChannel) {
        for (Sink s : sinks.values()) {
            if (s.channel != currentChannel) {
                return s.channel;
            }
        }
        System.out.println("1 channel left");
        return currentChannel;
    }

    private int pickNextChannel(int currentChannel) {
        for (Sink s : sinks.values()) {
            if (!channelHasPendingBroadcast(s.channel) && !sinks.get(s.channel).completed && s.channel!=currentChannel) {
                return s.channel;
            }
        }
        return currentChannel;
    }

    private boolean channelHasPendingBroadcast(int channel) {
        for (Broadcast b : broadcasts) {
            if (b.channel == channel) {
                return true;
            }
        }
        return false;
    }

    private void setSinkAndChannel(int channel, double time) throws IllegalActionException {
        setChannel(channel, time);
    }

    private void setChannel(int channel, double time) throws IllegalActionException {
        System.out.println(String.format("Time: %s Switched from channel %s to channel %s", getDirector().getModelTime(), currentChannel, channel));
        currentChannel = channel;
        lastChannelSwitch = time;
        channelOutput.send(0, new IntToken(channel));
    }

    private double createEarliestPossibleBroadcast(Beacon b1, Beacon b2, int channel, double period, double currentTime) throws IllegalActionException {
        double broadcastTime = period * (Math.min(b1.n, b2.n)) + currentTime;
        double deadline = broadcastTime + tttt_MIN;
        setupBroadcastAndCallBack(broadcastTime, deadline, channel);
        System.out.println("  1st broadcast.");
        return broadcastTime;
    }

    private void create2ndBroadcastUsingPeriod(Beacon b, int channel, double t) throws IllegalActionException {
        double broadcast = (b.n * t) + b.t;
        double deadline = broadcast + t;
        if (setupBroadcastAndCallBack(broadcast, deadline, channel)) {
            removeAllSCforChannel(channel);
            System.out.println("  2nd broadcast w/ 3 beacons.");
        }
    }

    private void create2ndBroadcastUsingPreviousBroadcast(double previousBroadcast, double t, int channel, Integer n) throws IllegalActionException {
        double broadcast = previousBroadcast + ((11 + n) * t);
        double deadline = broadcast + t;
        if (setupBroadcastAndCallBack(broadcast, deadline, channel)) {
            removeAllSCforChannel(channel);
            System.out.println("  2nd broadcast w/ 2 beacons.");
        }
    }


    //Boolean managed to schedule task
    private boolean setupBroadcastAndCallBack(double broadcastTime, double deadline, int channel) throws IllegalActionException {
        ArrayList<Broadcast> bAtTime = timeInBroadcasts(broadcastTime, broadcasts);
//        if (bAtTime.size() > 0) {
//            if (deadline - broadcastTime > microDelay) {
//                return setupBroadcastAndCallBack(broadcastTime + microDelay, deadline, channel);
//            } else {
//                System.out.println(String.format("Failed to schedule broadcast for channel: %s at time: %s:", channel, broadcastTime));
//                return false;
//            }
////            if (checkAllBroadcastsMeetDeadline(bAtTime, microDelay)){
////                broadcasts.removeAll(bAtTime);
////                for (Broadcast b: bAtTime){
////                    b.broadcastTime.add(microDelay);
////                }
////                broadcasts.addAll(bAtTime);
////                getDirector().fireAt(this, getLatestBroadcastTime(bAtTime));
////                return true;
////            } else {
////                return false;
////            }
//
//
//        }
        Broadcast b = new Broadcast(channel, broadcastTime, deadline);
        getDirector().fireAt(this, new Time(getDirector(), broadcastTime));
        System.out.println(String.format("Preparing broadcast on channel: %s at time: %s w/ 2 beacons.", channel, broadcastTime));
        broadcasts.add(b);
        return true;
    }

    private Time getLatestBroadcastTime(ArrayList<Broadcast> broadcasts) throws IllegalActionException {
        double time = 0.0;
        for (Broadcast b : broadcasts) {
            double value = b.broadcastTime;
            if (value > time) {
                time = value;
            }
        }
        return new Time(getDirector(), time);
    }

    private boolean checkAllBroadcastsMeetDeadline(ArrayList<Broadcast> broadcasts, double delay) {
        for (Broadcast b : broadcasts) {
            if (b.broadcastTime + delay < b.cutoffTime) {
                return false;
            }
        }
        return true;
    }

//
//    public double calculatePeriod(Beacon b1, Beacon b2, int N) throws IllegalActionException {
//        double numPeriods = Math.abs(b1.n - b2.n);
//        double period;
//        if (numPeriods == 0) {
//            //Special case for N=1
//            period = Math.abs((b1.t - b2.t)) / (N + 11);
//        } else {
//            period = Math.abs((b1.t - b2.t)) / numPeriods;
//        }
//        System.out.println(String.format("Calculated period: %s for channel: %s", period, currentChannel));
//        return period;
//    }

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
            t += tttt_MIN;
        }
        if (timeInChannelSwitches(t, channelSwitches) != null) {
            t += tttt_MIN;
        }
        System.out.println(String.format("Time: %s created channel SC at time %s for channel %s", getDirector().getModelTime(), t, c));
        channelSwitches.add(new ChannelSwitch(t, c));
        getDirector().fireAt(this, t);
    }


    private Time calculateChannelCallbackOfNextSeries(Beacon b, double period, Time currentTime) throws IllegalActionException {
        double listenTime = b.t + ((11 + b.n) * period);
        return new Time(getDirector(), listenTime).add(currentTime);
    }

    private boolean waitedLongerThanMinPeriod(double time, double lastChannelSwitch, double minPeriod) {
        return (time - lastChannelSwitch - minPeriod) > 0;
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
