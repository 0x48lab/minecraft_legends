package com.hacklab.minecraft_legends.domain.entity

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import java.time.LocalDateTime
import java.util.*

data class SupplyBox(
    val id: UUID = UUID.randomUUID(),
    val gameId: UUID,
    val location: BoxLocation,
    val tier: SupplyBoxTier,
    val status: SupplyBoxStatus = SupplyBoxStatus.AVAILABLE,
    val contents: List<LootItem>,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val openedAt: LocalDateTime? = null,
    val openedBy: UUID? = null // Player UUID
) {
    fun open(playerId: UUID): SupplyBox {
        return copy(
            status = SupplyBoxStatus.OPENED,
            openedAt = LocalDateTime.now(),
            openedBy = playerId
        )
    }
    
    fun isEmpty(): Boolean = contents.isEmpty()
    
    fun getRarityDistribution(): Map<ItemRarity, Int> {
        return contents.groupBy { it.rarity }
            .mapValues { it.value.size }
    }
}

data class BoxLocation(
    val x: Double,
    val y: Double,
    val z: Double,
    val worldName: String
) {
    fun toLocation(): Location? {
        val world = org.bukkit.Bukkit.getWorld(worldName) ?: return null
        return Location(world, x, y, z)
    }
}

enum class SupplyBoxTier(val displayName: String, val color: String) {
    BASIC("Basic Supply", "§7"),
    ADVANCED("Advanced Supply", "§b"),
    ELITE("Elite Supply", "§5"),
    LEGENDARY("Legendary Supply", "§6")
}

enum class SupplyBoxStatus {
    AVAILABLE,
    OPENED,
    DESTROYED
}

data class LootItem(
    val material: Material,
    val amount: Int = 1,
    val rarity: ItemRarity,
    val enchantments: List<LootEnchantment> = emptyList(),
    val customName: String? = null,
    val lore: List<String> = emptyList(),
    val potionType: String? = null
) {
    fun toItemStack(): org.bukkit.inventory.ItemStack {
        val itemStack = org.bukkit.inventory.ItemStack(material, amount)
        val meta = itemStack.itemMeta
        
        if (meta != null) {
            // カスタム名を設定
            customName?.let { meta.setDisplayName(it) }
            
            // 説明文を設定
            if (lore.isNotEmpty()) {
                meta.lore = lore
            }
            
            // エンチャントを適用
            enchantments.forEach { lootEnchant ->
                val enchantment = Enchantment.getByName(lootEnchant.type)
                if (enchantment != null) {
                    meta.addEnchant(enchantment, lootEnchant.level, true)
                }
            }
            
            itemStack.itemMeta = meta
        }
        
        // ポーション系アイテムの処理
        if (material.name.contains("POTION") && potionType != null) {
            // TODO: ポーションタイプの設定
        }
        
        return itemStack
    }
}

data class LootEnchantment(
    val type: String, // Enchantment name
    val level: Int,
    val chance: Double = 100.0 // 付与される確率（%）
)

enum class ItemRarity(val displayName: String, val color: String, val weight: Int) {
    COMMON("Common", "§f", 70),
    RARE("Rare", "§b", 25),
    EPIC("Epic", "§5", 5),
    LEGENDARY("Legendary", "§6", 1)
}

data class LootTable(
    val name: String,
    val items: Map<ItemRarity, List<LootTableEntry>>
) {
    fun generateLoot(itemCount: Int): List<LootItem> {
        val generatedItems = mutableListOf<LootItem>()
        
        repeat(itemCount) {
            val rarity = selectRarity()
            val rarityItems = items[rarity] ?: emptyList()
            
            if (rarityItems.isNotEmpty()) {
                val entry = rarityItems.random()
                if (kotlin.random.Random.nextDouble(0.0, 100.0) <= entry.chance) {
                    val amount = if (entry.amount.contains("-")) {
                        val parts = entry.amount.split("-")
                        kotlin.random.Random.nextInt(parts[0].toInt(), parts[1].toInt() + 1)
                    } else {
                        entry.amount.toInt()
                    }
                    
                    val enchantments = entry.enchantments.filter { 
                        kotlin.random.Random.nextDouble(0.0, 100.0) <= it.chance 
                    }
                    
                    generatedItems.add(
                        LootItem(
                            material = entry.material,
                            amount = amount,
                            rarity = rarity,
                            enchantments = enchantments,
                            potionType = entry.potionType
                        )
                    )
                }
            }
        }
        
        return generatedItems
    }
    
    private fun selectRarity(): ItemRarity {
        val totalWeight = ItemRarity.values().sumOf { it.weight }
        val randomValue = kotlin.random.Random.nextInt(0, totalWeight)
        var currentWeight = 0
        
        for (rarity in ItemRarity.values()) {
            currentWeight += rarity.weight
            if (randomValue < currentWeight) {
                return rarity
            }
        }
        
        return ItemRarity.COMMON
    }
}

data class LootTableEntry(
    val material: Material,
    val chance: Double,
    val amount: String, // "1" or "1-3" for range
    val enchantments: List<LootEnchantment> = emptyList(),
    val potionType: String? = null
)

data class SupplyBoxConfiguration(
    val spawnCount: Int = 20, // マップあたりのサプライボックス数
    val tierDistribution: Map<SupplyBoxTier, Double> = mapOf(
        SupplyBoxTier.BASIC to 50.0,
        SupplyBoxTier.ADVANCED to 30.0,
        SupplyBoxTier.ELITE to 15.0,
        SupplyBoxTier.LEGENDARY to 5.0
    ),
    val itemsPerBox: Map<SupplyBoxTier, IntRange> = mapOf(
        SupplyBoxTier.BASIC to 3..5,
        SupplyBoxTier.ADVANCED to 4..6,
        SupplyBoxTier.ELITE to 5..7,
        SupplyBoxTier.LEGENDARY to 6..8
    ),
    val respawnEnabled: Boolean = false,
    val respawnInterval: Long = 300 // seconds
)