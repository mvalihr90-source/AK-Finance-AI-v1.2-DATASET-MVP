# v1.1 MVP

SMS -> Sender Intelligence -> Local Analyzer -> Classification -> Amount Extraction -> OTP Sanitization -> SQLite -> User Feedback.

OTP is never a completed transaction.
A high-confidence completed transaction can create one financial transaction.
User corrections become feedback labels.

The analyzer is deliberately a local MVP boundary. A real TFLite/LiteRT model can replace it later without changing the SMS/database/UI architecture.
