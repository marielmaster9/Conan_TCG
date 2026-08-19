package com.example.conantcg.data.model

data class PackStats(
    val total: Int,
    val owned: Int,
    val rarityMap: Map<String, Pair<Int, Int>>
)