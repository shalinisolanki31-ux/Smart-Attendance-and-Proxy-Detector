package com.azuresight;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Everything related to the local SQLite file (azuresight_attendance.db).
 * No database server is needed, the file is created automatically.
 */
public class DatabaseHelper {

    private static final String URL = "jdbc:sqlite:" + Config.DB_FILE;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(URL);
    }

    // Create the tables if they are not there yet
    public void init() throws SQLException {
        try (Connection con = connect(); Statement st = con.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS students ("
                    + "student_id TEXT PRIMARY KEY, "
                    + "name TEXT NOT NULL, "
                    + "person_id TEXT NOT NULL)");

            st.execute("CREATE TABLE IF NOT EXISTS attendance ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "student_id TEXT NOT NULL, "
                    + "date TEXT NOT NULL, "
                    + "time TEXT NOT NULL, "
                    + "status TEXT NOT NULL)");

            st.execute("CREATE TABLE IF NOT EXISTS proxy_logs ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "date_time TEXT NOT NULL, "
                    + "student_id TEXT, "
                    + "reason TEXT NOT NULL)");
        }
    }

    // ---------- students ----------

    public void addStudent(Student s) throws SQLException {
        String sql = "INSERT INTO students (student_id, name, person_id) VALUES (?, ?, ?)";
        try (Connection con = connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, s.getStudentId());
            ps.setString(2, s.getName());
            ps.setString(3, s.getPersonId());
            ps.executeUpdate();
        }
    }

    public boolean studentExists(String studentId) throws SQLException {
        String sql = "SELECT 1 FROM students WHERE student_id = ?";
        try (Connection con = connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, studentId);
            return ps.executeQuery().next();
        }
    }

    public Student findByPersonId(String personId) throws SQLException {
        String sql = "SELECT student_id, name, person_id FROM students WHERE person_id = ?";
        try (Connection con = connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, personId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new Student(rs.getString("student_id"), rs.getString("name"), rs.getString("person_id"));
            }
            return null;
        }
    }

    public int countStudents() throws SQLException {
        try (Connection con = connect(); Statement st = con.createStatement()) {
            ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM students");
            return rs.getInt(1);
        }
    }

    // ---------- attendance ----------

    public boolean alreadyPresentToday(String studentId) throws SQLException {
        String sql = "SELECT 1 FROM attendance WHERE student_id = ? AND date = ?";
        try (Connection con = connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, studentId);
            ps.setString(2, LocalDate.now().toString());
            return ps.executeQuery().next();
        }
    }

    public void markPresent(String studentId) throws SQLException {
        String sql = "INSERT INTO attendance (student_id, date, time, status) VALUES (?, ?, ?, 'Present')";
        try (Connection con = connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, studentId);
            ps.setString(2, LocalDate.now().toString());
            ps.setString(3, LocalTime.now().format(TIME_FORMAT));
            ps.executeUpdate();
        }
    }

    // onlyToday = true gives only today's list, false gives the full history
    public List<AttendanceRecord> getAttendance(boolean onlyToday) throws SQLException {
        String sql = "SELECT a.student_id, s.name, a.date, a.time "
                + "FROM attendance a JOIN students s ON a.student_id = s.student_id "
                + (onlyToday ? "WHERE a.date = ? " : "")
                + "ORDER BY a.date DESC, a.time DESC";

        List<AttendanceRecord> list = new ArrayList<>();
        try (Connection con = connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            if (onlyToday) {
                ps.setString(1, LocalDate.now().toString());
            }
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new AttendanceRecord(rs.getString("student_id"), rs.getString("name"),
                        rs.getString("date"), rs.getString("time")));
            }
        }
        return list;
    }

    // ---------- proxy logs ----------

    public void addProxyLog(String studentId, String reason) throws SQLException {
        String sql = "INSERT INTO proxy_logs (date_time, student_id, reason) VALUES (?, ?, ?)";
        try (Connection con = connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, LocalDateTime.now().format(DATETIME_FORMAT));
            ps.setString(2, studentId == null ? "Unknown" : studentId);
            ps.setString(3, reason);
            ps.executeUpdate();
        }
    }

    public List<ProxyLog> getProxyLogs() throws SQLException {
        List<ProxyLog> list = new ArrayList<>();
        try (Connection con = connect(); Statement st = con.createStatement()) {
            ResultSet rs = st.executeQuery("SELECT date_time, student_id, reason FROM proxy_logs ORDER BY id DESC");
            while (rs.next()) {
                list.add(new ProxyLog(rs.getString("date_time"), rs.getString("student_id"), rs.getString("reason")));
            }
        }
        return list;
    }
}
