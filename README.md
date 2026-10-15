# Daneshyar Fresh — دانش‌یار ۱.۲.۲

نویسنده: امیرحسین فلاحتی

## ساخت از مخزن مستقل daneshyar-fresh

این مخزن سورس را مستقیم می‌سازد. هیچ ZIP قدیمی در GitHub استخراج نمی‌شود.

۱. در GitHub یک مخزن خالی با نام daneshyar-fresh بسازید.
۲. بسته Daneshyar-Fresh-Repository.zip را روی کامپیوتر استخراج کنید. داخل پوشه daneshyar-fresh، پوشه UniversityAssistant و README.md را با Add file > Upload files آپلود و Commit کنید؛ خود ZIP یا پوشه بیرونی daneshyar-fresh را آپلود نکنید.
۳. با Add file > Create new file مسیر .github/workflows/android.yml را بسازید. تمام متن فایل جداگانه daneshyar-fresh-android.yml را در آن قرار دهید و Commit کنید. اگر پوشه .github همراه سورس قبلاً آپلود شده است، همین فایل را ویرایش کنید.
۴. Actions > Daneshyar Fresh 1.2.2 - Build and test APK > Run workflow را با variant=debug اجرا کنید.
۵. در Summary همان اجرا، خروجی Daneshyar-Fresh-APK-v1.2.2-debug-run-شماره را دانلود و استخراج کنید. فایل نصب Daneshyar-1.2.2-debug.apk است؛ build-info.json نسخه واقعی، هش و شماره اجرا را ثبت می‌کند.
۶. در صورت شکست، گزارش Daneshyar-Fresh-Reports-v1.2.2-run-همان شماره را ارسال کنید. وجود APK به تنهایی به معنی عبور تست‌ها نیست.

در ریشه مخزن باید UniversityAssistant/app/build.gradle و .github/workflows/android.yml موجود باشند. فایل‌های قبلی مخزن قدیمی را اینجا کپی نکنید.

این بسته سورس است و APK آماده داخل آن نیست. ساخت واقعی APK و تست اندروید هنوز انجام نشده‌اند. ساخت GitHub به اینترنت نیاز دارد؛ اجرای برنامه روی گوشی آفلاین است.

مخزن جدید از نظر سورس مستقل است؛ برنامه همان شناسه ir.daneshyar.offline را دارد. برای نصب به صورت آپدیت روی نسخه قبلی، همان کلید امضا لازم است. در مخزن جدید Secrets امضا خودکار منتقل نمی‌شوند؛ راهنمای UPDATE_SECURITY_FA.md را ببینید. پیش از تغییر نصب پشتیبان بگیرید.


Java 17، رابط بومی Android با Material 3، دیتابیس SQLite محلی. امکانات: مدیریت دروس، استاد، نمره و واحد، برنامه هفتگی، رویداد، تقویم شمسی و انتخابگر ساعت/تاریخ، اعلان، صدای دلخواه، تکرار هشدار تا توقف دستی با محدودیت‌های اندروید، تم، جزوه/یادداشت/لینک هر درس، پشتیبان محلی.

راهنمای کامل: UniversityAssistant/README_FA.md
