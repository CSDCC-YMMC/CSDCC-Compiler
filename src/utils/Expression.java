package utils;

import ir.Value;
import ir.constants.ConstFloat;
import ir.constants.ConstInt;
import ir.constants.Constant;
import ir.instructions.Instruction;
import ir.instructions.binaryInstructions.*;
import ir.instructions.memoryInstructions.Alloca;
import ir.instructions.memoryInstructions.GEP;
import ir.instructions.memoryInstructions.Load;
import ir.instructions.memoryInstructions.Store;
import ir.instructions.otherInstructions.*;
import ir.instructions.terminatorInstructions.Br;
import ir.instructions.terminatorInstructions.Ret;
import ir.types.FloatType;
import ir.types.IntType;

import java.util.ArrayList;
import java.util.HashSet;

public class Expression {
    public HashSet<Instruction> source = new HashSet<>();
    public Instruction instruction;
    public ArrayList<Value> operands = new ArrayList<>();

    public Expression(Instruction instruction) {
        this.instruction = instruction;
        this.operands.addAll(instruction.getOperators());
        source.add(instruction);
    }

    public boolean isSameType(Instruction instr1, Instruction instr2){
        boolean isSame = false;
        if (instr1 instanceof Add && instr2 instanceof Add) isSame = true;
        else if (instr1 instanceof Sub && instr2 instanceof Sub) isSame = true;
        else if (instr1 instanceof Mul && instr2 instanceof Mul) isSame = true;
        else if (instr1 instanceof Sdiv && instr2 instanceof Sdiv) isSame = true;
        else if (instr1 instanceof Srem && instr2 instanceof Srem) isSame = true;
        else if (instr1 instanceof Icmp && instr2 instanceof Icmp) isSame = true;
        else if (instr1 instanceof Alloca && instr2 instanceof Alloca) isSame = true;
        else if (instr1 instanceof GEP && instr2 instanceof GEP) isSame = true;
        else if (instr1 instanceof Load && instr2 instanceof Load) isSame = true;
        else if (instr1 instanceof Store && instr2 instanceof Store) isSame = true;
        else if (instr1 instanceof BitCast && instr2 instanceof BitCast) isSame = true;
        else if (instr1 instanceof Call && instr2 instanceof Call) isSame = true;
        else if (instr1 instanceof Conversion && instr2 instanceof Conversion) isSame = true;
        else if (instr1 instanceof Phi && instr2 instanceof Phi) isSame = true;
        else if (instr1 instanceof Zext && instr2 instanceof Zext) isSame = true;
        else if (instr1 instanceof Br && instr2 instanceof Br) isSame = true;
        else if (instr1 instanceof Ret && instr2 instanceof Ret) isSame = true;
        return isSame;
    }

    @Override
    public boolean equals(Object o){
        if( !(o instanceof Expression instr) ){
            return false;
        }
        if( !isSameType(instr.instruction,instruction) ) {
            return false;
        }
        if( operands.size() != instr.operands.size() ){
            return false;
        }
        if( instruction instanceof Icmp icmp_1 && instr.instruction instanceof Icmp icmp_2){
            if( icmp_1.getOperator(0).getValueType() instanceof FloatType && icmp_2.getOperator(0).getValueType() instanceof FloatType){
                return icmp_1.getCondition() == icmp_2.getCondition();
            } else if( icmp_1.getOperator(0).getValueType() instanceof IntType && icmp_2.getOperator(0).getValueType() instanceof IntType){
                return icmp_1.getCondition() == icmp_2.getCondition();
            } else return false;
        }

        if( instruction instanceof Call call_1 && instr.instruction instanceof Call call_2){
            if( call_1.getFunction().getIsBuiltIn() || call_2.getFunction().getIsBuiltIn()){
                return false;
            }
        }
        for( int i = 0 ; i < operands.size() ; i++){
            Value op1 = operands.get(i);
            Value op2 = instr.operands.get(i);
            // int const
            if( op1 instanceof ConstInt constInt1 && op2 instanceof ConstInt constInt2){
                if(constInt1.getValue() != constInt2.getValue()){
                    return false;
                }
                continue;
            }
            // float const
            if( op1 instanceof ConstFloat constFloat1 && op2 instanceof ConstFloat constFloat2){
                if(constFloat1.getValue() != constFloat2.getValue()){
                    return false;
                }
                continue;
            }
            if( !op1.equals(op2) )return false;
        }
        return true;
    }
}
