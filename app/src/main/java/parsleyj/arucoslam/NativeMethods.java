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

    public static native void processCameraFrame(
            long cameraMatrixAddr,
            long distCoeffsAddr,
            long inputMatAddr,
            long resultMatAddr,
            double[] outRvect,
            double[] outTvect
    );

}
