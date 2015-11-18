package lsi.wsn.sync;

import ptolemy.actor.gui.PtExecuteApplication;
import java.io.File;

public class Test {
    public static void main(String[] args) throws Exception {
        PtExecuteApplication ptExec = new PtExecuteApplication(new String[]{
                "src"+ File.separator+"model.xml"
        });

//        List entities = ((TypedCompositeActor)ptExec.models().get(1)).deepEntityList();
//        for(Object entity : entities) {
//            if(entity instanceof TDMAnchor){
//                TDMAnchor e = (TDMAnchor)entity;
//                e.setN("5");
//                e.setT("8");
//            }
//        }
        ptExec.runModels();
        ptExec.waitForFinish();
    }
}