package com.thatguycy.worlddynamicsengine.manager.inn;

import com.palmergames.bukkit.towny.exceptions.NotRegisteredException;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;
import com.palmergames.bukkit.towny.object.TownBlockType;
import com.palmergames.bukkit.towny.TownyAPI;
import com.thatguycy.worlddynamicsengine.manager.economy.EconomyBridge;
import com.thatguycy.worlddynamicsengine.model.InnBed;
import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import java.io.File;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.Sign;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;

/**
 * 管理 Towny INN 地块床位价格、告示牌和入睡收费，失败时按原币种尝试退款。
 */
public final class InnManager {
    private final WorldDynamicsEngine plugin;
    private FileConfiguration config;
    private final Map<String, InnBed> beds;
    private final Map<UUID, Payment> pending = new HashMap<>();

    /**
     * 记录一次床位预扣的付款人与收款人、世界、床位及原币种，供确认或退款使用。
     */
    private record Payment(UUID payer, UUID receiver, String world, String bedKey, EconomyBridge.Charge charge) {}
    /**
     * 返回有效旅馆地块所属城镇及当前床位收款居民。
     */
    public record Plot(Town town, Resident recipient) {}

    public InnManager(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
        beds = plugin.getDataStorageManager().loadStringKeyed("inn_beds", InnBed.class);
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "inn.yml");
        if (!file.exists()) {
            plugin.saveResource("inn.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
        try (var stream = plugin.getResource("inn.yml")) {
            if (stream != null) {
                var defaults = YamlConfiguration.loadConfiguration(
                        new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8));
                config.setDefaults(defaults);
                config.options().copyDefaults(true);
                config.save(file);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load inn config", e);
        }
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::refreshSigns, 20L, 100L);
    }

    private void refreshSigns() {
        boolean changed = false;
        for (var iterator = beds.entrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            String[] coordinates = entry.getKey().split(":");
            var world = plugin.getServer().getWorld(UUID.fromString(coordinates[0]));
            if (world == null) continue;
            int x = Integer.parseInt(coordinates[1]);
            int y = Integer.parseInt(coordinates[2]);
            int z = Integer.parseInt(coordinates[3]);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
            Block foot = foot(world.getBlockAt(x, y, z));
            Plot plot = foot == null ? null : plot(foot);
            if (plot == null || !plot.town().getUUID().equals(entry.getValue().townId())) {
                iterator.remove();
                changed = true;
                continue;
            }
            Block signBlock = foot.getRelative(((Bed) foot.getBlockData()).getFacing().getOppositeFace());
            if (!key(signBlock).equals(entry.getValue().signKey()) || !(signBlock.getState() instanceof Sign sign)) {
                iterator.remove();
                changed = true;
                continue;
            }
            writeSign(sign, entry.getValue().amount(), entry.getValue().currency(), plot.recipient().getName());
        }
        if (changed) plugin.getDataStorageManager().saveStringKeyed("inn_beds", beds);
    }

    public boolean enabled() {
        return config.getBoolean("enabled", true);
    }

    public void message(Player player, String key, Object... args) {
        player.sendMessage(plugin.formatLocaleMessage("inn_" + key, args));
    }

    public Block foot(Block block) {
        if (!(block.getBlockData() instanceof Bed bed)) {
            return null;
        }
        Block foot = bed.getPart() == Bed.Part.FOOT ? block : block.getRelative(bed.getFacing().getOppositeFace());
        Block head = foot.getRelative(bed.getFacing());
        if (!(foot.getBlockData() instanceof Bed f) || f.getPart() != Bed.Part.FOOT
                || !(head.getBlockData() instanceof Bed h) || h.getPart() != Bed.Part.HEAD
                || f.getFacing() != h.getFacing() || foot.getType() != head.getType()) {
            return null;
        }
        return foot;
    }

    public Plot plot(Block foot) {
        TownBlock plot = TownyAPI.getInstance().getTownBlock(foot.getLocation());
        if (plot == null || !plot.getType().equals(TownBlockType.INN) || !plot.hasTown()) {
            return null;
        }
        Block head = foot.getRelative(((Bed) foot.getBlockData()).getFacing());
        if (!plot.equals(TownyAPI.getInstance().getTownBlock(head.getLocation()))) {
            return null;
        }
        try {
            Town town = plot.getTown();
            Resident recipient = plot.hasResident() ? plot.getResident() : town.getMayor();
            return recipient == null ? null : new Plot(town, recipient);
        } catch (NotRegisteredException e) {
            return null;
        }
    }

    public boolean canEdit(Player player, Block foot) {
        Plot plot = plot(foot);
        return plot != null && (player.getUniqueId().equals(plot.recipient().getUUID())
                || player.getUniqueId().equals(plot.town().getMayor().getUUID()));
    }

    public static String key(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    public void setPrice(Player player, Block target, BigDecimal amount, String currency) {
        Block foot = foot(target);
        if (!enabled() || foot == null || !canEdit(player, foot)) {
            message(player, "edit_denied");
            return;
        }
        EconomyBridge economy = plugin.getEconomyBridge();
        if (amount.signum() < 0 || amount.scale() > 2 || !economy.supportsExactCurrency(currency)) {
            message(player, "invalid_price");
            return;
        }
        Plot plot = plot(foot);
        Block signBlock = foot.getRelative(((Bed) foot.getBlockData()).getFacing().getOppositeFace());
        InnBed old = beds.get(key(foot));
        boolean existing = old != null && old.signKey().equals(key(signBlock))
                && signBlock.getState() instanceof Sign;
        if ((!existing && !signBlock.getType().isAir())
                || !signBlock.getRelative(BlockFace.DOWN).getType().isSolid()
                || !TownyAPI.getInstance().getTownBlock(foot.getLocation()).equals(
                        TownyAPI.getInstance().getTownBlock(signBlock.getLocation()))) {
            message(player, "sign_blocked");
            return;
        }
        BlockState previous = signBlock.getState();
        try {
            if (!existing) {
                signBlock.setType(Material.OAK_SIGN, false);
                var data = (org.bukkit.block.data.type.Sign) signBlock.getBlockData();
                data.setRotation(((Bed) foot.getBlockData()).getFacing().getOppositeFace());
                signBlock.setBlockData(data, false);
            }
            BlockPlaceEvent place = new BlockPlaceEvent(signBlock, previous, signBlock.getRelative(BlockFace.DOWN),
                    new ItemStack(Material.OAK_SIGN), player, true, EquipmentSlot.HAND);
            plugin.getServer().getPluginManager().callEvent(place);
            if (place.isCancelled() || !place.canBuild() || !(signBlock.getState() instanceof Sign sign)) {
                previous.update(true, false);
                message(player, "sign_blocked");
                return;
            }
            writeSign(sign, amount, currency, plot.recipient().getName());
            beds.put(key(foot), new InnBed(amount, currency, plot.town().getUUID(), key(signBlock)));
            plugin.getDataStorageManager().saveStringKeyed("inn_beds", beds);
            message(player, "price_set", amount.toPlainString(), currency, plot.recipient().getName());
        } catch (RuntimeException e) {
            previous.update(true, false);
            if (old == null) beds.remove(key(foot)); else beds.put(key(foot), old);
            plugin.getLogger().severe("Failed to set inn bed price: " + e.getMessage());
            message(player, "sign_blocked");
        }
    }

    private void writeSign(Sign sign, BigDecimal amount, String currency, String owner) {
        sign.setLine(0, plugin.getLocaleMessage("inn_sign_title"));
        sign.setLine(1, amount.toPlainString() + " " + currency);
        sign.setLine(2, plugin.getLocaleMessage("inn_sign_owner"));
        sign.setLine(3, owner);
        sign.setWaxed(true);
        if (!sign.update(true, false)) {
            throw new IllegalStateException("Sign update failed");
        }
    }

    public boolean beginSleep(Player player, Block target) {
        Block foot = foot(target);
        if (!enabled() || foot == null) return true;
        Plot plot = plot(foot);
        if (plot == null) return true;
        Resident resident = TownyAPI.getInstance().getResident(player);
        if (resident != null && plot.town().hasResident(resident)) return true;
        if (pending.containsKey(player.getUniqueId())) return false;
        InnBed bed = beds.get(key(foot));
        Block signBlock = foot.getRelative(((Bed) foot.getBlockData()).getFacing().getOppositeFace());
        if (bed != null && (!bed.townId().equals(plot.town().getUUID())
                || !bed.signKey().equals(key(signBlock)) || !(signBlock.getState() instanceof Sign))) {
            beds.remove(key(foot));
            plugin.getDataStorageManager().saveStringKeyed("inn_beds", beds);
            bed = null;
        }
        EconomyBridge.Charge charge;
        try {
            charge = bed == null ? new EconomyBridge.Charge(
                    new BigDecimal(config.getString("defaultFee.amount", "25.00")),
                    config.getString("defaultFee.currency", "default")) : new EconomyBridge.Charge(bed.amount(), bed.currency());
            if (bed != null) {
                if (bed.signKey().equals(key(signBlock)) && signBlock.getState() instanceof Sign sign) {
                    writeSign(sign, bed.amount(), bed.currency(), plot.recipient().getName());
                }
            }
        } catch (RuntimeException e) {
            message(player, "payment_failed");
            return false;
        }
        EconomyBridge economy = plugin.getEconomyBridge();
        if (!economy.supportsExactCurrency(charge.currency())) {
            message(player, "invalid_price");
            return false;
        }
        if (charge.isFree() || player.getUniqueId().equals(plot.recipient().getUUID())) return true;
        if (!economy.withdrawExact(player.getUniqueId(), player.getWorld().getName(), charge).success()) {
            message(player, "payment_failed");
            return false;
        }
        Payment payment = new Payment(player.getUniqueId(), plot.recipient().getUUID(),
                player.getWorld().getName(), key(foot), charge);
        pending.put(player.getUniqueId(), payment);
        // Wait for actual bed entry so later event cancellations never cost money.
        plugin.getServer().getScheduler().runTask(plugin, () -> settle(player, payment));
        return true;
    }

    private void settle(Player player, Payment payment) {
        if (!pending.remove(payment.payer(), payment)) return;
        Block sleepingFoot = player.isOnline() && player.isSleeping() ? foot(player.getBedLocation().getBlock()) : null;
        if (sleepingFoot != null && key(sleepingFoot).equals(payment.bedKey())
                && plugin.getEconomyBridge().depositExact(payment.receiver(), payment.world(), payment.charge()).success()) {
            message(player, "paid", payment.charge().amount().toPlainString(), payment.charge().currency());
        } else {
            if (player.isSleeping()) player.wakeup(false);
            refund(payment);
        }
    }

    private void refund(Payment payment) {
        if (!plugin.getEconomyBridge().depositExact(payment.payer(), payment.world(), payment.charge()).success()) {
            plugin.getLogger().severe("INN REFUND FAILED payer=" + payment.payer()
                    + " amount=" + payment.charge().amount() + " currency=" + payment.charge().currency());
        }
    }

    public void refundPending() {
        pending.values().forEach(this::refund);
        pending.clear();
    }

    public boolean supportsProtectedBlock(Block block) {
        return protectedBlock(block.getRelative(BlockFace.UP));
    }

    public boolean protectedBlock(Block block) {
        Block foot = foot(block);
        return foot != null && beds.containsKey(key(foot))
                || beds.values().stream().anyMatch(b -> b.signKey().equals(key(block)));
    }

    public boolean canModify(Player player, Block block) {
        Block foot = foot(block);
        if (foot != null) return canEdit(player, foot);
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            foot = foot(block.getRelative(face));
            if (foot != null && beds.containsKey(key(foot))
                    && beds.get(key(foot)).signKey().equals(key(block))) {
                return canEdit(player, foot);
            }
        }
        return false;
    }

    public void removeBroken(Block block) {
        Block foot = foot(block);
        String blockKey = key(block);
        if (beds.entrySet().removeIf(e -> e.getValue().signKey().equals(blockKey)
                || foot != null && e.getKey().equals(key(foot)))) {
            plugin.getDataStorageManager().saveStringKeyed("inn_beds", beds);
        }
    }
}
