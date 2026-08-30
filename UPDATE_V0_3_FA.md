# نسخه 0.3.3

این پچ برای رفع خطای Build نسخه 0.3.2 است.

علت خطا این بود که AppViewModel نسخه جدید روی GitHub قرار گرفته بود،
اما Repository و مدل‌های هماهنگ با آن به‌صورت کامل جایگزین نشده بودند؛
در نتیجه متدهای createBooking و updateUnitCapacity شناخته نمی‌شدند.

این پچ فایل‌های مرتبط را با هم و به‌صورت یک مجموعه هماهنگ آپلود می‌کند:
- App.kt
- AppViewModel.kt
- Repository.kt
- SupabaseRest.kt
- Models.kt
- build.gradle.kts
- VERSION.txt

نسخه:
versionCode=6
versionName=0.3.3

برای این پچ نیازی به اجرای مجدد Migration در Supabase نیست.
