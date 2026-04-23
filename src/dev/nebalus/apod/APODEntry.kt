package dev.nebalus.apod

import java.time.LocalDate

data class APODEntry(
    val date: LocalDate,
    val title: String,
    val mediaType: String,
    val url: String,
    val hdurl: String?,
    val explanation: String
) {
    val isImage: Boolean
        get() = mediaType == "image"

    val bestImageUrl: String
        get() = if (!hdurl.isNullOrEmpty()) hdurl else url

    override fun toString(): String = "[$date] $title ($mediaType)"
}
