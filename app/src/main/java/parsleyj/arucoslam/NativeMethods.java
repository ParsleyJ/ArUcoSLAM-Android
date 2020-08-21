package parsleyj.arucoslam;

public class NativeMethods {

    public static native int detectCalibrationCorners(
            long inputMatAddr,
            float[][] cornersPoints,
            int[] idsVect,
            int[] size,
            int maxMarkers
    );

    public static native double calibrate(
            float[][][] collectedCorners,
            int[][] idsVect,
            int sizeRows,
            int sizeCols,
            long camMatrixAddr,
            long distCoeffsAddr
    );


    public static native double calibrateChArUco(
            float[][][] collectedCorners,
            int[][] idsVect,
            long[] collectedFrames,
            int sizeRows,
            int sizeCols,
            long camMatrixAddr,
            long distCoeffsAddr
    );

    public static native int processCameraFrame(
            long cameraMatrixAddr,
            long distCoeffsAddr,
            long inputMatAddr,
            long resultMatAddr,
            int[] acceptedMarkerIDs,
            double[] markerSizes,
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
            double[] fixedLengths,
            int foundPoses,
            int[] inMarkers,
            double[] inRvects,
            double[] inTvects,
            double[] previous2dPositions,
            double[] previous2dOrientations,
            double[] newPosition
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
