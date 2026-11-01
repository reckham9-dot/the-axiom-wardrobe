package online.theaxiom.wardrobe.rig;

import java.util.*;
import static online.theaxiom.wardrobe.rig.WingsRigPlanner.*;

/**
 * Server-side integration seam for ONE experimental Nether-wing model.
 * The platform port must implement *atomic* passenger mount, not a chase loop.
 * Failure is terminal for this session until an explicit reset, so broken
 * clients never get streams of orphan ItemDisplays or repeated mount attempts.
 */
public final class WingsAttachmentCoordinator<H> {
    public interface PassengerPort<H> {
        /** Spawn, set item_model and mount. Return empty unless all three succeed. */
        Optional<H> mount(Frame frame, String modelId, WearableRigCore.Anchor anchor);
        /**
         * Check the actual server entity, not only an earlier successful mount.
         * This is intentionally called on EVERY frame, including when body yaw
         * has not changed; other plugins or Minecraft may remove passengers.
         */
        boolean isMounted(H handle, Frame frame);
        /** Adjust visual rotation/torso offset without teleporting the entity. */
        boolean updatePose(H handle, Frame frame, WearableRigCore.Anchor anchor);
        /** Remove entity + passenger association. Must be safe to call twice. */
        void dismount(H handle);
    }
    private final PassengerPort<H> port;
    private final WingsRigPlanner planner = new WingsRigPlanner();
    private final Map<UUID,H> handles = new HashMap<>();
    private final Set<UUID> failed = new HashSet<>();
    public WingsAttachmentCoordinator(PassengerPort<H> port) { this.port = Objects.requireNonNull(port); }
    public boolean blocked(UUID owner) { return failed.contains(owner); }
    public int attachedCount() { return handles.size(); }
    /** Read-only observation for troubleshooting; never exposes an editable handle map. */
    public Optional<H> attached(UUID owner) { return Optional.ofNullable(handles.get(owner)); }
    public void resourcePackAccepted(UUID owner) { planner.resourcePackAccepted(owner); }
    public void resourcePackLost(UUID owner) { planner.resourcePackLost(owner); }

    /** Call on primary server thread only; all calls to port are synchronous. */
    public void frame(Frame snapshot) {
        if (failed.contains(snapshot.player())) return;
        // Do not trust cached Java handles after a server-side dismount or
        // external entity deletion. A lost passenger is a terminal visual
        // failure for this session; do not recreate it on every tick.
        H current = handles.get(snapshot.player());
        if (current != null) {
            boolean physicallyAttached;
            try { physicallyAttached = port.isMounted(current, snapshot); }
            catch (RuntimeException problem) { physicallyAttached = false; }
            if (!physicallyAttached) {failClosed(snapshot.player()); return;}
        }
        for (Change change : planner.frame(snapshot)) {
            switch (change.action()) {
                case DISMOUNT -> detach(change.player());
                case MOUNT -> {
                    detach(change.player());
                    try {
                        var maybe = port.mount(snapshot, change.model(), change.anchor());
                        if (maybe.isEmpty()) { failClosed(change.player()); break; }
                        handles.put(change.player(), maybe.get());
                    } catch (RuntimeException problem) { failClosed(change.player()); }
                }
                case UPDATE_BODY_YAW -> {
                    var handle = handles.get(change.player());
                    if (handle == null) { failClosed(change.player()); break; }
                    try {
                        if (!port.updatePose(handle,snapshot,change.anchor())) failClosed(change.player());
                    } catch (RuntimeException problem) { failClosed(change.player()); }
                }
            }
        }
    }
    private void detach(UUID owner) {
        H removed = handles.remove(owner);
        if (removed != null) port.dismount(removed);
    }
    private void failClosed(UUID owner) {
        detach(owner);
        planner.forget(owner);
        failed.add(owner);
    }
    /** Reset only after explicit reconnect or administrator action. */
    public void reset(UUID owner) {detach(owner);planner.forget(owner);failed.remove(owner);}
    public void disconnect(UUID owner) {
        detach(owner); planner.disconnect(owner); failed.remove(owner);
    }
}
