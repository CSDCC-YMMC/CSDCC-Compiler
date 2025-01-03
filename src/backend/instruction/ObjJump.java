package backend.instruction;

import backend.module.ObjBlock;
import backend.module.ObjFunction;

public class ObjJump extends ObjInstruction{

    private ObjBlock target;
    private ObjFunction func;

    public ObjJump(ObjFunction func) {
        this.func = func;
    }

    public ObjJump(ObjBlock target) {   // 无条件跳转
        this.target = target;
    }

    public ObjJump(ObjCond cond, ObjBlock target) {    // 有条件跳转
        super(cond);
        this.target = target;
    }

    public ObjBlock getTarget() {
        return target;
    }

    public void setTarget(ObjBlock target) {
        this.target = target;
    }

    public boolean isCall() {
        return target == null;
    }
    public boolean isBranch() {
        return target != null && getCond() != ObjCond.any;
    }
    public boolean isJump() {
        return target != null && getCond() == ObjCond.any;
    }

    @Override
    public String toString() {
        if (target != null)
            return "\tb" + getCond() + "\t" + target.getName() +  "\n" + (getCond() == ObjCond.any ? ".ltorg\n" : "");
        else {
            return func.refresh() + "\tbx\tlr\n.ltorg\n";
        }
    }
}
