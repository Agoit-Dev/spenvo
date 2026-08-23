package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.repository.StorageRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SubirAvatarUseCaseTest {

    private val repo = FakeStorageRepository()

    @Test
    fun `delega uid, bytes y contentType al repositorio y devuelve la url`() = runTest {
        val useCase = SubirAvatarUseCase(repo)
        val bytes = byteArrayOf(1, 2, 3)

        val url = useCase(uid = "user-1", bytes = bytes, contentType = "image/jpeg")

        assertEquals("user-1", repo.ultimoUid)
        assertEquals(bytes, repo.ultimosBytes)
        assertEquals("image/jpeg", repo.ultimoContentType)
        assertEquals("https://fake.storage/user-1/avatar.jpg", url)
    }

    @Test
    fun `propaga la excepcion del repositorio sin capturarla`() = runTest {
        val useCase = SubirAvatarUseCase(FakeStorageRepository(fallar = true))

        assertThrows(IllegalStateException::class.java) {
            runTest { useCase(uid = "user-1", bytes = byteArrayOf(), contentType = "image/jpeg") }
        }
    }
}

private class FakeStorageRepository(private val fallar: Boolean = false) : StorageRepository {
    var ultimoUid: String? = null
    var ultimosBytes: ByteArray? = null
    var ultimoContentType: String? = null

    override suspend fun subirAvatar(uid: String, bytes: ByteArray, contentType: String): String {
        if (fallar) error("fallo simulado")
        ultimoUid = uid
        ultimosBytes = bytes
        ultimoContentType = contentType
        return "https://fake.storage/$uid/avatar.jpg"
    }
}
