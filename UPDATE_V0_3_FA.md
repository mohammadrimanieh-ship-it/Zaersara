# نسخه 0.3.4

رفع خطای Build نسخه 0.3.3.

علت خطا:
فایل App.kt تابع normalizeNumeric را import می‌کرد، اما فایل
util/PersianDigits.kt که این تابع در آن تعریف شده بود، در پچ قبلی
برای GitHub قرار نگرفته بود.

این پچ فایل PersianDigits.kt را اضافه می‌کند و نسخه را به 0.3.4
(versionCode=7) افزایش می‌دهد.

نیازی به اجرای مجدد SQL در Supabase نیست.
