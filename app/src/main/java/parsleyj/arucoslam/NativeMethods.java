package parsleyj.arucoslam;

public class NativeMethods {


    public static native int detectMarkers(
            long cameraMatrixAddr,
            long distCoeffsAddr,
            long inputMatAddr,
            long resultMatAddr,
            double markerSize,
            int maxMarkers,
            int[] detectedIDsVect,
            double[] outRvects,
            double[] outTvects
    );

    public static native int estimateCameraPosition(
            long cameraMatrixAddr,
            long distCoeffsAddr,
            long inputMatAddr,
            int[] fixedMarkers,
            double[] fixedRVects,
            double[] fixedTVects,
            double[] fixedMarkerConfidences,
            double fixedLength,

            int foundPoses,
            int[] inMarkers,
            double[] inRvects,
            double[] inTvects,
            double[] outRvec,
            double[] outTvec
    );

    public static native void composeRT(
            double[] inRvec1,
            double[] inTvec1,
            double[] inRvec2,
            double[] inTvec2,
            double[] outRvec,
            double[] outTvec
    );

}
