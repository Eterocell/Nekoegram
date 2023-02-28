package com.eterocell.nekoegram.update

data class UpdateInfo(
    val version: String,
    val changelog: String,
    val size: String,
    val downloadUrl: String,
    val uploadDate: String
)
