package parsleyj.arucoslam.datamodel

import android.util.Log
import org.opencv.core.Mat
import parsleyj.arucoslam.asDoubleBuffer
import parsleyj.arucoslam.copyToNewByteBuffer
import kotlin.reflect.KProperty


/**
 * Used to solve via checksum redundancy the problem where a Mat changes its interal values after
 * the ART GC runs. Apparently Android does not care so much about memory used by jni libraries.
 * This is the non-nullable, lazy and immutable variant of this delegate, i.e. the managed property
 * is initialized with the retriever function at first access and cannot be re-assigned.
 */
class CRCCheckedMat(
    private val retriever: () -> Mat
) {
    private var mat: Mat? = null
    private var checkValue: Int = 0


    operator fun getValue(thisRef: Any, property: KProperty<*>): Mat {
        if (mat == null) {
            setMat(property)
        }

        val evalSumCheck = computeSumCheck(mat!!)
        Log.i(
            "CRCCheckedMat", "${property.name}: evalSumCheck=${evalSumCheck}" +
                    " original: $checkValue"
        )
        if (checkValue != evalSumCheck) {
            Log.d("CRCCheckedMat", "${property.name}: INVALIDATED MAT! retrieving...")
            mat = retriever()
        }

        return mat!!
    }


    private fun setMat(property: KProperty<*>){
        val value = retriever()
        mat = value
        checkValue = computeSumCheck(value)
        Log.i("CRCCheckedMat", "${property.name}: computed checkValue=${value}")
    }

    companion object {
        private fun modRtuCrc(buf: ByteArray): Int {
            var crc = 0xFFFF
            for (element in buf) {
                crc = crc xor (element.toInt() and 0xFF) // XOR byte into least sig. byte of crc
                for (i in 8 downTo 1) {    // Loop over each bit
                    if (crc and 0x0001 != 0) {      // If the LSB is set
                        crc = crc shr 1 // Shift right and XOR 0xA001
                        crc = crc xor 0xA001
                    } else  // Else LSB is not set
                        crc = crc shr 1 // Just shift right
                }
            }
            // Note, this number has low and high bytes swapped, so use it accordingly (or swap bytes)
            return crc
        }

        fun computeSumCheck(m: Mat): Int {

            val doubleData = DoubleArray((m.total() * m.channels()).toInt())
            m.get(0, 0, doubleData)

            return modRtuCrc(doubleData.asDoubleBuffer().copyToNewByteBuffer().array())
        }
    }
}