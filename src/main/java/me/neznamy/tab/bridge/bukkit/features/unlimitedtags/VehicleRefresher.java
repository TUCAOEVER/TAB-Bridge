package me.neznamy.tab.bridge.bukkit.features.unlimitedtags;

import me.neznamy.tab.bridge.bukkit.BukkitBridge;
import me.neznamy.tab.bridge.bukkit.BukkitBridgePlayer;
import me.neznamy.tab.bridge.bukkit.platform.BukkitPlatform;
import me.neznamy.tab.bridge.shared.BridgePlayer;
import me.neznamy.tab.bridge.shared.TABBridge;
import me.neznamy.tab.bridge.shared.message.outgoing.SetOnBoat;
import org.bukkit.entity.Entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class VehicleRefresher {

    //map of players currently in a vehicle
    private final WeakHashMap<BridgePlayer, Entity> playersInVehicle = new WeakHashMap<>();
    
    //map of vehicles carrying players
    private final Map<Integer, List<Entity>> vehicles = new ConcurrentHashMap<>();
    
    //set of players currently on boats
    private final Set<BridgePlayer> playersOnBoats = Collections.newSetFromMap(new WeakHashMap<>());

    private final WeakHashMap<BridgePlayer, String> playerVehicles = new WeakHashMap<>();
    
    private final BridgeNameTagX feature;

    public VehicleRefresher(BridgeNameTagX feature) {
        this.feature = feature;
        TABBridge.getInstance().getScheduler().scheduleAtFixedRate(() -> {
            if (!feature.isEnabled()) return;
            for (BridgePlayer inVehicle : playersInVehicle.keySet()) {
                ArmorStandManager asm = feature.getArmorStandManager(inVehicle);
                if (asm != null) asm.teleport();
            }
            for (BridgePlayer p : TABBridge.getInstance().getOnlinePlayers()) {
                if (feature.getPlayersPreviewingNameTag().contains(p)) {
                    feature.getArmorStandManager(p).teleport((BukkitBridgePlayer) p);
                }
                Entity e = ((BukkitBridgePlayer)p).getPlayer().getVehicle();
                String vehicle = e == null ? "" : e.getClass().getName() + "@" + Integer.toHexString(e.hashCode());
                if (!playerVehicles.getOrDefault(p, "null").equals(vehicle)) {
                    playerVehicles.put(p, vehicle);
                    refresh((BukkitBridgePlayer) p);
                }
            }
        }, 0, 50, TimeUnit.MILLISECONDS);
    }

    public void onJoin(BukkitBridgePlayer connectedPlayer) {
        Entity vehicle = connectedPlayer.getPlayer().getVehicle();
        if (vehicle != null) {
            updateVehicle(vehicle);
            playersInVehicle.put(connectedPlayer, vehicle);
            if (feature.isDisableOnBoats() && vehicle.getType().toString().contains("BOAT")) {
                playersOnBoats.add(connectedPlayer);
            }
        }
    }

    public void onQuit(BukkitBridgePlayer disconnectedPlayer) {
        if (playersInVehicle.containsKey(disconnectedPlayer)) vehicles.remove(playersInVehicle.get(disconnectedPlayer).getEntityId());
        for (List<Entity> entities : vehicles.values()) {
            entities.remove(disconnectedPlayer.getPlayer());
        }
    }

    public void refresh(BukkitBridgePlayer p) {
        if (feature.isPlayerDisabled(p)) return;
        Entity vehicle = p.getPlayer().getVehicle();
        if (playersInVehicle.containsKey(p) && vehicle == null) {
            //vehicle exit
            vehicles.remove(playersInVehicle.get(p).getEntityId());
            feature.getArmorStandManager(p).teleport();
            playersInVehicle.remove(p);
            if (feature.isDisableOnBoats() && playersOnBoats.contains(p)) {
                playersOnBoats.remove(p);
                p.sendPluginMessage(new SetOnBoat(false));
            }
        }
        if (!playersInVehicle.containsKey(p) && vehicle != null) {
            //vehicle enter
            updateVehicle(vehicle);
            feature.getArmorStandManager(p).respawn(); //making teleport instant instead of showing teleport animation
            playersInVehicle.put(p, vehicle);
            if (feature.isDisableOnBoats() && vehicle.getType().toString().contains("BOAT")) {
                playersOnBoats.add(p);
                p.sendPluginMessage(new SetOnBoat(true));
            }
        }
    }

    public boolean isOnBoat(BridgePlayer p) {
        return playersOnBoats.contains(p);
    }
    
    public Map<Integer, List<Entity>> getVehicles() {
        return vehicles;
    }
    
    /**
     * Returns list of all passengers on specified vehicle
     * @param vehicle - vehicle to check passengers of
     * @return list of passengers
     */
    @SuppressWarnings("deprecation")
    public List<Entity> getPassengers(Entity vehicle){
        if (BukkitBridge.getMinorVersion() >= 11) {
            return vehicle.getPassengers();
        } else {
            if (vehicle.getPassenger() != null) {
                return Collections.singletonList(vehicle.getPassenger());
            } else {
                return new ArrayList<>();
            }
        }
    }

    private void updateVehicle(Entity vehicle) {
        ((BukkitPlatform)TABBridge.getInstance().getPlatform()).runEntityTask(vehicle, () -> vehicles.put(vehicle.getEntityId(), getPassengers(vehicle)));
    }
}