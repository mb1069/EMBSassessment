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
            if(!channelHasPendingBroadcast(currentChannel)){
                int count = ((IntToken) countToken).intValue();
                currentSink.addBeacon(new Beacon(time, count));
                System.out.println(String.format("Time: %s Channel: %s Count: %s Beacons: %s", time, currentChannel, count, currentSink.beacons.size()));
                //TODO if beacon1.n =1 listen to someting else for 12*minPeriod then switch back
                if (currentSink.beacons.size()==2){
                    //Generate first broadcast from 2 beacons
                    ArrayList<Beacon> beacons = new ArrayList<Beacon>(currentSink.beacons);
                    Time period = new Time(getDirector(), calculatePeriod(beacons.get(0), beacons.get(1)));
                    currentSink.t = period.getDoubleValue();
                    Time broadcastTime = generateFirstBroadcast(beacons, period);
                    //Special case where we can calculate second firing
                    //TODO generalise to work for any interval of cycles
                    if (beacons.get(0).n==1 && beacons.get(1).n==1){
                        Time broadcastTime2 = broadcastTime.add(12 * period.getDoubleValue());
                        broadcasts.add(new Broadcast(currentChannel, broadcastTime2, broadcastTime2.add(period)));
                        firingTimes.add(broadcastTime2);
                        getDirector().fireAt(this, broadcastTime2);
                        System.out.println(String.format("Preparing final broadcast at time: %s channel: %s w/ 2 beacons.", broadcastTime2, currentChannel));
                        sinks.remove(currentChannel);
                    }
                    setSinkAndChannel(pickNextChannel());

                } else if(currentSink.beacons.size()==3){
                    System.out.println(String.format("Final count: %s, period: %s", count, currentSink.t));
                    Time broadcastTime = new Time(getDirector(), (count * currentSink.t)+time.getDoubleValue());
                    getDirector().fireAt(this, broadcastTime);
                    Time broadcastDeadline = broadcastTime.add(new Time(getDirector(), currentSink.t));
                    broadcasts.add(new Broadcast(currentChannel, broadcastTime, broadcastDeadline));
                    firingTimes.add(broadcastTime);
                    sinks.remove(currentChannel);
                    System.out.println(String.format("Preparing final broadcast at time: %s on channel: %s w/ >2 beacons.", broadcastTime, currentChannel));
                    setSinkAndChannel(pickNextChannel());
                }


                output.broadcast(new IntToken(currentChannel));
            }
        } catch (NoTokenException e) {}

    }

    private Time generateFirstBroadcast(ArrayList<Beacon> beacons, Time period) throws IllegalActionException {
        Time broadcastTime = calculateBroadcastTime(beacons.get(0), beacons.get(1));
        System.out.println(String.format("Calculated: Period: %s, broadCastTime: %s", period, broadcastTime));
        broadcasts.add(new Broadcast(currentChannel, broadcastTime, broadcastTime.add(period)));
        getDirector().fireAt(this, broadcastTime);
        firingTimes.add(broadcastTime);
        System.out.println(String.format("Preparing broadcast at time: %s channel: %s w/ 2 beacons.", broadcastTime, currentChannel));
        return broadcastTime;
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
        if (numPeriods==0 && b1.n == 1){
            //Special case for N=1
            return Math.abs((b1.t.subtract(b2.t)).getDoubleValue())/12;
        }
        return Math.abs((b1.t.subtract(b2.t)).getDoubleValue())/numPeriods;
    }
}
