package online.theaxiom.wardrobe.rig;

import java.util.*;

/**
 * Original, Paper-agnostic wearable rig. All measurements are in Minecraft blocks.
 * A renderer must attach the resolved item model to the corresponding anchor; it
 * must not apply these translations both to an entity location and a model transform.
 * This library intentionally does not spawn entities or touch player state.
 */
public final class WearableRigCore {
    private WearableRigCore() {}
    public enum Movement { STANDING, SNEAKING, SWIMMING, GLIDING, SLEEPING }
    public enum BackSlot { WINGS, CAPE, BACKPACK }
    public enum Slot { BACK, HAT, AURA, PET }
    public record Vec3(double x, double y, double z) {
        public Vec3 add(Vec3 o) { return new Vec3(x + o.x, y + o.y, z + o.z); }
        public Vec3 subtract(Vec3 o) { return new Vec3(x - o.x, y - o.y, z - o.z); }
        public double distance(Vec3 other) { var d = subtract(other); return Math.sqrt(d.x*d.x+d.y*d.y+d.z*d.z); }
    }
    public record PlayerPose(Vec3 feet, double bodyYaw, Movement movement, boolean alive, boolean resourcePackAccepted) {
        public PlayerPose { Objects.requireNonNull(feet); Objects.requireNonNull(movement); }
    }
    public record Anchor(Vec3 world, double bodyYaw, float scale) {}

    /** Priority only controls visibility; it does not erase equipped slots in storage. */
    public static Optional<BackSlot> visibleBack(Set<BackSlot> equipped) {
        for (BackSlot slot : BackSlot.values()) if (equipped.contains(slot)) return Optional.of(slot);
        return Optional.empty();
    }

    public static boolean canRender(PlayerPose player) {
        return player.alive() && player.resourcePackAccepted() && player.movement() != Movement.SLEEPING;
    }

    /**
     * Maps local coordinates (right, up, backwards) to Minecraft world coordinates.
     * At yaw 0 the player faces +Z (south), therefore player-right is -X
     * (west), not +X. At yaw 90 the player faces -X and right is -Z.
     * Only use this for mathematical planning, never both as world teleport and
     * as model-space translation; that would double the offset.
     */
    public static Vec3 worldOffset(double bodyYawDegrees, double right, double up, double back) {
        double theta = Math.toRadians(bodyYawDegrees);
        return new Vec3(
            -Math.cos(theta) * right + Math.sin(theta) * back,
            up,
            -Math.sin(theta) * right - Math.cos(theta) * back
        );
    }
    private static double torsoY(Movement movement) {
        return switch (movement) {
            case STANDING -> 1.29;
            case SNEAKING -> 1.03;
            case SWIMMING, GLIDING -> 0.57;
            case SLEEPING -> 0.0;
        };
    }
    /** Returns empty for a state that must not display the model. */
    public static Optional<Anchor> backAnchor(PlayerPose player) {
        if (!canRender(player)) return Optional.empty();
        return Optional.of(new Anchor(
            player.feet().add(worldOffset(player.bodyYaw(), 0.0, torsoY(player.movement()), 0.215)),
            player.bodyYaw(),
            0.56f));
    }

    /**
     * Return a model-local offset relative to a *single* chosen attachment origin.
     * Never also add this offset to the entity location after applying the transform.
     */
    public static Vec3 localModelTranslation(BackSlot slot) {
        return switch (slot) {
            case WINGS -> new Vec3(0, 0, 0);
            case CAPE -> new Vec3(0, -0.045, -0.025);
            case BACKPACK -> new Vec3(0, 0.015, 0.065);
        };
    }

    public static Map<Slot, String> visibleChoices(PlayerPose player, Map<String,String> equipped) {
        if (!player.alive()) return Map.of();
        LinkedHashMap<Slot,String> visible = new LinkedHashMap<>();
        if (player.resourcePackAccepted() && player.movement() != Movement.SLEEPING) {
            for (var candidate: List.of("wings", "cape", "backpack")) {
                String id = equipped.get(candidate);
                if (id != null && !id.isBlank()) { visible.put(Slot.BACK, id); break; }
            }
            String hat = equipped.get("hat");
            if (hat != null && !hat.isBlank()) visible.put(Slot.HAT, hat);
            String pet = equipped.get("pet");
            if (pet != null && !pet.isBlank()) visible.put(Slot.PET, pet);
        }
        String aura = equipped.get("aura");
        if (aura != null && !aura.isBlank()) visible.put(Slot.AURA, aura);
        return Collections.unmodifiableMap(visible);
    }
}
