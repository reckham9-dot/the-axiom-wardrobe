package online.theaxiom.wardrobe.probe;

import java.util.*;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import online.theaxiom.wardrobe.rig.WingsAttachmentCoordinator;
import online.theaxiom.wardrobe.rig.RigCalibration;
import online.theaxiom.wardrobe.rig.WingsRigPlanner;
import static online.theaxiom.wardrobe.rig.WearableRigCore.*;
import static online.theaxiom.wardrobe.rig.WingsRigPlanner.*;

/**
 * Isolated admin-only Paper test. NEVER takes over skin/cosmetic state or economy.
 * A single ItemDisplay mounts to the player only after the right resource pack
 * was reported SUCCESSFULLY_LOADED in the same session. No teleport chase.
 * 
 * This class requires compilation against the real Paper API; it is NOT a
 * deployable plugin until the external Maven build + live test succeeds.
 */
public final class AxiomRigProbePlugin extends JavaPlugin implements Listener, CommandExecutor {
    private static final String TAG = "axiomrig-probe-092";
    private final Map<UUID, UUID> lastPackId = new HashMap<>();
    private final Map<UUID, PlayerResourcePackStatusEvent.Status> lastPackStatus = new HashMap<>();
    // Important on servers with multiple packs: an unrelated pack cannot revoke
    // a successfully loaded AxiomWardrobe pack or stop /axiomrig show.
    private final Map<UUID, PlayerResourcePackStatusEvent.Status> requiredPackStatus = new HashMap<>();
    private final Set<UUID> requested = new HashSet<>();
    private WingsAttachmentCoordinator<ItemDisplay> rig;
    private UUID requiredPackId;
    private NamespacedKey itemModelKey;
    private boolean enabledProbe;
    private RigCalibration.Values tuning;
    private int mounts, removals, failures;

    @Override public void onEnable() {
        saveDefaultConfig();
        enabledProbe = getConfig().getBoolean("probe.enabled", false);
        String required = getConfig().getString("probe.required-pack-id", "").trim();
        try { requiredPackId = required.isBlank() ? null : UUID.fromString(required); }
        catch (IllegalArgumentException ex) { requiredPackId = null; getLogger().warning("Invalid probe.required-pack-id. Model output disabled."); }
        String key = getConfig().getString("probe.item-model", "theaxiom:wings_nether_probe");
        itemModelKey = NamespacedKey.fromString(key);
        if (itemModelKey == null) { enabledProbe = false; getLogger().severe("Invalid probe.item-model; model output disabled."); }
        try {
            tuning = RigCalibration.of(getConfig().getDouble("probe.x", 0),
                getConfig().getDouble("probe.y", -.35),
                getConfig().getDouble("probe.z", -.22),
                getConfig().getDouble("probe.scale", .55));
        } catch (IllegalArgumentException ex) {
            enabledProbe = false;
            tuning = new RigCalibration.Values(0, -.35f, -.22f, .55f);
            getLogger().severe("Invalid probe calibration. Probe disabled; fix config.yml before testing.");
        }
        rig = new WingsAttachmentCoordinator<>(new PaperPort());
        Objects.requireNonNull(getCommand("axiomrig"), "Missing axiomrig command").setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        // Only our own stale diagnostic entities are removed. Never touch other plugins' displays.
        Bukkit.getWorlds().forEach(world -> world.getEntitiesByClass(ItemDisplay.class).stream()
            .filter(e -> e.getScoreboardTags().contains(TAG)).forEach(ItemDisplay::remove));
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
        getLogger().info("Rig Probe 0.9.6 loaded. Enabled=" + enabledProbe +
            ", expected pack=" + (requiredPackId == null ? "NOT SET (fail-closed)" : requiredPackId));
    }
    @Override public void onDisable() {
        if (rig != null) {
            for (UUID id : new HashSet<>(requested)) rig.disconnect(id);
        }
        for (var world : Bukkit.getWorlds()) {
            for (var e : world.getEntitiesByClass(ItemDisplay.class))
                if (e.getScoreboardTags().contains(TAG)) e.remove();
        }
        requested.clear();lastPackId.clear();lastPackStatus.clear();requiredPackStatus.clear();
    }
    @EventHandler public void onJoin(PlayerJoinEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        rig.reset(id); requested.remove(id); lastPackId.remove(id); lastPackStatus.remove(id);requiredPackStatus.remove(id);
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        rig.disconnect(id); requested.remove(id); lastPackId.remove(id); lastPackStatus.remove(id);requiredPackStatus.remove(id);
    }
    @EventHandler public void onTeleport(PlayerTeleportEvent event) {
        UUID id=event.getPlayer().getUniqueId();
        rig.disconnect(id); requested.remove(id);
    }
    @EventHandler public void onWorldChange(PlayerChangedWorldEvent event) {
        UUID id=event.getPlayer().getUniqueId();
        rig.disconnect(id); requested.remove(id);
    }
    @EventHandler public void onPack(PlayerResourcePackStatusEvent event) {
        Player sourcePlayer = event.getPlayer();
        UUID id = sourcePlayer.getUniqueId();
        UUID pack = event.getID();
        PlayerResourcePackStatusEvent.Status status = event.getStatus();
        // A queued event from a disconnected client MUST NOT grant the next login
        // the previous session's pack acceptance, even for the same offline UUID.
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(this, () -> recordPackStatus(sourcePlayer, id, pack, status));
        } else recordPackStatus(sourcePlayer, id, pack, status);
    }
    private void recordPackStatus(Player sourcePlayer, UUID id, UUID pack,
                                  PlayerResourcePackStatusEvent.Status status) {
        if (!sourcePlayer.isOnline() || Bukkit.getPlayer(id) != sourcePlayer) return;
        lastPackId.put(id, pack);lastPackStatus.put(id, status);
        if (requiredPackId == null || !requiredPackId.equals(pack)) return;
        requiredPackStatus.put(id, status);
        if (status == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED)
            rig.resourcePackAccepted(id);
        else {
            // Important: resourcePackLost only invalidates the planner gate.
            // As requested is removed below, tick() would otherwise NEVER run
            // again for this user and leave the old ItemDisplay attached.
            // Disconnect removes the physical model immediately.
            rig.disconnect(id);
            requested.remove(id);
        }
    }
    private void tick() {
        if (!enabledProbe || requiredPackId == null || itemModelKey == null) return;
        for (UUID id : new HashSet<>(requested)) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) {rig.disconnect(id); requested.remove(id); continue;}
            boolean unsuitable = player.isDead() || player.isSwimming() || player.isGliding() || player.isSleeping()
                || player.isInsideVehicle();
            Movement movement = player.isSwimming() ? Movement.SWIMMING
                : player.isGliding() ? Movement.GLIDING
                : player.isSleeping() ? Movement.SLEEPING
                : player.isSneaking() ? Movement.SNEAKING : Movement.STANDING;
            Location loc = player.getLocation();
            PlayerPose pose = new PlayerPose(new Vec3(loc.getX(),loc.getY(),loc.getZ()),
                player.getBodyYaw(),movement,!player.isDead(),true);
            try {
                rig.frame(new Frame(id,player.getWorld().getUID(),pose,
                    player.isInsideVehicle(),unsuitable ? null : "wings_nether"));
            } catch (RuntimeException ex) {
                failures++; requested.remove(id); rig.disconnect(id);
                getLogger().warning("Rig disabled after rendering error ("+ex.getClass().getSimpleName()+").");
            }
        }
    }
    @Override public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("axiomrig.admin")) { sender.sendMessage("No permission."); return true; }
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sender.sendMessage("RigProbe: enabled="+enabledProbe+", requiredPack="+requiredPackId+
                ", active="+rig.attachedCount()+", mounts="+mounts+", removed="+removals+", failures="+failures);
            if (sender instanceof Player p) {
                UUID id=p.getUniqueId();
                sender.sendMessage("Last observed pack="+lastPackId.get(id)+", status="+lastPackStatus.get(id)
                    +", required pack status="+requiredPackStatus.get(id)
                    +", requested="+requested.contains(id)+", blocked="+rig.blocked(id));
                sender.sendMessage("Calibration: x="+tuning.x()+", y="+tuning.y()+
                    ", z="+tuning.z()+", scale="+tuning.scale());
            }
            return true;
        }
        if (!(sender instanceof Player player)) {sender.sendMessage("Use show/hide from a player; status works in console.");return true;}
        UUID id=player.getUniqueId();
        switch(args[0].toLowerCase(Locale.ROOT)) {
            case "tune" -> {
                RigCalibration.Values next;
                try { next = RigCalibration.parse(args,1); }
                catch (IllegalArgumentException bad) {
                    sender.sendMessage("Usage: /axiomrig tune <x> <y> <z> <scale> (offset -1.5..1.5, scale 0.05..1.5)");
                    return true;
                }
                tuning = next;
                getConfig().set("probe.x", (double)next.x());
                getConfig().set("probe.y", (double)next.y());
                getConfig().set("probe.z", (double)next.z());
                getConfig().set("probe.scale", (double)next.scale());
                saveConfig();
                // Remount the one probe, never move any other plugin's entities.
                // Preserve current session pack acceptance only if confirmed.
                rig.reset(id);
                if (requiredPackStatus.get(id) == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED)
                    rig.resourcePackAccepted(id);
                if (requested.contains(id)) tick();
                sender.sendMessage("Calibration applied and saved; values="+next);
            }
            case "show" -> {
                if (!enabledProbe || requiredPackId == null || itemModelKey == null) {
                    sender.sendMessage("Probe disabled or pack/model not configured."); return true;
                }
                if (requiredPackStatus.get(id) != PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) {
                    sender.sendMessage("Required pack not confirmed. Required="+requiredPackId+
                        " status="+requiredPackStatus.get(id)+"; last observed=" + lastPackId.get(id));
                    return true;
                }
                if (player.isInsideVehicle() || !player.getPassengers().isEmpty()) {
                    sender.sendMessage("Leave your vehicle/remove other passengers first; probe will not interfere.");return true;
                }
                rig.reset(id);rig.resourcePackAccepted(id);
                requested.add(id);tick();
                if (rig.attached(id).isPresent())
                    sender.sendMessage("Rig object mounted server-side. Use /axiomrig inspect and verify from a second client.");
                else if (rig.blocked(id))
                    sender.sendMessage("Rig object FAILED to mount; blocked for this session. Check server log and /axiomrig inspect.");
                else sender.sendMessage("Rig object not mounted (posture or resource-pack gate); use /axiomrig inspect.");
            }
            case "inspect" -> {
                sender.sendMessage("Pack state: expected="+requiredPackId+", observed="+lastPackId.get(id)+
                    ", requiredStatus="+requiredPackStatus.get(id));
                var current=rig.attached(id);
                if (current.isEmpty()) {
                    sender.sendMessage("Rig object: ABSENT (requested="+requested.contains(id)+
                        ", blocked="+rig.blocked(id)+")");
                } else {
                    ItemDisplay display=current.get();
                    var stack=display.getItemStack();
                    ItemMeta meta=stack.getItemMeta();
                    sender.sendMessage("Rig object: valid="+display.isValid()+
                        ", mountedToPlayer="+(display.getVehicle()==player)+
                        ", model="+(meta==null ? null : meta.getItemModel())+
                        ", transform="+display.getItemDisplayTransform()+
                        ", scale="+tuning.scale());
                    sender.sendMessage("Rig locations: player="+player.getLocation().toVector()+
                        ", display="+display.getLocation().toVector()+
                        ", riderCount="+player.getPassengers().size());
                }
            }
            case "hide" -> { requested.remove(id);rig.disconnect(id);sender.sendMessage("Rig probe removed."); }
            default -> sender.sendMessage("/axiomrig status | show | hide | inspect | tune <x> <y> <z> <scale>");
        }
        return true;
    }
    private final class PaperPort implements WingsAttachmentCoordinator.PassengerPort<ItemDisplay> {
        @Override public Optional<ItemDisplay> mount(Frame frame, String modelId, Anchor anchor) {
            Player player = Bukkit.getPlayer(frame.player());
            if (player == null || player.isInsideVehicle() || !player.getPassengers().isEmpty()) return Optional.empty();
            ItemStack model = new ItemStack(Material.CARROT_ON_A_STICK);
            ItemMeta meta = model.getItemMeta();
            if (meta == null || itemModelKey == null) return Optional.empty();
            meta.setItemModel(itemModelKey);
            if (!model.setItemMeta(meta)) return Optional.empty();
            ItemDisplay display = null;
            try {
                display = player.getWorld().spawn(player.getLocation(),ItemDisplay.class, d -> {
                    d.setPersistent(false);
                    d.setGravity(false);
                    d.setItemStack(model);
                    d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                    d.setBillboard(Display.Billboard.FIXED);
                    d.setTeleportDuration(0);
                    d.setInterpolationDuration(0);
                    d.setShadowRadius(0);
                    var calibration = tuning;
                    d.setTransformation(new Transformation(new Vector3f(calibration.x(),calibration.y(),calibration.z()),
                        new Quaternionf(),new Vector3f(calibration.scale(),calibration.scale(),calibration.scale()),new Quaternionf()));
                    d.addScoreboardTag(TAG);
                });
                if (!player.addPassenger(display)) {display.remove(); failures++;return Optional.empty();}
                display.setRotation((float) frame.pose().bodyYaw(), 0f);
                mounts++;
                return Optional.of(display);
            } catch (RuntimeException ex) {
                if (display != null) display.remove();failures++;
                getLogger().warning("Passenger attachment failed: " + ex.getClass().getSimpleName());
                return Optional.empty();
            }
        }
        @Override public boolean isMounted(ItemDisplay display,Frame frame) {
            Player player = Bukkit.getPlayer(frame.player());
            return player != null && player.isOnline() && display.isValid()
                && display.getWorld().equals(player.getWorld())
                && display.getVehicle() == player
                && player.getPassengers().contains(display);
        }
        @Override public boolean updatePose(ItemDisplay display,Frame frame,Anchor anchor) {
            if (!display.isValid()) return false;
            // No location teleport. Only align visual yaw with the player's torso.
            // Paper/client rendering may need further calibration; do not claim this is final.
            display.setRotation((float) frame.pose().bodyYaw(), 0f);
            return true;
        }
        @Override public void dismount(ItemDisplay display) {
            if (display.isValid()) {display.remove();removals++;}
        }
    }
}
