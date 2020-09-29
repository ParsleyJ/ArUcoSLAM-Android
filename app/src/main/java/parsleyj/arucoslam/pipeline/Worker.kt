package parsleyj.arucoslam.pipeline

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import parsleyj.arucoslam.defaultDispatcher
import parsleyj.kotutils.with

open class Processor<InputT, OutputT, SupportDataT>(
    val recycledState: SupportDataT,
    emptyOutput: OutputT,
    private val coroutineScope: CoroutineScope = defaultDispatcher,
    val block: suspend (InputT, OutputT, SupportDataT) -> Unit,
) {
    private val result = emptyOutput
    private var input: InputT? = null
    var requestToken = -1L
    private set
    private var currentJob: Job? = null

    fun assignInput(input: InputT?, token: Long){
        if(input!=null){
            this.input = input
        }
        requestToken = token
    }


    suspend fun computeAsync(): Deferred<Pair<OutputT, Long>> {
        val deferred = coroutineScope.async {
            val inp = input
            if (inp == null) {
                return@async result with requestToken
            } else {
                try {
                    block(inp, result, recycledState)
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
                return@async result with requestToken
            }
        }
        currentJob = deferred
        return deferred
    }

    fun isBusy() = currentJob?.isActive ?: false
}

