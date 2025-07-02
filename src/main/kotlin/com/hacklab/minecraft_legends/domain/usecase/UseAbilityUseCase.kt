package com.hacklab.minecraft_legends.domain.usecase

import com.hacklab.minecraft_legends.domain.entity.AbilityCooldown
import com.hacklab.minecraft_legends.domain.entity.AbilityType
import com.hacklab.minecraft_legends.domain.repository.GameRepository
import com.hacklab.minecraft_legends.domain.repository.LegendRepository
import org.bukkit.entity.Player
import java.time.LocalDateTime
import java.util.*

interface UseAbilityUseCase {
    suspend fun execute(request: UseAbilityRequest): Result<UseAbilityResponse>
}

data class UseAbilityRequest(
    val player: Player,
    val gameId: UUID,
    val abilityType: AbilityType
)

data class UseAbilityResponse(
    val success: Boolean,
    val message: String,
    val cooldownSeconds: Long = 0
)

class UseAbilityUseCaseImpl(
    private val legendRepository: LegendRepository,
    private val gameRepository: GameRepository
) : UseAbilityUseCase {
    
    override suspend fun execute(request: UseAbilityRequest): Result<UseAbilityResponse> {
        return try {
            val playerId = request.player.uniqueId
            
            // ゲームの存在確認
            val game = gameRepository.findById(request.gameId)
                ?: return Result.success(UseAbilityResponse(
                    success = false,
                    message = "Game not found"
                ))
            
            // プレイヤーのレジェンド選択を取得
            val selection = legendRepository.findLegendSelection(request.gameId, playerId)
                ?: return Result.success(UseAbilityResponse(
                    success = false,
                    message = "No legend selected"
                ))
            
            val legendId = selection.selectedLegend
                ?: return Result.success(UseAbilityResponse(
                    success = false,
                    message = "No legend selected"
                ))
            
            // レジェンドを取得
            val legend = legendRepository.findLegendById(legendId)
                ?: return Result.success(UseAbilityResponse(
                    success = false,
                    message = "Legend not found"
                ))
            
            // クールダウンチェック
            val cooldown = legendRepository.findAbilityCooldown(playerId, legendId, request.abilityType)
            if (cooldown != null && !cooldown.isExpired) {
                return Result.success(UseAbilityResponse(
                    success = false,
                    message = "Ability is on cooldown",
                    cooldownSeconds = cooldown.remainingSeconds
                ))
            }
            
            // アビリティ使用
            val abilityUsed = legend.useAbility(request.player, request.abilityType)
            
            if (abilityUsed) {
                // クールダウンを設定
                val cooldownDuration = legend.getAbilityCooldown(request.abilityType)
                if (cooldownDuration > 0) {
                    val newCooldown = AbilityCooldown(
                        playerId = playerId,
                        legendId = legendId,
                        abilityType = request.abilityType,
                        cooldownEndTime = LocalDateTime.now().plusSeconds(cooldownDuration)
                    )
                    legendRepository.saveAbilityCooldown(newCooldown)
                }
                
                Result.success(UseAbilityResponse(
                    success = true,
                    message = "Ability used successfully",
                    cooldownSeconds = cooldownDuration
                ))
            } else {
                Result.success(UseAbilityResponse(
                    success = false,
                    message = "Failed to use ability"
                ))
            }
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}