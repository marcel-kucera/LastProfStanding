/*
 * This Kotlin source file was generated by the Gradle 'init' task.
 */

import androidx.compose.foundation.layout.Row
import androidx.compose.material.Button
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.runtime.*
import lastprofstanding.engine.*
import lastprofstanding.engine.grid.*

class MyCell : Cell(true), InteractiveCell<MyCell> {
    override fun testForSpawningNewCell(grid: Grid, position: GridPosition): Pair<MyCell, GridPosition> {
        return Pair(MyCell(), position + GridPosition(0, 1))
    }

    override fun clone(): Cell {
        return MyCell()
    }

    override val icon: Int = 5
    override val textRepresentation = "M"
}

@Composable
fun App() {
    val engine by remember { mutableStateOf(Engine.getInstance()) }
    var engineState by remember { mutableStateOf(EngineState(Grid(0, 0), StatsCounter())) }
    var didResetGrid by remember { mutableStateOf(false) }

    if (!didResetGrid) {
        engine.load(Level.BASIC)
        engineState = engine.state
        didResetGrid = true
    }

    Scaffold(topBar = {
        Row {
            Button(onClick = {
                engine.startSimulation(SimulationSpeed.X1) { state ->
                    engineState = state
                }
            }) {
                Text("Start simulation")
            }
            Button(onClick = {
                engine.stopSimulation()
                engineState = engine.state
            }) {
                Text("Stop simulation")
            }
            Button(onClick = {
                engine.load(Level.BASIC)
                engineState = engine.state
            }) {
                Text("Reset simulation")
            }
        }
    }) {
        Text("${engineState.stats}\n${engineState.grid.getTextRepresentation()}")
    }
}