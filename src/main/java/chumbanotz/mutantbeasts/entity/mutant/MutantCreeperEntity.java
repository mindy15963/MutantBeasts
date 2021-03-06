package chumbanotz.mutantbeasts.entity.mutant;

import java.util.EnumSet;

import chumbanotz.mutantbeasts.entity.CreeperMinionEggEntity;
import chumbanotz.mutantbeasts.entity.CreeperMinionEntity;
import chumbanotz.mutantbeasts.entity.MBEntityType;
import chumbanotz.mutantbeasts.entity.ai.goal.AvoidDamageGoal;
import chumbanotz.mutantbeasts.entity.ai.goal.MBHurtByTargetGoal;
import chumbanotz.mutantbeasts.entity.ai.goal.MBMeleeAttackGoal;
import chumbanotz.mutantbeasts.pathfinding.MBGroundPathNavigator;
import chumbanotz.mutantbeasts.util.EntityUtil;
import chumbanotz.mutantbeasts.util.MBSoundEvents;
import chumbanotz.mutantbeasts.util.MutatedExplosion;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntitySize;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Pose;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAtGoal;
import net.minecraft.entity.ai.goal.LookRandomlyGoal;
import net.minecraft.entity.ai.goal.NearestAttackableTargetGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WaterAvoidingRandomWalkingGoal;
import net.minecraft.entity.effect.LightningBoltEntity;
import net.minecraft.entity.monster.CreeperEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.IFluidState;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.pathfinding.PathNavigator;
import net.minecraft.tags.Tag;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityPredicates;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Explosion;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public class MutantCreeperEntity extends CreeperEntity {
	private static final DataParameter<Byte> STATUS = EntityDataManager.createKey(MutantCreeperEntity.class, DataSerializers.BYTE);
	public static final int MAX_CHARGE_TIME = 100;
	public static final int MAX_DEATH_TIME = 100;
	private int chargeTime;
	private int chargeHits;
	private boolean canSummonLightning;
	private DamageSource deathCause = DamageSource.GENERIC;

	public MutantCreeperEntity(EntityType<? extends MutantCreeperEntity> type, World worldIn) {
		super(type, worldIn);
		this.stepHeight = 1.5F;
		this.experienceValue = 30;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new SwimGoal(this));
		this.goalSelector.addGoal(1, new MutantCreeperEntity.JumpAttackGoal());
		this.goalSelector.addGoal(1, new MutantCreeperEntity.SpawnMinionsGoal());
		this.goalSelector.addGoal(1, new MutantCreeperEntity.ChargeAttackGoal());
		this.goalSelector.addGoal(2, new MBMeleeAttackGoal(this, 1.3D));
		this.goalSelector.addGoal(3, new AvoidDamageGoal(this, 1.3D));
		this.goalSelector.addGoal(4, new WaterAvoidingRandomWalkingGoal(this, 1.0D));
		this.goalSelector.addGoal(5, new LookAtGoal(this, PlayerEntity.class, 8.0F));
		this.goalSelector.addGoal(5, new LookRandomlyGoal(this));
		this.targetSelector.addGoal(1, new MBHurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, PlayerEntity.class, true).setUnseenMemoryTicks(300));
		this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AnimalEntity.class, 100, true, true, EntityUtil::isFeline));
	}

	@Override
	protected void registerAttributes() {
		super.registerAttributes();
		this.getAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(120.0D);
		this.getAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(5.0D);
		this.getAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.26D);
		this.getAttribute(SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(24.0D);
		this.getAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
		this.getAttribute(SWIM_SPEED).setBaseValue(4.5D);
	}

	@Override
	protected void registerData() {
		super.registerData();
		this.dataManager.register(STATUS, (byte)0);
	}

	@Override
	public boolean getPowered() {
		return (this.dataManager.get(STATUS) & 1) != 0;
	}

	private void setPowered(boolean powered) {
		byte b0 = this.dataManager.get(STATUS);
		this.dataManager.set(STATUS, powered ? (byte)(b0 | 1) : (byte)(b0 & -2));
	}

	public boolean isJumpAttacking() {
		return (this.dataManager.get(STATUS) & 2) != 0;
	}

	private void setJumpAttacking(boolean jumping) {
		byte b0 = this.dataManager.get(STATUS);
		this.dataManager.set(STATUS, jumping ? (byte)(b0 | 2) : (byte)(b0 & -3));
	}

	public boolean isCharging() {
		return (this.dataManager.get(STATUS) & 4) != 0;
	}

	private void setCharging(boolean flag) {
		byte b0 = this.dataManager.get(STATUS);
		this.dataManager.set(STATUS, flag ? (byte)(b0 | 4) : (byte)(b0 & -5));
	}

	@Override
	protected float getStandingEyeHeight(Pose poseIn, EntitySize sizeIn) {
		return 2.6F;
	}

	@Override
	protected PathNavigator createNavigator(World worldIn) {
		return new MBGroundPathNavigator(this, worldIn);
	}

	@Override
	public void fall(float distance, float damageMultiplier) {
	}

	@Override
	public boolean attackEntityAsMob(Entity entityIn) {
		boolean flag = entityIn.attackEntityFrom(DamageSource.causeMobDamage(this), (float)(this.getAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getValue()));
		double x = entityIn.posX - this.posX;
		double y = entityIn.posY - this.posY;
		double z = entityIn.posZ - this.posZ;
		double d = Math.sqrt(x * x + y * y + z * z);
		entityIn.addVelocity(x / d * 0.5D, y / d * 0.2D, z / d * 0.5D);
		entityIn.stopRiding();
		this.applyEnchantments(this, entityIn);
		return flag;
	}

	@Override
	public boolean attackEntityFrom(DamageSource source, float amount) {
		if (this.getPowered() && source.isFireDamage()) {
			this.extinguish();
			return false;
		}

		if (source.isExplosion()) {
			float healAmount = amount / 2.0F;

			if (!(source.getTrueSource() instanceof MutantCreeperEntity) && this.getHealth() < this.getMaxHealth()) {
				this.heal(healAmount);
				if (this.world instanceof ServerWorld) {
					for (int i = 0; i < (int)(healAmount / 2.0F); i++) {
						double d0 = this.rand.nextGaussian() * 0.02D;
						double d1 = this.rand.nextGaussian() * 0.02D;
						double d2 = this.rand.nextGaussian() * 0.02D;
						((ServerWorld)this.world).spawnParticle(ParticleTypes.HEART, this.posX + (double)(this.rand.nextFloat() * this.getWidth() * 2.0F) - (double)this.getWidth(), this.posY + 0.5D + (double)(this.rand.nextFloat() * this.getHeight()), this.posZ + (double)(this.rand.nextFloat() * this.getWidth() * 2.0F) - (double)this.getWidth(), 0, d0, d1, d2, 1.0D);
					}
				}
			}

			return true;
		} else if (this.isCharging()) {
			if (!source.isMagicDamage() && source.getImmediateSource() instanceof LivingEntity) {
				source.getImmediateSource().attackEntityFrom(DamageSource.causeMobDamage(this).setDamageBypassesArmor(), 2.0F);
			}

			if (!this.world.isRemote && amount > 0.0F && source.getImmediateSource() != null && super.attackEntityFrom(source, amount)) {
				--this.chargeHits;
			}
		}

		return !(source.getTrueSource() instanceof CreeperEntity) && super.attackEntityFrom(source, amount);
	}

	@Override
	public void onStruckByLightning(LightningBoltEntity lightningBolt) {
		this.setPowered(true);
	}

	@Override
	protected boolean processInteract(PlayerEntity player, Hand hand) {
		return false;
	}

	@Override
	public int getMaxSpawnedInChunk() {
		return 1;
	}

	@Override
	protected void func_213623_ec() {
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	public void handleStatusUpdate(byte id) {
		if (id == 6) {
			for (int i = 0; i < 15; ++i) {
				double d0 = this.rand.nextGaussian() * 0.02D;
				double d1 = this.rand.nextGaussian() * 0.02D;
				double d2 = this.rand.nextGaussian() * 0.02D;
				this.world.addParticle(ParticleTypes.HEART, this.posX + (double)(this.rand.nextFloat() * this.getWidth() * 2.0F) - (double)this.getWidth(), this.posY + 0.5D + (double)(this.rand.nextFloat() * this.getHeight()), this.posZ + (double)(this.rand.nextFloat() * this.getWidth() * 2.0F) - (double)this.getWidth(), d0, d1, d2);
			}
		} else {
			super.handleStatusUpdate(id);
		}
	}

	@Override
	public void tick() {
		super.tick();

		if (!this.world.isRemote) {
			if (this.isJumpAttacking()) {
				if (this.onGround || this.world.containsAnyLiquid(this.getBoundingBox())) {
					this.setJumpAttacking(false);
					MutatedExplosion.create(this, this.getPowered() ? 6.0F : 4.0F, false, MutatedExplosion.Mode.DESTROY);
				}
			} else if (this.isAlive() && !this.isAIDisabled() && this.isEntityInsideOpaqueBlock() && this.ticksExisted % 30 == 0) {
				this.setJumpAttacking(true);
			}
		}
	}

	@Override
	protected boolean canBeRidden(Entity entityIn) {
		return false;
	}

	@Override
	public boolean isPushedByWater() {
		return false;
	}

	@Override
	protected void constructKnockBackVector(LivingEntity livingEntity) {
		livingEntity.applyEntityCollision(this);
		livingEntity.velocityChanged = true;
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	public float getCreeperFlashIntensity(float partialTicks) {
		float f = (float)this.deathTime / (float)MAX_DEATH_TIME;

		if (this.isCharging()) {
			int i = this.ticksExisted % 20;
			f = i < 10 ? 0.6F : 0.0F;
		}

		return f * 255.0F;
	}

	@Override
	public void onDeath(DamageSource cause) {
		if (!this.world.isRemote) {
			this.deathCause = cause;
			this.setCharging(false);

			if (!this.isSilent()) {
				this.world.playMovingSound(null, this, MBSoundEvents.ENTITY_MUTANT_CREEPER_DEATH, this.getSoundCategory(), 2.0F, 1.0F);
			}

			if (cause.getTrueSource() instanceof PlayerEntity) {
				this.attackingPlayer = (PlayerEntity)cause.getTrueSource();
			}

			if (this.recentlyHit > 0) {
				this.recentlyHit += MAX_DEATH_TIME;
			}
		}
	}

	@Override
	protected void onDeathUpdate() {
		float power = this.getPowered() ? 12.0F : 8.0F;
		float radius = power * 1.5F;

		for (Entity entity : this.world.getEntitiesInAABBexcluding(this, this.getBoundingBox().grow((double)radius), EntityPredicates.CAN_AI_TARGET)) {
			double x = this.posX - entity.posX;
			double y = this.posY - entity.posY;
			double z = this.posZ - entity.posZ;
			double d = Math.sqrt(x * x + y * y + z * z);
			float scale = (float)this.deathTime / (float)MAX_DEATH_TIME;
			entity.addVelocity(x / d * (double)scale * 0.09D, y / d * (double)scale * 0.09D, z / d * (double)scale * 0.09D);
		}

		this.setPosition(this.posX + (double)(this.rand.nextFloat() * 0.2F) - 0.10000000149011612D, this.posY, this.posZ + (double)(this.rand.nextFloat() * 0.2F) - 0.10000000149011612D);

		if (!this.world.areCollisionShapesEmpty(this)) {
			this.pushOutOfBlocks(this.posX, (this.getBoundingBox().minY + this.getBoundingBox().maxY) / 2.0D, this.posZ);
		}

		if (++this.deathTime == MAX_DEATH_TIME) {
			if (!this.world.isRemote) {
				MutatedExplosion.create(this, power, this.isBurning(), MutatedExplosion.Mode.BREAK);
				EntityUtil.spawnLingeringCloud(this);
				super.onDeath(this.deathCause);

				if (EntityUtil.dropExperience(this, this.recentlyHit, this::getExperiencePoints, this.attackingPlayer) && this.attackingPlayer != null) {
					CreeperMinionEggEntity egg = new CreeperMinionEggEntity(this.world, this.attackingPlayer);
					egg.setPosition(this.posX, this.posY, this.posZ);
					this.world.addEntity(egg);
				}
			}

			this.remove();
		}
	}

	@Override
	public void setMotionMultiplier(BlockState p_213295_1_, Vec3d p_213295_2_) {
		super.setMotionMultiplier(p_213295_1_, p_213295_2_.scale(6.0D));
	}

	@Override
	public boolean ableToCauseSkullDrop() {
		return this.getPowered();
	}

	@Deprecated
	@Override
	public int getCreeperState() {
		return -1;
	}

	@Override
	protected void handleFluidJump(Tag<Fluid> fluidTag) {
		this.setMotion(this.getMotion().add(0.0D, 0.04D, 0.0D));
	}

	@Override
	public float getExplosionResistance(Explosion explosionIn, IBlockReader worldIn, BlockPos pos, BlockState blockStateIn, IFluidState fluidState, float resistance) {
		return this.getPowered() && !blockStateIn.isAir(worldIn, pos) && !net.minecraft.tags.BlockTags.WITHER_IMMUNE.contains(blockStateIn.getBlock()) ? Math.min(0.8F, resistance) : resistance;
	}

	@Override
	public void playAmbientSound() {
		if (this.getAttackTarget() == null) {
			super.playAmbientSound();
		}
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return MBSoundEvents.ENTITY_MUTANT_CREEPER_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource damageSourceIn) {
		return MBSoundEvents.ENTITY_MUTANT_CREEPER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return MBSoundEvents.ENTITY_MUTANT_CREEPER_HURT;
	}

	@Override
	public void writeAdditional(CompoundNBT compound) {
		super.writeAdditional(compound);
		compound.putBoolean("JumpAttacking", this.isJumpAttacking());
		compound.putBoolean("Charging", this.isCharging());
		compound.putInt("ChargeTime", this.chargeTime);
		compound.putInt("ChargeHits", this.chargeHits);
		compound.putBoolean("SummonLightning", this.canSummonLightning);

		if (this.getPowered()) {
			compound.putBoolean("Powered", true);
		}

		if (this.deathTime > 0 && this.attackingPlayer != null) {
			compound.putUniqueId("KillerUUID", this.attackingPlayer.getUniqueID());
		}

		for (String s : new String[] {"powered", "Fuse", "ExplosionRadius", "ignited"}) {
			compound.remove(s);
		}
	}

	@Override
	public void readAdditional(CompoundNBT compound) {
		super.readAdditional(compound);
		this.setPowered(compound.getBoolean("powered") || compound.getBoolean("Powered"));
		this.setJumpAttacking(compound.getBoolean("JumpAttacking"));
		this.setCharging(compound.getBoolean("Charging"));
		this.chargeTime = compound.getInt("ChargeTime");
		this.chargeHits = compound.getInt("ChargeHits");
		this.canSummonLightning = compound.getBoolean("SummonLightning");

		if (compound.contains("KillerUUID")) {
			this.recentlyHit = Integer.MAX_VALUE;
			this.attackingPlayer = this.world.getPlayerByUuid(compound.getUniqueId("KillerUUID"));
		}

		if (this.deathTime > 0 && !this.isSilent()) {
			this.world.playMovingSound(null, this, MBSoundEvents.ENTITY_MUTANT_CREEPER_DEATH, this.getSoundCategory(), 2.0F, 1.0F);
		}
	}

	class SpawnMinionsGoal extends Goal {
		@Override
		public boolean shouldExecute() {
			float chance = !hasPath() || getLastDamageSource() != null && getLastDamageSource().isProjectile() ? 2.5F : 0.6F;
			return getAttackTarget() != null && getDistanceSq(getAttackTarget()) <= 1024.0D && !isCharging() && !isJumpAttacking() && rand.nextFloat() * 100.0F < chance;
		}

		@Override
		public boolean shouldContinueExecuting() {
			return false;
		}

		@Override
		public void startExecuting() {
			int maxSpawn = world.getDifficulty().getId() * 2;
			for (int i = (int)Math.ceil((double)getHealth() / getMaxHealth() * (double)maxSpawn); i > 0; --i) {
				CreeperMinionEntity creeper = MBEntityType.CREEPER_MINION.create(world);
				double x = posX + (double)(rand.nextFloat() - rand.nextFloat());
				double y = posY + (double)(rand.nextFloat() * 0.5F);
				double z = posZ + (double)(rand.nextFloat() - rand.nextFloat());
				double xx = getAttackTarget().posX - posX;
				double yy = getAttackTarget().posY - posY;
				double zz = getAttackTarget().posZ - posZ;
				creeper.setMotion(xx * 0.15D + (double)(rand.nextFloat() * 0.05F), yy * 0.15D + (double)(rand.nextFloat() * 0.05F), zz * 0.15D + (double)(rand.nextFloat() * 0.05F));
				creeper.setLocationAndAngles(x, y, z, rotationYaw, rotationPitch);
				creeper.setOwnerId(entityUniqueID);
				creeper.setAttackTarget(getAttackTarget());
				creeper.setPowered(getPowered());
				world.addEntity(creeper);
			}
		}
	}

	class ChargeAttackGoal extends Goal {
		public ChargeAttackGoal() {
			this.setMutexFlags(EnumSet.of(Goal.Flag.JUMP, Goal.Flag.LOOK, Goal.Flag.MOVE));
		}

		@Override
		public boolean shouldExecute() {
			LivingEntity target = getAttackTarget();
			boolean attemptHeal = !(getMaxHealth() - getHealth() < getMaxHealth() / 6.0F);
			return target != null && onGround && attemptHeal && getDistanceSq(target) >= 25.0D && getDistanceSq(target) <= 1024.0D ? rand.nextFloat() * 100.0F < 0.7F : isCharging();
		}

		@Override
		public boolean shouldContinueExecuting() {
			if (canSummonLightning && getAttackTarget() != null && getDistanceSq(getAttackTarget()) < 25.0D) {
				return false;
			}

			return chargeTime < MAX_CHARGE_TIME && chargeHits > 0;
		}

		@Override
		public void startExecuting() {
			setCharging(true);
			navigator.clearPath();

			if (chargeHits == 0) {
				chargeHits = 3 + rand.nextInt(3);
			}

			if (rand.nextInt(world.isThundering() ? 2 : 6) == 0 && !getPowered()) {
				canSummonLightning = true;
			}
		}

		@Override
		public void tick() {
			int i = chargeTime % 20;

			if (i == 0 || i == 20) {
				playSound(MBSoundEvents.ENTITY_MUTANT_CREEPER_CHARGE, 0.6F, 0.7F + rand.nextFloat() * 0.6F);
			}

			++chargeTime;
		}

		@Override
		public void resetTask() {
			if (canSummonLightning && getAttackTarget() != null && getDistanceSq(getAttackTarget()) < 25.0D && world.canBlockSeeSky(getPosition())) {
				((ServerWorld)world).addLightningBolt(new LightningBoltEntity(world, posX, posY, posZ, false));
			} else {
				if (chargeTime >= MAX_CHARGE_TIME) {
					heal(30.0F);
					world.setEntityState(MutantCreeperEntity.this, (byte)6);
				}
			}

			chargeTime = 0;
			chargeHits = 4 + rand.nextInt(3);
			setCharging(false);
			canSummonLightning = false;
		}
	}

	class JumpAttackGoal extends Goal {
		@Override
		public boolean shouldExecute() {
			LivingEntity target = getAttackTarget();
			return target != null && getDistanceSq(target) <= 1024.0D && onGround && !isCharging() ? rand.nextFloat() * 100.0F < 0.9F : false;
		}

		@Override
		public boolean shouldContinueExecuting() {
			return false;
		}

		@Override
		public void startExecuting() {
			setJumpAttacking(true);
			setMotion((getAttackTarget().posX - posX) * 0.2D, 1.4D, (getAttackTarget().posZ - posZ) * 0.2D);
		}
	}
}