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
    protected ArrayList<Time> firingTimes = new ArrayList<Time>();
    protected int currentChannel = 11;
    protected int[] channels = {11,12,13,14,15};
    protected HashMap<Integer, Sink> sinks = new HashMap<Integer,Sink>();
    protected Sink currentSink;
    protected ArrayList<Broadcast> broadcasts = new ArrayList<Broadcast>();

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
        // If any broadcasts need to be done
        if (firingTimes.contains(time)) {
            fireBroadcasts(time);
        }
        //TODO Context switches

        try {
            Token countToken = input.get(0);
            int count = ((IntToken) countToken).intValue();
            Beacon b = new Beacon(time, count);
            System.out.println(String.format("Time: %s Channel: %s Count: %s Beacons: %s", time, currentChannel, count, currentSink.beacons.size()));
            if (currentSink.t!=null){
                create2ndBroadcastUsingPeriod(currentChannel, b, currentSink.t);
                setSinkAndChannel(pickNextChannel());
            //If beacon is second received in stream
            } else if (currentSink.beacons.size()==1){
                // TODO
                if (currentSink.N!=null){
                    Time period = new Time(getDirector(), calculatePeriod(currentSink.beacons.get(0), currentSink.beacons.get(1)));
                    currentSink.t = period.getDoubleValue();
                    createFirstBroadcast(currentSink.beacons, period);
                    // attempt to generate second broadcast
                    calculatePossiblePeriods(currentSink.beacons.get(0), b, currentSink.N);
                } else {

                }
                currentSink.beacons.add(b);
                // TODO create callback to same channel to listen to 3rd packet
                setSinkAndChannel(pickNextChannel());
            } else {
                if (waitedLongerThanMinPeriod(lastChannelSwitch, time, MIN_PERIOD)){
                    currentSink.N=b.n;
                }
                currentSink.beacons.add(b);
                // TODO create callback to same channel in minPeriod
                setSinkAndChannel(pickNextChannel());
            }

//                if (currentSink.beacons.size()==2){
//                    //Generate first broadcast from 2 beacons
//
//                    ArrayList<Beacon> beacons = new ArrayList<Beacon>(currentSink.beacons);
//                    Time period = new Time(getDirector(), calculatePeriod(beacons.get(0), beacons.get(1)));
//                    currentSink.t = period.getDoubleValue();
//                    Time broadcastTime = createFirstBroadcast(beacons, period);
//                    //Special case where we can calculate second firing
//                    //TODO generalise to work for any interval of cycles
//                    if (beacons.get(0).n==1 && beacons.get(1).n==1){
//                        Time broadcastTime2 = broadcastTime.add(12 * period.getDoubleValue());
//                        broadcasts.add(new Broadcast(currentChannel, broadcastTime2, broadcastTime2.add(period)));
//                        firingTimes.add(broadcastTime2);
//                        getDirector().fireAt(this, broadcastTime2);
//                        System.out.println(String.format("Preparing final broadcast at time: %s channel: %s w/ 2 beacons.", broadcastTime2, currentChannel));
//                        sinks.remove(currentChannel);
//                    }
//                    setSinkAndChannel(pickNextChannel());
//
//                } else if(currentSink.beacons.size()==3){
//
//                }


            output.broadcast(new IntToken(currentChannel));

        } catch (NoTokenException e) {}

    }

    private Double[] calculatePossiblePeriods(Beacon b1, Beacon b2, int N) {
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
        //TODO FINISH
        return (Double[]) possiblePeriods.toArray();
    }

    private boolean waitedLongerThanMinPeriod(Time lastChannelSwitch, Time time, double minPeriod) {
        return ((time.subtract(lastChannelSwitch)).getDoubleValue()-minPeriod)>0;
    }


    private Time createFirstBroadcast(ArrayList<Beacon> beacons, Time period) throws IllegalActionException {
        Time broadcastTime = calculateBroadcastTime(beacons.get(0), beacons.get(1));
        broadcasts.add(new Broadcast(currentChannel, broadcastTime, broadcastTime.add(period)));
        getDirector().fireAt(this, broadcastTime);
        firingTimes.add(broadcastTime);
        System.out.println(String.format("Preparing broadcast at time: %s channel: %s w/ 2 beacons.", broadcastTime, currentChannel));
        return broadcastTime;
    }

    private void create2ndBroadcastUsingPeriod(int currentChannel, Beacon b, Double t) throws IllegalActionException {
        Time finalBroadcast = new Time(getDirector(), (b.n * currentSink.t)+b.t.getDoubleValue());
        getDirector().fireAt(this, finalBroadcast);
        Time broadcastDeadline = finalBroadcast.add(new Time(getDirector(), currentSink.t));
        broadcasts.add(new Broadcast(currentChannel, finalBroadcast, broadcastDeadline));
        firingTimes.add(finalBroadcast);
        sinks.remove(currentChannel);
        System.out.println(String.format("Preparing final broadcast at time: %s on channel: %s w/ >2 beacons.", finalBroadcast, currentChannel));
    }


    private void fireBroadcasts(Time currentTime) throws IllegalActionException {
        ArrayList<Broadcast> fired = new ArrayList<Broadcast>();
        for (Broadcast b : broadcasts) {
            if ((b.broadcastTime.getDoubleValue() == currentTime.getDoubleValue()) && (b.cutoffTime.getDoubleValue() - getDirector().getModelTime().getDoubleValue() > 0)) {
                System.out.println(String.format("Time: %s CutoffTime: %s Channel: %s Broadcasting!", getDirector().getModelTime(), b.cutoffTime, b.channel));
                setChannel(b.channel);
                feedbackOutput.broadcast(new IntToken(666));
                try {
                    sinks.get(b.channel).numTransmitted++;
                } catch (NullPointerException ignored){}
                fired.add(b);
            }
        }
        for (Broadcast b: fired){
            broadcasts.remove(b);
        }
        firingTimes.remove(currentTime);
    }

    private int pickOtherChannel(int currentChannel){
        for(Sink s: sinks.values()){
            if (!channelHasPendingBroadcast(s.channel) && currentChannel!=s.channel){
                return s.channel;
            }
        }
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
        setSink(channel);
        setChannel(channel);
    }

    private void setSink(int channel){
        if (currentSink.numTransmitted<=2) {
            //If completed
            sinks.put(currentSink.channel, currentSink);
        } else {
            sinks.remove(currentSink.channel);
        }
        currentSink = sinks.get(channel);
    }

    private void setChannel(int channel) throws IllegalActionException {
        System.out.println(String.format("Switched from channel %s to channel %s", currentChannel, channel));
        currentChannel = channel;
        channelOutput.broadcast(new IntToken(channel));
    }

    private void toggle(boolean[] b, int index) {
        b[index] = !b[index];
    }

    public Time calculateBroadcastTime(Beacon b1, Beacon b2) throws IllegalActionException {
        return new Time(getDirector(), calculatePeriod(b1, b2)*(Math.min(b1.n, b2.n))).add(getDirector().getModelTime());
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
}
