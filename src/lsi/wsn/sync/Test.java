package lsi.wsn.sync;

import ptolemy.actor.TypedCompositeActor;
import ptolemy.actor.gui.PtExecuteApplication;
import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Test class to run ptolemy without the UI
 */
public class Test {

    public static void main(String[] args) throws Exception {
        PtExecuteApplication ptExec = new PtExecuteApplication(new String[]{
                "src"+ File.separator+"model.xml"
        });


        ptExec.runModels();
        ptExec.waitForFinish();
        List entities = ((TypedCompositeActor)ptExec.models().get(1)).deepEntityList();

        //Prints out results for each sink in the format: numReceivedAtCorrectTime / numReceivedAtIncorrectTime
        for(Object entity : entities) {
            if(entity instanceof TDMAnchor){
                TDMAnchor e = (TDMAnchor)entity;
                System.out.println(e.getResults());
            }
        }
    }}