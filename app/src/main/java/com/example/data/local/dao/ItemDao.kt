package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entities.Item
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Query("SELECT * FROM items ORDER BY name ASC")
    fun getAllItems(): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE uuid = :uuid LIMIT 1")
    suspend fun getItemByUuid(uuid: String): Item?

    @Query("SELECT * FROM items WHERE sku = :sku LIMIT 1")
    suspend fun getItemBySku(sku: String): Item?

    @Query("SELECT * FROM items WHERE name LIKE '%' || :query || '%' OR sku LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchItems(query: String): Flow<List<Item>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: Item)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<Item>)

    @Query("DELETE FROM items")
    suspend fun clearAll()
}
