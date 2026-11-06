package de.cadentem.pale_hound.entities;

import de.cadentem.pale_hound.config.ServerConfig;
import de.cadentem.pale_hound.PaleHound;
import de.cadentem.pale_hound.entities.goals.CustomHurtByTargetGoal;
import de.cadentem.pale_hound.entities.goals.PaleHoundBreakBlocksGoal;
import de.cadentem.pale_hound.entities.goals.PaleHoundBreakInvisGoal;
import de.cadentem.pale_hound.entities.goals.PaleHoundChaseGoal;
import de.cadentem.pale_hound.entities.goals.PaleHoundFleeGoal;
import de.cadentem.pale_hound.entities.goals.PaleHoundStareGoal;
import de.cadentem.pale_hound.entities.goals.PaleHoundRoamGoal;
import de.cadentem.pale_hound.entities.navigation.PaleHoundNavigation;
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
   private final RawAnimation CRAWL_IDLE = RawAnimation.begin().then("animation.pale_hound.crawl_idle", LoopType.LOOP);
   private final RawAnimation CRAWL_TUNNEL = RawAnimation.begin().then("animation.pale_hound.crawl_tunnel", LoopType.LOOP);
   private final RawAnimation CRAWL_TUNNEL_IDLE = RawAnimation.begin().then("animation.pale_hound.crawl_tunnel_idle", LoopType.LOOP);
   private final RawAnimation CRAWL_LOW = RawAnimation.begin().then("animation.pale_hound.crawl_low", LoopType.LOOP);
   private final RawAnimation CRAWL_LOW_IDLE = RawAnimation.begin().then("animation.pale_hound.crawl_low_idle", LoopType.LOOP);
   private final RawAnimation CRAWL_BACKWARD = RawAnimation.begin().thenLoop("animation.pale_hound.crawl_backward");
   private final RawAnimation CRAWL_TUNNEL_BACKWARD = RawAnimation.begin().thenLoop("animation.pale_hound.crawl_tunnel_backward");
   private final RawAnimation CRAWL_LOW_BACKWARD = RawAnimation.begin().thenLoop("animation.pale_hound.crawl_low_backward");
   public static final double ANIMATION_SPEED = 0.75;
   public static final int POSTURE_BLEND_TICKS = 12;
   private final RawAnimation FLEE = RawAnimation.begin().then("animation.pale_hound.flee", LoopType.LOOP);
   public static final EntityDataAccessor<Boolean> FLEEING_ACCESSOR = SynchedEntityData.defineId(PaleHoundEntity.class, EntityDataSerializers.BOOLEAN);
   public static final EntityDataAccessor<Boolean> CROUCHING_ACCESSOR = SynchedEntityData.defineId(PaleHoundEntity.class, EntityDataSerializers.BOOLEAN);
   public static final EntityDataAccessor<Boolean> CRAWLING_ACCESSOR = SynchedEntityData.defineId(PaleHoundEntity.class, EntityDataSerializers.BOOLEAN);
   public static final EntityDataAccessor<Boolean> SPOTTED_ACCESSOR = SynchedEntityData.defineId(PaleHoundEntity.class, EntityDataSerializers.BOOLEAN);
   public static final EntityDataAccessor<Boolean> CLIMBING_ACCESSOR = SynchedEntityData.defineId(PaleHoundEntity.class, EntityDataSerializers.BOOLEAN);
   public static final EntityDataAccessor<Boolean> BACKING_ACCESSOR = SynchedEntityData.defineId(PaleHoundEntity.class, EntityDataSerializers.BOOLEAN);
   public static final EntityDataAccessor<Boolean> GROUND_INSPECTION_ACCESSOR = SynchedEntityData.defineId(PaleHoundEntity.class, EntityDataSerializers.BOOLEAN);
   private de.cadentem.pale_hound.entities.goals.PaleHoundInspectItemGoal inspectItemGoal;
   public Roll currentRoll = Roll.STROLL;
   private final de.cadentem.pale_hound.entities.hunting.HoundHuntBrain huntBrain = new de.cadentem.pale_hound.entities.hunting.HoundHuntBrain(this);
   private final de.cadentem.pale_hound.entities.hunting.HoundHazardLearning hazardLearning = new de.cadentem.pale_hound.entities.hunting.HoundHazardLearning(this);
   private Player surfaceStalkTarget;
   private java.util.UUID commandEncounterPlayer;
   private final HoundAudioController encounterAudio = new HoundAudioController(this);
   private boolean spottedDuringSurfaceStalk;
   private int surfaceStalkTicks;
   private int surfaceRetreatTicks;
   private int surfaceGazeGraceTicks;
   private int surfaceRunReactionTicks;
   private java.util.UUID surfaceStalkAttackTarget;
   private final de.cadentem.pale_hound.entities.navigation.HoundEscapeVisibility escapeVisibility = new de.cadentem.pale_hound.entities.navigation.HoundEscapeVisibility();
   // Prefer stalking before ordinary target acquisition gets a chance on the first tick.
   private int surfaceStalkCheckTicks;
   public boolean isFleeing;
   public boolean hasSpawned;
   public boolean pleaseStopMoving;
   public boolean targetIsFacingMe;
   private int ticksTillRemove;
   private int idleRecoveryTicks;
   private CrawlPose animationCrawlPose = CrawlPose.TALL;
   private CrawlPose pendingAnimationPose = CrawlPose.TALL;
   private int animationPosePendingSince;
   private PaleHoundRoamGoal roamingGoal;
   private int chaseSoundClock;
   private boolean alreadyPlayedFleeSound;
   private boolean alreadyPlayedSpottedSound;
   private boolean startedPlayingChaseSound;
   private boolean alreadyPlayedDeathSound;

   public PaleHoundEntity(EntityType<? extends PaleHoundEntity> entityType, Level level) {
      super(entityType, level);
      this.moveControl = new de.cadentem.pale_hound.entities.navigation.HoundMoveControl(this);
      this.refreshDimensions();
      this.ticksTillRemove = Utils.presenceTicks((Integer)ServerConfig.TIME_UNTIL_LEAVE.get());
      this.setPathfindingMalus(PathType.UNPASSABLE_RAIL, 0.0F);
      this.setPathfindingMalus(PathType.WATER, 8.0F);
      this.setPathfindingMalus(PathType.DANGER_FIRE, 8.0F);
      this.setPathfindingMalus(PathType.DAMAGE_FIRE, -1.0F);
      this.setPathfindingMalus(PathType.LAVA, -1.0F);
      this.setPathfindingMalus(PathType.POWDER_SNOW, -1.0F);
      this.hazardLearning.applyPathCosts();
   }

   public void applyConfigAttributes() {
      this.setAttribute(this.getAttribute(Attributes.ARMOR), (Double)ServerConfig.ARMOR.get());
      this.setAttribute(this.getAttribute(Attributes.MAX_HEALTH), (Double)ServerConfig.MAX_HEALTH.get());
      this.setAttribute(this.getAttribute(Attributes.ATTACK_DAMAGE), (Double)ServerConfig.ATTACK_DAMAGE.get());
      this.setAttribute(this.getAttribute(Attributes.ATTACK_SPEED), (Double)ServerConfig.ATTACK_SPEED.get());
      // Scale the configured value so existing world configs also receive the requested slowdown.
      this.setAttribute(this.getAttribute(Attributes.MOVEMENT_SPEED), ServerConfig.MOVEMENT_SPEED.get() * 0.8);
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
      double movementSpeed = 0.7 * 0.8;
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
      builder.define(BACKING_ACCESSOR, false);
      builder.define(GROUND_INSPECTION_ACCESSOR, false);
   }

   protected void registerGoals() {
      this.goalSelector.addGoal(0, new de.cadentem.pale_hound.entities.goals.PaleHoundHazardAvoidGoal(this));
      this.inspectItemGoal = new de.cadentem.pale_hound.entities.goals.PaleHoundInspectItemGoal(this);
      this.goalSelector.addGoal(1, this.inspectItemGoal);
      this.goalSelector.addGoal(1, new FloatGoal(this));
      this.goalSelector.addGoal(2, new PaleHoundSurfaceStalkGoal(this));
      this.goalSelector.addGoal(1, new de.cadentem.pale_hound.entities.goals.PaleHoundHuntGoal(this));
      this.goalSelector.addGoal(1, new PaleHoundBreakBlocksGoal(this));
      this.goalSelector.addGoal(2, new PaleHoundChaseGoal(this, true));
      this.goalSelector.addGoal(2, new PaleHoundFleeGoal(this, 40.0F, 1.2));
      this.goalSelector.addGoal(3, new PaleHoundBreakInvisGoal(this));
      this.goalSelector.addGoal(3, new PaleHoundStareGoal(this));
      this.roamingGoal = new PaleHoundRoamGoal(this, 0.35);
      this.goalSelector.addGoal(4, this.roamingGoal);
      this.targetSelector.addGoal(1, new CustomHurtByTargetGoal(this));
      this.targetSelector.addGoal(2, new PaleHoundTargetTooCloseGoal(this, 13.0F));
      this.targetSelector.addGoal(3, new PaleHoundTargetSeesMeGoal(this));
      this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Mob.class, 10, true, false, PaleHoundTargets::isAllowed));
      this.goalSelector.addGoal(5, new MeleeAttackGoal(this, 1.0, true) {
         @Override public boolean canUse() { return currentRoll == Roll.CHASE && !isSurfaceStalking() && !isFleeing && super.canUse(); }
         @Override public boolean canContinueToUse() { return currentRoll == Roll.CHASE && !isSurfaceStalking() && !isFleeing && super.canContinueToUse(); }
      });
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

   public boolean isBackingAway() { return this.entityData.get(BACKING_ACCESSOR); }
   public void setBackingAway(boolean backing) { this.entityData.set(BACKING_ACCESSOR, backing); }

   public boolean isCommandEncounter() { return this.commandEncounterPlayer != null; }

   public boolean isCommandEncounterTarget(Entity target) {
      return target instanceof Player && target.isAlive() && !target.isRemoved() && target.level() == this.level()
         && target.getUUID().equals(this.commandEncounterPlayer);
   }

   public boolean isValidEncounterTarget(Entity target) {
      return this.isCommandEncounterTarget(target) || Utils.isValidTarget(target);
   }

   public Player getCommandEncounterPlayer() {
      return this.commandEncounterPlayer == null ? null : this.level().getPlayerByUUID(this.commandEncounterPlayer);
   }

   public void markCommandEncounter(Player player) { this.commandEncounterPlayer = player.getUUID(); }

   public void startCommandEncounter(Player player) {
      this.markCommandEncounter(player);
      this.hasSpawned = true;
      this.setInvisible(false);
      this.currentRoll = Roll.STROLL;
      this.surfaceStalkCheckTicks = 100;
      if (this.getRandom().nextFloat() < 0.9F && this.tryStartSurfaceStalking(player)) return;
      this.currentRoll = Roll.STARE;
      this.setTarget(player);
   }

   @Override public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
      super.addAdditionalSaveData(tag);
      if (this.commandEncounterPlayer != null) tag.putUUID("CommandEncounterPlayer", this.commandEncounterPlayer);
      tag.put("LearnedHazards", this.hazardLearning.save());
   }

   @Override public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
      super.readAdditionalSaveData(tag);
      this.commandEncounterPlayer = tag.hasUUID("CommandEncounterPlayer") ? tag.getUUID("CommandEncounterPlayer") : null;
      this.hazardLearning.load(tag.getCompound("LearnedHazards"));
   }

   public boolean isSurfaceStalkAttack() {
      return this.surfaceStalkAttackTarget != null && this.getTarget() != null
         && this.surfaceStalkAttackTarget.equals(this.getTarget().getUUID());
   }

   public de.cadentem.pale_hound.entities.hunting.HoundHuntBrain getHuntBrain() { return this.huntBrain; }
   public de.cadentem.pale_hound.entities.hunting.HoundHazardLearning getHazardLearning() { return this.hazardLearning; }
   public boolean isAvoidingHazard() { return this.hazardLearning != null && this.hazardLearning.isAvoiding(); }
   public boolean canSeePlayer(Player player) { return this.huntBrain.canSee(player, false); }
   public boolean isInspectingItem() { return this.inspectItemGoal != null && this.inspectItemGoal.isActive(); }
   public de.cadentem.pale_hound.entities.goals.PaleHoundInspectItemGoal getItemInspectionGoal() { return this.inspectItemGoal; }
   public boolean isInspectingGround() { return this.entityData.get(GROUND_INSPECTION_ACCESSOR); }
   public void setInspectingGround(boolean value) {
      this.entityData.set(GROUND_INSPECTION_ACCESSOR, value);
      if (value) this.setCrawling(true);
   }
   public void cancelItemInspection() { if (this.inspectItemGoal != null) this.inspectItemGoal.cancel(); }
   public void resumeRoaming() {
      if (this.roamingGoal != null && !this.isSurfaceStalking() && this.getTarget() == null
         && !this.huntBrain.blocksLegacyTargeting() && this.currentRoll == Roll.STROLL) this.roamingGoal.trigger();
   }

   public void beginEvidenceInvestigation() {
      this.surfaceStalkTarget = null;
      this.surfaceRetreatTicks = 0;
      this.surfaceRunReactionTicks = 0;
      this.surfaceStalkCheckTicks = 100;
      this.setBackingAway(false);
      this.isFleeing = false;
      this.entityData.set(FLEEING_ACCESSOR, false);
      this.setTarget(null);
      this.setAggressive(this.huntBrain.isAngryTrail());
      this.setSpotted(false);
      this.setInvisible(false);
      this.currentRoll = Roll.STROLL;
      this.pleaseStopMoving = false;
      this.getNavigation().stop();
   }

   public void beginPlayerPursuit(Player player) {
      if (!this.isValidEncounterTarget(player) || player.level() != this.level() || this.huntBrain.enabled() && !this.hasLineOfSight(player)) return;
      this.surfaceStalkTarget = null;
      this.surfaceRetreatTicks = 0;
      this.surfaceRunReactionTicks = 0;
      this.surfaceStalkCheckTicks = 100;
      this.setBackingAway(false);
      this.isFleeing = false;
      this.entityData.set(FLEEING_ACCESSOR, false);
      this.setSpotted(false);
      this.setInvisible(false);
      this.pleaseStopMoving = false;
      this.getNavigation().stop();
      this.currentRoll = Roll.CHASE;
      this.setTarget(player);
      this.surfaceStalkAttackTarget = player.getUUID();
      this.setAggressive(true);
      this.huntBrain.acquired(player);
   }

   public Player getSurfaceStalkTarget() {
      return this.surfaceStalkTarget;
   }

   @Override
   public boolean doHurtTarget(Entity target) {
      return !this.isAvoidingHazard() && !this.isSurfaceStalking() && !this.isFleeing && this.currentRoll != Roll.FLEE
         && (!this.huntBrain.enabled() || !(target instanceof Player) || !this.huntBrain.blocksLegacyTargeting() && this.hasLineOfSight(target))
         && PaleHoundTargets.isAllowed(target, this) && super.doHurtTarget(target);
   }

   public boolean isSurfaceStalkRetreating() {
      return this.surfaceRetreatTicks > 0;
   }

   public boolean tryStartSurfaceStalking(Player player) {
      if (this.isAvoidingHazard() || this.huntBrain.blocksLegacyTargeting() || this.isSurfaceStalking() || this.isAggressive() || this.isFleeing
         || this.currentRoll == Roll.CHASE || this.currentRoll == Roll.FLEE
         || !this.isValidEncounterTarget(player) || !Utils.isOnSurface(this) || !Utils.isOnSurface(player)
         || this.distanceToSqr(player) > Math.pow(this.huntBrain.enabled() ? this.huntBrain.visionRange(player, true) : 64, 2) || this.huntBrain.enabled() && !this.hasLineOfSight(player)) return false;
      this.surfaceStalkTarget = player;
      this.huntBrain.observe(player);
      this.encounterAudio.playerDetected(player);
      this.spottedDuringSurfaceStalk = false;
      this.surfaceStalkTicks = (int)Math.ceil(Utils.scalePresenceTicks(this.getRandom().nextIntBetweenInclusive(600, 900))
         * ServerConfig.SURFACE_STALK_DURATION_MULTIPLIER.get());
      this.surfaceRetreatTicks = 0;
      this.surfaceGazeGraceTicks = 160;
      this.surfaceRunReactionTicks = 0;
      this.escapeVisibility.reset();
      this.getNavigation().stop();
      return true;
   }

   @Override
   public void setTarget(LivingEntity target) {
      LivingEntity previous = this.getTarget();
      boolean perceived = !(target instanceof Player) || this.huntBrain == null || !this.huntBrain.enabled() || this.huntBrain.canSee((Player)target, true);
      super.setTarget(!this.isSurfaceStalking() && perceived && PaleHoundTargets.isAllowed(target, this) ? target : null);
      if (!this.level().isClientSide() && this.huntBrain != null && this.getTarget() instanceof Player observed) this.huntBrain.observe(observed);
      if (this.surfaceStalkAttackTarget != null && (this.getTarget() == null || !this.surfaceStalkAttackTarget.equals(this.getTarget().getUUID())))
         this.surfaceStalkAttackTarget = null;
      if (!this.level().isClientSide() && this.encounterAudio != null && this.getTarget() instanceof Player player && previous != player)
         this.encounterAudio.playerDetected(player);
   }

   @Override
   public boolean canAttack(LivingEntity target) {
      return !this.isAvoidingHazard() && !this.isSurfaceStalking() && !this.isFleeing && this.currentRoll != Roll.FLEE
         && (!this.huntBrain.enabled() || !(target instanceof Player) || !this.huntBrain.blocksLegacyTargeting() && this.hasLineOfSight(target))
         && PaleHoundTargets.isAllowed(target, this) && super.canAttack(target);
   }

   @Override public void checkDespawn() {
      if ((this.isSurfaceStalking() || this.huntBrain.controlsMovement() || this.isFleeing || this.currentRoll == Roll.FLEE)
         && (this.level().getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL || this.isCommandEncounter())) {
         this.setNoActionTime(0);
         return;
      }
      super.checkDespawn();
   }

   @Override protected boolean shouldDespawnInPeaceful() {
      return !this.isCommandEncounter() && super.shouldDespawnInPeaceful();
   }

   private void updateSurfaceStalking() {
      if (!this.huntBrain.blocksLegacyTargeting() && !this.isSurfaceStalking() && --this.surfaceStalkCheckTicks <= 0) {
         this.surfaceStalkCheckTicks = 100;
         if (this.getRandom().nextFloat() < 0.9F) {
            Player player = this.getCommandEncounterPlayer();
            if (player == null) player = this.level().getNearestPlayer(this.getX(), this.getY(), this.getZ(), Math.max(64, ServerConfig.HUNT_LIT_VISION_RANGE.get()),
               candidate -> this.isValidEncounterTarget(candidate) && Utils.isOnSurface(candidate) && this.canSeePlayer((Player)candidate));
            this.tryStartSurfaceStalking(player);
         }
      }
      if (this.isSurfaceStalking()) {
         if (this.isAvoidingHazard() && !this.isSurfaceStalkRetreating()) return;
         if (this.surfaceGazeGraceTicks > 0) this.surfaceGazeGraceTicks--;
         if (!this.isSurfaceStalkRetreating()) {
            // Crowding him into 12 blocks takes priority over both the gaze reaction and backing off.
            Player intruder = this.level().players().stream()
               .filter(player -> this.isValidEncounterTarget(player) && this.distanceToSqr(player) <= 12 * 12
                  && Utils.isOnSurface(player) && this.hasLineOfSight(player) && !this.hazardLearning.threateningItem(player))
               .min(java.util.Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
            if (intruder != null) {
               this.beginPlayerPursuit(intruder);
               this.targetSelector.setControlFlag(net.minecraft.world.entity.ai.goal.Goal.Flag.TARGET, false);
               return;
            }
         }
         if (this.isSurfaceStalkRetreating()) {
            if (this.escapeVisibility.canDisappear(this, 60)) {
               if (this.spottedDuringSurfaceStalk && !this.isCommandEncounter() && this.level() instanceof ServerLevel serverLevel) {
                  PaleHound.scheduleSpawnAfterSpottedRetreat(serverLevel);
               }
               this.disappear();
               return;
            }
         } else {
            // Begin reacting at 14 blocks, and allow a one-second rush inside the 12-block attack radius.
            double reactionRange = this.surfaceRunReactionTicks > 0 ? 16 : 14;
            boolean closeGaze = this.surfaceGazeGraceTicks <= 0 && this.level().players().stream()
               .anyMatch(player -> this.distanceToSqr(player) <= reactionRange * reactionRange && this.isLookingAtMe(player, true));
            this.surfaceRunReactionTicks = closeGaze ? this.surfaceRunReactionTicks + 1 : 0;
            if (this.surfaceRunReactionTicks >= 20) {
               this.spottedDuringSurfaceStalk = true;
               this.surfaceRetreatTicks = 60;
               this.getNavigation().stop();
            } else if (--this.surfaceStalkTicks <= 0 || !this.isValidEncounterTarget(this.surfaceStalkTarget)
               || !Utils.isOnSurface(this) || !Utils.isOnSurface(this.surfaceStalkTarget)
               || this.distanceToSqr(this.surfaceStalkTarget) > Math.pow(Math.max(80, this.huntBrain.visionRange(this.surfaceStalkTarget, true) + 16), 2)) {
               this.surfaceRetreatTicks = 60;
               this.getNavigation().stop();
            }
         }
         if (this.isSurfaceStalkRetreating()) this.setBackingAway(false);
         this.setTarget(null);
         this.setAggressive(false);
         this.currentRoll = Roll.STROLL;
         this.isFleeing = this.isSurfaceStalkRetreating();
         this.entityData.set(FLEEING_ACCESSOR, this.isFleeing);
         this.setSpotted(false);
         this.pleaseStopMoving = false;
         this.setInvisible(false);
      }
      this.targetSelector.setControlFlag(net.minecraft.world.entity.ai.goal.Goal.Flag.TARGET, !this.isSurfaceStalking() && !this.isSurfaceStalkAttack() && !this.huntBrain.blocksLegacyTargeting());
   }

   public void tick() {
      if (this.isRemoved()) return;
      if (!this.level().isClientSide()) {
         this.hazardLearning.tick();
         if (!this.isNoAi()) this.huntBrain.tick();
         this.updateSurfaceStalking();
      }
      if (this.isRemoved()) return;
      if (!this.level().isClientSide() && !this.isSurfaceStalking() && !this.huntBrain.controlsMovement() && !this.isFleeing && this.currentRoll != Roll.FLEE && --this.ticksTillRemove <= 0) {
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
         boolean blockedStanding = !this.hasStandingClearance(this.position());
         var route = this.getNavigation().getPath();
         if (route != null && !route.isDone()) {
            Vec3 next = route.getNextEntityPos(this);
            if (this.distanceToSqr(next) < 2.25 && !this.hasStandingClearance(next)) blockedStanding = true;
         }
         boolean shouldCrouch = blockedStanding || isTwoAboveSolid
            || !isOffsetFacingSolid && !isOffsetFacingAboveSolid && (isOffsetFacingTwoAboveSolid || isFacingSolid && isThreeAboveSolid);
         boolean closeChase = this.currentRoll == Roll.CHASE && this.isAggressive()
            && this.getTarget() != null && this.getTarget().isAlive()
            && this.distanceToSqr(this.getTarget()) <= 12.0 * 12.0;
         boolean shouldCrawl = this.isInspectingItem() || this.isInspectingGround() || !this.isAvoidingHazard() && (this.huntBrain.shouldCrawl() || this.isSurfaceStalking() && !this.isSurfaceStalkRetreating()) || shouldCrouch || closeChase || isAboveSolid
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

      if (!this.level().isClientSide() && !this.isSurfaceStalking() && this.getTarget() == null) this.pleaseStopMoving = false;
      if (!this.level().isClientSide() && !this.isSurfaceStalking() && this.getTarget() == null
         && !this.huntBrain.controlsMovement() && !this.pleaseStopMoving && this.getNavigation().isDone() && this.roamingGoal != null) {
         if (++this.idleRecoveryTicks >= 100) {
            this.currentRoll = Roll.STROLL;
            this.setAggressive(false);
            this.isFleeing = false;
            this.entityData.set(FLEEING_ACCESSOR, false);
            this.roamingGoal.trigger();
            this.idleRecoveryTicks = 0;
         }
      } else {
         this.idleRecoveryTicks = 0;
      }
      this.refreshDimensions();
      this.getNavigation().setSpeedModifier(this.getSpeedModifier());
      if (!this.level().isClientSide()) this.entityData.set(FLEEING_ACCESSOR, this.isFleeing || this.isSurfaceStalkRetreating() || this.isAvoidingHazard());
      super.tick();
      if (!this.level().isClientSide()) this.encounterAudio.tick();
   }

   public double getSpeedModifier() {
      if (this.isSurfaceStalkRetreating() || this.isFleeing) return 1.2;
      if (this.isAvoidingHazard()) return 1.1;
      if (this.isBackingAway()) return 0.22;
      if (this.isInspectingItem()) return 0.35;
      if (this.huntBrain.controlsMovement()) return this.huntBrain.speed();
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

   @Override public int getMaxFallDistance() {
      // Fall immunity also needs to be reflected in path planning, especially on cave ledges.
      return 32;
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
      return new PaleHoundNavigation(this, level);
   }

   private PlayState predicate(AnimationState<PaleHoundEntity> state) {
      boolean isCurrentAboveSolid = this.level().getBlockState(this.blockPosition().above()).isSolid();
      boolean isCurrentTwoAboveSolid = this.level().getBlockState(this.blockPosition().above(2)).isSolid();
      boolean fleeing = this.entityData.get(FLEEING_ACCESSOR);
      state.getController().transitionLength(fleeing ? 3 : POSTURE_BLEND_TICKS);
      double horizontalSpeed = Math.hypot(this.getDeltaMovement().x, this.getDeltaMovement().z);
      state.getController().setAnimationSpeed(fleeing && state.isMoving()
         ? Math.max(1.0, Math.min(3.0, horizontalSpeed / 0.2)) : ANIMATION_SPEED);
      if (this.isCrawling() || isCurrentAboveSolid) {
         CrawlPose pose = this.getAnimationCrawlPose();
         if (!state.isMoving()) return state.setAndContinue(switch (pose) {
            case TALL -> this.CRAWL_IDLE;
            case TUNNEL -> this.CRAWL_TUNNEL_IDLE;
            case LOW -> this.CRAWL_LOW_IDLE;
         });
         Vec3 velocity = this.getDeltaMovement();
         double speed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
         state.getController().setAnimationSpeed(fleeing ? Math.max(1.0, Math.min(6.0, speed * 12))
            : ANIMATION_SPEED * Math.max(0.75, Math.min(3.0, speed * 50)));
         if (this.isBackingAway()) return state.setAndContinue(switch (pose) {
            case TALL -> this.CRAWL_BACKWARD;
            case TUNNEL -> this.CRAWL_TUNNEL_BACKWARD;
            case LOW -> this.CRAWL_LOW_BACKWARD;
         });
         return state.setAndContinue(switch (pose) {
            case TALL -> this.CRAWL;
            case TUNNEL -> this.CRAWL_TUNNEL;
            case LOW -> this.CRAWL_LOW;
         });
      } else if (isCurrentTwoAboveSolid) {
         return state.isMoving() ? state.setAndContinue(this.CROUCH_RUN) : state.setAndContinue(this.CROUCH_IDLE);
      } else if (fleeing) {
         return state.isMoving() ? state.setAndContinue(this.FLEE) : state.setAndContinue(this.CHASE_IDLE);
      } else if (this.isAggressive()) {
         return state.isMoving() ? state.setAndContinue(this.CHASE) : state.setAndContinue(this.IS_SPOTTED);
      } else if (!this.pleaseStopMoving && (!(Boolean)this.entityData.get(SPOTTED_ACCESSOR) || state.isMoving())) {
         return state.isMoving() ? state.setAndContinue(this.CALM_RUN) : state.setAndContinue(this.CALM_STILL);
      } else {
         return state.setAndContinue(this.IS_SPOTTED);
      }
   }

   public void registerControllers(ControllerRegistrar registrar) {
      registrar.add(new AnimationController<>(this, "controller", POSTURE_BLEND_TICKS, this::predicate));
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
      if (this.level() instanceof ServerLevel level && !this.isSilent() && !this.isAggressive() && !this.isFleeing
         && !this.encounterAudio.isUndergroundStalking()) {
         level.getPlayers(player -> player.distanceToSqr(this) <= 48 * 48).forEach(player ->
            PacketDistributor.sendToPlayer(player, new de.cadentem.pale_hound.network.HoundSoundCue(
               de.cadentem.pale_hound.network.HoundSoundCue.Kind.AMBIENT_CRYING, this.getUUID())));
      }
   }

   @Override public void remove(@NotNull RemovalReason reason) {
      if (!this.level().isClientSide() && this.encounterAudio != null) this.encounterAudio.stopStalking();
      super.remove(reason);
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

   public enum CrawlPose { TALL, TUNNEL, LOW }

   public CrawlPose getCrawlPose() {
      if (this.isInspectingGround()) return CrawlPose.LOW;
      double halfWidth = 0.25 * this.getScale();
      var tallBounds = new net.minecraft.world.phys.AABB(this.getX() - halfWidth, this.getY() + 0.000001,
         this.getZ() - halfWidth, this.getX() + halfWidth, this.getY() + 2.7 * this.getScale() - 0.000001,
         this.getZ() + halfWidth);
      Vec3 forward = Vec3.directionFromRotation(0, this.yBodyRot).scale(1.25 * this.getScale());
      tallBounds = tallBounds.expandTowards(forward.x, 0, forward.z);
      // Check collision shapes, including slabs and partial roof-edge overlaps.
      if (!this.level().getBlockCollisions(this, tallBounds).iterator().hasNext()) return CrawlPose.TALL;
      var tunnelBounds = new net.minecraft.world.phys.AABB(tallBounds.minX, tallBounds.minY, tallBounds.minZ,
         tallBounds.maxX, this.getY() + 2.0 * this.getScale() - 0.000001, tallBounds.maxZ);
      return this.level().getBlockCollisions(this, tunnelBounds).iterator().hasNext() ? CrawlPose.LOW : CrawlPose.TUNNEL;
   }

   /** Compress promptly, but require half a second of clearance before rising again. */
   public CrawlPose getAnimationCrawlPose() {
      CrawlPose requested = this.getCrawlPose();
      if (requested.ordinal() >= this.animationCrawlPose.ordinal()) {
         this.animationCrawlPose = requested;
         this.pendingAnimationPose = requested;
         this.animationPosePendingSince = this.tickCount;
      } else if (requested != this.pendingAnimationPose) {
         this.pendingAnimationPose = requested;
         this.animationPosePendingSince = this.tickCount;
      } else if (this.tickCount - this.animationPosePendingSince >= 10) {
         this.animationCrawlPose = requested;
      }
      return this.animationCrawlPose;
   }

   private boolean hasStandingClearance(Vec3 position) {
      double halfWidth = 0.25 * this.getScale();
      var bounds = new net.minecraft.world.phys.AABB(position.x - halfWidth, position.y + 0.000001,
         position.z - halfWidth, position.x + halfWidth, position.y + 2.7 * this.getScale() - 0.000001,
         position.z + halfWidth);
      return this.level().noCollision(this, bounds);
   }

   public void setCrawling(boolean shouldCrawl) {
      // Do not let a goal stop or a temporary path-search pose expand into surrounding blocks.
      if (!shouldCrawl && !this.hasStandingClearance(this.position())) shouldCrawl = true;
      if (shouldCrawl) {
         this.getEntityData().set(CROUCHING_ACCESSOR, false);
      }

      this.getEntityData().set(CRAWLING_ACCESSOR, shouldCrawl);
      this.refreshDimensions();
   }

   public boolean isCrawling() {
      return (Boolean)this.entityData.get(CRAWLING_ACCESSOR);
   }

   @Override public boolean shouldRenderAtSqrDistance(double distanceSquared) {
      // The tunnel collision box is only half a block tall. It must not shrink the
      // draw distance of a tall stalking model to 32 blocks on open terrain.
      double range = 128.0 * Math.max(1.0, this.getScale()) * Entity.getViewScale();
      return distanceSquared < range * range;
   }

   @Override public net.minecraft.world.phys.AABB getBoundingBoxForCulling() {
      double scale = this.getScale();
      return new net.minecraft.world.phys.AABB(this.getX() - 2 * scale, this.getY() - 0.25 * scale,
         this.getZ() - 2 * scale, this.getX() + 2 * scale, this.getY() + 3.5 * scale,
         this.getZ() + 2 * scale).minmax(super.getBoundingBoxForCulling());
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
      if (!this.isValidEncounterTarget(target)) {
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
      if (target == null || this.huntBrain.enabled()
         && (de.cadentem.pale_hound.entities.navigation.HoundEscapeVisibility.isVisible(this)
            || target instanceof Player player && !this.huntBrain.canSee(player, true))) {
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
