package com.karthick.partysync.data.remote

/**
 * Groups an ordered "still needed" chunk-hash subset into upload batches, honoring the
 * empirically-verified constraint documented on [Up2kClient.uploadChunkBatch]: a request
 * carrying exactly 1 chunk hash breaks the server's finalization bookkeeping whenever the file
 * has more than 1 chunk in total. Padding a would-be lone batch with an adjacent
 * already-uploaded chunk (re-sent harmlessly) guarantees every batch is safe to send.
 */
object ChunkBatching {
    private const val MAX_CHUNKS_PER_BATCH = 8

    /**
     * @param fullOrderedHashes every chunk hash for the file, in file order (from the handshake
     *   request / cached session state).
     * @param neededHashes the subset the server reported as still missing, in the same relative
     *   order as [fullOrderedHashes]. Assumed to be one (or more) contiguous runs by index —
     *   true for this app's always-sequential-from-resume-point upload pattern.
     */
    fun plan(fullOrderedHashes: List<String>, neededHashes: List<String>): List<List<String>> {
        if (neededHashes.isEmpty()) return emptyList()
        if (fullOrderedHashes.size <= 1) return listOf(neededHashes)

        val batches = neededHashes.chunked(MAX_CHUNKS_PER_BATCH).toMutableList()

        if (batches.size == 1 && batches[0].size == 1) {
            val onlyHash = batches[0].single()
            val idx = fullOrderedHashes.indexOf(onlyHash)
            batches[0] = if (idx > 0) {
                listOf(fullOrderedHashes[idx - 1], onlyHash)
            } else {
                listOf(onlyHash, fullOrderedHashes[idx + 1])
            }
        } else if (batches.size > 1 && batches.last().size == 1) {
            val last = batches.removeAt(batches.lastIndex)
            batches[batches.lastIndex] = batches[batches.lastIndex] + last
        }

        return batches
    }
}
