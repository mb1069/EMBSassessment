package lsi.wsn.sync;

import ptolemy.actor.gui.PtExecuteApplication;
import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

public class Test {
    public static final int N_MAX = 10;
    public static final int N_MIN = 1;
    public static final double T_MIN = 0.5;
    public static final double T_MAX = 1.5;
    public static final double P_MAX = (11+N_MAX)*T_MAX;

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
    }}