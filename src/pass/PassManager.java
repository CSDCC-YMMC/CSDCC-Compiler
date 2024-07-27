package pass;

import config.Config;
import ir.Module;
import pass.analysis.CFG;
import pass.analysis.Dom;
import pass.analysis.LoopAnalysis;
import pass.analysis.SideEffect;
import pass.transform.*;
import pass.transform.emituseless.UselessPhiEmit;
import pass.transform.emituseless.UselessStoreEmit;
import pass.transform.gcmgvn.GCMGVN;
import pass.transform.loop.LCSSA;
import pass.transform.loop.LoopUnroll;

import java.util.ArrayList;

public class PassManager {
    private Module module = Module.getModule();
    private ArrayList<Pass> passes = new ArrayList<>();

    public void run() {
        passes.add(new CFG());
        passes.add(new Dom());
        passes.add(new LoopAnalysis());
        passes.add(new GlobalValueLocalize());
        passes.add(new Mem2reg());
//        passes.add(new GepFuse());
        passes.add(new InlineFunction());

        // SCCP后可能出现没有value的phi
        passes.add(new SCCP());
//        passes.add(new UselessPhiEmit());
        passes.add(new SimplifyInst());
//
        passes.add(new MergeBlocks());

        passes.add(new SideEffect());
//        passes.add(new UselessReturnEmit());
        // UselessStoreEmit 前面，一定要进行函数副作用的分析
        passes.add(new UselessStoreEmit());
//        passes.add(new DeadCodeEmit());
        passes.add(new UselessPhiEmit());
        if (Config.isO1) {
            GVNGCMPass();
//            passes.add(new LCSSA());
//            passes.add(new LoopUnroll());

            passes.add(new MergeBlocks());

            passes.add(new CFG());
            passes.add(new Dom());
            passes.add(new GAVN());  // GAVN前需要最新的CFG和Dom, 放在GVN GCM后面较好
        }
        passes.add(new MathOptimize());
//
        passes.add(new InstructionCleanUp());

//        passes.add(new GepSplit());
        passes.add(new Dom());
        for (Pass pass : passes) {
            pass.run();
        }
    }

    /**
     * 这个是专门用来处理 GVN 和 GCM 的 Pass
     * GVN 和 GCM 之前一定要先进行副作用判断，来确定某一个函数是否可以被处理
     */
    private void GVNGCMPass() {
        passes.add(new SideEffect());
        passes.add(new GCMGVN());
    }
}