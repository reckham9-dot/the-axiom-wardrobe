package online.theaxiom.wardrobe;

/**
 * Exact JSON POST contract expected by /api/wardrobe_v1.php?action=server_set_cosmetic.
 * Called from BridgeClient.setCosmetic in AxiomWardrobe 0.8.1-bridge-jsonfix.
 * Validation mirrors BridgeClient's existing kind/item-ID allowlists.
 */
public final class WardrobeJsonBody {
    private WardrobeJsonBody() {}
    public static String encode(String kind, String itemId) {
        if (kind == null || !kind.matches("[a-z]{2,12}")
                || itemId == null || !itemId.matches("[a-z0-9_]{1,40}")) {
            throw new IllegalArgumentException("Bad cosmetics id");
        }
        return "{\"kind\":\"" + kind + "\",\"item_id\":\"" + itemId + "\"}";
    }
}
