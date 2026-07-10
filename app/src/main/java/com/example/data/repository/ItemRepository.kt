package com.example.data.repository

import com.example.data.local.dao.ItemDao
import com.example.data.local.entities.Item
import kotlinx.coroutines.flow.Flow

class ItemRepository(private val itemDao: ItemDao) {
    val allItems: Flow<List<Item>> = itemDao.getAllItems()

    fun searchItems(query: String): Flow<List<Item>> {
        return if (query.isBlank()) {
            itemDao.getAllItems()
        } else {
            itemDao.searchItems(query)
        }
    }

    suspend fun getItemByUuid(uuid: String): Item? {
        return itemDao.getItemByUuid(uuid)
    }

    suspend fun getItemBySku(sku: String): Item? {
        return itemDao.getItemBySku(sku)
    }

    suspend fun insertItems(items: List<Item>) {
        itemDao.insertItems(items)
    }
}
