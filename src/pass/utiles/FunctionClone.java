package pass.utiles;

import ir.*;
import ir.constants.ConstInt;
import ir.constants.Constant;
import ir.instructions.binaryInstructions.*;
import ir.instructions.memoryInstructions.Alloca;
import ir.instructions.memoryInstructions.GEP;
import ir.instructions.otherInstructions.*;
import ir.instructions.terminatorInstructions.Br;
import ir.instructions.terminatorInstructions.Ret;
import ir.types.*;
import ir.instructions.Instruction;
import ir.instructions.memoryInstructions.Store;
import ir.instructions.memoryInstructions.Load;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class FunctionClone {

    private final HashMap<Value, Value> copyMap = new HashMap<>();

    private final HashSet<BasicBlock> visited = new HashSet<>();

    private final IrBuilder irBuilder = IrBuilder.getIrBuilder();

    private final ArrayList<Phi> phis = new ArrayList<>();

    public Function copyFunction(Function srcFunc) {
        copyMap.clear();
        visited.clear();
        phis.clear();

        Function copyFunc = new Function(srcFunc.getName() + "_COPY", srcFunc.getValueType(), srcFunc.getIsBuiltIn());

        int argNum = srcFunc.getArguments().size();
        for (int i = 0; i < argNum; i++) {
            Argument argument = srcFunc.getArguments().get(i);
            putValue(argument, copyFunc.getArguments().get(i));
        }

        buildBlock(copyFunc, srcFunc.getFirstBlock());
        visited.clear();

        copyBlocks(srcFunc.getFirstBlock());

        for (Phi phi : phis) {
            for (int i = 0; i < phi.getOperators().size(); i++) {
                Value v = findValue(phi.getOperator(i));
                ((Phi) findValue(phi)).setOperator(i, v);
                if (findValue(phi.getOperator(i)) instanceof BasicBlock block) {
                    if (!block.getUsers().contains(((Phi) findValue(phi)))) {
                        findValue(phi.getOperator(i)).addUser(((Phi) findValue(phi)));
                    }
                }
            }
        }

        return copyFunc;
    }

    private void buildBlock(Function copyFunc, BasicBlock block) {
        putValue(block, irBuilder.buildBasicBlock(copyFunc));

        for (BasicBlock successor : block.getSuccessors())
        {
            if (!visited.contains(successor))
            {
                visited.add(successor);
                buildBlock(copyFunc, successor);
            }
        }
    }

    private void putValue(Value source, Value copy) {
        copyMap.put(source, copy);
    }

    private Value findValue(Value source) {
        if (source instanceof GlobalVariable || source instanceof Constant || source instanceof Function) {
            return source;
        } else if (copyMap.containsKey(source) && copyMap.get(source) != null) {
            return copyMap.get(source);
        } else {
            assert false : "Don't have copy source: " + source;
            return Constant.getZeroConstant(IntType.I32);
        }
    }

    private void copyBlocks(BasicBlock basicBlock) {

        for (Instruction srcInstr : basicBlock.getInstructionsArray()) {
            Instruction copyInstr = copyInstr(srcInstr);
            putValue(srcInstr, copyInstr);
        }

        for (BasicBlock successor : basicBlock.getSuccessors())
        {
            if (!visited.contains(successor))
            {
                visited.add(successor);
                copyBlocks(successor);
            }
        }
    }

    private Instruction copyInstr(Instruction srcInstr) {
        Instruction copyInstr = null;
        BasicBlock copyBlock = (BasicBlock) findValue(srcInstr.getParent());

        if (srcInstr instanceof BinaryInstruction)
        {
            Value copyOp1 = findValue(((BinaryInstruction) srcInstr).getOp1());
            Value copyOp2 = findValue(((BinaryInstruction) srcInstr).getOp2());
            if (srcInstr instanceof Add)
            {
                copyInstr = irBuilder.buildAdd(copyBlock, (DataType) copyOp1.getValueType(), copyOp1, copyOp2);
            }
            else if (srcInstr instanceof Sub)
            {
                copyInstr = irBuilder.buildSub(copyBlock, (DataType) copyOp1.getValueType(), copyOp1, copyOp2);
            }
            else if (srcInstr instanceof Mul)
            {
                copyInstr = irBuilder.buildMul(copyBlock, (DataType) copyOp1.getValueType(), copyOp1, copyOp2);
            }
            else if (srcInstr instanceof Sdiv)
            {
                copyInstr = irBuilder.buildSdiv(copyBlock, (DataType) copyOp1.getValueType(), copyOp1, copyOp2);
            }
            else if (srcInstr instanceof Srem)
            {
                copyInstr = irBuilder.buildSrem(copyBlock, (DataType) copyOp1.getValueType(), copyOp1, copyOp2);
            }
            else if (srcInstr instanceof Icmp)
            {
                copyInstr = irBuilder.buildIcmp(copyBlock, ((Icmp) srcInstr).getCondition(), copyOp1, copyOp2);
            }
        } else if (srcInstr instanceof Zext) {
            copyInstr = irBuilder.buildZext(copyBlock, findValue(((Zext) srcInstr).getConversionValue()));
        } else if (srcInstr instanceof Phi) {
            copyInstr = irBuilder.buildPhi((DataType) srcInstr.getValueType(), copyBlock, ((Phi) srcInstr).getPrecursorNum());
//            copyInstr = irBuilder.buildPhi((DataType) srcInstr.getValueType(), copyBlock, 0);
            phis.add((Phi) srcInstr);
        } else if (srcInstr instanceof Load) {
            copyInstr = irBuilder.buildLoad(copyBlock, findValue(((Load) srcInstr).getAddr()));
        } else if (srcInstr instanceof Store) {
            irBuilder.buildStore(copyBlock,
                    findValue(((Store) srcInstr).getValue()), findValue(((Store) srcInstr).getAddr()));
        } else if (srcInstr instanceof Alloca) {
            copyInstr = irBuilder.buildALLOCA(((PointerType) srcInstr.getValueType()).getPointeeType(), copyBlock);
        } else if (srcInstr instanceof GEP) {
            ArrayList<Value> copyIndices = new ArrayList<>();
            for (Value index : ((GEP) srcInstr).getIndex()) {
                copyIndices.add(findValue(index));
            }
            Value copyBase = findValue(((GEP) srcInstr).getBase());
            Value data = ((GEP) srcInstr).getBase();
            ValueType dataType = ((PointerType) data.getValueType()).getPointeeType();
            while(  dataType instanceof ArrayType arrayType){
                dataType = arrayType.getElementType();
            }
            if (copyIndices.size() == 1) {
                copyInstr = irBuilder.buildGEP(copyBlock, copyBase, copyIndices.get(0));
            } else if (copyIndices.size() == 2) {
                copyInstr = irBuilder.buildGEP(copyBlock, copyBase, copyIndices.get(0), copyIndices.get(1));
            } else {
                copyInstr = irBuilder.buildGEP(copyBlock, new PointerType(dataType), copyBase, copyIndices);
            }
        } else if (srcInstr instanceof Call) {
            ArrayList<Value> args = new ArrayList<>();
            for (int i = 1; i < srcInstr.getNumOfOps(); i++) {
                args.add(findValue(srcInstr.getOperator(i)));
            }
            copyInstr = irBuilder.buildCall(copyBlock, ((Call) srcInstr).getFunction(), args);
        } else if (srcInstr instanceof Br) {
            if (((Br) srcInstr).getHasCondition()) {
                irBuilder.buildBr(copyBlock, findValue(srcInstr.getOperator(0)),
                        (BasicBlock) findValue(srcInstr.getOperator(1)),
                        (BasicBlock) findValue(srcInstr.getOperator(2)));
            } else {
                irBuilder.buildBr(copyBlock, (BasicBlock) findValue(srcInstr.getOperator(0)));
            }
        } else if (srcInstr instanceof Ret) {
            if (srcInstr.getOperators().isEmpty()) {
                irBuilder.buildRet(copyBlock);
            } else {
                irBuilder.buildRet(copyBlock, findValue(((Ret) srcInstr).getRetValue()));
            }
        } else if (srcInstr instanceof Conversion conversionInstr) {
            copyInstr = irBuilder.buildConversion(copyBlock, conversionInstr.getType(), (DataType) conversionInstr.getValueType(), findValue(conversionInstr.getConversionValue()));
        } else if (srcInstr instanceof BitCast bitCastInstr) {
            copyInstr = irBuilder.buildBitCast(copyBlock, (DataType) bitCastInstr.getValueType(), findValue(bitCastInstr.getConversionValue()));
        }

        return copyInstr;
    }

}
