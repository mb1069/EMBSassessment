package lsi.wsn.sync;
//   lsi.wsn.sync.MyNode
import ptolemy.actor.NoTokenException;
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
    protected int[] channels = {11,12,13,14,15};
    protected HashMap<Integer, Sink> sinks = new HashMap<Integer,Sink>();
    protected Sink currentSink;
    protected ArrayList<Broadcast> broadcasts = new ArrayList<Broadcast>();
    protected double microDelay = 0.01;
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
    }

    @Override
    public void fire() throws IllegalActionException {
        super.fire();
        Time time = getDirector().getModelTime();
        if (time.getDoubleValue()==8.0){
            System.out.println();
        }
        // If any broadcasts need to be done
        ArrayList<Broadcast> broadcastsAtTime = timeInBroadcasts(time, broadcasts);
        if (broadcastsAtTime.size()>0) {
            int saveChannel = currentChannel;
            for(Broadcast b: fireBroadcasts(time, broadcastsAtTime)){
                broadcasts.remove(b);
            }
            setSinkAndChannel(saveChannel);
            return;
        }

        ChannelSwitch cs = timeInChannelSwitches(time, channelSwitches);
        if (cs!=null){
            setSinkAndChannel(cs.channel);
        }

        if (input.hasToken(0)){
            Token countToken = input.get(0);
            int count = ((IntToken) countToken).intValue();
            Beacon b = new Beacon(time, count);
            System.out.println(String.format("Time: %s Channel: %s Count: %s Beacons: %s", time, currentChannel, count, currentSink.beacons.size()+1));
            if (currentSink.t!=null){
                create2ndBroadcastUsingPeriod(b, currentChannel, currentSink.t);
                setSinkAndChannel(pickNextChannel());
                //If beacon is second received in stream
            } else if (currentSink.beacons.size()==1){
                double period = calculatePossiblePeriods(currentSink.beacons.get(0), b, currentSink.N);
                currentSink.t=period;
                // generate first broadcast
                Time broadcast = createEarliestPossibleBroadcast(currentSink.beacons.get(0), b, currentChannel, period);
                if (currentSink.N!=null){
                    //  generate second broadcast
                    create2ndBroadcastUsingPreviousBroadcast(broadcast, period, currentChannel, currentSink.N);
                    deleteSink(currentChannel);
                    setSinkAndChannel(pickNextChannel());
                } else {
                    //Generate callback to earliest possible beacon
                    Time nextBeaconTime = calculateChannelCallbackOfNextSeries(b, period);
                    createChannelSwitchCallBack(nextBeaconTime, currentChannel);
                    currentSink.beacons.add(b);
                    setSinkAndChannel(pickNextChannel());
                }

            } else {
                if (waitedLongerThanMinPeriod(lastChannelSwitch, time, MIN_PERIOD)){
                    currentSink.N=b.n;
                }
                currentSink.beacons.add(b);
                // TODO create callback to same channel in minPeriod
                createChannelSwitchCallBack(time.add(MIN_PERIOD),currentChannel);
                setSinkAndChannel(tryPickDifferentChannel(currentChannel));
            }



            output.send(0, new IntToken(currentChannel));

        }
    }

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
        return possiblePeriods.get(0);
    }

    private ArrayList<Broadcast> fireBroadcasts(Time currentTime, ArrayList<Broadcast> scheduledBroadcasts) throws IllegalActionException {
        ArrayList<Broadcast> fired = new ArrayList<Broadcast>();
        for (Broadcast b : scheduledBroadcasts) {
            if ((b.broadcastTime.getDoubleValue() == currentTime.getDoubleValue()) && (b.cutoffTime.getDoubleValue() - getDirector().getModelTime().getDoubleValue() > 0)) {
                setChannel(b.channel);
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

    private void setSinkAndChannel(int channel) throws IllegalActionException {
        currentSink = sinks.get(channel);
        setChannel(channel);
    }

    private void setChannel(int channel) throws IllegalActionException {
        System.out.println(String.format("Time: %s Switched from channel %s to channel %s", getDirector().getModelTime(), currentChannel, channel));
        currentChannel = channel;
        channelOutput.send(0, new IntToken(channel));
    }

    private Time createEarliestPossibleBroadcast(Beacon b1, Beacon b2, int channel, Double t) throws IllegalActionException {
        Time broadcastTime = new Time(getDirector(), calculatePeriod(b1, b2)*(Math.min(b1.n, b2.n))).add(getDirector().getModelTime()).add(microDelay);
        setupBroadcastAndCallBack(broadcastTime, broadcastTime.add(t), channel);
        System.out.println(String.format("Preparing broadcast at time: %s channel: %s w/ 2 beacons.", broadcastTime, currentChannel));
        return broadcastTime;
    }

    private void create2ndBroadcastUsingPeriod(Beacon b, int channel, Double t) throws IllegalActionException {
        Time broadcastTime = new Time(getDirector(), (b.n * t)+b.t.getDoubleValue()).add(microDelay);
        Time broadcastDeadline = broadcastTime.add(new Time(getDirector(), t));
        setupBroadcastAndCallBack(broadcastTime, broadcastDeadline, channel);
        deleteSink(channel);
        System.out.println(String.format("Preparing final broadcast at time: %s on channel: %s w/ >2 beacons.", broadcastTime, channel));
    }

    private Time create2ndBroadcastUsingPreviousBroadcast(Time previousBroadcast, Double t, int channel, Integer n) throws IllegalActionException {
        Time broadcastTime = new Time(getDirector(), previousBroadcast.getDoubleValue()+((11+n)*t)).add(microDelay);
        setupBroadcastAndCallBack(broadcastTime, broadcastTime.add(t), channel);
        deleteSink(channel);
        System.out.println(String.format("Preparing broadcast at time: %s channel: %s w/ 2 beacons.", broadcastTime, channel));
        return broadcastTime;
    }

    private void setupBroadcastAndCallBack(Time finalBroadcast, Time broadcastDeadline, int channel) throws IllegalActionException {
        getDirector().fireAt(this, finalBroadcast);
        broadcasts.add(new Broadcast(channel, finalBroadcast, broadcastDeadline));
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
        System.out.println(String.format("Time: %s created channel SC at time %s for channel %c", getDirector().getModelTime(), t, c));
        channelSwitches.add(new ChannelSwitch(t,c));
        getDirector().fireAt(this, t);
    }


    private Time calculateChannelCallbackOfNextSeries(Beacon b, double period) throws IllegalActionException {
        double listenTime = b.t.getDoubleValue()+((11+b.n)*period);
        return new Time(getDirector(), listenTime);
    }

    private boolean waitedLongerThanMinPeriod(Time lastChannelSwitch, Time time, double minPeriod) {
        return ((time.subtract(lastChannelSwitch)).getDoubleValue()-minPeriod)>0;
    }

    private void deleteSink(int channel){
        currentSink = null;
        sinks.remove(channel);
    }
}
