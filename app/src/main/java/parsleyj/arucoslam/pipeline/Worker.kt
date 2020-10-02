package parsleyj.arucoslam.pipeline

import kotlinx.coroutines.*
import parsleyj.arucoslam.defaultDispatcher

/**
 * A Worker takes an input of type [InputT] and performs a computation, by using the support
 * data structure of type [SupportDataT] and by saving the result of such computation on a data
 * structure of type [OutputT]. This system is optimized to carry out a succession of computations;
 * in fact, the output and support data structures are recycled and never re-allocated between a
 * computation an the other.
 */
open class Worker<InputT, OutputT, SupportDataT>(
    val recycledState: SupportDataT,
    emptyOutput: OutputT,
    private val coroutineScope: CoroutineScope = defaultDispatcher,
    val block: suspend (InputT, OutputT, SupportDataT, Long, Long) -> Unit,
    val onDone: Worker<InputT, OutputT, SupportDataT>.() -> Unit
) {
    private val result = emptyOutput
    private var input: InputT? = null
    var isActive = false
    var requestToken = -1L
    private set
    private var currentJob: Job? = null

    fun assignInput(input: InputT?, token: Long){
        if(input!=null){
            this.input = input
        }
        requestToken = token
    }


    suspend fun compute() :Job? {
        isActive = true
        currentJob = coroutineScope.launch {
            val inp = input
            if (inp == null) {
                done()
            } else {
                try {
                    block(inp, result, recycledState, requestToken, System.currentTimeMillis())
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
                done()
            }
        }
        return currentJob
    }

    private fun done() {
        isActive = false
        this.onDone()
    }

    fun isBusy() = isActive

    fun retrieveResult() = result
}

