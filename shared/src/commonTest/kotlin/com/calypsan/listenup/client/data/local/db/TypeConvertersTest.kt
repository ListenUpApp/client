package com.calypsan.listenup.client.data.local.db

import kotlin.test.Test
import kotlin.test.assertEquals

class TypeConvertersTest {
    private val converters = Converters()

    @Test
    fun fromSyncState_synced_returnsOrdinal() {
        val result = converters.fromSyncState(SyncState.SYNCED)
        assertEquals(0, result)
    }

    @Test
    fun fromSyncState_notSynced_returnsOrdinal() {
        val result = converters.fromSyncState(SyncState.NOT_SYNCED)
        assertEquals(1, result)
    }

    @Test
    fun fromSyncState_syncing_returnsOrdinal() {
        val result = converters.fromSyncState(SyncState.SYNCING)
        assertEquals(2, result)
    }

    @Test
    fun fromSyncState_conflict_returnsOrdinal() {
        val result = converters.fromSyncState(SyncState.CONFLICT)
        assertEquals(3, result)
    }

    @Test
    fun toSyncState_0_returnsSynced() {
        val result = converters.toSyncState(0)
        assertEquals(SyncState.SYNCED, result)
    }

    @Test
    fun toSyncState_1_returnsNotSynced() {
        val result = converters.toSyncState(1)
        assertEquals(SyncState.NOT_SYNCED, result)
    }

    @Test
    fun toSyncState_2_returnsSyncing() {
        val result = converters.toSyncState(2)
        assertEquals(SyncState.SYNCING, result)
    }

    @Test
    fun toSyncState_3_returnsConflict() {
        val result = converters.toSyncState(3)
        assertEquals(SyncState.CONFLICT, result)
    }

    @Test
    fun syncStateConversion_roundTrip_preservesValue() {
        SyncState.entries.forEach { state ->
            val ordinal = converters.fromSyncState(state)
            val restored = converters.toSyncState(ordinal)
            assertEquals(state, restored)
        }
    }
}
