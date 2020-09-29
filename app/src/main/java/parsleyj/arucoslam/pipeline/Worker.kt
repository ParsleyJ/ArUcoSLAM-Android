package parsleyj.arucoslam.pipeline

import kotlinx.coroutines.*
import parsleyj.arucoslam.FrameProcessor
import parsleyj.arucoslam.backgroundExec
import parsleyj.arucoslam.defaultDispatcher
import parsleyj.kotutils.with

open class Worker<InputT, OutputT, SupportDataT>(
    val recycledState: SupportDataT,
    emptyOutput: OutputT,
    private val coroutineScope: CoroutineScope = defaultDispatcher,
    val block: suspend (InputT, OutputT, SupportDataT) -> Unit,
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
                    block(inp, result, recycledState)
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

