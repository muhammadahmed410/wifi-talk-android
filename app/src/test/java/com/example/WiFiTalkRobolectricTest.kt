package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.crypto.SecurityManager
import com.example.db.AppDatabase
import com.example.db.CallLogEntity
import com.example.db.MessageEntity
import com.example.db.PeerEntity
import com.example.model.CallType
import com.example.model.MessageStatus
import com.example.model.PeerStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WiFiTalkRobolectricTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var securityManager: SecurityManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        securityManager = SecurityManager(context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testSecurityManager_persistentIdentity() {
        val id1 = securityManager.deviceId
        assertNotNull(id1)
        assertEquals(32, id1.length) // 16 bytes in hex = 32 chars

        // Create new instance on same context, should retrieve same persistent ID
        val secManager2 = SecurityManager(context)
        assertEquals(id1, secManager2.deviceId)

        // Test payload signature
        val sig1 = securityManager.signPayload("test_message_123")
        val sig2 = secManager2.signPayload("test_message_123")
        assertEquals(sig1, sig2)

        // Test replay protection and packet sequence
        val now = System.currentTimeMillis()
        val validSeq1 = securityManager.validatePacket("peer-a", now, 10L, now)
        assertTrue(validSeq1)

        // Same or lower sequence should fail (anti-replay)
        val replaySeq = securityManager.validatePacket("peer-a", now, 10L, now)
        assertFalse(replaySeq)

        // Clock skew beyond 30s should fail
        val staleTime = now - 45_000
        val clockSkew = securityManager.validatePacket("peer-a", staleTime, 20L, now)
        assertFalse(clockSkew)
    }

    @Test
    fun testSecurityManager_sanitization() {
        val longString = "A".repeat(3000)
        val sanitized = securityManager.sanitizeMessageText(longString)
        assertEquals(2000, sanitized.length)

        val nullByteString = "Hello\u0000World"
        val clean = securityManager.sanitizeMessageText(nullByteString)
        assertEquals("HelloWorld", clean)
    }

    @Test
    fun testRoomDatabase_peerLifecycle() = runBlocking {
        val peer = PeerEntity(
            deviceId = "dev-1234",
            displayName = "Alice's Phone",
            ipAddress = "192.168.1.15",
            status = PeerStatus.ONLINE.name
        )
        database.peerDao().insertOrUpdatePeer(peer)

        val retrieved = database.peerDao().getPeerById("dev-1234")
        assertNotNull(retrieved)
        assertEquals("Alice's Phone", retrieved?.displayName)
        assertEquals(PeerStatus.ONLINE.name, retrieved?.status)

        // Update status to UNREACHABLE
        database.peerDao().updatePeerStatus("dev-1234", PeerStatus.UNREACHABLE.name)
        val updated = database.peerDao().getPeerById("dev-1234")
        assertEquals(PeerStatus.UNREACHABLE.name, updated?.status)
    }

    @Test
    fun testRoomDatabase_messageQueueAndAck() = runBlocking {
        val msg1 = MessageEntity(
            id = "msg-1",
            conversationPeerId = "peer-bob",
            senderDeviceId = "peer-alice",
            text = "Offline pending message",
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.PENDING.name,
            isIncoming = false
        )
        database.messageDao().insertMessage(msg1)

        val pending = database.messageDao().getPendingForPeer("peer-bob")
        assertEquals(1, pending.size)
        assertEquals("msg-1", pending[0].id)

        // Simulate delivery ACK received
        database.messageDao().updateMessageStatus("msg-1", MessageStatus.DELIVERED.name)
        val pendingAfterAck = database.messageDao().getPendingForPeer("peer-bob")
        assertEquals(0, pendingAfterAck.size)

        val messagesList = database.messageDao().getMessagesForConversation("peer-bob").first()
        assertEquals(1, messagesList.size)
        assertEquals(MessageStatus.DELIVERED.name, messagesList[0].status)
    }

    @Test
    fun testRoomDatabase_callLogRecord() = runBlocking {
        val call = CallLogEntity(
            id = "call-1",
            peerDeviceId = "peer-charlie",
            peerDisplayName = "Charlie",
            callType = CallType.OUTGOING.name,
            timestamp = System.currentTimeMillis(),
            durationSeconds = 85L,
            terminationReason = "Normal hangup"
        )
        database.callLogDao().insertCallLog(call)

        val logs = database.callLogDao().getAllCallLogsFlow().first()
        assertEquals(1, logs.size)
        assertEquals("Charlie", logs[0].peerDisplayName)
        assertEquals(85L, logs[0].durationSeconds)
    }
}
