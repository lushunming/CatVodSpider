package com.github.catvod.bean.pan123

data class ShareInfo(
        val filename: String,
        val shareKey: String,
        val sharePwd: String,
        val next: Int,
        val fileId: Long,
        val S3KeyFlag: String,
        val Size: Long,
        val Etag: String
    )