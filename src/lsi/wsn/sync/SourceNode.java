package lsi.wsn.sync;

import ptolemy.actor.TypedAtomicActor;
import ptolemy.actor.TypedIOPort;
import ptolemy.actor.util.Time;
import ptolemy.data.IntToken;
import ptolemy.data.Token;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;

import java.util.*;

/**
 * Class representing the sourceNode communicating with all sinks
 */
public class SourceNode extends TypedAtomicActor {
    protected TypedIOPort input; // input port
    protected TypedIOPort broadcastOutput; // output port to communicate to sinks
    protected TypedIOPort channelOutput; // output port to switch the channel
    protected int currentChannel = 11; // initial channel
    protected HashMap<Integer, Sink> sinks = new HashMap<Integer, Sink>(); // hashmap of all known sinks organised by channel
    protected ArrayList<Broadcast> broadcasts = new ArrayList<Broadcast>(); // list of all scheduled broadcasts to channels
    protected ArrayList<ChannelSwitch> channelSwitches = new ArrayList<ChannelSwitch>(); // list of scheduled channelSwitch events

    protected double microDelay = 0.001; // microDelay used to co-ordinate channel switch and broadcast events
    protected double lastChannelSwitch; // time since last channel switch in

    public static final int N_MAX = 10; // maximum number of beacons per cycle
    public static final double T_MIN = 0.5; // minimum interval between beacons
    public static final double T_MAX = 1.5; // maximum interval between periods


    public SourceNode(CompositeEntity container, String name) throws IllegalActionException, NameDuplicationException {
        super(container, name);
        // Initialise all ports
        input = new TypedIOPort(this, "input", true, false);
        broadcastOutput = new TypedIOPort(this, "broadcast", false, true);
        channelOutput = new TypedIOPort(this, "channel", false, true);
        // Initialise sinks in hashmap
        for (int channel : new int[]{11,12,13,14,15}) {
            sinks.put(channel, new Sink(channel));
        }
    }

    @Override
    public void initialize() throws IllegalActionException {
        super.initialize();
        //Set inital channel
        setChannel(currentChannel, lastChannelSwitch);
        lastChannelSwitch = getDirector().getModelTime().getDoubleValue();
    }



    @Override
    public void fire() throws IllegalActionException {
        super.fire();

        double currentTime = round(getDirector().getModelTime().getDoubleValue());

        // Send any broadcasts scheduled at currentTime
        ArrayList<Broadcast> broadcastsAtTime = getBroadcastsAtTime(currentTime, broadcasts);
        if (broadcastsAtTime.size() > 0) {

            int channel = currentChannel; //Save channel to restore state following broadcasts
            for (Broadcast b : fireBroadcasts(currentTime, broadcastsAtTime)) {
                broadcasts.remove(b); // Remove broadcasts as they are sent
            }
            createChannelSwitchCallBack(currentTime+microDelay, channel); // Create callback to restore state
        }

        // Send any channel switches scheduled at currentTime
        ChannelSwitch cs = getChannelSwitchesAtTime(currentTime, channelSwitches);
        if (cs!=null && (cs.getChannel()!=currentChannel)){
            setChannel(cs.getChannel(), currentTime);
        }

        // Process 1 token if available
        if (input.hasToken(0) && sinks.containsKey(currentChannel)) {
            Token countToken = input.get(0);
            Beacon b = new Beacon(currentTime, ((IntToken) countToken).intValue());
            sinks.get(currentChannel).getBeacons().add(b);
            ArrayList<Beacon> beacons = sinks.get(currentChannel).getBeacons();
            int numBeacons = beacons.size();

            System.out.println(String.format("Time: %s Channel: %s Received beacon n: %s w/ beaconN: %s ", currentTime, currentChannel, beacons.size(), b.n));

            // If time spent on channel > minimum interval between beacons and no beacons have been received before this one during that time,
            // this beacon is the first of this cycle, and therefore the channel's N value = this beacon's n value.
            if (currentTime-lastChannelSwitch>T_MAX && (currentTime - sinks.get(currentChannel).getLastBeaconTime() >T_MAX)){
                sinks.get(currentChannel).setN(b.n);
            }

            // Record arrival time for subsequent beacons
            sinks.get(currentChannel).setLastBeaconTime(currentTime);

            //If last two beacons have consecutive numbers and arrived within diffN*T_MAX
            // If beacons arrive within T_MAX * (b1.n-b2.n) -> calculate interval between beacons and therefore period of channel
            if (numBeacons>1){
                int diffN = beacons.get(numBeacons-2).n-beacons.get(numBeacons-1).n;
                double diffT = round(Math.abs(beacons.get(numBeacons-2).t-beacons.get(numBeacons-1).t));
                // Check beacons are from the same cycle
                if (diffN>0 && diffT<=(T_MAX*diffN)){
                    sinks.get(currentChannel).setT(round(diffT/diffN));
                    createNextPossibleBroadcast(b, currentChannel, sinks.get(currentChannel).getT(), currentTime);
                }
            }

            // Special case for beacons with same n and channel with known N -> establish T, and create broadcasts using following if statement
            if (numBeacons>1 && beaconsHaveSameN(beacons) && sinks.get(currentChannel).getN()!=null && latestBeaconsWithinMaxCyclePeriod(beacons) && sinks.get(currentChannel).getPlannedBroadcasts()<2){
                sinks.get(currentChannel).setT(round((Math.abs(beacons.get(numBeacons-1).t - beacons.get(numBeacons-2).t))/(11+b.n)));
            }

            // If both N and T are known, can create the 2nd broadcast
            if (sinks.get(currentChannel).getPlannedBroadcasts()<2 && sinks.get(currentChannel).getN()!=null && sinks.get(currentChannel).getT()!=null){
                createNextPossibleBroadcast(b, currentChannel, sinks.get(currentChannel).getT(), currentTime);
                if (sinks.get(currentChannel).getPlannedBroadcasts()<2) { // if more broadcasts required
                    create2ndBroadcast(b, currentChannel, sinks.get(currentChannel).getT(), sinks.get(currentChannel).getN());
                }
            }



            // If more than 2 beacons available and broadcasts needed, can attempt to generate possible combinations of T and N
            if (numBeacons > 1 && sinks.get(currentChannel).getPlannedBroadcasts()<2) {

                Set<SinkProperties> sps;

                // if any properties of sink are not known
                if (sinks.get(currentChannel).getT()==null || sinks.get(currentChannel).getN()==null) {
                    // Generate compatible combinations of T and N for latest pair of beacons
                    sps = generatePossibleSinkProperties(beacons.get(numBeacons - 2), beacons.get(numBeacons - 1), sinks.get(currentChannel).getN(), sinks.get(currentChannel).getT());
                } else {
                    //Case where all data is already known
                    sps = new HashSet<SinkProperties>();
                    sps.add(new SinkProperties(sinks.get(currentChannel).getN(), sinks.get(currentChannel).getT()));
                }

                if (sinks.get(currentChannel).getPossibleProperties() == null) {
                    sinks.get(currentChannel).setPossibleProperties(sps); // if first set of properties store in Sink
                } else {
                    // otherwise intersect with existing properties to find a tuple (T,N) that is compatible with all beacons received from the Sink
                    sinks.get(currentChannel).setPossibleProperties(intersection(sinks.get(currentChannel).getPossibleProperties(), sps));
                }

                // If only 1 possible tuple, store in Sink and create any remaining broadcasts
                if (sinks.get(currentChannel).getPossibleProperties().size() == 1) {
                    SinkProperties sp = sinks.get(currentChannel).getPossibleProperties().iterator().next();
                    sinks.get(currentChannel).setN(sp.getN());
                    sinks.get(currentChannel).setT(sp.getT());
                    double broadcast = createNextPossibleBroadcast(b, currentChannel, sp.getT(), currentTime);
                    if (sinks.get(currentChannel).getPlannedBroadcasts()<2) { // if more broadcasts required
                        create2ndBroadcast(broadcast, currentChannel, sp.getT(), sp.getN());
                    }
                }
            }

            // Pick next channel to switch to
            int newChannel = pickNextChannel(currentChannel, currentTime);
            if (newChannel!=currentChannel) {
                // If broadcasts are all scheduled for channel, do not create callback to return to current channel
                if (sinks.get(currentChannel).getPlannedBroadcasts()==2){
                    setChannel(newChannel, currentTime);

                    // If one more broadcast to go and 2 known beacons, create callback to earliest possible beacon of next cycle
                } else if (numBeacons==2 && sinks.get(currentChannel).getPlannedBroadcasts()<2){
                    double t = getEarliestTimeLastBeaconOfNextCycle(sinks.get(currentChannel).getPossibleProperties(), b);
                    createChannelSwitchCallBack(t, currentChannel);
                    setChannel(newChannel, currentTime);

                    // Quick switchback to receive next beacon in current channel
                } else if (beacons.size()==1 && b.n!=1){
                    createChannelSwitchCallBack(currentTime+(T_MIN-microDelay), currentChannel);
                    setChannel(newChannel, currentTime);

                    // Long sleep if channel about to enter sleep period
                } else if (beacons.size()==1 && b.n==1){
                    createChannelSwitchCallBack(currentTime+(11*(T_MIN-microDelay)), currentChannel);
                    setChannel(newChannel, currentTime);
                }
            }
        }

    }

    /**
     * Check if all beacons in array have same value N.
     * @param beacons: list of all beacons to verify
     * @return true if all beacons have same n, false if any pair of beacons have a different n
     */
    private boolean beaconsHaveSameN(ArrayList<Beacon> beacons) {
        int n = beacons.get(0).n;
        for (Beacon b: beacons){
            if (b.n!=n){
                return false;
            }
        }
        return true;
    }

    /**
     * Check if latest 2 beacons arrived in 2 consecutive cycles
     * @param beacons: list of known beacons for sink
     * @return : true if beacons arrived in consecutive cycles, false otherwise
     */
    private boolean latestBeaconsWithinMaxCyclePeriod(ArrayList<Beacon> beacons) {
        int numBeacons = beacons.size();
        double timeDiff = Math.abs(beacons.get(numBeacons - 1).t - beacons.get(numBeacons - 2).t);
        double nDiff = (beacons.get(numBeacons-2).n-beacons.get(numBeacons-1).n);
        return timeDiff<=(T_MAX*(11+nDiff));
    }


    /**
     * Calculate the earliest time at which the last beacon of the following cycle may occur
     * @param setSP: set of all possible Sink properties (T,N) given known beacons
     * @param latestBeacon: latest beacon received on that channel
     * @return earliest time (from the start of the simulation) at which the last beacon of the next cycle may occur
     */
    private double getEarliestTimeLastBeaconOfNextCycle(Set<SinkProperties> setSP, Beacon latestBeacon){
        double shortestPeriod = Double.MAX_VALUE;
        int possibleN = 0;
        for (SinkProperties sp: setSP){
            double potentialPeriod = (11+sp.getN())*sp.getT();
            if (potentialPeriod<shortestPeriod){
                shortestPeriod = potentialPeriod;
                possibleN = sp.getN();
            }
        }
        return round(latestBeacon.t+((11+possibleN)*(shortestPeriod/(11+possibleN))));
    }

    /**
     * Generates a set of possible sink properties (T,N) which are compatible with the given pair of beacons b1 & b2 and any known information N or T
     * @param b1: first beacon
     * @param b2: second beacon
     * @param N: if not null, N value of sink
     * @param T: if not null, T value of sink
     * @return : set of all possible values for Sink
     */
    private HashSet<SinkProperties> generatePossibleSinkProperties(Beacon b1, Beacon b2, Integer N, Double T) {
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
                        if (T == null) {
                            sps.add(new SinkProperties(n, round(period)));
                        } else if (round(period)==T){
                            sps.add(new SinkProperties(n, round(period)));
                        }
                    } else if (n==N){
                        sps.add(new SinkProperties(n, round(period)));
                    }
                }
            }
        }
        return sps;
    }

    /**
     * Rounding method used to adjust for any rounding errors in ptolemy
     * @param x: value to round
     * @return x rounded to 3DP
     */
    private double round(double x){
        double factor = 1e3;
        x+=0.0001;
        return Math.floor(x * factor) / factor;
    }

    /**
     * Returns a list of all broadcasts objects scheduled at time
     * @param time: time to compare to broadcast's time
     * @param broadcasts: list of all broadcasts
     * @return ArrayList of broadcasts scheduled for time
     */
    private ArrayList<Broadcast> getBroadcastsAtTime(double time, ArrayList<Broadcast> broadcasts) {
        ArrayList<Broadcast> broadcastsAtTime = new ArrayList<Broadcast>();
        for (Broadcast b : broadcasts) {
            if (time == b.getBroadcastTime()) {
                broadcastsAtTime.add(b);
            }
        }
        return broadcastsAtTime;
    }

    /**
     * Send broadcasts on designated channel and record transmission
     * @param currentTime: time at which broadcast is sent
     * @param scheduledBroadcasts: arrayList of all broadcasts to be done at current time
     * @return ArrayList of broadcasts that have been sent
     * @throws IllegalActionException
     */
    private ArrayList<Broadcast> fireBroadcasts(double currentTime, ArrayList<Broadcast> scheduledBroadcasts) throws IllegalActionException {
        ArrayList<Broadcast> fired = new ArrayList<Broadcast>();
        for (Broadcast b : scheduledBroadcasts) {
            if ((b.getDeadline() - getDirector().getModelTime().getDoubleValue() > 0)) {
                setChannel(b.getChannel(), currentTime);
                System.out.println(String.format("Time: %s CutoffTime: %s Channel: %s Broadcasting!", getDirector().getModelTime(), b.getDeadline(), b.getChannel()));
                broadcastOutput.send(0, new IntToken(666));
                try {
                    sinks.get(b.getChannel()).incrementNumTransmitted();
                } catch (NullPointerException ignored) {}
                fired.add(b);
            }
        }
        return fired;

    }

    /**
     * Pick next channel to listen to following certain criteria
     *      Don't pick the same channel as the current one
     *      Don't pick channels with impending channel switches to that channel (indication of channel in sleep status or no listening required until the channel switch (e.g: catch last beacon of next cycle)
     *      Don't pick channels that do not require additional broadcasts (surplus to requirements)
     * @param currentChannel: integer representing current channel
     * @param currentTime: double representing current time
     * @return integer representing next channel to switch to
     */
    private int pickNextChannel(int currentChannel, double currentTime) {
        for (Sink s : sinks.values()) {
            if (!channelHasPendingSC(s.getChannel(), currentTime) && sinks.get(s.getChannel()).getPlannedBroadcasts()<2 && s.getChannel()!=currentChannel) {
                return s.getChannel();
            }
        }
        return currentChannel;
    }


    /**
     * Verifies whether any channel switches towards a given channel have been scheduled
     * @param channel: channel to which channel switches are scheduled
     * @param currentTime: time from which to establish boundary criteria
     * @return true if channel switch towards channel will occur within T_MAX, false otherwise
     */
    private boolean channelHasPendingSC(int channel, double currentTime) {
        for (ChannelSwitch sc : channelSwitches) {
            if (sc.getChannel() == channel && sc.getTime()-currentTime>T_MAX) {
                return true;
            }
        }
        return false;
    }

    /**
     * Set the current channel to listen to and change sink context to match channel
     * @param channel: channel to switch to
     * @param time: double representing time at which channel switch is occuring
     * @throws IllegalActionException
     */
    private void setChannel(int channel, double time) throws IllegalActionException {
        // Remove channelSwitch from list of channel switches
        ArrayList<ChannelSwitch> cs = new ArrayList<ChannelSwitch>();
        for (ChannelSwitch c: channelSwitches){
            if (c.getTime()==time){
                cs.add(c);
            }
        }
        channelSwitches.removeAll(cs);

        System.out.println(String.format("Time: %s Switched from channel %s to channel %s", getDirector().getModelTime(), currentChannel, channel));
        currentChannel = channel; // set context to target appropriate Sink object
        lastChannelSwitch = time; // record time of channel switch for future reference
        channelOutput.broadcast(new IntToken(channel));
    }

    /**
     * Create broadcast at the end of current cycle
     * @param b: latest known beacon
     * @param channel: channel for which to schedule broadcast
     * @param t: t of Sink
     * @param currentTime: time at which broadcast scheduling is taking place
     * @return time for which broadcast was scheduled
     * @throws IllegalActionException
     */
    private double createNextPossibleBroadcast(Beacon b, int channel, double t, double currentTime) throws IllegalActionException {
        double broadcastTime = round((t  * b.n) + currentTime);
        double deadline = broadcastTime + T_MIN;
        setupBroadcastAndCallBack(broadcastTime, deadline, channel);
        System.out.println("  1st broadcast.");
        return broadcastTime;
    }

    /**
     * Create 2nd broadcast at the end of following cycle
     * @param b: latest known beacon
     * @param channel: channel for which to schedule broadcast
     * @param t: period of Sink
     * @param N: number of beacons per cycle for given channel
     * @throws IllegalActionException
     */
    private void create2ndBroadcast(Beacon b, int channel, double t, int N) throws IllegalActionException {
        double broadcast = round((((N+11)+b.n) * t) + b.t);
        double deadline = broadcast + t;
        //If broadcast successfully scheduled, remove any impending channel switches back towards current channel as no further beacons required from channel.
        if (setupBroadcastAndCallBack(broadcast, deadline, channel)) {
            removeAllSCforChannel(channel);
        }
    }

    /**
     * Create 2nd broadcast based on the time of the previous broadcast and the T and N values of the channel
     * @param previousBroadcast : scheduled time of previous broadcast
     * @param channel : channel for which to schedule broadcast
     * @param t : period of Sink
     * @param N : number of beacons per cycle for given channel
     * @throws IllegalActionException
     */
    private void create2ndBroadcast(double previousBroadcast, int channel, double t, int N) throws IllegalActionException {
        double broadcast = previousBroadcast + ((11 + N) * t);
        double deadline = broadcast + t;
        //If broadcast successfully scheduled, remove any impending channel switches back towards current channel as no further beacons required from channel.
        if (setupBroadcastAndCallBack(broadcast, deadline, channel)) {
            removeAllSCforChannel(channel);
        }
    }


    /**
     * Create broadcast object and callback to create callback
     * @param broadcastTime: time at which broadcast is to be sent to sink
     * @param deadline: deadline by which broadcast MUST be sent to arrive in receiving period
     * @param channel: channel on which to broadcast at broadcastTime
     * @return false if broadcast already scheduled for that time, True if broadcast sucessfully scheduled
     * @throws IllegalActionException
     */
    private boolean setupBroadcastAndCallBack(double broadcastTime, double deadline, int channel) throws IllegalActionException {
        for(Broadcast b: broadcasts){
            if (b.getChannel() == channel && b.getBroadcastTime()==broadcastTime){
                return false;
            }
        }
        sinks.get(currentChannel).incrementPlannedBroadcasts();
        Broadcast b = new Broadcast(channel, broadcastTime, deadline-microDelay);
        getDirector().fireAt(this, new Time(getDirector(), broadcastTime));
        System.out.println(String.format("Preparing broadcast on channel: %s at time: %s w/ 2 beacons.", channel, broadcastTime));
        broadcasts.add(b);
        return true;
    }

    /**
     * Return the channel switch object scheduled for time
     * @param time: time with which to search list of channelSwitches
     * @param cSwitches: arraylist of channel switches to search for
     * @return channelSwitch if found a compatible channel switch in cSwitches, null otherwise
     */
    private ChannelSwitch getChannelSwitchesAtTime(double time, ArrayList<ChannelSwitch> cSwitches) {
        for (ChannelSwitch cs : cSwitches) {
            if (time == cs.getTime()) {
                return cs;
            }
        }
        return null;
    }


    /**
     * Create channel switch at the closest possible time to t or later, save in global list of channel switches and create callback
     * @param t: earliest time at which channel switch should occur
     * @param c: channel to switch to at time t
     * @throws IllegalActionException
     */
    private void createChannelSwitchCallBack(double t, int c) throws IllegalActionException {
        // If channel switch already exists for time t, schedule for as soon as possible after time T
        if (getBroadcastsAtTime(t, broadcasts).size() > 0) {
            createChannelSwitchCallBack(t +T_MIN, c);
            return;
        }

        t = round(t);
        System.out.println(String.format("Time: %s created channel SC at time %s for channel %s", getDirector().getModelTime(), t, c));
        channelSwitches.add(new ChannelSwitch(t, c));
        getDirector().fireAt(this, new Time(getDirector(), t));
    }

    /**
     * Remove all scheduled channel switches for channel (method is called if no subsequent beacons are necessary for that channel)
     * @param channel: channel for which to remove all channel switch events
     */
    private void removeAllSCforChannel(int channel) {
        ArrayList<ChannelSwitch> toRemove = new ArrayList<ChannelSwitch>();
        for (ChannelSwitch cs : channelSwitches) {
            if (cs.getChannel() == channel) {
                toRemove.add(cs);
            }
        }
        channelSwitches.removeAll(toRemove);
    }


    /**
     * Utility method to calculate the intersection of two sets of SinkProperties
     * @param setA: set of SinkProperties generated from a pair of beacons
     * @param setB: set of SinkProperties generated from a pair of beacons (with at least one different beacon to previous pair)
     * @return set of SinkProperties found in both setA and setB
     */
    private static Set<SinkProperties> intersection(Set<SinkProperties> setA, Set<SinkProperties> setB){
        Set<SinkProperties> temp = new HashSet<SinkProperties>();
        for (SinkProperties sp: setA){
            for (SinkProperties sp2: setB){
                if (sp.equals(sp2)){
                    temp.add(sp);
                }
            }
        }
        return temp;
    }
}
