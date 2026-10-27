package com.example.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PeerDao {
    @Query("SELECT * FROM peers ORDER BY CASE WHEN status = 'ONLINE' THEN 1 WHEN status = 'UNREACHABLE' THEN 2 ELSE 3 END, lastSeenEpoch DESC")
    fun getAllPeersFlow(): Flow<List<PeerEntity>>

    @Query("SELECT * FROM peers WHERE deviceId = :deviceId LIMIT 1")
    suspend fun getPeerById(deviceId: String): PeerEntity?

    @Query("SELECT * FROM peers WHERE isBlocked = 0 ORDER BY lastSeenEpoch DESC")
    suspend fun getActivePeers(): List<PeerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdatePeer(peer: PeerEntity)

    @Update
    suspend fun updatePeer(peer: PeerEntity)

    @Query("UPDATE peers SET status = :status WHERE deviceId = :deviceId")
    suspend fun updatePeerStatus(deviceId: String, status: String)

    @Query("UPDATE peers SET isBlocked = :isBlocked WHERE deviceId = :deviceId")
    suspend fun setBlocked(deviceId: String, isBlocked: Boolean)

    @Query("DELETE FROM peers WHERE deviceId = :deviceId")
    suspend fun deletePeer(deviceId: String)

    @Query("DELETE FROM peers")
    suspend fun clearAll()
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationPeerId = :peerId ORDER BY timestamp ASC")
    fun getMessagesForConversation(peerId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages ORDER BY timestamp DESC")
    fun getAllMessagesFlow(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE status = 'PENDING' AND isIncoming = 0")
    suspend fun getPendingOutgoingMessages(): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE conversationPeerId = :peerId AND status = 'PENDING' AND isIncoming = 0")
    suspend fun getPendingForPeer(peerId: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("UPDATE messages SET status = :status WHERE id = :id")
    suspend fun updateMessageStatus(id: String, status: String)

    @Query("UPDATE messages SET status = 'READ' WHERE conversationPeerId = :peerId AND isIncoming = 1 AND status != 'READ'")
    suspend fun markConversationAsRead(peerId: String)

    @Query("SELECT COUNT(*) FROM messages WHERE conversationPeerId = :peerId AND isIncoming = 1 AND status != 'READ'")
    fun getUnreadCountFlow(peerId: String): Flow<Int>

    @Query("DELETE FROM messages WHERE conversationPeerId = :peerId")
    suspend fun deleteConversation(peerId: String)

    @Query("DELETE FROM messages")
    suspend fun clearAll()
}

@Dao
interface CallLogDao {
    @Query("SELECT * FROM call_logs ORDER BY timestamp DESC")
    fun getAllCallLogsFlow(): Flow<List<CallLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallLog(callLog: CallLogEntity)

    @Query("DELETE FROM call_logs")
    suspend fun clearAll()
}
