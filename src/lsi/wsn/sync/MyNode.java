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
        Time currentTime = getDirector().getModelTime();
        // If any broadcasts need to be done
        if (firingTimes.contains(currentTime)) {
            fireBroadcasts(currentTime);

            return;
        }

        //TODO Context switches

        try {
            Token countToken = input.get(0);
            if(!channelHasPendingBroadcast(currentChannel)){
                Time time = getDirector().getModelTime();
                int count = ((IntToken) countToken).intValue();
                currentSink.addBeacon(new Beacon(time, count));
                System.out.println(String.format("Time: %s Double time: %s Channel: %s Count %s Beacons: %s", time, time.getDoubleValue(), currentChannel, count, currentSink.beacons.size()));

                if (currentSink.beacons.size()==2){
                    ArrayList<Beacon> beacons = currentSink.beacons;

                    Time broadcastTime = calculateBroadcastTime(beacons.get(0), beacons.get(1));
                    Time period = new Time(getDirector(), calculatePeriod(beacons.get(0), beacons.get(1)));
                    currentSink.t = period.getDoubleValue();
                    System.out.println(String.format("Calculated: Period: %s, broadCastTime: %s", period, broadcastTime));
                    Time broadcastDeadline = broadcastTime.add(period);
                    broadcasts.add(new Broadcast(currentChannel, broadcastTime, broadcastDeadline));
                    getDirector().fireAt(this, broadcastTime);
                    firingTimes.add(broadcastTime);
                    setSinkAndChannel(pickNextChannel());
                    System.out.println(String.format("Preparing broadcast at time: %s channel: %s w/ 2 beacons.", broadcastTime, currentChannel));

                } else if(currentSink.beacons.size()==3){
                    System.out.println(String.format("Final count: %s, period: %s", count, currentSink.t));
                    Time broadcastTime = new Time(getDirector(), (count * currentSink.t)+time.getDoubleValue());
                    getDirector().fireAt(this, broadcastTime);
                    Time broadcastDeadline = broadcastTime.add(new Time(getDirector(), currentSink.t));
                    broadcasts.add(new Broadcast(currentChannel, broadcastTime, broadcastDeadline));
                    firingTimes.add(broadcastTime);
                    sinks.remove(currentChannel);
                    setSinkAndChannel(pickNextChannel());
                    System.out.println(String.format("Preparing final broadcast at time: %s on channel: %s w/ >2 beacons.", broadcastTime, currentChannel));
                }


                output.broadcast(new IntToken(currentChannel));
            }
        } catch (NoTokenException e) {}

    }

    private void fireBroadcasts(Time currentTime) throws IllegalActionException {
        ArrayList<Broadcast> fired = new ArrayList<Broadcast>();
        for (Broadcast b : broadcasts) {
            if ((b.broadcastTime.getDoubleValue() == currentTime.getDoubleValue()) && (b.cutoffTime.getDoubleValue() - getDirector().getModelTime().getDoubleValue() > 0)) {
                System.out.println(String.format("Time: %s CutoffTime: %s Channel: %s Broadcasting!", getDirector().getModelTime(), b.cutoffTime, currentChannel));
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
        return Math.abs((b1.t.subtract(b2.t)).getDoubleValue())/numPeriods;
    }

}
