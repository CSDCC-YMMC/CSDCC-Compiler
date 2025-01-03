package pass.transform;

import ir.*;
import ir.Module;
import ir.constants.ConstInt;
import ir.instructions.Instruction;
import ir.instructions.memoryInstructions.GEP;
import ir.instructions.memoryInstructions.Load;
import ir.instructions.memoryInstructions.Store;
import ir.instructions.otherInstructions.Call;
import pass.Pass;
import pass.analysis.PureFunction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class GAVN implements Pass {
    private final Module module = Module.getModule();
    // 记录指针与其loadInst的映射关系
    HashMap<Value, Value> GAVNMap = new HashMap<>();
    HashSet<GEP> canGAVN = new HashSet<>();
    private HashMap<Function, Boolean> is_pure = new HashMap<>();
    @Override
    public void run() {
        ArrayList<Function> functions = module.getFunctionsArray();

        PureFunction pureFunction = new PureFunction();
        pureFunction.markPure();
        this.is_pure = pureFunction.isPure;

        for(Function function :  functions){
            if( !function.getIsBuiltIn() ){
                // 针对指针没有store的情况
                simpleGVN(function);

                arrayGVN(function);
            }
        }
    }

    private void simpleGVN(Function function) {
        GAVNMap.clear();
        canGAVN.clear();
        initCanGAVN(function);

        RPOSearch(function.getFirstBlock());
    }

    private void arrayGVN(Function function) {
        ArrayList<BasicBlock> basicBlocks = function.getBasicBlocksArray();
        for(BasicBlock basicBlock : basicBlocks){
            GAVNMap.clear();
            addLoad(basicBlock);
            for( BasicBlock son : basicBlock.getIdoms()){
                // TODO 只考虑这种弱的情况 后面可以增强
                if( basicBlock.getSuccessors().contains(son) && son.getPrecursors().size() == 1){
                    replaceLoad(son);
                }
            }
        }
    }

    private void addLoad(BasicBlock basicBlock) {
        ArrayList<Instruction> instructions = basicBlock.getInstructionsArray();
        for(Instruction instruction : instructions){
            if(instruction instanceof Load load){
                if( GAVNMap.containsKey(load.getAddr())){
                    load.replaceAllUsesWith((GAVNMap.get(load.getAddr())));
                    load.removeSelf();
                } else {
                    GAVNMap.put(load.getAddr(), load);
                }
            } else if( instruction instanceof Store store){
                if( mysteriousStore( store.getAddr() ) ){
//                    deleteGEP((GEP) store.getAddr());
                    GAVNMap.clear();
                } else GAVNMap.remove( store.getAddr() );
            } else if( instruction instanceof Call call && !(is_pure.containsKey(call.getFunction()) && is_pure.get(call.getFunction())) ){
                //保守起见 碰到call非纯函数就清空
                GAVNMap.clear();
            }
        }
    }

    private boolean mysteriousStore(Value pointer) {
        if( pointer instanceof GEP gep ){
            for( Value value : gep.getIndex() ){
                if( !(value instanceof ConstInt) ){
                    return true;
                }
            }
        }
        return false;
    }

    private void deleteGEP(GEP gep) {
        ArrayList<GEP> deleteGeps = new ArrayList<>();
        for (Value value : GAVNMap.keySet()) {
            if( !(value instanceof GEP temp_gep) ){
                continue;
            }
            boolean deleteFlag = true;
            if (!(temp_gep.getBase().equals(gep.getBase()))) {
                continue;
            }
            if (temp_gep.getIndex().size() != gep.getIndex().size()) {
                continue;
            }
            for (int j = 0; j < gep.getIndex().size(); j++) {
                if (gep.getIndex().get(j) instanceof ConstInt constInt1 && temp_gep.getIndex().get(j) instanceof ConstInt constInt2) {
                    if (constInt1 != constInt2) {
                        deleteFlag = false;
                        break;
                    }
                }
            }
            if (deleteFlag) {
                deleteGeps.add(temp_gep);
            }
        }
        for (GEP deleteGep : deleteGeps) {
            GAVNMap.remove(deleteGep);
        }
    }

    private void replaceLoad(BasicBlock basicBlock) {
        HashMap<Value,Value> tempRemoves = new HashMap<>();
        ArrayList<Instruction> instructions = basicBlock.getInstructionsArray();
        for( Instruction instruction : instructions){
            if( instruction instanceof Load load && GAVNMap.containsKey(load.getAddr()) ){
                load.replaceAllUsesWith((GAVNMap.get(load.getAddr())));
                load.removeSelf();
            } else if( instruction instanceof Store store ){
                if( mysteriousStore( store.getAddr() ) ){
                    GAVNMap.clear();
//                    deleteGEP((GEP) store.getAddr());
                    tempRemoves.clear();
                    break;
                }
                Value key = store.getAddr();
                if( GAVNMap.containsKey(key) ){
                    Value value = GAVNMap.get(key);
                    tempRemoves.put(key, value);
                    GAVNMap.remove(key);
                }
            }
        }

        for(Value key : tempRemoves.keySet()){
            GAVNMap.put(key, tempRemoves.get(key));
        }
    }

    private void initCanGAVN(Function function) {
        ArrayList<GEP> geps = new ArrayList<>();
        ArrayList<BasicBlock> basicBlocks = function.getBasicBlocksArray();

        for( BasicBlock basicBlock : basicBlocks ){
            ArrayList<Instruction> instructions = basicBlock.getInstructionsArray();
            for( Instruction instruction : instructions ){
                if( instruction instanceof Store store ){
                    if( mysteriousStore(store.getAddr()) ){
                        geps.clear();
                    } else if( store.getAddr() instanceof GEP gep){
                        geps.remove(gep);
                    }
                }
                if( instruction instanceof GEP gep){
                    geps.add(gep);
                }
                if( instruction instanceof Call call && !(is_pure.containsKey(call.getFunction()) && is_pure.get(call.getFunction())) ){
                    //保守起见 碰到call非纯函数就清空
                    geps.clear();
                }
            }
        }

        for( GEP gep : geps ){
            boolean storeFlag = false;
            for( User user : gep.getUsers() ){
                if( user instanceof Store ){
                    storeFlag = true;
                    break;
                }
            }
            if( !storeFlag ){
                canGAVN.add(gep);
            }
        }
    }

    private void RPOSearch(BasicBlock basicBlock) {
        ArrayList<Instruction> instructions = basicBlock.getInstructionsArray();
        HashSet<Load> numberedInstructions = new HashSet<>();

        for( Instruction instruction : instructions ){
            if( instruction instanceof Load load && load.getAddr() instanceof GEP gep && canGAVN.contains(gep) ){
                boolean isNumbered = loadGAVN(load);
                if( isNumbered ){
                    numberedInstructions.add(load);
                }
            }
        }

        for( BasicBlock idom : basicBlock.getIdoms() ){
            RPOSearch(idom);
        }

        for( Load load : numberedInstructions ){
            GAVNMap.remove(load.getAddr());
        }
    }

    private boolean loadGAVN(Load load) {

        if( GAVNMap.containsKey(load.getAddr()) ){
            Value addr = GAVNMap.get(load.getAddr());
            load.replaceAllUsesWith(addr);
            load.removeSelf();
            return false;
        }

        GAVNMap.put(load.getAddr(), load);
        return true;
    }
}
