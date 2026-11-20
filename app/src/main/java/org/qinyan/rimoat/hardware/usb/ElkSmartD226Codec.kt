package org.qinyan.rimoat.hardware.usb

import java.io.ByteArrayOutputStream
import java.util.PriorityQueue

/** D226 adds a frequency-table Huffman stream around the ordinary pulse dictionary. */
internal object ElkSmartD226Codec {
    private data class Node(val count: Int, val symbol: Int = -1, val left: Node? = null, val right: Node? = null)

    fun encode(input: ByteArray): ByteArray {
        require(input.isNotEmpty() && input.size <= 65535)
        val counts = IntArray(256)
        input.forEach { counts[it.toInt() and 255]++ }
        // The receiver reconstructs the tree from counts. Preserve the reference heap's
        // weight-only ordering and ascending symbol insertion, including ties.
        val heap = PriorityQueue<Node>(compareBy { it.count })
        counts.forEachIndexed { symbol, count -> if (count > 0) heap.add(Node(count, symbol)) }
        while (heap.size > 1) {
            val left = heap.remove()
            val right = heap.remove()
            heap.add(Node(left.count + right.count, left = left, right = right))
        }
        val codes = arrayOfNulls<String>(256)
        fun visit(node: Node, path: String) {
            if (node.symbol >= 0) codes[node.symbol] = path.ifEmpty { "0" }
            else { visit(node.left!!, path + '0'); visit(node.right!!, path + '1') }
        }
        visit(heap.remove(), "")
        val out = ByteArrayOutputStream()
        fun word(value: Int) { out.write(value ushr 8); out.write(value and 255) }
        word(counts.count { it > 0 })
        counts.forEachIndexed { symbol, count ->
            if (count > 0) { out.write(symbol); word(count) }
        }
        val bitCount = input.sumOf { codes[it.toInt() and 255]!!.length }
        out.write((8 - bitCount % 8) % 8)
        var byte = 0
        var used = 0
        input.forEach { value ->
            codes[value.toInt() and 255]!!.forEach { bit ->
                byte = (byte shl 1) or (bit - '0')
                if (++used == 8) { out.write(byte); byte = 0; used = 0 }
            }
        }
        if (used != 0) out.write(byte shl (8 - used))
        return out.toByteArray()
    }
}
