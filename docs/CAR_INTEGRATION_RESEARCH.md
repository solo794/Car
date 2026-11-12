# بحث: إزاي تطبيقنا يكلّم العربية (Dongfeng Shine / Shine GS)

> تاريخ البحث: 2026-11-12. المصادر في آخر الملف.
> ⚠️ كل اللي هنا من مجتمع الملاك والهندسة العكسية، **مش وثائق رسمية**،
> ولازم يتأكد على الشاشة بتاعتك بالأوامر في قسم "خطوات التحقق".

## 1. الشاشة دي إيه بالظبط؟

| البند | المعلومة | المصدر |
|---|---|---|
| النظام | Android 8.1 | المنيو الهندسي (chuyakov.ru) + 4PDA |
| المعالج | Qualcomm SDM450 (موديول Quectel SC60) | 4PDA، وأخبار WindLink X 2.0 |
| الواجهة | WindLink (من تطوير Dongfeng مع **Pateo/博泰**) | Sina / Gasgoo |
| الـ stack البرمجي | **Qinggan (擎感)**: شركة Pateo. اللانشر الأصلي `com.qinggan.app.launcher` | سكربتات المجتمع (tcse) |
| MCU | منفصل، بنسخ زي `C65OB202212191` و`C65OB202308151` | Drive2 |

**الخلاصة:** دي مش Android Automotive (AAOS) ومفيهاش `CarPropertyManager`
الرسمي. ده Android 8.1 عادي، وفوقه خدمات خاصة بشركة Qinggan، وهي اللي بتكلّم
الـ MCU، والـ MCU هو اللي على الـ CAN bus.

```
CAN bus ──► MCU ──(UART/SPI)──► خدمة com.qinggan.canbus.service (Binder: "qg.canbus")
                                        │
                ┌───────────────────────┼──────────────────────┐
           SystemUI (حرارة برّه)    شريط التكييف           تطبيقنا (لو اتصلنا بالـ Binder)
```

يعني اللي قالك "لازم تعمل listen to API" **كلامه صح في الأساس**: الـ API
موجود، بس هو **AIDL/Binder داخلي غير موثّق**، مش SDK عام.

## 2. الطرق الثلاث اللي ممكن نجيب بيها بيانات العربية

### الطريقة A: قراءة الـ logcat (اللي تطبيق Temper بيعملها)
تطبيق **Dongfeng Temper** (`com.example.temper`، النسخة 1.1.1، بتاع
TwinsLoppi من DongFeng Belarus) **مش بيستخدم أي API**. هو بياخد صلاحية
`READ_LOGS` عن طريق سكربت USB:

```sh
su -c "pm grant com.example.temper android.permission.READ_LOGS"
```

وبعدين بيقرا الـ logcat، ويطلّع منه السطر اللي خدمة الـ CAN بتكتب فيه حرارة برّه.
- ✅ سهل ومجرّب على Shine GS.
- ❌ قراءة بس (مفيش تحكم)، وهش (لو الفيرموير غيّر صيغة الـ log الطريقة تبوظ)،
  وعايز صلاحية لازم تتدّي بسكربت USB أو adb.

### الطريقة B: الاتصال المباشر بخدمة الـ CAN بتاعة Qinggan ⭐ (الأقوى)
فيه مكتبة مفتوحة اسمها [`qgcar`](https://github.com/agusibrahim/qgcar) معمولة
بالهندسة العكسية لنفس الـ stack (Qinggan/Pateo على Qualcomm MSM8953، في
Wuling/Baojun/Roewe). الـ SDM450 من نفس عيلة MSM8953، واللانشر عندنا
`com.qinggan.*`، **فالاحتمال كبير إنها نفس الخدمات أو قريبة منها جداً**.

الخدمات اللي المكتبة بتتعامل معاها:

| الخدمة | اسم الـ Binder | فيها إيه |
|---|---|---|
| `com.qinggan.canbus.service` | `qg.canbus`، الواجهة `com.qinggan.canbus.ICanBusService` | السرعة، RPM، الفتيس، الأبواب، **حرارة برّه**، التكييف كامل (60+ حقل) + **تحكم** في التكييف والشبابيك، TPMS، الحساسات، الوقود، العدّاد، **إرسال الملاحة للتابلوه** |
| `com.qinggan.keymanager.service` | — | زراير الدركسيون (اعتراض وتغيير وظيفتها) |
| `com.qinggan.carsignal.service` | `qg.carsignal` | حرارة البوردة، جهد البطارية 12V، نسخة الـ MCU، نوع العربية |
| `com.qinggan.systempolicy` | `qg.system_policy` | السطوع، ليلي/نهاري، قفل الشاشة |
| `com.qinggan.audiopolicy.service` | `qg.audio_policy` | EQ، Balance/Fader، الصوت حسب السرعة |
| `com.qinggan.tbox.service` | `dls_tbox_service` | VIN، 4G، الهوت سبوت |
| `com.qinggan.powermanager` | `qg.power_manager` | حالة التشغيل، شاشة التوقف |
| `com.qinggan.tuner.service` | `qg.tuner_service` | الراديو FM/AM |

بتشتغل إزاي: `ServiceManager.getService("qg.canbus")` (بالـ reflection) أو
`bindService` على الباكدج، وبعدين `binder.transact(code, …)` بأرقام
Transaction معروفة (مثلاً `getAirCondition=30`، `setAirConditionState=31`،
`getVehicleState=54`، `setVehicleState=55`)، وتسجيل Callback
`ICanBusServiceCallback` عشان توصلك التحديثات لحظة بلحظة.

- ✅ قراءة **وتحكم**، ولحظي (callbacks)، ومن غير root لو الخدمة مش بتشيّك على
  الصلاحيات.
- ⚠️ **أرقام الـ Transaction بتتغير مع نسخة الـ AIDL.** لو Dongfeng عندها نسخة
  مختلفة عن Wuling هنبعت رقم غلط ونستدعي دالة غلط. لازم نتأكد من الأرقام من
  الـ APK الأصلي على العربية قبل ما نبعت أي أمر **كتابة** (تحكم).
- ⚠️ ممكن الخدمة تكون محمية بـ signature permission أو whitelist. ساعتها
  محتاجين نثبّت تطبيقنا كـ system app في `/system/priv-app` (وده متاح بسكربت
  `custom_rep.sh` اللي بيشتغل كـ root، شوف `DEVICE_ACCESS.md`).
- ⚠️ ترخيص `qgcar` بيقول "research and personal use" ومش عليه رخصة واضحة،
  فـ**منقلش الكود بتاعه في الريبو**. نستخدمه مرجع ونكتب الطبقة بتاعتنا بنفسنا.

### الطريقة C: CAN مباشرة (OBD2 / CAN adapter)
ELM327 Bluetooth على فيشة OBD2 (فيه بوست على Drive2 عن Shine 1.5). بيجيب
بيانات المحرك (حرارة المية، RPM، السرعة) بالـ PIDs القياسية.
- ✅ مستقل تماماً عن الشاشة.
- ❌ مش هيتحكم في التكييف، وغالباً مفيش حرارة برّه في الـ PIDs القياسية، وعايز
  قطعة هاردوير.

## 3. اقتراحي

1. **أول حاجة** نفتح ADB (سكربت `openADB` في `DEVICE_ACCESS.md`).
2. نشغّل **أوامر التحقق** اللي تحت، ونبعتلي النتايج. دي هتحسم هل الطريقة B
   شغالة على عربيتك.
3. نسحب الـ APKs الأصلية (`com.qinggan.canbus.service` واللانشر وشريط التكييف)
   ونفكّها بـ **jadx** عشان نطلّع الـ AIDL الحقيقي وأرقام الـ Transaction بتاعة
   Dongfeng بالظبط. نسحب كمان `Dongfeng_Temper_1.1.1.apk` ونشوف هو بيدوّر على
   أنهي tag في الـ log.
4. نبني في اللانشر طبقة `VehicleBridge` بثلاث مستويات: Binder (B)، ولو فشل
   يرجع لـ logcat (A)، ولو فشل يخفي الويدجت. نبدأ **بالقراءة بس** (حرارة برّه،
   سرعة، فتيس، أبواب، TPMS)، وبعدها التحكم في التكييف لما الأرقام تتأكد.
   ده هيخلي ودجت التكييف الاحتياطي يبقى حقيقي بدل الأرقام المحلية.

## 4. خطوات التحقق على العربية (بعد ما ADB يشتغل)

```sh
adb shell getprop ro.build.version.release        # المتوقع 8.1.0
adb shell getprop ro.board.platform               # المتوقع msm8953
adb shell pm list packages | grep -i -E "qinggan|pateo|dfm|dongfeng|windlink"
adb shell service list | grep -i -E "qg\.|qinggan|canbus|tbox"
adb shell dumpsys package com.qinggan.canbus.service | grep -i -E "permission|version"

# اسحب الـ APKs عشان نفكّها
adb shell pm path com.qinggan.canbus.service
adb pull <المسار-اللي-طلع> canbus.apk

# شوف الـ CAN service بتكتب إيه في الـ log (افتح/اقفل التكييف وغيّر الحرارة)
adb logcat -v time | grep -i -E "canbus|aircondition|temp|hvac"
```

لو `service list` طلّع `qg.canbus`، يبقى الطريقة B ممكنة بنسبة كبيرة جداً.

## 5. تحذيرات
- أوامر **الكتابة** على الـ CAN (تكييف، شبابيك، شنطة) لو اتبعتت بأرقام غلط
  ممكن تعمل تصرّف غير متوقع. **ممنوع نجرّبها والعربية ماشية.**
- السكربتات اللي بتشتغل كـ root من الـ USB تقدر تبوّظ الشاشة. وفيه احتمال إنها
  تأثر على الضمان، فاسأل الوكيل الأول.
- ما تشتغلش على الشاشة وأنت سايق.

## المصادر
- مكتبة qgcar (خدمات Qinggan): https://github.com/agusibrahim/qgcar
- سكربتات مجتمع dongfeng-aeolus.ru (ومنها `Others/temper`):
  https://github.com/tcse/dongfeng-aeolus.ru
- صفحة Temper: https://dongfeng-aeolus.ru/141-dongfeng-temper-otobrazhaet-temperaturu-za-bortom-na-jekrane-magnitoly-shine-gs.html
- قسم التطبيقات في الموقع: https://dongfeng-aeolus.ru/apk/
- DFM Sidebar: https://dongfeng-aeolus.ru/145-dfm-sidebar.html
- Beetle ADB على Shine GS: موضوع في منتدى dongfeng-aeolus.ru (قسم MCU Shine GS)
- 4PDA، موضوع ГУ Shine GS: https://4pda.to/forum/index.php?showtopic=1079450
- المنيو الهندسي: https://chuyakov.ru/2869-dongfeng-shine-gs-inzhenernoe-menju.html
- تحديث الفيرموير/MCU: https://www.drive2.ru/l/669464318802143720/
- اختراق الشاشة: https://www.drive2.ru/l/668358759860413230/
- مراجعة بعد 30 ألف كم (Temper وShowtime): https://www.drive2.ru/l/723614716714297704/
- ELM327 على Shine: https://www.drive2.ru/l/680804131975205918/
- WindLink X 2.0 وSC60: https://www.pcauto.com.cn/nation/4182/41820228.html
- أكواد ADB الصينية (`hellofengshen`، المنفذ 5578): https://tieba.baidu.com/p/7738071242
