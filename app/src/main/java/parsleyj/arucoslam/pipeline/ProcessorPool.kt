package parsleyj.arucoslam.pipeline

import kotlinx.coroutines.*
import kotlinx.coroutines.selects.select
import parsleyj.arucoslam.get
import parsleyj.arucoslam.list
import parsleyj.arucoslam.mainDispatcher
import parsleyj.kotutils.*


class ProcessorPool<MetadataT, InputT, OutputT, SupportDataT>(
    val metadata: MetadataT,
    private val maxProcessors: Int,
    private val supplyEmptyOutput: () -> OutputT,
    private val instantiateSupportData: () -> SupportDataT,
    private val coroutineScope: CoroutineScope = mainDispatcher,
    private val jobTimeout: Long = 1000L,
    private val block: suspend (MetadataT, InputT, OutputT, SupportDataT) -> Unit,
    private val onCannotProcess: (OutputT, InputT, Long) -> OutputT = { _, _, _ -> supplyEmptyOutput() },
) {
    private var lastResult = supplyEmptyOutput()
    private var lastResultToken = -1L
    private val processors = mutableListOf<Processor<MetadataT, InputT, OutputT, SupportDataT>>()

    private suspend fun <E : Job> Iterable<E>.joinFirst(): E = select {
        for (job in this@joinFirst) {
            job.onJoin { job }
        }
    }


    private suspend fun <E : Deferred<R>, R> Iterable<E>.awaitFirst(): R =
        joinFirst().getCompleted()


    fun supply(input: InputT?, token: Long) {
        // if there are no free processors, do not process the frame.
        val proc = getFreeProcessor()
        if (proc == null) {
            // we cannot find a free processor.
            // let's just return the input unprocessed
            if (input != null) {
                lastResult = onCannotProcess(lastResult, input, token)
            }
        } else {
            // we have a free frame processor
            // we assign the input frame to it
            proc.assignInput(input, token)
            coroutineScope.launch {
                // let's asynchronously start the job
                val job = coroutineScope.async { some(proc.computeAsync().await()) }
                //starts a race between the two coroutines:
                // - the first one executes the processor's job and then returns an OSome
                // - the second one simply awaits for jobTimeout milliseconds and then returns ONothing
                val optionalResult: Optional<Pair<OutputT, Long>> = list[
                        job,
                        coroutineScope.async {
                            delay(jobTimeout)
                            return@async nothing<Pair<OutputT, Long>>()
                        }
                ].awaitFirst()


                when(optionalResult){
                    is OSome<Pair<OutputT, Long>> -> {
                        val (result, orderToken) = optionalResult.get
                        // sets as last result if the order is correct
                        synchronized(this@ProcessorPool) {
                            if (lastResultToken < orderToken) {
                                lastResult = result
                                lastResultToken = orderToken
                            }
                        }
                    }
                    is ONothing<Pair<OutputT, Long>> ->{
                        //if after 'jobTimeout' milliseconds the job is not done, it is cancelled
                        job.cancel()
                    }
                }
            }
        }
    }

    /**
     * Retrieves the last computed result
     */
    fun retrieve(): OutputT {
        return lastResult
    }

    /**
     * Sorts processors by bringing the free ones at the beginning
     */
    private fun sortProcessors() {
        processors.sortBy { if (it.isBusy()) 1 else -1 }
    }

    /**
     * It finds a free processor if available. If not, it creates new frame processor.
     * If the creation is not possible, it returns null.
     */
    private fun getFreeProcessor(): Processor<MetadataT, InputT, OutputT, SupportDataT>? {
        if (processors.isEmpty()) {
            val frameProcessor = createProcessor()
            processors.add(frameProcessor)
            return frameProcessor
        }

        sortProcessors()

        return if (processors.first().isBusy()) {
            // if the first one is busy, all of them are.
            // let's try to instantiate one processor
            if (processors.size < maxProcessors) {
                // we can create a new processor
                val frameProcessor = createProcessor()
                processors.add(frameProcessor)
                // returning the new processor
                frameProcessor
            } else {
                // all busy, cannot instantiate more, return null
                null
            }
        } else {
            // simply return the first (which is not busy)
            processors.first()
        }

    }

    private fun createProcessor(): Processor<MetadataT, InputT, OutputT, SupportDataT> {
        return Processor(
            metadata,
            instantiateSupportData(),
            supplyEmptyOutput(),
            coroutineScope,
            block,
        )
    }
}


