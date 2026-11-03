package de.cadentem.pale_hound.entities;

import de.cadentem.pale_hound.config.ServerConfig;
import de.cadentem.pale_hound.PaleHound;
import de.cadentem.pale_hound.entities.goals.CustomHurtByTargetGoal;
import de.cadentem.pale_hound.entities.goals.PaleHoundBreakBlocksGoal;
import de.cadentem.pale_hound.entities.goals.PaleHoundBreakInvisGoal;
import de.cadentem.pale_hound.entities.goals.PaleHoundChaseGoal;
import de.cadentem.pale_hound.entities.goals.PaleHoundFleeGoal;
import de.cadentem.pale_hound.entities.goals.PaleHoundStareGoal;
import de.cadentem.pale_hound.entities.goals.PaleHoundStrollGoal;
import de.cadentem.pale_hound.entities.goals.PaleHoundTargetSeesMeGoal;
import de.cadentem.pale_hound.entities.goals.PaleHoundTargetTooCloseGoal;
import de.cadentem.pale_hound.entities.goals.Roll;
import de.cadentem.pale_hound.entities.goals.PaleHoundSurfaceStalkGoal;
import de.cadentem.pale_hound.network.CaveSound;
import de.cadentem.pale_hound.network.NetworkHandler;
import de.cadentem.pale_hound.registry.ModSounds;
import de.cadentem.pale_hound.util.Utils;
import de.cadentem.pale_hound.util.PaleHoundTargets;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar;
import software.bernie.geckolib.animation.Animation.LoopType;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public class PaleHoundEntity extends Monster implements GeoEntity {
   private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
   private final RawAnimation CHASE = RawAnimation.begin().then("animation.pale_hound.new_run", LoopType.LOOP);
   private final RawAnimation CHASE_IDLE = RawAnimation.begin().then("animation.pale_hound.run_idle", LoopType.LOOP);
   private final RawAnimation CROUCH_RUN = RawAnimation.begin().then("animation.pale_hound.crouch_run_new", LoopType.LOOP);
   private final RawAnimation CROUCH_IDLE = RawAnimation.begin().then("animation.pale_hound.crouch_idle", LoopType.LOOP);
   private final RawAnimation CALM_RUN = RawAnimation.begin().then("animation.pale_hound.calm_move", LoopType.LOOP);
   private final RawAnimation CALM_STILL = RawAnimation.begin().then("animation.pale_hound.calm_idle", LoopType.LOOP);
   private final RawAnimation IS_SPOTTED = RawAnimation.begin().then("animation.pale_hound.spotted", LoopType.LOOP);
   private final RawAnimation CRAWL = RawAnimation.begin().then("animation.pale_hound.crawl", LoopType.LOOP);
   private final RawAnimation FLEE = RawAnimation.begin().then("animation.pale_hound.flee", LoopType.LOOP);
   public static final EntityDataAccessor<Boolean> FLEEING_ACCESSOR = SynchedEntityData.defineId(PaleHoundEntity.class, EntityDataSerializers.BOOLEAN);
   public static final EntityDataAccessor<Boolean> CROUCHING_ACCESSOR = SynchedEntityData.defineId(PaleHoundEntity.class, EntityDataSerializers.BOOLEAN);
   public static final EntityDataAccessor<Boolean> CRAWLING_ACCESSOR = SynchedEntityData.defineId(PaleHoundEntity.class, EntityDataSerializers.BOOLEAN);
   public static final EntityDataAccessor<Boolean> SPOTTED_ACCESSOR = SynchedEntityData.defineId(PaleHoundEntity.class, EntityDataSerializers.BOOLEAN);
   public static final EntityDataAccessor<Boolean> CLIMBING_ACCESSOR = SynchedEntityData.defineId(PaleHoundEntity.class, EntityDataSerializers.BOOLEAN);
   public Roll currentRoll = Roll.STROLL;
   private Player surfaceStalkTarget;
   private boolean spottedDuringSurfaceStalk;
   private int surfaceStalkTicks;
   private int surfaceRetreatTicks;
   // Prefer stalking before ordinary target acquisition gets a chance on the first tick.
   private int surfaceStalkCheckTicks;
   public boolean isFleeing;
   public boolean hasSpawned;
   public boolean pleaseStopMoving;
   public boolean targetIsFacingMe;
   private int ticksTillRemove;
   private int chaseSoundClock;
   private boolean alreadyPlayedFleeSound;
   private boolean alreadyPlayedSpottedSound;
   private boolean startedPlayingChaseSound;
   private boolean alreadyPlayedDeathSound;

   public PaleHoundEntity(EntityType<? extends PaleHoundEntity> entityType, Level level) {
      super(entityType, level);
      this.refreshDimensions();
      this.ticksTillRemove = Utils.secondsToTicks((Integer)ServerConfig.TIME_UNTIL_LEAVE.get());
      this.setPathfindingMalus(PathType.UNPASSABLE_RAIL, 0.0F);
   }

   public void applyConfigAttributes() {
      this.setAttribute(this.getAttribute(Attributes.ARMOR), (Double)ServerConfig.ARMOR.get());
      this.setAttribute(this.getAttribute(Attributes.MAX_HEALTH), (Double)ServerConfig.MAX_HEALTH.get());
      this.setAttribute(this.getAttribute(Attributes.ATTACK_DAMAGE), (Double)ServerConfig.ATTACK_DAMAGE.get());
      this.setAttribute(this.getAttribute(Attributes.ATTACK_SPEED), (Double)ServerConfig.ATTACK_SPEED.get());
      this.setAttribute(this.getAttribute(Attributes.MOVEMENT_SPEED), (Double)ServerConfig.MOVEMENT_SPEED.get());
      this.setAttribute(this.getAttribute(Attributes.STEP_HEIGHT), 1.0);
      // 1.20.1 depth-strider levels were divided by three before interpolation.
      this.setAttribute(this.getAttribute(Attributes.WATER_MOVEMENT_EFFICIENCY), ServerConfig.DEPTH_STRIDER_BONUS.get() / 3.0);
   }

   private void setAttribute(AttributeInstance attribute, double newValue) {
      if (attribute != null) {
         double baseValue = attribute.getBaseValue();
         float difference = (float)(newValue - baseValue);
         attribute.setBaseValue(newValue);
         if (attribute.getAttribute() == Attributes.MAX_HEALTH) {
            this.setHealth(this.getHealth() + difference);
         } else if (attribute.getAttribute() == Attributes.MOVEMENT_SPEED) {
            this.setSpeed(this.getSpeed() + difference);
         }
      }
   }

   public static AttributeSupplier getAttributeBuilder() {
      double armor = 10.0;
      double maxHealth = 80.0;
      double attackDamage = 20.0;
      double attackSpeed = 0.35;
      double movementSpeed = 0.7;
      double followRange = 100.0;
      return createMobAttributes()
         .add(Attributes.ARMOR, armor)
         .add(Attributes.MAX_HEALTH, maxHealth)
         .add(Attributes.ATTACK_DAMAGE, attackDamage)
         .add(Attributes.ATTACK_SPEED, attackSpeed)
         .add(Attributes.MOVEMENT_SPEED, movementSpeed)
         .add(Attributes.FOLLOW_RANGE, followRange)
         .build();
   }

   protected void defineSynchedData(SynchedEntityData.Builder builder) {
      super.defineSynchedData(builder);
      builder.define(FLEEING_ACCESSOR, false);
      builder.define(CROUCHING_ACCESSOR, false);
      builder.define(CRAWLING_ACCESSOR, false);
      builder.define(SPOTTED_ACCESSOR, false);
      builder.define(CLIMBING_ACCESSOR, false);
   }

   protected void registerGoals() {
      this.goalSelector.addGoal(1, new FloatGoal(this));
      this.goalSelector.addGoal(1, new PaleHoundSurfaceStalkGoal(this));
      this.goalSelector.addGoal(1, new PaleHoundBreakBlocksGoal(this));
      this.goalSelector.addGoal(2, new PaleHoundChaseGoal(this, true));
      this.goalSelector.addGoal(2, new PaleHoundFleeGoal(this, 40.0F, 1.2));
      this.goalSelector.addGoal(3, new PaleHoundBreakInvisGoal(this));
      this.goalSelector.addGoal(3, new PaleHoundStareGoal(this));
      this.goalSelector.addGoal(4, new PaleHoundStrollGoal(this, 0.35));
      this.targetSelector.addGoal(1, new CustomHurtByTargetGoal(this));
      this.targetSelector.addGoal(2, new PaleHoundTargetTooCloseGoal(this, 13.0F));
      this.targetSelector.addGoal(3, new PaleHoundTargetSeesMeGoal(this));
      this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Mob.class, 10, true, false, PaleHoundTargets::isAllowed));
      this.targetSelector.addGoal(5, new MeleeAttackGoal(this, 1.0, true));
   }

   public void disappear() {
      this.playDisappearSound();
      this.discard();
   }

   public boolean hasSpawned() {
      return this.hasSpawned;
   }

   protected boolean canRide(@NotNull Entity vehicle) {
      return ServerConfig.ALLOW_RIDING.get() ? super.canRide(vehicle) : false;
   }

   public boolean startRiding(@NotNull Entity vehicle, boolean force) {
      return ServerConfig.ALLOW_RIDING.get() ? super.startRiding(vehicle, force) : false;
   }

   public boolean isSurfaceStalking() {
      return this.surfaceStalkTarget != null;
   }

   public Player getSurfaceStalkTarget() {
      return this.surfaceStalkTarget;
   }

   @Override
   public boolean doHurtTarget(Entity target) {
      return !this.isSurfaceStalking() && PaleHoundTargets.isAllowed(target) && super.doHurtTarget(target);
   }

   public boolean isSurfaceStalkRetreating() {
      return this.surfaceRetreatTicks > 0;
   }

   public boolean tryStartSurfaceStalking(Player player) {
      if (this.isSurfaceStalking() || this.isAggressive() || this.isFleeing
         || this.currentRoll == Roll.CHASE || this.currentRoll == Roll.FLEE
         || !Utils.isValidTarget(player) || !Utils.isOnSurface(this) || !Utils.isOnSurface(player)
         || this.distanceToSqr(player) > 64 * 64) return false;
      this.surfaceStalkTarget = player;
      this.spottedDuringSurfaceStalk = false;
      this.surfaceStalkTicks = this.getRandom().nextIntBetweenInclusive(600, 900);
      this.surfaceRetreatTicks = 0;
      this.getNavigation().stop();
      return true;
   }

   @Override
   public void setTarget(LivingEntity target) {
      super.setTarget(!this.isSurfaceStalking() && PaleHoundTargets.isAllowed(target) ? target : null);
   }

   @Override
   public boolean canAttack(LivingEntity target) {
      return !this.isSurfaceStalking() && PaleHoundTargets.isAllowed(target) && super.canAttack(target);
   }

   private void updateSurfaceStalking() {
      if (!this.isSurfaceStalking() && --this.surfaceStalkCheckTicks <= 0) {
         this.surfaceStalkCheckTicks = 100;
         if (this.getRandom().nextFloat() < 0.9F) {
            Player player = this.level().getNearestPlayer(this.getX(), this.getY(), this.getZ(), 64,
               candidate -> Utils.isValidTarget(candidate) && Utils.isOnSurface(candidate));
            this.tryStartSurfaceStalking(player);
         }
      }
      if (this.isSurfaceStalking()) {
         if (this.isSurfaceStalkRetreating()) {
            if (--this.surfaceRetreatTicks <= 0) {
               if (this.spottedDuringSurfaceStalk && this.level() instanceof ServerLevel serverLevel) {
                  PaleHound.scheduleSpawnAfterSpottedRetreat(serverLevel);
               }
               this.disappear();
               return;
            }
         } else if (this.level().players().stream().anyMatch(player -> this.isLookingAtMe(player, true))) {
            this.spottedDuringSurfaceStalk = true;
            this.surfaceRetreatTicks = 60;
            this.getNavigation().stop();
         } else if (--this.surfaceStalkTicks <= 0 || !Utils.isValidTarget(this.surfaceStalkTarget)
            || !Utils.isOnSurface(this) || !Utils.isOnSurface(this.surfaceStalkTarget)
            || this.distanceToSqr(this.surfaceStalkTarget) > 80 * 80) {
            this.surfaceRetreatTicks = 60;
            this.getNavigation().stop();
         }
         this.setTarget(null);
         this.setAggressive(false);
         this.currentRoll = Roll.STROLL;
         this.isFleeing = this.isSurfaceStalkRetreating();
         this.entityData.set(FLEEING_ACCESSOR, this.isFleeing);
         this.setSpotted(false);
         this.pleaseStopMoving = false;
         this.setInvisible(false);
      }
      this.targetSelector.setControlFlag(net.minecraft.world.entity.ai.goal.Goal.Flag.TARGET, !this.isSurfaceStalking());
   }

   public void tick() {
      if (this.isRemoved()) return;
      if (!this.level().isClientSide()) this.updateSurfaceStalking();
      if (this.isRemoved()) return;
      if (!this.level().isClientSide() && !this.isSurfaceStalking() && --this.ticksTillRemove <= 0) {
         this.disappear();
         return;
      }

      if (this.goalSelector.getAvailableGoals().isEmpty() || this.targetSelector.getAvailableGoals().isEmpty()) {
         this.registerGoals();
         this.goalSelector.tick();
         this.targetSelector.tick();
      }

      if (this.getTarget() != null) {
         this.targetIsFacingMe = this.isLookingAtMe(this.getTarget(), false);
      }

      if (this.level() instanceof ServerLevel) {
         boolean isAboveSolid = this.level().getBlockState(this.blockPosition().above()).isSolid();
         boolean isTwoAboveSolid = this.level().getBlockState(this.blockPosition().above(2)).isSolid();
         boolean isThreeAboveSolid = this.level().getBlockState(this.blockPosition().above(3)).isSolid();
         Vec3i offset = this.getDirectionVector();
         boolean isFacingSolid = this.level().getBlockState(this.blockPosition().relative(this.getDirection())).isSolid();
         if (isFacingSolid) {
            offset = offset.offset(0, 1, 0);
         }

         boolean isOffsetFacingSolid = this.level().getBlockState(this.blockPosition().offset(offset)).isSolid();
         boolean isOffsetFacingAboveSolid = this.level().getBlockState(this.blockPosition().offset(offset).above()).isSolid();
         boolean isOffsetFacingTwoAboveSolid = this.level().getBlockState(this.blockPosition().offset(offset).above(2)).isSolid();
         boolean shouldCrouch = isTwoAboveSolid
            || !isOffsetFacingSolid && !isOffsetFacingAboveSolid && (isOffsetFacingTwoAboveSolid || isFacingSolid && isThreeAboveSolid);
         boolean closeChase = this.currentRoll == Roll.CHASE && this.isAggressive()
            && this.getTarget() != null && this.getTarget().isAlive()
            && this.distanceToSqr(this.getTarget()) <= 12.0 * 12.0;
         boolean shouldCrawl = (this.isSurfaceStalking() && !this.isSurfaceStalkRetreating()) || shouldCrouch || closeChase || isAboveSolid
            || !isOffsetFacingSolid && isOffsetFacingAboveSolid || isFacingSolid && isTwoAboveSolid;
         if (this.isAggressive() || this.isFleeing) {
            this.entityData.set(SPOTTED_ACCESSOR, false);
         }

         this.setClimbing(this.horizontalCollision);
         this.entityData.set(CROUCHING_ACCESSOR, shouldCrouch);
         this.setCrawling(shouldCrawl);
      }

      if ((Boolean)this.entityData.get(SPOTTED_ACCESSOR)) {
         this.playSpottedSound();
      }

      this.refreshDimensions();
      this.getNavigation().setSpeedModifier(this.getSpeedModifier());
      super.tick();
   }

   public double getSpeedModifier() {
      if (this.isSurfaceStalkRetreating()) return 1.2;
      return this.isCrawling() ? 0.35 : (this.isCrouching() ? 0.6 : 0.85);
   }

   @NotNull
   public EntityDimensions getDefaultDimensions(@NotNull Pose pose) {
      if ((Boolean)this.entityData.get(CRAWLING_ACCESSOR)) {
         return EntityDimensions.fixed(0.5F, 0.5F);
      } else {
         return this.entityData.get(CROUCHING_ACCESSOR) ? EntityDimensions.fixed(0.5F, 1.7F) : super.getDefaultDimensions(pose);
      }
   }

   private boolean isMoving() {
      Vec3 velocity = this.getDeltaMovement();
      float avgVelocity = (float)(Math.abs(velocity.x) + Math.abs(velocity.z)) / 2.0F;
      return avgVelocity > 0.03F;
   }

   public void reRoll() {
      this.currentRoll = Roll.fromValue(new Random().nextInt(3));
   }

   public void pickRoll(@NotNull List<Roll> rolls) {
      this.currentRoll = rolls.get(new Random().nextInt(rolls.size()));
   }

   public boolean onClimbable() {
      return this.isClimbing();
   }

   public boolean isClimbing() {
      if (!(Boolean)ServerConfig.CAN_CLIMB.get()) {
         return false;
      } else {
         return this.getTarget() == null ? false : !this.isCrawling() && !this.isCrouching() && (Boolean)this.entityData.get(CLIMBING_ACCESSOR);
      }
   }

   public void setSpotted(boolean value) {
      this.entityData.set(SPOTTED_ACCESSOR, value);
   }

   public void setClimbing(boolean isClimbing) {
      this.entityData.set(CLIMBING_ACCESSOR, isClimbing);
   }

   @NotNull
   protected PathNavigation createNavigation(@NotNull Level level) {
      WallClimberNavigation navigation = new WallClimberNavigation(this, level);
      navigation.setMaxVisitedNodesMultiplier(4.0F);
      return navigation;
   }

   private PlayState predicate(AnimationState<PaleHoundEntity> state) {
      boolean isCurrentAboveSolid = this.level().getBlockState(this.blockPosition().above()).isSolid();
      boolean isCurrentTwoAboveSolid = this.level().getBlockState(this.blockPosition().above(2)).isSolid();
      if (this.isCrawling() || isCurrentAboveSolid) {
         return state.setAndContinue(this.CRAWL);
      } else if (isCurrentTwoAboveSolid) {
         return state.isMoving() ? state.setAndContinue(this.CROUCH_RUN) : state.setAndContinue(this.CROUCH_IDLE);
      } else if (this.isAggressive()) {
         return state.isMoving() ? state.setAndContinue(this.CHASE) : state.setAndContinue(this.IS_SPOTTED);
      } else if ((Boolean)this.entityData.get(FLEEING_ACCESSOR)) {
         return state.isMoving() ? state.setAndContinue(this.FLEE) : state.setAndContinue(this.CHASE_IDLE);
      } else if (!this.pleaseStopMoving && (!(Boolean)this.entityData.get(SPOTTED_ACCESSOR) || state.isMoving())) {
         return state.isMoving() ? state.setAndContinue(this.CALM_RUN) : state.setAndContinue(this.CALM_STILL);
      } else {
         return state.setAndContinue(this.IS_SPOTTED);
      }
   }

   public void registerControllers(ControllerRegistrar registrar) {
      registrar.add(new AnimationController<>(this, "controller", 3, this::predicate));
   }

   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return this.cache;
   }

   protected void playStepSound(@NotNull BlockPos pPos, @NotNull BlockState pState) {
      super.playStepSound(pPos, pState);
      this.playEntitySound(this.chooseStep());
   }

   private void playEntitySound(SoundEvent soundEvent) {
      this.playEntitySound(soundEvent, 1.0F, 1.0F);
   }

   private void playEntitySound(SoundEvent soundEvent, float volume, float pitch) {
      this.level().playSound(null, this, soundEvent, SoundSource.HOSTILE, volume, pitch);
   }

   private void playBlockPosSound(ResourceLocation soundResource, float volume, float pitch) {
      if (this.level() instanceof ServerLevel serverLevel) {
         int radius = 32;
         serverLevel.getPlayers(player -> player.distanceToSqr(this) <= radius * radius)
            .forEach(
               player -> PacketDistributor.sendToPlayer(player, new CaveSound(soundResource, this.blockPosition(), volume, pitch))
            );
      }
   }

   public void playChaseSound() {
      if (this.startedPlayingChaseSound || this.isMoving()) {
         if (this.chaseSoundClock <= 0) {
            Random rand = new Random();
            switch (rand.nextInt(4)) {
               case 0:
                  this.playEntitySound((SoundEvent)ModSounds.CHASE_1.get(), 3.0F, 1.0F);
                  break;
               case 1:
                  this.playEntitySound((SoundEvent)ModSounds.CHASE_2.get(), 3.0F, 1.0F);
                  break;
               case 2:
                  this.playEntitySound((SoundEvent)ModSounds.CHASE_3.get(), 3.0F, 1.0F);
                  break;
               case 3:
                  this.playEntitySound((SoundEvent)ModSounds.CHASE_4.get(), 3.0F, 1.0F);
            }

            this.startedPlayingChaseSound = true;
            this.resetChaseSoundClock();
         }

         this.chaseSoundClock--;
      }
   }

   public void playDisappearSound() {
      this.playBlockPosSound(((SoundEvent)ModSounds.DISAPPEAR.get()).getLocation(), 3.0F, 1.0F);
   }

   public void playFleeSound() {
      if (!this.alreadyPlayedFleeSound) {
         Random rand = new Random();
         switch (rand.nextInt(2)) {
            case 0:
               this.playEntitySound((SoundEvent)ModSounds.FLEE_1.get(), 3.0F, 1.0F);
               break;
            case 1:
               this.playEntitySound((SoundEvent)ModSounds.FLEE_2.get(), 3.0F, 1.0F);
         }

         this.alreadyPlayedFleeSound = true;
      }
   }

   private void playSpottedSound() {
      if (!this.alreadyPlayedSpottedSound) {
         this.playEntitySound((SoundEvent)ModSounds.SPOTTED.get(), 3.0F, 1.0F);
         this.alreadyPlayedSpottedSound = true;
      }
   }

   private void resetChaseSoundClock() {
      this.chaseSoundClock = Utils.secondsToTicks(5);
   }

   private SoundEvent chooseAmbientSound() {
      Random rand = new Random();

      return switch (rand.nextInt(7)) {
         case 1 -> (SoundEvent)ModSounds.WOLF_IDLE2.get();
         case 2 -> (SoundEvent)ModSounds.WOLF_IDLE3.get();
         case 3 -> (SoundEvent)ModSounds.WOLF_HOWL1.get();
         case 4 -> (SoundEvent)ModSounds.WOLF_HOWL2.get();
         case 5 -> (SoundEvent)ModSounds.WOLF_WHINE.get();
         case 6 -> (SoundEvent)ModSounds.WOLF_PANTING.get();
         default -> (SoundEvent)ModSounds.WOLF_IDLE1.get();
      };
   }

   public int getAmbientSoundInterval() {
      return Utils.secondsToTicks(20);
   }

   public void playAmbientSound() {
      SoundEvent soundEvent = this.chooseAmbientSound();
      this.playEntitySound(soundEvent, 2.0F, 1.0F);
   }

   private SoundEvent chooseStep() {
      Random rand = new Random();

      return switch (rand.nextInt(4)) {
         case 1 -> (SoundEvent)ModSounds.CHASE_STEP_2.get();
         case 2 -> (SoundEvent)ModSounds.CHASE_STEP_3.get();
         case 3 -> (SoundEvent)ModSounds.CHASE_STEP_4.get();
         default -> (SoundEvent)ModSounds.CHASE_STEP_1.get();
      };
   }

   private SoundEvent chooseHurtSound() {
      Random rand = new Random();

      return switch (rand.nextInt(4)) {
         case 1 -> (SoundEvent)ModSounds.HOUND_HURT_2.get();
         case 2 -> (SoundEvent)ModSounds.HOUND_HURT_3.get();
         case 3 -> (SoundEvent)ModSounds.HOUND_HURT_4.get();
         default -> (SoundEvent)ModSounds.HOUND_HURT_1.get();
      };
   }

   protected void playHurtSound(@NotNull DamageSource pSource) {
      SoundEvent soundevent = this.chooseHurtSound();
      this.playEntitySound(soundevent, 2.0F, 1.0F);
   }

   public void setCrawling(boolean shouldCrawl) {
      if (shouldCrawl) {
         this.getEntityData().set(CROUCHING_ACCESSOR, false);
      }

      this.getEntityData().set(CRAWLING_ACCESSOR, shouldCrawl);
      this.refreshDimensions();
   }

   public boolean isCrawling() {
      return (Boolean)this.entityData.get(CRAWLING_ACCESSOR);
   }

   protected void tickDeath() {
      this.getLootTable();
      super.tickDeath();
      if (!this.alreadyPlayedDeathSound) {
         this.playBlockPosSound(((SoundEvent)ModSounds.HOUND_DEATH.get()).getLocation(), 2.0F, 1.0F);
         this.alreadyPlayedDeathSound = true;
      }
   }

   public boolean isLookingAtMe(Entity target, boolean directlyLooking) {
      if (!Utils.isValidTarget(target)) {
         return false;
      } else if (target.getEyePosition(1.0F).distanceTo(this.getPosition(1.0F)) > ((Integer)ServerConfig.SPOTTING_RANGE.get()).intValue()) {
         return false;
      } else {
         Vec3 viewVector = target.getViewVector(1.0F).normalize();
         Vec3 difference = new Vec3(this.getX() - target.getX(), this.getEyeY() - target.getEyeY(), this.getZ() - target.getZ());
         difference = difference.normalize();
         double dot = viewVector.dot(difference);
         return directlyLooking && target instanceof Player player ? dot > 0.99 && player.hasLineOfSight(this) : dot > 0.3;
      }
   }

   public boolean teleportToTarget() {
      LivingEntity target = this.getTarget();
      if (target == null) {
         return false;
      } else {
         Vec3 targetPosition = new Vec3(this.getX() - target.getX(), this.getY(0.5) - target.getEyeY(), this.getZ() - target.getZ());
         targetPosition = targetPosition.normalize();
         double radius = 16.0;
         double d1 = this.getX() + (this.getRandom().nextDouble() - 0.5) * (radius / 2.0) - targetPosition.x * radius;
         double d2 = this.getY() + (this.getRandom().nextInt((int)radius) - radius / 2.0) - targetPosition.y * radius;
         double d3 = this.getZ() + (this.getRandom().nextDouble() - 0.5) * (radius / 2.0) - targetPosition.z * radius;
         MutableBlockPos validPosition = BlockPos.containing(d1, d2, d3).mutable();

         while (validPosition.getY() > this.level().getMinBuildHeight() && !this.level().getBlockState(validPosition).blocksMotion()) {
            validPosition.move(Direction.DOWN);
         }

         this.teleportTo(validPosition.getX(), validPosition.getY(), validPosition.getZ());
         return true;
      }
   }

   private Vec3i getDirectionVector() {
      return new Vec3i(this.getDirection().getStepX(), this.getDirection().getStepY(), this.getDirection().getStepZ());
   }

   protected SoundEvent getAmbientSound() {
      return this.chooseAmbientSound();
   }

   protected SoundEvent getHurtSound(@NotNull DamageSource damageSourceIn) {
      return this.chooseHurtSound();
   }

   protected SoundEvent getDeathSound() {
      return (SoundEvent)ModSounds.HOUND_DEATH.get();
   }

   protected float getSoundVolume() {
      return 0.4F;
   }
}
