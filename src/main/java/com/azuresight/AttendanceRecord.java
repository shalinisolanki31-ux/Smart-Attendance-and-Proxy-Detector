package com.azuresight;

public class AttendanceRecord {
    private String studentId;
    private String name;
    private String date;
    private String time;

    public AttendanceRecord(String studentId, String name, String date, String time) {
        this.studentId = studentId;
        this.name = name;
        this.date = date;
        this.time = time;
    }

    public String getStudentId() { return studentId; }
    public String getName() { return name; }
    public String getDate() { return date; }
    public String getTime() { return time; }
}
