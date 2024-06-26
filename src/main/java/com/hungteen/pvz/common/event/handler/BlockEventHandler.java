package com.hungteen.pvz.common.event.handler;

import com.hungteen.pvz.PVZConfig;
import com.hungteen.pvz.common.block.BlockRegister;
import com.hungteen.pvz.common.item.ItemRegister;
import com.hungteen.pvz.utils.EntityUtil;
import com.hungteen.pvz.utils.PlayerUtil;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.monster.EndermanEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.event.world.BlockEvent;

public class BlockEventHandler {

	/**
	 * trigger endermans around when dig amethyst ore.
	 */
	public static void triggerAmethystAround(BlockEvent.BreakEvent ev){
		if(! ev.getWorld().isClientSide() && ev.getState().getBlock().equals(BlockRegister.AMETHYST_ORE.get()) && PlayerUtil.isValidPlayer(ev.getPlayer())){
			final float range = 32;
			final AxisAlignedBB aabb = EntityUtil.getEntityAABB(ev.getPlayer(), range, range);
			EntityUtil.getPredicateEntities(ev.getPlayer(), aabb, EndermanEntity.class, (e) -> true).forEach(enderman ->{
				if(ev.getWorld().getRandom().nextFloat() < 1.0){
					enderman.setTarget(ev.getPlayer());
				}
			});
		}
	}

	public static void checkAndDropSeeds(BlockEvent.BreakEvent ev) {
		PlayerEntity player = ev.getPlayer();
		BlockState state = ev.getState();
		BlockPos pos = ev.getPos();
		if(! player.level.isClientSide) {
			if(state.getBlock() == Blocks.GRASS || state.getBlock() == Blocks.TALL_GRASS) {//break grass
				if(player.getRandom().nextDouble() < PVZConfig.COMMON_CONFIG.BlockSettings.PeaDropChance.get()) {
					player.level.addFreshEntity(new ItemEntity(player.level,pos.getX(), pos.getY(), pos.getZ(), new ItemStack(ItemRegister.PEA.get(), 1)));
				} else if(player.getRandom().nextDouble() < PVZConfig.COMMON_CONFIG.BlockSettings.CabbageDropChance.get()) {
					player.level.addFreshEntity(new ItemEntity(player.level,pos.getX(), pos.getY(), pos.getZ(), new ItemStack(ItemRegister.CABBAGE_SEEDS.get(), 1)));
				}
			}
		}
	}
}
