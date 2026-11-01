package online.theaxiom.wardrobe.rig;

import java.util.*;
import static online.theaxiom.wardrobe.rig.WearableRigCore.*;
import static online.theaxiom.wardrobe.rig.WingsRigPlanner.*;

public class TestWingsAttachmentCoordinator {
    static int checks;
    static void eq(Object a,Object b) {checks++;if (!Objects.equals(a,b)) throw new AssertionError("expected "+b+", actual "+a+", #"+checks);}
    static class SimulatedPaper implements WingsAttachmentCoordinator.PassengerPort<Integer> {
        int mounts, removals, poses; boolean rejectMount, rejectPose;
        final Set<Integer> live = new HashSet<>();
        public Optional<Integer> mount(Frame frame,String id,Anchor anchor) {
            if (rejectMount) return Optional.empty();
            mounts++;int h=mounts;live.add(h);return Optional.of(h);
        }
        public boolean isMounted(Integer h,Frame frame) {return live.contains(h);}
        public boolean updatePose(Integer h,Frame frame,Anchor a) {poses++;return !rejectPose && live.contains(h);}
        public void dismount(Integer h) {removals++;live.remove(h);}
    }
    static final UUID P=UUID.fromString("87017064-74d7-327b-831e-270e2f005d93"),
        WORLD=UUID.fromString("00000000-0000-0000-0000-000000000001"),
        OTHER=UUID.fromString("00000000-0000-0000-0000-000000000002");
    static Frame f(double x,double yaw,Movement posture,UUID world) {
        return new Frame(P,world,new PlayerPose(new Vec3(x,80,0),yaw,posture,true,true),false,"wings_nether");
    }
    public static void main(String[] args) {
        var backend=new SimulatedPaper();
        var c=new WingsAttachmentCoordinator<Integer>(backend);
        c.frame(f(0,0,Movement.STANDING,WORLD));eq(backend.mounts,0);eq(c.attached(P),Optional.empty());
        c.resourcePackAccepted(P);
        c.frame(f(0,0,Movement.STANDING,WORLD));eq(backend.mounts,1);eq(c.attached(P),Optional.of(1));
        for (int i=0;i<500;i++)c.frame(f(i/10.0,0,Movement.STANDING,WORLD));
        eq(backend.mounts,1);eq(backend.poses,0);eq(backend.live.size(),1);eq(c.attached(P),Optional.of(1));
        c.frame(f(10,90,Movement.STANDING,WORLD));eq(backend.poses,1);
        c.frame(f(10,90,Movement.SNEAKING,WORLD));eq(backend.poses,2);
        c.frame(f(10,90,Movement.STANDING,OTHER));eq(backend.mounts,2);
        eq(backend.removals,1);eq(backend.live.size(),1);
        c.frame(f(10,90,Movement.SWIMMING,OTHER));eq(backend.removals,2);
        c.frame(f(10,90,Movement.STANDING,OTHER));eq(backend.mounts,3);
        c.resourcePackLost(P);c.frame(f(10,90,Movement.STANDING,OTHER));eq(backend.removals,3);
        c.disconnect(P);eq(c.attachedCount(),0);eq(backend.live.size(),0);eq(c.attached(P),Optional.empty());
        // Repeated mount failure must not churn entities, even after 1000 ticks.
        backend.rejectMount=true;c.resourcePackAccepted(P);c.frame(f(0,0,Movement.STANDING,WORLD));
        eq(c.blocked(P),true);eq(c.attachedCount(),0);eq(c.attached(P),Optional.empty());
        for (int i=0;i<1000;i++)c.frame(f(i/100.0,0,Movement.STANDING,WORLD));
        eq(backend.mounts,3);eq(backend.live.size(),0);
        backend.rejectMount=false;c.reset(P);c.resourcePackAccepted(P);
        c.frame(f(0,0,Movement.STANDING,WORLD));eq(backend.mounts,4);
        backend.rejectPose=true;c.frame(f(0,45,Movement.STANDING,WORLD));
        eq(c.blocked(P),true);eq(backend.live.size(),0);
        eq(c.attachedCount(),0);
        c.disconnect(P);eq(c.blocked(P),false);
        // Regression: external plugin or server removed the ItemDisplay
        // while the player's yaw never changed. Old probe falsely counted it
        // as mounted forever. No respawn, no entity churn, fail closed.
        var external=new SimulatedPaper();
        var observer=new WingsAttachmentCoordinator<Integer>(external);
        observer.resourcePackAccepted(P);
        observer.frame(f(0,0,Movement.STANDING,WORLD));
        eq(external.mounts,1);eq(observer.attached(P),Optional.of(1));
        external.live.clear(); // server / another plugin removed passenger
        observer.frame(f(0,0,Movement.STANDING,WORLD));
        eq(observer.blocked(P),true);eq(observer.attachedCount(),0);
        eq(observer.attached(P),Optional.empty());
        for (int i=0; i<1000; i++) observer.frame(f(i*0.1,0,Movement.STANDING,WORLD));
        eq(external.mounts,1);eq(external.live.size(),0);
        observer.disconnect(P);eq(observer.blocked(P),false);
        observer.resourcePackAccepted(P);
        observer.frame(f(0,0,Movement.STANDING,WORLD));
        eq(external.mounts,2);eq(observer.blocked(P),false);
        observer.disconnect(P);eq(external.live.size(),0);
        System.out.println("AXIOM_WARDROBE_ATTACHMENT_COORDINATOR_PASS checks="+checks);
    }
}
