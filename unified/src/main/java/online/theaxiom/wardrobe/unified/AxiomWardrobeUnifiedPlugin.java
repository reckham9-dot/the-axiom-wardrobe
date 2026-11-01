package online.theaxiom.wardrobe.unified;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.plugin.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.*;
import java.util.*;
import java.util.logging.*;

/**
 * One UI for The Axiom skins + cosmetics.
 * Source of truth: installed AxiomWardrobe 0.8 (skin/SQL/site).
 * Renderer: HMCCosmetics (uses its actual BACKPACK/HELMET/BALLOON API).
 * Does not create equipment in any player inventory or modify Vault.
 */
public final class AxiomWardrobeUnifiedPlugin extends JavaPlugin implements Listener, CommandExecutor {
    private static final List<String> TYPES=List.of("cape","wings","hat","aura","backpack","pet");
    private static final String[] IDS={
        "cape_scarlet","cape_royal","cape_shadow",
        "wings_elytra","wings_angel","wings_nether",
        "hat_crown","hat_witch","hat_ice",
        "aura_flame","aura_magic","aura_snow",
        "backpack_chest","backpack_ender","pet_star","pet_bee"
    };
    private static final String[] KINDS={
        "cape","cape","cape","wings","wings","wings","hat","hat","hat",
        "aura","aura","aura","backpack","backpack","pet","pet"
    };
    private static final String[] NAMES={
        "Алый плащ","Королевский плащ","Теневой плащ",
        "Крылья странника","Крылья ангела","Крылья Незера",
        "Золотая корона","Шляпа ведьмы","Ледяной венец",
        "Огненная аура","Магическая аура","Снежная аура",
        "Рюкзак путешественника","Рюкзак Энда","Звёздный спутник","Пчела-спутник"
    };
    private static final Map<String,String> LABELS=Map.of(
        "cape","Плащи","wings","Крылья","hat","Головные уборы",
        "aura","Ауры","backpack","Рюкзаки","pet","Спутники");
    private static final Map<String,Material> ICONS=Map.of(
        "cape",Material.WHITE_BANNER,"wings",Material.FEATHER,
        "hat",Material.GOLDEN_HELMET,"aura",Material.BLAZE_POWDER,
        "backpack",Material.CHEST,"pet",Material.BEE_SPAWN_EGG);
    private Plugin wardrobe;
    private Field choicesField;
    private HmcAdapter cosmetics;
    private final Map<UUID,Map<String,String>> displayed=new HashMap<>();
    private int successes,waiting,failures,task=-1;

    @Override public void onEnable(){
        saveDefaultConfig();
        wardrobe=Bukkit.getPluginManager().getPlugin("AxiomWardrobe");
        Plugin hmc=Bukkit.getPluginManager().getPlugin("HMCCosmetics");
        if(wardrobe==null||!wardrobe.isEnabled()||hmc==null||!hmc.isEnabled()){
            getLogger().severe("Requires enabled AxiomWardrobe + HMCCosmetics");
            Bukkit.getPluginManager().disablePlugin(this);return;
        }
        try {
            choicesField=wardrobe.getClass().getDeclaredField("equipped");
            choicesField.setAccessible(true);
            cosmetics=new HmcAdapter(hmc);
        }catch(ReflectiveOperationException ex){
            getLogger().log(Level.SEVERE,"Unsupported AxiomWardrobe/HMCCosmetics version",ex);
            Bukkit.getPluginManager().disablePlugin(this);return;
        }
        Objects.requireNonNull(getCommand("axiomwardrobe")).setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this,this);
        begin();
        getLogger().info("Unified wardrobe enabled: skins and site retained; "+IDS.length+" cosmetics in GUI.");
    }
    @Override public void onDisable(){
        if(task>=0) Bukkit.getScheduler().cancelTask(task);
        displayed.clear();
    }
    private void begin(){
        if(task>=0) Bukkit.getScheduler().cancelTask(task);
        long interval=Math.max(20,Math.min(1200,getConfig().getLong("backend.sync-ticks",40)));
        task=Bukkit.getScheduler().scheduleSyncRepeatingTask(this,this::syncOnline,80L,interval);
    }
    private void syncOnline(){
        for(Player p:Bukkit.getOnlinePlayers()){
            try {sync(p);} catch(Exception ex) {
                failures++;
                if(failures<5||failures%100==0)
                    getLogger().warning("Failed to sync cosmetics: "+ex.getClass().getSimpleName());
            }
        }
    }
    @SuppressWarnings("unchecked")
    private Map<String,String> choices(Player p){
        try{
            Object obj=choicesField.get(wardrobe);
            if(!(obj instanceof Map<?,?> map)) return Map.of();
            Object value=map.get(p.getUniqueId());
            if(!(value instanceof Map<?,?> state)) return Map.of();
            Map<String,String> result=new HashMap<>();
            for(var e:state.entrySet())
                if(e.getKey() instanceof String k&&e.getValue() instanceof String v&&TYPES.contains(k))
                    result.put(k,v);
            return result;
        }catch(IllegalAccessException ex){throw new IllegalStateException("Could not read AxiomWardrobe selections",ex);}
    }
    private String mapping(String id){
        if(id==null||Arrays.stream(IDS).noneMatch(id::equals))return "";
        String key=getConfig().getString("backend.mappings."+id,"").trim();
        return key.matches("[a-z0-9_-]{1,64}")?key:"";
    }
    private String selectedBack(Map<String,String> state){
        // One backend BACKPACK slot: wings first, then cape, then backpack.
        for(String k:List.of("wings","cape","backpack")){
            String id=state.get(k);
            if(id!=null&&!id.isBlank()&&!id.equals("none"))return id;
        }
        return "";
    }
    private void sync(Player p){
        var state=choices(p);
        var last=displayed.computeIfAbsent(p.getUniqueId(),id->new HashMap<>());
        Map<String,String> desired=Map.of(
            "BACKPACK",mapping(selectedBack(state)),
            "BALLOON",mapping(state.get("pet")),
            "HELMET",(p.getGameMode()==GameMode.CREATIVE&&getConfig().getBoolean("backend.hide-helmet-in-creative",true))
                ?"":mapping(state.get("hat"))
        );
        for(String slot:List.of("BACKPACK","BALLOON","HELMET")){
            String next=desired.get(slot);
            String prev=last.getOrDefault(slot,"");
            if(prev.equals(next))continue;
            // Never remove third-party cosmetics we have not applied.
            if(!prev.isBlank()){
                if(!cosmetics.unequip(p,slot)){failures++;continue;}
                last.remove(slot);
            }
            if(next.isEmpty())continue;
            if(!cosmetics.exists(next)){waiting++;continue;}
            if(cosmetics.equip(p,next,slot)){last.put(slot,next);successes++;}
            else failures++;
        }
    }
    @EventHandler public void join(PlayerJoinEvent event){
        UUID id=event.getPlayer().getUniqueId();
        displayed.remove(id);
        Bukkit.getScheduler().runTaskLater(this,()->{
            Player p=Bukkit.getPlayer(id);if(p!=null&&p.isOnline())sync(p);
        },100L);
    }
    @EventHandler public void leave(PlayerQuitEvent event){displayed.remove(event.getPlayer().getUniqueId());}

    private class StyleHolder implements InventoryHolder{
        final UUID player; final String page;
        final Map<Integer,String> buttons=new HashMap<>();
        Inventory inventory;
        StyleHolder(UUID id,String page){this.player=id;this.page=page;}
        public Inventory getInventory(){return inventory;}
    }
    private ItemStack icon(Material type,String title,String... lines){
        ItemStack stack=new ItemStack(type);
        ItemMeta meta=stack.getItemMeta();
        if(meta!=null){
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&',title));
            List<String> lore=new ArrayList<>();
            for(String line:lines)lore.add(ChatColor.translateAlternateColorCodes('&',line));
            meta.setLore(lore);stack.setItemMeta(meta);
        }
        return stack;
    }
    private void main(Player p){
        StyleHolder h=new StyleHolder(p.getUniqueId(),"main");
        h.inventory=Bukkit.createInventory(h,27,ChatColor.translateAlternateColorCodes('&',
            getConfig().getString("ui.main-title","&dThe Axiom &8| &fГардероб")));
        h.inventory.setItem(4,icon(Material.PLAYER_HEAD,"&bСкины","&7Каталог из 93 подписанных скинов","&7Синхронизация с личным кабинетом"));
        String[] types={"cape","wings","hat","aura","backpack","pet"};
        int[] slots={10,11,12,14,15,16};
        for(int i=0;i<types.length;i++){
            String kind=types[i];int slot=slots[i];
            h.inventory.setItem(slot,icon(ICONS.get(kind),"&d"+LABELS.get(kind),
                "&7Выбор украшения и снятие","&7Рендерер: HMCCosmetics"));
            h.buttons.put(slot,kind);
        }
        h.inventory.setItem(22,icon(Material.BOOK,"&eThe Axiom",
            "&7Единый каталог косметики","&7Экономика и инвентарь не изменяются"));
        p.openInventory(h.inventory);
    }
    private void category(Player p,String kind){
        if(!TYPES.contains(kind))return;
        StyleHolder h=new StyleHolder(p.getUniqueId(),kind);
        h.inventory=Bukkit.createInventory(h,36,ChatColor.DARK_PURPLE+"The Axiom | "+LABELS.get(kind));
        String equipped=choices(p).get(kind);
        int pos=10;
        for(int i=0;i<IDS.length;i++){
            if(!KINDS[i].equals(kind))continue;
            String id=IDS[i];
            boolean wearing=id.equals(equipped);
            h.inventory.setItem(pos,icon(ICONS.get(kind),(wearing?"&a✔ ":"&d")+NAMES[i],
                wearing?"&aВыбрано":"&7Не выбрано",
                mapping(id).isEmpty()?"&eОжидает модель HMCCosmetics":"&aМодель подключена",
                "&7Нажми для выбора"));
            h.buttons.put(pos,id);pos++;
        }
        h.inventory.setItem(27,icon(Material.ARROW,"&eНазад"));
        h.inventory.setItem(31,icon(Material.BARRIER,"&cСнять украшение"));
        p.openInventory(h.inventory);
    }
    @EventHandler public void click(InventoryClickEvent e){
        if(!(e.getView().getTopInventory().getHolder() instanceof StyleHolder h))return;
        e.setCancelled(true);
        if(!(e.getWhoClicked() instanceof Player p)||!h.player.equals(p.getUniqueId()))return;
        int slot=e.getRawSlot();
        if(slot<0||slot>=e.getView().getTopInventory().getSize())return;
        if(h.page.equals("main")){
            if(slot==4){Bukkit.getScheduler().runTask(this,()->p.performCommand("wardrobe"));return;}
            String page=h.buttons.get(slot);
            if(page!=null)Bukkit.getScheduler().runTask(this,()->category(p,page));
        }else if(slot==27){
            Bukkit.getScheduler().runTask(this,()->main(p));
        }else if(slot==31){
            Bukkit.getScheduler().runTask(this,()->{
                p.performCommand("wardrobe unequip "+h.page);
                sync(p);category(p,h.page);
            });
        }else {
            String id=h.buttons.get(slot);
            if(id!=null){
                Bukkit.getScheduler().runTask(this,()->{
                    p.performCommand("wardrobe equip "+id);
                    sync(p);category(p,h.page);
                });
            }
        }
    }
    @EventHandler public void drag(InventoryDragEvent e){
        if(e.getView().getTopInventory().getHolder() instanceof StyleHolder)e.setCancelled(true);
    }
    @Override public boolean onCommand(CommandSender sender,Command cmd,String label,String[] args){
        if(args.length>0){
            switch(args[0].toLowerCase(Locale.ROOT)){
                case "status" -> {
                    sender.sendMessage("§bUnified 1.0 RC1: HMCC="+cosmetics.ready+
                        ", applied="+successes+", missingModels="+waiting+", errors="+failures);
                    sender.sendMessage("§7Configured mappings="+Arrays.stream(IDS).filter(id->!mapping(id).isEmpty()).count()+"/13; skins and website remain AxiomWardrobe");
                    return true;
                }
                case "reload" -> {
                    if(!sender.hasPermission("axiomwardrobe.unified.admin"))return true;
                    reloadConfig();displayed.clear();begin();
                    sender.sendMessage("§aUnified configuration reloaded");return true;
                }
                case "sync" -> {
                    if(!sender.hasPermission("axiomwardrobe.unified.admin"))return true;
                    syncOnline();sender.sendMessage("§aAll online players reconciled");return true;
                }
            }
        }
        if(sender instanceof Player p)main(p);
        else sender.sendMessage("Only players can open the wardrobe GUI");
        return true;
    }
    /** Reflection here only avoids bundling the GPL plugin into our JAR. */
    private static final class HmcAdapter{
        final boolean ready=true;
        private final Method find,user,equip,unequip,slotValue,itemSlot,update;
        HmcAdapter(Plugin hmc)throws ReflectiveOperationException{
            ClassLoader cl=hmc.getClass().getClassLoader();
            Class<?> api=Class.forName("com.hibiscusmc.hmccosmetics.api.HMCCosmeticsAPI",true,cl);
            Class<?> item=Class.forName("com.hibiscusmc.hmccosmetics.cosmetic.Cosmetic",true,cl);
            Class<?> player=Class.forName("com.hibiscusmc.hmccosmetics.user.CosmeticUser",true,cl);
            Class<?> slot=Class.forName("com.hibiscusmc.hmccosmetics.cosmetic.CosmeticSlot",true,cl);
            find=api.getMethod("getCosmetic",String.class);
            user=api.getMethod("getUser",UUID.class);
            equip=api.getMethod("equipCosmetic",player,item);
            unequip=api.getMethod("unequipCosmetic",player,slot);
            update=player.getMethod("updateCosmetic",slot);
            slotValue=slot.getMethod("valueOf",String.class);
            itemSlot=item.getMethod("getSlot");
        }
        boolean exists(String id){
            try{return find.invoke(null,id)!=null;}catch(ReflectiveOperationException ex){return false;}
        }
        boolean equip(Player p,String id,String requiredSlot){
            try{
                Object target=user.invoke(null,p.getUniqueId()),cosmetic=find.invoke(null,id);
                if(target==null||cosmetic==null)return false;
                Object slot=itemSlot.invoke(cosmetic);
                if(!Objects.equals(slot,slotValue.invoke(null,requiredSlot)))return false;
                equip.invoke(null,target,cosmetic);
                update.invoke(target,slot);
                return true;
            }catch(ReflectiveOperationException ex){return false;}
        }
        boolean unequip(Player p,String slotName){
            try{
                Object target=user.invoke(null,p.getUniqueId()),slot=slotValue.invoke(null,slotName);
                if(target==null||slot==null)return false;
                unequip.invoke(null,target,slot);update.invoke(target,slot);return true;
            }catch(ReflectiveOperationException ex){return false;}
        }
    }
}
