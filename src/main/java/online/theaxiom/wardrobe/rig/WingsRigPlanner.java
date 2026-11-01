package online.theaxiom.wardrobe.rig;

import java.util.*;
import static online.theaxiom.wardrobe.rig.WearableRigCore.*;

/**
 * Deterministic one-model migration gate: deliberately only Nether wings.
 * Paper adapter MUST fail closed when mounting is rejected: never replace a
 * failed mount with a delayed teleporting ItemDisplay ('flying textures').
 * No Bukkit imports, no economy, no SQL, no player inventory modifications.
 */
public final class WingsRigPlanner {
    public static final String TEST_MODEL_ID = "theaxiom:wings_nether_probe";
    public enum Action { MOUNT, UPDATE_BODY_YAW, DISMOUNT }
    public record Frame(UUID player, UUID world, PlayerPose pose, boolean riding, String equippedWing) {
        public Frame { Objects.requireNonNull(player); Objects.requireNonNull(world); Objects.requireNonNull(pose); }
    }
    public record Change(Action action, UUID player, UUID world, String model, Anchor anchor) { }
    private record Mounted(UUID world, String model, double yaw, Movement movement) { }
    private final Map<UUID, Mounted> mounted = new HashMap<>();
    private final Set<UUID> explicitlyAccepted = new HashSet<>();

    /** Call only after a trusted pack-status event for this player's current session. */
    public void resourcePackAccepted(UUID player) { explicitlyAccepted.add(player); }
    public void resourcePackLost(UUID player) { explicitlyAccepted.remove(player); }
    public void forget(UUID player) { explicitlyAccepted.remove(player); mounted.remove(player); }
    public int mountedCount() { return mounted.size(); }

    public List<Change> frame(Frame f) {
        final UUID owner = f.player();
        final Mounted previous = mounted.get(owner);
        // No visual output for untested postures; do NOT interpolate bad rig transforms.
        final boolean suitablePose = f.pose().movement() == Movement.STANDING
            || f.pose().movement() == Movement.SNEAKING;
        final boolean allowed = explicitlyAccepted.contains(owner) && !f.riding()
            && suitablePose && canRender(f.pose())
            && "wings_nether".equals(f.equippedWing());
        if (!allowed) {
            mounted.remove(owner);
            return previous == null ? List.of() : List.of(new Change(Action.DISMOUNT, owner, previous.world(), previous.model(), null));
        }
        final Anchor anchor = backAnchor(f.pose()).orElseThrow();
        if (previous == null) {
            mounted.put(owner,new Mounted(f.world(),TEST_MODEL_ID,f.pose().bodyYaw(),f.pose().movement()));
            return List.of(new Change(Action.MOUNT, owner, f.world(), TEST_MODEL_ID,anchor));
        }
        if (!previous.world().equals(f.world()) || !previous.model().equals(TEST_MODEL_ID)) {
            mounted.put(owner,new Mounted(f.world(),TEST_MODEL_ID,f.pose().bodyYaw(),f.pose().movement()));
            return List.of(new Change(Action.DISMOUNT,owner,previous.world(),previous.model(),null),
                new Change(Action.MOUNT,owner,f.world(),TEST_MODEL_ID,anchor));
        }
        if (previous.yaw() != f.pose().bodyYaw() || previous.movement() != f.pose().movement()) {
            mounted.put(owner,new Mounted(f.world(),TEST_MODEL_ID,f.pose().bodyYaw(),f.pose().movement()));
            return List.of(new Change(Action.UPDATE_BODY_YAW,owner,f.world(),TEST_MODEL_ID,anchor));
        }
        return List.of(); // passenger position handled by server; no teleports on ordinary movement
    }

    public List<Change> disconnect(UUID player) {
        final Mounted removed = mounted.remove(player);
        explicitlyAccepted.remove(player);
        return removed == null ? List.of() : List.of(new Change(Action.DISMOUNT,player,removed.world(),removed.model(),null));
    }
}
