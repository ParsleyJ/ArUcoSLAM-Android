package parsleyj.arucoslam;

public class NativeMethods {


    public static native int detectMarkers(
            //TODO marker dictionary
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

            int fixedMarkerCount,
            int[] fixedMarkers,
            double[] fixedRVects,
            double[] fixedTVects,
            double fixedLength,

            int foundPoses,
            int[] inMarkers,
            double[] inRvects,
            double[] inTvects,
            double[] outRvec,
            double[] outTvec
    );

    public static native void invertRT(
            double[] inrvec,
            double[] intvec,
            double[] outrvec,
            double[] outtvec
    );

    public static native void composeRT(
            double[] inRvec1,
            double[] inTvec1,
            double[] inRvec2,
            double[] inTvec2,
            double[] outRvec,
            double[] outTvec
    );

    public static native double angularDistance(
            double[] inRvec1,
            double[] inRvec2
    );

    public static native void poseCentroid(
            double[] inRvecs,
            double[] inTvecs,
            int offset,
            int count,
            double[] outRvec,
            double[] outTvec
    );

    public static final int PHONE_POSE_STATUS_INVALID = -1;
    public static final int PHONE_POSE_STATUS_UNAVAILABLE = 0;
    public static final int PHONE_POSE_STATUS_UPDATED = 1;
    public static final int PHONE_POSE_STATUS_LAST_KNOWN = 2;

    public static native void renderMap(
            double markerLength,
            double[] markerRVects,
            double[] markerTVects,
            int fixedMarkerCount,
            double[] mapCameraPoseRotation,
            double[] mapCameraPoseTranslation,
            double mapCameraFovX,
            double mapCameraFovY,
            double mapCameraApertureX,
            double mapCameraApertureY,
            int phonePoseStatus,
            double[] phonePositionRvect,
            double[] phonePositionTvect,
            int previousPhonePosesCount,
            double[] previousPhonePosesRvects,
            double[] previousPhonePosesTvects,
            int mapCameraPixelsX,
            int mapCameraPixelsY,
            int mapTopLeftCornerX,
            int mapTopLeftCornerY,
            long resultMatAddr,
            boolean fullScreenMode
    );

}
