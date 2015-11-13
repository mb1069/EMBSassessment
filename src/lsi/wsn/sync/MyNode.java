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

public class MyNode extends TypedAtomicActor{
    protected TypedIOPort input;
    protected TypedIOPort output;
    protected TypedIOPort feedbackOutput;
    protected Time lastTime;
    protected int lastCount = 0;
    protected boolean fire = false;
    public MyNode(CompositeEntity container, String name) throws IllegalActionException, NameDuplicationException {
        super(container, name);
        input = new TypedIOPort(this, "input", true, false);
        output = new TypedIOPort(this, "output", false, true);
        feedbackOutput = new TypedIOPort(this, "output2", false, true);
    }

    @Override
    public void initialize() throws IllegalActionException {
        super.initialize();
        getDirector().fireAt(this, getDirector().getModelTime().add(20));
    }

    @Override
    public void fire() throws IllegalActionException {
        super.fire();
        if (fire){
            fire = false;
            feedbackOutput.broadcast(new IntToken(666));
        } else {
            try {
                Token countToken = input.get(0);
                int count = ((IntToken) countToken).intValue();
                if (lastCount == 0){
                    lastTime = getDirector().getModelTime();
                    lastCount = count;
                } else {
                    Time broadcastTime = predictBroadcastTime(lastCount, lastTime, count, getDirector().getModelTime());
                    fire = true;
                    //Add method to prevent firing multiple events at same channel simultaneously, and keep track of which record to next fire
                    getDirector().fireAt(this, broadcastTime);
                }
                output.broadcast(countToken);
                System.out.println(getDirector().getModelTime() + " : " + count);
            } catch( NoTokenException e){}
        }
    }

    private Time predictBroadcastTime(int firstCount, Time firstTime, int secondCount, Time secondTime){
        int countDiff = secondCount-firstCount;
        long period = (firstTime.subtract(secondTime)).getLongValue()/countDiff;
        return new Time(getDirector(), (period*secondCount)).add(secondTime);
    }
}
