package com.squatwolf.gagyebu.data

import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

/**
 * Firestore 공유 가계부.
 * ledgers/{초대코드} 문서에 members(최대 2명), 내역은 ledgers/{초대코드}/entries 에 저장한다.
 * 로그인은 익명 로그인이라 계정 없이 쓰고, 다른 사람은 초대코드로 들어온다.
 */
class CloudLedger(private val prefs: SharedPreferences) : LedgerRepo {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override val status = MutableStateFlow("연결 중…")

    /** 로그인이 끝나고 쓸 가계부가 정해지면 초대코드가 들어온다. */
    val code = MutableStateFlow<String?>(null)

    private val uid get() = auth.currentUser?.uid

    suspend fun start() {
        try {
            if (auth.currentUser == null) auth.signInAnonymously().await()
            use(prefs.getString(KEY, null) ?: create())
        } catch (e: Exception) {
            status.value = "연결 실패: ${e.message}"
        }
    }

    /** 상대의 초대코드로 들어간다. 실패하면 이유를 돌려준다. */
    suspend fun join(input: String): String? {
        val c = input.trim().uppercase()
        if (c.isEmpty()) return "코드를 입력해 주세요"
        if (c == code.value) return "지금 쓰고 있는 가계부예요"
        val ref = db.collection(LEDGERS).document(c)
        return try {
            val snap = ref.get().await()
            if (!snap.exists()) return "없는 코드예요"
            val members = (snap.get("members") as? List<*>).orEmpty().map { it.toString() }
            if (uid !in members) {
                if (members.size >= 2) return "이미 두 명이 쓰고 있는 가계부예요"
                ref.update("members", FieldValue.arrayUnion(uid)).await()
            }
            use(c)
            null
        } catch (e: Exception) {
            "참여 실패: ${e.message}"
        }
    }

    private suspend fun create(): String {
        val c = newCode()
        db.collection(LEDGERS).document(c)
            .set(mapOf("members" to listOf(uid), "createdAt" to FieldValue.serverTimestamp()))
            .await()
        return c
    }

    private fun use(c: String) {
        prefs.edit().putString(KEY, c).apply()
        code.value = c
        status.value = "공유 중 · 초대코드 $c"
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun entries(month: YearMonth): Flow<List<Entry>> = code.flatMapLatest { c ->
        if (c == null) {
            flowOf(emptyList())
        } else {
            callbackFlow {
                val reg = entriesOf(c)
                    .whereGreaterThanOrEqualTo("date", month.atDay(1).toString())
                    .whereLessThanOrEqualTo("date", month.atEndOfMonth().toString())
                    .addSnapshotListener { snap, err ->
                        if (err != null) {
                            status.value = "읽기 실패: ${err.message}"
                            return@addSnapshotListener
                        }
                        trySend(snap?.documents?.mapNotNull { it.toEntry() } ?: emptyList())
                    }
                awaitClose { reg.remove() }
            }
        }
    }

    // 쓰기는 기다리지 않는다. 오프라인이어도 화면에 바로 보이고 연결되면 올라간다.
    override fun add(entry: Entry) {
        val c = code.value ?: return
        entriesOf(c).document().set(
            mapOf(
                "date" to entry.date,
                "income" to entry.income,
                "amount" to entry.amount,
                "category" to entry.category,
                "memo" to entry.memo,
                "method" to entry.method,
                "author" to entry.author,
                "uid" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
            )
        )
    }

    override fun delete(id: String) {
        val c = code.value ?: return
        entriesOf(c).document(id).delete()
    }

    private fun entriesOf(c: String) = db.collection(LEDGERS).document(c).collection("entries")

    private fun DocumentSnapshot.toEntry(): Entry? {
        return Entry(
            id = id,
            date = getString("date") ?: return null,
            income = getBoolean("income") ?: false,
            amount = getLong("amount") ?: 0,
            category = getString("category").orEmpty(),
            memo = getString("memo").orEmpty(),
            method = getString("method").orEmpty(),
            author = getString("author").orEmpty(),
        )
    }

    private companion object {
        const val LEDGERS = "ledgers"
        const val KEY = "ledger_code"

        // 헷갈리는 글자(0, O, 1, I)는 뺐다.
        const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

        fun newCode() = (1..8).map { ALPHABET.random() }.joinToString("")
    }
}
