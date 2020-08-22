package parsleyj.arucoslam.datamodel

import android.util.Log
import org.opencv.core.Mat
import parsleyj.arucoslam.asDoubleBuffer
import parsleyj.arucoslam.copyToNewByteBuffer
import parsleyj.arucoslam.datamodel.CRCCheckedMat.Companion.computeSumCheck
import kotlin.reflect.KProperty


/**
 * Used to solve via checksum redundancy the problem where a Mat changes its interal values after
 * the ART GC runs. Apparently Android does not care so much about memory used by jni libraries.
 * This is the nullable and mutable variant of this delegate, i.e. the managed property is
 * initialized with null (!!!) and can be re-assigned. At each new assignment, a new CRC check
 * value is computed and used later to verify data integrity.
 */
class CRCCheckedMutableMat(
    private val retriever: () -> Mat?
) {
    private var mat: Mat? = null
    private var checkValue: Int = 0


    operator fun getValue(thisRef: Any, property: KProperty<*>): Mat? {
        if (mat != null) {
            val evalSumCheck = computeSumCheck(mat!!)
            Log.i(
                "CRCCheckedMutableMat", "${property.name}: evalSumCheck=${evalSumCheck}" +
                        " original: $checkValue"
            )
            if (checkValue != evalSumCheck) {
                Log.d("CRCCheckedMutableMat", "${property.name}: INVALIDATED MAT! retrieving...")
                mat = retriever()
            }
        }
        return mat
    }

    operator fun setValue(thisRef: Any, property: KProperty<*>, value: Mat?) {
        if (value == null) {
            mat = null
        }
        if (value is Mat) {
            mat = value
            checkValue = computeSumCheck(value)
            Log.i("CRCCheckedMutableMat", "${property.name}: computed checkValue=${value}")
        }
    }


}