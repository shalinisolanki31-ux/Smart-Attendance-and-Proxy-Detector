package com.azuresight;

public class ProxyLog {
    private String dateTime;
    private String studentId;   // can be "Unknown" if face was not registered
    private String reason;

    public ProxyLog(String dateTime, String studentId, String reason) {
        this.dateTime = dateTime;
        this.studentId = studentId;
        this.reason = reason;
    }

    public String getDateTime() { return dateTime; }
    public String getStudentId() { return studentId; }
    public String getReason() { return reason; }
}
