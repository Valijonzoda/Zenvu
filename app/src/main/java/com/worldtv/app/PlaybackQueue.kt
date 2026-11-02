package com.worldtv.app

import com.worldtv.app.domain.MediaEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PlaybackQueue {
    data class State(
        val entries: List<MediaEntry> = emptyList(),
        val index: Int = 0,
    ) {
        val current: MediaEntry? get() = entries.getOrNull(index)
    }

    private val stateFlow = MutableStateFlow(State())
    val state: StateFlow<State> = stateFlow.asStateFlow()

    fun open(entries: List<MediaEntry>, index: Int) {
        if (entries.isEmpty()) return
        stateFlow.value = State(entries, index.coerceIn(entries.indices))
    }

    fun select(key: String) {
        val current = stateFlow.value
        val index = current.entries.indexOfFirst { it.key == key }
        if (index >= 0) stateFlow.value = current.copy(index = index)
    }

    fun clear() {
        stateFlow.value = State()
    }

    fun next() = shift(1)

    fun previous() = shift(-1)

    private fun shift(delta: Int) {
        val current = stateFlow.value
        val size = current.entries.size
        if (size == 0) return
        val nextIndex = ((current.index + delta) % size + size) % size
        stateFlow.value = current.copy(index = nextIndex)
    }
}
