package ir.instructions.otherInstructions;

import ir.BasicBlock;
import ir.Value;
import ir.instructions.Instruction;
import ir.types.DataType;

import java.util.ArrayList;
/**
 @author Conroy
 */
public class Conversion extends Instruction {
    private String type;

    public Conversion(int nameNum, String type, DataType dataType, BasicBlock parent, Value value){
        super("%v" + nameNum, dataType, parent, new ArrayList<>(){{
            add(value);
        }});
        this.type = type;
    }

    public Value getConversionValue(){
        return getOperator(0);
    }

    public String getType() { return type; }


    @Override
    public String toString(){
        if(this.type.equals("fptosi")){
            return this.getName() + " = fptosi float " + getOperator(0).getName() + " to i32";
        } else {
            return this.getName() + " = sitofp i32 " + getOperator(0).getName() + " to float";
        }
    }

    public String getHashNumbering(){
        if(this.type.equals("fptosi")){
            return "fptosi float " + getOperator(0).getName() + " to i32";
        } else {
            return "sitofp i32 " + getOperator(0).getName() + " to float";
        }
    }
}
