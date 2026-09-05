# راه‌اندازی پشتیبان روزانه v0.8

فایل `.github/workflows/daily-database-backup.yml` هر روز یک `pg_dump` از دیتابیس می‌گیرد و آن را ۳۰ روز در GitHub Actions نگه می‌دارد.

برای فعال شدن، در GitHub Repository > Settings > Secrets and variables > Actions یک Secret با نام دقیق زیر بسازید:

`SUPABASE_DB_URL`

مقدار آن باید Connection String دیتابیس Supabase باشد (ترجیحاً Direct connection و با رمز دیتابیس). این مقدار را داخل سورس برنامه یا فایل‌های عمومی قرار ندهید.

بعد از ثبت Secret، از تب Actions می‌توانید Workflow با نام `Daily Supabase Backup` را یک بار با `Run workflow` اجرا کنید تا صحت اتصال آزمایش شود. اجرای زمان‌بندی‌شده روزانه به طور خودکار انجام می‌شود.
