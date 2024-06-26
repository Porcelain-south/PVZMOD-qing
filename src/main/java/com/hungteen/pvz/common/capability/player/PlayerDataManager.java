package com.hungteen.pvz.common.capability.player;


import com.hungteen.pvz.PVZConfig;
import com.hungteen.pvz.api.PVZAPI;
import com.hungteen.pvz.api.events.PlayerLevelChangeEvent;
import com.hungteen.pvz.api.types.IPAZType;
import com.hungteen.pvz.common.advancement.trigger.InvasionMissionTrigger;
import com.hungteen.pvz.common.advancement.trigger.MoneyTrigger;
import com.hungteen.pvz.common.advancement.trigger.SunAmountTrigger;
import com.hungteen.pvz.common.advancement.trigger.TreeLevelTrigger;
import com.hungteen.pvz.common.event.handler.PlayerEventHandler;
import com.hungteen.pvz.common.item.spawn.card.SummonCardItem;
import com.hungteen.pvz.common.network.CardInventoryPacket;
import com.hungteen.pvz.common.network.PAZStatsPacket;
import com.hungteen.pvz.common.network.PVZPacketHandler;
import com.hungteen.pvz.common.network.toclient.PlayerStatsPacket;
import com.hungteen.pvz.common.world.invasion.Invasion;
import com.hungteen.pvz.common.world.invasion.MissionManager;
import com.hungteen.pvz.utils.PlayerUtil;
import com.hungteen.pvz.utils.StringUtil;
import com.hungteen.pvz.utils.enums.Resources;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.network.PacketDistributor;

import java.util.*;


public class PlayerDataManager {

	private final PlayerEntity player;
	/* player resources */
	private final HashMap<Resources, Integer> resources = new HashMap<>(Resources.values().length);
	/* summon card inventory */
	private final List<ItemStack> cards = new ArrayList<>();
	private int emptySlot;
	/* plant & zombie cd */
	private final Map<IPAZType, Integer> pazCardCD = new HashMap<>();
	private final Map<IPAZType, Float> pazCardBar = new HashMap<>();
	/* plant & zombie lock */
	private final Map<IPAZType, Boolean> pazLocked = new HashMap<>();
	/* misc data */
	private final Invasion invasion;
	public String lastVersion = StringUtil.INIT_VERSION;
	private final OtherStats otherStats;
	
	public PlayerDataManager(PlayerEntity player) {
		this.player = player;
		{// init player resources.
			for(Resources res : Resources.values()) {
				resources.put(res, Resources.getInitialValue(res));
			}
		}
		{// init card inventory.
			for(int i = 0; i <= Resources.SLOT_NUM.max; ++ i) {
				this.cards.add(ItemStack.EMPTY);
			}
		}
		{// init about plants & zombies.
			PVZAPI.get().getPAZs().forEach(plant -> {
				this.pazCardCD.put(plant, 0);
				this.pazCardBar.put(plant, 0F);
				this.pazLocked.put(plant, true);
			});
		}
		{// init misc data.
			this.invasion = new Invasion(player);
			this.otherStats = new OtherStats(this);
		}
	}
	
	/**
	 * read {@link PlayerDataStorage#readNBT(net.minecraftforge.common.capabilities.Capability, IPlayerDataCapability, net.minecraft.util.Direction, net.minecraft.nbt.INBT)}.
	 */
	public void loadFromNBT(CompoundNBT baseTag) {
		{// load player resources.
			if(baseTag.contains("player_stats")) {//old.
				CompoundNBT statsTag = baseTag.getCompound("player_stats");
			    for(Resources res : Resources.values()) {
			    	if(statsTag.contains("player_" + res.toString())) {
				        this.resources.put(res, statsTag.getInt("player_" + res.toString()));
			    	}
			    }
			}
			if(baseTag.contains("player_resources")) {//new.
				CompoundNBT statsTag = baseTag.getCompound("player_resources");
			    for(Resources res : Resources.values()) {
			    	if(statsTag.contains(res.toString().toLowerCase())) {
				        this.resources.put(res, statsTag.getInt(res.toString().toLowerCase()));
			    	}
			    }
			}
		}
		{// load card inventory.
			if(baseTag.contains("card_inventory")) {
				CompoundNBT nbt = baseTag.getCompound("card_inventory");
				for(int i = 0; i <= Resources.SLOT_NUM.max; ++ i) {
					if(nbt.contains("slot_" + i)) {
						this.cards.set(i, ItemStack.of(nbt.getCompound("slot_" + i)));
					}
				}
				if(nbt.contains("current_pos")) {
					this.emptySlot = nbt.getInt("current_pos");
				}
			}
		}
		{/* old load method */
			{// load plant & zombie card cd.
				if(baseTag.contains("paz_card_item_cd")) {
					final CompoundNBT nbt = baseTag.getCompound("paz_card_item_cd");
					PVZAPI.get().getPAZs().forEach(plant -> {
						final CompoundNBT plantNBT = (CompoundNBT) nbt.get(plant.getIdentity());
						if(plantNBT != null) {
							if(plantNBT.contains("paz_card_cd")) {
								this.pazCardCD.put(plant, plantNBT.getInt("paz_card_cd"));
							}
							if(plantNBT.contains("paz_card_bar")) {
								this.pazCardBar.put(plant, (float) plantNBT.getInt("paz_card_bar"));
							}
						}
					});
				}
			}
			{// load plant & zombie lock.
				if(baseTag.contains("paz_locks")) {
					final CompoundNBT nbt = baseTag.getCompound("paz_locks");
					PVZAPI.get().getPAZs().forEach(plant -> {
						final CompoundNBT plantNBT = (CompoundNBT) nbt.get(plant.getIdentity());
						if(plantNBT != null) {
							if(plantNBT.contains("paz_locked")) {
								this.pazLocked.put(plant, plantNBT.getBoolean("paz_locked"));
							}
						}
					});
				}
			}
		}
		{// load plant & zombie card cd.
			if(baseTag.contains("paz_data")) {
				final CompoundNBT nbt = baseTag.getCompound("paz_data");
				PVZAPI.get().getPAZs().forEach(plant -> {
					final CompoundNBT plantNBT = nbt.getCompound(plant.getIdentity());
					if(plantNBT.contains("paz_card_cd")) {
						this.pazCardCD.put(plant, plantNBT.getInt("paz_card_cd"));
					}
					if(plantNBT.contains("paz_card_bar")) {
						this.pazCardBar.put(plant, plantNBT.getFloat("paz_card_bar"));
					}
					if(plantNBT.contains("paz_locked")) {
						this.pazLocked.put(plant, plantNBT.getBoolean("paz_locked"));
					}
				});
			}
		}
		{// load misc data.
			if(baseTag.contains("invasion_data")){
				this.invasion.load(baseTag.getCompound("invasion_data"));
			}
			if(baseTag.contains("last_join_version")) {
				this.lastVersion = baseTag.getString("last_join_version");
			}
			this.otherStats.loadFromNBT(baseTag);
		}
	}
	
	/**
	 * write {@link PlayerDataStorage#writeNBT(net.minecraftforge.common.capabilities.Capability, IPlayerDataCapability, net.minecraft.util.Direction)}.
	 */
	public CompoundNBT saveToNBT() {
		CompoundNBT baseTag = new CompoundNBT();
		{// save player resources.
			CompoundNBT statsNBT = new CompoundNBT();
			for(Resources res : Resources.values()) {
				statsNBT.putInt(res.toString().toLowerCase(), resources.get(res));
			}
			baseTag.put("player_resources", statsNBT);
		}
		{// save card inventory.
			CompoundNBT nbt = new CompoundNBT();
			for(int i = 0; i < Resources.SLOT_NUM.max; ++ i) {
				nbt.put("slot_" + i, this.getItemAt(i).save(new CompoundNBT()));
			}
			nbt.putInt("current_pos", this.emptySlot);
			baseTag.put("card_inventory", nbt);
		}
		{// save plant & zombie card cd.
			final CompoundNBT nbt = new CompoundNBT();
			PVZAPI.get().getPAZs().forEach(plant -> {
				final CompoundNBT plantNBT = new CompoundNBT();
				plantNBT.putInt("paz_card_cd", this.pazCardCD.get(plant));
				plantNBT.putFloat("paz_card_bar", this.pazCardBar.get(plant));
				plantNBT.putBoolean("paz_locked", this.isPAZLocked(plant));
				nbt.put(plant.getIdentity(), plantNBT);
			});
			baseTag.put("paz_data", nbt);
		}
		{// load misc data.
			{
				final CompoundNBT nbt = new CompoundNBT();
				this.invasion.save(nbt);
				baseTag.put("invasion_data", nbt);
			}
			baseTag.putString("last_join_version", this.lastVersion);
			this.otherStats.saveToNBT(baseTag);
		}
		return baseTag;
	}

	/**
	 * copy player data when clone event happen.
	 */
	public void cloneFromExistingPlayerData(PlayerDataManager data, boolean died) {
		this.loadFromNBT(data.saveToNBT());
		
		/* reset some value when die */
		if(died) {
			if(! PVZConfig.COMMON_CONFIG.RuleSettings.KeepSunWhenDie.get()) {
				this.setResource(Resources.SUN_NUM, 50);
			}
		}
	}
	
	/**
	 * run when player join world.
	 * {@link PlayerEventHandler#onPlayerLogin(PlayerEntity)}
	 */
	public void init() {
		this.syncBounds();
		this.syncToClient();
	}
	
	/**
	 * avoid render out of bound.
	 * {@link #init()}
	 */
	private void syncBounds() {
		{// player resources.
		    for(Resources res : Resources.values()) {
			    this.addResource(res, 0);
		    }
		}
	}
	
	/**
	 * sync data to client side.
	 * {@link #init()}
	 */
	public void syncToClient() {
		{// player resources.
		    for(Resources res : Resources.values()) {
			    this.sendResourcePacket(player, res);
		    }
		}
		{// card inventory.
			for(int i = 0; i <= Resources.SLOT_NUM.max; ++ i) {
				this.sendInventoryPacket(player, i, this.getItemAt(i).save(new CompoundNBT()));
			}
			this.setCurrentPos(this.getCurrentPos(), true);
		}
		{// plants & zombies syncs.
			PVZAPI.get().getPAZs().forEach(plant -> {
				if(plant.getSummonCard().isPresent()) {//card cd.
					player.getCooldowns().addCooldown(plant.getSummonCard().get(), this.getPlantCardCD(plant));
				}
				this.sendPAZLockPacket(player, plant);//lock.
			});
		}
		{//wave.
			this.invasion.sendAllWavePacket(player);
		}
	}

	/*
	 * Operations of Player Resources.
	 */
	
	public int getResource(Resources res){
		return this.resources.get(res);
	}
	
	/**
	 * check (min, max) and sync send packet.
	 */
	public void setResource(Resources res, int num) {
		resources.put(res, num);
		if(res == Resources.TREE_LVL) {
			resources.put(Resources.TREE_XP, 0);
			this.addResource(Resources.TREE_XP, 0);
		}
		this.addResource(res, 0);
	}
	
	public void addResource(Resources res, int num) {
		int now = resources.get(res);
		final int old = now;
		if(res == Resources.TREE_XP) {
			addTreeXp(now, num);
		} else if(res == Resources.SUN_NUM) {
			now = MathHelper.clamp(now + num, 0, PlayerUtil.getPlayerMaxSunNum(resources.get(Resources.TREE_LVL)));
			resources.put(Resources.SUN_NUM, now);
			if(player instanceof ServerPlayerEntity){
				SunAmountTrigger.INSTANCE.trigger((ServerPlayerEntity) player, now);
			}
		} else if(res == Resources.ENERGY_NUM) {
			now = MathHelper.clamp(now + num, 0, resources.get(Resources.MAX_ENERGY_NUM));
			resources.put(Resources.ENERGY_NUM, now);
		} else if(res == Resources.LOTTERY_CHANCE) {
			now = MathHelper.clamp(now + num, res.min, res.max);
			resources.put(res, now);
			if (num > 0) PlayerUtil.sendMsgTo(player, new TranslationTextComponent("invasion.pvz.mission.finish6", num));
		} else{
			now = MathHelper.clamp(now + num, res.min, res.max);
			resources.put(res, now);

			if(res == Resources.TREE_LVL) {
				this.addResource(Resources.SUN_NUM, 0);
				if(player instanceof ServerPlayerEntity){
					TreeLevelTrigger.INSTANCE.trigger((ServerPlayerEntity) player, now);
					if(old != now){
						MinecraftForge.EVENT_BUS.post(new PlayerLevelChangeEvent(player, old, now));
					}
				}
			} else if(res == Resources.MONEY) {
				if(player instanceof ServerPlayerEntity){
					MoneyTrigger.INSTANCE.trigger((ServerPlayerEntity) player, now);
				}
			} else if(res == Resources.MISSION_VALUE){
				if(num > 0 && this.getResource(Resources.MISSION_TYPE) == MissionManager.MissionType.INSTANT_KILL.ordinal()){
					++ this.invasion.killInSecond;
				}
			} else if(res == Resources.MISSION_FINISH_TIME){
				if(player instanceof ServerPlayerEntity){
					InvasionMissionTrigger.INSTANCE.trigger((ServerPlayerEntity) player, now);
				}
			}
		}
		
		this.sendResourcePacket(player, res);
	}
	
	/**
	 * add tree xp and maxLevel up.
	 */
	private void addTreeXp(int now, int num) {
		int lvl = resources.get(Resources.TREE_LVL);
		if(num > 0) {
			int req = PlayerUtil.getPlayerLevelUpXp(lvl);
			while(lvl < Resources.TREE_LVL.max && num + now >= req) {
				num -= req - now;
				this.addResource(Resources.TREE_LVL, 1);
				now = 0;
				req = PlayerUtil.getPlayerLevelUpXp(lvl);
			}
			resources.put(Resources.TREE_XP, num + now);
		} else {
			num = - num;
			while(lvl > 1 && num > now) {
				num -= now;
				-- lvl;
				now = PlayerUtil.getPlayerLevelUpXp(lvl);
				this.addResource(Resources.TREE_LVL, - 1);
			}
			resources.put(Resources.TREE_XP, Math.max(now - num, 0));
		}
	}
	
	private void sendResourcePacket(PlayerEntity player, Resources res){
		if (player instanceof ServerPlayerEntity) {
			PVZPacketHandler.CHANNEL.send(
				PacketDistributor.PLAYER.with(() -> {
					return (ServerPlayerEntity) player;
				}),
				new PlayerStatsPacket(res.ordinal(), resources.get(res))
			);
		}
	}
	
	/*
	 * Operation about SummonCard Inventory.
	 */
	
	public void onScrollInventory(double delta) {
		final int maxSlot = this.getResource(Resources.SLOT_NUM);
		int now = this.emptySlot;
		int next = (now + (delta > 0 ? -1 : 1) + (maxSlot + 1)) % (maxSlot + 1);
		while(this.getItemAt(next).isEmpty()){
			next = (next + (delta > 0 ? -1 : 1) + (maxSlot + 1)) % (maxSlot + 1);
		}
		player.setItemInHand(Hand.MAIN_HAND, this.getItemAt(next));
		this.setCurrentPos(next, true);
	}
	
	public void onSwitchCard() {
		if(player.getMainHandItem().getItem() instanceof SummonCardItem) {
		    this.setItemAt(player.getMainHandItem(), this.emptySlot, true);
		}
	}
	
	public ItemStack getItemAt(int pos) {
		return this.cards.get(pos);
	}
	
	public void setItemAt(ItemStack stack, int pos, boolean sync) {
		pos = MathHelper.clamp(pos, 0, this.cards.size() - 1);
		this.cards.set(pos, stack);
		if(sync) {
			this.sendInventoryPacket(player, pos, this.cards.get(pos).save(new CompoundNBT()));
		}
	}
	
	public void setCurrentPos(int pos, boolean sync) {
		this.emptySlot = pos;
		CompoundNBT nbt = new CompoundNBT();
		nbt.putInt(CardInventoryPacket.FLAG, this.emptySlot);
		if(sync) {
			this.sendInventoryPacket(player, -1, nbt);
		}
	}
	
	public int getCardsSize() {
		return this.cards.size();
	}
	
	public int getCurrentPos() {
		return this.emptySlot;
	}
	
	private void sendInventoryPacket(PlayerEntity player, int type, CompoundNBT nbt) {
		if (player instanceof ServerPlayerEntity) {
			PVZPacketHandler.CHANNEL.send(
				PacketDistributor.PLAYER.with(() -> {
					return (ServerPlayerEntity) player;
				}),
				new CardInventoryPacket(type, nbt)
			);
		} else{
			PVZPacketHandler.CHANNEL.sendToServer(new CardInventoryPacket(type, nbt));
		}
	}

	/*
	 * Operation about plant card CD.
	 * just store data, no need to manually sync to client.
	 */
	
	public void setPAZCardCD(IPAZType plant, int tick) {
		this.pazCardCD.put(plant, tick);
	}
	
	public int getPAZCardCoolDown(IPAZType plant) {
		return this.pazCardCD.get(plant);
	}
	
	public void setPAZCardBar(IPAZType plant, float bar) {
		this.pazCardBar.put(plant, bar);
	}
	
	public float getPAZCardBarLength(IPAZType plant) {
		return this.pazCardBar.get(plant);
	}
	
	public int getPlantCardCD(IPAZType plant) {
		return (int) (this.pazCardBar.get(plant) * this.pazCardCD.get(plant));
	}

	public void saveSummonCardCD(SummonCardItem item, int cd) {
		this.setPAZCardCD(item.type, cd);
		this.setPAZCardBar(item.type, 1);
	}

	public void loadSummonCardCDs() {
		PVZAPI.get().getPAZs().forEach(paz -> {
			paz.getSummonCard().ifPresent(item -> {
				this.player.getCooldowns().addCooldown(item, this.getPlantCardCD(paz));
			});
		});
	}

	/*
	 * Operation about plant lock.
	 */
	
	public void setPAZLocked(IPAZType plant, boolean is) {
		final boolean old = this.isPAZLocked(plant);
		this.pazLocked.put(plant, is);
		if(old != is) {
			this.sendPAZLockPacket(player, plant);
		}
	}
	
	public boolean isPAZLocked(IPAZType plant) {
		return this.pazLocked.getOrDefault(plant, true);
	}

	private void sendPAZLockPacket(PlayerEntity player, IPAZType plant){
		if (player instanceof ServerPlayerEntity) {
			PVZPacketHandler.CHANNEL.send(
					PacketDistributor.PLAYER.with(() -> {
						return (ServerPlayerEntity) player;
					}),
					new PAZStatsPacket(PAZStatsPacket.PAZPacketTypes.UNLOCK, plant.getIdentity(), this.isPAZLocked(plant))
			);
		}
	}

	public final class OtherStats{
		
		@SuppressWarnings("unused")
		private final PlayerDataManager manager;
		public int playSoundTick;
		
		public OtherStats(PlayerDataManager manager) {
			this.manager = manager;
			this.playSoundTick = 0;
		}
		
		private void saveToNBT(CompoundNBT baseTag) {
			baseTag.putInt("base_sound_tick", this.playSoundTick);
		}

		private void loadFromNBT(CompoundNBT baseTag) {
			if(baseTag.contains("base_sound_tick")) {
				this.playSoundTick = baseTag.getInt("base_sound_tick");
			}
		}
	}

	public Invasion getInvasion(){
		return this.invasion;
	}

	public OtherStats getOtherStats() {
		return this.otherStats;
	}

	protected Optional<ServerPlayerEntity> getServerPlayerOpt(){
		if(this.player instanceof ServerPlayerEntity){
			return Optional.ofNullable((ServerPlayerEntity) this.player);
		}
		return Optional.empty();
	}
	
}
