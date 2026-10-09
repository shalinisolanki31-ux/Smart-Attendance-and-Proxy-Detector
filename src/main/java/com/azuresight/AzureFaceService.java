package com.azuresight;

import com.azuresight.AzureModels.*;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

/**
 * Talks to the Azure Face API using Java HttpClient (JDK 17).
 * Every method here sends one request over HTTPS and reads the JSON answer with Gson.
 */
public class AzureFaceService {

    private final HttpClient client = HttpClient.newHttpClient();
    private final Gson gson = new Gson();

    private final String groupUrl = Config.ENDPOINT + "/face/v1.0/persongroups/" + Config.PERSON_GROUP_ID;

    // ---------- small helper methods ----------

    private HttpResponse<String> sendJson(String method, String url, String jsonBody) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .header("Ocp-Apim-Subscription-Key", Config.API_KEY)
                .header("Content-Type", "application/json");

        if (jsonBody == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.method(method, HttpRequest.BodyPublishers.ofString(jsonBody));
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> sendImage(String url, byte[] jpg) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Ocp-Apim-Subscription-Key", Config.API_KEY)
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(jpg))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    // If Azure says anything other than success, we throw an error with its message
    private String checkOk(HttpResponse<String> response) throws IOException {
        if (response.statusCode() >= 300) {
            throw new IOException("Azure error " + response.statusCode() + ": " + response.body());
        }
        return response.body();
    }

    // ---------- setup ----------

    // Makes the "student group" in Azure (only needed the first time)
    public void createPersonGroupIfNeeded() throws IOException, InterruptedException {
        String body = "{\"name\":\"SmartPresence Students\",\"recognitionModel\":\"recognition_04\"}";
        HttpResponse<String> response = sendJson("PUT", groupUrl, body);

        // 409 means the group is already there, which is fine
        if (response.statusCode() != 409) {
            checkOk(response);
        }
    }

    // ---------- registering a student ----------

    // Creates a person in Azure and adds all given photos of the face. Returns the personId.
    public String registerStudent(String name, List<byte[]> photos) throws IOException, InterruptedException {
        String body = gson.toJson(java.util.Map.of("name", name));
        String json = checkOk(sendJson("POST", groupUrl + "/persons", body));
        String personId = gson.fromJson(json, PersonCreated.class).personId;

        try {
            for (byte[] photo : photos) {
                String url = groupUrl + "/persons/" + personId
                        + "/persistedFaces?detectionModel=detection_03";
                checkOk(sendImage(url, photo));
            }
        } catch (IOException e) {
            // something went wrong (maybe no face in photo), so delete the half-made person
            sendJson("DELETE", groupUrl + "/persons/" + personId, null);
            throw e;
        }

        trainGroup();
        return personId;
    }

    // Azure must "learn" the new faces before it can recognize them
    private void trainGroup() throws IOException, InterruptedException {
        checkOk(sendJson("POST", groupUrl + "/train", null));

        for (int i = 0; i < 20; i++) {          // wait up to around 20 seconds
            Thread.sleep(1000);
            String json = checkOk(sendJson("GET", groupUrl + "/training", null));
            String status = gson.fromJson(json, TrainingStatus.class).status;
            if (status.equals("succeeded")) {
                return;
            }
            if (status.equals("failed")) {
                throw new IOException("Azure training failed.");
            }
        }
        throw new IOException("Training took too long. Try again.");
    }

    // ---------- checking attendance ----------

    // Finds faces in a photo. We also ask for the head pose (used in the liveness check)
    public List<DetectedFace> detect(byte[] jpg) throws IOException, InterruptedException {
        String url = Config.ENDPOINT + "/face/v1.0/detect"
                + "?returnFaceId=true"
                + "&returnFaceAttributes=headPose"
                + "&recognitionModel=recognition_04"
                + "&detectionModel=detection_03";
        String json = checkOk(sendImage(url, jpg));
        return gson.fromJson(json, new TypeToken<List<DetectedFace>>() {}.getType());
    }

    // Asks Azure: "whose face is this?" Returns the best match or null if nobody matched
    public Candidate identify(String faceId) throws IOException, InterruptedException {
        String body = gson.toJson(java.util.Map.of(
                "personGroupId", Config.PERSON_GROUP_ID,
                "faceIds", List.of(faceId),
                "maxNumOfCandidatesReturned", 1,
                "confidenceThreshold", Config.MIN_CONFIDENCE));

        String json = checkOk(sendJson("POST", Config.ENDPOINT + "/face/v1.0/identify", body));
        List<IdentifyResult> results = gson.fromJson(json, new TypeToken<List<IdentifyResult>>() {}.getType());

        if (results.isEmpty() || results.get(0).candidates.isEmpty()) {
            return null;
        }
        return results.get(0).candidates.get(0);
    }
}
