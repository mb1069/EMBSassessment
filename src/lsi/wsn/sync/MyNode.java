package lsi.wsn.sync;

import ptolemy.actor.IOPort;
import ptolemy.actor.TypedAtomicActor;
import ptolemy.actor.TypedIOPort;
import ptolemy.actor.util.Time;
import ptolemy.data.IntToken;
import ptolemy.data.LongToken;
import ptolemy.data.RecordToken;
import ptolemy.data.Token;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;

public class MyNode extends TypedAtomicActor{
    protected TypedIOPort input;
    protected TypedIOPort output;
    protected Time time;
    protected int[] channels = {11,12,13,14,15};
    public MyNode(CompositeEntity container, String name) throws IllegalActionException, NameDuplicationException {
        super(container, name);
        time =  getDirector().getModelTime();
        input = new TypedIOPort(this, "input", true, false);
        output = new TypedIOPort(this, "output", false, true);

    }

    @Override
    public void initialize() throws IllegalActionException {
        super.initialize();
        getDirector().fireAt(this, getDirector().getModelTime().add(20));
    }

    @Override
    public void fire() throws IllegalActionException {
        super.fire();
        getDirector().fireAt(this, getDirector().getModelTime().add(20));
        LongToken time = new LongToken(getDirector().getModelTime().getLongValue());

        Token count = input.get(0);
        RecordToken token = new RecordToken(new String[]{"channel", "time", "count"}, new Token[]{new IntToken(11), time, count});
        output.broadcast(token);
    }
}
