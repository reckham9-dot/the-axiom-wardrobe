package online.theaxiom.wardrobe.rig;

public final class TestRigCalibration {
    private static int assertions;
    private static void check(boolean value) { assertions++; if (!value) throw new AssertionError(); }
    private static void reject(Runnable action) {
        assertions++;
        try { action.run(); throw new AssertionError("Unexpectedly accepted invalid tuning"); }
        catch (IllegalArgumentException expected) { }
    }
    public static void main(String[] args) {
        var base=RigCalibration.parse(new String[]{"tune","0","-0.35","-0.22","0.55"},1);
        check(base.x()==0f); check(base.y()==-.35f);check(base.z()==-.22f);check(base.scale()==.55f);
        reject(()->RigCalibration.parse(new String[]{"tune","1"},1));
        reject(()->RigCalibration.parse(new String[]{"tune","a","1","0",".4"},1));
        reject(()->RigCalibration.parse(new String[]{"tune","NaN","0","0",".4"},1));
        reject(()->RigCalibration.parse(new String[]{"tune","Infinity","0","0",".4"},1));
        reject(()->RigCalibration.parse(new String[]{"tune","1.51","0","0",".4"},1));
        reject(()->RigCalibration.parse(new String[]{"tune","0","0","0",".04"},1));
        reject(()->RigCalibration.parse(new String[]{"tune","0","0","0","1.51"},1));
        for(int i=0;i<1000;i++){
            var v=RigCalibration.of(Math.sin(i)*1.4,Math.cos(i)*1.4,0.0,0.55);
            check(Math.abs(v.x())<=1.5 && Math.abs(v.y())<=1.5 && v.scale()==.55f);
        }
        System.out.println("TestRigCalibration OK assertions="+assertions);
    }
}
