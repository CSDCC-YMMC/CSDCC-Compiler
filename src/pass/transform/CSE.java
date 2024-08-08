package pass.transform;

import ir.*;
import ir.Module;
import ir.constants.ConstInt;
import ir.instructions.Instruction;
import ir.instructions.memoryInstructions.Alloca;
import ir.instructions.memoryInstructions.GEP;
import ir.instructions.memoryInstructions.Load;
import ir.instructions.memoryInstructions.Store;
import ir.instructions.otherInstructions.Call;
import ir.instructions.terminatorInstructions.Br;
import ir.instructions.terminatorInstructions.Ret;
import ir.types.VoidType;
import pass.Pass;
import pass.analysis.PureFunction;
import utils.Expression;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class CSE implements Pass {
    private final Module module = Module.getModule();

    private HashMap<BasicBlock,ArrayList<Boolean>> Gen;
    private HashMap<BasicBlock,ArrayList<Boolean>> KILL;
    private HashMap<BasicBlock,ArrayList<Boolean>> IN;
    private HashMap<BasicBlock,ArrayList<Boolean>> OUT;

    private ArrayList<Expression> available = new ArrayList<>();
    private ArrayList<Instruction> delete_list = new ArrayList<>();

    private HashMap<Function, Boolean> is_pure = new HashMap<>();
    private HashMap<Function, HashSet<Value>> global_var_store_effects = new HashMap<>();
    @Override
    public void run() {
        PureFunction pureFunction = new PureFunction();
        pureFunction.markPure();
        this.is_pure = pureFunction.isPure;
        this.global_var_store_effects = pureFunction.globalVarStoreEffects;

        ArrayList<Function> functions = module.getFunctionsArray();
        for (Function function : functions) {
            if( !function.getIsBuiltIn() ){
                do{
                    localCSE(function);
                }while(!delete_list.isEmpty());
            }
        }
    }

    private void localCSE(Function function){
        ArrayList<BasicBlock> blocks = function.getBasicBlocksArray();
        for (BasicBlock bb : blocks) {
            do {
                delete_list.clear();
                ArrayList<Instruction> instructions = bb.getInstructionsArray();
                ArrayList<Instruction> preInstructions = new ArrayList<>();
                for (Instruction inst : instructions) {
                    if (!isOptimizable(inst)) {
                        preInstructions.add(inst);
                        continue;
                    }

                    Instruction preInst = isAppear(inst, preInstructions);
                    if (preInst != null) {
                        delete_list.add(inst);
                        // FIXME 后面会remove self
                        inst.replaceAllUsesWith(preInst);
                    } else {
                        preInstructions.add(inst);
                    }
                }
                deleteInstr();
            } while (!delete_list.isEmpty());
        }
    }

    private Instruction isAppear(Instruction inst,ArrayList<Instruction> instructions){
        int index = instructions.size();
        for (int i = index-1 ; i >= 0 ; i--) {
            Instruction inst2 = instructions.get(i);
            if( isKill(inst,inst2) ) return null;
            Expression expression1 = new Expression(inst);
            Expression expression2 = new Expression(inst2);
            if( expression1.equals(expression2) ){
                return inst2;
            }
        }
        return null;
    }

    private boolean isKill(Instruction inst1,Instruction inst2){

        if( inst1 instanceof Call call1 && inst2 instanceof Call call2 ){
            if( !call1.getFunction().equals(call2.getFunction()) ){
                return false;
            }
            if( call1.getFunction().getIsBuiltIn() ){
                return true;
            }
            if( is_pure.containsKey(call1.getFunction()) ){
                return !is_pure.get(call1.getFunction());
            }
        }


        if( !(inst1 instanceof Load load) ){
            return false;
        }

        if( inst2 instanceof Store && isStoreWithDifferentIndex(inst1,inst2) ){
            return false;
        }

        Value lval_load = load.getAddr();
        Value target_load = findOrigin(lval_load);

        if( isArgOrGlobalArrayOp(lval_load,target_load) ){
            if( inst2 instanceof Store store){
                Value lval_store = store.getAddr();
                Value target_store = findOrigin(lval_store);

                if( target_load instanceof GlobalVariable && target_store instanceof GlobalVariable ){
                    return target_store.equals(target_load);
                }

                return isArgOrGlobalArrayOp(lval_store,target_store);
            }
            if( inst2 instanceof Call call){
                Function func = call.getFunction();
                if( func.getIsBuiltIn() ){
                    return true;
                }
                if( is_pure.containsKey(func) ){
                    return !is_pure.get(func);
                }
                return true;
            }
            return false;
        }


        if( inst2 instanceof Store store){
            return target_load.equals(findOrigin(store.getAddr()));
        }

        if( inst2 instanceof Call call){
            Function func = call.getFunction();
            if( func.getIsBuiltIn() ){
                return true;
            }
            if( is_pure.containsKey(func) && is_pure.get(func)){
                return false;
            }
            if( global_var_store_effects.containsKey(func) && global_var_store_effects.get(func).contains(lval_load) ){
                return true;
            }
            for( Value v : inst2.getOperators() ){
                if( findOrigin(v).equals(target_load) ){
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isArgOrGlobalArrayOp( Value lval, Value target ){
        return (target instanceof Argument) ||
                (target instanceof GlobalVariable && !(lval instanceof GlobalVariable));
    }

    private Value findOrigin( Value value ){
        Value lval_runner = value;
        while( lval_runner instanceof GEP gep){
            lval_runner = gep.getBase();
        }
        return lval_runner;
    }

    private boolean isStoreWithDifferentIndex(Instruction inst1,Instruction inst2){
        if( !(inst2 instanceof Store store) ){
            return true;
        }
        if( !(inst1 instanceof Load load) ){
            return true;
        }

        Value lval_1 = load.getAddr();
        Value lval_2 = store.getAddr();

        if( lval_1 instanceof GEP gep_1 && lval_2 instanceof GEP gep_2 ){
            if( gep_1.getBase() != gep_2.getBase() ) {
                return true;
            }
            if( gep_1.getIndex().size() != gep_2.getIndex().size() ){
                return true;
            }
            for( int i = 0 ; i < gep_1.getIndex().size() ; i++ ){
                Value index_1 = gep_1.getIndex().get(i);
                Value index_2 = gep_2.getIndex().get(i);
                if( index_1 instanceof ConstInt constInt1 && index_2 instanceof ConstInt constInt2 ){
                    if( constInt1.getValue() != constInt2.getValue() ){
                        return true;
                    }
                }
            }
            return false;
        } else if( lval_2.equals(lval_1) ){
            return false;
        }
        return false;
    }

    private boolean isOptimizable(Instruction instruction){
        if( instruction instanceof Ret || instruction instanceof Br || instruction instanceof Store
          || (instruction instanceof Call call && call.getFunction().getReturnType() instanceof VoidType)
           || instruction instanceof Alloca ){
            return false;
        }
        if( instruction instanceof Call call ){
            Function function = call.getFunction();
            return is_pure.containsKey(function);
        }
        return true;
    }

    void deleteInstr(){
        for (Instruction instruction : delete_list){
            instruction.removeSelf();
        }
    }
}
