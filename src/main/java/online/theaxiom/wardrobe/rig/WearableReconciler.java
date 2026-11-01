package online.theaxiom.wardrobe.rig;

import java.util.*;
import static online.theaxiom.wardrobe.rig.WearableRigCore.*;

/** Small deterministic plan for entity adapters; never mutates Bukkit itself. */
public final class WearableReconciler {
    public enum Action { SPAWN, DESPAWN, UPDATE_MODEL, KEEP }
    public record Operation(Action action, Slot slot, String id) {}
    private final EnumMap<Slot,String> active = new EnumMap<>(Slot.class);
    private boolean initialized;
    public List<Operation> reconcile(Map<Slot,String> requested) {
        Objects.requireNonNull(requested);
        List<Operation> out = new ArrayList<>();
        for (Slot slot : Slot.values()) {
            String old = active.get(slot);
            String next = requested.get(slot);
            if (Objects.equals(old, next)) {
                if (next != null) out.add(new Operation(Action.KEEP, slot, next));
                continue;
            }
            if (next == null) {
                if (old != null) out.add(new Operation(Action.DESPAWN, slot, old));
                active.remove(slot);
            } else {
                if (old == null) out.add(new Operation(Action.SPAWN, slot, next));
                else out.add(new Operation(Action.UPDATE_MODEL, slot, next));
                active.put(slot, next);
            }
        }
        initialized = true;
        return List.copyOf(out);
    }
    public boolean initialized() { return initialized; }
    public Map<Slot,String> snapshot() { return Collections.unmodifiableMap(new EnumMap<>(active)); }
    public List<Operation> clear() { return reconcile(Map.of()); }
}
