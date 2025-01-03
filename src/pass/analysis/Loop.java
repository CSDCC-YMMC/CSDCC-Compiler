package pass.analysis;

import ir.BasicBlock;
import ir.Value;
import ir.instructions.Instruction;
import ir.instructions.binaryInstructions.Add;
import ir.instructions.binaryInstructions.Icmp;
import ir.instructions.binaryInstructions.Mul;
import ir.instructions.binaryInstructions.Sub;

import java.util.ArrayList;
import java.util.HashSet;

public class Loop {
    private static int idCounter = 0;
    // ========================= Loop Base Info =========================
    private int id;
    private int depth;
    /**
     * 该 Loop 的父 Loop
     */
    private Loop parent;
    /**
     * Header: 存在唯一的 Basic Block，使得它支配 Loop 中包含的所有 Basic Block。这个 Basic Block 称为 Header。
     */
    private BasicBlock header;
    /**
     * Latch: Latch 指后继包含 Header 的 Basic Block，可以理解为跳转到循环头的基本块
     */
    private HashSet<BasicBlock> latches = new HashSet<>();
    /**
     * Exit: 退出循环后，第一个到达的基本块
     */
    private HashSet<BasicBlock> exits = new HashSet<>();
    /**
     * Exiting: 即将要退出循环的块，即 Exiting 块可以跳转到 Exit 块
     */
    private HashSet<BasicBlock> exitings = new HashSet<>();
    /**
     * Loop 中包含的全部基本块
     */
    private ArrayList<BasicBlock> allBlocks = new ArrayList<>();
    /**
     * subLoops：该 Loop 的子 Loop
     */
    private ArrayList<Loop> children = new ArrayList<>();

    // ========================= Inductor Var =========================
    private Value idcVar = null;
    private Value idcEnd = null;
    private Value idcInit = null;
    private Value idcAlu = null;
    private Value idcStep = null;
    private Icmp cond = null;
    private int loopTimes;
    private boolean isInductorVarSet = false;
    // ========================= Array GVN GCM =========================
    private boolean isArrayInit = false;
    private int arrayInitDims = 0;
    private Value initArray = null;
    private Value initValue = null;
    private HashSet<Instruction> extras = null;

    // ========================= parallel =========================
    private boolean isMarkedParallel;

    public void setArrayInitInfo(int arrayInitDims, Value initArray, Value initValue, HashSet<Instruction> extras) {
        this.isArrayInit = true;
        this.arrayInitDims = arrayInitDims;
        this.initArray = initArray;
        this.initValue = initValue;
        this.extras = extras;
    }

    public boolean getParallel() {
        return isMarkedParallel;
    }

    public void setParallel() {
        helper(this);
    }
    private void helper(Loop loop) {
        loop.isMarkedParallel = true;
        for (Loop subLoop : loop.getChildren()) {
            helper(subLoop);
        }
    }

    public HashSet<Instruction> getExtras() {
        return extras;
    }

    public Value getInitValue() {
        return initValue;
    }

    public int getArrayInitDims() {
        return arrayInitDims;
    }

    public Value getInitArray() {
        return initArray;
    }


    // ========================= Compute Var =========================
    private boolean isComputeLoopSet = false;
    private Value cptInit;
    private Value cptAlu;
    private Value cptPhi;
    private int aluIdx = 0;

    public Loop (BasicBlock header, HashSet<BasicBlock> latches) {
        this.id = idCounter++;
        header.setLoop(this);
        header.setIsLoopHeader();
        this.header = header;
        this.latches.addAll(latches);
    }

    public void resetLoopIdCounter() {
        idCounter = 0;
    }
    public int getId() {
        return id;
    }

    public Loop getParent() {
        return parent;
    }

    public void setParent(Loop parent) {
        this.parent = parent;
    }

    public BasicBlock getHeader() {
        return header;
    }

    public void addLatch(BasicBlock latch) {
        latches.add(latch);
    }

    public HashSet<BasicBlock> getLatches() {
        return latches;
    }

    public void setLatches(HashSet<BasicBlock> latches) {
        this.latches = latches;
    }

    public void addExit(BasicBlock exit) {
        exits.add(exit);
    }

    public HashSet<BasicBlock> getExits() {
        return exits;
    }

    public void setExits(HashSet<BasicBlock> exits) {
        this.exits = exits;
    }

    public void addExiting(BasicBlock exiting) {
        exitings.add(exiting);
    }

    public HashSet<BasicBlock> getExitings() {
        return exitings;
    }

    public HashSet<BasicBlock> getEnterings() {
        HashSet<BasicBlock> precs = header.getPrecursors();
        HashSet<BasicBlock> enterings = new HashSet<>();
        for (BasicBlock prec : precs) {
            if (!latches.contains(prec)) {
                enterings.add(prec);
            }
        }
        return enterings;
    }

    public void addChild(Loop child) {
        if (!children.contains(child)) {
            children.add(child);
        }
    }

    public void removeChild(Loop child) {
        children.remove(child);
    }

    public ArrayList<Loop> getChildren() {
        return children;
    }

    public boolean hasChildLoop() {
        return !children.isEmpty();
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public int getDepth() {
        return depth;
    }

    public void addBlock(BasicBlock block) {
        if (!allBlocks.contains(block)) {
            allBlocks.add(block);
        }
    }

    public void addBlockInLoopChain(BasicBlock block) {
        Loop curLoop = this;
        while (curLoop != null) {
            if (!curLoop.getAllBlocks().contains(block)) {
                curLoop.addBlock(block);
            }
            curLoop = curLoop.getParent();
        }
    }

    public ArrayList<BasicBlock> getAllBlocks() {
        return allBlocks;
    }

    public ArrayList<BasicBlock> getCurrentLoopLevelBlocks(){
        ArrayList<BasicBlock> blocks = new ArrayList<>();
        for (BasicBlock block : allBlocks) {
            if( block.getLoop() != null && block.getLoopDepth() == depth ){
                blocks.add(block);
            }
        }
        return blocks;
    }

    public void removeBlock(BasicBlock block) {
        allBlocks.remove(block);
        if( block == header ){
            block.clearIsLoopHeader();
        }
    }

    public void removeBlockInLoopChain(BasicBlock block) {
        Loop curLoop = this;
        while (curLoop != null) {
            curLoop.removeBlock(block);
            curLoop = curLoop.getParent();
        }
    }

    public void removeLoopInfo() {
        clearInductorVar();
        for (BasicBlock block : allBlocks) {
            block.removeLoop();
        }
        /*
        Parent Loop 中也需要移除此 Loop
         */
        if (parent != null) {
            parent.removeChild(this);
            parent = null;
        }
    }

    /**
     * 删除循环本身、子循环，以及循环内的 Blocks
     */
    public void removeSelf() {
        ArrayList<Loop> childLoops = new ArrayList<>(children);
        for (Loop child : childLoops) {
            child.removeSelf();
        }
        ArrayList<BasicBlock> blocks = new ArrayList<>(allBlocks);
        for (BasicBlock block : blocks) {
            removeBlockInLoopChain(block);
            block.removeSelf();
        }
        if (parent != null) {
            parent.removeChild(this);
            parent = null;
        }
    }

    public void clearInductorVar() {
        idcVar = null;
        idcEnd = null;
        idcInit = null;
        idcAlu = null;
        idcStep = null;
        cond = null;
        loopTimes = -1;
        isInductorVarSet = false;
    }

    public ArrayList<Loop> computeDfsLoops() {
        ArrayList<Loop> ans = new ArrayList<>();
        for (Loop child : children) {
            ans.addAll(child.computeDfsLoops());
        }
        ans.add(this);
        return ans;
    }

    public HashSet<BasicBlock> computeAllExits() {
        HashSet<BasicBlock> ans = new HashSet<>();
        ans.addAll(exits);
        for (Loop child : children) {
            ans.addAll(child.computeAllExits());
        }
        return ans;
    }

    public HashSet<BasicBlock> computeChildrenExits() {
        HashSet<BasicBlock> ans = new HashSet<>();
        for (Loop child : children) {
            ans.addAll(child.computeChildrenExits());
            ans.addAll(child.getExits());
        }
        return ans;
    }

    public int computeLoopSize() {
        int loopSize = 0;
        for (BasicBlock block : this.getAllBlocks()) {
            loopSize += block.getInstructionsArray().size();
        }
        return loopSize;
    }

    public int computeLoopTimes(int init, int end, int step, Instruction alu, Icmp.Condition cmp) {
        int loopTimes = -1;
        if (cmp.equals(Icmp.Condition.EQ)) {
            return (init == end) ? 1 : -1;
        } else if ((cmp.equals(Icmp.Condition.GE) && init < end) || (cmp.equals(Icmp.Condition.LE) && init > end)) {
            return -1;
        } else if ((cmp.equals(Icmp.Condition.GT) & init <= end) || (cmp.equals(Icmp.Condition.LT) && init >= end)) {
            return -1;
        }
        if (alu instanceof Add) {
            if (cmp.equals(Icmp.Condition.NE)) {
                loopTimes = ((end - init) % step == 0) ? (end - init) / step : -1;
            } else if (cmp.equals(Icmp.Condition.GE) || cmp.equals(Icmp.Condition.LE)) {
                loopTimes = (end - init) / step + 1;
            } else if (cmp.equals(Icmp.Condition.GT) || cmp.equals(Icmp.Condition.LT)) {
                loopTimes = ((end - init) % step == 0) ? (end - init) / step : (end - init) / step + 1;
            }
        } else if (alu instanceof Sub) {
            if (cmp.equals(Icmp.Condition.NE)) {
                loopTimes = ((init - end) % step == 0) ? (init - end) / step : -1;
            } else if (cmp.equals(Icmp.Condition.GE) || cmp.equals(Icmp.Condition.LE)) {
                loopTimes = (init - end) / step + 1;
            } else if (cmp.equals(Icmp.Condition.GT) || cmp.equals(Icmp.Condition.LT)) {
                loopTimes = ((init - end) % step == 0) ? (init - end) / step : (init - end) / step + 1;
            }
        } else if (alu instanceof Mul) {
            double val = Math.log(end / init) / Math.log(step);
            boolean tag = init * Math.pow(step, val) == end;
            if (cmp.equals(Icmp.Condition.NE)) {
                loopTimes = tag ? (int) val : -1;
            } else if (cmp.equals(Icmp.Condition.GE) || cmp.equals(Icmp.Condition.LE)) {
                loopTimes = (int) val + 1;
            } else if (cmp.equals(Icmp.Condition.GT) || cmp.equals(Icmp.Condition.LT)) {
                loopTimes = tag ? (int) val : (int) val + 1;
            }
        }
        return loopTimes;
    }

    public ArrayList<BasicBlock> computeDfsBlocksFromEntry(BasicBlock entry) {
        return entry.computeDfsSuccBlocks();
    }

    /**
     * 循环结构：
     * int i;
     * while (i < n) {
     *     i = i + k;
     * }
     * 特征为：
     * 1、只有一个 latch
     * 2、只有一个 Exit
     * 3、只有一个 Exiting
     * 4、Header 只有两个前驱
     * @return true/false
     */
    public Boolean isSimpleLoop() {
        if (header.getPrecursors().size() != 2) {
            return false;
        }
        if (latches.size() != 1) {
            return false;
        }
        if (exits.size() != 1) {
            return false;
        }
        if (exitings.size() != 1) {
            return false;
        }
        BasicBlock exiting = exitings.iterator().next();
        return exiting == header;
    }

    public void setInductorVar(Value idcVar, Value idcEnd, Value idcInit, Value idcAlu, Value idcStep, Icmp cond) {
        this.idcVar = idcVar;
        this.idcEnd = idcEnd;
        this.idcInit = idcInit;
        this.idcAlu = idcAlu;
        this.idcStep = idcStep;
        this.cond = cond;
        this.isInductorVarSet = true;
    }

    public void setInductorVarPartially(Value idcVar, Value idcEnd, Value idcInit, Icmp cond) {
        this.idcVar = idcVar;
        this.idcEnd = idcEnd;
        this.idcInit = idcInit;
        this.cond = cond;
        this.isInductorVarSet = true;
    }

    public Value getIdcVar() {
        return idcVar;
    }

    public Value getIdcEnd() {
        return idcEnd;
    }

    public Value getIdcInit() {
        return idcInit;
    }

    public Value getIdcAlu() {
        return idcAlu;
    }

    public Value getIdcStep() {
        return idcStep;
    }

    public Icmp getCond() {
        return cond;
    }

    public void setLoopTimes(int loopTimes) {
        this.loopTimes = loopTimes;
    }

    public int getLoopTimes() {
        return loopTimes;
    }
    public Boolean isInductorVarSet() {
        return isInductorVarSet;
    }

    public void clearComputeInfo() {
        this.isComputeLoopSet = false;
        this.cptAlu = null;
        this.cptPhi = null;
        this.cptInit = null;
        this.aluIdx = 0;
    }

    public void setComputeInfo(Value cptInit, Value cptAlu, Value cptPhi, int idx) {
        this.isComputeLoopSet = true;
        this.cptInit = cptInit;
        this.cptAlu = cptAlu;
        this.cptPhi = cptPhi;
        this.aluIdx = idx;
    }

    public Boolean isComputeInfoSet() {
        return isComputeLoopSet;
    }

    public Value getCptAlu() {
        return cptAlu;
    }

    public Value getCptPhi() {
        return cptPhi;
    }

    public Value getCptInit() {
        return cptInit;
    }

    public int getAluIdx() {
        return aluIdx;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("=========== Loop id : ").append(id).append(" ===========\n");
        sb.append("Loop Header: ").append(header.getName()).append("\n");
        sb.append("Loop Latches: ").append("\n");
        for (BasicBlock block : latches) {
            sb.append("\t").append(block.getName()).append("\n");
        }
        sb.append("Loop AllBlocks: ").append("\n");
        for (BasicBlock block : allBlocks) {
            sb.append("\t").append(block.getName()).append("\n");
        }
        return sb.toString();
    }
}
