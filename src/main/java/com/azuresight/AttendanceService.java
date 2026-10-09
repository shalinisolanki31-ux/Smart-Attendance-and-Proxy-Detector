package com.azuresight;

import com.azuresight.AzureModels.Candidate;
import com.azuresight.AzureModels.DetectedFace;

import java.util.List;
import java.util.function.Consumer;

/**
 * The main logic of the project (same as the flowchart in our proposal):
 *
 *   capture -> detect face -> identify student -> liveness check -> mark present
 *                                                      |
 *                                          (anything fails) -> proxy alert
 */
public class AttendanceService {

    private final CameraService camera;
    private final AzureFaceService azure;
    private final DatabaseHelper db;

    public AttendanceService(CameraService camera, AzureFaceService azure, DatabaseHelper db) {
        this.camera = camera;
        this.azure = azure;
        this.db = db;
    }

    // "status" is used to show messages on the screen while we work
    public CheckInResult checkIn(Consumer<String> status) throws Exception {

        // ---- Step 1: take the first photo ----
        status.accept("Scanning your face...");
        byte[] photo1 = camera.captureJpeg();
        List<DetectedFace> faces1 = azure.detect(photo1);

        if (faces1 == null || faces1.isEmpty()) {
            return new CheckInResult(false, false, "No face found. Please look at the camera.");
        }
        if (faces1.size() > 1) {
            return new CheckInResult(false, false, "More than one face found. One student at a time please.");
        }
        DetectedFace face1 = faces1.get(0);

        // ---- Step 2: who is this? ----
        Candidate match = azure.identify(face1.faceId);
        if (match == null) {
            db.addProxyLog(null, "Unregistered face tried to check in");
            return new CheckInResult(false, true, "Face not registered in the system!");
        }

        Student student = db.findByPersonId(match.personId);
        if (student == null) {
            return new CheckInResult(false, false, "Face found in Azure but student is missing in local database.");
        }

        if (db.alreadyPresentToday(student.getStudentId())) {
            return new CheckInResult(false, false, student.getName() + ", your attendance is already marked today.");
        }

        // ---- Step 3: liveness check (turn your head) ----
        status.accept("Hi " + student.getName() + "! Now slowly turn your head to one side...");
        Thread.sleep(2500);

        byte[] photo2 = camera.captureJpeg();
        List<DetectedFace> faces2 = azure.detect(photo2);

        if (faces2 == null || faces2.size() != 1) {
            db.addProxyLog(student.getStudentId(), "Liveness failed: face lost during head-turn check");
            return new CheckInResult(false, true, "Liveness check failed. Please try again.");
        }
        DetectedFace face2 = faces2.get(0);

        // the head must have really moved
        double turn = Math.abs(face2.faceAttributes.headPose.yaw - face1.faceAttributes.headPose.yaw);
        if (turn < Config.MIN_HEAD_TURN) {
            db.addProxyLog(student.getStudentId(), "Liveness failed: head did not move (possible photo/screen)");
            return new CheckInResult(false, true, "Liveness failed! Could not confirm a real person.");
        }

        // the second face must be the SAME student (nobody swapped in the middle)
        Candidate match2 = azure.identify(face2.faceId);
        if (match2 == null || !match2.personId.equals(match.personId)) {
            db.addProxyLog(student.getStudentId(), "Liveness failed: different face in second scan");
            return new CheckInResult(false, true, "Face did not match in second scan!");
        }

        // ---- Step 4: everything is fine, mark present ----
        db.markPresent(student.getStudentId());
        return new CheckInResult(true, false, "Welcome " + student.getName() + "! You are marked PRESENT.");
    }
}
