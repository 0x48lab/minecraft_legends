package com.hacklab.minecraft_legends.presentation.listener

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Vector
import java.util.*

class ZiplineListener(private val plugin: JavaPlugin) : Listener {
    
    // アクティブなジップライン
    private val activeZiplines = mutableMapOf<UUID, ZiplineData>()
    
    // ジップライン使用中のプレイヤー
    private val playersOnZipline = mutableMapOf<UUID, ZiplineRide>()
    
    // 設定
    private val maxZiplineDistance = 64.0
    private val ziplineSpeed = 1.5
    private val ziplineHeight = 1.5 // プレイヤーがロープの下にぶら下がる高さ
    
    @EventHandler
    fun onPlayerUseZipline(event: PlayerInteractEvent) {
        val player = event.player
        val item = event.item ?: return
        
        // アイテムがジップラインアイテムか確認
        if (!isZiplineItem(item)) return
        
        // 右クリックのみ反応
        if (!event.action.toString().contains("RIGHT")) return
        
        // クールダウンチェック（別途実装）
        
        // プレイヤーの位置から向いている方向のブロックを検出
        val startLocation = player.location.add(0.0, 1.0, 0.0)
        val endBlock = findTargetBlock(player, maxZiplineDistance) ?: run {
            player.sendMessage("§c目標となるブロックが見つかりません！")
            return
        }
        
        val endLocation = endBlock.location.add(0.5, 0.0, 0.5)
        val distance = startLocation.distance(endLocation)
        
        if (distance < 5.0) {
            player.sendMessage("§c距離が近すぎます！")
            return
        }
        
        // ジップラインを作成
        createZipline(player, startLocation, endLocation)
        
        event.isCancelled = true
    }
    
    private fun findTargetBlock(player: Player, maxDistance: Double): org.bukkit.block.Block? {
        val start = player.eyeLocation
        val direction = start.direction.normalize()
        
        // レイキャストでブロックを検出
        for (i in 1..maxDistance.toInt()) {
            val checkLocation = start.clone().add(direction.clone().multiply(i.toDouble()))
            val block = checkLocation.block
            
            // 空気以外のブロックを検出
            if (block.type != Material.AIR && block.type.isSolid) {
                return block
            }
        }
        
        return null
    }
    
    private fun createZipline(player: Player, start: Location, end: Location) {
        // 既存のジップラインを削除
        removeZipline(player.uniqueId)
        
        // アンカーポイント（見えないアーマースタンド）を作成
        val startAnchor = createAnchor(start)
        val endAnchor = createAnchor(end)
        
        val zipline = ZiplineData(
            ownerId = player.uniqueId,
            startLocation = start,
            endLocation = end,
            startAnchor = startAnchor,
            endAnchor = endAnchor
        )
        
        activeZiplines[player.uniqueId] = zipline
        
        // ロープの視覚効果を開始
        startRopeEffect(zipline)
        
        player.sendMessage("§aジップラインを設置しました！ジャンプして掴まってください。")
        player.playSound(player.location, Sound.BLOCK_CHAIN_PLACE, 1.0f, 1.0f)
        
        // 一定時間後に自動削除
        object : BukkitRunnable() {
            override fun run() {
                removeZipline(player.uniqueId)
            }
        }.runTaskLater(plugin, 600L) // 30秒後
    }
    
    private fun createAnchor(location: Location): ArmorStand {
        val world = location.world!!
        val anchor = world.spawnEntity(location, EntityType.ARMOR_STAND) as ArmorStand
        
        anchor.isVisible = false
        anchor.isMarker = true
        anchor.setGravity(false)
        anchor.isInvulnerable = true
        anchor.isCustomNameVisible = false
        
        return anchor
    }
    
    private fun startRopeEffect(zipline: ZiplineData) {
        object : BukkitRunnable() {
            override fun run() {
                if (!activeZiplines.containsKey(zipline.ownerId)) {
                    cancel()
                    return
                }
                
                // ロープのパーティクル効果
                drawRope(zipline.startLocation, zipline.endLocation)
            }
        }.runTaskTimer(plugin, 0L, 5L)
    }
    
    private fun drawRope(start: Location, end: Location) {
        val distance = start.distance(end)
        val direction = end.toVector().subtract(start.toVector()).normalize()
        
        // ロープに沿ってパーティクルを生成
        var currentDistance = 0.0
        while (currentDistance <= distance) {
            val particleLocation = start.clone().add(direction.clone().multiply(currentDistance))
            
            // 茶色のダストパーティクルでロープを表現
            particleLocation.world!!.spawnParticle(
                Particle.DUST,
                particleLocation,
                1,
                0.0, 0.0, 0.0,
                0.0,
                Particle.DustOptions(org.bukkit.Color.fromRGB(101, 67, 33), 1.5f)
            )
            
            currentDistance += 0.5
        }
    }
    
    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        val player = event.player
        val playerId = player.uniqueId
        
        // すでにジップラインに乗っている場合はスキップ
        if (playersOnZipline.containsKey(playerId)) return
        
        // プレイヤーのジップラインを確認
        val zipline = activeZiplines[playerId] ?: return
        
        // ジャンプして近くにいるか確認
        if (!player.isOnGround && player.velocity.y > 0) {
            val playerLoc = player.location
            
            // ロープまでの最短距離を計算
            val distanceToRope = getDistanceToLine(playerLoc, zipline.startLocation, zipline.endLocation)
            
            if (distanceToRope < 2.0) {
                // ジップラインに乗る
                startZiplineRide(player, zipline)
            }
        }
    }
    
    private fun getDistanceToLine(point: Location, lineStart: Location, lineEnd: Location): Double {
        val line = lineEnd.toVector().subtract(lineStart.toVector())
        val toPoint = point.toVector().subtract(lineStart.toVector())
        
        val lineLength = line.length()
        if (lineLength == 0.0) return point.distance(lineStart)
        
        val dotProduct = toPoint.dot(line) / (lineLength * lineLength)
        val closestPoint = if (dotProduct < 0) {
            lineStart.toVector()
        } else if (dotProduct > 1) {
            lineEnd.toVector()
        } else {
            lineStart.toVector().add(line.multiply(dotProduct))
        }
        
        return point.toVector().distance(closestPoint)
    }
    
    private fun startZiplineRide(player: Player, zipline: ZiplineData) {
        player.sendMessage("§bジップライン移動開始！")
        player.playSound(player.location, Sound.ITEM_ARMOR_EQUIP_ELYTRA, 1.0f, 1.0f)
        
        val ride = ZiplineRide(
            playerId = player.uniqueId,
            zipline = zipline,
            progress = 0.0
        )
        
        playersOnZipline[player.uniqueId] = ride
        
        // 移動処理を開始
        object : BukkitRunnable() {
            override fun run() {
                val currentRide = playersOnZipline[player.uniqueId]
                if (currentRide == null) {
                    cancel()
                    return
                }
                
                // 進行度を更新
                currentRide.progress += ziplineSpeed / 20.0 / zipline.startLocation.distance(zipline.endLocation)
                
                if (currentRide.progress >= 1.0) {
                    // 終点に到達
                    endZiplineRide(player)
                    cancel()
                    return
                }
                
                // プレイヤーの位置を更新
                val currentPosition = zipline.startLocation.toVector()
                    .add(zipline.endLocation.toVector()
                    .subtract(zipline.startLocation.toVector())
                    .multiply(currentRide.progress))
                
                val newLocation = currentPosition.toLocation(player.world)
                newLocation.yaw = player.location.yaw
                newLocation.pitch = player.location.pitch
                newLocation.y -= ziplineHeight // ロープの下にぶら下がる
                
                player.teleport(newLocation)
                player.velocity = Vector(0, 0, 0) // 重力を無効化
                
                // エフェクト
                player.world.spawnParticle(
                    Particle.FIREWORK,
                    player.location.add(0.0, 1.5, 0.0),
                    3,
                    0.2, 0.2, 0.2,
                    0.05
                )
            }
        }.runTaskTimer(plugin, 0L, 1L)
    }
    
    private fun endZiplineRide(player: Player) {
        playersOnZipline.remove(player.uniqueId)
        player.sendMessage("§aジップライン移動完了！")
        player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 2.0f)
        
        // 着地の勢いを追加
        player.velocity = player.location.direction.multiply(0.5).setY(0.2)
    }
    
    private fun removeZipline(playerId: UUID) {
        val zipline = activeZiplines.remove(playerId) ?: return
        
        // アンカーを削除
        zipline.startAnchor.remove()
        zipline.endAnchor.remove()
        
        // 使用中のプレイヤーがいれば降ろす
        val player = plugin.server.getPlayer(playerId)
        if (player != null && playersOnZipline.containsKey(playerId)) {
            endZiplineRide(player)
        }
    }
    
    private fun isZiplineItem(item: org.bukkit.inventory.ItemStack): Boolean {
        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.has(
            org.bukkit.NamespacedKey(plugin, "zipline_item"),
            org.bukkit.persistence.PersistentDataType.STRING
        )
    }
    
    fun cleanup() {
        // すべてのジップラインを削除
        activeZiplines.keys.toList().forEach { removeZipline(it) }
    }
}

data class ZiplineData(
    val ownerId: UUID,
    val startLocation: Location,
    val endLocation: Location,
    val startAnchor: ArmorStand,
    val endAnchor: ArmorStand
)

data class ZiplineRide(
    val playerId: UUID,
    val zipline: ZiplineData,
    var progress: Double
)