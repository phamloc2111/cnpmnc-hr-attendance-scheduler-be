# Attendance explanations (SYNC-29 and SYNC-30)

Employees can submit one explanation per shift and issue type for a late arrival, missing check-in, or missing check-out. A rejected attendance record can also be explained once. The API derives eligible issues from assigned shifts and persisted attendance evidence; clients cannot create arbitrary issue types. A reason must contain 10–2000 characters. Employees may attach zero to three JPEG, PNG, or WebP images, each at most 5 MiB. Every submission starts in `PENDING`.

All endpoints require a bearer token under `/api/v1/attendance/explanations`:

| Method | Path | Role | Purpose |
| --- | --- | --- | --- |
| GET | `/candidates?from=YYYY-MM-DD&to=YYYY-MM-DD&page=0&size=20` | Employee | Eligible shifts and previously submitted explanations |
| POST | `/` | Employee | Submit JSON without images, or multipart form fields `scheduleId`, `category`, optional `attendanceRecordId`, `reason`, and repeated `images` parts |
| GET | `/{id}/images/{imageId}` | Owner/assigned manager/admin | Retrieve an attached image with bearer authentication and `Cache-Control: no-store` |
| GET | `/mine?from=...&to=...&page=0&size=20` | Employee | Submission and decision history |
| GET | `/?from=...&to=...&status=PENDING&page=0&size=20` | Manager/Admin | Review queue; managers see only direct reports |
| PATCH | `/{id}/decision` | Manager/Admin | Send `approved`, `reason`, and `exemptLatePenalty` |

Rejection requires a reason of at least 10 characters. A late penalty is refunded only when approval explicitly sets `exemptLatePenalty=true` and includes written grounds. Ordinary approval preserves the late deduction. Approved missing-punch explanations feed the monthly diligence calculation: timely explanations follow the 48-hour rule and the existing monthly warning tiers. An explanation changes review status; it does not fabricate a check-in or check-out event. Employees can see decisions through `/mine` and the attendance results page.

Migration `V15__attendance_explanations.sql` adds the audit table. Do not edit an already applied migration; add a later migration for schema changes.

Migration `V16__attendance_explanation_images.sql` stores image bytes and metadata. The image count and formats are validated on the server; invalid submissions do not create a partial explanation. Explanations include image metadata and authenticated URLs in employee and management responses.
