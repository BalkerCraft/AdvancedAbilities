package me.delected.advancedabilities.modern.abilities;

import me.delected.advancedabilities.api.AdvancedProvider;
import me.delected.advancedabilities.api.objects.ability.Ability;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ModernGrapplingHook extends Ability implements Listener {
    private final Map<UUID, Vector> grapple = new HashMap<>();
    private final Set<UUID> fallList = new HashSet<>();

    public String getId() {
        return "grappling-hook";
    }

    public boolean removeItem() {
        return false;
    }

    @EventHandler
    public void onGrappleLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player))
            return;
        Player shooter = (Player) event.getEntity().getShooter();
        if (event.getEntity().getType() != EntityType.FISHING_HOOK)
            return;
        ItemStack item = shooter.getItemInHand();
        Ability ability = AdvancedProvider.getAPI().getAbilityManager().getAbilityByItem(item);
        if (ability == null)
            return;
        if (AdvancedProvider.getAPI().getAbilityManager().inCooldown(shooter, this)) {
            event.setCancelled(true);
            return;
        }

        this.grapple.putIfAbsent(shooter.getUniqueId(), event.getEntity().getVelocity());
    }

    @EventHandler
    public void onPlayerFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (!this.grapple.containsKey(playerId))
            return;

        if (event.getState() != PlayerFishEvent.State.REEL_IN &&
            event.getState() != PlayerFishEvent.State.CAUGHT_ENTITY &&
            event.getState() != PlayerFishEvent.State.CAUGHT_FISH &&
            event.getState() != PlayerFishEvent.State.IN_GROUND)
            return;

        Location loc = player.getLocation();
        Location hookLoc = event.getHook().getLocation();

        this.grapple.remove(playerId);
        double dis = loc.distance(hookLoc);

        ItemStack item = player.getItemInHand();
        Ability ability = AdvancedProvider.getAPI().getAbilityManager().getAbilityByItem(item);
        if (ability != null && ability.getId().equals(this.getId())) {
            item.setDurability((short) 0);
        }

        if (!getConfig().getBoolean("fall-damage"))
            this.fallList.add(playerId);
        addCooldown(player);

        Vector direction = hookLoc.toVector().subtract(loc.toVector()).normalize();

        double cappedDistance = Math.min(dis, 35.0);

        double velocityMultiplier = Math.min(6, 1.6 + (cappedDistance * 0.175));

        double horizontalMultiplier = velocityMultiplier;
        double verticalMultiplier = velocityMultiplier * 0.8;

        double verticalComponent = direction.getY();
        if (verticalComponent > 0.7) {
            horizontalMultiplier *= (1 - (verticalComponent - 0.6)); // Greatly reduce horizontal push
            verticalMultiplier = Math.min(1.5, verticalMultiplier); // Cap vertical boost (2x stronger)
        }

        Vector newVelocity = new Vector(
            direction.getX() * horizontalMultiplier,
            direction.getY() * verticalMultiplier,
            direction.getZ() * horizontalMultiplier
        );

        if (newVelocity.getY() < 0.4) {
            newVelocity.setY(Math.max(newVelocity.getY(), 0.8)); // 2x stronger upward boost
        }

        Vector currentVelocity = player.getVelocity();
        newVelocity.setX(newVelocity.getX() * 0.9 + currentVelocity.getX() * 0.1);
        newVelocity.setZ(newVelocity.getZ() * 0.9 + currentVelocity.getZ() * 0.1);

        player.setVelocity(newVelocity);
    }

    @EventHandler
    public void onPlayerFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player))
            return;
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL)
            return;
        if (this.fallList.contains(event.getEntity().getUniqueId())) {
            this.fallList.remove(event.getEntity().getUniqueId());
            event.setCancelled(true);
        }
    }
}