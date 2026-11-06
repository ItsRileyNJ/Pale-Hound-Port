package de.cadentem.pale_hound.events;

import de.cadentem.pale_hound.config.ServerConfig;
import de.cadentem.pale_hound.entities.PaleHoundEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.VanillaGameEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;

/** Real gameplay vibrations, not the client's sound volume or an omniscient player poll. */
@EventBusSubscriber(modid = "pale_hound")
public final class HoundHearingEvents {
    private HoundHearingEvents() {}
    /** Match vanilla's audible chewing/sipping cadence, including interrupted meals. */
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void consuming(LivingEntityUseItemEvent.Tick event) {
        if (!(event.getEntity() instanceof Player player) || !(player.level() instanceof ServerLevel level)
            || !ServerConfig.SENSORY_HUNTING.get()) return;
        var item = event.getItem();
        var animation = item.getUseAnimation();
        if (animation != UseAnim.EAT && animation != UseAnim.DRINK) return;
        int remaining = event.getDuration(), duration = item.getUseDuration(player);
        if (remaining <= 0 || remaining % 4 != 0 || duration - remaining <= (int)(duration * 0.21875F)) return;
        broadcast(level, player, player.position(), 12, 14);
    }
    /** Single potions can return their bottle before vanilla emits GameEvent.DRINK. */
    @SubscribeEvent public static void consumed(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof Player player) || !(player.level() instanceof ServerLevel level)
            || !ServerConfig.SENSORY_HUNTING.get()) return;
        var animation = event.getItem().getUseAnimation();
        if (animation == UseAnim.EAT || animation == UseAnim.DRINK) broadcast(level, player, player.position(), 12, 14);
    }
    @SubscribeEvent public static void damaged(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof PaleHoundEntity hound && !hound.level().isClientSide()) {
            hound.getHazardLearning().damaged(event.getSource(), event.getNewDamage());
            if (event.getSource().getEntity() instanceof Player player && event.getNewDamage() > 0)
                hound.getHuntBrain().hear(player, hound.position(), 12, 30);
        }
    }
    @SubscribeEvent public static void hear(VanillaGameEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !ServerConfig.SENSORY_HUNTING.get()) return;
        var cause = event.getCause();
        if (cause instanceof Projectile projectile) cause = projectile.getOwner();
        if (cause instanceof net.minecraft.world.entity.item.PrimedTnt tnt) cause = tnt.getOwner();
        // Only players and unattributed world disturbances are hearing evidence.
        // Bat/passive-mob steps, splashes, impacts and button/door events must never redirect a hunt.
        if (cause instanceof net.minecraft.world.entity.LivingEntity && !(cause instanceof Player)) return;
        Player player = cause instanceof Player owner ? owner : null;
        var kind = event.getVanillaEvent();
        double radius; float strength;
        if (kind.is(GameEvent.STEP)) {
            if (player == null) return;
            if (player.isSteppingCarefully() || player.isCrouching()) return;
            radius = player.isSprinting() ? 28 : 10; strength = player.isSprinting() ? 24 : 10;
        } else if (kind.is(GameEvent.HIT_GROUND) || kind.is(GameEvent.SPLASH)) { radius = 24; strength = 20;
        } else if (kind.is(GameEvent.BLOCK_OPEN) || kind.is(GameEvent.BLOCK_CLOSE)
            || kind.is(GameEvent.BLOCK_ACTIVATE) || kind.is(GameEvent.BLOCK_DEACTIVATE)
            || kind.is(GameEvent.CONTAINER_OPEN) || kind.is(GameEvent.CONTAINER_CLOSE)) { radius = 24; strength = 22;
        } else if (kind.is(GameEvent.BLOCK_DESTROY) || kind.is(GameEvent.BLOCK_PLACE)) { radius = 24; strength = 22;
        } else if (kind.is(GameEvent.PROJECTILE_SHOOT)) { radius = 40; strength = 32;
        } else if (kind.is(GameEvent.PROJECTILE_LAND)) { radius = 24; strength = 18;
        } else if (kind.is(GameEvent.EXPLODE)) { radius = 64; strength = 50;
        } else if (kind.is(GameEvent.SWIM) && player != null) { radius = 16; strength = 12;
        } else if (kind.is(GameEvent.ENTITY_DAMAGE) && player != null) { radius = 24; strength = 24;
        } else if ((kind.is(GameEvent.EAT) || kind.is(GameEvent.DRINK)) && player != null) { radius = 12; strength = 14;
        } else if ((kind.is(GameEvent.EQUIP) || kind.is(GameEvent.UNEQUIP)) && player != null) { radius = 16; strength = 16;
        } else return;
        if (player == null && !(kind.is(GameEvent.EXPLODE) || kind.is(GameEvent.PROJECTILE_LAND) || kind.is(GameEvent.BLOCK_OPEN)
            || kind.is(GameEvent.BLOCK_CLOSE) || kind.is(GameEvent.BLOCK_ACTIVATE) || kind.is(GameEvent.BLOCK_DEACTIVATE)
            || kind.is(GameEvent.CONTAINER_OPEN) || kind.is(GameEvent.CONTAINER_CLOSE))) return;
        broadcast(level, player, event.getEventPosition(), radius, strength);
    }
    private static void broadcast(ServerLevel level, Player player, Vec3 position, double radius, float strength) {
        double reach = radius * ServerConfig.HOUND_HEARING_MULTIPLIER.get();
        var bounds = new AABB(position, position).inflate(reach);
        for (var hound : level.getEntitiesOfClass(PaleHoundEntity.class, bounds))
            if (!hound.isNoAi()) hound.getHuntBrain().hear(player, position, radius, strength);
    }
}
