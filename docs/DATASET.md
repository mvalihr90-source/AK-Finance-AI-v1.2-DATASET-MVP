# Dataset Design

هدف Dataset این است که مدل نهایی فقط یک Parser متنی نباشد و چند وظیفه را یاد بگیرد.

هر رکورد شامل:
- sender
- bank
- sanitized_text
- message_type
- transaction_direction
- transaction_status
- payment_channel
- amount
- user_label
- confidence

`user_label` برچسب مرجع انسانی است و از prediction مدل جدا نگه داشته می‌شود.

برای آموزش نهایی:
- train.jsonl برای آموزش
- validation.jsonl برای تنظیم مدل
- test.jsonl فقط برای ارزیابی نهایی

OTP واقعی هرگز در export قرار نمی‌گیرد.
