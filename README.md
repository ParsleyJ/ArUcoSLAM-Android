# ArUcoSLAM-Android
An Android app that implements a SLAM system (Simultaneous Localization And Mapping) using ArUco markers and computer vision.

The camera acquires frames that are processed in real time. By analyzing the perspective of the marker images that appear on each frame, relative 3D poses of the markers and of the phone camera can be estimated. 
The app keeps discovered markers in memory, and uses new discovered markers to continue to detect the pose of the phone while moving away from the old markers. 
A 3D map with a view from the top is rendered on the right-bottom corner, which displays the pose of the camera, the poses of the markers, and a track which represents an approximation of the history of the positions of the phone.
A complex data analysis system performs real time checks on new detected phone poses (by taking into account how such an estimate is computed and the history of the previous poses) and ensures that only valid estimates are considered, while invalid estimates (in red) are discarded.


Written in Kotlin, Java and C++. Image processing and computer vision tasks are implemented with the help of OpenCV libraries.

![](arucoslam1.gif)

To ensure fast performances, the processing of the stream of frames is parallelized by using workers implemented via Kotlin's coroutines, which launched on the Android main background dispatcher.
Moreover, all big data structures (like the openCV Mat objects that contain the frames) are recycled for each worker, to avoid heavy allocation jobs and GC invocations as most as possible.
