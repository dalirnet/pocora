package ir.pocora.protocol

import ir.pocora.Role

object Protocol {
    const val SERVICE_TYPE = "_pocora._tcp"
    const val FRAME_HEADER_SIZE_BYTES = 4
    const val MAXIMUM_PAYLOAD_SIZE_BYTES = 4 * 1024 * 1024
    const val SYNC_INTERVAL_MILLISECONDS = 60_000L

    // Each role has its own port, so both apps can listen on one phone.
    // A fixed port is what lets a phone be reached through the gateway address when discovery finds nothing.
    const val PARENT_PORT = 47601
    const val CHILD_PORT = 47602

    const val CONNECT_TIMEOUT_MILLISECONDS = 5_000
    const val HANDSHAKE_TIMEOUT_MILLISECONDS = 10_000
    const val DISCOVERY_TIMEOUT_MILLISECONDS = 4_000L

    // How long a new connection waits for its first message.
    const val REQUEST_TIMEOUT_MILLISECONDS = 10_000

    // How long the child waits for the parent to tap Accept.
    const val PAIR_ANSWER_TIMEOUT_MILLISECONDS = 180_000

    fun portOf(role: Role): Int = if (role == Role.PARENT) PARENT_PORT else CHILD_PORT
}
