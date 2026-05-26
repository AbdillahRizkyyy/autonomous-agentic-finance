package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nominal: Double,
    val category: String,
    val type: String, // "INCOME" atau "EXPENSE"
    val description: String,
    val source: String, // "NOTIFIKASI", "LAYAR", "TUNAI"
    val timestamp: Long = System.currentTimeMillis()
)
