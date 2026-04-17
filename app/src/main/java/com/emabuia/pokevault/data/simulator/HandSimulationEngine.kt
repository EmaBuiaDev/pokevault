package com.emabuia.pokevault.data.simulator

import kotlin.random.Random

data class SimulatorCard(
    val name: String,
    val isBasic: Boolean,
    val isEnergy: Boolean,
    val isSupporter: Boolean,
    val isOutCard: Boolean
)

data class ProblemHandSample(
    val cards: List<String>,
    val tags: List<String>
)

data class HandSimulationSummary(
    val totalRuns: Int,
    val starterRate: Double,
    val mulliganRate: Double,
    val averageBasics: Double,
    val averageMulligans: Double,
    val energyByT1Rate: Double,
    val outByT1Rate: Double,
    val setupByT2Rate: Double,
    val keyCardByT2Rate: Double?,
    val sampleHand: List<String>,
    val problemHands: List<ProblemHandSample>
)

object HandSimulationEngine {

    fun run(
        cardPool: List<SimulatorCard>,
        runs: Int,
        keyCardNames: List<String>
    ): HandSimulationSummary {
        if (runs <= 0) {
            return emptySummary()
        }

        if (cardPool.size < 7) {
            return emptySummary()
        }

        val basicsInDeck = cardPool.count { it.isBasic }
        val normalizedKeyCards = keyCardNames
            .asSequence()
            .map { it.lowercase().trim() }
            .filter { it.isNotBlank() }
            .toSet()

        if (basicsInDeck == 0) {
            return HandSimulationSummary(
                totalRuns = runs,
                starterRate = 0.0,
                mulliganRate = 100.0,
                averageBasics = 0.0,
                averageMulligans = 99.0,
                energyByT1Rate = 0.0,
                outByT1Rate = 0.0,
                setupByT2Rate = 0.0,
                keyCardByT2Rate = normalizedKeyCards.takeIf { it.isNotEmpty() }?.let { 0.0 },
                sampleHand = cardPool.take(7).map { it.name },
                problemHands = listOf(
                    ProblemHandSample(
                        cards = cardPool.take(7).map { it.name },
                        tags = listOf("NO_BASIC_IN_DECK")
                    )
                )
            )
        }

        val random = Random(System.currentTimeMillis())

        var firstHandStarterHits = 0
        var firstHandMulligans = 0
        var totalBasics = 0
        var totalMulligans = 0
        var energyByT1Hits = 0
        var outByT1Hits = 0
        var setupByT2Hits = 0
        var keyCardByT2Hits = 0
        var sampleHand: List<String> = emptyList()

        val problemHands = mutableListOf<ProblemHandSample>()

        repeat(runs) { index ->
            var mulligansThisRun = 0
            var opening: List<SimulatorCard>
            var remaining: List<SimulatorCard>

            // Simulate mulligan loop until a legal opening hand is found.
            while (true) {
                val shuffled = cardPool.shuffled(random)
                opening = shuffled.take(7)
                remaining = shuffled.drop(7)

                val hasStarter = opening.any { it.isBasic }
                if (mulligansThisRun == 0) {
                    if (hasStarter) {
                        firstHandStarterHits += 1
                    } else {
                        firstHandMulligans += 1
                    }
                }

                if (hasStarter) break
                mulligansThisRun += 1
                if (mulligansThisRun >= 50) break
            }

            totalMulligans += mulligansThisRun

            val drawT1 = remaining.getOrNull(0)
            val drawT2 = remaining.getOrNull(1)

            val handT1 = opening + listOfNotNull(drawT1)
            val handT2 = handT1 + listOfNotNull(drawT2)

            val basics = opening.count { it.isBasic }
            val hasEnergyT1 = handT1.any { it.isEnergy }
            val hasSupporterT1 = handT1.any { it.isSupporter }
            val hasOutT1 = handT1.any { it.isOutCard }
            val hasEnergyT2 = handT2.any { it.isEnergy }
            val keyCardByT2 = normalizedKeyCards.takeIf { it.isNotEmpty() }?.let { keys ->
                handT2.any { keys.contains(it.name.lowercase().trim()) }
            }

            val setupByT2 = basics > 0 && hasEnergyT2 && (hasSupporterT1 || hasOutT1)

            totalBasics += basics
            if (hasEnergyT1) energyByT1Hits += 1
            if (hasOutT1) outByT1Hits += 1
            if (setupByT2) setupByT2Hits += 1
            if (keyCardByT2 == true) keyCardByT2Hits += 1

            val tags = mutableListOf<String>()
            if (!hasEnergyT1) tags += "NO_ENERGY_T1"
            if (!hasOutT1) tags += "NO_OUT_T1"
            if (!setupByT2) tags += "SETUP_RISK_T2"
            if (normalizedKeyCards.isNotEmpty() && keyCardByT2 == false) tags += "MISS_KEYCARD_T2"

            if (tags.isNotEmpty() && problemHands.size < 12) {
                problemHands += ProblemHandSample(
                    cards = opening.map { it.name },
                    tags = tags
                )
            }

            if (index == runs - 1) {
                sampleHand = opening.map { it.name }
            }
        }

        val totalRuns = runs.toDouble()
        val keyRate = normalizedKeyCards.takeIf { it.isNotEmpty() }?.let {
            (keyCardByT2Hits / totalRuns) * 100.0
        }

        return HandSimulationSummary(
            totalRuns = runs,
            starterRate = (firstHandStarterHits / totalRuns) * 100.0,
            mulliganRate = (firstHandMulligans / totalRuns) * 100.0,
            averageBasics = totalBasics / totalRuns,
            averageMulligans = totalMulligans / totalRuns,
            energyByT1Rate = (energyByT1Hits / totalRuns) * 100.0,
            outByT1Rate = (outByT1Hits / totalRuns) * 100.0,
            setupByT2Rate = (setupByT2Hits / totalRuns) * 100.0,
            keyCardByT2Rate = keyRate,
            sampleHand = sampleHand,
            problemHands = problemHands
        )
    }

    private fun emptySummary(): HandSimulationSummary {
        return HandSimulationSummary(
            totalRuns = 0,
            starterRate = 0.0,
            mulliganRate = 0.0,
            averageBasics = 0.0,
            averageMulligans = 0.0,
            energyByT1Rate = 0.0,
            outByT1Rate = 0.0,
            setupByT2Rate = 0.0,
            keyCardByT2Rate = null,
            sampleHand = emptyList(),
            problemHands = emptyList()
        )
    }
}
