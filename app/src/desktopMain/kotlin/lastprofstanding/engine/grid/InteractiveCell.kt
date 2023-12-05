package lastprofstanding.engine.grid

interface InteractiveCell {
    /**
     * Test, whether a new cell should be spawned. Might also return `GridPosition`s that are out-of-bounds.
     * @param grid Grid to calculate
     * @param position Position where the new cell should be spawned
     * @return A pair with the cell to spawn and its wished position (if empty)
     */
    fun getSpawnPattern(grid: Grid, position: GridPosition): SpawnPattern?
}