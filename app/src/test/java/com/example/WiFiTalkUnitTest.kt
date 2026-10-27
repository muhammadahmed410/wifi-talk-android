package com.example

import com.example.audio.G711Codec
import com.example.model.CallSession
import com.example.model.CallState
import com.example.model.Message
import com.example.model.MessageStatus
import com.example.model.Peer
import com.example.model.PeerStatus
import com.example.net.SignalingProtocol
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WiFiTalkUnitTest {

    @Test
    fun testG711Codec_encodeDecodeLoopback() {
        val original = ShortArray(160) { index ->
            // Sine wave test signal
            (Math.sin(2.0 * Math.PI * index / 16.0) * 16000).toInt().toShort()
        }

        val encoded = ByteArray(160)
        val encodedLen = G711Codec.encodeMuLaw(original, original.size, encoded)
        assertEquals(160, encodedLen)

        val decoded = ShortArray(160)
        val decodedLen = G711Codec.decodeMuLaw(encoded, encoded.size, decoded)
        assertEquals(160, decodedLen)

        // Verify G.711 mu-law reproduction within logarithmic quantization tolerance
        for (i in original.indices) {
            val diff = Math.abs(original[i] - decoded[i])
            assertTrue("Quantization error too large at $i: $diff", diff < 1500)
        }
    }

    @Test
    fun testSignalingProtocol_messageSendAndAck() {
        val msgPayload = SignalingProtocol.buildMessageSend(
            messageId = "msg-1234",
            conversationId = "peer-5678",
            senderId = "peer-1111",
            senderName = "Alice",
            text = "Hello over Wi-Fi without internet!",
            timestamp = 1700000000000L,
            seq = 42L,
            signature = "sig-abc"
        )

        val json = JSONObject(msgPayload)
        assertEquals(SignalingProtocol.TYPE_MSG_SEND, json.getString("type"))
        assertEquals("msg-1234", json.getString("messageId"))
        assertEquals("Alice", json.getString("senderName"))
        assertEquals("Hello over Wi-Fi without internet!", json.getString("text"))
        assertEquals(42L, json.getLong("seq"))

        // Build ACK
        val ackPayload = SignalingProtocol.buildMessageAck("msg-1234", "peer-5678", 1700000001000L)
        val ackJson = JSONObject(ackPayload)
        assertEquals(SignalingProtocol.TYPE_MSG_ACK, ackJson.getString("type"))
        assertEquals("msg-1234", ackJson.getString("messageId"))
    }

    @Test
    fun testSignalingProtocol_callSignalingLifecycle() {
        val invite = SignalingProtocol.buildCallInvite(
            callId = "call-999",
            callerId = "alice-device",
            callerName = "Alice",
            callerAudioPort = 5356,
            timestamp = 1700000000000L,
            seq = 1L,
            signature = "sig-call"
        )
        val inviteJson = JSONObject(invite)
        assertEquals(SignalingProtocol.TYPE_CALL_INVITE, inviteJson.getString("type"))
        assertEquals("call-999", inviteJson.getString("callId"))
        assertEquals(5356, inviteJson.getInt("callerAudioPort"))

        val accept = SignalingProtocol.buildCallAccept("call-999", "bob-device", 5356)
        val acceptJson = JSONObject(accept)
        assertEquals(SignalingProtocol.TYPE_CALL_ACCEPT, acceptJson.getString("type"))
        assertEquals("bob-device", acceptJson.getString("calleeId"))

        val end = SignalingProtocol.buildCallEnd("call-999", "alice-device", 125L)
        val endJson = JSONObject(end)
        assertEquals(SignalingProtocol.TYPE_CALL_END, endJson.getString("type"))
        assertEquals(125L, endJson.getLong("duration"))
    }

    @Test
    fun testCallSession_stateTransitions() {
        val call = CallSession(
            callId = "test-call",
            peerDeviceId = "peer-1",
            peerDisplayName = "Bob",
            peerIp = "192.168.1.10",
            state = CallState.OUTGOING_DIALING
        )

        assertTrue(call.isActive)
        assertFalse(call.isConnected)

        val connected = call.copy(state = CallState.CONNECTED, connectTime = System.currentTimeMillis())
        assertTrue(connected.isActive)
        assertTrue(connected.isConnected)

        val ended = connected.copy(state = CallState.ENDED)
        assertFalse(ended.isActive)
        assertFalse(ended.isConnected)
    }

    @Test
    fun testPeerModel_statusAndReachability() {
        val onlinePeer = Peer(
            deviceId = "dev-1",
            displayName = "Charlie",
            ipAddress = "192.168.1.50",
            status = PeerStatus.ONLINE
        )
        assertTrue(onlinePeer.isReachable)

        val unreachablePeer = onlinePeer.copy(status = PeerStatus.UNREACHABLE)
        assertFalse(unreachablePeer.isReachable)

        val offlinePeer = onlinePeer.copy(status = PeerStatus.OFFLINE)
        assertFalse(offlinePeer.isReachable)
    }
}
