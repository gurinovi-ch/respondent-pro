package com.respondent.pro.cabinet

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.lang.reflect.Proxy

/**
 * Граничная безопасность хранилища: сбой Keystore/SharedPreferences
 * не должен ронять приложение (важно из ревью — крэш-луп Настроек).
 * SharedPreferences подменяется прокси-двойником с инъекцией сбоев.
 */
class BindingStorageTest {

    private val store = mutableMapOf<String, Any?>()

    /** Прокси-двойник SharedPreferences: failRead/failWrite имитируют сбой Keystore. */
    private fun fakePrefs(failRead: Boolean = false, failWrite: Boolean = false): SharedPreferences {
        val editorHolder = arrayOfNulls<SharedPreferences.Editor>(1)
        editorHolder[0] = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java)
        ) { _, method, args ->
            when (method.name) {
                "putString" -> {
                    if (failWrite) throw SecurityException("keystore unavailable")
                    store[args[0] as String] = args[1]
                    editorHolder[0]
                }
                "clear" -> { store.clear(); editorHolder[0] }
                "apply" -> null
                "commit" -> true
                else -> editorHolder[0]
            }
        } as SharedPreferences.Editor

        return Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getString" -> {
                    if (failRead) throw SecurityException("keystore unavailable")
                    @Suppress("UNCHECKED_CAST")
                    (store[args[0] as String] as? String) ?: args[1] as? String
                }
                "edit" -> editorHolder[0]
                "contains" -> store.containsKey(args[0])
                else -> null
            }
        } as SharedPreferences
    }

    @Test
    fun `roundtrip write-read-clear`() {
        val storage = EncryptedBindingStorage(fakePrefs())
        assertNull(storage.read())

        storage.write(StoredBinding("rpro_k", "t1", "o1", "ООО", "Точка"))
        val back = storage.read()
        assertEquals("rpro_k", back?.apiKey)
        assertEquals("t1", back?.tabletId)
        assertEquals("o1", back?.organizationId)
        assertEquals("ООО", back?.organizationName)
        assertEquals("Точка", back?.pointName)

        storage.clear()
        assertNull(storage.read())
    }

    @Test
    fun `failing read returns null instead of throwing`() {
        val storage = EncryptedBindingStorage(fakePrefs(failRead = true))
        assertNull(storage.read())
    }

    @Test
    fun `failing write does not throw`() {
        val storage = EncryptedBindingStorage(fakePrefs(failWrite = true))
        storage.write(StoredBinding("k", "t", "o", "Org", null)) // не должно упасть
        storage.clear() // и очистка тоже
    }
}
