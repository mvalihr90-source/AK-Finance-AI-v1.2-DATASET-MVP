# AK Finance AI v1.2 — Dataset MVP

این نسخه سه کار اصلی را اضافه می‌کند:

1. وارد کردن همه SMSهای قبلی گوشی
2. Label کردن هر پیام با چند برچسب مستقل
3. خروجی استاندارد و امن برای Machine Learning

Labelهای اصلی:
- BANK_TRANSACTION
- BANK_OTP
- BANK_ALERT
- NON_BANK
- UNKNOWN

فیلدهای تکمیلی:
- bank
- amount
- transaction_direction
- transaction_status
- payment_channel

## خروجی ML

با دکمه `خروجی Dataset برای Machine Learning` یک ZIP ساخته می‌شود که شامل:

- dataset.jsonl
- dataset.csv
- train.jsonl
- validation.jsonl
- test.jsonl
- label_schema.json
- manifest.json

تقسیم train/validation/test به‌صورت deterministic و درون هر label انجام می‌شود.

برای حریم خصوصی:
- raw SMS داخل export قرار نمی‌گیرد.
- OTP واقعی داخل export قرار نمی‌گیرد.
- متن آموزشی از `sanitized_text` استفاده می‌کند.

این MVP هنوز مدل ML واقعی ندارد؛ داده‌های Label شده خروجی مناسب برای مرحله آموزش مدل محلی بعدی هستند.
