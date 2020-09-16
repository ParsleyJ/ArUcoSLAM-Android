package parsleyj.arucoslam.framepipeline

import parsleyj.arucoslam.pipeline.ProcessorPool
import parsleyj.kotutils.with

class SLAMStateUpdaterComponent(
    //TODO
) :ProcessorPool<
        Unit,
        Triple<FoundPoses?, Track?, AllPoses>,
        Pair<Track, AllPoses>,
        SLAMStateUpdaterComponent.SupportData>(
    Unit,
    3,
    {Track.empty() with AllPoses.empty()},
    { SupportData() },
    onCannotProcess = { _, (_, trackFromMaintainer, allPoses) ->
        (trackFromMaintainer ?: Track.empty()) with allPoses
    },
    block = { _, (foundPoses, previousTrack, allPoses), outputTrack, _, ->
        TODO()
    },
){
    class SupportData
}