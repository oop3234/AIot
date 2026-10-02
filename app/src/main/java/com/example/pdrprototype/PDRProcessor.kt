package com.example.pdrprototype

import kotlin.math.cos
import kotlin.math.sin

class PDRProcessor {
    var x = 0.0
        private set
    var y = 0.0
        private set
    var totalDistance = 0.0
        private set
    var stepCount = 0
        private set
    var heading = 0.0
        private set

    private val defaultStepLength = 0.70

    fun onStep() {
        stepCount++
        val stepLength = defaultStepLength
        val rad = Math.toRadians(heading)
        val dx = stepLength * sin(rad)
        val dy = stepLength * cos(rad)
        x += dx
        y += dy
        totalDistance += stepLength
    }

    fun setHeading(heading: Double) {
        var h = heading
        while (h < 0) h += 360
        while (h >= 360) h -= 360
        this.heading = h
    }

    fun reset() {
        x = 0.0
        y = 0.0
        totalDistance = 0.0
        stepCount = 0
        heading = 0.0
    }

    fun correctPosition(newX: Double, newY: Double) {
        x = newX
        y = newY
    }
}
