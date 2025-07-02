package com.hacklab.minecraft_legends.domain.usecase

import com.hacklab.minecraft_legends.domain.entity.LegendSelection
import com.hacklab.minecraft_legends.domain.repository.GameRepository
import com.hacklab.minecraft_legends.domain.repository.LegendRepository
import com.hacklab.minecraft_legends.domain.repository.TeamRepository
import java.util.*

interface SelectLegendUseCase {
    suspend fun execute(request: SelectLegendRequest): Result<LegendSelection>
}

data class SelectLegendRequest(
    val playerId: UUID,
    val gameId: UUID,
    val legendId: String
)

class SelectLegendUseCaseImpl(
    private val legendRepository: LegendRepository,
    private val gameRepository: GameRepository,
    private val teamRepository: TeamRepository
) : SelectLegendUseCase {
    
    override suspend fun execute(request: SelectLegendRequest): Result<LegendSelection> {
        return try {
            // ゲームの存在確認
            val game = gameRepository.findById(request.gameId)
                ?: return Result.failure(Exception("Game not found"))
            
            // プレイヤーのチーム取得
            val team = game.getTeamByPlayerId(request.playerId)
                ?: return Result.failure(Exception("Player not in any team"))
            
            // レジェンドの存在・有効性確認
            val legend = legendRepository.findLegendById(request.legendId)
                ?: return Result.failure(Exception("Legend not found"))
            
            if (!legend.isEnabled) {
                return Result.failure(Exception("Legend is disabled"))
            }
            
            // チーム内でのレジェンド重複チェック
            if (!legendRepository.isLegendAvailableForTeam(request.gameId, team.id, request.legendId)) {
                return Result.failure(Exception("Legend already selected by teammate"))
            }
            
            // 既存の選択を取得または作成
            val existingSelection = legendRepository.findLegendSelection(request.gameId, request.playerId)
            val selection = existingSelection?.copy(
                selectedLegend = request.legendId,
                isLocked = false
            ) ?: LegendSelection(
                gameId = request.gameId,
                teamId = team.id,
                playerId = request.playerId,
                selectedLegend = request.legendId
            )
            
            // 選択を保存
            legendRepository.saveLegendSelection(selection)
            
            Result.success(selection)
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}