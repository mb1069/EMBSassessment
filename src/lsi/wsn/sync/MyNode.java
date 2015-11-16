package lsi.wsn.sync;
//   lsi.wsn.sync.MyNode
import ptolemy.actor.IOPort;
import ptolemy.actor.NoTokenException;
import ptolemy.actor.TypedAtomicActor;
import ptolemy.actor.TypedIOPort;
import ptolemy.actor.util.Time;
import ptolemy.data.*;
import ptolemy.data.type.BaseType;
import ptolemy.data.type.Type;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MyNode extends TypedAtomicActor {
    protected TypedIOPort input;
    protected TypedIOPort output;
    protected TypedIOPort feedbackOutput;
    protected HashMap<Time, Integer> firingTimes = new HashMap<Time, Integer>();
    protected ArrayList<Time> times = new ArrayList<Time>();
    protected ArrayList<Integer> counts = new ArrayList<Integer>();
    protected int currentChannel = 11;



    public MyNode(CompositeEntity container, String name) throws IllegalActionException, NameDuplicationException {
        super(container, name);
        input = new TypedIOPort(this, "input", true, false);
        output = new TypedIOPort(this, "output", false, true);
        feedbackOutput = new TypedIOPort(this, "output2", false, true);
    }

    @Override
    public void initialize() throws IllegalActionException {
        super.initialize();
    }

    @Override
    public void fire() throws IllegalActionException {
        super.fire();
        Time currentTime = getDirector().getModelTime();
        if (firingTimes.keySet().contains(currentTime)) {
            System.out.println('a');
            //TODO set channel
            feedbackOutput.broadcast(new IntToken(666));
            System.out.println("Firing!! Time is: " + currentTime);
            firingTimes.remove(currentTime);

        } else {
            try {
                Token countToken = input.get(0);
                Time time = getDirector().getModelTime();
                int count = ((IntToken) countToken).intValue();
                System.out.println(getDirector().getModelTime() + " : " + count);

                if (!firingTimes.values().contains(currentChannel)) {
                    counts.add(count);
                    times.add(time);
                    if (counts.size() == 2) {
                        Time broadcastTime = predictBroadcastTime(counts, times);
                        //Add method to prevent firing multiple events at same channel simultaneously, and keep track of which record to next fire
                        getDirector().fireAt(this, broadcastTime);
                        System.out.println("Firing at: " + broadcastTime);
                        firingTimes.put(broadcastTime, currentChannel);
                        counts = new ArrayList<Integer>();
                        times = new ArrayList<Time>();
                    }
                }
                output.broadcast(countToken);
            } catch (NoTokenException e) {
            }
        }
    }

    private Time predictBroadcastTime(List<Integer> counts, List<Time> times) {
        int countDiff = counts.get(0) - counts.get(1);
        long longTime = ((times.get(1).subtract(times.get(0)).getLongValue()) / countDiff);
        System.out.println("Period: " + new Time(getDirector(), longTime));
        return new Time(getDirector(), (longTime * counts.get(1))).add(times.get(1));
    }

    private void toggle(boolean[] b, int index) {
        b[index] = !b[index];
    }
}