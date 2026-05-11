package com.mindmatrix.nimmaguru

import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

class FirebaseRepository {
    private val database = FirebaseDatabase.getInstance()

    // Fetch the list of Gurus
    suspend fun fetchGurus(): List<Guru> {
        return try {
            val snapshot = database.getReference("gurus").get().await()
            snapshot.children.mapNotNull { it.getValue(Guru::class.java) }
        } catch (e: Exception) {
            Log.e("Firebase", "Error fetching gurus", e)
            emptyList()
        }
    }

    // Fetch the list of Sessions
   /* suspend fun fetchSessions(): List<Session> {
        return try {
            val snapshot = database.getReference("sessions").get().await()
            snapshot.children.mapNotNull { it.getValue(Session::class.java) }
        } catch (e: Exception) {
            emptyList()
        }
    }*/
}