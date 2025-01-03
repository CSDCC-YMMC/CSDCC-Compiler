package ir.constants;

import ir.types.FloatType;

public class ConstFloat extends Constant{
    public static final ConstFloat ZERO = new ConstFloat(0);
    private final float value;

    public ConstFloat(float value){
        super(new FloatType());
        this.value = value;
    }

    public float getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConstFloat that = (ConstFloat) o;
        return value == that.value;
    }

    @Override
    public String toString(){
        return String.format("0x%x", Double.doubleToRawLongBits(value));
    }

    @Override
    public String getName(){
        return String.format("0x%x", Double.doubleToRawLongBits(value));
    }
}
