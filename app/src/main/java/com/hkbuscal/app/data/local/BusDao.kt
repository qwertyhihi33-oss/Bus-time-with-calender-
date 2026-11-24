package com.hkbuscal.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BusDao {

    @Query("SELECT COUNT(*) FROM bus_stops WHERE operatorCode = :operatorCode")
    suspend fun countStops(operatorCode: String): Int

    @Query("SELECT * FROM bus_stops WHERE operatorCode = :operatorCode")
    suspend fun getStopsByOperator(operatorCode: String): List<BusStopEntity>

    @Query("SELECT * FROM bus_stops WHERE stopKey = :stopKey LIMIT 1")
    suspend fun getStopByKey(stopKey: String): BusStopEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStops(stops: List<BusStopEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStop(stop: BusStopEntity)

    // Commute rules
    @Query("SELECT * FROM commute_rules ORDER BY id DESC")
    fun observeRules(): Flow<List<CommuteRuleEntity>>

    @Query("SELECT * FROM commute_rules ORDER BY id DESC")
    suspend fun getAllRules(): List<CommuteRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: CommuteRuleEntity): Long

    @Delete
    suspend fun deleteRule(rule: CommuteRuleEntity)
}
