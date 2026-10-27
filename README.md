# WiFi Talk — Production Native Android Local P2P Voice Calling & Messaging

**WiFi Talk** is a native Android application built with Kotlin and Jetpack Compose that allows devices connected to the same Wi-Fi router or a portable phone hotspot to automatically discover each other, place full-duplex VoIP voice calls, and exchange persistent text messages **completely offline** — with zero internet connection, zero mobile data, zero cloud servers, zero accounts, and zero external push notification services.

---

## 1. System Architecture

WiFi Talk follows clean, lifecycle-aware Android architecture divided into distinct, decoupled layers:

```
┌─────────────────────────────────────────────────────────────┐
│                 Jetpack Compose UI Layer                    │
│  PeopleScreen • ChatsScreen • DialPadScreen • History •     │
│  DiagnosticsScreen • CallOverlayScreen (Incoming/Connected) │
└──────────────────────────────┬──────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────┐
│                    WiFiTalkViewModel                        │
│          StateFlows: Peers, Messages, CallSession           │
└──────────────────────────────┬──────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────┐
│                    WiFiTalkRepository                       │
│    Orchestrates Discovery, Signaling, Audio, & Persistence  │
└──────┬───────────────┬──────────────┬───────────────┬───────┘
       │               │              │               │
┌──────▼──────┐ ┌──────▼──────┐ ┌─────▼──────┐ ┌──────▼──────┐
│  Discovery  │ │  Signaling  │ │ VoIP Audio │ │ Local Store │
│  NSD/mDNS & │ │ TCP Sockets │ │  UDP Audio │ │ Room SQLite │
│ UDP Beacon  │ │ Framed JSON │ │ G.711 PCMU │ │ Encrypted   │
│ (Port 5354) │ │ (Port 5355) │ │ (Port 5356)│ │ Prefs/DB    │
└─────────────┘ └─────────────┘ └────────────┘ └─────────────┘
```

### Key Modules:
- **`com.example.crypto`**: `SecurityManager` generates cryptographically random 128-bit persistent device IDs, signs/authenticates frames using HMAC-SHA256, enforces monotonic sequence numbers, anti-replay timestamp verification (±30s window), and payload sanitization (buffer overflow protection).
- **`com.example.net`**:
  - `LocalDiscoveryManager`: Dual discovery combining Android Network Service Discovery (NSD / DNS-SD `_wifitalk._tcp`) and UDP Multicast/Broadcast beacons on port 5354 every 4 seconds. Heartbeat monitoring transitions peers between `ONLINE`, `UNREACHABLE` (>12s), and `OFFLINE` (>25s).
  - `SignalingServer` & `SignalingClient`: High-speed TCP framed JSON protocol on port 5355 for instant call invites, accepts, declines, cancellations, hangup, chat messages, and delivery acknowledgments.
- **`com.example.audio`**:
  - `VoipAudioEngine`: Full-duplex VoIP engine over UDP (port 5356).
  - `G711Codec`: Standard ITU-T G.711 mu-law (PCMU) companding. Compresses 16-bit linear PCM into 8-bit log PCM (160 bytes per 20ms frame at 8 kHz). Zero third-party native binaries or licenses needed.
  - Hardware Acoustic Echo Cancellation (AEC) and Noise Suppression (NS) attached to `AudioRecord` session.
  - Jitter buffer queue and packet loss concealment (PLC) for smooth playback.
  - In-call microphone mute and speakerphone controls.
- **`com.example.db`**:
  - `AppDatabase` (Room SQLite) persisting `PeerEntity`, `MessageEntity`, and `CallLogEntity`.
  - True delivery acknowledgment: a message is marked `DELIVERED` only after the receiver has received, verified, and saved the record to SQLite. If a peer is offline, messages remain `PENDING` and are automatically retried when discovery detects the peer is back `ONLINE`.
- **`com.example.service`**:
  - `WiFiTalkService`: Declared Foreground Service (`connectedDevice|microphone`) with ongoing notification so devices stay reachable in the background.
  - High-priority full-screen incoming call notifications with Answer and Decline actions.

---

## 2. Network Protocol Specification

### Discovery Beacon (UDP Port 5354):
```json
WFTK_BCN:{"deviceId":"3a1f8c...","displayName":"Alice","port":5355,"audioPort":5356,"time":1700000000000}
```

### Signaling Frames (TCP Port 5355):
- **Call Invite**:
  ```json
  {"type":"CALL_INVITE","callId":"uuid","callerId":"3a1f8c...","callerName":"Alice","callerAudioPort":5356,"timestamp":1700000000000,"seq":1,"sig":"..."}
  ```
- **Call Accept**:
  ```json
  {"type":"CALL_ACCEPT","callId":"uuid","calleeId":"5b2e9d...","calleeAudioPort":5356}
  ```
- **Call Decline**:
  ```json
  {"type":"CALL_DECLINE","callId":"uuid","calleeId":"5b2e9d...","reason":"Declined"}
  ```
- **Call End**:
  ```json
  {"type":"CALL_END","callId":"uuid","senderId":"3a1f8c...","duration":125}
  ```
- **Chat Message Send**:
  ```json
  {"type":"MSG_SEND","messageId":"msg-uuid","conversationId":"peer-id","senderId":"3a1f8c...","senderName":"Alice","text":"Hello","timestamp":1700000000000,"seq":2,"sig":"..."}
  ```
- **Delivery Acknowledgment (ACK)**:
  ```json
  {"type":"MSG_ACK","messageId":"msg-uuid","receiverId":"5b2e9d...","timestamp":1700000000100}
  ```

### Real-Time VoIP Audio Datagram (UDP Port 5356):
```
[Bytes 0..3]   Magic header 0x5746544B ("WFTK")
[Bytes 4..7]   Call ID hash code (Int)
[Bytes 8..15]  Monotonic packet sequence number (Long)
[Bytes 16..19] Timestamp (Int)
[Bytes 20..179] 160 Bytes G.711 mu-law compressed audio (20ms speech frame at 8 kHz)
```

---

## 3. Permissions Matrix

| Permission | Type | Rationale |
|---|---|---|
| `INTERNET` | Install-time | Required for local TCP/UDP socket creation and binding on Wi-Fi |
| `ACCESS_NETWORK_STATE` | Install-time | Monitor Wi-Fi state changes, IP changes, and subnet configuration |
| `ACCESS_WIFI_STATE` | Install-time | Inspect local Wi-Fi connection info and SSID |
| `CHANGE_WIFI_MULTICAST_STATE` | Install-time | Acquire `MulticastLock` to receive UDP discovery beacons and mDNS |
| `NEARBY_WIFI_DEVICES` | Runtime (API 33+) | Android 13+ Wi-Fi peer discovery with `neverForLocation` flag |
| `RECORD_AUDIO` | Runtime | Capture microphone input for full-duplex VoIP calling |
| `MODIFY_AUDIO_SETTINGS` | Install-time | Manage speakerphone, earpiece routing, and communication audio mode |
| `POST_NOTIFICATIONS` | Runtime (API 33+) | Heads-up incoming call alert and incoming chat notifications |
| `VIBRATE` | Install-time | Ring vibration pattern for incoming calls |
| `WAKE_LOCK` | Install-time | Prevent CPU sleep during active voice calls |
| `USE_FULL_SCREEN_INTENT` | Install-time | Display incoming call screen when phone is locked |
| `FOREGROUND_SERVICE` | Install-time | Run background availability service |
| `FOREGROUND_SERVICE_CONNECTED_DEVICE` | Install-time | Android 14+ background discovery and peer signaling |
| `FOREGROUND_SERVICE_MICROPHONE` | Install-time | Android 14+ microphone access during active calls |

---

## 4. Physical Device Testing Checklist

### Scenario A: Two Phones on the Same Wi-Fi Router (Zero Internet)
1. Turn off Mobile Data on Phone A and Phone B.
2. Disconnect the WAN/Internet cable from your home Wi-Fi router (Wi-Fi router is on, but has **no internet access**).
3. Connect Phone A and Phone B to that Wi-Fi router.
4. Launch WiFi Talk on both phones.
5. **Verify Discovery**:
   - Phone A appears in Phone B's People list with green "Online" badge.
   - Phone B appears in Phone A's People list.
6. **Verify Text Messaging & Delivery ACKs**:
   - Phone A sends "Hello over offline Wi-Fi!".
   - Phone A's message shows single checkmark while sending, then switches to double checkmark (`DELIVERED`) immediately once Phone B writes it to SQLite.
   - Phone B taps the notification and views the chat. Phone A receives `READ` receipt.
7. **Verify Pending Message Queueing**:
   - Turn off Wi-Fi on Phone B. Phone A sees Phone B transition to `UNREACHABLE` then `OFFLINE`.
   - Phone A sends "This is a queued message". Status shows Clock icon (`PENDING`).
   - Turn Wi-Fi back on Phone B. Discovery detects Phone B is `ONLINE`.
   - Phone A automatically flushes the queue, transmitting the pending message. Phone A updates status to `DELIVERED`.
8. **Verify Full-Duplex VoIP Calling**:
   - Phone A taps Call on Phone B. Phone A shows "Calling...".
   - Phone B rings with audible ringtone, vibration, and full-screen incoming call UI.
   - Phone B taps "Answer".
   - Both phones show "Connected", duration timer starts counting.
   - Speak into Phone A: audio plays clearly through Phone B's speaker.
   - Speak into Phone B: audio plays clearly through Phone A.
   - Test "Mute": muting Phone A stops microphone capture without dropping call.
   - Test "Speaker": toggles between earpiece and loud speakerphone.
   - Tap "End Call": call ends cleanly, audio sockets release, call is recorded in Call History with exact duration.

### Scenario B: Portable Phone Hotspot (Completely Off-Grid / Field Operation)
1. On Phone A, turn ON "Personal Hotspot" / "Mobile Hotspot" (Mobile data is NOT needed).
2. On Phone B, connect to Phone A's Hotspot Wi-Fi.
3. Open WiFi Talk on both devices.
4. Verify both devices automatically discover each other over the hotspot subnet (e.g. 192.168.43.x) and execute calls and chats with full-duplex clarity.

### Scenario C: Router AP Client Isolation Diagnosis
1. Connect devices to a guest or hotel network that enables AP Client Isolation.
2. Open WiFi Talk -> Diagnostics tab.
3. Enter Phone B's IP address and tap "Test Ping".
4. Diagnostics will report "Unreachable: Peer at IP unreachable or port blocked", indicating that the router is actively filtering device-to-device traffic, and recommending Hotspot mode.

---

## 5. Security Model & Considerations

- **Cryptographic Primitives**: Standard Android `SecureRandom` 128-bit persistent device ID; standard HMAC-SHA256 signatures over packet headers and text payloads.
- **Replay Protection**: Packets carry monotonic sequence counters and strict ±30s timestamp windows. Duplicate or outdated packets are dropped.
- **Sanitization & Rate Limiting**: Maximum signaling packet size is bounded to 64KB, text messages to 2000 characters, and audio frames to 180 bytes, eliminating memory exhaustion risks.
- **Privacy**: The discovery beacon contains only the user-chosen display name and ports. Message texts, chat histories, and call metadata are never broadcast and never leave local device SQLite storage.
- **Known Limitations**: On open Wi-Fi networks without WPA2/WPA3 encryption, raw RF packets could be captured by a passive eavesdropper with a packet sniffer unless end-to-end asymmetric encryption (e.g. Noise protocol / Signal protocol) is negotiated per session. Users in adversarial environments should use WPA2/WPA3 protected Wi-Fi or encrypted hotspots.
