package parsleyj.arucoslam;

/**
 * Collections of the all native methods used by the app, which were all collected here because the
 * linking between Kotlin and the C++ JNI functions does not recognise the types of some parameters
 * (in particular, {@code Array<Array<Double>>} were translated to {@code jobject[]} in C++,
 * instead of {@code jdouble[][]}.
 */
public class NativeMethods {


    /**
     * Given an image, detects all the markers and computes their poses in it. Each "pose"
     * is an RT transformation which switches points from the marker's coordinate system
     * to the coordinate system of the camera?
     * Moreover, copies the input image on the output image, and adds the "contours"
     * to the detected markers.
     *
     * @param markerDictionary the dictionary of markers
     * @param cameraMatrixAddr the camera matrix
     * @param distCoeffsAddr the distortion coefficients of the camera
     * @param inputMatAddr the input image
     * @param resultMatAddr the output image
     * @param markerSize the side length (in meters) of the markers
     * @param maxMarkers the maximum numbers of markers expected to be found in an image
     * @param detectedIDsVect an array which will be filled with the IDs of the found markers
     * @param outRvects an array (of size 3*N) which contains the rotation vectors of the marker
     *                  poses
     * @param outTvects an array (of size 3*N) which contains the translation vectors of the marker
     *                  poses
     * @return the number of markers found (N)
     */
    public static native int detectMarkers(
            int markerDictionary,
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

    /**
     * Given the camera parameters, a set of known poses of various markers and a set of poses of
     * markers in an image, attempts to compute an estimate of the pose of the camera in the world
     * coordinate system. The returned RT transformation is the one that changes from the world
     * coordinate system to the camera coordinate system.
     * When there are no pose indicators (i.e. the various poses of the camera, each one obtained
     * from the markers which pose in the world is known), nothing is done on the output vectors.
     * When there is only an indicator, it is copied as-is on the output vectors.
     * When exactly 2 indicators are passed, the "pose centroid" of the two indicators are computed
     * and returned as result.
     * When more than 2 indicators of such pose are available, the estimate is computed by using
     * the RANSAC method.
     * Moreover, this function draws the 3D axis of the poses of the markers on the image Mat.
     *
     * @param cameraMatrixAddr the camera matrix
     * @param distCoeffsAddr the distortion coefficients of the camera
     * @param inputMatAddr the input image
     * @param fixedMarkerCount the count of known markers
     * @param fixedMarkers the ids of the known markers
     * @param fixedRVects the rotation vectors of the known markers
     * @param fixedTVects the translation vectors of the known markers
     * @param fixedLength the side length of the markers
     * @param foundPoses the count of found marker poses
     * @param inMarkers the ids of the found markers
     * @param inRvects the rotation vectors of the found markers
     * @param inTvects the translation vectors of the found markers
     * @param outRvec the output rotation vector of the computed camera pose estimate
     * @param outTvec the output translation vector of the computed camera pose estimate
     * @param tvecInlierThreshold (RANSAC) threshold of the distance in meters used to determine
     *                            if an estimated pose is an inlier
     * @param tvecOutlierProbability (RANSAC) the (pre-estimated) probability that a position in a
     *                               random subset of poses is an outlier (p)
     * @param rvecInlierThreshold (RANSAC) threshold of the "angular" distance in radians used to
     *                            determine if an estimated pose is an inlier
     * @param rvecOutlierProbability (RANSAC) the (pre-estimated) probability that an orientation
     *                               in a random subset of poses is an outlier (p)
     * @param maxRansacIterations (RANSAC) the maximum number of iterations to be performed (M)
     * @param optimalModelTargetProbability (RANSAC) probability (P) that the returned pose estimate
     *                                      is the optimal pose (i.e. the one with the most inliers);
     *                                      this and the previous parameters are used to compute the
     *                                      number of effective RANSAC iterations by using this
     *                                      formula: min(N, M) where N = log(1-P)/log(1-(1-p^S)) and
     *                                      S is the size of the random subset of poses of each
     *                                      iteration, which is equal to 5 if the number of total
     *                                      poses is >= 10, 2 otherwise.
     * @return the number of inliers (w.r.t. the poses used to estimate the pose) of the returned
     *          estimate.
     */
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
            double[] outTvec,

            double tvecInlierThreshold,
            double tvecOutlierProbability,
            double rvecInlierThreshold,
            double rvecOutlierProbability,
            int maxRansacIterations,
            double optimalModelTargetProbability
    );

    /**
     * Computes the inverse of an RT transformation. Note that such computation is not as costly
     * as the computation of the inverse of a generic matrix, since the inverse of <br>
     * [   R,    T,    <br>
     *     0,    1   ] <br>
     * is equal to     <br>
     * [  R', -R'T,    <br>
     *     0,    1   ] <br>
     * where R' is the transpose of R.
     *
     * @param inrvec the input rotation vector
     * @param intvec the input translation vector
     * @param outrvec the rotation vector of the inverse
     * @param outtvec the translation vector of the inverse
     */
    public static native void invertRT(
            double[] inrvec,
            double[] intvec,
            double[] outrvec,
            double[] outtvec
    );

    /**
     * Computes the composition of two RT transformation. If the transformation 1 switches from the
     * coordinate system S_a to the coordinate system S_b, and the transformation 2 switches from
     * the coordinate system S_b to the coordinate system S_c, the transfromation resulting from
     * this operation switches from the coordinate system S_a to the cooordinate system S_c.
     *
     * @param inRvec1 first input rotation vector
     * @param inTvec1 first input translation vector
     * @param inRvec2 first input rotation vector
     * @param inTvec2 first input translation vector
     * @param outRvec the rotation vector of the composed transformation
     * @param outTvec the translation vector of the composed transformation
     */
    public static native void composeRT(
            double[] inRvec1,
            double[] inTvec1,
            double[] inRvec2,
            double[] inTvec2,
            double[] outRvec,
            double[] outTvec
    );

    /**
     * Computes the "angular distance" i.e. the difference between two rotations as a single angle
     * in degrees.
     * @param inRvec1 the first rotation
     * @param inRvec2 the second rotation
     * @return the angular distance
     */
    public static native double angularDistance(
            double[] inRvec1,
            double[] inRvec2
    );

    /**
     * Computes the "pose centroid" i.e. the pose which is the average of the all the poses in the
     * input data arrays between offset*3 and offset*3+count*3.
     * @param inRvecs the N*3 array of N rotation vectors
     * @param inTvecs the N*3 array of N translation vectors
     * @param offset the first pose to be considered
     * @param count how many poses to use from the arrays (starting from offset)
     * @param outRvec the output rotation
     * @param outTvec the output translation
     */
    public static native void poseCentroid(
            double[] inRvecs,
            double[] inTvecs,
            int offset,
            int count,
            double[] outRvec,
            double[] outTvec
    );

    /**
     * The pose of the phone is found, but is invalidated by a validity check
     */
    public static final int PHONE_POSE_STATUS_INVALID = -1;
    /**
     * No pose of the phone is available yet
     */
    public static final int PHONE_POSE_STATUS_UNAVAILABLE = 0;
    /**
     * The pose of the phone is new and valid
     */
    public static final int PHONE_POSE_STATUS_UPDATED = 1;
    /**
     * No new pose of the phone is available, the indicated pose is the last known
     */
    public static final int PHONE_POSE_STATUS_LAST_KNOWN = 2;

    /**
     * Renders a 2D map on the mat. It shows the poses of all found markers, the pose of the camera
     * (if available) and its status, the track of previous positions of the camera.
     *
     * @param markerLength the side length of all the markers
     * @param markerRVects the rotation vector of the pose of the known markers
     * @param markerTVects the translation vector of the pose of the known markers
     * @param fixedMarkerCount the number of known markers to be rendered
     * @param mapCameraPoseRotation the orientation in the world of the virtual map camera
     * @param mapCameraPoseTranslation the position in the world of the virtual map camera
     * @param mapCameraFovX the angle in radians which defines the horizontal Field Of View of the
     *                      virtual map camera
     * @param mapCameraFovY the angle in radians which defines the vertical Field Of View of the
     *                      virtual map camera
     * @param mapCameraApertureX the "sensor" width of the virtual map camera
     * @param mapCameraApertureY the "sensor" heigth of the virtual map camera
     * @param phonePoseStatus the current status code of the phone pose
     * @param phonePositionRvect the rotation vector of the phone pose
     * @param phonePositionTvect the translation vector of the phone pose
     * @param previousPhonePosesCount the count of previous phone poses
     * @param previousPhonePosesRvects the 3*N array for the rotation vectors of the previous poses
     * @param previousPhonePosesTvects the 3*N array for the translation vectors of the previous poses
     * @param mapCameraPixelsX the number of pixels in the resulting mat which will be used to
     *                         render the mat when not in fullscreen mode
     * @param mapCameraPixelsY the number of pixels in the resulting mat which will be used to
     *                         render the mat when not in fullscreen mode
     * @param mapTopLeftCornerX the x coordinate in the mat of the top-left corner of the map
     * @param mapTopLeftCornerY the x coordinate in the mat of the top-left corner of the map
     * @param resultMatAddr the mat on which the map will be rendered
     * @param fullScreenMode whether the map should be rendered in fullscreen mode or not
     */
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
