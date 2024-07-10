package ast;


import ir.*;
import ir.types.*;

import java.util.ArrayList;
import java.util.Stack;

public abstract class Node {
     public final ArrayList<Node> childNode = new ArrayList<>();

     /*================================ 中间代码转换 ================================*/

     // 这两个栈用于方便 break 和 continue 确定自己的跳转目标,因为 loop 可能嵌套,
     // 为了避免外层 loop 的信息被内层 loop 覆盖,所以采用了栈结构
     public static final Stack<BasicBlock> loopSelfBlockDown = new Stack<>();
     public static final Stack<BasicBlock> loopNextBlockDown = new Stack<>();

     public static final IrBuilder builder = IrBuilder.getIrBuilder();
     public static final IrSymbolTable irSymbolTable = new IrSymbolTable();

     // 综合属性:各种 buildIr 的结果(单值形式)如果会被其更高的节点应用,那么需要利用这个值进行通信
     public static Value valueUp;
     // 综合属性:返回值是一个 int ,其实本质上将其包装成 ConstInt 就可以通过 valueUp 返回,但是这样返回更加简便
     public static int valueIntUp = 0;
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

     // 遍历 AST 从而建立 ir tree
     // 需要加入运行时库函数,最后还是没有加入符号表,这依赖于程序是正确的
     // Attention : 所有子类都要重写这个方法,否则运行时库函数多次加入
     public void buildIrTree(){
          /*================================ getint ================================*/
          Function.getint = builder.buildFunction("getint", new FunctionType(new ArrayList<>(), new IntType(32)), true);

          /*================================ getch ================================*/
          Function.getch = builder.buildFunction("getch", new FunctionType(new ArrayList<>(), new IntType(32)), true);

          /*================================ getfloat ================================*/
          Function.getfloat = builder.buildFunction("getfloat", new FunctionType(new ArrayList<>(), new FloatType()), true);

          /*================================ getarray ================================*/
          ArrayList<DataType> getarrayArgs = new ArrayList<>();
          getarrayArgs.add(new PointerType(new IntType(32)));
          Function.getarray = builder.buildFunction("getarray", new FunctionType(getarrayArgs, new IntType(32)), true);

          /*================================ getfarray ================================*/
          ArrayList<DataType> getfarrayArgs = new ArrayList<>();
          getfarrayArgs.add(new PointerType(new FloatType()));
          Function.getfarray = builder.buildFunction("getarray", new FunctionType(getfarrayArgs, new IntType(32)), true);

          /*================================ putint ================================*/
          ArrayList<DataType> putintArgs = new ArrayList<>();
          putintArgs.add(new IntType(32));
          Function.putint = builder.buildFunction("putint", new FunctionType(putintArgs, new VoidType()), true);

          /*================================ putch ================================*/
          ArrayList<DataType> putchArgs = new ArrayList<>();
          putchArgs.add(new IntType(32));
          Function.putch = builder.buildFunction("putch", new FunctionType(putchArgs, new VoidType()), true);

          /*================================ putfloat ================================*/
          ArrayList<DataType> putfloatArgs = new ArrayList<>();
          putfloatArgs.add(new FloatType());
          Function.putfloat = builder.buildFunction("putfloat", new FunctionType(putfloatArgs, new VoidType()), true);

          /*================================ putarray ================================*/
          ArrayList<DataType> putarrayArgs = new ArrayList<>();
          putarrayArgs.add(new IntType(32));
          putarrayArgs.add(new PointerType(new IntType(32)));
          Function.putarray = builder.buildFunction("putarray", new FunctionType(putarrayArgs, new VoidType()), true);

          /*================================ putfarray ================================*/
          ArrayList<DataType> putfarrayArgs = new ArrayList<>();
          putfarrayArgs.add(new IntType(32));
          putfarrayArgs.add(new PointerType(new FloatType()));
          Function.putfarray = builder.buildFunction("putfarray", new FunctionType(putfarrayArgs, new VoidType()), true);

          /*================================ putstr ================================*/
          ArrayList<DataType> printfArgs = new ArrayList<>();
          printfArgs.add(new PointerType(new IntType(8)));
          Function.putstr = builder.buildFunction("putstr", new FunctionType(printfArgs, new VoidType()), true);

          for (Node node : childNode) {
               node.buildIrTree();
          }
     }

     abstract public void accept();
}