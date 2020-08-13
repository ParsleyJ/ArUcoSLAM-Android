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
            int[][] vectors,
            boolean refine,
            int sizeRows,
            int sizeCols,
            long[] resultsAddresses
    );


    public static native void processCameraFrame(
            long cameraMatrixAddr,
            long distCoeffsAddr,
            long inputMatAddr,
            long resultMatAddr,
            double[] outRvect,
            double[] outTvect
    );

}
