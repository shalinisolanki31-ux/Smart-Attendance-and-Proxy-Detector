package com.azuresight;

import java.util.List;

/**
 * These small classes match the JSON that Azure sends back.
 * Gson fills them automatically (the field names must be same as in JSON).
 */
public class AzureModels {

    public static class DetectedFace {
        public String faceId;
        public FaceAttributes faceAttributes;
    }

    public static class FaceAttributes {
        public HeadPose headPose;
    }

    public static class HeadPose {
        public double pitch;
        public double roll;
        public double yaw;     // left-right turn of the head
    }

    public static class IdentifyResult {
        public String faceId;
        public List<Candidate> candidates;
    }

    public static class Candidate {
        public String personId;
        public double confidence;
    }

    public static class PersonCreated {
        public String personId;
    }

    public static class TrainingStatus {
        public String status;   // notstarted, running, succeeded, failed
    }
}
