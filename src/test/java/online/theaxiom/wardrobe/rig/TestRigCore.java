package online.theaxiom.wardrobe.rig;
import java.util.*;
import static online.theaxiom.wardrobe.rig.WearableRigCore.*;
import static online.theaxiom.wardrobe.rig.WearableReconciler.*;

public final class TestRigCore {
    private static int checks;
    static void near(double actual, double expected) {
        checks++;if (!Double.isFinite(actual)|| Math.abs(actual-expected)>1e-5)
            throw new AssertionError("expected "+expected+" got "+actual);
    }
    static void eq(Object actual,Object expected) {
        checks++;if(!Objects.equals(actual,expected))throw new AssertionError("expected "+expected+" got "+actual);
    }
    static void yes(boolean v) { checks++;if(!v)throw new AssertionError("condition failed: test "+checks); }
    static PlayerPose pose(double x,double y,double z,double yaw,Movement m) {
        return new PlayerPose(new Vec3(x,y,z),yaw,m,true,true);
    }
    public static void main(String[] args) {
        var origin = pose(10,70,-13,0,Movement.STANDING);
        var a0 = backAnchor(origin).orElseThrow();
        near(a0.world().x(),10);near(a0.world().y(),71.29);near(a0.world().z(),-13.215);
        near(a0.bodyYaw(),0);near(a0.scale(),.56);
        for(int yaw: new int[]{0,30,90,120,180,240,270,330,360}) {
            var a = backAnchor(pose(10,70,-13,yaw,Movement.STANDING)).orElseThrow();
            near(a.world().distance(new Vec3(10,71.29,-13)),.215);
            near(a.bodyYaw(),yaw);
        }
        near(backAnchor(pose(10,70,-13,90,Movement.STANDING)).orElseThrow().world().x(),10.215);
        near(backAnchor(pose(10,70,-13,180,Movement.STANDING)).orElseThrow().world().z(),-12.785);
        near(backAnchor(pose(10,70,-13,270,Movement.STANDING)).orElseThrow().world().x(),9.785);
        for (Movement m : new Movement[]{Movement.SNEAKING,Movement.SWIMMING,Movement.GLIDING}) {
            var anch = backAnchor(pose(10,70,-13,0,m)).orElseThrow();
            near(anch.world().y(),70+(m == Movement.SNEAKING ? 1.03 : .57));
        }
        yes(backAnchor(pose(0,0,0,0,Movement.SLEEPING)).isEmpty());
        yes(backAnchor(new PlayerPose(new Vec3(0,0,0),0,Movement.STANDING,false,true)).isEmpty());
        yes(backAnchor(new PlayerPose(new Vec3(0,0,0),0,Movement.STANDING,true,false)).isEmpty());
        for (int angle=0;angle<360;angle+=15) {
            var offset = worldOffset(angle,0.1,.4,.2);
            near(Math.hypot(offset.x(),offset.z()),Math.hypot(.1,.2));
            near(offset.y(),.4);
            var a=backAnchor(pose(0,0,0,angle,Movement.STANDING)).orElseThrow();
            var shifted=backAnchor(pose(53,-14,21,angle,Movement.STANDING)).orElseThrow();
            near(shifted.world().subtract(a.world()).x(),53);
            near(shifted.world().subtract(a.world()).y(),-14);
            near(shifted.world().subtract(a.world()).z(),21);
        }
        eq(visibleBack(EnumSet.allOf(BackSlot.class)),Optional.of(BackSlot.WINGS));
        eq(visibleBack(EnumSet.of(BackSlot.CAPE,BackSlot.BACKPACK)),Optional.of(BackSlot.CAPE));
        eq(visibleBack(EnumSet.of(BackSlot.BACKPACK)),Optional.of(BackSlot.BACKPACK));
        eq(visibleBack(EnumSet.noneOf(BackSlot.class)),Optional.empty());
        eq(localModelTranslation(BackSlot.WINGS),new Vec3(0,0,0));
        var all=Map.of("wings","wings_nether","cape","cape_scarlet","backpack","backpack_ender", "hat","hat_crown","pet","pet_star","aura","aura_magic");
        var visible=visibleChoices(origin,all);
        eq(visible.get(Slot.BACK),"wings_nether");
        eq(visible.get(Slot.HAT),"hat_crown");
        eq(visible.get(Slot.PET),"pet_star");
        eq(visible.get(Slot.AURA),"aura_magic");
        eq(visible.size(),4);
        var withoutPack=visibleChoices(new PlayerPose(origin.feet(),0,Movement.STANDING,true,false),all);
        eq(withoutPack,Map.of(Slot.AURA,"aura_magic"));
        eq(visibleChoices(new PlayerPose(origin.feet(),0,Movement.STANDING,false,true),all),Map.of());
        var withoutWings=new HashMap<>(all);withoutWings.remove("wings");
        eq(visibleChoices(origin, withoutWings).get(Slot.BACK),"cape_scarlet");
        withoutWings.remove("cape");
        eq(visibleChoices(origin, withoutWings).get(Slot.BACK),"backpack_ender");
        var reconciler=new WearableReconciler();
        var initial=reconciler.reconcile(visible);
        eq(initial.size(),4);
        yes(initial.stream().allMatch(o->o.action()==Action.SPAWN));
        yes(reconciler.initialized());
        var stable=reconciler.reconcile(visible);
        eq(stable.size(),4);
        yes(stable.stream().allMatch(o->o.action()==Action.KEEP));
        var changed=new EnumMap<Slot,String>(visible);changed.put(Slot.BACK,"cape_shadow");changed.remove(Slot.HAT);
        var diffs=reconciler.reconcile(changed);
        eq(diffs.size(),4);
        eq(diffs.stream().filter(o->o.action()==Action.UPDATE_MODEL).count(),1L);
        eq(diffs.stream().filter(o->o.action()==Action.DESPAWN).count(),1L);
        eq(reconciler.snapshot().get(Slot.BACK),"cape_shadow");
        var clear=reconciler.clear();eq(clear.size(),3);
        yes(clear.stream().allMatch(o->o.action()==Action.DESPAWN));
        eq(reconciler.snapshot(),Map.of());
        eq(reconciler.clear().size(),0);
        // Simulated movement cannot spawn duplicate back entities when the selection is unchanged.
        var r2=new WearableReconciler();
        var target=Map.of(Slot.BACK,"wings_nether");
        eq(r2.reconcile(target).get(0).action(),Action.SPAWN);
        for (int i=0;i<200;i++) {
            var events=r2.reconcile(target);
            eq(events.size(),1);eq(events.get(0).action(),Action.KEEP);
        }
        System.out.println("AXIOM_WARDROBE_RIG_CORE_PASS checks="+checks);
    }
}
