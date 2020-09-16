package parsleyj.arucoslam.framepipeline

import parsleyj.arucoslam.pipeline.ProcessorPool
import parsleyj.kotutils.with

class SLAMStateMaintainerComponent(
    //TODO
):ProcessorPool<
        Unit,
        Pair<Track, FoundPoses?>,
        Pair<Track, AllPoses>,
        SLAMStateMaintainerComponent.Companion.SupportData>(
    Unit,
    1,
    {Track.empty() with AllPoses.empty()},
    {SupportData()},
    onCannotProcess = { lastResult, _ -> lastResult },
    block = { _, (inputTrack, inputPoses), (outputTrack, allPoses), supportData ->
        TODO()
    }
){
    companion object{
        class SupportData{
            //TODO
        }
    }
}