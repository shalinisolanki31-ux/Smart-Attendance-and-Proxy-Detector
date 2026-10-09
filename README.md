# SmartPresence - Face Recognition & Proxy Detector System
Team Azure Sight | BCA Project

A JavaFX desktop app. A student looks at the webcam, Azure Face API finds out who
it is, a small liveness check proves it is a real person, and attendance is saved
in a local SQLite file. Fake attempts show a red **Proxy Alert** on the teacher dashboard.

## What you need
1. **JDK 17** and **Maven** (or IntelliJ / Eclipse / NetBeans with Maven support)
2. A **webcam** and internet
3. An **Azure Face resource** (portal.azure.com -> create "Face" resource)
   - copy the *Key* and *Endpoint* from "Keys and Endpoint"
   - Important: Microsoft keeps face *identification* under "Limited Access".
     You may need to fill the access form on the Azure Face page and wait for approval
     (use your college email and write that it is an academic project).

## Setup
Open `src/main/java/com/azuresight/Config.java` and paste your key and endpoint
(or set the environment variables `AZURE_FACE_KEY` and `AZURE_FACE_ENDPOINT`).

## Run
```
mvn clean javafx:run
```
Or in an IDE: import as Maven project and run `Launcher.java`.

## How to use
1. **Register Student** tab: type ID + name, student looks at camera, click *Capture & Register*.
2. **Dashboard** tab: click *Start Check-in*, student looks at camera, then slowly turns head when asked.
3. Teacher sees the result, today's list and any red proxy alert.
4. **Attendance History** and **Proxy Alerts** tabs show everything saved in the database.

## Project files (what each one does)
| File | Job |
|---|---|
| `Config.java` | key, endpoint and settings |
| `MainApp.java` | JavaFX dashboard (4 tabs) |
| `CameraService.java` | Sarxos webcam, live preview, JPEG capture |
| `AzureFaceService.java` | HttpClient calls to Azure (detect, identify, register, train) |
| `AzureModels.java` | classes that Gson fills from Azure's JSON |
| `AttendanceService.java` | the main check-in logic (same as our flowchart) |
| `DatabaseHelper.java` | SQLite tables: students, attendance, proxy_logs |
| `Student / AttendanceRecord / ProxyLog / CheckInResult` | simple data classes |

## How the liveness check works
Azure's built-in Face Liveness service needs a special session with its mobile/web SDK,
so it cannot be called with one plain photo from Java. So we do a simple
challenge-response check: the student is asked to turn the head, we read the head
angle (yaw) from Azure in two photos, and the angle must change and the face must
still be the same person. A still photo or phone screen does not turn on its own.
This is basic - it can be written in the report as "future scope: Azure Face Liveness SDK".

## Known limits (good to say honestly in viva)
- Free Azure tier allows about 20 calls/minute; one check-in uses 4 calls.
- Needs good light and one face at a time.
- Someone moving a photo can fake a small angle change; the real Azure Liveness would be stronger.
