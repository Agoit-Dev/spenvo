package com.agoitdev.spenvo.data.local.converter

import androidx.room.TypeConverter
import com.agoitdev.spenvo.domain.sync.CampoConflicto
import com.agoitdev.spenvo.domain.sync.SnapshotConflicto
import com.agoitdev.spenvo.domain.sync.TipoRegistro
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    @TypeConverter
    fun instantToLong(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun longToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun localDateToString(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun stringToLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun tipoRegistroToString(value: TipoRegistro): String = value.name

    @TypeConverter
    fun stringToTipoRegistro(value: String): TipoRegistro = TipoRegistro.valueOf(value)

    // SnapshotConflicto carries java.time.Instant, which kotlinx-serialization has no built-in
    // serializer for — a persistence-only DTO (epoch millis) keeps that detail out of
    // :core:domain, same pattern as every other entity in this project (domain models never carry
    // persistence annotations; Categoria/CategoriaDto/CategoriaEntity is the existing precedent).
    @TypeConverter
    fun snapshotConflictoToJson(value: SnapshotConflicto): String = Json.encodeToString(value.toDto())

    @TypeConverter
    fun jsonToSnapshotConflicto(value: String): SnapshotConflicto =
        Json.decodeFromString<SnapshotConflictoDto>(value).toDomain()
}

@Serializable
private data class SnapshotConflictoDto(
    val editadoPor: String?,
    val editadoEnMillis: Long?,
    val borrado: Boolean,
    val campos: List<CampoConflictoDto>,
)

@Serializable
private data class CampoConflictoDto(val clave: String, val valor: String)

private fun SnapshotConflicto.toDto() = SnapshotConflictoDto(
    editadoPor = editadoPor,
    editadoEnMillis = editadoEn?.toEpochMilli(),
    borrado = borrado,
    campos = campos.map { CampoConflictoDto(it.clave, it.valor) },
)

private fun SnapshotConflictoDto.toDomain() = SnapshotConflicto(
    editadoPor = editadoPor,
    editadoEn = editadoEnMillis?.let(Instant::ofEpochMilli),
    borrado = borrado,
    campos = campos.map { CampoConflicto(it.clave, it.valor) },
)
