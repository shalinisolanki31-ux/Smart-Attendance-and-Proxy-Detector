package com.azuresight;

/**
 * All the settings of the project are kept here,
 * so we only change them in one place.
 */
public class Config {

    // Copy these two from your Azure Face resource (Keys and Endpoint page)
    public static final String ENDPOINT =
            System.getenv().getOrDefault("AZURE_FACE_ENDPOINT", "https://YOUR-RESOURCE-NAME.cognitiveservices.azure.com");
    public static final String API_KEY =
            System.getenv().getOrDefault("AZURE_FACE_KEY", "PASTE_YOUR_KEY_HERE");

    // Name of the group in Azure where all student faces are stored
    public static final String PERSON_GROUP_ID = "azuresight-students";

    // Azure gives a confidence between 0 and 1. Below this we say "not recognized"
    public static final double MIN_CONFIDENCE = 0.70;

    // How many degrees the head must turn for the liveness check to pass
    public static final double MIN_HEAD_TURN = 12.0;

    // Our local database file
    public static final String DB_FILE = "azuresight_attendance.db";
}
