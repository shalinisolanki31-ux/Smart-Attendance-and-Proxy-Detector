package com.azuresight;

public class Student {
    private String studentId;
    private String name;
    private String personId;   // the id Azure gives to this student's face

    public Student(String studentId, String name, String personId) {
        this.studentId = studentId;
        this.name = name;
        this.personId = personId;
    }

    public String getStudentId() { return studentId; }
    public String getName() { return name; }
    public String getPersonId() { return personId; }
}
