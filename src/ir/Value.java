package ir;

import ir.types.ValueType;

import java.util.ArrayList;
import java.util.Objects;

/**
 @author Conroy
 */
public abstract class Value {
   // 唯一标识
   private final int id;
   // value 的名字,用于打印 llvm ir
   protected String name;
   private ValueType valueType;
   // value 的拥有者,注意不是使用者 user
   private Value parent;
   // 记录使用过当前 value 的使用者,一个 value 可以有多个 user
   private final ArrayList<User> users = new ArrayList<>();
   // id 的唯一性
   private static int idCounter = 0;

   public Value(String name, ValueType valueType, Value parent){
       this.id = idCounter++;
       this.name = name;
       this.valueType = valueType;
       this.parent = parent;
   }

    public int getId(){
        return id;
    }
    public String getName(){
        return name;
    }
    public ValueType getValueType(){
        return valueType;
    }
    public void setValueType(ValueType valueType) {
       this.valueType = valueType;
    }
    public Value getParent(){
        return parent;
    }


    public ArrayList<User> getUsers(){
        return users;
    }

    public void setParent(Value parent){
        this.parent = parent;
    }
    public void setName(int num){
        this.name = "%" + num;
    }

    public void addUser(User selfUser){
        if( !users.contains(selfUser) ){
            users.add(selfUser);
        }
    }

    public void removeUser(User user) {
//        if( !users.contains(user) )
//            throw new AssertionError("value-" + getId() + " try to remove nonexistent user: " + user + " " + user.getId());
//        users.remove(user);
        users.remove(user);
    }

    public void replaceAllUsesWith(Value replacement){
        ArrayList<User> usersClone = new ArrayList<>(users);
        for (User user : usersClone){
            for (int i = 0; i < user.getNumOfOps(); i++){
                if (user.getOperator(i) == this){
                    user.setOperator(i, replacement);
                }
            }
        }
        users.clear();
    }

    @Override
    public boolean equals(Object o){
        // 引用同一个内存地址
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Value value = (Value) o;
        return id == value.id;
    }

    @Override
    public int hashCode(){
        return Objects.hash(id);
    }
}
