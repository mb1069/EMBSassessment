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
    protected int currentChannel = 15;
    protected int[] channels = {11, 12, 13, 14, 15};
    protected HashMap<Integer, Sink> sinks = new HashMap<Integer,Sink>();
    protected Sink currentSink;
    protected ArrayList<Broadcast> broadcasts = new ArrayList<Broadcast>();
    protected double microDelay = 0.001;
    protected Time lastChannelSwitch;

    public static final double MIN_PERIOD = 0.5;
    public static final double MAX_PERIOD = 1.5;

    public MyNode(CompositeEntity container, String name) throws IllegalActionException, NameDuplicationException {
        super(container, name);
        input = new TypedIOPort(this, "input", true, false);
        output = new TypedIOPort(this, "output", false, true);
        feedbackOutput = new TypedIOPort(this, "output2", false, true);
        channelOutput = new TypedIOPort(this, "channel", false, true);
        for(int channel: channels) {
            sinks.put(channel, new Sink(channel));
        }
        currentSink = sinks.get(currentChannel);
    }

    @Override
    public void initialize() throws IllegalActionException {
        super.initialize();
        lastChannelSwitch = getDirector().getModelTime();
        setSinkAndChannel(currentChannel, lastChannelSwitch);
    }

    @Override
    public void fire() throws IllegalActionException {
        super.fire();
        Time time = getDirector().getModelTime();
        // If any broadcasts need to be done
        ArrayList<Broadcast> broadcastsAtTime = timeInBroadcasts(time, broadcasts);
        if (broadcastsAtTime.size()>0) {
            int saveChannel = currentChannel;
            for(Broadcast b: fireBroadcasts(time, broadcastsAtTime)){
                broadcasts.remove(b);
            }
            createChannelSwitchCallBack(time.add(microDelay/2), saveChannel);
            return;
        }

        ChannelSwitch cs = timeInChannelSwitches(time, channelSwitches);
        if (cs!=null){
            setSinkAndChannel(cs.channel, time);
            channelSwitches.remove(cs);
        }

        if (time.getDoubleValue()==14.5){
            System.out.println("a");
        }
        if (input.hasToken(0) && this.sinks.containsKey(currentChannel)){
            Token countToken = input.get(0);
            int count = ((IntToken) countToken).intValue();
            Beacon b = new Beacon(time, count);
            System.out.println(String.format("Time: %s Channel: %s", time, currentChannel));
            System.out.println(String.format("Time: %s Channel: %s Count: %s Beacons: %s", time, currentChannel, count, currentSink.beacons.size()+1));
            if (currentSink.t!=null){
                create2ndBroadcastUsingPeriod(b, currentChannel, currentSink.t);
                setSinkAndChannel(pickNextChannel(), time);
                //If beacon is second received in stream
            } else if (currentSink.beacons.size()==1){
                //TODO deal with N=Null case with 2 beacons
                double period = calculatePossiblePeriods(currentSink.beacons.get(0), b, currentSink.N);
                currentSink.t=period;
                // generate first broadcast
                Time broadcast = createEarliestPossibleBroadcast(currentSink.beacons.get(0), b, currentChannel, period);
                if (currentSink.N!=null){
                    //  generate second broadcast
                    create2ndBroadcastUsingPreviousBroadcast(broadcast, period, currentChannel, currentSink.N);
                    deleteSink(currentChannel);
                    setSinkAndChannel(pickNextChannel(), time);
                } else {
                    //Generate callback to earliest possible beacon
                    Time nextBeaconTime = calculateChannelCallbackOfNextSeries(b, period);
                    createChannelSwitchCallBack(nextBeaconTime, currentChannel);
                    currentSink.beacons.add(b);
                    setSinkAndChannel(pickNextChannel(), time);
                }

            } else {
                if (waitedLongerThanMinPeriod(time, lastChannelSwitch, MIN_PERIOD) && b.n==1){
                    currentSink.N=b.n;
                    Time waited = time.subtract(lastChannelSwitch);
                    createChannelSwitchCallBack(time.add(waited),currentChannel);
                } else {
                    createChannelSwitchCallBack(time.add(MIN_PERIOD),currentChannel);
                }
                currentSink.beacons.add(b);
                int channel = tryPickDifferentChannel(currentChannel);
                if (channel!=currentChannel){
                    setSinkAndChannel(channel, time);
                }
            }



            output.send(0, new IntToken(currentChannel));

        }
    }

//    private Time getNextBroadcastTime(Time currentTime, ArrayList<Broadcast> broadcasts) throws IllegalActionException {
//        double minDifference = Double.MAX_VALUE;
//        double cTime = currentTime.getDoubleValue();
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

    private ArrayList<Broadcast> timeInBroadcasts(Time time, ArrayList<Broadcast> broadcasts){
        ArrayList<Broadcast> broadcastsAtTime = new ArrayList<Broadcast>();
        for(Broadcast b : broadcasts){
            if (time.getDoubleValue()==b.broadcastTime.getDoubleValue()){
                broadcastsAtTime.add(b);
            }
        }
        return broadcastsAtTime;
    }

    private double calculatePossiblePeriods(Beacon b1, Beacon b2, int N) {
        double diffTime = Math.abs(b1.t.getDoubleValue() - b2.t.getDoubleValue());
        double diffN = Math.abs(b1.n-b2.n);
        if (N==1 && b1.n==b2.n){
            return diffTime/12;
        }
        ArrayList<Double> possiblePeriods = new ArrayList<Double>();
        double period = Double.MAX_VALUE;
        int i = 0;
        while (period>MIN_PERIOD){
            period = diffTime/(((11+N)*i)+diffN);
            if (MIN_PERIOD<=period && period <=MAX_PERIOD){
                possiblePeriods.add(period);
            }
            i++;
        }
        if (possiblePeriods.size()>1){
            throw new IllegalStateException("More than one possible period");
        }
        if (possiblePeriods.size()==0){
            throw new IllegalStateException("No possible period.");
        }
        return possiblePeriods.get(0);
    }

    private ArrayList<Broadcast> fireBroadcasts(Time currentTime, ArrayList<Broadcast> scheduledBroadcasts) throws IllegalActionException {
        ArrayList<Broadcast> fired = new ArrayList<Broadcast>();
        for (Broadcast b : scheduledBroadcasts) {
            if ((b.broadcastTime.getDoubleValue() == currentTime.getDoubleValue()) && (b.cutoffTime.getDoubleValue() - getDirector().getModelTime().getDoubleValue() > 0)) {
                setChannel(b.channel, currentTime);
                System.out.println(String.format("Time: %s CutoffTime: %s Channel: %s Broadcasting!", getDirector().getModelTime(), b.cutoffTime, b.channel));
                feedbackOutput.send(0, new IntToken(666));
                try {
                    sinks.get(b.channel).numTransmitted++;
                } catch (NullPointerException ignored){}
                fired.add(b);
            }
        }
        return fired;

    }

    private int tryPickDifferentChannel(int currentChannel){
        for (Sink s: sinks.values()){
            if (s.channel!=currentChannel){
                return s.channel;
            }
        }
        System.out.println("1 channel left");
        return currentChannel;
    }

    private int pickNextChannel(){
        for(Sink s: sinks.values()){
            if (!channelHasPendingBroadcast(s.channel)){
                return s.channel;
            }
        }
        return currentChannel;
    }

    private boolean channelHasPendingBroadcast(int channel){
        for(Broadcast b: broadcasts){
            if (b.channel==channel){
                return true;
            }
        }
        return false;
    }

    private void setSinkAndChannel(int channel, Time time) throws IllegalActionException {
        currentSink = sinks.get(channel);
        setChannel(channel, time);
    }

    private void setChannel(int channel, Time time) throws IllegalActionException {
        System.out.println(String.format("Time: %s Switched from channel %s to channel %s", getDirector().getModelTime(), currentChannel, channel));
        currentChannel = channel;
        lastChannelSwitch = time;
        channelOutput.send(0, new IntToken(channel));
    }

    private Time createEarliestPossibleBroadcast(Beacon b1, Beacon b2, int channel, double t) throws IllegalActionException {
        Time broadcastTime = new Time(getDirector(), calculatePeriod(b1, b2)*(Math.min(b1.n, b2.n))).add(getDirector().getModelTime());
        setupBroadcastAndCallBack(broadcastTime, broadcastTime.add(t), channel, t);
        System.out.println("  1st broadcast.");
        return broadcastTime;
    }

    private void create2ndBroadcastUsingPeriod(Beacon b, int channel, double t) throws IllegalActionException {
        Time broadcast = new Time(getDirector(), (b.n * t)+b.t.getDoubleValue());
        Time deadline = broadcast.add(new Time(getDirector(), t));
        if (setupBroadcastAndCallBack(broadcast, deadline, channel, t)) {
            deleteSink(channel);
            System.out.println("  2nd broadcast w/ 3 beacons.");
        }
    }

    private void create2ndBroadcastUsingPreviousBroadcast(Time previousBroadcast, double t, int channel, Integer n) throws IllegalActionException {
        Time broadcast = new Time(getDirector(), previousBroadcast.getDoubleValue()+((11+n)*t));
        Time deadline = broadcast.add(new Time(getDirector(), t));
        if (setupBroadcastAndCallBack(broadcast, deadline, channel, t)) {
            deleteSink(channel);
            System.out.println("  2nd broadcast w/ 2 beacons.");
        }
    }



    //Boolean managed to schedule task
    private boolean setupBroadcastAndCallBack(Time broadcastTime, Time deadline, int channel, double t) throws IllegalActionException {
        ArrayList<Broadcast> bAtTime = timeInBroadcasts(broadcastTime, broadcasts);
        if (bAtTime.size()>0){
            if (deadline.subtract(broadcastTime).getDoubleValue()>microDelay){
                return setupBroadcastAndCallBack(broadcastTime.add(microDelay), deadline, channel, t);
            } else {
                System.out.println(String.format("Failed to schedule broadcast for channel: %s at time: %s:",channel, broadcastTime));
                return false;
            }
//            if (checkAllBroadcastsMeetDeadline(bAtTime, microDelay)){
//                broadcasts.removeAll(bAtTime);
//                for (Broadcast b: bAtTime){
//                    b.broadcastTime.add(microDelay);
//                }
//                broadcasts.addAll(bAtTime);
//                getDirector().fireAt(this, getLatestBroadcastTime(bAtTime));
//                return true;
//            } else {
//                return false;
//            }


        }
        Broadcast b = new Broadcast(channel, broadcastTime, deadline);
        getDirector().fireAt(this, broadcastTime);
        System.out.println(String.format("Preparing broadcast on channel: %s at time: %s w/ 2 beacons.", channel, broadcastTime));
        broadcasts.add(b);
        return true;
    }

    private Time getLatestBroadcastTime(ArrayList<Broadcast> broadcasts) throws IllegalActionException {
        double time = 0.0;
        for (Broadcast b: broadcasts){
            double value = b.broadcastTime.getDoubleValue();
            if (value>time){
                time = value;
            }
        }
        return new Time(getDirector(), time);
    }

    private boolean checkAllBroadcastsMeetDeadline(ArrayList<Broadcast> broadcasts, double delay){
        for (Broadcast b: broadcasts){
            if (b.broadcastTime.getDoubleValue()+delay<b.cutoffTime.getDoubleValue()){
                return false;
            }
        }
        return true;
    }


    public double calculatePeriod(Beacon b1, Beacon b2) throws IllegalActionException {
        double numPeriods = Math.abs(b1.n-b2.n);
        double period;
        if (numPeriods==0 && b1.n == 1){
            //Special case for N=1
            period =  Math.abs((b1.t.subtract(b2.t)).getDoubleValue())/12;
        } else {
            period =  Math.abs((b1.t.subtract(b2.t)).getDoubleValue())/numPeriods;
        }
        System.out.println(String.format("Calculated period: %s for channel: %s", period, currentChannel));
        return period;
    }

    private ChannelSwitch timeInChannelSwitches(Time time, ArrayList<ChannelSwitch> cSwitches){
        for(ChannelSwitch cs : cSwitches){
            if (time.getDoubleValue()==cs.time.getDoubleValue()){
                return cs;
            }
        }
        return null;
    }

    private void createChannelSwitchCallBack(Time t, int c) throws IllegalActionException {
        if(timeInBroadcasts(t, broadcasts).size()>0){
            t.add(MIN_PERIOD);
        }
        if(timeInChannelSwitches(t, channelSwitches)!=null){
            t.add(MIN_PERIOD);
        }
        System.out.println(String.format("Time: %s created channel SC at time %s for channel %s", getDirector().getModelTime(), t, c));
        channelSwitches.add(new ChannelSwitch(t,c));
        getDirector().fireAt(this, t);
    }


    private Time calculateChannelCallbackOfNextSeries(Beacon b, double period) throws IllegalActionException {
        double listenTime = b.t.getDoubleValue()+((11+b.n)*period);
        return new Time(getDirector(), listenTime);
    }

    private boolean waitedLongerThanMinPeriod(Time time, Time lastChannelSwitch, double minPeriod) {
        return ((time.subtract(lastChannelSwitch)).getDoubleValue()-minPeriod)>0;
    }

    private void deleteSink(int channel){
        currentSink = null;
        sinks.remove(channel);
    }
}
