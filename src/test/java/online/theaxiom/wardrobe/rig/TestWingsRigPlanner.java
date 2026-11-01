package online.theaxiom.wardrobe.rig;

import java.util.*;
import static online.theaxiom.wardrobe.rig.WearableRigCore.*;
import static online.theaxiom.wardrobe.rig.WingsRigPlanner.*;

public final class TestWingsRigPlanner {
    static int checks;
    static void yes(boolean b, String why) { checks++; if(!b) throw new AssertionError("check "+checks+": "+why); }
    static void eq(Object a, Object b) {yes(Objects.equals(a,b),"expected="+b+", actual="+a);}
    static void near(double a, double b) {yes(Math.abs(a-b)<1e-9,"expected="+b+", actual="+a);}
    static final UUID OWNER = UUID.fromString("87017064-74d7-327b-831e-270e2f005d93");
    static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    static Frame frame(Movement move, double yaw, String chosen, boolean inVehicle, UUID world, boolean packReported) {
        return new Frame(OWNER,world,new PlayerPose(new Vec3(0,64,0),yaw,move,true,packReported),inVehicle,chosen);
    }
    static Action only(WingsRigPlanner p, Frame f) {
        var updates=p.frame(f); eq(updates.size(),1); return updates.get(0).action();
    }
    public static void main(String[] args) {
        // Cardinal Minecraft conventions and inverse side: right is WEST at yaw 0.
        Vec3 southRight=worldOffset(0,1,0,0);
        near(southRight.x(),-1);near(southRight.z(),0);
        near(worldOffset(90,1,0,0).z(),-1);
        near(worldOffset(180,1,0,0).x(),1);
        near(worldOffset(270,1,0,0).z(),1);
        near(worldOffset(0,0,0,1).z(),-1);
        near(worldOffset(90,0,0,1).x(),1);
        for(int deg=0;deg<360;deg+=5) {
            Vec3 right=worldOffset(deg,1,0,0),back=worldOffset(deg,0,0,1);
            near(right.distance(new Vec3(0,0,0)),1);
            near(back.distance(new Vec3(0,0,0)),1);
            near(right.x()*back.x()+right.z()*back.z(),0);
        }
        WingsRigPlanner p=new WingsRigPlanner();
        Frame yes=frame(Movement.STANDING,0,"wings_nether",false,WORLD,true);
        eq(p.frame(yes).size(),0); // acceptance is explicit; not inferred from a field alone
        p.resourcePackAccepted(OWNER);
        eq(only(p,yes),Action.MOUNT);
        eq(p.mountedCount(),1);
        for(int i=0;i<200;i++)eq(p.frame(new Frame(OWNER,WORLD,new PlayerPose(new Vec3(i*.25,64,i*.75),0,Movement.STANDING,true,true),false,"wings_nether")).size(),0);
        eq(only(p,frame(Movement.STANDING,90,"wings_nether",false,WORLD,true)),Action.UPDATE_BODY_YAW);
        eq(only(p,frame(Movement.SNEAKING,90,"wings_nether",false,WORLD,true)),Action.UPDATE_BODY_YAW);
        var dimension=p.frame(frame(Movement.STANDING,90,"wings_nether",false,OTHER,true));
        eq(dimension.size(),2);
        eq(dimension.get(0).action(),Action.DISMOUNT);
        eq(dimension.get(1).action(),Action.MOUNT);
        eq(dimension.get(0).world(),WORLD);
        eq(dimension.get(1).world(),OTHER);
        eq(only(p,frame(Movement.STANDING,90,"wings_nether",true,OTHER,true)),Action.DISMOUNT);
        eq(p.frame(frame(Movement.STANDING,90,"wings_nether",true,OTHER,true)).size(),0);
        eq(only(p,frame(Movement.STANDING,90,"wings_nether",false,OTHER,true)),Action.MOUNT);
        eq(only(p,frame(Movement.SWIMMING,90,"wings_nether",false,OTHER,true)),Action.DISMOUNT);
        eq(only(p,frame(Movement.STANDING,90,"wings_nether",false,OTHER,true)),Action.MOUNT);
        eq(only(p,frame(Movement.GLIDING,90,"wings_nether",false,OTHER,true)),Action.DISMOUNT);
        eq(only(p,frame(Movement.STANDING,90,"wings_nether",false,OTHER,true)),Action.MOUNT);
        eq(only(p,frame(Movement.STANDING,90,"cape_scarlet",false,OTHER,true)),Action.DISMOUNT);
        eq(only(p,frame(Movement.STANDING,90,"wings_nether",false,OTHER,true)),Action.MOUNT);
        p.resourcePackLost(OWNER);
        eq(only(p,frame(Movement.STANDING,90,"wings_nether",false,OTHER,true)),Action.DISMOUNT);
        eq(p.frame(frame(Movement.STANDING,90,"wings_nether",false,OTHER,true)).size(),0);
        p.resourcePackAccepted(OWNER);
        eq(only(p,frame(Movement.STANDING,90,"wings_nether",false,OTHER,true)),Action.MOUNT);
        eq(p.disconnect(OWNER).size(),1);
        eq(p.disconnect(OWNER).size(),0);
        eq(p.mountedCount(),0);
        eq(p.frame(yes).size(),0);
        System.out.println("AXIOM_WARDROBE_WINGS_RIG_PLANNER_PASS checks="+checks);
    }
}
