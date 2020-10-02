package parsleyj.arucoslam.pipeline

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.select
import parsleyj.arucoslam.backgroundExec
import parsleyj.arucoslam.mainDispatcher

/**
 * A RenderingWorkerPool is a set of [Worker]s specialized for real-time rendering; the main idea
 * behind this is that only the *last* result returned by a computation is the one that matters; so
 * if a result is obtained too late to be retrieved, such result is discarded. This is particularly
 * fine for image rendering where some frame skipping is acceptable. The main primitives of this
 * concept are [supply] and [retrieve], used, respectively, to supply an input to the pool and
 * to receive the last output available.
 *
 * @param maxWorkers the maximum number of parallel workers
 * @param supplyEmptyOutput a function that constructs an empty output data structure
 * @param supplyEmptySupportData a function that constructs an empty support data structure
 * @param coroutineScope the scope on which the jobs of the workers are launched
 * @param jobTimeout time in milliseconds after which a job is cancelled
 * @param onCannotProcess a callback that tells the pool what to do when there are no free workers
 *                        and no new workers cannot be created
 * @param block a suspendable function that defines what to do in each job; the parameters are:
 *              1. the input of the job; 2. the reference on which to write the output of the job;
 *              3. the support data structure; 4. a numerical token identifying the current job;
 *              5. unix-epoch timestamp in milliseconds of the start of the job
 */
open class RenderingWorkerPool<InputT, OutputT, SupportDataT>(
    private val maxWorkers: Int,
    private val supplyEmptyOutput: () -> OutputT,
    private val supplyEmptySupportData: () -> SupportDataT,
    private val coroutineScope: CoroutineScope = mainDispatcher,
    private val jobTimeout: Long = 1000L,
    private val onCannotProcess: (OutputT, InputT) -> OutputT = { _, _ -> supplyEmptyOutput() },
    private val block: suspend (InputT, OutputT, SupportDataT, Long, Long) -> Unit,
) {
    private var lastResult = supplyEmptyOutput()
    private var lastResultToken = -1L
    protected val workers = mutableListOf<Worker<InputT, OutputT, SupportDataT>>()
    private val tokenGenerator = object:Iterator<Long>{
        var count = 0L
        override fun hasNext(): Boolean {
            return true
        }

        override fun next(): Long {
            return count++
        }

    }


    fun supply(input: InputT) {
        val token = tokenGenerator.next()
        Log.d("WorkerPool", "Supply invoked - token: $token")
        // if there are no free workers, do not process the frame.
        val worker = getFreeWorker()
        if (worker == null) {
            // we cannot find a free worker.
            // let's see what to do using onCannotProcess
            if (input != null) {
                lastResult = onCannotProcess(lastResult, input)
            }
        } else {
            // we have a free frame worker
            // we assign the input frame to it
            worker.assignInput(input, token)

            // let's asynchronously start the job
            backgroundExec {
                val job = worker.compute()
                //if after 'jobTimeout' milliseconds the job is not done, it is cancelled
                delay(jobTimeout)
                job?.cancel()
                worker.isActive = false
            }
        }
    }

    /**
     * Retrieves the last computed result
     */
    fun retrieve(): OutputT {
        Log.d("WorkerPool", "Retrieve invoked - last result token: $lastResultToken")
        return lastResult
    }

    /**
     * Sorts workers by bringing the free ones at the beginning
     */
    private fun sortWorkers() {
        workers.sortBy { if (it.isBusy()) 1 else -1 }
    }

    /**
     * It finds a free worker if available. If not, it creates new frame worker.
     * If the creation is not possible, it returns null.
     */
    private fun getFreeWorker(): Worker<InputT, OutputT, SupportDataT>? {
        if (workers.isEmpty()) {
            val frameProcessor = createWorker()
            workers.add(frameProcessor)
            return frameProcessor
        }

        sortWorkers()

        return if (workers.first().isBusy()) {
            // if the first one is busy, all of them are.
            // let's try to instantiate one worker
            if (workers.size < maxWorkers) {
                // we can create a new worker
                val frameProcessor = createWorker()
                workers.add(frameProcessor)
                // returning the new worker
                frameProcessor
            } else {
                // all busy, cannot instantiate more, return null
                null
            }
        } else {
            // simply return the first (which is not busy)
            workers.first()
        }

    }

    private fun createWorker(): Worker<InputT, OutputT, SupportDataT> {
        return Worker(
            supplyEmptySupportData(),
            supplyEmptyOutput(),
            coroutineScope,
            block = block,
            onDone = worker@{
                synchronized(this@RenderingWorkerPool) {
                    if (lastResultToken < this.requestToken) {
                        lastResult = this.retrieveResult()
                        lastResultToken = this.requestToken
                    }
                }
            },
        )
    }

    /**
     * Returns the percentage of busy workers
     */
    fun usage(): Double {
        return (workers.filter { it.isBusy() }.count().toDouble() / maxWorkers.toDouble()) * 100.0
    }
}
