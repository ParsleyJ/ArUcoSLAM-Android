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
            int sizeRows,
            int sizeCols,
            long[] resultsAddresses
    );



}
