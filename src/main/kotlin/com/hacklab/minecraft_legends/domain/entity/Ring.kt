package com.hacklab.minecraft_legends.domain.entity

import java.time.LocalDateTime
import java.util.*

data class Ring(
    val gameId: UUID,
    val currentPhase: Int = 0,
    val currentCenter: RingCenter,
    val currentSize: Double,
    val nextCenter: RingCenter? = null,
    val nextSize: Double? = null,
    val isMoving: Boolean = false,
    val phaseStartTime: LocalDateTime? = null,
    val phaseEndTime: LocalDateTime? = null,
    val damage: Double = 0.0,
    val warningTime: Long = 0, // seconds before ring starts moving
    val shrinkDuration: Long = 0 // seconds to complete shrinking
) {
    fun startPhase(
        phase: Int,
        newCenter: RingCenter,
        newSize: Double,
        damage: Double,
        warningTime: Long,
        shrinkDuration: Long
    ): Ring {
        val now = LocalDateTime.now()
        return copy(
            currentPhase = phase,
            nextCenter = newCenter,
            nextSize = newSize,
            damage = damage,
            warningTime = warningTime,
            shrinkDuration = shrinkDuration,
            phaseStartTime = now,
            phaseEndTime = now.plusSeconds(warningTime + shrinkDuration),
            isMoving = false
        )
    }
    
    fun startMoving(): Ring {
        return copy(isMoving = true)
    }
    
    fun completeMovement(): Ring {
        return copy(
            currentCenter = nextCenter ?: currentCenter,
            currentSize = nextSize ?: currentSize,
            nextCenter = null,
            nextSize = null,
            isMoving = false
        )
    }
    
    fun isPlayerInside(x: Double, z: Double): Boolean {
        val distance = kotlin.math.sqrt(
            (x - currentCenter.x) * (x - currentCenter.x) +
            (z - currentCenter.z) * (z - currentCenter.z)
        )
        return distance <= currentSize / 2
    }
    
    fun getDistanceFromEdge(x: Double, z: Double): Double {
        val distanceFromCenter = kotlin.math.sqrt(
            (x - currentCenter.x) * (x - currentCenter.x) +
            (z - currentCenter.z) * (z - currentCenter.z)
        )
        return (currentSize / 2) - distanceFromCenter
    }
    
    fun getCurrentInterpolatedPosition(progress: Double): RingPosition? {
        if (!isMoving || nextCenter == null || nextSize == null) return null
        
        val interpolatedX = currentCenter.x + (nextCenter.x - currentCenter.x) * progress
        val interpolatedZ = currentCenter.z + (nextCenter.z - currentCenter.z) * progress
        val interpolatedSize = currentSize + (nextSize - currentSize) * progress
        
        return RingPosition(
            center = RingCenter(interpolatedX, interpolatedZ),
            size = interpolatedSize
        )
    }
}

data class RingCenter(
    val x: Double,
    val z: Double
)

data class RingPosition(
    val center: RingCenter,
    val size: Double
)

data class RingPhaseConfig(
    val phase: Int,
    val waitTime: Long, // seconds to wait before shrinking
    val shrinkTime: Long, // seconds to complete shrinking
    val damage: Double, // damage per second outside ring
    val finalSize: Double? = null // null means calculate based on current size
)

data class RingConfiguration(
    val phases: List<RingPhaseConfig>,
    val updateInterval: Long = 3, // seconds between updates
    val initialSize: Double = 2000.0, // initial ring size
    val finalSize: Double = 0.0, // final ring size (0 = complete shrink)
    val damageGracePeriod: Long = 5 // seconds before damage starts
) {
    fun getPhaseConfig(phase: Int): RingPhaseConfig? {
        return phases.find { it.phase == phase }
    }
    
    fun getTotalGameTime(): Long {
        return phases.sumOf { it.waitTime + it.shrinkTime }
    }
    
    fun getMaxPhase(): Int {
        return phases.maxOfOrNull { it.phase } ?: 0
    }
}