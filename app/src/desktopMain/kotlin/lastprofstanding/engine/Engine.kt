package lastprofstanding.engine

import androidx.compose.runtime.currentCompositionErrors
import lastprofstanding.engine.grid.*
import lastprofstanding.engine.grid.lecturing.Lecturer
import kotlin.math.max
import kotlin.time.measureTime

class Engine private constructor() {

    companion object {
        private var instance: Engine? = null

        fun getInstance() =
            instance ?: synchronized(this) {
                instance ?: Engine().also { instance = it }
            }

        /**
         * Get the time the simulation thread should sleep for between steps (in milliseconds).
         * @param stepTime time required to perform one simulation step
         * @param speed the speed setting of the simulation
         */
        private fun getSleepInterval(stepTime: Float, speed: SimulationSpeed): Float {
            val divisor = when (speed) {
                SimulationSpeed.PAUSED -> throw Exception("Simulation paused. It makes no sense to get a sleep interval.")
                SimulationSpeed.X1 -> 1
                SimulationSpeed.X2 -> 2
            }.toFloat()
            // 1 step/s for X1
            val targetPeriod = 1000f / divisor
            return max(targetPeriod - stepTime, 0f)
        }
    }


    private var current: Grid
    private var previous: Grid
    private var tileGrid: Grid
    private var stats = StatsCounter()
    private lateinit var simulationStepCallback: SimulationStepCallback
    private var runningSimulation = false

    init {
        current = Grid(0, 0)
        previous = Grid(0, 0)
        tileGrid = Grid(0, 0)
    }

    val state: EngineState
        get() = EngineState(previous, tileGrid, stats)

    fun load(level: LevelType) {
        val lvl = LevelController.load(level)
        current = lvl.dataGrid
        previous = lvl.dataGrid.clone()
        tileGrid = lvl.tileGrid
        stats = StatsCounter()
    }

    fun startSimulation(speed: SimulationSpeed, callback: SimulationStepCallback) {
        if (runningSimulation) {
            return
        }
        simulationStepCallback = callback
        runningSimulation = true
        if (speed == SimulationSpeed.PAUSED) {
            // Unpause requires calling `startSimulation` again
            runningSimulation = false
            return
        }
        Thread {
            simulationLoop(speed)
        }.start()
    }

    private fun simulationLoop(speed: SimulationSpeed) {
        while (runningSimulation) {
            val timeRequired = measureTime {
                stats += performSimulationStep()
                simulationStepCallback(state)
            }.inWholeMilliseconds.toFloat()
            val sleepInterval = getSleepInterval(timeRequired, speed)
            stats.timeSpentPlaying += timeRequired + sleepInterval
            Thread.sleep(sleepInterval.toLong())
        }
    }

    fun stopSimulation() {
        runningSimulation = false
    }

    fun applyAbility(ability: AbilityType, position: GridPosition) {
        previous.get(position)?.let { cell: Cell ->
            ability.getAbility().apply(cell)
        }
    }

    private fun performSimulationStep(): StatsCounter {
        var stats = StatsCounter()
        for (x in 0 until previous.columnCount) {
            for (y in 0 until previous.rowCount) {
                val position = GridPosition(x, y)
                previous.get(position)?.let { cell: Cell ->
                    spawnNewCellsForCell(cell, position)
                    val newPosition = moveCellAppropriately(cell, position)
                    stats += eliminateCellIfLifetimeOver(cell, position)
                    stats += eliminateCellIfDying(cell, position)
                    stats += evaluateWeakness(cell, position)
                    stats += fight(cell, position)

                }
            }
        }
        for (x in 0 until previous.columnCount) {
            for (y in 0 until previous.rowCount) {
                current.get(GridPosition(x, y))?.apply {
                    stepsSurvived += 1
                }
            }
        }

        previous = current.clone()

        return StatsCounter(0f, 0) // TODO: Update stats counter in step
    }

    private fun fight(cell: Cell, position: GridPosition): StatsCounter {
        cell.strength?.let { strength ->
            val sameCells = cell.countCells(previous, position, strength.radius, cell::class)
            val friendlyCells = cell.countCells(previous, position, strength.radius, strength.friendlyCell)
            val enemyCells =
                cell.countCells(previous, position, strength.radius) { _, _ -> true } - (sameCells + friendlyCells)
            val difference = enemyCells - (sameCells + friendlyCells)
            if (difference > strength.treshold) {
                killCellAt(position)
                return StatsCounter(lecturersDied = 1)
            }
        }
        return StatsCounter()
    }

    private fun eliminateCellIfDying(cell: Cell, position: GridPosition): StatsCounter {
        if (cell.checkIfDying(previous, position)) {
            return killCellAt(position)
        }
        return StatsCounter()
    }

    private fun spawnNewCellsForCell(
        spawningCell: Cell,
        position: GridPosition
    ) {
        if (spawningCell.testForSpawningNewCells(previous, position)) {
            spawningCell.getSpawnPattern(previous, position)?.let { pattern: SpawnPattern ->
                for ((pos, cell) in pattern) {
                    spawnCellIfWithinBounds(cell, pos)
                    spawningCell.activeAbility?.applyToSpawnedCell(cell)
                }
            }
        }
    }

    private fun moveCellAppropriately(cell: Cell, position: GridPosition): GridPosition {
        val newPos = cell.getMovementData(previous, position)
        moveCellIfPossible(cell, position, newPos)
        return newPos
    }

    /**
     * Spawn cell if the `position` is within bounds and points to empty cell. Overwrites empty cells, but no others.
     * @param cell cell to spawn
     * @param position position to spawn the new cell at
     */
    private fun spawnCellIfWithinBounds(cell: Cell, position: GridPosition) {
        if (position.outOfBounds(current.rowCount, current.columnCount)) {
            return
        }
        val existingCell = previous.get(position)
        if (existingCell?.passable != false) {
            current.replace(position, cell)
        }
    }

    private fun moveCellIfPossible(cell: Cell, currentPosition: GridPosition, newPosition: GridPosition) {
        if (!newPosition.outOfBounds(previous.rowCount, previous.columnCount) && currentPosition != newPosition) {
            previous.get(newPosition)?.let {
                if (it.passable) {
                    current.replace(newPosition, cell)
                    current.replace(currentPosition, (tileGrid.get(currentPosition) as Tile).generateDataCell())
                }
            }
        }
    }

    private fun eliminateCellIfLifetimeOver(cell: Cell, position: GridPosition): StatsCounter {
        if (cell.evaluateWhetherLifetimeOver()) {
            return killCellAt(position)
        }
        return StatsCounter()
    }

    private fun killCellAt(position: GridPosition): StatsCounter {
        val cell = previous.get(position)
        val lecturersDied = if (cell is Lecturer) {
            1
        } else {
            0
        }
        val tile = tileGrid.get(position) as Tile
        current.replace(position, tile.generateDataCell())
        return StatsCounter(lecturersDied = lecturersDied)
    }

    private fun evaluateWeakness(cell: Cell, position: GridPosition): StatsCounter {
        cell.weakness?.let { weakness: Weakness<*> ->
            if (cell.countCells(previous, position, weakness.radius, weakness.against) >= weakness.cellCount) {
                return killCellAt(position)
            }
        }
        return StatsCounter()
    }
}