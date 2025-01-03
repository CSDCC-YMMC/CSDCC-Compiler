package ast;


import ir.*;
import ir.constants.ConstArray;
import ir.constants.Constant;
import ir.types.*;

import java.util.ArrayList;
import java.util.Stack;

public abstract class Node {
     public final ArrayList<Node> childNode = new ArrayList<>();

     /*================================ 中间代码转换 ================================*/

     // 这两个栈用于方便 break 和 continue 确定自己的跳转目标,因为 loop 可能嵌套,
     // 为了避免外层 loop 的信息被内层 loop 覆盖,所以采用了栈结构
     public static final Stack<BasicBlock> loopCondBlockDown = new Stack<>();
     public static final Stack<BasicBlock> loopNextBlockDown = new Stack<>();

     public static final IrBuilder builder = IrBuilder.getIrBuilder();
     public static final IrSymbolTable irSymbolTable = new IrSymbolTable();

     // 综合属性:各种 buildIr 的结果(单值形式)如果会被其更高的节点应用,那么需要利用这个值进行通信
     public static Value valueUp;
     // 综合属性:返回值是一个基本块,用来输出main函数最后的return结果
     public static BasicBlock lastBasicBlockUp = null;
     // 综合属性:各种 buildIr 的结果(数组形式)如果会被其更高的节点应用,那么需要利用这个值进行通信
     public static ArrayList<Value> valueArrayUp = new ArrayList<>();
     // 综合属性:函数的参数类型通过这个上传
     public static DataType argTypeUp = null;
     // 综合属性:函数的参数类型数组通过这个上传
     public static ArrayList<DataType> argTypeArrayUp = new ArrayList<>();
     // 综合属性:用来确定当前条件判断中是否是这种情况 if(7),对于这种情况,需要多加入一条 Icmp
     public static boolean i32InRelUp;

     // 继承属性:说明进行全局初始化
     public static boolean globalInitDown = false;
     // 继承属性:说明当前表达式可求值,进而可以说明此时的返回值是 valueIntUp
     public static boolean canCalValueDown = false;
     // 继承属性:在 build 实参的时候用的,对于 PrimaryExp,会有一个 Load LVal 的动作; 当 PrimaryExp 作为实参的时候,如果实参需要的是一个指针,那么就不需要 load
     public static boolean paramNotNeedLoadDown = false;

     // build 的当前函数
     public static Function curFunc = null;
     // build 的当前基本块
     public static BasicBlock curBlock = null;

     public static int buildInFlag = 1;


     public void buildIrTree(){
          for (Node node : childNode) {
               node.buildIrTree();
          }
     }

     abstract public void accept();
}
