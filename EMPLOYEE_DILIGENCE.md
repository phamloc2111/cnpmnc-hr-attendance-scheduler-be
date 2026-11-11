# Employee diligence (SYNC-32)

Authenticated employees can read only their own recorded work time and diligence deductions. All responses use `Cache-Control: no-store`; manager and admin accounts cannot use these endpoints.

| Method | Path | Result |
| --- | --- | --- |
| GET | `/api/v1/diligence/me?month=YYYY-MM` | Monthly score, recorded work minutes, and daily deduction history |
| GET | `/api/v1/diligence/me/week?date=YYYY-MM-DD` | Monday–Sunday score, work minutes, and daily history, including weeks across month boundaries |

`workedMinutes` comes from server-recorded check-in and check-out timestamps for each assigned shift, less the shift's break minutes. A shift without both events contributes zero completed work minutes. Future events are excluded. Deductions reuse the existing monthly diligence rules and manager decisions, including the 20-point daily cap. A weekly score is 100 minus the deductions in that week; it is an informational weekly view, while the monthly score resets at each month boundary. The response includes raw component deductions and the applied day total so employees can see when the cap reduced the charge.
