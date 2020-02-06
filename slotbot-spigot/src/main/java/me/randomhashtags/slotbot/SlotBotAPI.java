package me.randomhashtags.slotbot;

import com.sun.istack.internal.NotNull;
import me.randomhashtags.slotbot.universal.UInventory;
import me.randomhashtags.slotbot.util.ChatUtils;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public enum SlotBotAPI implements Listener, CommandExecutor, ChatUtils {
    INSTANCE;

    private boolean isEnabled;
    private UInventory gui, preview;
    public ItemStack ticket;

    private ItemStack item;
    private ItemMeta itemMeta;
    private List<String> lore;
    
    private ItemStack ticketLocked, ticketUnlocked, spinnerMissingTickets, spinnerReadyToSpin, rewardSlot, withdrawTickets;
    private ItemStack randomizedLootPlaceholder, randomizedLootReadyToRoll, previewRewards, background;
    private int withdrawTicketsSlot, spinnerSlot, previewRewardsSlot;
    private List<Integer> ticketSlots;
    private List<String> rewards;
    private List<ItemStack> previewRewardList;

    private HashMap<Player, HashMap<Integer, List<Integer>>> rollingTasks;
    private HashMap<Player, List<Integer>> pending;
    private HashMap<Integer, HashSet<Integer>> slots;

    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if(args.length >= 1 && args[0].equals("reload") && sender.hasPermission("SlotBot.reload")) {
            SLOT_BOT.reload();
        } else if(sender instanceof Player) {
            view((Player) sender);
        } else {
            return false;
        }
        return true;
    }

    public void load() {
        if(isEnabled) {
            return;
        }
        isEnabled = true;
        final long started = System.currentTimeMillis();

        PLUGIN_MANAGER.registerEvents(this, SLOT_BOT);
        item = new ItemStack(Material.APPLE);
        itemMeta = item.getItemMeta();
        lore = new ArrayList<>();

        ticket = createItemStack(SLOT_BOT_CONFIG, "items.ticket");
        CUSTOM_ITEMS.put("slotbotticket", ticket);

        ticketLocked = createItemStack(SLOT_BOT_CONFIG, "items.ticket locked");
        ticketUnlocked = createItemStack(SLOT_BOT_CONFIG, "items.ticket unlocked");
        rewardSlot = createItemStack(SLOT_BOT_CONFIG, "items.reward slot");
        withdrawTickets = createItemStack(SLOT_BOT_CONFIG, "items.withdraw tickets");
        spinnerMissingTickets = createItemStack(SLOT_BOT_CONFIG, "items.spinner missing ticket");
        spinnerReadyToSpin = createItemStack(SLOT_BOT_CONFIG, "items.spinner ready to spin");
        spinnerSlot = SLOT_BOT_CONFIG.getInt("items.spinner missing ticket.slot");

        preview = new UInventory(null, SLOT_BOT_CONFIG.getInt("preview rewards.size"), colorize(SLOT_BOT_CONFIG.getString("preview rewards.title")));
        gui = new UInventory(null, SLOT_BOT_CONFIG.getInt("gui.size"), colorize(SLOT_BOT_CONFIG.getString("gui.title")));
        randomizedLootPlaceholder = createItemStack(SLOT_BOT_CONFIG, "items.randomized loot placeholder");
        randomizedLootReadyToRoll = createItemStack(SLOT_BOT_CONFIG, "items.randomized loot ready to roll");
        background = createItemStack(SLOT_BOT_CONFIG, "gui.background");

        final Inventory inv = gui.getInventory();
        inv.setItem(spinnerSlot, spinnerMissingTickets);

        slots = new HashMap<>();
        ticketSlots = new ArrayList<>();
        int ticketAmount = 0;
        for(String s : SLOT_BOT_CONFIG.getStringList("items.ticket.slots")) {
            ticketAmount++;
            final int slot = Integer.parseInt(s);
            inv.setItem(slot, getTicketLocked(ticketAmount));
            ticketSlots.add(slot);
        }

        for(String key : getConfigurationSectionKeys(SLOT_BOT_CONFIG, "gui.reward slots", false)) {
            final int slot = Integer.parseInt(key);
            inv.setItem(slot, rewardSlot);
            final HashSet<Integer> rewardSlots = new HashSet<>();
            for(String s : SLOT_BOT_CONFIG.getStringList("gui.reward slots." + key)) {
                final int rewardSlot = Integer.parseInt(s);
                inv.setItem(rewardSlot, randomizedLootPlaceholder);
                rewardSlots.add(rewardSlot);
            }
            slots.put(slot, rewardSlots);
        }
        for(String key : getConfigurationSectionKeys(SLOT_BOT_CONFIG, "gui", false, "title", "size", "background", "reward slots", "visual placeholder slots")) {
            final String path = "gui." + key;
            final String item = SLOT_BOT_CONFIG.getString(path + ".item");
            final int slot = SLOT_BOT_CONFIG.getInt(path + ".slot");
            final boolean isWithdraw = "WITHDRAW_TICKETS".equals(item);
            if(isWithdraw) {
                withdrawTicketsSlot = slot;
            }
            inv.setItem(slot, isWithdraw ? null : createItemStack(SLOT_BOT_CONFIG, path));
        }

        final ItemStack visualPlaceholder = createItemStack(SLOT_BOT_CONFIG, "items.visual placeholder");
        for(String s : SLOT_BOT_CONFIG.getStringList("gui.visual placeholder slots")) {
            inv.setItem(Integer.parseInt(s), visualPlaceholder);
        }

        for(int i = 0; i < inv.getSize(); i++) {
            if(inv.getItem(i) == null) {
                inv.setItem(i, background);
            }
        }

        rollingTasks = new HashMap<>();
        pending = new HashMap<>();
        rewards = SLOT_BOT_CONFIG.getStringList("rewards");

        previewRewardsSlot = SLOT_BOT_CONFIG.getInt("items.preview rewards.slot");
        previewRewards = createItemStack(SLOT_BOT_CONFIG, "items.preview rewards");
        itemMeta = previewRewards.getItemMeta(); lore.clear();
        previewRewardList = new ArrayList<>();

        final List<String> actualRewards = new ArrayList<>();
        for(String s : itemMeta.getLore()) {
            if(s.contains("{AMOUNT}") && s.contains("{ITEM}")) {
                for(String reward : rewards) {
                    final ItemStack is = createItemStack(null, reward);
                    if(is != null) {
                        actualRewards.add(reward);
                        ItemMeta meta = is.getItemMeta();
                        lore.add(s.replace("{AMOUNT}", Integer.toString(is.getAmount())).replace("{ITEM}", meta.hasDisplayName() ? meta.getDisplayName() : is.getType().name()));
                        previewRewardList.add(is);
                    }
                }
            } else {
                lore.add(s);
            }
        }
        itemMeta.setLore(lore); lore.clear();
        previewRewards.setItemMeta(itemMeta);
        inv.setItem(previewRewardsSlot, previewRewards);
        rewards = actualRewards;

        final Inventory previewInv = preview.getInventory();
        for(ItemStack is : previewRewardList) {
            previewInv.setItem(previewInv.firstEmpty(), is);
        }
        sendConsoleDidLoadFeature("SlotBotAPI", started);
    }
    public void unload() {
        if(isEnabled) {
            isEnabled = false;
            for(Player player : new ArrayList<>(pending.keySet())) {
                player.closeInventory();
            }
            HandlerList.unregisterAll(this);
        }
    }

    private ItemStack getTicketLocked(int ticketAmount) {
        item = ticketLocked.clone();
        itemMeta = item.getItemMeta(); lore.clear();
        for(String string : itemMeta.getLore()) {
            lore.add(string.replace("{AMOUNT}", Integer.toString(ticketAmount)));
        }
        itemMeta.setLore(lore); lore.clear();
        item.setItemMeta(itemMeta);
        item.setAmount(ticketAmount);
        return item;
    }

    public void view(@NotNull Player player) {
        player.closeInventory();
        player.openInventory(Bukkit.createInventory(player, gui.getSize(), gui.getTitle()));
        player.getOpenInventory().getTopInventory().setContents(gui.getInventory().getContents());
        player.updateInventory();
    }
    public void viewPreview(@NotNull Player player) {
        player.closeInventory();
        player.openInventory(Bukkit.createInventory(player, preview.getSize(), preview.getTitle()));
        player.getOpenInventory().getTopInventory().setContents(preview.getInventory().getContents());
        player.updateInventory();
    }
    public void tryWithdrawingTickets(@NotNull Player player) {
        final Inventory top = player.getOpenInventory().getTopInventory();
        if(withdrawTickets.isSimilar(top.getItem(withdrawTicketsSlot))) {
            int ticketAmount = 0;
            final boolean isPending = pending.containsKey(player);
            final List<Integer> pendingSlots = isPending ? pending.get(player) : null;
            final List<Integer> keySet = new ArrayList<>(slots.keySet());
            for(int i : ticketSlots) {
                final int rewardSlot = keySet.get(ticketAmount);
                ticketAmount++;
                if(ticketUnlocked.isSimilar(top.getItem(i)) && (!isPending || !pendingSlots.contains(rewardSlot))) {
                    giveItem(player, ticket);
                    top.setItem(i, getTicketLocked(keySet.indexOf(rewardSlot)+1));
                    for(int slot : slots.get(rewardSlot)) {
                        top.setItem(slot, randomizedLootPlaceholder);
                    }
                }
            }
            top.setItem(withdrawTicketsSlot, background);
            top.setItem(spinnerSlot, spinnerMissingTickets);
            player.updateInventory();
        }
    }
    public int getInsertedTickets(@NotNull Player player) {
        final Inventory top = player.getOpenInventory().getTopInventory();
        int total = 0;
        for(int slot : ticketSlots) {
            if(ticketUnlocked.isSimilar(top.getItem(slot))) {
                total++;
            }
        }
        return total;
    }
    public void tryInsertingTicket(@NotNull Player player) {
        final Inventory top = player.getOpenInventory().getTopInventory();
        final int inserted = getInsertedTickets(player), maxAllowed = ticketSlots.size();
        if(inserted < maxAllowed) {
            final ItemStack withdraw = top.getItem(withdrawTicketsSlot), spin = top.getItem(spinnerSlot);
            if(!withdrawTickets.isSimilar(withdraw)) {
                top.setItem(withdrawTicketsSlot, withdrawTickets);
            }
            if(!spinnerReadyToSpin.isSimilar(spin)) {
                top.setItem(spinnerSlot, spinnerReadyToSpin);
            }
            for(int slot : slots.get(slots.keySet().toArray()[inserted])) {
                top.setItem(slot, randomizedLootReadyToRoll);
            }
            removeItem(player, ticket, 1);
            final int slot = ticketSlots.get(inserted);
            item = ticketUnlocked.clone();
            item.setAmount(inserted+1);
            top.setItem(slot, item);
            player.updateInventory();
        }
    }
    public boolean trySpinning(@NotNull Player player, int slot, @NotNull ItemStack targetItem) {
        if(spinnerReadyToSpin.isSimilar(targetItem)) {
            final Inventory top = player.getOpenInventory().getTopInventory();
            top.setItem(withdrawTicketsSlot, background);
            List<Integer> insertedTickets = new ArrayList<>();
            final boolean isPending = pending.containsKey(player);
            final List<Integer> pendingSlots = isPending ? pending.get(player) : null;
            int ticket = 0;
            for(int i : ticketSlots) {
                if(ticketUnlocked.isSimilar(top.getItem(i))) {
                    final int rewardSlot = (int) slots.keySet().toArray()[ticket];
                    if(!isPending || !pendingSlots.contains(rewardSlot)) {
                        insertedTickets.add(rewardSlot);
                    }
                    ticket++;
                }
            }
            if(!insertedTickets.isEmpty()) {
                for(int rewardSlot : insertedTickets) {
                    startRolling(player, top, rewardSlot);
                }
                return true;
            }
        } else if(ticketSlots.contains(slot) && !ticketUnlocked.isSimilar(targetItem)) {
            final HashMap<String, String> replacements = new HashMap<>();
            final int index = ticketSlots.indexOf(slot);
            replacements.put("{AMOUNT}", Integer.toString(index+1));
            sendStringListMessage(player, getStringList(SLOT_BOT_CONFIG, "messages.slot requires ticket"), replacements);
        }
        return false;
    }
    private void updateRandomLoot(Player player, Inventory top, int rewardSlot, boolean isRandom) {
        final int size = rewards.size();
        top.setItem(rewardSlot, isRandom ? createItemStack(null, rewards.get(RANDOM.nextInt(size))) : null);
        for(int value : slots.get(rewardSlot)) {
            top.setItem(value, isRandom ? createItemStack(null, rewards.get(RANDOM.nextInt(size))) : null);
        }
        player.updateInventory();
    }
    private void startRolling(Player player, Inventory top, int rewardSlot) {
        if(!rollingTasks.containsKey(player)) {
            rollingTasks.put(player, new HashMap<>());
        }
        if(!pending.containsKey(player)) {
            pending.put(player, new ArrayList<>());
        }
        final HashMap<Integer, List<Integer>> slotTasks = rollingTasks.get(player);
        if(!slotTasks.containsKey(rewardSlot)) {
            slotTasks.put(rewardSlot, new ArrayList<>());
        }

        final List<Integer> tasks = slotTasks.get(rewardSlot);
        pending.get(player).add(rewardSlot);

        updateRandomLoot(player, top, rewardSlot, true);
        for(int i = 1; i <= 10; i++) {
            tasks.add(SCHEDULER.scheduleSyncDelayedTask(SLOT_BOT, () -> {
                updateRandomLoot(player, top, rewardSlot, true);
            }, i*5));
        }
        for(int i = 1; i <= 10; i++) {
            final int I = i;
            tasks.add(SCHEDULER.scheduleSyncDelayedTask(SLOT_BOT, () -> {
                updateRandomLoot(player, top, rewardSlot, true);
                if(I == 10) {
                    stopRolling(player, rewardSlot);
                }
            }, 50+(i*10)));
        }
    }
    public void stopRolling(@NotNull Player player) {
        for(int rewardSlot : slots.keySet()) {
            stopRolling(player, rewardSlot);
        }
    }
    public void stopRolling(@NotNull Player player, int rewardSlot) {
        if(pending.containsKey(player) && pending.get(player).contains(rewardSlot) && rollingTasks.containsKey(player)) {
            final HashMap<Integer, List<Integer>> tasks = rollingTasks.get(player);
            if(tasks.containsKey(rewardSlot)) {
                for(int task : tasks.get(rewardSlot)) {
                    SCHEDULER.cancelTask(task);
                }
                final Inventory top = player.getOpenInventory().getTopInventory();
                for(int slot : slots.get(rewardSlot)) {
                    top.setItem(slot, randomizedLootPlaceholder);
                }
                tasks.remove(rewardSlot);
                player.updateInventory();
            }
        }
    }
    private void giveLoot(Player player) {
        if(pending.containsKey(player)) {
            final Inventory top = player.getOpenInventory().getTopInventory();
            final List<ItemStack> items = new ArrayList<>();
            final String tickets = Integer.toString(getInsertedTickets(player)), playerName = player.getName();
            for(int i : slots.keySet()) {
                item = top.getItem(i);
                if(item != null && !rewardSlot.isSimilar(item)) {
                    itemMeta = item.getItemMeta();
                    items.add(item);
                    giveItem(player, item);
                }
            }
            final boolean isCentered = SLOT_BOT_CONFIG.getBoolean("messages.loot.centered");
            for(String s : getStringList(SLOT_BOT_CONFIG, "messages.loot.msg")) {
                s = s.replace("{PLAYER}", playerName).replace("{TICKETS}", tickets);
                if(s.contains("{AMOUNT}") && s.contains("{ITEM}")) {
                    for(ItemStack is : items) {
                        itemMeta = is.getItemMeta();
                        final String name = itemMeta.hasDisplayName() ? itemMeta.getDisplayName() : is.getType().name();
                        final String target = s.replace("{AMOUNT}", Integer.toString(is.getAmount())).replace("{ITEM}", name);
                        final String message = isCentered ? center(target, 70) : target;
                        final TextComponent hover = getHoverMessage(message, is);
                        sendHoverMessage(null, hover, true);
                    }
                } else {
                    Bukkit.broadcastMessage(isCentered ? center(s, 70) : s);
                }
            }
            pending.remove(player);
        }
    }

    @EventHandler
    private void inventoryCloseEvent(InventoryCloseEvent event) {
        final Player player = (Player) event.getPlayer();
        if(rollingTasks.containsKey(player)) {
            stopRolling(player);
            rollingTasks.remove(player);
        }
        giveLoot(player);
    }
    @EventHandler
    private void playerInteractEvent(PlayerInteractEvent event) {
        final ItemStack is = event.getItem();
        if(ticket.isSimilar(is)) {
            event.setCancelled(true);
            event.getPlayer().updateInventory();
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private void inventoryClickEvent(InventoryClickEvent event) {
        final Player player = (Player) event.getWhoClicked();
        final Inventory top = player.getOpenInventory().getTopInventory();
        if(player.equals(top.getHolder())) {
            final String title = event.getView().getTitle();
            final boolean isGUI = title.equals(gui.getTitle());
            if(isGUI || title.equals(preview.getTitle())) {
                event.setCancelled(true);
                player.updateInventory();
                final ItemStack current = event.getCurrentItem();
                if(current == null || !isGUI) {
                    return;
                }
                final int slot = event.getRawSlot();

                if(slot >= top.getSize()) {
                    if(current.isSimilar(ticket)) {
                        tryInsertingTicket(player);
                    }
                } else if(slot == previewRewardsSlot) {
                    viewPreview(player);
                } else if(current.isSimilar(spinnerMissingTickets)) {
                    sendStringListMessage(player, getStringList(SLOT_BOT_CONFIG, "messages.missing tickets"), null);
                } else if(current.isSimilar(spinnerReadyToSpin)) {
                    for(int i : ticketSlots) {
                        trySpinning(player, i, spinnerReadyToSpin);
                    }
                } else if(current.isSimilar(withdrawTickets)) {
                    tryWithdrawingTickets(player);
                } else if(ticketSlots.contains(slot)) {
                    trySpinning(player, slot, current);
                }
            }
        }
    }
}
