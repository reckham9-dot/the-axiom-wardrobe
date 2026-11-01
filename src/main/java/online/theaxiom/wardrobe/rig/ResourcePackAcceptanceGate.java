package online.theaxiom.wardrobe.rig;
import java.util.*;
/**
 * The server can send multiple packs. A client accepting/downloaded another
 * pack must never authorize an AxiomWardrobe model. Paper event#getID must
 * match the configured ID of the merged ResourcePackManager resource pack.
 */
public final class ResourcePackAcceptanceGate {
    public enum Status { ACCEPTED, DOWNLOADED, SUCCESSFULLY_LOADED, DECLINED, DISCARDED, FAILED_DOWNLOAD, FAILED_RELOAD, INVALID_URL }
    private final UUID requiredPack;
    private final Set<UUID> accepted = new HashSet<>();
    public ResourcePackAcceptanceGate(UUID requiredPack) { this.requiredPack = Objects.requireNonNull(requiredPack); }
    public void onStatus(UUID player, UUID packId, Status status) {
        Objects.requireNonNull(player); Objects.requireNonNull(packId); Objects.requireNonNull(status);
        if (!requiredPack.equals(packId)) return;
        if (status == Status.SUCCESSFULLY_LOADED) accepted.add(player);
        else accepted.remove(player); // downloaded != applied: fail closed
    }
    public boolean mayRender(UUID player) { return accepted.contains(player); }
    public void onQuit(UUID player) { accepted.remove(player); }
    public int acceptedCount() { return accepted.size(); }
}
