package de.cadentem.pale_hound.events;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.datafixers.util.Pair;
import de.cadentem.pale_hound.PaleHound;
import de.cadentem.pale_hound.commands.HoundHighlightCommand;
import de.cadentem.pale_hound.config.ServerConfig;
import de.cadentem.pale_hound.entities.PaleHoundEntity;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;

@EventBusSubscriber(
   modid = "pale_hound",
   bus = Bus.GAME
)
public class ForgeEvents {
   private static final ConcurrentHashMap<Integer, Integer> HIT_COUNTER = new ConcurrentHashMap<>();

   @SubscribeEvent
   public static void applyEntityConfig(EntityJoinLevelEvent event) {
      if (event.getEntity() instanceof PaleHoundEntity hound) {
         hound.applyConfigAttributes();
      }
   }

   @SubscribeEvent
   public static void handleHurt(LivingIncomingDamageEvent event) {
      if (event.getEntity() instanceof PaleHoundEntity paleHound) {
         boolean skipDamage = event.getSource().is(DamageTypes.DROWN)
            || event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD)
            || event.getSource().is(DamageTypes.IN_WALL);
         boolean increaseCounter = event.getSource().is(DamageTypes.DROWN) || event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD);
         if (skipDamage) {
            if (increaseCounter && !paleHound.level().isClientSide()) {
               HIT_COUNTER.merge(paleHound.getId(), 1, Integer::sum);
               if (HIT_COUNTER.get(paleHound.getId()) > 5) {
                  HIT_COUNTER.remove(paleHound.getId());
                  boolean couldTeleport = paleHound.teleportToTarget();
                  paleHound.hurtMarked = true;
                  if (!couldTeleport) {
                     String key = paleHound.level().dimension().location().toString();
                     if (ServerConfig.isValidDimension(key)) {
                        int spawnDelta = (int)(((Integer)ServerConfig.CAN_SPAWN_MIN.get()).intValue() * 0.3);
                        int noiseDelta = (int)(((Integer)ServerConfig.RESET_NOISE_MIN.get()).intValue() * 0.3);
                        PaleHound.speedUpTimers(key, spawnDelta, noiseDelta);
                     }

                     paleHound.disappear();
                  }
               }
            }

            event.setCanceled(true);
         } else if (event.getSource().is(DamageTypes.FALL)) {
            event.setCanceled(true);
         }
      }
   }

   @SubscribeEvent
   public static void handleDimensionChange(EntityTravelToDimensionEvent event) {
      PaleHound.RELOAD_MISSING = true;
   }

   @SubscribeEvent
   public static void handleKnockback(LivingKnockBackEvent event) {
      if (event.getEntity() instanceof PaleHoundEntity paleHound && paleHound.isClimbing()) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent
   public static void registerEvents(RegisterCommandsEvent event) {
      LiteralArgumentBuilder<CommandSourceStack> builder = (LiteralArgumentBuilder<CommandSourceStack>)Commands.literal("pale_hound")
         .requires(HoundHighlightCommand::canUse);
      builder.then(HoundHighlightCommand.command());
      builder.then(
         Commands.literal("fast_forward").requires(source -> source.hasPermission(2))
            .then(
               Commands.argument("dimension", DimensionArgument.dimension())
                  .then(
                     Commands.argument("spawn_delta_ticks", IntegerArgumentType.integer())
                        .then(Commands.argument("noise_delta_ticks", IntegerArgumentType.integer()).executes(context -> {
                           String dimension = DimensionArgument.getDimension(context, "dimension").dimension().location().toString();
                           int spawnDelta = IntegerArgumentType.getInteger(context, "spawn_delta_ticks");
                           int noiseDelta = IntegerArgumentType.getInteger(context, "noise_delta_ticks");
                           boolean wasSuccessful = PaleHound.speedUpTimers(dimension, spawnDelta, noiseDelta);
                           if (wasSuccessful) {
                              ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("Server configuration has been reloaded"), true);
                           } else {
                              ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("Timer for dimension [" + dimension + "] does not exist"));
                           }

                           return 1;
                        }))
                  )
            )
      );
      builder.then(Commands.literal("get_target").requires(source -> source.hasPermission(2)).then(Commands.argument("dimension", DimensionArgument.dimension()).executes(context -> {
         String dimension = DimensionArgument.getDimension(context, "dimension").dimension().location().toString();
         Entity currentVictim = PaleHound.getCurrentVictim(dimension);
         if (currentVictim != null) {
            ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal(currentVictim.toString()), true);
         } else {
            ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("Timer for dimension [" + dimension + "] does not exist or has no target"));
         }

         return 1;
      })));
      builder.then(
         Commands.literal("get_timer").requires(source -> source.hasPermission(2))
            .then(
               Commands.argument("dimension", DimensionArgument.dimension())
                  .then(
                     Commands.argument("type", StringArgumentType.string())
                        .suggests((context, suggestionsBuilder) -> SharedSuggestionProvider.suggest(new String[]{"spawn", "noise"}, suggestionsBuilder))
                        .executes(context -> {
                           String dimension = DimensionArgument.getDimension(context, "dimension").dimension().location().toString();
                           String type = StringArgumentType.getString(context, "type");
                           Pair<Integer, Integer> timer = PaleHound.getTimer(dimension, type);
                           if ((Integer)timer.getFirst() != -1 && (Integer)timer.getSecond() != -1) {
                              double currentSeconds = ((Integer)timer.getFirst()).intValue() / 20.0;
                              double targetSeconds = ((Integer)timer.getSecond()).intValue() / 20.0;
                              ((CommandSourceStack)context.getSource())
                                 .sendSuccess(() -> Component.literal(currentSeconds + " / " + targetSeconds + " seconds"), true);
                           } else {
                              ((CommandSourceStack)context.getSource()).sendFailure(Component.literal("Timer for dimension [" + dimension + "] does not exist"));
                           }

                           return 1;
                        })
                  )
            )
      );
      event.getDispatcher().register(builder);
   }
}
