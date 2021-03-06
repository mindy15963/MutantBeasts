package chumbanotz.mutantbeasts.item;

import java.util.UUID;

import com.google.common.collect.Multimap;

import net.minecraft.block.Block;
import net.minecraft.block.DispenserBlock;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.IArmorMaterial;
import net.minecraft.item.ItemStack;
import net.minecraft.item.WallOrFloorItem;

public class ArmorBlockItem extends WallOrFloorItem {
	private static final UUID ARMOR_MODIFIER = UUID.fromString("2AD3F246-FEE1-4E67-B886-69FD380BB150");
	private final IArmorMaterial material;

	public ArmorBlockItem(IArmorMaterial material, Block floorBlock, Block wallBlockIn, Properties propertiesIn) {
		super(floorBlock, wallBlockIn, propertiesIn.defaultMaxDamage(material.getDurability(EquipmentSlotType.HEAD)));
		this.material = material;
		DispenserBlock.registerDispenseBehavior(this, ArmorItem.DISPENSER_BEHAVIOR);
	}

	@Override
	public int getItemEnchantability(ItemStack stack) {
		return this.material.getEnchantability();
	}

	@Override
	public Multimap<String, AttributeModifier> getAttributeModifiers(EquipmentSlotType slot, ItemStack stack) {
		Multimap<String, AttributeModifier> multimap = super.getAttributeModifiers(slot, stack);
		if (slot == EquipmentSlotType.HEAD) {
			multimap.put(SharedMonsterAttributes.ARMOR.getName(), new AttributeModifier(ARMOR_MODIFIER, "Armor modifier", (double)this.material.getDamageReductionAmount(slot), AttributeModifier.Operation.ADDITION));
			multimap.put(SharedMonsterAttributes.ARMOR_TOUGHNESS.getName(), new AttributeModifier(ARMOR_MODIFIER, "Armor toughness", (double)this.material.getToughness(), AttributeModifier.Operation.ADDITION));
		}

		return multimap;
	}
}